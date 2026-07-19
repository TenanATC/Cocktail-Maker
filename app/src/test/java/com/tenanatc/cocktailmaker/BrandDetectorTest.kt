package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.data.BrandDetector
import com.tenanatc.cocktailmaker.data.BrandTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandDetectorTest {

    private val detector = BrandDetector(TestData.data.brands)

    @Test
    fun `reads premium and value tequila off label text`() {
        val brands = detector.detect(
            """
            G4
            TEQUILA BLANCO
            100% DE AGAVE

            JOSE CUERVO
            ESPECIAL
            """.trimIndent()
        )
        val byId = brands.associateBy { it.id }
        assertTrue("expected G4", "g4" in byId)
        assertTrue("expected Jose Cuervo", "jose_cuervo" in byId)
        assertEquals(BrandTier.PREMIUM, byId.getValue("g4").tier)
        assertEquals(BrandTier.VALUE, byId.getValue("jose_cuervo").tier)
    }

    @Test
    fun `longer match beats generic fallback`() {
        val brands = detector.detect("JOHNNIE WALKER BLACK LABEL blended scotch whisky")
        assertEquals(listOf("jw_black"), brands.map { it.id })
    }

    @Test
    fun `distinct bottles at different positions are both kept`() {
        val brands = detector.detect("BLANTON'S single barrel ... JIM BEAM kentucky bourbon")
        assertEquals(setOf("blantons", "jim_beam"), brands.map { it.id }.toSet())
    }

    @Test
    fun `accents and case do not matter`() {
        val brands = detector.detect("espolòn reposado")
        assertEquals(listOf("espolon"), brands.map { it.id })
    }

    @Test
    fun `no false positives on unrelated text`() {
        assertTrue(detector.detect("shopping list: eggs, milk, bread").isEmpty())
        assertTrue(detector.detect("").isEmpty())
    }
}
