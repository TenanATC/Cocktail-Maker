package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.data.MatchMode
import com.tenanatc.cocktailmaker.data.RecipeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the Strict / Flexible / Adventurous modes and family fallbacks. */
class FlexibleMatchingTest {

    private val data = TestData.data
    private val matcher = RecipeMatcher(data)

    @Test
    fun `family fallback lets scotch stand in for bourbon in flexible mode`() {
        val hand = setOf("scotch", "simple_syrup", "angostura_bitters")

        // STRICT: no curated bourbon->scotch rule exists, so the Old Fashioned
        // is only a near-miss.
        val strict = matcher.match(hand, mode = MatchMode.STRICT)
        val strictOf = strict.first { it.recipe.id == "old_fashioned" }
        assertTrue(!strictOf.makeableNow)

        // FLEXIBLE: the whiskey family covers it, flagged as a fallback.
        val flexible = matcher.match(hand, mode = MatchMode.FLEXIBLE)
        val flexOf = flexible.first { it.recipe.id == "old_fashioned" }
        assertTrue(flexOf.makeableNow)
        val sub = flexOf.substitutions.single { it.wanted.id == "bourbon" }
        assertTrue(sub.familyFallback)
        assertEquals("scotch", sub.useInstead.id)
    }

    @Test
    fun `curated substitutions always win over family fallbacks`() {
        // Manhattan wants rye; bourbon has a curated rule, scotch only the family.
        val results = matcher.match(
            setOf("bourbon", "scotch", "sweet_vermouth", "angostura_bitters"),
            mode = MatchMode.FLEXIBLE,
        )
        val manhattan = results.first { it.recipe.id == "manhattan" }
        val sub = manhattan.substitutions.single()
        assertEquals("bourbon", sub.useInstead.id)
        assertTrue(!sub.familyFallback)
    }

    @Test
    fun `family fallback scores below curated substitution`() {
        val curated = matcher.match(
            setOf("bourbon", "sweet_vermouth", "angostura_bitters"),
            mode = MatchMode.FLEXIBLE,
        ).first { it.recipe.id == "manhattan" }.score
        val fallback = matcher.match(
            setOf("scotch", "sweet_vermouth", "angostura_bitters"),
            mode = MatchMode.FLEXIBLE,
        ).first { it.recipe.id == "manhattan" }.score
        assertTrue(fallback < curated)
    }

    @Test
    fun `adventurous mode surfaces recipes strict mode hides`() {
        // Vodka alone: Bloody Mary is missing 2 of its 3 required ingredients,
        // below the strict coverage gate but within the adventurous one.
        val strict = matcher.match(setOf("vodka"), mode = MatchMode.STRICT)
        assertTrue(strict.none { it.recipe.id == "bloody_mary" })

        val adventurous = matcher.match(setOf("vodka"), mode = MatchMode.ADVENTUROUS)
        assertTrue(adventurous.any { it.recipe.id == "bloody_mary" })
    }

    @Test
    fun `strict mode matches are unchanged by the mode parameter default`() {
        val hand = setOf("tequila", "lime_juice", "triple_sec")
        assertEquals(
            matcher.match(hand).map { it.recipe.id },
            matcher.match(hand, mode = MatchMode.STRICT).map { it.recipe.id },
        )
    }

    @Test
    fun `unlock suggestions find the bottle that opens the most drinks`() {
        val unlocks = matcher.unlockSuggestions(setOf("gin", "campari"))
        val vermouth = unlocks.first { it.ingredient.id == "sweet_vermouth" }
        assertTrue(vermouth.unlocked.any { it.id == "negroni" })
        // Everything suggested must actually unlock something new.
        assertTrue(unlocks.all { it.unlocked.isNotEmpty() })
    }

    @Test
    fun `unlock suggestions never repeat drinks already makeable`() {
        val hand = setOf("gin", "tonic_water", "campari", "sweet_vermouth")
        val makeableNow = matcher.match(hand)
            .filter { it.makeableNow }
            .map { it.recipe.id }
            .toSet()
        assertTrue("gin_tonic" in makeableNow && "negroni" in makeableNow)

        val unlocks = matcher.unlockSuggestions(hand)
        for (suggestion in unlocks) {
            assertTrue(suggestion.unlocked.none { it.id in makeableNow })
        }
    }
}
