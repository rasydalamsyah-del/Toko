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

data class Sale(
    val id: Long,
    val time: Long,
    val total: Long,
    val paid: Long,
    val change: Long,
    val itemCount: Int,
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
    val sales: List<Sale>,
    val top: List<TopItem>,
) {
    val count: Int get() = sales.size

    companion object {
        val EMPTY = Report(0, 0, 0, emptyList(), emptyList())
    }
}
