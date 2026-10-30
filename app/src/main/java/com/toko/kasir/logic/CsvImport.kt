package com.toko.kasir.logic

import kotlin.math.roundToLong

/** Satu baris barang hasil baca file impor. */
data class ImportRow(
    val name: String,
    val barcodes: List<String>,
    val price: Long,
    val cost: Long,
    val stock: Int,
    val unit: String,
    val group: String,
) {
    val barcode: String get() = barcodes.firstOrNull() ?: ""
}

class ImportException(message: String) : Exception(message)

/**
 * Membaca daftar barang dari CSV. Kolom dikenali dari judulnya, jadi bisa membaca
 * format aplikasi ini (nama;barcode;harga;...) maupun ekspor aplikasi kasir lain
 * (Name,ProductGroup,SKU,Barcode,...,Cost,...,Price,...). Pemisah ; , atau tab.
 */
object CsvImport {

    private val aliases = mapOf(
        "name" to listOf("nama", "nama barang", "name", "item", "produk", "product"),
        "barcode" to listOf("barcode", "kode barcode", "kode", "ean"),
        "price" to listOf("harga", "harga jual", "price", "harga_jual"),
        "cost" to listOf("modal", "harga modal", "harga beli", "cost", "harga_modal"),
        "stock" to listOf("stok", "stock", "quantity", "qty", "jumlah"),
        "unit" to listOf("satuan", "unit", "measurementunit", "measurement unit"),
        "group" to listOf("kategori", "category", "group", "productgroup", "product group"),
    )

    fun parse(text: String): List<ImportRow> {
        val lines = text.removePrefix("﻿").lines().toMutableList()
        if (lines.firstOrNull()?.trim()?.startsWith("sep=", ignoreCase = true) == true) lines.removeAt(0)
        val headerLine = lines.firstOrNull { it.isNotBlank() } ?: throw ImportException("File kosong")
        val delimiter = listOf(';', '\t', ',').maxByOrNull { d -> headerLine.count { it == d } } ?: ','

        val records = splitRecords(lines.joinToString("\n"), delimiter).filter { r -> r.any { it.isNotBlank() } }
        if (records.isEmpty()) throw ImportException("File kosong")
        val header = records.first().map { it.trim().lowercase() }
        val col = aliases.mapValues { (_, names) -> header.indexOfFirst { it in names } }
        if (col.getValue("name") < 0) throw ImportException("Kolom nama barang tidak ditemukan")
        if (col.getValue("price") < 0) throw ImportException("Kolom harga tidak ditemukan")

        fun get(r: List<String>, key: String): String {
            val i = col.getValue(key)
            return if (i in r.indices) r[i].trim() else ""
        }

        return records.drop(1).mapNotNull { r ->
            val name = get(r, "name").replace(Regex("\\s+"), " ")
            if (name.isEmpty()) return@mapNotNull null
            ImportRow(
                name = name,
                barcodes = Barcodes.split(get(r, "barcode").replace(" ", "")),
                price = number(get(r, "price")),
                cost = number(get(r, "cost")),
                stock = number(get(r, "stock")).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                unit = get(r, "unit").lowercase().ifEmpty { "pcs" },
                group = get(r, "group"),
            )
        }
    }

    /**
     * Angka dari teks: "12500.0" (titik desimal), "12.500" atau "Rp12.500" (titik ribuan),
     * "829.166666" (desimal panjang). Negatif dianggap 0.
     */
    fun number(text: String): Long {
        val t = text.replace("Rp", "", ignoreCase = true).replace(" ", "").trim()
        if (t.isEmpty() || t.startsWith("-")) return 0
        val single = Regex("^(\\d+)[.,](\\d+)$").find(t)
        if (single != null && single.groupValues[2].length != 3) {
            return t.replace(',', '.').toDoubleOrNull()?.roundToLong() ?: 0
        }
        return t.filter { it.isDigit() }.take(15).toLongOrNull() ?: 0
    }

    /** Pemisah CSV sederhana yang mendukung tanda kutip dan baris baru di dalam kutip. */
    fun splitRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else when {
                c == '"' && field.isEmpty() -> inQuotes = true
                c == delimiter -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> {
                    row.add(field.toString()); records.add(row)
                    field = StringBuilder(); row = mutableListOf()
                }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString()); records.add(row)
        }
        return records
    }
}
