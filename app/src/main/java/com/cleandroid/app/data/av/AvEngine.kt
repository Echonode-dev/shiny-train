package com.cleandroid.app.data.av

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cleandroid.app.R
import com.cleandroid.app.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 100% offline detection engine with two layers:
 *
 * 1. Signature layer - SHA-256 of installed APKs (and files in Downloads) is
 *    compared against assets/av_signatures.json. The bundled DB contains the
 *    EICAR anti-virus test signature so the pipeline can be verified, plus a
 *    documented format for importing more hashes (see SECURITY.md).
 *
 * 2. Heuristic layer - risky *combinations* of requested permissions that
 *    historically correlate with SMS fraud, overlay attacks and rogue
 *    sideloaders. Single permissions never trigger a detection.
 *
 * No network, no uploads, nothing leaves the device.
 */
class AvEngine(
    private val context: Context,
    private val container: AppContainer
) {

    private val sigByHash = HashMap<String, AvSignature>()

    @Volatile
    var lastThreats: List<Threat> = emptyList()
        private set

    val signatureCount: Int
        get() = sigByHash.size

    fun loadSignatures() {
        if (sigByHash.isNotEmpty()) return
        try {
            context.assets.open(SIG_ASSET).use { ins ->
                val root = JSONObject(ins.readBytes().decodeToString())
                val arr = root.getJSONArray("signatures")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val hash = o.optString("sha256").lowercase()
                    if (hash.length != 64) continue
                    sigByHash[hash] = AvSignature(
                        sha256 = hash,
                        name = o.optString("name", "Unknown threat"),
                        severity = if (o.optInt("severity", 2) >= 2) 2 else 1,
                        note = o.optString("note")
                    )
                }
            }
        } catch (_: Exception) {
            // Empty DB -> heuristics still active.
        }
    }

    /** Scan every installed app (self excluded). Returns all threats found. */
    suspend fun scanApps(onThreat: (Threat) -> Unit = {}): List<Threat> =
        withContext(Dispatchers.IO) {
            loadSignatures()
            val threats = mutableListOf<Threat>()
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS
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

            val accessibilityPkgs = packagesUsingAccessibility(pm)
            val adminPkgs = deviceAdminPackages(pm)
            val self = context.packageName
            val hashDbReady = sigByHash.isNotEmpty()
            var i = 0

            for (pkg in packages) {
                i++
                if (i % 5 == 0) {
                    yield()
                    container.updateOp(
                        progress = i.toFloat() / packages.size,
                        label = context.getString(R.string.av_progress_apps, i, packages.size)
                    )
                }
                val appInfo = pkg.applicationInfo ?: continue
                val name = appInfo.packageName
                if (name == self) continue
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    name
                }
                val perms = pkg.requestedPermissions?.toSet() ?: emptySet()

                // --- Layer 1: signature hash of the APK ---
                if (hashDbReady) {
                    val path = appInfo.sourceDir
                    if (path != null) {
                        val h = sha256(File(path)) ?: continue
                        val sig = sigByHash[h]
                        if (sig != null) {
                            val t = Threat(
                                title = sig.name,
                                severity = sig.severity,
                                reasons = listOf(context.getString(R.string.av_reason_signature, sig.name)),
                                packageName = name,
                                filePath = null,
                                appName = label
                            )
                            threats.add(t)
                            onThreat(t)
                            continue
                        }
                    }
                }

                // --- Layer 2: permission-combo heuristics ---
                val (score, reasons) = scoreHeuristics(
                    perms = perms,
                    usesAccessibility = accessibilityPkgs.contains(name),
                    isAdmin = adminPkgs.contains(name),
                    installer = installerOf(pm, name)
                )
                if (score >= 50) {
                    val t = Threat(label, 2, reasons, name, null, label)
                    threats.add(t)
                    onThreat(t)
                } else if (score >= 25) {
                    val t = Threat(label, 1, reasons, name, null, label)
                    threats.add(t)
                    onThreat(t)
                }
            }
            lastThreats = threats.toList()
            threats
        }

    /** Quick hash check of files under [root] (e.g. Downloads). */
    suspend fun scanFiles(root: File, maxFiles: Int = 3000): List<Threat> =
        withContext(Dispatchers.IO) {
            loadSignatures()
            val out = mutableListOf<Threat>()
            if (sigByHash.isEmpty()) return@withContext out
            var count = 0

            suspend fun walk(dir: File, depth: Int) {
                if (depth > 5 || count >= maxFiles) return
                val children = dir.listFiles() ?: return
                for (f in children) {
                    if (f.isDirectory) {
                        walk(f, depth + 1)
                    } else if (f.isFile && f.length() in 1..MAX_SCAN_FILE) {
                        count++
                        if (count % 20 == 0) {
                            yield()
                            container.updateOp()
                        }
                        val h = sha256(f) ?: continue
                        val sig = sigByHash[h] ?: continue
                        out.add(
                            Threat(
                                title = sig.name,
                                severity = sig.severity,
                                reasons = listOf(context.getString(R.string.av_reason_signature, sig.name)),
                                packageName = null,
                                filePath = f.path
                            )
                        )
                        if (count >= maxFiles) return
                    }
                }
            }
            walk(root, 0)
            out
        }

    // ------------------------------------------------------------------ //

    private fun packagesUsingAccessibility(pm: PackageManager): Set<String> = try {
        pm.queryIntentServices(Intent(AccessibilityService.SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo?.packageName }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun deviceAdminPackages(pm: PackageManager): Set<String> = try {
        val action = "android.app.action.DEVICE_ADMIN_ENABLED"
        pm.queryBroadcastReceivers(Intent(action), 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (_: Exception) {
        null
    }

    private fun scoreHeuristics(
        perms: Set<String>,
        usesAccessibility: Boolean,
        isAdmin: Boolean,
        installer: String?
    ): Pair<Int, List<String>> {
        var score = 0
        val reasons = mutableListOf<String>()
        fun add(points: Int, resId: Int) {
            score += points
            reasons.add(context.getString(resId))
        }

        val sms = perms.contains(Manifest.permission.RECEIVE_SMS) ||
            perms.contains(Manifest.permission.READ_SMS)
        val overlay = perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)
        val installs = perms.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES)
        val callLog = perms.contains(Manifest.permission.READ_CALL_LOG)
        val sendSms = perms.contains(Manifest.permission.SEND_SMS)
        val queryAll = perms.contains(Manifest.permission.QUERY_ALL_PACKAGES)
        val fineLocation = perms.contains(Manifest.permission.ACCESS_FINE_LOCATION)

        if (sms && overlay) add(45, R.string.av_reason_sms_overlay)
        if (usesAccessibility && (sms || callLog)) add(35, R.string.av_reason_access_sms)
        if (usesAccessibility && installs) add(25, R.string.av_reason_access_installer)
        if (isAdmin && usesAccessibility) add(30, R.string.av_reason_admin_access)
        if (installs && installer != null && installer != PLAY_STORE) {
            add(20, R.string.av_reason_sideload)
        }
        if (installs && installer == null) add(10, R.string.av_reason_sideload_unknown)
        if (callLog && sendSms) add(35, R.string.av_reason_calllog_sms)
        if (queryAll && sms && fineLocation) add(25, R.string.av_reason_query_sms)

        return score to reasons
    }

    private suspend fun sha256(file: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
                total += n
                if (total and (64L * 1024 * 1024 - 1) == 0L) {
                    yield()
                }
            }
        }
        md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    private companion object {
        const val SIG_ASSET = "av_signatures.json"
        const val PLAY_STORE = "com.android.vending"
        const val MAX_SCAN_FILE = 500L * 1024 * 1024
    }
}
