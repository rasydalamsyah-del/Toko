package com.toko.kasir.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toko.kasir.data.CartItem
import com.toko.kasir.data.DbHelper
import com.toko.kasir.data.Product
import com.toko.kasir.data.Report
import com.toko.kasir.data.Repository
import com.toko.kasir.data.Sale
import com.toko.kasir.data.SaleItem
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.CartResult
import com.toko.kasir.logic.Period
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(val text: String, val id: Long = System.nanoTime())

class TokoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(DbHelper(app))

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var cart by mutableStateOf<List<CartItem>>(emptyList()); private set
    var message by mutableStateOf<UiMessage?>(null); private set
    /** Barcode hasil scan yang belum terdaftar, untuk ditawarkan ditambahkan. */
    var unknownBarcode by mutableStateOf<String?>(null); private set
    var lastSale by mutableStateOf<Sale?>(null); private set
    var period by mutableStateOf(Period.TODAY); private set
    var report by mutableStateOf(Report.EMPTY); private set

    init {
        refreshProducts()
        refreshReport()
    }

    private fun launchSafe(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            show(e.message ?: "Terjadi kesalahan")
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    fun show(text: String) {
        message = UiMessage(text)
    }

    fun clearMessage() {
        message = null
    }

    fun refreshProducts() = launchSafe {
        products = io { repo.allProducts() }
        // Sinkronkan data barang di keranjang (harga/stok terbaru).
        val byId = products.associateBy { it.id }
        cart = cart.mapNotNull { ci -> byId[ci.product.id]?.let { CartItem(it, ci.qty) } }
    }

    // ---------- Kasir ----------

    fun onBarcodeScanned(code: String) = launchSafe {
        val product = io { repo.findByBarcode(code) }
        if (product == null) unknownBarcode = code else addToCart(product)
    }

    fun dismissUnknownBarcode() {
        unknownBarcode = null
    }

    fun addToCart(product: Product) {
        when (val r = CartLogic.add(cart, product)) {
            is CartResult.Ok -> cart = r.items
            is CartResult.StokKurang ->
                show("Stok ${r.product.name} tidak cukup (sisa ${r.available})")
        }
    }

    fun setQty(productId: Long, qty: Int) {
        when (val r = CartLogic.setQty(cart, productId, qty)) {
            is CartResult.Ok -> cart = r.items
            is CartResult.StokKurang ->
                show("Stok ${r.product.name} hanya ${r.available}")
        }
    }

    fun clearCart() {
        cart = emptyList()
    }

    fun checkout(paid: Long, onDone: () -> Unit) = launchSafe {
        val sale = io { repo.checkout(cart, paid) }
        cart = emptyList()
        lastSale = sale
        onDone()
        refreshProducts()
        refreshReport()
    }

    fun dismissLastSale() {
        lastSale = null
    }

    // ---------- Barang & stok ----------

    fun saveProduct(p: Product, addToCartAfter: Boolean = false, onSuccess: () -> Unit) = launchSafe {
        val saved = io { repo.saveProduct(p) }
        onSuccess()
        products = io { repo.allProducts() }
        show(if (p.id == 0L) "${saved.name} ditambahkan" else "${saved.name} disimpan")
        if (addToCartAfter) addToCart(saved)
        refreshProducts()
    }

    fun deleteProduct(p: Product) = launchSafe {
        io { repo.deleteProduct(p.id) }
        cart = cart.filterNot { it.product.id == p.id }
        show("${p.name} dihapus")
        refreshProducts()
    }

    fun adjustStock(id: Long, delta: Int) = launchSafe {
        io { repo.adjustStock(id, delta) }
        refreshProducts()
    }

    fun setStock(id: Long, stock: Int) = launchSafe {
        io { repo.setStock(id, stock) }
        refreshProducts()
    }

    // ---------- Laporan ----------

    fun selectPeriod(p: Period) {
        period = p
        refreshReport()
    }

    fun refreshReport() = launchSafe {
        val from = period.start()
        report = io { repo.report(from) }
    }

    suspend fun saleItems(saleId: Long): List<SaleItem> = io { repo.saleItems(saleId) }
}
