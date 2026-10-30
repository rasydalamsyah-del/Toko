package com.toko.kasir.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Rupiah {
    fun format(value: Long): String {
        val digits = kotlin.math.abs(value).toString()
        val sb = StringBuilder()
        digits.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return (if (value < 0) "-Rp" else "Rp") + sb.reverse().toString()
    }

    /** Mengambil angka saja dari teks, mis. "Rp12.500" -> 12500. */
    fun parse(text: String): Long =
        text.filter { it.isDigit() }.take(15).toLongOrNull() ?: 0L

    /** Format angka tanpa "Rp" untuk kolom input. */
    fun plain(value: Long): String = format(value).removePrefix("Rp")
}

object Angka {
    /** Angka dengan titik ribuan, mis. 2350 -> "2.350". */
    fun format(value: Int): String = Rupiah.plain(value.toLong())
}

object Waktu {
    private val locale: Locale = Locale.forLanguageTag("id-ID")

    fun lengkap(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(ms))

    fun jam(ms: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(ms))

    fun struk(ms: Long): String = SimpleDateFormat("dd/MM/yy HH:mm", locale).format(Date(ms))

    fun file(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", locale).format(Date(ms))
}
