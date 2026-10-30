package com.cleandroid.app.data.bloat

import android.graphics.drawable.Drawable

/** One entry of the bundled bloatware database (assets/bloatware_db.json). */
data class BloatDbEntry(
    val category: String, // ads | analytics | oem | carrier | optional
    val name: String,
    val risk: String,     // high | medium | low
    val note: String
)

/** One flagged app on the device. */
data class BloatApp(
    val packageName: String,
    val label: String,
    val entry: BloatDbEntry,
    val isSystem: Boolean,
    /** true when the system uninstall dialog can actually remove it (user app / updated system app). */
    val canUninstall: Boolean,
    /** -1 when Usage Access is not granted. */
    val sizeBytes: Long,
    val icon: Drawable?
)

data class BloatScanResult(
    val flaggedCount: Int,
    val totalScanned: Int,
    val reclaimableBytes: Long
)
