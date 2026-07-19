package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.data.RecipeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMatcherTest {

    private val matcher = RecipeMatcher(TestData.data)

    @Test
    fun `empty hand matches nothing`() {
        assertTrue(matcher.match(emptySet()).isEmpty())
    }

    @Test
    fun `full margarita kit is ready to pour`() {
        val results = matcher.match(setOf("tequila", "lime_juice", "triple_sec"))
        val margarita = results.first { it.recipe.id == "margarita" }
        assertTrue(margarita.makeableNow)
        assertEquals(1.0, margarita.score, 1e-9)
        // A complete match should be ranked ahead of partial ones.
        assertTrue(results.indexOfFirst { it.recipe.id == "margarita" } < results.size)
        assertTrue(results.first().makeableNow)
    }

    @Test
    fun `missing lime falls back to lemon substitution`() {
        val results = matcher.match(setOf("tequila", "lemon_juice", "triple_sec"))
        val margarita = results.first { it.recipe.id == "margarita" }
        assertTrue(margarita.makeableNow)
        assertEquals(1, margarita.substitutions.size)
        assertEquals("lime_juice", margarita.substitutions[0].wanted.id)
        assertEquals("lemon_juice", margarita.substitutions[0].useInstead.id)
        assertTrue(margarita.score < 1.0)
    }

    @Test
    fun `direct match outranks substitution match`() {
        val results = matcher.match(setOf("tequila", "lemon_juice", "triple_sec", "brandy"))
        val sidecar = results.first { it.recipe.id == "sidecar" } // all direct
        val margarita = results.first { it.recipe.id == "margarita" } // lemon subbing for lime
        assertTrue(results.indexOf(sidecar) < results.indexOf(margarita))
    }

    @Test
    fun `recipes with too many missing ingredients are excluded`() {
        val results = matcher.match(setOf("vodka"))
        // Bloody Mary needs tomato and lemon juice too; with only vodka on hand
        // half of the required list is missing, so it must not appear.
        assertTrue(results.none { it.recipe.id == "bloody_mary" })
        // But two-ingredient vodka drinks may show up as near-misses or matches.
        assertTrue(results.any { it.recipe.id == "black_russian" || it.recipe.id == "screwdriver" })
    }

    @Test
    fun `optional ingredients never count against a match`() {
        // Whiskey Sour: egg white and bitters are optional.
        val results = matcher.match(setOf("bourbon", "lemon_juice", "simple_syrup"))
        val sour = results.first { it.recipe.id == "whiskey_sour" }
        assertTrue(sour.makeableNow)
        assertEquals(1.0, sour.score, 1e-9)
    }

    @Test
    fun `rye covers bourbon in an old fashioned`() {
        val results = matcher.match(setOf("rye_whiskey", "simple_syrup", "angostura_bitters"))
        val of = results.first { it.recipe.id == "old_fashioned" }
        assertTrue(of.makeableNow)
        assertEquals("rye_whiskey", of.substitutions.single().useInstead.id)
        // The Sazerac (rye, syrup, peychauds->angostura sub, absinthe missing) should
        // also surface as a near-miss.
        assertTrue(results.any { it.recipe.id == "sazerac" })
    }

    @Test
    fun `makeable recipes always sort before near misses`() {
        val results = matcher.match(setOf("gin", "lime_juice", "simple_syrup", "mint"))
        val firstMiss = results.indexOfFirst { !it.makeableNow }
        if (firstMiss >= 0) {
            assertTrue(results.drop(firstMiss).none { it.makeableNow })
        }
        assertTrue(results.first { it.recipe.id == "southside" }.makeableNow)
        assertTrue(results.first { it.recipe.id == "gimlet" }.makeableNow)
    }
}
