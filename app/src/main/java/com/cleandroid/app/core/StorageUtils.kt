package com.cleandroid.app.core

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager

data class VolumeInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else usedBytes.toFloat() / totalBytes
}

object StorageUtils {

    /** Primary shared volume (/storage/emulated/0) - what users call "internal storage". */
    fun primaryVolume(): VolumeInfo = try {
        val dir = Environment.getExternalStorageDirectory()
        val stat = StatFs(dir.absolutePath)
        VolumeInfo(stat.totalBytes, stat.availableBytes)
    } catch (_: Exception) {
        VolumeInfo(0, 0)
    }

    /**
     * App + data + cache size of one package, via the public StorageStatsManager.
     * Requires the (special) Usage Access grant; returns -1 when unavailable.
     */
    fun packageSize(context: Context, packageName: String): Long = try {
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val s = stats.queryStatsForUid(StorageManager.UUID_DEFAULT, packageName)
        s.appBytes + s.dataBytes + s.cacheBytes
    } catch (_: Exception) {
        -1L
    }
}
