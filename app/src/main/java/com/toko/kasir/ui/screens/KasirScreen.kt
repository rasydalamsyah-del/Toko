package com.toko.kasir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toko.kasir.data.CartItem
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.components.EmptyState
import com.toko.kasir.ui.components.NumberField
import com.toko.kasir.ui.components.ProductDialog
import com.toko.kasir.ui.scanner.BarcodeScannerView
import com.toko.kasir.ui.scanner.CameraPermissionGate

@Composable
fun KasirScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    var showCamera by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var newBarcode by remember { mutableStateOf<String?>(null) }

    val cart = vm.cart
    val total = CartLogic.total(cart)
    val matches = remember(query, vm.products) {
        if (query.isBlank()) emptyList() else vm.products.filter { it.matches(query) }.take(6)
    }
    // Kamera dijeda saat ada dialog supaya tidak bentrok dengan kamera di dialog lain.
    val dialogOpen = paying || newBarcode != null || vm.unknownBarcode != null || vm.lastSale != null

    Column(modifier.fillMaxSize()) {
        if (showCamera) {
            if (dialogOpen) {
                Box(
                    Modifier.fillMaxWidth().height(190.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Kamera dijeda", color = MaterialTheme.colorScheme.outline) }
            } else {
                CameraPermissionGate(Modifier.fillMaxWidth().height(190.dp)) {
                    BarcodeScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onScanned = vm::onBarcodeScanned,
                    )
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
                    val exact = vm.products.firstOrNull { it.barcode.isNotEmpty() && it.barcode == q }
                    when {
                        q.isEmpty() -> Unit
                        exact != null -> { vm.addToCart(exact); query = "" }
                        matches.size == 1 -> { vm.addToCart(matches.first()); query = "" }
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
                                vm.addToCart(p)
                                query = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (p.stock > 0) "Stok ${p.stock} ${p.unit}" else "Stok habis",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.stock > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(Rupiah.format(p.price), fontWeight = FontWeight.SemiBold)
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
            LazyColumn(Modifier.weight(1f)) {
                items(cart, key = { it.product.id }) { item ->
                    CartRow(
                        item = item,
                        onMinus = { vm.setQty(item.product.id, item.qty - 1) },
                        onPlus = { vm.setQty(item.product.id, item.qty + 1) },
                    )
                    HorizontalDivider()
                }
            }
        }

        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                if (cart.isNotEmpty()) {
                    TextButton(onClick = vm::clearCart) { Text("Kosongkan") }
                }
                Button(onClick = { paying = true }, enabled = cart.isNotEmpty()) {
                    Text("Bayar")
                }
            }
        }
    }

    if (paying) {
        PaymentDialog(
            total = total,
            onDismiss = { paying = false },
            onConfirm = { paid -> vm.checkout(paid) { paying = false } },
        )
    }

    vm.unknownBarcode?.let { code ->
        AlertDialog(
            onDismissRequest = vm::dismissUnknownBarcode,
            title = { Text("Barang belum terdaftar") },
            text = { Text("Barcode $code belum ada di daftar barang. Tambahkan sekarang?") },
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
            onDismiss = { newBarcode = null },
            onSave = { p -> vm.saveProduct(p, addToCartAfter = p.stock > 0) { newBarcode = null } },
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
                    SummaryLine("Dibayar", Rupiah.format(sale.paid))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SummaryLine("Kembalian", Rupiah.format(sale.change), bold = true)
                }
            },
            confirmButton = { Button(onClick = vm::dismissLastSale) { Text("Transaksi baru") } },
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CartRow(item: CartItem, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.product.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${Rupiah.format(item.product.price)} × ${item.qty}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(Rupiah.format(item.subtotal), fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
        Text(
            item.qty.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(32.dp),
            maxLines = 1,
        )
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun PaymentDialog(total: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var paidText by remember { mutableStateOf("") }
    val paid = Rupiah.parse(paidText)
    val change = CartLogic.change(total, paid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembayaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total belanja", style = MaterialTheme.typography.labelMedium)
                Text(
                    Rupiah.format(total),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
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
                Spacer(Modifier.height(4.dp))
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
        },
        confirmButton = {
            Button(onClick = { onConfirm(paid) }, enabled = change != null && total > 0) {
                Text("Selesai")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
