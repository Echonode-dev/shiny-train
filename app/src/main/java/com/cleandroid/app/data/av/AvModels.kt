package com.cleandroid.app.data.av

data class AvSignature(
    val sha256: String,
    val name: String,
    /** 1 = medium, 2 = high */
    val severity: Int,
    val note: String
)

/** One detection: either an app ([packageName]) or a file ([filePath]). */
data class Threat(
    val title: String,
    /** 1 = medium, 2 = high */
    val severity: Int,
    val reasons: List<String>,
    val packageName: String?,
    val filePath: String?,
    val appName: String? = null
)
