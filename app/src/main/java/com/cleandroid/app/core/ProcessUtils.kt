package com.cleandroid.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val low: Boolean
) {
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f
        else (1f - availableBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * RAM utilities. Only public, non-root APIs are used:
 *  - ActivityManager.getMemoryInfo() for usage display
 *  - ActivityManager.killBackgroundProcesses() (normal permission) to ask
 *    the system to stop background processes of *user* apps.
 *
 * Note: since Android 5 the system only ever exposes this cooperative API;
 * force-stopping other apps is intentionally impossible without root.
 */
class ProcessUtils(private val context: Context) {

    fun ramInfo(): RamInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    /** @return number of third-party apps the system was asked to stop. */
    suspend fun killBackgroundProcesses(): Int = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val self = context.packageName
        val packages: List<android.content.pm.PackageInfo> = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        } catch (_: Exception) {
            emptyList()
        }
        var asked = 0
        for (pkg in packages) {
            val app = pkg.applicationInfo ?: continue
            val name = app.packageName
            if (name == self) continue
            val isPureSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isPureSystem) continue
            try {
                am.killBackgroundProcesses(name)
                asked++
            } catch (_: SecurityException) {
                // Protected app; skip.
            }
        }
        asked
    }
}
