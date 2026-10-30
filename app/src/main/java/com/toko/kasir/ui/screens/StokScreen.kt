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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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

const val LOW_STOCK = 5

private enum class StokFilter(val label: String) { SEMUA("Semua"), MENIPIS("Menipis"), HABIS("Habis") }

@Composable
fun StokScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf(StokFilter.SEMUA) }
    var restock by remember { mutableStateOf<Product?>(null) }
    val products = vm.products

    val habis = products.count { it.stock <= 0 }
    val menipis = products.count { it.stock in 1..LOW_STOCK }
    val nilaiStok = products.sumOf { it.cost * it.stock.coerceAtLeast(0) }
    val list = remember(products, filter) {
        products.filter {
            when (filter) {
                StokFilter.SEMUA -> true
                StokFilter.MENIPIS -> it.stock in 1..LOW_STOCK
                StokFilter.HABIS -> it.stock <= 0
            }
        }.sortedWith(compareBy<Product> { it.stock }.thenBy { it.name.lowercase() })
    }

    LazyColumn(modifier.fillMaxSize()) {
        item {
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
                    onMinus = { vm.adjustStock(p.id, -1) },
                    onPlus = { vm.adjustStock(p.id, 1) },
                    onRestock = { restock = p },
                )
                HorizontalDivider()
            }
        }
    }

    restock?.let { p ->
        RestockDialog(
            product = p,
            onDismiss = { restock = null },
            onAdd = { qty ->
                vm.adjustStock(p.id, qty)
                vm.show("Stok ${p.name} +$qty")
                restock = null
            },
            onSet = { qty ->
                vm.setStock(p.id, qty)
                vm.show("Stok ${p.name} diatur jadi $qty")
                restock = null
            },
        )
    }
}

@Composable
private fun StockRow(p: Product, onMinus: () -> Unit, onPlus: () -> Unit, onRestock: () -> Unit) {
    val color = when {
        p.stock <= 0 -> MaterialTheme.colorScheme.error
        p.stock <= LOW_STOCK -> MaterialTheme.colorScheme.tertiary
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
                    p.stock <= LOW_STOCK -> "Menipis: ${p.stock} ${p.unit}"
                    else -> "${p.stock} ${p.unit}"
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
    val qty = qtyText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restok ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stok sekarang: ${product.stock} ${product.unit}")
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
                NumberField(
                    qtyText, { qtyText = it },
                    if (setMode) "Stok baru" else "Jumlah barang masuk",
                )
                if (!setMode && qty != null) {
                    Text("Stok menjadi ${product.stock + qty} ${product.unit}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { qty?.let { if (setMode) onSet(it) else onAdd(it) } },
                enabled = qty != null && (setMode || qty > 0),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
