package com.cleandroid.app.data.clean

import java.io.File

/** One category of junk found on the device. */
data class JunkGroup(
    val id: String,
    val title: String,
    val desc: String,
    val files: List<File>,
    val sizeBytes: Long,
    val defaultChecked: Boolean
)
