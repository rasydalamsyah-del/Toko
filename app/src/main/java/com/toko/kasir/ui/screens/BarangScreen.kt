package com.toko.kasir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.toko.kasir.logic.Barcodes
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.components.EmptyState
import com.toko.kasir.ui.components.ProductDialog
import com.toko.kasir.ui.components.ScanDialog

@Composable
fun BarangScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createWithCode by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val list = remember(query, vm.products) { vm.products.filter { it.matches(query) } }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari nama atau barcode") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                        }
                    } else {
                        IconButton(onClick = { scanning = true }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Cari dengan scan")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            Text(
                "${list.size} barang",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = if (vm.products.isEmpty()) "Belum ada barang" else "Tidak ditemukan",
                    hint = if (vm.products.isEmpty()) "Tekan \"Tambah barang\" untuk mulai mengisi daftar."
                    else "Coba kata kunci lain.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { p ->
                        ProductRow(p) { editing = p }
                        HorizontalDivider()
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Tambah barang") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating || createWithCode != null) {
        ProductDialog(
            initial = null,
            presetBarcode = createWithCode,
            lookup = vm::lookupKatalog,
            onDismiss = { creating = false; createWithCode = null },
            onSave = { vm.saveProduct(it) { creating = false; createWithCode = null } },
        )
    }
    editing?.let { p ->
        ProductDialog(
            initial = p,
            onDismiss = { editing = null },
            onSave = { vm.saveProduct(it) { editing = null } },
            lookup = vm::lookupKatalog,
            onDelete = {
                vm.deleteProduct(p)
                editing = null
            },
        )
    }
    if (scanning) {
        ScanDialog(
            onDismiss = { scanning = false },
            onResult = { code ->
                scanning = false
                val variants = Barcodes.variants(code)
                val found = vm.products.firstOrNull { p -> p.allBarcodes.any { (c, _) -> c in variants } }
                // Belum terdaftar: langsung buka form tambah barang, nama terisi dari katalog bila ada.
                if (found != null) editing = found else createWithCode = code
            },
        )
    }
}

@Composable
private fun ProductRow(p: Product, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                p.priceText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                p.barcode.ifEmpty { if (p.units.any { it.barcode.isNotEmpty() }) "Barcode per satuan" else "Tanpa barcode" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                "Stok ${p.stockText()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (p.stock <= 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
