package com.toko.kasir.logic

import com.toko.kasir.data.CartItem
import com.toko.kasir.data.Product
import com.toko.kasir.data.SaleUnit
import kotlin.math.floor
import kotlin.math.roundToLong

sealed class CartResult {
    data class Ok(val items: List<CartItem>) : CartResult()
    /** [available] dalam satuan dasar barang. */
    data class StokKurang(val product: Product, val available: Int) : CartResult()
}

/** Logika keranjang murni (tanpa Android) supaya mudah diuji. */
object CartLogic {

    /** Jumlah satuan dasar barang ini yang sudah terpakai di keranjang. */
    fun baseUsed(items: List<CartItem>, productId: Long, excludingKey: String? = null): Int =
        items.filter { it.product.id == productId && it.key != excludingKey }.sumOf { it.baseQty }

    fun add(items: List<CartItem>, product: Product, unit: SaleUnit, qty: Int = 1): CartResult {
        val key = CartItem.keyOf(product.id, unit.id)
        val existing = items.find { it.key == key }
        val newQty = (existing?.qty ?: 0) + qty
        val usedElsewhere = baseUsed(items, product.id, key)
        if (usedElsewhere + newQty * unit.factor > product.stock) {
            val alreadyInLine = (existing?.qty ?: 0) * unit.factor
            return CartResult.StokKurang(
                product, (product.stock - usedElsewhere - alreadyInLine).coerceAtLeast(0)
            )
        }
        // Barang yang baru discan selalu naik ke paling atas supaya langsung terlihat.
        return CartResult.Ok(listOf(CartItem(product, unit, newQty)) + items.filterNot { it.key == key })
    }

    /** Mengatur jumlah; jumlah 0 atau kurang menghapus baris dari keranjang. */
    fun setQty(items: List<CartItem>, key: String, qty: Int): CartResult {
        val line = items.find { it.key == key } ?: return CartResult.Ok(items)
        if (qty <= 0) return CartResult.Ok(items.filterNot { it.key == key })
        val usedElsewhere = baseUsed(items, line.product.id, key)
        if (usedElsewhere + qty * line.unit.factor > line.product.stock) {
            return CartResult.StokKurang(line.product, (line.product.stock - usedElsewhere).coerceAtLeast(0))
        }
        return CartResult.Ok(items.map { if (it.key == key) it.copy(qty = qty) else it })
    }

    /** Ganti satuan sebuah baris (mis. bungkus -> batang), jumlah tetap. */
    fun changeUnit(items: List<CartItem>, key: String, unit: SaleUnit): CartResult {
        val line = items.find { it.key == key } ?: return CartResult.Ok(items)
        if (line.unit.id == unit.id) return CartResult.Ok(items)
        return add(items.filterNot { it.key == key }, line.product, unit, line.qty)
    }

    /**
     * Satuan khusus untuk "jual sesuai nominal", mis. minyak Rp7.000.
     * Harga per satuan dasar diambil dari satuan jual dengan isi terbesar (harga grosirnya).
     * Mengembalikan null bila nominal terlalu kecil.
     */
    fun nominalUnit(product: Product, nominal: Long, id: Long): SaleUnit? {
        val ref = product.saleUnits.filter { it.price > 0 }.maxByOrNull { it.factor } ?: return null
        val perBase = ref.price.toDouble() / ref.factor
        val baseQty = floor(nominal / perBase).toInt()
        if (baseQty <= 0 || nominal <= 0) return null
        val cost = (ref.cost.toDouble() / ref.factor * baseQty).roundToLong()
        return SaleUnit(
            id = id,
            name = "${Angka.format(baseQty)} ${product.unit}",
            factor = baseQty,
            price = nominal,
            cost = cost,
        )
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
