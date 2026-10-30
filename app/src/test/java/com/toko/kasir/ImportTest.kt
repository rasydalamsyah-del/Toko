package com.toko.kasir

import com.toko.kasir.logic.Barcodes
import com.toko.kasir.logic.CsvImport
import com.toko.kasir.logic.ImportException
import com.toko.kasir.logic.Katalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportTest {

    // ---------- Barcode ----------

    @Test
    fun variasiBarcodeNolDiDepan() {
        assertEquals(listOf("0711844160057", "711844160057"), Barcodes.variants("0711844160057"))
        assertEquals(listOf("711844160057", "0711844160057"), Barcodes.variants("711844160057"))
        assertEquals(listOf("D8992761002022"), Barcodes.variants("D8992761002022"))
        assertTrue(Barcodes.variants("  ").isEmpty())
    }

    @Test
    fun checkDigitBarcode() {
        assertTrue(Barcodes.isValid("8992761139018")) // Ades
        assertTrue(Barcodes.isValid("711844160057")) // UPC-A ABC
        assertTrue(Barcodes.isValid("89941225")) // EAN-8
        assertFalse(Barcodes.isValid("8992761139019"))
        assertFalse(Barcodes.isValid("ABC"))
    }

    @Test
    fun pecahBeberapaBarcode() {
        assertEquals(listOf("711844120402", "8994907001302"), Barcodes.split("711844120402|8994907001302"))
        assertEquals(listOf("a", "b"), Barcodes.split(" a , b, a "))
    }

    // ---------- CSV ----------

    @Test
    fun formatAplikasiIni() {
        val rows = CsvImport.parse(
            """
            nama;barcode;harga;modal;stok;satuan;kategori
            169 KRETEK 12;8997018920135;8500;7400;0;pcs;ROKOK
            ABC SAMBAL TERASI SCH 20GR;711844120402|8994907001302;1500;0;0;pcs;PRODUK
            ;123;1000;0;0;pcs;
            """.trimIndent()
        )
        assertEquals(2, rows.size) // baris tanpa nama dilewati
        assertEquals("169 KRETEK 12", rows[0].name)
        assertEquals(8_500L, rows[0].price)
        assertEquals(7_400L, rows[0].cost)
        assertEquals("ROKOK", rows[0].group)
        assertEquals(listOf("711844120402", "8994907001302"), rows[1].barcodes)
    }

    @Test
    fun formatEksporAplikasiKasirLain() {
        val rows = CsvImport.parse(
            """
            Name,ProductGroup,SKU,Barcode,MeasurementUnit,Cost,Markup,Price,Tax,IsTaxInclusivePrice,IsPriceChangeAllowed,IsUsingDefaultQuantity,IsService,IsEnabled,Description,Quantity,Supplier,ReorderPoint,PreferredQuantity,LowStockWarning,WarningQuantity
            "BAYGON 0,3 AE CITRUS FRESH
            200ML",PRODUK,10100156,8998899400341,PCS,0,0,16500.0,,0,0,1,0,1,,0,,,,,
            KOPIKAP CAPPUCCINO,PRODUK,10101285,8996001600153,PCS,829.166666666667,20.603,1000.0,,0,0,1,0,1,,48,PT. CIPTA NIAGA SEMESTA,0,0,0,0
            ABC PLUS GULA 18GR,PRODUK,10100035,8991002101265,PCS,0,0,500.0,,0,0,1,0,1,,-3,,,,,
            """.trimIndent()
        )
        assertEquals(3, rows.size)
        assertEquals("BAYGON 0,3 AE CITRUS FRESH 200ML", rows[0].name) // koma & baris baru dalam kutip
        assertEquals(16_500L, rows[0].price)
        assertEquals(829L, rows[1].cost)
        assertEquals(48, rows[1].stock)
        assertEquals(0, rows[2].stock) // stok minus jadi 0
        assertEquals("pcs", rows[2].unit)
    }

    @Test
    fun bacaAngkaDariBerbagaiFormat() {
        assertEquals(12_500L, CsvImport.number("12500.0"))
        assertEquals(12_500L, CsvImport.number("12.500"))
        assertEquals(12_500L, CsvImport.number("Rp12.500"))
        assertEquals(1_234_567L, CsvImport.number("1.234.567"))
        assertEquals(829L, CsvImport.number("829.166666666667"))
        assertEquals(0L, CsvImport.number("-3"))
        assertEquals(0L, CsvImport.number(""))
    }

    @Test(expected = ImportException::class)
    fun tanpaKolomHargaDitolak() {
        CsvImport.parse("nama;barcode\nIndomie;123")
    }

    @Test
    fun excelDenganSepDanBom() {
        val rows = CsvImport.parse("﻿sep=;\nNama;Harga Jual\nIndomie;3.500\n")
        assertEquals(1, rows.size)
        assertEquals(3_500L, rows[0].price)
        assertTrue(rows[0].barcodes.isEmpty())
    }

    // ---------- Katalog ----------

    @Test
    fun barisKatalog() {
        val k = Katalog.parseLine("8991002101630\t1500\t1000\tABC KOPI SUSU 31GR|ABC KOPI SUSU 32 GR SCT")!!
        assertEquals(listOf("ABC KOPI SUSU 31GR", "ABC KOPI SUSU 32 GR SCT"), k.names)
        assertEquals(1_500L, k.price)
        assertNull(Katalog.parseLine("rusak"))
    }

    // ---------- Aset yang ikut di APK ----------

    private fun asset(name: String): File {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        return candidates.first { it.exists() }
    }

    @Test
    fun katalogBawaanTerbacaDanCocokDenganScan() {
        val katalog = asset("katalog.tsv").bufferedReader().use { Katalog.load(it) }
        assertTrue("katalog terlalu sedikit: ${katalog.size}", katalog.size > 25_000)
        // Kamera bisa membaca UPC-A 12 digit atau EAN-13 berawalan 0: keduanya harus ketemu.
        val a = katalog.lookup("711844110021")
        val b = katalog.lookup("0711844110021")
        assertNotNull(a)
        assertEquals(a, b)
        assertEquals("ABC KECAP SASET", a!!.names.first())
        // Barang dari daftar harga punya harga saran
        assertEquals(8_500L, katalog.lookup("8997018920135")!!.price)
    }

    @Test
    fun daftarHargaBawaanTerbacaLengkap() {
        val rows = CsvImport.parse(asset("daftar-harga.csv").readText())
        assertEquals(2_892, rows.size)
        assertTrue(rows.count { it.price > 0 } >= 2_860)
        assertEquals(setOf("PRODUK", "ROKOK", "BBM", ""), rows.map { it.group }.toSet())
        assertTrue(rows.count { it.barcodes.size > 1 } >= 50)
    }
}
