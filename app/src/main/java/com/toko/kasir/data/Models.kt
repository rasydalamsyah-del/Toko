package com.toko.kasir.data

data class Product(
    val id: Long = 0,
    val barcode: String = "",
    val name: String,
    val price: Long,
    val cost: Long = 0,
    val stock: Int = 0,
    val unit: String = "pcs",
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return name.contains(q, ignoreCase = true) || barcode.contains(q, ignoreCase = true)
    }
}

data class CartItem(val product: Product, val qty: Int) {
    val subtotal: Long get() = product.price * qty
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
) {
    val subtotal: Long get() = price * qty
}

data class TopItem(val name: String, val qty: Int)

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
