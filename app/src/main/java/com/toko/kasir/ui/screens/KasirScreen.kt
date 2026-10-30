package com.toko.kasir.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toko.kasir.data.CartItem
import com.toko.kasir.data.PayMethod
import com.toko.kasir.data.Product
import com.toko.kasir.data.SaleUnit
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.components.EmptyState
import com.toko.kasir.ui.components.NumberField
import com.toko.kasir.ui.components.ProductDialog
import com.toko.kasir.ui.components.rememberReceiptActions
import com.toko.kasir.ui.scanner.BarcodeScannerView
import com.toko.kasir.ui.scanner.CameraPermissionGate
import kotlinx.coroutines.delay

@Composable
fun KasirScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    var showCamera by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var newBarcode by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var editQty by remember { mutableStateOf<CartItem?>(null) }
    var changeUnitFor by remember { mutableStateOf<CartItem?>(null) }
    val listState = rememberLazyListState()
    val receipt = rememberReceiptActions(vm)

    val cart = vm.cart
    val total = CartLogic.total(cart)
    val matches = remember(query, vm.products) {
        if (query.isBlank()) emptyList() else vm.products.filter { it.matches(query) }.take(6)
    }
    // Kamera dijeda saat ada dialog supaya tidak bentrok dengan kamera di dialog lain.
    val dialogOpen = paying || newBarcode != null || vm.unknownBarcode != null ||
        vm.lastSale != null || confirmClear || editQty != null ||
        vm.unitPicker != null || changeUnitFor != null

    // Barang baru: gulir ke atas, sorot sebentar, lalu hilangkan sorotan.
    val highlight = vm.highlight
    LaunchedEffect(highlight) {
        if (highlight != null) {
            listState.animateScrollToItem(0)
            delay(1800)
            vm.clearHighlight(highlight)
        }
    }

    Column(modifier.fillMaxSize()) {
        if (showCamera) {
            Box(Modifier.fillMaxWidth().height(140.dp).clipToBounds()) {
                if (dialogOpen) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xFF1B1B1B)),
                        contentAlignment = Alignment.Center,
                    ) { Text("Kamera dijeda", color = Color.White.copy(alpha = 0.7f)) }
                } else {
                    CameraPermissionGate(Modifier.fillMaxSize()) {
                        BarcodeScannerView(
                            modifier = Modifier.fillMaxSize(),
                            onScanned = vm::onBarcodeScanned,
                        )
                    }
                }
                if (highlight != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .widthIn(max = 280.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                " ${highlight.label}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Cari nama / ketik barcode") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    // Juga mendukung scanner barcode USB/Bluetooth yang mengetik + Enter.
                    val q = query.trim()
                    val exact = vm.products.any { p ->
                        p.allBarcodes.any { (c, _) -> c == q }
                    }
                    when {
                        q.isEmpty() -> Unit
                        exact -> { vm.onBarcodeScanned(q); query = "" }
                        matches.size == 1 -> { vm.selectProduct(matches.first()); query = "" }
                        q.all { it.isDigit() } && q.length >= 6 -> { vm.onBarcodeScanned(q); query = "" }
                    }
                }),
            )
            IconButton(onClick = { showCamera = !showCamera }) {
                Icon(
                    if (showCamera) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    contentDescription = if (showCamera) "Sembunyikan kamera" else "Tampilkan kamera",
                )
            }
        }

        if (matches.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                matches.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = p.stock > 0) {
                                vm.selectProduct(p)
                                query = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.stock > 0) "Stok ${p.stockText()}" else "Stok habis",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.stock > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            p.saleUnits.firstOrNull()?.let { "${Rupiah.format(it.price)}/${it.name}" } ?: "",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (cart.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.ShoppingCart,
                title = "Keranjang kosong",
                hint = "Scan barcode barang atau cari dari kolom di atas.",
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Keranjang · ${cart.size} barang",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Filled.DeleteSweep, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(" Kosongkan", color = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(cart, key = { it.key }) { item ->
                    CartRow(
                        item = item,
                        highlighted = highlight?.key == item.key,
                        onMinus = { vm.setQty(item.key, item.qty - 1) },
                        onPlus = { vm.setQty(item.key, item.qty + 1) },
                        onQtyClick = { editQty = item },
                        onUnitClick = { changeUnitFor = item },
                    )
                    HorizontalDivider()
                }
            }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Total (${CartLogic.totalQty(cart)} item)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        Rupiah.format(total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = { paying = true },
                    enabled = cart.isNotEmpty(),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Bayar", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (paying) {
        PaymentDialog(
            total = total,
            vm = vm,
            onDismiss = { paying = false },
            onConfirm = { paid, method -> vm.checkout(paid, method) { paying = false } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Kosongkan keranjang?") },
            text = { Text("${cart.size} barang di keranjang akan dihapus.") },
            confirmButton = {
                Button(onClick = {
                    vm.clearCart()
                    confirmClear = false
                }) { Text("Kosongkan") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }

    editQty?.let { item ->
        QtyDialog(
            item = item,
            onDismiss = { editQty = null },
            onSave = { qty ->
                vm.setQty(item.key, qty)
                editQty = null
            },
        )
    }

    vm.unitPicker?.let { product ->
        UnitPickerDialog(
            product = product,
            title = product.name,
            onDismiss = vm::dismissUnitPicker,
            onPick = { unit ->
                vm.dismissUnitPicker()
                vm.addToCart(product, unit)
            },
            onNominal = if (product.allowNominal) { nominal: Long ->
                vm.dismissUnitPicker()
                vm.addNominal(product, nominal)
            } else null,
        )
    }

    changeUnitFor?.let { item ->
        UnitPickerDialog(
            product = item.product,
            title = "Ganti satuan ${item.product.name}",
            onDismiss = { changeUnitFor = null },
            onPick = { unit ->
                vm.changeUnit(item.key, unit)
                changeUnitFor = null
            },
            onNominal = null,
        )
    }

    vm.unknownBarcode?.let { code ->
        AlertDialog(
            onDismissRequest = vm::dismissUnknownBarcode,
            title = { Text("Barang belum terdaftar") },
            text = {
                val hit = vm.lookupKatalog(code)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Barcode $code belum ada di daftar barang. Tambahkan sekarang?")
                    if (hit != null) {
                        Text(
                            "Di katalog: ${hit.names.first()}" +
                                if (hit.price > 0) " (harga saran ${Rupiah.format(hit.price)})" else "",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    newBarcode = code
                    vm.dismissUnknownBarcode()
                }) { Text("Tambah barang") }
            },
            dismissButton = { TextButton(onClick = vm::dismissUnknownBarcode) { Text("Lewati") } },
        )
    }

    newBarcode?.let { code ->
        ProductDialog(
            initial = null,
            presetBarcode = code,
            lookup = vm::lookupKatalog,
            onDismiss = { newBarcode = null },
            onSave = { p ->
                vm.saveProduct(p, addToCartAfter = p.stock > 0, scannedCode = code) { newBarcode = null }
            },
        )
    }

    vm.lastSale?.let { sale ->
        AlertDialog(
            onDismissRequest = vm::dismissLastSale,
            icon = {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
            },
            title = { Text("Transaksi berhasil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryLine("Total", Rupiah.format(sale.total))
                    SummaryLine("Dibayar (${sale.method.label})", Rupiah.format(sale.paid))
                    if (sale.method == PayMethod.TUNAI) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        SummaryLine("Kembalian", Rupiah.format(sale.change), bold = true)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { receipt.share(sale) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Bagikan")
                        }
                        OutlinedButton(onClick = { receipt.print(sale) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Cetak")
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = vm::dismissLastSale) { Text("Transaksi baru") } },
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    highlighted: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onQtyClick: () -> Unit,
    onUnitClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "sorot",
    )
    val canChangeUnit = !item.isNominal && item.product.saleUnits.size > 1
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.product.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.isNominal) item.unit.name else "/${item.unit.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (canChangeUnit) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(enabled = canChangeUnit, onClick = onUnitClick)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    if (item.isNominal) "  ${Rupiah.format(item.subtotal)}"
                    else "  ${Rupiah.format(item.unit.price)} · ${Rupiah.format(item.subtotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.isNominal) {
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Close, contentDescription = "Hapus") }
        } else {
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
            Text(
                item.qty.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 44.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onQtyClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
        }
    }
}

/** Pilih satuan (batang/bungkus/slop) atau isi nominal, saat memilih barang di kasir. */
@Composable
private fun UnitPickerDialog(
    product: Product,
    title: String,
    onDismiss: () -> Unit,
    onPick: (SaleUnit) -> Unit,
    onNominal: ((Long) -> Unit)?,
) {
    var nominalText by remember { mutableStateOf("") }
    val nominal = Rupiah.parse(nominalText)
    val preview = remember(nominal) {
        if (nominal > 0) CartLogic.nominalUnit(product, nominal, -1) else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Stok: ${product.stockText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                product.saleUnits.forEach { u ->
                    OutlinedButton(
                        onClick = { onPick(u) },
                        enabled = u.factor <= product.stock,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(u.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(Rupiah.format(u.price))
                    }
                }
                if (onNominal != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Sesuai nominal", fontWeight = FontWeight.Medium)
                    NumberField(nominalText, { nominalText = it }, "Nominal pembeli", prefix = "Rp")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(2_000L, 5_000L, 10_000L, 20_000L).forEach { v ->
                            AssistChip(onClick = { nominalText = v.toString() }, label = { Text(Rupiah.format(v)) })
                        }
                    }
                    if (nominal > 0) {
                        Text(
                            if (preview != null) "Dapat ${preview.name}" else "Nominal terlalu kecil",
                            color = if (preview != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = { onNominal(nominal) },
                        enabled = preview != null && preview.factor <= product.stock,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Tambah ${if (nominal > 0) Rupiah.format(nominal) else "nominal"}") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun QtyDialog(item: CartItem, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(item.qty.toString()) }
    val qty = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.product.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Jumlah (${item.unit.name})")
                Text(
                    "Stok tersedia: ${item.product.stockText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { qty?.let(onSave) }, enabled = qty != null) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    total: Long,
    vm: TokoViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Long, PayMethod) -> Unit,
) {
    var method by remember { mutableStateOf(PayMethod.TUNAI) }
    var paidText by remember { mutableStateOf("") }
    val paid = Rupiah.parse(paidText)
    val change = CartLogic.change(total, paid)
    val canPay = total > 0 && (method != PayMethod.TUNAI || change != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembayaran") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Total belanja", style = MaterialTheme.typography.labelMedium)
                Text(
                    Rupiah.format(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PayMethod.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = method == m,
                            onClick = { method = m },
                            shape = SegmentedButtonDefaults.itemShape(i, PayMethod.entries.size),
                        ) { Text(m.label, maxLines = 1) }
                    }
                }
                when (method) {
                    PayMethod.TUNAI -> {
                        NumberField(paidText, { paidText = it }, "Uang diterima", prefix = "Rp")
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(onClick = { paidText = total.toString() }, label = { Text("Uang pas") })
                            CartLogic.cashSuggestions(total).forEach { v ->
                                AssistChip(onClick = { paidText = v.toString() }, label = { Text(Rupiah.format(v)) })
                            }
                        }
                        if (paidText.isNotEmpty() && change == null) {
                            Text(
                                "Uang kurang ${Rupiah.format(total - paid)}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (change != null) {
                            Text(
                                "Kembalian ${Rupiah.format(change)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    PayMethod.QRIS -> {
                        val img = vm.qris
                        if (img != null) {
                            Image(
                                img, contentDescription = "QRIS toko",
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            )
                            Text(
                                "Minta pembeli scan QRIS ini, lalu cek notifikasi dana masuk.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                "Gambar QRIS belum diatur. Tambahkan di menu Toko supaya bisa ditunjukkan ke pembeli.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    PayMethod.TRANSFER -> {
                        Text(
                            "Tekan Selesai setelah transfer masuk ke rekening toko.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(paid, method) }, enabled = canPay) {
                Text(if (method == PayMethod.TUNAI) "Selesai" else "Sudah dibayar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
