package com.toko.kasir.logic

import com.toko.kasir.data.CartItem
import com.toko.kasir.data.Product

sealed class CartResult {
    data class Ok(val items: List<CartItem>) : CartResult()
    data class StokKurang(val product: Product, val available: Int) : CartResult()
}

/** Logika keranjang murni (tanpa Android) supaya mudah diuji. */
object CartLogic {

    fun add(items: List<CartItem>, product: Product, qty: Int = 1): CartResult {
        val existing = items.find { it.product.id == product.id }
        val newQty = (existing?.qty ?: 0) + qty
        if (newQty > product.stock) return CartResult.StokKurang(product, product.stock)
        // Barang yang baru discan selalu naik ke paling atas supaya langsung terlihat.
        val updated = listOf(CartItem(product, newQty)) + items.filterNot { it.product.id == product.id }
        return CartResult.Ok(updated)
    }

    /** Mengatur jumlah; jumlah 0 atau kurang menghapus barang dari keranjang. */
    fun setQty(items: List<CartItem>, productId: Long, qty: Int): CartResult {
        val existing = items.find { it.product.id == productId } ?: return CartResult.Ok(items)
        if (qty <= 0) return CartResult.Ok(items.filterNot { it.product.id == productId })
        if (qty > existing.product.stock) {
            return CartResult.StokKurang(existing.product, existing.product.stock)
        }
        return CartResult.Ok(items.map { if (it.product.id == productId) it.copy(qty = qty) else it })
    }

    fun total(items: List<CartItem>): Long = items.sumOf { it.subtotal }

    fun totalQty(items: List<CartItem>): Int = items.sumOf { it.qty }

    /** Kembalian, atau null bila uang kurang. */
    fun change(total: Long, paid: Long): Long? = if (paid < total) null else paid - total

    /** Usulan nominal uang tunai yang umum, lebih besar dari total. */
    fun cashSuggestions(total: Long): List<Long> {
        if (total <= 0) return emptyList()
        val steps = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
        return steps.map { s -> ((total + s - 1) / s) * s }
            .filter { it > total }
            .distinct()
            .sorted()
            .take(4)
    }
}
