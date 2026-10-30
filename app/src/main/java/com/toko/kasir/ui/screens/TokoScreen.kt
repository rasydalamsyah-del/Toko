package com.toko.kasir.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toko.kasir.data.BtPrinter
import com.toko.kasir.data.PrinterDevice
import com.toko.kasir.logic.Angka
import com.toko.kasir.logic.Waktu
import com.toko.kasir.ui.TokoViewModel

@Composable
fun TokoScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var info by remember(vm.store) { mutableStateOf(vm.store) }
    var devices by remember { mutableStateOf<List<PrinterDevice>?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val pickQris = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setQris(uri)
    }
    val backupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) vm.backupTo(uri) }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openImportFile(uri)
    }
    val csvFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportCsv(uri) }

    val loadPrinters: () -> Unit = {
        try {
            devices = vm.pairedPrinters()
        } catch (e: Exception) {
            vm.show(e.message ?: "Bluetooth tidak bisa dibuka")
        }
    }
    val btPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadPrinters() else vm.show("Izin Bluetooth diperlukan untuk printer") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Info toko", "Tampil di bagian atas dan bawah struk.") {
            OutlinedTextField(
                value = info.name, onValueChange = { info = info.copy(name = it) },
                label = { Text("Nama toko") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = info.address, onValueChange = { info = info.copy(address = it) },
                label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = info.phone, onValueChange = { info = info.copy(phone = it) },
                label = { Text("No. telepon / WA") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = info.footer, onValueChange = { info = info.copy(footer = it) },
                label = { Text("Catatan kaki struk") }, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.saveStore(info) }, enabled = info != vm.store) { Text("Simpan info toko") }
        }

        Section("QRIS toko", "Ditampilkan ke pembeli saat memilih bayar QRIS.") {
            val img = vm.qris
            if (img != null) {
                Image(
                    img, contentDescription = "QRIS toko",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickQris.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    IconText(Icons.Filled.QrCode2, if (img == null) "Pilih gambar QRIS" else "Ganti gambar")
                }
                if (img != null) TextButton(onClick = vm::clearQris) { Text("Hapus") }
            }
        }

        Section(
            "Printer struk",
            "Printer thermal Bluetooth 58mm (ESC/POS). Pasangkan (pair) dulu di pengaturan Bluetooth HP.",
        ) {
            Text(
                "Printer: ${vm.printerName ?: "belum dipilih"}",
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (BtPrinter.hasPermission(context)) loadPrinters()
                    else btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) { IconText(Icons.Filled.Print, "Pilih printer") }
                OutlinedButton(onClick = vm::testPrint, enabled = vm.printerName != null) {
                    Text("Tes cetak")
                }
            }
        }

        Section(
            "Impor barang",
            "Masukkan banyak barang sekaligus. Sebelum disimpan, daftar bisa dicek dan harganya diubah.",
        ) {
            OutlinedButton(onClick = vm::openBuiltInPriceList, modifier = Modifier.fillMaxWidth()) {
                IconText(Icons.Filled.PlaylistAdd, "Dari daftar harga bawaan (2.892 barang)")
            }
            OutlinedButton(onClick = { importFile.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                IconText(Icons.Filled.UploadFile, "Dari file CSV")
            }
            Text(
                "File Excel: simpan dulu sebagai CSV. Kolom yang dikenali: nama, barcode, harga, modal, stok, satuan, kategori.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (vm.katalog.size > 0) "Katalog barcode: ${Angka.format(vm.katalog.size)} barang. " +
                    "Saat scan barang yang belum terdaftar, nama terisi otomatis."
                else "Memuat katalog barcode…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Section(
            "Data toko",
            "Simpan backup secara rutin ke Google Drive atau kirim ke WA sendiri, supaya data aman kalau HP rusak.",
        ) {
            OutlinedButton(
                onClick = { backupFile.launch("kasir-toko-backup-${Waktu.file(System.currentTimeMillis())}.db") },
                modifier = Modifier.fillMaxWidth(),
            ) { IconText(Icons.Filled.Backup, "Backup data") }
            OutlinedButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) { IconText(Icons.Filled.Restore, "Pulihkan dari backup") }
            OutlinedButton(
                onClick = { csvFile.launch("transaksi-${Waktu.file(System.currentTimeMillis())}.csv") },
                modifier = Modifier.fillMaxWidth(),
            ) { IconText(Icons.Filled.TableChart, "Ekspor transaksi (Excel/CSV)") }
        }
    }

    devices?.let { list ->
        AlertDialog(
            onDismissRequest = { devices = null },
            title = { Text("Pilih printer") },
            text = {
                if (list.isEmpty()) {
                    Text("Belum ada perangkat Bluetooth yang dipasangkan. Pair printer dulu di pengaturan Bluetooth HP.")
                } else {
                    Column {
                        list.forEach { d ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.choosePrinter(d)
                                        devices = null
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(d.name)
                                Text(
                                    d.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { devices = null }) { Text("Tutup") } },
        )
    }

    vm.importDraft?.let { rows ->
        ImportDialog(
            rows = rows,
            existingCodes = remember(rows, vm.products) { vm.existingCodes() },
            existingNames = remember(rows, vm.products) { vm.existingNames() },
            saving = vm.importing,
            onDismiss = vm::closeImport,
            onConfirm = { selected, update -> vm.confirmImport(selected, update) },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Pulihkan dari backup?") },
            text = {
                Text("Semua data barang dan transaksi di HP ini akan diganti dengan isi file backup yang dipilih.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmRestore = false
                    restoreFile.launch(arrayOf("*/*"))
                }) { Text("Pilih file backup") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun Section(title: String, hint: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun IconText(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(" $text")
}
