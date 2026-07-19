package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.data.CocktailData
import com.tenanatc.cocktailmaker.data.CocktailParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Loads the real bundled assets so the dictionaries are validated on every test run. */
object TestData {
    val data: CocktailData by lazy {
        val assets = File("src/main/assets")
        CocktailParser.parse(
            File(assets, "ingredients.json").readText(),
            File(assets, "cocktails.json").readText(),
            File(assets, "brands.json").readText(),
        )
    }
}

class CocktailDataTest {

    private val data = TestData.data

    @Test
    fun `dictionaries are non-trivial`() {
        assertTrue("expected a real ingredient dictionary", data.ingredients.size >= 40)
        assertTrue("expected a real recipe book", data.recipes.size >= 50)
        assertTrue("expected substitution rules", data.substitutions.size >= 20)
    }

    @Test
    fun `ingredient ids are unique`() {
        val ids = data.ingredients.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `recipe ids are unique`() {
        val ids = data.recipes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every recipe ingredient exists in the dictionary`() {
        for (recipe in data.recipes) {
            for (line in recipe.ingredients) {
                assertTrue(
                    "${recipe.id} references unknown ingredient '${line.ingredientId}'",
                    line.ingredientId in data.ingredientsById,
                )
            }
        }
    }

    @Test
    fun `every substitution references known ingredients`() {
        for (sub in data.substitutions) {
            assertTrue("unknown missing id '${sub.missingId}'", sub.missingId in data.ingredientsById)
            assertTrue("unknown useInstead id '${sub.useInsteadId}'", sub.useInsteadId in data.ingredientsById)
            assertTrue("self-substitution for '${sub.missingId}'", sub.missingId != sub.useInsteadId)
        }
    }

    @Test
    fun `pantry ids exist`() {
        for (id in data.pantry) {
            assertTrue("unknown pantry id '$id'", id in data.ingredientsById)
        }
    }

    @Test
    fun `every recipe has at least one required non-pantry ingredient and instructions`() {
        for (recipe in data.recipes) {
            val required = recipe.ingredients.filter { !it.optional && it.ingredientId !in data.pantry }
            assertTrue("${recipe.id} has no required ingredients", required.isNotEmpty())
            assertTrue("${recipe.id} has no instructions", recipe.instructions.isNotEmpty())
        }
    }

    @Test
    fun `brand catalog is well-formed`() {
        assertTrue("expected a real brand catalog", data.brands.size >= 60)
        val ids = data.brands.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (brand in data.brands) {
            assertTrue(
                "brand '${brand.id}' references unknown ingredient '${brand.ingredientId}'",
                brand.ingredientId in data.ingredientsById,
            )
            assertTrue("brand '${brand.id}' has no keywords", brand.keywords.isNotEmpty())
        }
    }

    @Test
    fun `aliases never collide across different ingredients`() {
        val seen = mutableMapOf<String, String>()
        for (ingredient in data.ingredients) {
            for (alias in ingredient.aliases + ingredient.name.lowercase()) {
                val existing = seen.put(alias.lowercase(), ingredient.id)
                assertTrue(
                    "alias '$alias' maps to both '$existing' and '${ingredient.id}'",
                    existing == null || existing == ingredient.id,
                )
            }
        }
    }
}
