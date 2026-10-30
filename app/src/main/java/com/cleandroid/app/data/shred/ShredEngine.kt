package com.cleandroid.app.data.shred

import android.content.Context
import android.os.StatFs
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID

/**
 * Permanent file deletion (shredding).
 *
 * Method: 3 sequential overwrite passes (0x00, 0xFF, CSPRNG bytes) with an
 * fsync after each pass, then rename-to-random-name + unlink so the original
 * file name can no longer be linked to any leftover content.
 *
 * HONEST LIMITATION (also shown in the UI): on modern flash storage with
 * F2FS/ext4 journaling, wear leveling and TRIM, no userspace tool can
 * guarantee that every physical NAND cell is overwritten. This deletes the
 * file's data blocks as reliably as the filesystem allows - which is the same
 * guarantee class as every other shredder app on Android.
 */
class ShredEngine(
    private val context: Context,
    private val container: AppContainer
) {

    suspend fun shredFile(file: File, passes: Int = PASSES): ShredResult =
        withContext(Dispatchers.IO) {
            if (!file.exists() || !file.isFile) {
                return@withContext ShredResult(false, file.path, 0, "not a file")
            }
            val len = file.length()
            if (len == 0L) {
                file.delete()
                return@withContext ShredResult(true, file.path, 0)
            }
            val buf = ByteArray(BUFFER)
            val random = SecureRandom()
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    for (pass in 1..passes) {
                        when (pass) {
                            1 -> Arrays.fill(buf, 0)
                            2 -> Arrays.fill(buf, 0xFF.toByte())
                            else -> random.nextBytes(buf)
                        }
                        raf.seek(0)
                        var written = 0L
                        while (written < len) {
                            val chunk = minOf(BUFFER.toLong(), len - written).toInt()
                            raf.write(buf, 0, chunk)
                            written += chunk
                            if (written and CHECK_MASK == 0L) {
                                container.updateOp(progress = written.toFloat() / len)
                                coroutineContext.ensureActive()
                            }
                        }
                        raf.fd.sync()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return@withContext ShredResult(false, file.path, 0, e.message)
            }
            // Break the name->content linkage, then unlink.
            val renamed = File(file.parentFile, ".shred-" + UUID.randomUUID().toString().take(8))
            if (file.renameTo(renamed)) renamed.delete() else file.delete()
            ShredResult(true, file.path, len)
        }

    /** Shreds every file inside [dir] (recursively), then removes the directory. */
    suspend fun shredDirectory(
        dir: File,
        passes: Int = PASSES,
        onFile: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ShredResult> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        suspend fun collect(d: File, depth: Int) {
            if (depth > 8) return
            val children = d.listFiles() ?: return
            for (c in children) {
                coroutineContext.ensureActive()
                if (c.isDirectory) collect(c, depth + 1) else if (c.isFile) files.add(c)
            }
        }
        collect(dir, 0)
        val results = mutableListOf<ShredResult>()
        var done = 0
        for (f in files) {
            coroutineContext.ensureActive()
            results.add(shredFile(f, passes))
            done++
            onFile(done, files.size)
        }
        dir.delete()
        results
    }

    /**
     * Overwrites all currently free space on the internal volume by filling
     * the app's own cache directory with random data until the disk is full,
     * then deleting the filler files. Single pass - this is a "flush residual
     * blocks" operation, not a per-file shred.
     */
    suspend fun wipeFreeSpace(
        onProgress: (filled: Long, estimate: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir ?: return@withContext 0L
        val estimate = try {
            StatFs(dir.path).availableBytes
        } catch (_: Exception) {
            0L
        }
        val buf = ByteArray(BUFFER)
        SecureRandom().nextBytes(buf)
        var filled = 0L
        var index = 0
        var consecutiveFailures = 0
        try {
            while (consecutiveFailures < 3 && index < 2048) {
                coroutineContext.ensureActive()
                val f = File(dir, "wipe-$index")
                try {
                    FileOutputStream(f).use { out ->
                        while (true) {
                            out.write(buf, 0, BUFFER)
                            filled += BUFFER
                            if (filled and CHECK_MASK == 0L) {
                                onProgress(filled, estimate)
                                container.updateOp(
                                    progress = if (estimate > 0) (filled.toFloat() / estimate).coerceAtMost(1f) else -1f
                                )
                            }
                        }
                    }
                } catch (e: IOException) {
                    consecutiveFailures++
                } finally {
                    try {
                        f.delete()
                    } catch (_: Exception) {
                    }
                }
                index++
            }
        } catch (e: CancellationException) {
            // Cancelled: fall through to cleanup below.
        }
        // Cleanup: delete every filler file, even after cancellation.
        dir.listFiles { f -> f.name.startsWith("wipe-") }?.forEach { it.delete() }
        filled
    }

    private companion object {
        const val PASSES = 3
        const val BUFFER = 1024 * 1024
        const val CHECK_MASK = 64L * 1024 * 1024 - 1
    }
}
