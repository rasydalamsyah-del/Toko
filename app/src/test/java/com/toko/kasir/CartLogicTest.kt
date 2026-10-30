package com.toko.kasir

import com.toko.kasir.data.CartItem
import com.toko.kasir.data.Product
import com.toko.kasir.data.SaleUnit
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.CartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartLogicTest {

    private val indomie = Product(id = 1, barcode = "089686010947", name = "Indomie Goreng", price = 3_500, cost = 3_000, stock = 10)
    private val aqua = Product(id = 2, barcode = "8886008101053", name = "Aqua 600ml", price = 4_000, cost = 3_200, stock = 2)

    // Rokok: dasar batang, bungkus isi 16, slop isi 160. Stok 10 bungkus = 160 batang.
    private val bungkus = SaleUnit(11, "bungkus", 16, 30_000, 28_000, "8999909096004")
    private val slop = SaleUnit(12, "slop", 160, 295_000, 280_000, "8999909096011")
    private val rokok = Product(
        id = 3, name = "Rokok X", price = 2_000, cost = 1_750, stock = 160, unit = "batang",
        units = listOf(slop, bungkus),
    )

    // Minyak: dasar gram, tidak dijual per gram. ¼ kg, ½ kg, 1 kg. Stok 5 kg.
    private val seperempat = SaleUnit(21, "¼ kg", 250, 5_000, 4_250)
    private val setengah = SaleUnit(22, "½ kg", 500, 9_500, 8_500)
    private val sekilo = SaleUnit(23, "1 kg", 1000, 18_000, 17_000)
    private val minyak = Product(
        id = 4, name = "Minyak goreng", price = 0, stock = 5000, unit = "gram",
        units = listOf(sekilo, seperempat, setengah), allowNominal = true,
    )

    private fun ok(r: CartResult): List<CartItem> {
        assertTrue("Harusnya berhasil, tapi: $r", r is CartResult.Ok)
        return (r as CartResult.Ok).items
    }

    private fun add(items: List<CartItem>, p: Product, qty: Int = 1) =
        CartLogic.add(items, p, p.saleUnits.first(), qty)

    // ---------- Satu satuan (seperti sebelumnya) ----------

    @Test
    fun tambahBarangBaruMasukKeranjang() {
        val cart = ok(add(emptyList(), indomie))
        assertEquals(1, cart.size)
        assertEquals(1, cart[0].qty)
    }

    @Test
    fun barangTerakhirDiscanNaikKePalingAtas() {
        var cart = ok(add(emptyList(), indomie))
        cart = ok(add(cart, aqua))
        assertEquals(2L, cart[0].product.id)
        cart = ok(add(cart, indomie))
        assertEquals(1L, cart[0].product.id)
        assertEquals(2, cart[0].qty)
        assertEquals(2, cart.size)
    }

    @Test
    fun tidakBolehMelebihiStok() {
        var cart = ok(add(emptyList(), aqua))
        cart = ok(add(cart, aqua))
        val r = add(cart, aqua)
        assertTrue(r is CartResult.StokKurang)
        assertEquals(0, (r as CartResult.StokKurang).available)
    }

    @Test
    fun aturJumlahNolMenghapusBaris() {
        val cart = ok(add(emptyList(), indomie))
        assertTrue(ok(CartLogic.setQty(cart, cart[0].key, 0)).isEmpty())
    }

    @Test
    fun totalDanKembalian() {
        var cart = ok(add(emptyList(), indomie, 3)) // 10.500
        cart = ok(add(cart, aqua)) // 4.000
        assertEquals(14_500L, CartLogic.total(cart))
        assertEquals(4, CartLogic.totalQty(cart))
        assertEquals(5_500L, CartLogic.change(14_500, 20_000))
        assertNull(CartLogic.change(14_500, 10_000))
    }

    @Test
    fun usulanUangTunai() {
        assertEquals(listOf(15_000L, 20_000L, 50_000L, 100_000L), CartLogic.cashSuggestions(12_500))
        assertEquals(listOf(50_000L, 100_000L), CartLogic.cashSuggestions(20_000))
    }

    // ---------- Multi-satuan ----------

    @Test
    fun satuanJualUrutDariTerkecil() {
        assertEquals(listOf("batang", "bungkus", "slop"), rokok.saleUnits.map { it.name })
        // Minyak tidak dijual per gram
        assertEquals(listOf("¼ kg", "½ kg", "1 kg"), minyak.saleUnits.map { it.name })
        assertTrue(rokok.needsUnitChoice)
        assertFalse(indomie.needsUnitChoice)
    }

    @Test
    fun bungkusDanBatangBerbagiStokYangSama() {
        var cart = ok(CartLogic.add(emptyList(), rokok, bungkus, 9)) // 144 batang
        cart = ok(CartLogic.add(cart, rokok, rokok.baseUnit, 16)) // total 160 batang
        assertEquals(2, cart.size)
        assertEquals(30_000L * 9 + 2_000L * 16, CartLogic.total(cart))
        // Sisa 0: tambah 1 batang lagi ditolak (baris batang yang sudah ada ikut dihitung)
        val r = CartLogic.add(cart, rokok, rokok.baseUnit)
        assertTrue(r is CartResult.StokKurang)
        assertEquals(0, (r as CartResult.StokKurang).available)
    }

    @Test
    fun slopDitolakJikaSudahAdaBungkusDiKeranjang() {
        val cart = ok(CartLogic.add(emptyList(), rokok, bungkus))
        val r = CartLogic.add(cart, rokok, slop)
        assertTrue(r is CartResult.StokKurang)
        assertEquals(144, (r as CartResult.StokKurang).available)
    }

    @Test
    fun gantiSatuanBungkusJadiBatang() {
        var cart = ok(CartLogic.add(emptyList(), rokok, bungkus, 2))
        cart = ok(CartLogic.changeUnit(cart, cart[0].key, rokok.baseUnit))
        assertEquals(1, cart.size)
        assertEquals("batang", cart[0].unit.name)
        assertEquals(2, cart[0].qty)
        assertEquals(4_000L, CartLogic.total(cart))
    }

    @Test
    fun gantiSatuanDigabungDenganBarisYangSudahAda() {
        var cart = ok(CartLogic.add(emptyList(), rokok, rokok.baseUnit, 3))
        cart = ok(CartLogic.add(cart, rokok, bungkus, 1))
        cart = ok(CartLogic.changeUnit(cart, cart[0].key, rokok.baseUnit)) // 1 bungkus -> 1 batang
        assertEquals(1, cart.size)
        assertEquals(4, cart[0].qty)
    }

    @Test
    fun teksStokDipecahPerSatuan() {
        assertEquals("1 slop", rokok.stockText(160))
        assertEquals("8 bungkus + 13 batang", rokok.stockText(141))
        assertEquals("5 batang", rokok.stockText(5))
        assertEquals("2 × 1 kg + 350 gram", minyak.stockText(2350))
        assertEquals("10 pcs", indomie.stockText())
    }

    @Test
    fun stokMenipisMengikutiSatuanTerkecil() {
        assertTrue(rokok.copy(stock = 5).isLow)
        assertFalse(rokok.copy(stock = 6).isLow)
        // Minyak: menipis bila ≤ 5 × 250 gram
        assertTrue(minyak.copy(stock = 1250).isLow)
        assertFalse(minyak.copy(stock = 1300).isLow)
    }

    // ---------- Jual sesuai nominal ----------

    @Test
    fun nominalMinyakDihitungDariHargaPerKilo() {
        // Rp18.000/kg = Rp18/gram -> Rp7.000 = 388 gram (dibulatkan ke bawah)
        val u = CartLogic.nominalUnit(minyak, 7_000, -5)!!
        assertEquals(388, u.factor)
        assertEquals(7_000L, u.price)
        assertEquals("388 gram", u.name)
        assertEquals(6_596L, u.cost) // 17 x 388

        val cart = ok(CartLogic.add(emptyList(), minyak, u))
        assertTrue(cart[0].isNominal)
        assertEquals(388, cart[0].baseQty)
        assertEquals(7_000L, CartLogic.total(cart))
    }

    @Test
    fun nominalTerlaluKecilDitolak() {
        assertNull(CartLogic.nominalUnit(minyak, 10, -1))
        assertNull(CartLogic.nominalUnit(minyak, 0, -1))
    }

    @Test
    fun nominalTidakBolehMelebihiStok() {
        val besar = CartLogic.nominalUnit(minyak, 100_000, -2)!! // 5.555 gram > 5.000
        assertTrue(CartLogic.add(emptyList(), minyak, besar) is CartResult.StokKurang)
    }

    @Test
    fun pencarianJugaCocokDenganBarcodeSatuan() {
        assertTrue(rokok.matches("8999909096011"))
        assertTrue(indomie.matches("indo"))
        assertFalse(indomie.matches("aqua"))
    }
}
