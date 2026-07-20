package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.data.RiffGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiffGeneratorTest {

    private val data = TestData.data
    private val generator = RiffGenerator(data)

    @Test
    fun `invents a smoky sour no recipe covers`() {
        val riffs = generator.generate(setOf("mezcal", "lemon_juice", "honey_syrup"))
        val sour = riffs.first { it.id.startsWith("riff_sour_mezcal") }
        assertEquals("Mezcal Honey Sour", sour.name)
        assertTrue(sour.tags.contains("riff"))
        // Fully makeable by construction: only uses what's on hand.
        assertTrue(sour.ingredients.all {
            it.ingredientId in setOf("mezcal", "lemon_juice", "honey_syrup")
        })
    }

    @Test
    fun `never duplicates an existing recipe`() {
        // Tequila + lime + agave is exactly Tommy's Margarita.
        val riffs = generator.generate(setOf("tequila", "lime_juice", "agave_syrup"))
        assertTrue(riffs.none { riff ->
            riff.ingredients.map { it.ingredientId }.toSet() ==
                setOf("tequila", "lime_juice", "agave_syrup")
        })
    }

    @Test
    fun `skips riffs that are stripped-down versions of makeable classics`() {
        // With rum, lime, and sugar on hand, the Daiquiri is makeable — a
        // "White Rum Sour" riff of the same three bottles would be noise.
        val riffs = generator.generate(setOf("white_rum", "lime_juice", "simple_syrup"))
        assertTrue(riffs.none { it.id.startsWith("riff_sour_white_rum") })
    }

    @Test
    fun `vetoed pairings are never generated`() {
        val riffs = generator.generate(setOf("gin", "scotch", "cola"))
        assertTrue(riffs.none { riff ->
            val ids = riff.ingredients.map { it.ingredientId }
            ("gin" in ids || "scotch" in ids) && "cola" in ids
        })
    }

    @Test
    fun `old fashioned template refuses unaged spirits`() {
        val riffs = generator.generate(setOf("vodka", "simple_syrup", "angostura_bitters"))
        assertTrue(riffs.none { it.id.startsWith("riff_old_fashioned_vodka") })
    }

    @Test
    fun `premium bottles are showcased in riff descriptions`() {
        val g4 = data.brands.filter { it.id == "g4" }.groupBy { it.ingredientId }
        val riffs = generator.generate(
            setOf("tequila", "lemon_juice", "honey_syrup"),
            brandsByIngredient = g4,
        )
        val sour = riffs.first { it.id.startsWith("riff_sour_tequila") }
        assertTrue(sour.description.contains("G4"))
    }

    @Test
    fun `results are capped and varied`() {
        // A loaded shelf must not flood the results.
        val riffs = generator.generate(
            setOf(
                "bourbon", "rye_whiskey", "scotch", "mezcal", "gin",
                "lime_juice", "lemon_juice", "simple_syrup", "honey_syrup",
                "orgeat", "angostura_bitters", "soda_water", "ginger_beer",
            )
        )
        assertTrue(riffs.size <= 6)
        assertEquals(riffs.size, riffs.map { it.id }.toSet().size)
        // No spirit dominates the list.
        val bySpirit = riffs.groupBy { it.ingredients.first().ingredientId }
        assertTrue(bySpirit.values.all { it.size <= 2 })
    }
}
