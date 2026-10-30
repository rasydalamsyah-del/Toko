# Kasir Toko

Aplikasi kasir Android (Kotlin + Jetpack Compose) untuk toko kecil, dengan scan barcode lewat kamera.

## Fitur

| Halaman | Isi |
|---|---|
| **Kasir** | Scan barcode dengan kamera (senter bisa dinyalakan). Barang terakhir discan naik ke atas dan disorot. Jumlah bisa diketik langsung. Bayar tunai (hitung kembalian), QRIS (tampilkan QR toko), atau transfer. Struk bisa dibagikan ke WhatsApp atau dicetak ke printer Bluetooth. Mendukung scanner barcode USB/Bluetooth. |
| **Barang** | Tambah, ubah, hapus barang. **Multi-satuan**: satu barang bisa dijual per batang/bungkus/slop atau ¼ kg/½ kg/1 kg dengan harga dan barcode masing-masing, stok tetap satu. Bisa juga **jual sesuai nominal** (mis. minyak Rp7.000). |
| **Stok** | Ringkasan barang menipis/habis, nilai stok, cari atau scan barang, tombol +/− cepat, restok (tambah atau atur jumlah). |
| **Laporan** | Omzet, laba kotor, rincian per metode bayar, barang terlaris, riwayat transaksi. Transaksi bisa dibatalkan (stok kembali) dan struknya dicetak ulang. |
| **Toko** | Info toko untuk struk, gambar QRIS, pilih printer, impor barang (daftar harga bawaan 2.892 barang atau file CSV, dicek dulu sebelum disimpan), backup/pulihkan data, ekspor transaksi ke Excel (CSV). |

Katalog bawaan berisi ±25 ribu barcode: saat scan barang yang belum terdaftar, nama (dan harga saran bila ada) terisi otomatis.

Stok berkurang otomatis setiap transaksi. Semua data tersimpan di HP (SQLite), tanpa internet.

## Kenapa scan-nya cepat

- Pemindai **Google ML Kit** (model dibundel, jalan offline).
- **CameraX** dengan autofokus kontinu dan resolusi analisis 1280×720.
- Frame lama dibuang (`KEEP_ONLY_LATEST`), jadi tidak ada antrean yang bikin lambat.
- Barcode yang sama tidak terhitung dua kali selama masih terlihat kamera.

## Cara pasang

1. Buka tab **Releases** di repo ini dari HP.
2. Unduh file `KasirToko-v1.0.x.apk` terbaru.
3. Buka file itu, izinkan "Instal dari sumber ini", lalu instal.

Versi baru bisa langsung dipasang menimpa versi lama, data tidak hilang.

## Build sendiri

Setiap push ke `main` otomatis menjalankan unit test dan membuat APK lewat GitHub Actions.
Lokal: buka folder ini di Android Studio, atau jalankan `./gradlew assembleRelease`.

> Kunci tanda tangan (`app/kasir.keystore`) sengaja disimpan di repo supaya setiap build bisa
> menimpa versi sebelumnya. Jika repo ini publik dan aplikasi mau disebar luas, sebaiknya
> pindahkan kunci ke GitHub Secrets.
