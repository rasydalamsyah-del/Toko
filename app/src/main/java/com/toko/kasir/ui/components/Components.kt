package com.toko.kasir.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.toko.kasir.data.Product
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.scanner.BarcodeScannerView
import com.toko.kasir.ui.scanner.CameraPermissionGate

/** Kolom angka (rupiah/jumlah) yang hanya menerima digit. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        prefix = { if (prefix != null) Text(prefix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ScanDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Arahkan kamera ke barcode", style = MaterialTheme.typography.titleMedium)
                CameraPermissionGate(Modifier.fillMaxWidth().height(260.dp)) {
                    BarcodeScannerView(Modifier.fillMaxSize(), onScanned = onResult)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Batal")
                }
            }
        }
    }
}

/**
 * Form tambah/ubah barang. [initial] null berarti barang baru.
 */
@Composable
fun ProductDialog(
    initial: Product?,
    presetBarcode: String? = null,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var barcode by remember { mutableStateOf(initial?.barcode ?: presetBarcode ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah barang" else "Ubah barang") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it.trim() },
                    label = { Text("Barcode (boleh kosong)") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { scanning = true }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama barang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(price, { price = it }, "Harga jual", prefix = "Rp")
                NumberField(cost, { cost = it }, "Harga modal (opsional)", prefix = "Rp")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stock, { stock = it }, "Stok", Modifier.weight(1f))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(10) },
                        label = { Text("Satuan") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Hapus barang ini", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = Rupiah.parse(price)
                when {
                    name.isBlank() -> error = "Nama barang wajib diisi"
                    p <= 0 -> error = "Harga jual harus lebih dari 0"
                    else -> onSave(
                        Product(
                            id = initial?.id ?: 0,
                            barcode = barcode.trim(),
                            name = name.trim(),
                            price = p,
                            cost = Rupiah.parse(cost),
                            stock = stock.toIntOrNull() ?: 0,
                            unit = unit.trim().ifEmpty { "pcs" },
                        )
                    )
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )

    if (scanning) {
        ScanDialog(
            onDismiss = { scanning = false },
            onResult = {
                barcode = it
                scanning = false
            },
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus barang?") },
            text = { Text("\"$name\" akan dihapus. Riwayat penjualan tetap tersimpan.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Color? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = highlight ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
