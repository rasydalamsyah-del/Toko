package com.toko.kasir.logic

import java.io.BufferedReader

/** Data barang dari katalog bawaan: nama-nama yang pernah dipakai untuk barcode ini, dan harga saran. */
data class KatalogItem(
    val barcode: String,
    val names: List<String>,
    val price: Long,
    val cost: Long,
)

/** Kamus barcode -> nama barang (±25 ribu barang), dipakai untuk isi otomatis saat input barang baru. */
class Katalog(private val map: Map<String, KatalogItem>) {

    val size: Int get() = map.size

    fun lookup(code: String): KatalogItem? =
        Barcodes.variants(code).firstNotNullOfOrNull { map[it] }

    companion object {
        val EMPTY = Katalog(emptyMap())

        /** Format per baris: barcode \t harga \t modal \t nama1|nama2 */
        fun parseLine(line: String): KatalogItem? {
            val p = line.split('\t')
            if (p.size < 4 || p[0].isBlank()) return null
            val names = p[3].split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (names.isEmpty()) return null
            return KatalogItem(
                barcode = p[0].trim(),
                names = names,
                price = p[1].toLongOrNull() ?: 0,
                cost = p[2].toLongOrNull() ?: 0,
            )
        }

        fun load(reader: BufferedReader): Katalog {
            val map = HashMap<String, KatalogItem>(32_000)
            reader.useLines { lines ->
                lines.forEach { line -> parseLine(line)?.let { map[it.barcode] = it } }
            }
            return Katalog(map)
        }
    }
}
