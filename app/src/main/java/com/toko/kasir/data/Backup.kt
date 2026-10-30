package com.toko.kasir.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Backup dan pulihkan seluruh database toko sebagai satu file. */
class Backup(private val context: Context, private val helper: DbHelper) {

    fun export(out: OutputStream) {
        // Pastikan semua perubahan sudah ditulis ke file utama sebelum disalin.
        helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        helper.close()
        context.getDatabasePath(DbHelper.NAME).inputStream().use { it.copyTo(out) }
    }

    fun restore(input: InputStream) {
        val tmp = File(context.cacheDir, "restore.db")
        try {
            tmp.outputStream().use { input.copyTo(it) }

            val header = ByteArray(16)
            val read = tmp.inputStream().use { it.read(header) }
            require(read == 16 && String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                "File ini bukan backup Kasir Toko"
            }
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('products', 'sales', 'sale_items')",
                    null
                ).use { c ->
                    require(c.moveToFirst() && c.getInt(0) == 3) { "File ini bukan backup Kasir Toko" }
                }
            }

            helper.close()
            val dbFile = context.getDatabasePath(DbHelper.NAME)
            listOf("-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            tmp.copyTo(dbFile, overwrite = true)
        } finally {
            tmp.delete()
        }
    }
}
