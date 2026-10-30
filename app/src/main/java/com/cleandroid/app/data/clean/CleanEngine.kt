package com.cleandroid.app.data.clean

import android.content.Context
import android.os.Environment
import com.cleandroid.app.R
import com.cleandroid.app.core.Permissions
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage cleaner for public shared storage. Only touches well-known junk:
 *
 *  - DCIM/.thumbnails           (regenerable image thumbnails)
 *  - stray .apk installers      (outside Android/, i.e. leftovers)
 *  - .log / .tmp / .bak files   (small debug/temp artifacts)
 *  - empty directories          (deleted children-first, bottom-up)
 *  - old files in Downloads     (age threshold, always opt-in)
 *  - this app's own caches      (always safe)
 *
 * The Android/ directory tree is never touched, and nothing is deleted that
 * was not explicitly shown to the user first.
 */
class CleanEngine(
    private val context: Context,
    private val container: AppContainer
) {

    @Volatile
    var lastScanGroups: List<JunkGroup> = emptyList()
        private set

    @Volatile
    var lastFreedBytes: Long = 0
        private set

    @Volatile
    var lastScannedFiles: Int = 0
        private set

    suspend fun scan(onGroup: suspend (JunkGroup) -> Unit = {}): Long = withContext(Dispatchers.IO) {
        if (!Permissions.hasFullStorageAccess(context)) {
            lastScanGroups = emptyList()
            return@withContext 0L
        }
        val pub = Environment.getExternalStorageDirectory()
        val cutoff = System.currentTimeMillis() - OLD_FILE_DAYS * DAY_MS

        val thumbnails = mutableListOf<File>()
        val apks = mutableListOf<File>()
        val logs = mutableListOf<File>()
        val emptyDirs = mutableListOf<File>()
        val oldFiles = mutableListOf<File>()
        var visited = 0

        suspend fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH) return
            val children = dir.listFiles() ?: return
            for (f in children) {
                coroutineContext.ensureActive()
                visited++
                if (visited % 200 == 0) {
                    container.updateOp(progress = -1f)
                }
                if (f.isDirectory) {
                    if (SKIP_DIRS.contains(f.name)) continue
                    if (f.name == THUMBNAIL_DIR && f.parentFile?.name == "DCIM") {
                        f.listFiles()?.let { thumbs -> thumbnails.addAll(thumbs.filter { it.isFile }) }
                        continue
                    }
                    walk(f, depth + 1)
                    val kids = f.listFiles()
                    if (kids != null && kids.isEmpty()) emptyDirs.add(f)
                } else if (f.isFile) {
                    val name = f.name.lowercase()
                    when {
                        name.endsWith(".apk") -> apks.add(f)
                        LOG_EXTS.any { name.endsWith(it) } && f.length() < MAX_LOG_SIZE -> logs.add(f)
                    }
                    if (f.parentFile?.path == pub.path + "/" + Environment.DIRECTORY_DOWNLOADS ||
                        f.parentFile?.path?.startsWith(pub.path + "/" + Environment.DIRECTORY_DOWNLOADS + "/") == true
                    ) {
                        if (f.lastModified() < cutoff) oldFiles.add(f)
                    }
                }
            }
        }

        walk(pub, 0)

        // Own caches are always safe to clear.
        val ownCache = mutableListOf<File>()
        context.cacheDir?.listFiles()?.let { ownCache.addAll(it) }
        context.externalCacheDir?.listFiles()?.let { ownCache.addAll(it) }

        fun sizeOf(files: List<File>): Long = files.sumOf { if (it.isFile) it.length() else 0L }

        val groups = mutableListOf<JunkGroup>()
        suspend fun addGroup(g: JunkGroup) {
            groups.add(g)
            onGroup(g)
        }
        if (thumbnails.isNotEmpty()) addGroup(
            JunkGroup(
                GROUP_THUMBS, context.getString(R.string.clean_group_thumbs),
                context.getString(R.string.clean_group_thumbs_desc),
                thumbnails, sizeOf(thumbnails), true
            )
        )
        if (apks.isNotEmpty()) addGroup(
            JunkGroup(
                GROUP_APKS, context.getString(R.string.clean_group_apks),
                context.getString(R.string.clean_group_apks_desc),
                apks, sizeOf(apks), true
            )
        )
        if (logs.isNotEmpty()) addGroup(
            JunkGroup(
                GROUP_LOGS, context.getString(R.string.clean_group_logs),
                context.getString(R.string.clean_group_logs_desc),
                logs, sizeOf(logs), true
            )
        )
        if (emptyDirs.isNotEmpty()) {
            // Bottom-up order so children are removed before their parents.
            val ordered = emptyDirs.sortedByDescending { it.path.length }
            addGroup(
                JunkGroup(
                    GROUP_EMPTY, context.getString(R.string.clean_group_empty),
                    context.getString(R.string.clean_group_empty_desc),
                    ordered, 0L, true
                )
            )
        }
        if (oldFiles.isNotEmpty()) {
            val capped = oldFiles.sortedBy { it.lastModified() }.take(500)
            addGroup(
                JunkGroup(
                    GROUP_OLD, context.getString(R.string.clean_group_old),
                    context.getString(R.string.clean_group_old_desc),
                    capped, sizeOf(capped), false
                )
            )
        }
        if (ownCache.isNotEmpty()) addGroup(
            JunkGroup(
                GROUP_CACHE, context.getString(R.string.clean_group_cache),
                context.getString(R.string.clean_group_cache_desc),
                ownCache, sizeOf(ownCache), true
            )
        )

        lastScanGroups = groups.toList()
        lastScannedFiles = visited
        groups.sumOf { it.sizeBytes }
    }

    /** Deletes the selected groups. Returns bytes actually freed. */
    suspend fun clean(
        groups: List<JunkGroup>,
        onProgress: (freed: Long, total: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val total = groups.sumOf { it.sizeBytes }

        for (group in groups) {
            for (f in group.files) {
                coroutineContext.ensureActive()
                try {
                    val len = if (f.isFile) f.length() else 0L
                    if (f.isFile && f.delete()) freed += len
                    // Directories: delete() only succeeds when empty -> safe.
                    if (f.isDirectory) f.delete()
                } catch (_: Exception) {
                    // Read-only or busy file; skip it.
                }
            }
            container.updateOp(progress = if (total > 0) freed.toFloat() / total else -1f)
        }

        // Own cache group may contain directories with children -> recursive, self-owned.
        groups.filter { it.id == GROUP_CACHE }.forEach { g ->
            g.files.forEach { f ->
                try {
                    if (f.isDirectory) {
                        val before = dirSize(f)
                        if (f.deleteRecursively()) freed += before
                    }
                } catch (_: Exception) {
                }
            }
        }

        lastFreedBytes = freed
        lastScanGroups = emptyList()
        freed
    }

    private fun dirSize(dir: File): Long = try {
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    companion object {
        const val GROUP_THUMBS = "thumbs"
        const val GROUP_APKS = "apks"
        const val GROUP_LOGS = "logs"
        const val GROUP_EMPTY = "empty"
        const val GROUP_OLD = "old"
        const val GROUP_CACHE = "cache"

        const val OLD_FILE_DAYS = 90
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_DEPTH = 6
        private const val MAX_LOG_SIZE = 50L * 1024 * 1024
        private val LOG_EXTS = listOf(".log", ".tmp", ".bak")
        private const val THUMBNAIL_DIR = ".thumbnails"
        private val SKIP_DIRS = setOf("Android", "LOST.DIR")
    }
}
