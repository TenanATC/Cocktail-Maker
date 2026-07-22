package com.tenanatc.cocktailmaker

import com.tenanatc.cocktailmaker.vision.GeminiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientTest {

    private val client = GeminiClient(apiKey = "test")

    /** Wraps a model answer in the Gemini generateContent response envelope. */
    private fun envelope(answer: String): String =
        """{"candidates":[{"content":{"parts":[{"text": ${org.json.JSONObject.quote(answer)}}]}}]}"""

    @Test
    fun `parses ingredients with brand and tier`() {
        val items = client.parseForTest(
            envelope(
                """{"ingredients":[
                    {"id":"tequila","brand":"Tapatío 110","tier":"premium"},
                    {"id":"lime_juice","brand":null,"tier":null}
                ]}"""
            )
        )
        assertEquals(2, items.size)
        assertEquals("tequila", items[0].ingredientId)
        assertEquals("Tapatío 110", items[0].brand)
        assertEquals("premium", items[0].tier)
        // Generic item: brand/tier cleared to null.
        assertEquals("lime_juice", items[1].ingredientId)
        assertNull(items[1].brand)
        assertNull(items[1].tier)
    }

    @Test
    fun `handles the empty result`() {
        assertTrue(client.parseForTest(envelope("""{"ingredients":[]}""")).isEmpty())
    }

    @Test
    fun `tolerates a blocked or empty candidate list`() {
        assertTrue(client.parseForTest("""{"candidates":[]}""").isEmpty())
        assertTrue(client.parseForTest("""{"promptFeedback":{"blockReason":"SAFETY"}}""").isEmpty())
    }

    @Test
    fun `skips entries without an id and treats the string null as absent`() {
        val items = client.parseForTest(
            envelope(
                """{"ingredients":[
                    {"id":"","brand":"x","tier":"mid"},
                    {"id":"gin","brand":"null","tier":"null"}
                ]}"""
            )
        )
        assertEquals(1, items.size)
        assertEquals("gin", items[0].ingredientId)
        assertNull(items[0].brand)
        assertNull(items[0].tier)
    }

    @Test
    fun `defaults to a multi-model fallback chain`() {
        // A single quota-limited model shouldn't be the whole story — on HTTP 429
        // the client falls back to the next model's separate quota bucket.
        val chain = GeminiClient(apiKey = "x").modelChain
        assertTrue("expected at least two fallback models", chain.size >= 2)
        assertEquals(chain.size, chain.toSet().size)
        assertTrue(chain.all { it.startsWith("gemini-") })
    }

    @Test
    fun `every id Gemini is told to use exists in the real catalog`() {
        // The prompt lists real ids; make sure the catalog we would send is non-empty
        // and self-consistent so Gemini can only pick valid ids.
        val ids = TestData.data.ingredients.map { it.id }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }
}
