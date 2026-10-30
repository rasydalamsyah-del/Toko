package com.toko.kasir.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toko.kasir.data.Sale
import com.toko.kasir.data.SaleItem
import com.toko.kasir.logic.Period
import com.toko.kasir.logic.Rupiah
import com.toko.kasir.logic.Waktu
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.components.EmptyState
import com.toko.kasir.ui.components.StatCard
import com.toko.kasir.ui.components.rememberReceiptActions

@Composable
fun LaporanScreen(vm: TokoViewModel, modifier: Modifier = Modifier) {
    val report = vm.report
    var detail by remember { mutableStateOf<Sale?>(null) }
    var confirmCancel by remember { mutableStateOf<Sale?>(null) }
    val receipt = rememberReceiptActions(vm)

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Period.entries.forEach { p ->
                    FilterChip(
                        selected = vm.period == p,
                        onClick = { vm.selectPeriod(p) },
                        label = { Text(p.label) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    "Omzet", Rupiah.format(report.omzet), Modifier.weight(1f),
                    highlight = MaterialTheme.colorScheme.primary,
                )
                StatCard("Laba kotor", Rupiah.format(report.laba), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("Transaksi", report.count.toString(), Modifier.weight(1f))
                StatCard("Item terjual", report.itemsSold.toString(), Modifier.weight(1f))
            }
            if (report.byMethod.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Per metode bayar", style = MaterialTheme.typography.labelMedium)
                        report.byMethod.forEach { (m, sum) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(m.label, Modifier.weight(1f))
                                Text(Rupiah.format(sum), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Text(
                "Laba kotor = harga jual − harga modal. Transaksi batal tidak dihitung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (report.top.isNotEmpty()) {
            item {
                SectionTitle("Barang terlaris")
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    report.top.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("${i + 1}.", Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold)
                            Text(t.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${com.toko.kasir.logic.Angka.format(t.qty)} ${t.unit}".trim())
                        }
                    }
                }
            }
        }

        item { SectionTitle("Riwayat transaksi") }

        if (report.sales.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Assessment,
                    title = "Belum ada transaksi",
                    hint = "Transaksi dari halaman Kasir akan muncul di sini.",
                )
            }
        } else {
            items(report.sales, key = { it.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { detail = s }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("#${s.id} · ${Waktu.lengkap(s.time)}")
                        Text(
                            if (s.canceled) "Dibatalkan" else "${s.itemCount} item · ${s.method.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.canceled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Rupiah.format(s.total),
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (s.canceled) TextDecoration.LineThrough else null,
                        color = if (s.canceled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    detail?.let { sale ->
        var saleItems by remember(sale.id) { mutableStateOf<List<SaleItem>>(emptyList()) }
        LaunchedEffect(sale.id) { saleItems = vm.saleItems(sale.id) }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(if (sale.canceled) "Transaksi #${sale.id} (batal)" else "Transaksi #${sale.id}") },
            text = {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(Waktu.lengkap(sale.time), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    saleItems.forEach { si ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(si.label)
                                Text(
                                    "${Rupiah.format(si.price)} × ${si.qty}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(Rupiah.format(si.subtotal))
                        }
                    }
                    HorizontalDivider()
                    DetailLine("Total", Rupiah.format(sale.total), bold = true)
                    DetailLine("Dibayar (${sale.method.label})", Rupiah.format(sale.paid))
                    if (sale.change > 0) DetailLine("Kembalian", Rupiah.format(sale.change))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    if (!sale.canceled) {
                        TextButton(onClick = { confirmCancel = sale }) {
                            Text("Batalkan transaksi", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { detail = null }) { Text("Tutup") } },
        )
    }

    confirmCancel?.let { sale ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("Batalkan transaksi #${sale.id}?") },
            text = {
                Text(
                    "Stok barang dikembalikan dan transaksi tidak dihitung di laporan. " +
                        "Riwayatnya tetap tersimpan dengan tanda batal."
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.cancelSale(sale) {
                        confirmCancel = null
                        detail = null
                    }
                }) { Text("Batalkan transaksi") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Kembali") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun DetailLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
