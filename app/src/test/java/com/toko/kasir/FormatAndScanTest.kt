package com.toko.kasir

import com.toko.kasir.logic.Period
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.logic.ScanDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndScanTest {

    @Test
    fun formatRupiah() {
        assertEquals("Rp0", Rupiah.format(0))
        assertEquals("Rp500", Rupiah.format(500))
        assertEquals("Rp1.000", Rupiah.format(1_000))
        assertEquals("Rp12.500", Rupiah.format(12_500))
        assertEquals("Rp1.234.567", Rupiah.format(1_234_567))
        assertEquals("-Rp2.000", Rupiah.format(-2_000))
    }

    @Test
    fun bacaAngkaRupiah() {
        assertEquals(12_500L, Rupiah.parse("Rp12.500"))
        assertEquals(0L, Rupiah.parse(""))
        assertEquals(50_000L, Rupiah.parse("50000"))
    }

    @Test
    fun barcodeSamaTidakMasukDuaKaliSelamaMasihTerlihat() {
        val d = ScanDebouncer(sameCodeGapMs = 1200, anyCodeGapMs = 500)
        assertTrue(d.accept("A", 0))
        // Kamera terus melihat barcode yang sama tiap ~100ms
        var t = 100L
        while (t < 5_000) {
            assertFalse(d.accept("A", t))
            t += 100
        }
        // Barcode dijauhkan > 1,2 detik lalu discan lagi: masuk lagi
        assertTrue(d.accept("A", t + 1_300))
    }

    @Test
    fun barcodeBerbedaDiterimaSetelahJedaSingkat() {
        val d = ScanDebouncer(sameCodeGapMs = 1200, anyCodeGapMs = 500)
        assertTrue(d.accept("A", 0))
        assertFalse(d.accept("B", 200))
        assertTrue(d.accept("B", 600))
    }

    @Test
    fun periodeHariIniDimulaiTengahMalam() {
        val now = System.currentTimeMillis()
        val start = Period.TODAY.start(now)
        assertTrue(start <= now)
        assertTrue(now - start < 24L * 60 * 60 * 1000)
        assertEquals(0L, Period.ALL.start(now))
        val week = Period.WEEK.start(now)
        assertTrue(start - week in (6L * 24 * 60 * 60 * 1000 - 3_600_000)..(6L * 24 * 60 * 60 * 1000 + 3_600_000))
    }
}
