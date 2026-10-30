package com.cleandroid.app.data.shred

data class ShredResult(
    val success: Boolean,
    val path: String,
    val bytes: Long,
    val error: String? = null
)
