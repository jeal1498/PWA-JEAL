package com.jeal.smartwidget.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogBuilderTest {
    private val installed = mapOf(
        "wa" to "WhatsApp", "maps" to "Maps", "uber" to "Uber Driver", "spot" to "Spotify",
        "yt" to "YouTube", "chrome" to "Chrome", "gmail" to "Gmail", "acme" to "Ácme", "zoom" to "Zoom",
    )
    private val scores = mapOf("uber" to 0.9, "maps" to 0.8, "spot" to 0.7, "yt" to 0.6, "wa" to 0.5)

    private fun build(
        flags: Map<String, AppFlags> = emptyMap(), count: Int = 3, showAll: Boolean = true,
        prev: List<String> = emptyList(), sc: Map<String, Double> = scores,
    ) = CatalogBuilder.build(CatalogInput(installed, flags, sc, count, showAll, prev))

    @Test fun ordenFavoritosSugeridasAlfabetico() {
        val r = build(flags = mapOf("zoom" to AppFlags(favorite = true, favOrder = 1)))
        val pkgs = r.items.map { it.pkg }
        assertEquals(listOf("zoom", "uber", "maps"), pkgs.take(3))
        // resto alfabético, acentos ignorados (Ácme antes que Chrome)
        assertEquals(listOf("acme", "chrome", "gmail", "spot", "wa", "yt"), pkgs.drop(3))
    }

    @Test fun sinDuplicados() {
        val r = build(flags = mapOf("uber" to AppFlags(favorite = true)), count = 5)
        assertEquals(r.items.size, r.items.map { it.pkg }.toSet().size)
        assertEquals(installed.size, r.items.size)
    }

    @Test fun excluidaNoSeRecomiendaPeroSaleEnAlfabetico() {
        val r = build(flags = mapOf("uber" to AppFlags(excluded = true)))
        assertFalse("uber" in r.suggestions)
        assertEquals(Section.ALPHABETIC, r.items.first { it.pkg == "uber" }.section)
    }

    @Test fun desactivadaNoSaleEnNingunLado() {
        val r = build(flags = mapOf("maps" to AppFlags(disabled = true, favorite = true)))
        assertFalse(r.items.any { it.pkg == "maps" })
    }

    @Test fun mostrarTodasApagado() {
        val r = build(showAll = false)
        assertEquals(listOf("uber", "maps", "spot"), r.items.map { it.pkg })
    }

    @Test fun sinDatosNiFavoritosNoQuedaVacio() {
        val r = build(showAll = false, sc = emptyMap())
        assertTrue(r.fallback)
        assertEquals(installed.size, r.items.size)
    }

    @Test fun cantidadDeSugeridasConfigurable() {
        assertEquals(5, build(count = 5).suggestions.size)
        assertEquals(0, build(count = 0).suggestions.size)
    }

    @Test fun estabilidadConservaLugarYNoCambiaPorPoco() {
        // Antes: maps, uber, spot. Ahora "yt" (0.6) apenas cerca de "spot" (0.7) → spot se queda.
        val prev = listOf("maps", "uber", "spot")
        val r = build(prev = prev, sc = mapOf("uber" to 0.9, "maps" to 0.8, "spot" to 0.62, "yt" to 0.65))
        assertEquals(prev, r.suggestions)
    }

    @Test fun estabilidadCambiaSiLaDiferenciaEsGrande() {
        val prev = listOf("maps", "uber", "spot")
        val r = build(prev = prev, sc = mapOf("uber" to 0.9, "maps" to 0.8, "spot" to 0.2, "yt" to 0.7))
        assertEquals(listOf("maps", "uber", "yt"), r.suggestions)
    }
}
