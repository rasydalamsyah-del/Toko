package com.toko.kasir

import com.toko.kasir.data.PayMethod
import com.toko.kasir.data.Sale
import com.toko.kasir.data.SaleItem
import com.toko.kasir.data.StoreInfo
import com.toko.kasir.logic.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTest {

    private val store = StoreInfo(
        name = "Toko Berkah Jaya",
        address = "Jl. Merdeka No. 10, Kelurahan Sukamaju, Kecamatan Sukasari",
        phone = "0812-3456-7890",
        footer = "Terima kasih, selamat belanja kembali",
    )
    private val items = listOf(
        SaleItem("Indomie Goreng Rasa Rendang Spesial Extra Pedas Jumbo", 3_500, 3),
        SaleItem("Aqua 600ml", 4_000, 1),
        SaleItem("Rokok X", 30_000, 2, "bungkus"),
    )

    @Test
    fun semuaBarisMuatDiKertas58mm() {
        val sale = Sale(12, 0L, 74_500, 100_000, 25_500, 6, PayMethod.TUNAI)
        val text = Receipt.build(store, sale, items)
        text.lines().forEach { line ->
            assertTrue("Baris terlalu panjang (${line.length}): '$line'", line.length <= 32)
        }
        assertTrue(text.contains("TOTAL"))
        assertTrue(text.contains("Rp74.500"))
        assertTrue(text.contains("Kembali"))
        assertTrue(text.contains("Rp25.500"))
        assertTrue(text.contains("10.500"))
        assertTrue(text.contains("Rokok X (bungkus)"))
        assertTrue(text.contains("60.000"))
    }

    @Test
    fun nonTunaiTanpaKembalian() {
        val sale = Sale(13, 0L, 14_500, 14_500, 0, 4, PayMethod.QRIS)
        val text = Receipt.build(store, sale, items)
        assertTrue(text.contains("Bayar (QRIS)"))
        assertFalse(text.contains("Kembali"))
    }

    @Test
    fun strukBatalDitandai() {
        val sale = Sale(14, 0L, 14_500, 14_500, 0, 4, PayMethod.TRANSFER, canceled = true)
        assertTrue(Receipt.build(store, sale, items).contains("DIBATALKAN"))
    }

    @Test
    fun pemotonganKata() {
        assertEquals(listOf("abc def", "ghi"), Receipt.wrap("abc def ghi", 7))
        assertEquals(listOf("abcdefg", "hij"), Receipt.wrap("abcdefghij", 7))
        assertEquals(emptyList<String>(), Receipt.wrap("   ", 7))
    }

    @Test
    fun perintahPrinterEscPos() {
        val bytes = Receipt.escPos("Halo")
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
        assertEquals('H'.code.toByte(), bytes[2])
        // diakhiri perintah potong kertas GS V
        assertEquals(0x1D.toByte(), bytes[bytes.size - 4])
        assertEquals(0x56.toByte(), bytes[bytes.size - 3])
    }

    @Test
    fun metodeBayarDariTeks() {
        assertEquals(PayMethod.QRIS, PayMethod.of("QRIS"))
        assertEquals(PayMethod.TUNAI, PayMethod.of(null))
        assertEquals(PayMethod.TUNAI, PayMethod.of("lain"))
    }
}
