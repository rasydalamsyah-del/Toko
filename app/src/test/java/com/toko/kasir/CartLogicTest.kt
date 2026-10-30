package com.toko.kasir

import com.toko.kasir.data.CartItem
import com.toko.kasir.data.Product
import com.toko.kasir.logic.CartLogic
import com.toko.kasir.logic.CartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartLogicTest {

    private val indomie = Product(id = 1, barcode = "089686010947", name = "Indomie Goreng", price = 3_500, cost = 3_000, stock = 10)
    private val aqua = Product(id = 2, barcode = "8886008101053", name = "Aqua 600ml", price = 4_000, cost = 3_200, stock = 2)

    private fun ok(r: CartResult): List<CartItem> {
        assertTrue("Harusnya berhasil, tapi: $r", r is CartResult.Ok)
        return (r as CartResult.Ok).items
    }

    @Test
    fun tambahBarangBaruMasukKeranjang() {
        val cart = ok(CartLogic.add(emptyList(), indomie))
        assertEquals(1, cart.size)
        assertEquals(1, cart[0].qty)
    }

    @Test
    fun scanBarangSamaMenambahJumlahBukanBarisBaru() {
        var cart = ok(CartLogic.add(emptyList(), indomie))
        cart = ok(CartLogic.add(cart, indomie))
        cart = ok(CartLogic.add(cart, aqua))
        assertEquals(2, cart.size)
        assertEquals(2, cart.first { it.product.id == 1L }.qty)
    }

    @Test
    fun barangTerakhirDiscanNaikKePalingAtas() {
        var cart = ok(CartLogic.add(emptyList(), indomie))
        cart = ok(CartLogic.add(cart, aqua))
        assertEquals(2L, cart[0].product.id)
        // Scan indomie lagi: naik ke atas dengan jumlah 2
        cart = ok(CartLogic.add(cart, indomie))
        assertEquals(1L, cart[0].product.id)
        assertEquals(2, cart[0].qty)
        assertEquals(2, cart.size)
    }

    @Test
    fun tidakBolehMelebihiStok() {
        var cart = ok(CartLogic.add(emptyList(), aqua))
        cart = ok(CartLogic.add(cart, aqua))
        val r = CartLogic.add(cart, aqua)
        assertTrue(r is CartResult.StokKurang)
        assertEquals(2, (r as CartResult.StokKurang).available)
    }

    @Test
    fun stokHabisTidakBisaDijual() {
        val habis = indomie.copy(stock = 0)
        assertTrue(CartLogic.add(emptyList(), habis) is CartResult.StokKurang)
    }

    @Test
    fun aturJumlahNolMenghapusBarang() {
        val cart = ok(CartLogic.add(emptyList(), indomie))
        val after = ok(CartLogic.setQty(cart, indomie.id, 0))
        assertTrue(after.isEmpty())
    }

    @Test
    fun aturJumlahDiAtasStokDitolak() {
        val cart = ok(CartLogic.add(emptyList(), aqua))
        assertTrue(CartLogic.setQty(cart, aqua.id, 3) is CartResult.StokKurang)
    }

    @Test
    fun totalDanJumlahItem() {
        var cart = ok(CartLogic.add(emptyList(), indomie, 3)) // 10.500
        cart = ok(CartLogic.add(cart, aqua)) // 4.000
        assertEquals(14_500L, CartLogic.total(cart))
        assertEquals(4, CartLogic.totalQty(cart))
    }

    @Test
    fun kembalian() {
        assertEquals(5_500L, CartLogic.change(14_500, 20_000))
        assertEquals(0L, CartLogic.change(14_500, 14_500))
        assertNull(CartLogic.change(14_500, 10_000))
    }

    @Test
    fun usulanUangTunai() {
        assertEquals(listOf(15_000L, 20_000L, 50_000L, 100_000L), CartLogic.cashSuggestions(12_500))
        assertEquals(listOf(50_000L, 100_000L), CartLogic.cashSuggestions(20_000))
        assertTrue(CartLogic.cashSuggestions(0).isEmpty())
    }

    @Test
    fun pencarianBarang() {
        assertTrue(indomie.matches("indo"))
        assertTrue(indomie.matches("0896"))
        assertTrue(indomie.matches(""))
        assertTrue(!indomie.matches("aqua"))
    }
}
