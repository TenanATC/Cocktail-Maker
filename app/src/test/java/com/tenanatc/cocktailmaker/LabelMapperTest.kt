package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.vision.LabelMapper
import com.tenanatc.cocktailmaker.vision.VisionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMapperTest {

    private val mapper = LabelMapper(TestData.data)

    @Test
    fun `maps direct names and aliases`() {
        val labels = listOf(
            VisionLabel("Vodka", 0.98),
            VisionLabel("whisky", 0.91),
            VisionLabel("club soda", 0.77),
        )
        val ids = mapper.map(labels).map { it.id }
        assertEquals(listOf("vodka", "bourbon", "soda_water"), ids)
    }

    @Test
    fun `maps plural fruit labels to juices`() {
        val ids = mapper.map(
            listOf(
                VisionLabel("limes", 0.95),
                VisionLabel("lemons", 0.90),
                VisionLabel("cherries", 0.85),
                VisionLabel("strawberries", 0.80),
            )
        ).map { it.id }
        assertEquals(listOf("lime_juice", "lemon_juice", "cherry", "strawberry"), ids)
    }

    @Test
    fun `ignores irrelevant labels`() {
        val ids = mapper.map(
            listOf(
                VisionLabel("table", 0.99),
                VisionLabel("bottle", 0.98),
                VisionLabel("no person", 0.97),
                VisionLabel("gin", 0.90),
            )
        ).map { it.id }
        assertEquals(listOf("gin"), ids)
    }

    @Test
    fun `scans free-form OCR label text for ingredient words`() {
        val ids = mapper.mapText(
            """
            TEQUILA
            BLANCO
            100% DE AGAVE
            LONDON DRY GIN
            """.trimIndent()
        ).map { it.id }
        assertTrue("expected tequila in $ids", "tequila" in ids)
        assertTrue("expected gin in $ids", "gin" in ids)
        // "agave" maps to agave syrup via alias — acceptable, the list is user-editable.
    }

    @Test
    fun `deduplicates labels mapping to the same ingredient`() {
        val ingredients = mapper.map(
            listOf(
                VisionLabel("cointreau", 0.9),
                VisionLabel("orange liqueur", 0.8),
                VisionLabel("triple sec", 0.7),
            )
        )
        assertEquals(1, ingredients.size)
        assertTrue(ingredients[0].id == "triple_sec")
    }
}
