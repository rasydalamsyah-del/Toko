package com.toko.kasir.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import com.toko.kasir.logic.CartLogic

class Repository(private val helper: DbHelper) {

    private fun Cursor.toProduct() = Product(
        id = getLong(getColumnIndexOrThrow("id")),
        barcode = getString(getColumnIndexOrThrow("barcode")) ?: "",
        name = getString(getColumnIndexOrThrow("name")),
        price = getLong(getColumnIndexOrThrow("price")),
        cost = getLong(getColumnIndexOrThrow("cost")),
        stock = getInt(getColumnIndexOrThrow("stock")),
        unit = getString(getColumnIndexOrThrow("unit")) ?: "pcs",
    )

    fun allProducts(): List<Product> =
        helper.readableDatabase.rawQuery(
            "SELECT * FROM products ORDER BY name COLLATE NOCASE", null
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.toProduct()) }
        }

    fun findByBarcode(code: String): Product? =
        helper.readableDatabase.rawQuery(
            "SELECT * FROM products WHERE barcode = ? LIMIT 1", arrayOf(code.trim())
        ).use { c -> if (c.moveToFirst()) c.toProduct() else null }

    /** Simpan barang baru (id = 0) atau ubah barang lama. */
    fun saveProduct(p: Product): Product {
        val cv = ContentValues().apply {
            val code = p.barcode.trim()
            if (code.isEmpty()) putNull("barcode") else put("barcode", code)
            put("name", p.name.trim())
            put("price", p.price)
            put("cost", p.cost)
            put("stock", p.stock)
            put("unit", p.unit.trim().ifEmpty { "pcs" })
        }
        val db = helper.writableDatabase
        try {
            return if (p.id == 0L) {
                p.copy(id = db.insertOrThrow("products", null, cv))
            } else {
                db.update("products", cv, "id = ?", arrayOf(p.id.toString()))
                p
            }
        } catch (e: SQLiteConstraintException) {
            throw IllegalArgumentException("Barcode ${p.barcode} sudah dipakai barang lain")
        }
    }

    fun deleteProduct(id: Long) {
        helper.writableDatabase.delete("products", "id = ?", arrayOf(id.toString()))
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
    fun checkout(items: List<CartItem>, paid: Long): Sale {
        require(items.isNotEmpty()) { "Keranjang masih kosong" }
        val total = CartLogic.total(items)
        require(paid >= total) { "Uang yang diterima kurang" }

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (ci in items) {
                val current = db.rawQuery(
                    "SELECT stock FROM products WHERE id = ?", arrayOf(ci.product.id.toString())
                ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }
                    ?: throw IllegalStateException("${ci.product.name} sudah dihapus dari daftar barang")
                if (current < ci.qty) {
                    throw IllegalStateException("Stok ${ci.product.name} tidak cukup (sisa $current)")
                }
            }

            val now = System.currentTimeMillis()
            val saleId = db.insertOrThrow("sales", null, ContentValues().apply {
                put("time", now)
                put("total", total)
                put("paid", paid)
                put("change_amount", paid - total)
            })
            for (ci in items) {
                db.insertOrThrow("sale_items", null, ContentValues().apply {
                    put("sale_id", saleId)
                    put("product_id", ci.product.id)
                    put("name", ci.product.name)
                    put("price", ci.product.price)
                    put("cost", ci.product.cost)
                    put("qty", ci.qty)
                })
                db.execSQL(
                    "UPDATE products SET stock = stock - ? WHERE id = ?",
                    arrayOf<Any>(ci.qty, ci.product.id)
                )
            }
            db.setTransactionSuccessful()
            return Sale(saleId, now, total, paid, paid - total, CartLogic.totalQty(items))
        } finally {
            db.endTransaction()
        }
    }

    fun saleItems(saleId: Long): List<SaleItem> =
        helper.readableDatabase.rawQuery(
            "SELECT name, price, qty FROM sale_items WHERE sale_id = ? ORDER BY id",
            arrayOf(saleId.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(SaleItem(c.getString(0), c.getLong(1), c.getInt(2))) }
        }

    fun report(from: Long): Report {
        val db = helper.readableDatabase
        val args = arrayOf(from.toString())

        val sales = db.rawQuery(
            """SELECT s.id, s.time, s.total, s.paid, s.change_amount,
                  (SELECT COALESCE(SUM(qty), 0) FROM sale_items WHERE sale_id = s.id)
               FROM sales s WHERE s.time >= ? ORDER BY s.time DESC""",
            args
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Sale(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getInt(5)))
                }
            }
        }

        val (laba, sold) = db.rawQuery(
            """SELECT COALESCE(SUM((si.price - si.cost) * si.qty), 0), COALESCE(SUM(si.qty), 0)
               FROM sale_items si JOIN sales s ON s.id = si.sale_id WHERE s.time >= ?""",
            args
        ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else 0L to 0 }

        val top = db.rawQuery(
            """SELECT si.name, SUM(si.qty) AS q
               FROM sale_items si JOIN sales s ON s.id = si.sale_id
               WHERE s.time >= ? GROUP BY si.product_id, si.name ORDER BY q DESC LIMIT 5""",
            args
        ).use { c -> buildList { while (c.moveToNext()) add(TopItem(c.getString(0), c.getInt(1))) } }

        return Report(
            omzet = sales.sumOf { it.total },
            laba = laba,
            itemsSold = sold,
            sales = sales,
            top = top,
        )
    }
}
