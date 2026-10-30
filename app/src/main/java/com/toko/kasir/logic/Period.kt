package com.toko.kasir.logic

import java.util.Calendar

enum class Period(val label: String, private val days: Int?) {
    TODAY("Hari ini", 1),
    WEEK("7 hari", 7),
    MONTH("30 hari", 30),
    ALL("Semua", null);

    /** Waktu mulai (milidetik) periode ini, dihitung dari awal hari. */
    fun start(now: Long = System.currentTimeMillis()): Long {
        val d = days ?: return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(d - 1))
        }
        return cal.timeInMillis
    }
}
