package com.toko.kasir.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toko.kasir.data.Product
import com.toko.kasir.data.SaleUnit
import com.toko.kasir.logic.Barcodes
import com.toko.kasir.logic.KatalogItem
import com.toko.kasir.logic.Rupiah

/** Isian satu satuan jual di form (semua teks supaya mudah diedit). */
private data class UnitDraft(
    val id: Long = 0,
    val name: String = "",
    val factor: String = "",
    val price: String = "",
    val cost: String = "",
    val barcode: String = "",
)

/** Target scan barcode: -1 = barcode satuan dasar, 0.. = indeks satuan jual lain. */
private const val SCAN_BASE = -1

/**
 * Form tambah/ubah barang (layar penuh), termasuk satuan jual lain
 * seperti bungkus/slop untuk rokok atau ¼ kg / ½ kg untuk minyak.
 */
@Composable
fun ProductDialog(
    initial: Product?,
    presetBarcode: String? = null,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: (() -> Unit)? = null,
    lookup: (String) -> KatalogItem? = { null },
) {
    var barcode by remember { mutableStateOf(initial?.barcode ?: presetBarcode ?: "") }
    var altText by remember { mutableStateOf(initial?.altBarcodes.orEmpty().joinToString(", ")) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pcs") }
    var price by remember { mutableStateOf(initial?.price?.takeIf { it > 0 }?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var allowNominal by remember { mutableStateOf(initial?.allowNominal ?: false) }
    var units by remember {
        mutableStateOf(
            initial?.units.orEmpty().map {
                UnitDraft(
                    id = it.id,
                    name = it.name,
                    factor = it.factor.toString(),
                    price = it.price.toString(),
                    cost = it.cost.takeIf { c -> c > 0 }?.toString() ?: "",
                    barcode = it.barcode,
                )
            }
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var scanTarget by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Isi otomatis dari katalog bawaan saat barcode diisi/discan dan nama masih kosong.
    val hit = remember(barcode) { if (barcode.length >= 8) lookup(barcode) else null }
    LaunchedEffect(hit) {
        if (hit != null && name.isBlank()) {
            name = hit.names.first()
            if (price.isBlank() && hit.price > 0) price = hit.price.toString()
            if (cost.isBlank() && hit.cost > 0) cost = hit.cost.toString()
        }
    }

    fun updateUnit(i: Int, f: (UnitDraft) -> UnitDraft) {
        units = units.mapIndexed { idx, u -> if (idx == i) f(u) else u }
    }

    fun save() {
        val baseName = unit.trim().ifEmpty { "pcs" }
        val basePrice = Rupiah.parse(price)
        val baseCost = Rupiah.parse(cost)
        val parsed = mutableListOf<SaleUnit>()
        for ((i, u) in units.withIndex()) {
            val n = u.name.trim()
            val f = u.factor.toIntOrNull() ?: 0
            val pr = Rupiah.parse(u.price)
            when {
                n.isEmpty() -> { error = "Nama satuan ke-${i + 1} belum diisi"; return }
                f <= 0 -> { error = "Isi satuan \"$n\" harus lebih dari 0 $baseName"; return }
                pr <= 0 -> { error = "Harga satuan \"$n\" belum diisi"; return }
            }
            val c = Rupiah.parse(u.cost).takeIf { it > 0 } ?: (baseCost * f)
            parsed += SaleUnit(u.id, n, f, pr, c, u.barcode.trim())
        }
        when {
            name.isBlank() -> { error = "Nama barang wajib diisi"; return }
            basePrice <= 0 && parsed.isEmpty() ->
                { error = "Isi harga per $baseName, atau tambahkan satuan jual lain"; return }
            parsed.map { it.name.lowercase() }.let { it.size != it.toSet().size || baseName.lowercase() in it } ->
                { error = "Nama satuan tidak boleh sama"; return }
        }
        onSave(
            Product(
                id = initial?.id ?: 0,
                barcode = barcode.trim(),
                name = name.trim(),
                price = basePrice,
                cost = baseCost,
                stock = stock.toIntOrNull() ?: 0,
                unit = baseName,
                units = parsed,
                allowNominal = allowNominal,
                altBarcodes = Barcodes.split(altText).filter { it != barcode.trim() },
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                    Text(
                        if (initial == null) "Tambah barang" else "Ubah barang",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text("Simpan") }
                }
                HorizontalDivider()

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Nama barang") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hit != null) {
                        KatalogHint(hit, current = name, onPick = { name = it })
                    }

                    SectionLabel(
                        "Satuan dasar",
                        "Satuan terkecil, stok dihitung dengan satuan ini. Contoh: pcs, batang, gram, ml.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = unit, onValueChange = { unit = it.take(12) },
                            label = { Text("Satuan dasar") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(stock, { stock = it }, "Stok ($unit)", Modifier.weight(1f))
                    }
                    NumberField(
                        price, { price = it; error = null },
                        "Harga jual per $unit (kosongkan jika tidak dijual per $unit)", prefix = "Rp",
                    )
                    NumberField(cost, { cost = it }, "Harga modal per $unit (opsional)", prefix = "Rp")
                    OutlinedTextField(
                        value = barcode, onValueChange = { barcode = it.trim() },
                        label = { Text("Barcode per $unit (opsional)") }, singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { scanTarget = SCAN_BASE }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = altText, onValueChange = { altText = it },
                        label = { Text("Barcode lain, pisahkan dengan koma (opsional)") },
                        supportingText = { Text("Untuk barang yang punya barcode lama dan baru dari pabrik.") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionLabel(
                        "Satuan jual lain",
                        "Contoh rokok: bungkus isi 16 batang, slop isi 160. Minyak: ¼ kg isi 250 gram.",
                    )
                    units.forEachIndexed { i, u ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = u.name,
                                        onValueChange = { v -> updateUnit(i) { it.copy(name = v.take(12)) }; error = null },
                                        label = { Text("Nama satuan") }, singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    NumberField(
                                        u.factor, { v -> updateUnit(i) { it.copy(factor = v) }; error = null },
                                        "Isi ($unit)", Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { units = units.filterIndexed { idx, _ -> idx != i } }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus satuan")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumberField(
                                        u.price, { v -> updateUnit(i) { it.copy(price = v) }; error = null },
                                        "Harga jual", Modifier.weight(1f), prefix = "Rp",
                                    )
                                    NumberField(
                                        u.cost, { v -> updateUnit(i) { it.copy(cost = v) } },
                                        "Modal (ops.)", Modifier.weight(1f), prefix = "Rp",
                                    )
                                }
                                OutlinedTextField(
                                    value = u.barcode,
                                    onValueChange = { v -> updateUnit(i) { it.copy(barcode = v.trim()) } },
                                    label = { Text("Barcode ${u.name.ifBlank { "satuan ini" }} (opsional)") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { scanTarget = i }) {
                                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val f = u.factor.toIntOrNull()
                                val pr = Rupiah.parse(u.price)
                                if (f != null && f > 0 && u.name.isNotBlank()) {
                                    Text(
                                        "1 ${u.name} = ${f} $unit" +
                                            if (pr > 0) " · ${Rupiah.format(pr)}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { units = units + UnitDraft() }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" Tambah satuan jual")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bisa dijual sesuai nominal", fontWeight = FontWeight.Medium)
                            Text(
                                "Mis. pembeli minta \"minyak Rp7.000\". Jumlahnya dihitung dari harga satuan terbesar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = allowNominal, onCheckedChange = { allowNominal = it })
                    }

                    if (onDelete != null) {
                        HorizontalDivider()
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Hapus barang ini", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    scanTarget?.let { target ->
        ScanDialog(
            onDismiss = { scanTarget = null },
            onResult = { code ->
                if (target == SCAN_BASE) barcode = code
                else updateUnit(target) { it.copy(barcode = code) }
                scanTarget = null
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

/** Saran nama (dan harga) dari katalog bawaan untuk barcode yang diisi. */
@Composable
private fun KatalogHint(hit: KatalogItem, current: String, onPick: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (hit.names.size > 1) "Ditemukan di katalog (pilih nama):" else "Ditemukan di katalog:",
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                hit.names.forEach { n ->
                    FilterChip(
                        selected = n.equals(current.trim(), ignoreCase = true),
                        onClick = { onPick(n) },
                        label = { Text(n, maxLines = 1) },
                    )
                }
            }
            if (hit.price > 0) {
                Text(
                    "Harga saran dari daftar harga: ${Rupiah.format(hit.price)}. Sesuaikan dengan harga toko Anda.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
