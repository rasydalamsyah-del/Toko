package com.toko.kasir.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import com.toko.kasir.logic.CartLogic
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Repository(private val helper: DbHelper) {

    private fun Cursor.toProduct() = Product(
        id = getLong(getColumnIndexOrThrow("id")),
        barcode = getString(getColumnIndexOrThrow("barcode")) ?: "",
        name = getString(getColumnIndexOrThrow("name")),
        price = getLong(getColumnIndexOrThrow("price")),
        cost = getLong(getColumnIndexOrThrow("cost")),
        stock = getInt(getColumnIndexOrThrow("stock")),
        unit = getString(getColumnIndexOrThrow("unit")) ?: "pcs",
        allowNominal = getInt(getColumnIndexOrThrow("allow_nominal")) != 0,
    )

    fun allProducts(): List<Product> {
        val db = helper.readableDatabase
        val units = db.rawQuery(
            "SELECT id, product_id, name, factor, price, cost, barcode FROM product_units ORDER BY factor",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        c.getLong(1) to SaleUnit(
                            id = c.getLong(0),
                            name = c.getString(2),
                            factor = c.getInt(3),
                            price = c.getLong(4),
                            cost = c.getLong(5),
                            barcode = c.getString(6) ?: "",
                        )
                    )
                }
            }
        }.groupBy({ it.first }, { it.second })

        return db.rawQuery("SELECT * FROM products ORDER BY name COLLATE NOCASE", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val p = c.toProduct()
                    add(p.copy(units = units[p.id].orEmpty()))
                }
            }
        }
    }

    /** Cari barang beserta satuan yang barcode-nya cocok (barcode bungkus, slop, dll.). */
    fun findByBarcode(code: String): Pair<Product, SaleUnit>? {
        val c = code.trim()
        if (c.isEmpty()) return null
        for (p in allProducts()) {
            if (p.barcode == c) {
                val unit = if (p.price > 0) p.baseUnit else p.saleUnits.firstOrNull() ?: p.baseUnit
                return p to unit
            }
            p.units.firstOrNull { it.barcode == c }?.let { return p to it }
        }
        return null
    }

    /** Simpan barang baru (id = 0) atau ubah barang lama, termasuk semua satuan jualnya. */
    fun saveProduct(p: Product): Product {
        val codes = (listOf(p.barcode) + p.units.map { it.barcode })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(codes.size == codes.toSet().size) { "Ada barcode yang sama di beberapa satuan barang ini" }

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (code in codes) {
                val usedBy = db.rawQuery(
                    """SELECT name FROM products WHERE barcode = ? AND id <> ?
                       UNION ALL
                       SELECT p.name FROM product_units u JOIN products p ON p.id = u.product_id
                       WHERE u.barcode = ? AND u.product_id <> ?""",
                    arrayOf(code, p.id.toString(), code, p.id.toString())
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                if (usedBy != null) throw IllegalArgumentException("Barcode $code sudah dipakai \"$usedBy\"")
            }

            val cv = ContentValues().apply {
                val code = p.barcode.trim()
                if (code.isEmpty()) putNull("barcode") else put("barcode", code)
                put("name", p.name.trim())
                put("price", p.price)
                put("cost", p.cost)
                put("stock", p.stock)
                put("unit", p.unit.trim().ifEmpty { "pcs" })
                put("allow_nominal", if (p.allowNominal) 1 else 0)
            }
            val id = if (p.id == 0L) {
                db.insertOrThrow("products", null, cv)
            } else {
                db.update("products", cv, "id = ?", arrayOf(p.id.toString()))
                p.id
            }

            db.delete("product_units", "product_id = ?", arrayOf(id.toString()))
            for (u in p.units) {
                db.insertOrThrow("product_units", null, ContentValues().apply {
                    put("product_id", id)
                    put("name", u.name.trim())
                    put("factor", u.factor)
                    put("price", u.price)
                    put("cost", u.cost)
                    val code = u.barcode.trim()
                    if (code.isEmpty()) putNull("barcode") else put("barcode", code)
                })
            }
            db.setTransactionSuccessful()
            return p.copy(id = id)
        } catch (e: SQLiteConstraintException) {
            throw IllegalArgumentException("Barcode sudah dipakai barang lain")
        } finally {
            db.endTransaction()
        }
    }

    fun deleteProduct(id: Long) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("product_units", "product_id = ?", arrayOf(id.toString()))
            db.delete("products", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun adjustStock(id: Long, delta: Int) {
        helper.writableDatabase.execSQL(
            "UPDATE products SET stock = MAX(stock + ?, 0) WHERE id = ?",
            arrayOf<Any>(delta, id)
        )
    }

    fun setStock(id: Long, stock: Int) {
        helper.writableDatabase.execSQL(
            "UPDATE products SET stock = ? WHERE id = ?",
            arrayOf<Any>(stock.coerceAtLeast(0), id)
        )
    }

    /** Menyimpan transaksi dan mengurangi stok dalam satu transaksi database. */
    fun checkout(items: List<CartItem>, paid: Long, method: PayMethod): Sale {
        require(items.isNotEmpty()) { "Keranjang masih kosong" }
        val total = CartLogic.total(items)
        val actualPaid = if (method == PayMethod.TUNAI) paid else total
        require(actualPaid >= total) { "Uang yang diterima kurang" }

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            // Cek stok per barang (jumlah semua satuan dalam satuan dasar).
            for ((product, lines) in items.groupBy { it.product.id }.mapKeys { it.value.first().product }) {
                val needed = lines.sumOf { it.baseQty }
                val current = db.rawQuery(
                    "SELECT stock FROM products WHERE id = ?", arrayOf(product.id.toString())
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
                    ?: throw IllegalStateException("${product.name} sudah dihapus dari daftar barang")
                if (current < needed) {
                    throw IllegalStateException(
                        "Stok ${product.name} tidak cukup (sisa ${product.stockText(current)})"
                    )
                }
            }

            val now = System.currentTimeMillis()
            val saleId = db.insertOrThrow("sales", null, ContentValues().apply {
                put("time", now)
                put("total", total)
                put("paid", actualPaid)
                put("change_amount", actualPaid - total)
                put("method", method.name)
                put("status", 0)
            })
            for (ci in items) {
                db.insertOrThrow("sale_items", null, ContentValues().apply {
                    put("sale_id", saleId)
                    put("product_id", ci.product.id)
                    put("name", ci.product.name)
                    put("price", ci.unit.price)
                    put("cost", ci.unit.cost)
                    put("qty", ci.qty)
                    put("unit_name", ci.unit.name)
                    put("base_qty", ci.baseQty)
                })
                db.execSQL(
                    "UPDATE products SET stock = stock - ? WHERE id = ?",
                    arrayOf<Any>(ci.baseQty, ci.product.id)
                )
            }
            db.setTransactionSuccessful()
            return Sale(
                saleId, now, total, actualPaid, actualPaid - total,
                CartLogic.totalQty(items), method, canceled = false,
            )
        } finally {
            db.endTransaction()
        }
    }

    /** Membatalkan transaksi: status jadi batal dan stok barang dikembalikan. */
    fun cancelSale(saleId: Long) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val status = db.rawQuery(
                "SELECT status FROM sales WHERE id = ?", arrayOf(saleId.toString())
            ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
                ?: throw IllegalStateException("Transaksi tidak ditemukan")
            if (status != 0) throw IllegalStateException("Transaksi ini sudah dibatalkan")

            db.execSQL(
                """UPDATE products SET stock = stock +
                     (SELECT COALESCE(SUM(si.base_qty), 0) FROM sale_items si
                      WHERE si.sale_id = ? AND si.product_id = products.id)
                   WHERE id IN (SELECT product_id FROM sale_items WHERE sale_id = ?)""",
                arrayOf<Any>(saleId, saleId)
            )
            db.execSQL("UPDATE sales SET status = 1 WHERE id = ?", arrayOf<Any>(saleId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun saleItems(saleId: Long): List<SaleItem> =
        helper.readableDatabase.rawQuery(
            "SELECT name, price, qty, unit_name FROM sale_items WHERE sale_id = ? ORDER BY id",
            arrayOf(saleId.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(SaleItem(c.getString(0), c.getLong(1), c.getInt(2), c.getString(3) ?: ""))
                }
            }
        }

    fun report(from: Long): Report {
        val db = helper.readableDatabase
        val args = arrayOf(from.toString())

        val sales = db.rawQuery(
            """SELECT s.id, s.time, s.total, s.paid, s.change_amount,
                  (SELECT COALESCE(SUM(qty), 0) FROM sale_items WHERE sale_id = s.id),
                  s.method, s.status
               FROM sales s WHERE s.time >= ? ORDER BY s.time DESC""",
            args
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Sale(
                            id = c.getLong(0), time = c.getLong(1), total = c.getLong(2),
                            paid = c.getLong(3), change = c.getLong(4), itemCount = c.getInt(5),
                            method = PayMethod.of(c.getString(6)), canceled = c.getInt(7) != 0,
                        )
                    )
                }
            }
        }

        val (laba, sold) = db.rawQuery(
            """SELECT COALESCE(SUM((si.price - si.cost) * si.qty), 0), COALESCE(SUM(si.qty), 0)
               FROM sale_items si JOIN sales s ON s.id = si.sale_id
               WHERE s.time >= ? AND s.status = 0""",
            args
        ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else 0L to 0 }

        val top = db.rawQuery(
            """SELECT si.name, SUM(si.base_qty) AS q, COALESCE(p.unit, '')
               FROM sale_items si JOIN sales s ON s.id = si.sale_id
               LEFT JOIN products p ON p.id = si.product_id
               WHERE s.time >= ? AND s.status = 0
               GROUP BY si.product_id, si.name ORDER BY SUM(si.price * si.qty) DESC LIMIT 5""",
            args
        ).use { c ->
            buildList { while (c.moveToNext()) add(TopItem(c.getString(0), c.getInt(1), c.getString(2) ?: "")) }
        }

        val valid = sales.filterNot { it.canceled }
        val byMethod = PayMethod.entries.mapNotNull { m ->
            val sum = valid.filter { it.method == m }.sumOf { it.total }
            if (sum > 0) m to sum else null
        }

        return Report(
            omzet = valid.sumOf { it.total },
            laba = laba,
            itemsSold = sold,
            count = valid.size,
            sales = sales,
            top = top,
            byMethod = byMethod,
        )
    }

    /** Ekspor semua transaksi ke CSV (dipisah titik koma, terbaca di Excel Indonesia). */
    fun exportCsv(out: OutputStream) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("sep=;\n")
            w.write("No;Waktu;Metode;Status;Barang;Satuan;Jumlah;Harga;Subtotal;Total transaksi\n")
            helper.readableDatabase.rawQuery(
                """SELECT s.id, s.time, s.method, s.status, si.name, si.qty, si.price, s.total, si.unit_name
                   FROM sales s JOIN sale_items si ON si.sale_id = s.id
                   ORDER BY s.time, si.id""",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val qty = c.getInt(5)
                    val price = c.getLong(6)
                    w.write(
                        listOf(
                            c.getLong(0).toString(),
                            fmt.format(Date(c.getLong(1))),
                            PayMethod.of(c.getString(2)).label,
                            if (c.getInt(3) != 0) "Dibatalkan" else "Sah",
                            esc(c.getString(4)),
                            esc(c.getString(8) ?: ""),
                            qty.toString(),
                            price.toString(),
                            (price * qty).toString(),
                            c.getLong(7).toString(),
                        ).joinToString(";") + "\n"
                    )
                }
            }
        }
    }
}
