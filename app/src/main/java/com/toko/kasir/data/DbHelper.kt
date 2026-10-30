package com.toko.kasir.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "toko.db"
        const val VERSION = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT UNIQUE,
                name TEXT NOT NULL,
                price INTEGER NOT NULL DEFAULT 0,
                cost INTEGER NOT NULL DEFAULT 0,
                stock INTEGER NOT NULL DEFAULT 0,
                unit TEXT NOT NULL DEFAULT 'pcs',
                allow_nominal INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE sales(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                total INTEGER NOT NULL,
                paid INTEGER NOT NULL,
                change_amount INTEGER NOT NULL,
                method TEXT NOT NULL DEFAULT 'TUNAI',
                status INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL(
            """CREATE TABLE sale_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sale_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                price INTEGER NOT NULL,
                cost INTEGER NOT NULL,
                qty INTEGER NOT NULL,
                unit_name TEXT NOT NULL DEFAULT '',
                base_qty INTEGER NOT NULL DEFAULT 0)"""
        )
        createUnitsTable(db)
        db.execSQL("CREATE INDEX idx_sales_time ON sales(time)")
        db.execSQL("CREATE INDEX idx_items_sale ON sale_items(sale_id)")
    }

    private fun createUnitsTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE product_units(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                factor INTEGER NOT NULL,
                price INTEGER NOT NULL,
                cost INTEGER NOT NULL DEFAULT 0,
                barcode TEXT UNIQUE)"""
        )
        db.execSQL("CREATE INDEX idx_units_product ON product_units(product_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2: metode pembayaran dan status batal (0 = sah, 1 = dibatalkan).
            db.execSQL("ALTER TABLE sales ADD COLUMN method TEXT NOT NULL DEFAULT 'TUNAI'")
            db.execSQL("ALTER TABLE sales ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            // v3: multi-satuan. Barang lama otomatis jadi barang satu satuan.
            createUnitsTable(db)
            db.execSQL("ALTER TABLE products ADD COLUMN allow_nominal INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sale_items ADD COLUMN unit_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sale_items ADD COLUMN base_qty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE sale_items SET base_qty = qty")
        }
    }
}
