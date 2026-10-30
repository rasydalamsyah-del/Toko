package com.toko.kasir.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.toko.kasir.logic.Receipt
import java.io.IOException
import java.util.UUID

data class PrinterDevice(val name: String, val address: String)

/** Cetak ke printer thermal Bluetooth (ESC/POS) yang sudah dipasangkan (pair) di HP. */
object BtPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PrinterDevice> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IllegalStateException("HP ini tidak punya Bluetooth")
        if (!adapter.isEnabled) throw IllegalStateException("Nyalakan Bluetooth dulu")
        return adapter.bondedDevices.orEmpty()
            .map { PrinterDevice(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    fun print(context: Context, address: String, text: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IllegalStateException("HP ini tidak punya Bluetooth")
        if (!adapter.isEnabled) throw IllegalStateException("Nyalakan Bluetooth dulu")
        val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP)
        try {
            socket.connect()
            socket.outputStream.apply {
                write(Receipt.escPos(text))
                flush()
            }
            Thread.sleep(400)
        } catch (e: IOException) {
            throw IllegalStateException("Gagal terhubung ke printer. Pastikan printer menyala dan dekat.")
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }
}
