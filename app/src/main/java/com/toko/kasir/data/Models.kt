package com.toko.kasir.data

import com.toko.kasir.logic.Angka

/**
 * Satuan jual. [factor] = isi dalam satuan dasar barang
 * (mis. bungkus = 16 batang, ¼ kg = 250 gram).
 * id 0 = satuan dasar, id negatif = baris "sesuai nominal" di keranjang.
 */
data class SaleUnit(
    val id: Long,
    val name: String,
    val factor: Int,
    val price: Long,
    val cost: Long = 0,
    val barcode: String = "",
)

data class Product(
    val id: Long = 0,
    val barcode: String = "",
    val name: String,
    /** Harga per satuan dasar; 0 = tidak dijual per satuan dasar (mis. per gram). */
    val price: Long,
    val cost: Long = 0,
    /** Stok dalam satuan dasar. */
    val stock: Int = 0,
    /** Nama satuan dasar: pcs, batang, gram, ml, ... */
    val unit: String = "pcs",
    val units: List<SaleUnit> = emptyList(),
    val allowNominal: Boolean = false,
) {
    val baseUnit: SaleUnit get() = SaleUnit(0, unit, 1, price, cost, barcode)

    /** Semua satuan yang bisa dijual, dari isi terkecil. */
    val saleUnits: List<SaleUnit>
        get() = (if (price > 0) listOf(baseUnit) else emptyList()) + units.sortedBy { it.factor }

    /** Perlu pilih satuan di kasir (lebih dari satu satuan atau bisa jual nominal). */
    val needsUnitChoice: Boolean get() = saleUnits.size > 1 || allowNominal

    /** Satuan terkecil yang dijual, dipakai sebagai langkah tombol +/− di Stok. */
    val quickStep: Int get() = saleUnits.firstOrNull()?.factor ?: 1

    /** Batas stok menipis: 5 kali satuan jual terkecil. */
    val isLow: Boolean get() = stock in 1..(5 * quickStep)

    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) ||
            barcode.contains(q, ignoreCase = true) ||
            units.any { it.barcode.isNotEmpty() && it.barcode.contains(q, ignoreCase = true) }
    }

    /** Mis. 141 batang dengan bungkus isi 16 -> "8 bungkus + 13 batang". */
    fun stockText(qty: Int = stock): String {
        val base = "${Angka.format(qty)} $unit"
        val big = units.filter { it.factor > 1 && it.factor <= qty }.maxByOrNull { it.factor }
            ?: return base
        val n = qty / big.factor
        val rest = qty % big.factor
        val bigText = if (big.name.firstOrNull()?.isLetter() == true) "$n ${big.name}" else "$n × ${big.name}"
        return if (rest == 0) bigText else "$bigText + ${Angka.format(rest)} $unit"
    }

    /** Teks harga singkat untuk daftar, mis. "Rp2.000/batang · Rp30.000/bungkus". */
    fun priceText(): String =
        saleUnits.joinToString(" · ") { "${com.toko.kasir.logic.Rupiah.format(it.price)}/${it.name}" }
}

data class CartItem(val product: Product, val unit: SaleUnit, val qty: Int) {
    val key: String get() = keyOf(product.id, unit.id)
    val subtotal: Long get() = unit.price * qty
    val baseQty: Int get() = unit.factor * qty
    val isNominal: Boolean get() = unit.id < 0

    companion object {
        fun keyOf(productId: Long, unitId: Long) = "$productId:$unitId"
    }
}

enum class PayMethod(val label: String) {
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer");

    companion object {
        fun of(name: String?): PayMethod = entries.firstOrNull { it.name == name } ?: TUNAI
    }
}

data class Sale(
    val id: Long,
    val time: Long,
    val total: Long,
    val paid: Long,
    val change: Long,
    val itemCount: Int,
    val method: PayMethod = PayMethod.TUNAI,
    val canceled: Boolean = false,
)

data class SaleItem(
    val name: String,
    val price: Long,
    val qty: Int,
    val unitName: String = "",
) {
    val subtotal: Long get() = price * qty
    val label: String get() = if (unitName.isBlank()) name else "$name ($unitName)"
}

data class TopItem(val name: String, val qty: Int, val unit: String = "")

data class Report(
    val omzet: Long,
    val laba: Long,
    val itemsSold: Int,
    val count: Int,
    val sales: List<Sale>,
    val top: List<TopItem>,
    val byMethod: List<Pair<PayMethod, Long>>,
) {
    companion object {
        val EMPTY = Report(0, 0, 0, 0, emptyList(), emptyList(), emptyList())
    }
}

data class StoreInfo(
    val name: String = "Toko Saya",
    val address: String = "",
    val phone: String = "",
    val footer: String = "Terima kasih, selamat belanja kembali",
)
