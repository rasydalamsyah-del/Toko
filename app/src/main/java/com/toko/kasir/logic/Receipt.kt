package com.toko.kasir.logic

import com.toko.kasir.data.PayMethod
import com.toko.kasir.data.Sale
import com.toko.kasir.data.SaleItem
import com.toko.kasir.data.StoreInfo
import java.io.ByteArrayOutputStream

/** Menyusun struk teks untuk dibagikan atau dicetak di printer thermal 58mm (32 kolom). */
object Receipt {

    fun build(store: StoreInfo, sale: Sale, items: List<SaleItem>, width: Int = 32): String {
        val sb = StringBuilder()
        val line: (String) -> Unit = { sb.append(it).append('\n') }
        val center: (String) -> Unit = { text ->
            wrap(text, width).forEach { l -> line(" ".repeat(((width - l.length) / 2).coerceAtLeast(0)) + l) }
        }
        val lr: (String, String) -> Unit = { l, r ->
            val space = width - l.length - r.length
            if (space >= 1) {
                line(l + " ".repeat(space) + r)
            } else {
                wrap(l, width).forEach(line)
                line(" ".repeat((width - r.length).coerceAtLeast(0)) + r)
            }
        }
        val divider = "-".repeat(width)

        center(store.name.ifBlank { "Toko" })
        if (store.address.isNotBlank()) center(store.address)
        if (store.phone.isNotBlank()) center("Telp " + store.phone)
        line(divider)
        lr("No. ${sale.id}", Waktu.struk(sale.time))
        if (sale.canceled) center("*** DIBATALKAN ***")
        line(divider)
        items.forEach { item ->
            wrap(item.name, width).forEach(line)
            lr("  ${item.qty} x ${Rupiah.plain(item.price)}", Rupiah.plain(item.subtotal))
        }
        line(divider)
        lr("TOTAL", Rupiah.format(sale.total))
        lr("Bayar (${sale.method.label})", Rupiah.format(sale.paid))
        if (sale.method == PayMethod.TUNAI) lr("Kembali", Rupiah.format(sale.change))
        line(divider)
        if (store.footer.isNotBlank()) center(store.footer)
        return sb.toString()
    }

    /** Memecah teks per kata agar tiap baris paling panjang [width] karakter. */
    fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            var w = word
            while (w.length > width) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                lines += w.take(width)
                w = w.drop(width)
            }
            if (w.isEmpty()) return@forEach
            if (current.isEmpty()) {
                current.append(w)
            } else if (current.length + 1 + w.length <= width) {
                current.append(' ').append(w)
            } else {
                lines += current.toString()
                current = StringBuilder(w)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    /** Perintah ESC/POS: reset printer, teks, beberapa baris kosong, lalu potong kertas. */
    fun escPos(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x1B); out.write(0x40)
        val ascii = text.map { c -> if (c == '\n' || c.code in 32..126) c else '?' }.joinToString("")
        out.write(ascii.toByteArray(Charsets.US_ASCII))
        out.write("\n\n\n".toByteArray(Charsets.US_ASCII))
        out.write(0x1D); out.write(0x56); out.write(0x42); out.write(0x00)
        return out.toByteArray()
    }
}
