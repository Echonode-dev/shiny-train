package com.cleandroid.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small formatting helpers (no heavyweight libraries). */
object Fmt {

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return if (i == 0) "$value B"
        else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun date(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
