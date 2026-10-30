package com.cleandroid.app.data.bloat

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cleandroid.app.R
import com.cleandroid.app.core.Permissions
import com.cleandroid.app.core.StorageUtils
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects known bloatware by matching installed package names against the
 * bundled, fully offline database (assets/bloatware_db.json).
 *
 * Removal strategy (no-root):
 *  - user apps / updated system apps -> system uninstall dialog (REQUEST_DELETE_PACKAGES)
 *  - pure system apps                -> App Info screen, where Android offers "Disable"
 */
class BloatRepository(
    private val context: Context,
    private val container: AppContainer
) {

    private val db = ConcurrentHashMap<String, BloatDbEntry>()

    @Volatile
    var lastResult: BloatScanResult? = null
        private set

    val dbSize: Int get() = db.size

    fun loadDatabase() {
        if (db.isNotEmpty()) return
        try {
            context.assets.open(DB_ASSET).use { ins ->
                val root = JSONObject(ins.readBytes().decodeToString())
                val apps = root.getJSONArray("apps")
                for (i in 0 until apps.length()) {
                    val o = apps.getJSONObject(i)
                    val pkg = o.optString("p")
                    if (pkg.isEmpty()) continue
                    db[pkg] = BloatDbEntry(
                        category = o.optString("c", "oem"),
                        name = o.optString("n", pkg),
                        risk = o.optString("r", "medium"),
                        note = o.optString("note")
                    )
                }
            }
        } catch (_: Exception) {
            // Database unreadable -> scan returns empty; heuristics screen still works.
        }
    }

    fun categoryLabel(category: String): String = when (category) {
        "ads" -> context.getString(R.string.cat_ads)
        "analytics" -> context.getString(R.string.cat_analytics)
        "oem" -> context.getString(R.string.cat_oem)
        "carrier" -> context.getString(R.string.cat_carrier)
        else -> context.getString(R.string.cat_optional)
    }

    suspend fun scan(onItem: (BloatApp) -> Unit): BloatScanResult =
        withContext(Dispatchers.Default) {
            loadDatabase()
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val packages: List<PackageInfo> = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(flags)
                }
            } catch (_: Exception) {
                emptyList()
            }

            val showSizes = Permissions.hasUsageAccess(context)
            var flagged = 0
            var reclaimable = 0L
            var i = 0

            for (pkg in packages) {
                i++
                coroutineContext.ensureActive()
                if (i % 10 == 0) {
                    container.updateOp(progress = i.toFloat() / packages.size)
                }
                val entry = db[pkg.packageName] ?: continue
                val appInfo = pkg.applicationInfo ?: continue
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }
                val isSystem =
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem =
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val size = if (showSizes) {
                    StorageUtils.packageSize(context, pkg.packageName)
                } else -1L
                if (size > 0) reclaimable += size
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                flagged++
                onItem(
                    BloatApp(
                        packageName = pkg.packageName,
                        label = label,
                        entry = entry,
                        isSystem = isSystem && !isUpdatedSystem,
                        canUninstall = !isSystem || isUpdatedSystem,
                        sizeBytes = size,
                        icon = icon
                    )
                )
            }

            BloatScanResult(flagged, packages.size, reclaimable).also { lastResult = it }
        }

    private companion object {
        const val DB_ASSET = "bloatware_db.json"
    }
}
