package com.toko.kasir.logic

/**
 * Kamera membaca barcode yang sama berkali-kali per detik. Kelas ini memastikan
 * satu barang hanya masuk sekali selama barcode masih terlihat kamera.
 * Barcode yang sama baru diterima lagi setelah hilang dari kamera minimal
 * [sameCodeGapMs]; barcode berbeda diterima setelah [anyCodeGapMs].
 */
class ScanDebouncer(
    private val sameCodeGapMs: Long = 1200,
    private val anyCodeGapMs: Long = 500,
) {
    private var lastCode: String? = null
    private var lastSeen = Long.MIN_VALUE / 2
    private var lastAccepted = Long.MIN_VALUE / 2

    @Synchronized
    fun accept(code: String, now: Long): Boolean {
        if (code == lastCode) {
            val gap = now - lastSeen
            lastSeen = now
            if (gap >= sameCodeGapMs) {
                lastAccepted = now
                return true
            }
            return false
        }
        if (now - lastAccepted < anyCodeGapMs) return false
        lastCode = code
        lastSeen = now
        lastAccepted = now
        return true
    }
}
