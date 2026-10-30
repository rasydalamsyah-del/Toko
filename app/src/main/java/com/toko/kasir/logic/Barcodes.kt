package com.toko.kasir.logic

object Barcodes {
    /**
     * Bentuk lain dari barcode yang sama. Kamera bisa membaca UPC-A sebagai 12 digit
     * ("711844160057") atau EAN-13 berawalan nol ("0711844160057"), dan data lama
     * sering kehilangan angka nol di depan.
     */
    fun variants(code: String): List<String> {
        val c = code.trim()
        if (c.isEmpty()) return emptyList()
        val out = linkedSetOf(c)
        if (c.all { it.isDigit() }) {
            if (c.length == 13 && c.startsWith("0")) out += c.substring(1)
            if (c.length == 12) out += "0$c"
            if (c.length == 11) out += "0$c"
            if (c.length == 7) out += "0$c"
        }
        return out.toList()
    }

    /** Cocokkan check digit GTIN (EAN-8, UPC-A, EAN-13, GTIN-14). */
    fun isValid(code: String): Boolean {
        if (code.length !in setOf(8, 12, 13, 14) || !code.all { it.isDigit() }) return false
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        return (10 - sum % 10) % 10 == digits.last()
    }

    /** Pecah teks berisi beberapa barcode ("a|b", "a, b") menjadi daftar. */
    fun split(text: String): List<String> =
        text.split('|', ',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
