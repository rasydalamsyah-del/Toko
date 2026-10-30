package com.toko.kasir.ui.components

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.toko.kasir.data.BtPrinter
import com.toko.kasir.data.Sale
import com.toko.kasir.ui.TokoViewModel
import kotlinx.coroutines.launch

class ReceiptActions(
    val share: (Sale) -> Unit,
    val print: (Sale) -> Unit,
)

/** Aksi struk: bagikan (WhatsApp, dll.) dan cetak Bluetooth, termasuk meminta izin Bluetooth. */
@Composable
fun rememberReceiptActions(vm: TokoViewModel): ReceiptActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Sale?>(null) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val sale = pending
        pending = null
        if (granted && sale != null) vm.printReceipt(sale)
        else if (!granted) vm.show("Izin Bluetooth diperlukan untuk mencetak")
    }

    return remember(vm) {
        ReceiptActions(
            share = { sale ->
                scope.launch {
                    val text = vm.receiptText(sale)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Bagikan struk"))
                }
            },
            print = { sale ->
                if (BtPrinter.hasPermission(context)) {
                    vm.printReceipt(sale)
                } else {
                    pending = sale
                    permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
        )
    }
}
