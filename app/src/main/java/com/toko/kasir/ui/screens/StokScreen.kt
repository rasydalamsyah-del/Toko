package com.toko.kasir.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import com.toko.kasir.ui.components.ScanDialog
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toko.kasir.data.Product
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.components.EmptyState
import com.toko.kasir.ui.components.NumberField
import com.toko.kasir.ui.components.StatCard

private enum class StokFilter(val label: String) { SEMUA("Semua"), MENIPIS("Menipis"), HABIS("Habis") }

@Composable
fun StokScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf(StokFilter.SEMUA) }
    var query by rememberSaveable { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var restock by remember { mutableStateOf<Product?>(null) }
    val products = vm.products

    val habis = products.count { it.stock <= 0 }
    val menipis = products.count { it.isLow }
    val nilaiStok = products.sumOf { it.cost * it.stock.coerceAtLeast(0) }
    val list = remember(products, filter, query) {
        products.filter {
            it.matches(query) && when (filter) {
                StokFilter.SEMUA -> true
                StokFilter.MENIPIS -> it.isLow
                StokFilter.HABIS -> it.stock <= 0
            }
        }.sortedWith(compareBy<Product> { it.stock }.thenBy { it.name.lowercase() })
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari atau scan barang untuk restok") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                        }
                    } else {
                        IconButton(onClick = { scanning = true }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barang")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("Jenis barang", products.size.toString(), Modifier.weight(1f))
                StatCard(
                    "Menipis", menipis.toString(), Modifier.weight(1f),
                    highlight = if (menipis > 0) MaterialTheme.colorScheme.tertiary else null,
                )
                StatCard(
                    "Habis", habis.toString(), Modifier.weight(1f),
                    highlight = if (habis > 0) MaterialTheme.colorScheme.error else null,
                )
            }
            Text(
                "Nilai stok (harga modal): ${Rupiah.format(nilaiStok)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StokFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                    )
                }
            }
            HorizontalDivider()
        }

        if (list.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Warehouse,
                    title = if (products.isEmpty()) "Belum ada barang" else "Tidak ada barang di filter ini",
                    hint = if (products.isEmpty()) "Tambahkan barang di halaman Barang." else "Stok aman.",
                )
            }
        } else {
            items(list, key = { it.id }) { p ->
                StockRow(
                    p = p,
                    onMinus = { vm.adjustStock(p.id, -p.quickStep) },
                    onPlus = { vm.adjustStock(p.id, p.quickStep) },
                    onRestock = { restock = p },
                )
                HorizontalDivider()
            }
        }
    }

    if (scanning) {
        ScanDialog(
            onDismiss = { scanning = false },
            onResult = { code ->
                scanning = false
                val found = products.firstOrNull { p -> p.barcode == code || p.units.any { it.barcode == code } }
                if (found != null) restock = found else vm.show("Barcode $code belum terdaftar")
            },
        )
    }

    restock?.let { p ->
        RestockDialog(
            product = p,
            onDismiss = { restock = null },
            onAdd = { baseQty ->
                vm.adjustStock(p.id, baseQty)
                vm.show("Stok ${p.name} +${p.stockText(baseQty)}")
                restock = null
            },
            onSet = { baseQty ->
                vm.setStock(p.id, baseQty)
                vm.show("Stok ${p.name} diatur jadi ${p.stockText(baseQty)}")
                restock = null
            },
        )
    }
}

@Composable
private fun StockRow(p: Product, onMinus: () -> Unit, onPlus: () -> Unit, onRestock: () -> Unit) {
    val color = when {
        p.stock <= 0 -> MaterialTheme.colorScheme.error
        p.isLow -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    p.stock <= 0 -> "Habis"
                    p.isLow -> "Menipis: ${p.stockText()}"
                    else -> p.stockText()
                },
                color = color,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onMinus, enabled = p.stock > 0) {
            Icon(Icons.Filled.Remove, contentDescription = "Kurangi stok")
        }
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Tambah stok") }
        TextButton(onClick = onRestock) { Text("Restok") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockDialog(
    product: Product,
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit,
    onSet: (Int) -> Unit,
) {
    var qtyText by remember { mutableStateOf("") }
    var setMode by remember { mutableStateOf(false) }
    val units = remember(product) { listOf(product.baseUnit) + product.units.sortedBy { it.factor } }
    // Default restok pakai satuan terbesar (mis. slop/dus), karena biasanya kulakan dalam jumlah besar.
    var unit by remember(product) { mutableStateOf(units.last()) }
    val qty = qtyText.toIntOrNull()
    val baseQty = qty?.let { it * unit.factor }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restok ${product.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Stok sekarang: ${product.stockText()}")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !setMode,
                        onClick = { setMode = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Tambah") }
                    SegmentedButton(
                        selected = setMode,
                        onClick = { setMode = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Atur jadi") }
                }
                if (units.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        units.forEach { u ->
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u },
                                label = { Text(u.name) },
                            )
                        }
                    }
                }
                NumberField(
                    qtyText, { qtyText = it },
                    if (setMode) "Stok baru (${unit.name})" else "Jumlah masuk (${unit.name})",
                )
                if (baseQty != null) {
                    Text(
                        if (setMode) "Stok menjadi ${product.stockText(baseQty)}"
                        else "Stok menjadi ${product.stockText(product.stock + baseQty)}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { baseQty?.let { if (setMode) onSet(it) else onAdd(it) } },
                enabled = baseQty != null && (setMode || baseQty > 0),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
