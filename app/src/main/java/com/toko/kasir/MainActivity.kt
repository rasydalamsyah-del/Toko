package com.toko.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toko.kasir.ui.TokoViewModel
import com.toko.kasir.ui.screens.BarangScreen
import com.toko.kasir.ui.screens.KasirScreen
import com.toko.kasir.ui.screens.LaporanScreen
import com.toko.kasir.ui.screens.StokScreen
import com.toko.kasir.ui.theme.TokoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokoTheme { TokoApp() }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Kasir", Icons.Filled.PointOfSale),
    Tab("Barang", Icons.Filled.Inventory2),
    Tab("Stok", Icons.Filled.Warehouse),
    Tab("Laporan", Icons.Filled.Assessment),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokoApp(vm: TokoViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snackbar.showSnackbar(msg.text)
            vm.clearMessage()
        }
    }
    LaunchedEffect(tab) {
        if (tab == 3) vm.refreshReport()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Kasir Toko" else tabs[tab].title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> KasirScreen(vm, m)
            1 -> BarangScreen(vm, m)
            2 -> StokScreen(vm, m)
            else -> LaporanScreen(vm, m)
        }
    }
}
