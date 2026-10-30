package com.toko.kasir.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toko.kasir.data.Backup
import com.toko.kasir.data.BtPrinter
import com.toko.kasir.data.CartItem
import com.toko.kasir.data.DbHelper
import com.toko.kasir.data.PayMethod
import com.toko.kasir.data.PrinterDevice
import com.toko.kasir.data.Product
import com.toko.kasir.data.Report
import com.toko.kasir.data.Repository
import com.toko.kasir.data.Sale
import com.toko.kasir.data.SaleItem
import com.toko.kasir.data.Settings
import com.toko.kasir.data.StoreInfo
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.CartResult
import com.toko.kasir.logic.Period
import com.toko.kasir.logic.Receipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiMessage(val text: String, val id: Long = System.nanoTime())

/** Penanda barang yang baru masuk keranjang (untuk disorot dan ditampilkan di atas kamera). */
data class Highlight(val productId: Long, val label: String, val id: Long = System.nanoTime())

class TokoViewModel(private val app: Application) : AndroidViewModel(app) {

    private val helper = DbHelper(app)
    private val repo = Repository(helper)
    private val backup = Backup(app, helper)
    private val settings = Settings(app)
    private val qrisFile = File(app.filesDir, "qris.img")

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var cart by mutableStateOf<List<CartItem>>(emptyList()); private set
    var highlight by mutableStateOf<Highlight?>(null); private set
    var message by mutableStateOf<UiMessage?>(null); private set
    /** Barcode hasil scan yang belum terdaftar, untuk ditawarkan ditambahkan. */
    var unknownBarcode by mutableStateOf<String?>(null); private set
    var lastSale by mutableStateOf<Sale?>(null); private set
    var period by mutableStateOf(Period.TODAY); private set
    var report by mutableStateOf(Report.EMPTY); private set

    var store by mutableStateOf(settings.store); private set
    var printerName by mutableStateOf(settings.printerName); private set
    var qris by mutableStateOf<ImageBitmap?>(null); private set

    init {
        refreshProducts()
        refreshReport()
        launchSafe { qris = io { loadQris() } }
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
            is CartResult.Ok -> {
                cart = r.items
                val qty = r.items.firstOrNull()?.qty ?: 1
                highlight = Highlight(product.id, "${product.name} ($qty)")
            }
            is CartResult.StokKurang ->
                show("Stok ${r.product.name} tidak cukup (sisa ${r.available})")
        }
    }

    fun clearHighlight(h: Highlight) {
        if (highlight == h) highlight = null
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
        highlight = null
    }

    fun checkout(paid: Long, method: PayMethod, onDone: () -> Unit) = launchSafe {
        val sale = io { repo.checkout(cart, paid, method) }
        cart = emptyList()
        highlight = null
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

    fun cancelSale(sale: Sale, onDone: () -> Unit) = launchSafe {
        io { repo.cancelSale(sale.id) }
        onDone()
        show("Transaksi #${sale.id} dibatalkan, stok dikembalikan")
        refreshProducts()
        refreshReport()
    }

    // ---------- Struk ----------

    suspend fun receiptText(sale: Sale): String {
        val items = saleItems(sale.id)
        return Receipt.build(store, sale, items)
    }

    fun printReceipt(sale: Sale) = launchSafe {
        val address = settings.printerAddress
            ?: throw IllegalStateException("Printer belum dipilih. Atur di menu Toko.")
        val text = receiptText(sale)
        io { BtPrinter.print(app, address, text) }
        show("Struk dicetak")
    }

    fun testPrint() = launchSafe {
        val address = settings.printerAddress
            ?: throw IllegalStateException("Pilih printer dulu")
        val text = Receipt.wrap(store.name, 32).joinToString("\n") +
            "\nTes printer berhasil.\n" + "-".repeat(32) + "\n"
        io { BtPrinter.print(app, address, text) }
        show("Tes cetak terkirim")
    }

    fun pairedPrinters(): List<PrinterDevice> = BtPrinter.pairedDevices(app)

    fun choosePrinter(device: PrinterDevice) {
        settings.printerName = device.name
        settings.printerAddress = device.address
        printerName = device.name
        show("Printer: ${device.name}")
    }

    // ---------- Pengaturan toko ----------

    fun saveStore(info: StoreInfo) {
        settings.store = info
        store = info
        show("Info toko disimpan")
    }

    fun setQris(uri: Uri) = launchSafe {
        qris = io {
            app.contentResolver.openInputStream(uri)?.use { input ->
                qrisFile.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalStateException("Gambar tidak bisa dibuka")
            loadQris() ?: throw IllegalStateException("File ini bukan gambar")
        }
        show("Gambar QRIS disimpan")
    }

    fun clearQris() {
        qrisFile.delete()
        qris = null
    }

    private fun loadQris(): ImageBitmap? {
        if (!qrisFile.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(qrisFile.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            qrisFile.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        return bmp.asImageBitmap()
    }

    // ---------- Backup ----------

    fun backupTo(uri: Uri) = launchSafe {
        io {
            app.contentResolver.openOutputStream(uri)?.use { backup.export(it) }
                ?: throw IllegalStateException("Tidak bisa menyimpan file backup")
        }
        show("Backup tersimpan")
    }

    fun restoreFrom(uri: Uri) = launchSafe {
        io {
            app.contentResolver.openInputStream(uri)?.use { backup.restore(it) }
                ?: throw IllegalStateException("File backup tidak bisa dibuka")
        }
        cart = emptyList()
        show("Data berhasil dipulihkan")
        refreshProducts()
        refreshReport()
    }

    fun exportCsv(uri: Uri) = launchSafe {
        io {
            app.contentResolver.openOutputStream(uri)?.use { repo.exportCsv(it) }
                ?: throw IllegalStateException("Tidak bisa menyimpan file")
        }
        show("Transaksi diekspor")
    }
}
