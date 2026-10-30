package com.toko.kasir.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("toko_settings", Context.MODE_PRIVATE)

    var store: StoreInfo
        get() {
            val d = StoreInfo()
            return StoreInfo(
                name = prefs.getString("name", null) ?: d.name,
                address = prefs.getString("address", null) ?: d.address,
                phone = prefs.getString("phone", null) ?: d.phone,
                footer = prefs.getString("footer", null) ?: d.footer,
            )
        }
        set(v) {
            prefs.edit()
                .putString("name", v.name)
                .putString("address", v.address)
                .putString("phone", v.phone)
                .putString("footer", v.footer)
                .apply()
        }

    var printerName: String?
        get() = prefs.getString("printer_name", null)
        set(v) = prefs.edit().putString("printer_name", v).apply()

    var printerAddress: String?
        get() = prefs.getString("printer_address", null)
        set(v) = prefs.edit().putString("printer_address", v).apply()
}
