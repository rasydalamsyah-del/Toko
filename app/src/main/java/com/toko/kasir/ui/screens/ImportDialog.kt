package com.toko.kasir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toko.kasir.logic.Barcodes
import com.toko.kasir.logic.ImportRow
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.ui.components.NumberField

private enum class RowStatus(val label: String) {
    BARU("baru"),
    SUDAH_ADA("sudah ada"),
    DOBEL("dobel di file"),
    HARGA_NOL("harga 0"),
}

/** Satu baris yang bisa dicentang dan diubah harganya sebelum diimpor. */
private class Draft(val index: Int, val row: ImportRow, val status: RowStatus) {
    var include by mutableStateOf(status == RowStatus.BARU)
    var price by mutableLongStateOf(row.price)
    var cost by mutableLongStateOf(row.cost)
}

/**
 * Layar cek ulang sebelum impor: centang barang yang mau dimasukkan,
 * ubah harga jual/modal, lalu simpan sekaligus.
 */
@Composable
fun ImportDialog(
    rows: List<ImportRow>,
    existingCodes: Set<String>,
    existingNames: Set<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<ImportRow>, Boolean) -> Unit,
) {
    val drafts = remember(rows) {
        val seen = HashSet<String>()
        rows.mapIndexed { i, r ->
            val variants = r.barcodes.flatMap { Barcodes.variants(it) }
            val status = when {
                variants.any { it in existingCodes } ||
                    (r.barcodes.isEmpty() && r.name.trim().uppercase() in existingNames) -> RowStatus.SUDAH_ADA
                variants.any { it in seen } -> RowStatus.DOBEL
                r.price <= 0 -> RowStatus.HARGA_NOL
                else -> RowStatus.BARU
            }
            seen.addAll(variants)
            Draft(i, r, status)
        }
    }
    val groups = remember(rows) { rows.map { it.group }.filter { it.isNotBlank() }.distinct().sorted() }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var updateExisting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Draft?>(null) }

    val visible = remember(drafts, query, group) {
        drafts.filter { d ->
            (group == null || d.row.group == group) &&
                (query.isBlank() || d.row.name.contains(query, true) || d.row.barcodes.any { it.contains(query) })
        }
    }
    val counts = remember(drafts) { drafts.groupingBy { it.status }.eachCount() }
    val selected = drafts.count { it.include && it.price > 0 }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Tutup") }
                    Text("Cek ulang impor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onConfirm(
                                drafts.filter { it.include && it.price > 0 }
                                    .map { it.row.copy(price = it.price, cost = it.cost) },
                                updateExisting,
                            )
                        },
                        enabled = selected > 0 && !saving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(if (saving) "Menyimpan…" else "Simpan $selected") }
                }
                HorizontalDivider()

                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        RowStatus.entries.mapNotNull { s -> counts[s]?.let { "$it ${s.label}" } }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Cek harga sebelum disimpan, karena bisa berbeda dengan harga toko Anda. Ketuk harga untuk mengubah. Stok awal 0.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if ((counts[RowStatus.SUDAH_ADA] ?: 0) > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Perbarui harga barang yang sudah ada",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = updateExisting, onCheckedChange = { on ->
                                updateExisting = on
                                drafts.filter { it.status == RowStatus.SUDAH_ADA }.forEach { it.include = on }
                            })
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Cari nama atau barcode") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Semua") })
                        groups.forEach { g ->
                            FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                        }
                        TextButton(onClick = {
                            visible.filter { it.status != RowStatus.DOBEL }.forEach { it.include = true }
                        }) { Text("Pilih semua") }
                        TextButton(onClick = { visible.forEach { it.include = false } }) { Text("Kosongkan") }
                    }
                    Text(
                        "${visible.size} barang tampil",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()

                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.index }) { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = d.status != RowStatus.DOBEL) { d.include = !d.include }
                                .padding(end = 12.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = d.include,
                                onCheckedChange = { d.include = it },
                                enabled = d.status != RowStatus.DOBEL,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(d.row.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (d.row.barcode.ifEmpty { "tanpa barcode" }) +
                                        (if (d.row.barcodes.size > 1) " +${d.row.barcodes.size - 1}" else "") +
                                        (if (d.status != RowStatus.BARU) " · ${d.status.label}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (d.status == RowStatus.BARU) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                if (d.price > 0) Rupiah.format(d.price) else "isi harga",
                                fontWeight = FontWeight.SemiBold,
                                color = if (d.price > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { editing = d }
                                    .padding(8.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editing?.let { d ->
        var priceText by remember(d) { mutableStateOf(d.price.takeIf { it > 0 }?.toString() ?: "") }
        var costText by remember(d) { mutableStateOf(d.cost.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(d.row.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(priceText, { priceText = it }, "Harga jual", prefix = "Rp")
                    NumberField(costText, { costText = it }, "Harga modal (opsional)", prefix = "Rp")
                }
            },
            confirmButton = {
                Button(onClick = {
                    d.price = Rupiah.parse(priceText)
                    d.cost = Rupiah.parse(costText)
                    if (d.price > 0 && d.status != RowStatus.DOBEL) d.include = true
                    editing = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } },
        )
    }
}
