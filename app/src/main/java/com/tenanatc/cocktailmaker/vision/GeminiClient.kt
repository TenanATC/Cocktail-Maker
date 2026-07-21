package com.tenanatc.cocktailmaker.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One item Gemini reports seeing, already mapped to a canonical ingredient id. */
data class GeminiItem(
    val ingredientId: String,
    val brand: String?,
    val tier: String?,
)

sealed class GeminiResult {
    data class Success(val items: List<GeminiItem>) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}

/**
 * Recognizes cocktail ingredients by sending the photo to Google's Gemini
 * multimodal model. Unlike generic label models, Gemini reads a stylized bottle
 * (e.g. "Tapatío 110 Blanco") and reasons about what it is — returning the
 * canonical ingredient id plus, where legible, the brand and a quality tier.
 *
 * Kept free of Android dependencies so the response parser is unit-testable on
 * the JVM.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash",
) {

    /**
     * @param imageBase64 JPEG bytes, Base64-encoded (no line wraps).
     * @param catalog the (id, display name) pairs Gemini is allowed to choose from.
     */
    suspend fun recognize(
        imageBase64: String,
        catalog: List<Pair<String, String>>,
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error(
                "No Gemini API key configured. Add one in Settings, or pick ingredients by hand."
            )
        }
        try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use {
                    it.write(requestBody(imageBase64, catalog).toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    return@withContext GeminiResult.Error(apiErrorMessage(code, text))
                }
                GeminiResult.Success(parseItems(text))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            GeminiResult.Error("Couldn't reach Gemini: ${e.message ?: "unknown error"}")
        }
    }

    private fun requestBody(imageBase64: String, catalog: List<Pair<String, String>>): String {
        val idList = catalog.joinToString("\n") { (id, name) -> "$id = $name" }
        val prompt = """
            You are the vision step of a cocktail app. Look at this photo of drink
            ingredients and identify every cocktail-relevant item you can see:
            spirits, liqueurs, fortified wines (vermouth), mixers, juices, fresh
            fruit, herbs, syrups, and bitters.

            Map each item to EXACTLY ONE id from this list. If you see a specific
            brand, map it to its category id (e.g. a Tapatío or Espolòn bottle is
            "tequila"; a bottle of Cointreau is "triple_sec"):
            $idList

            For any bottle whose brand you can read, include the brand name and a
            quality tier — "premium", "mid", or "value" — from the brand's real
            reputation. Omit brand/tier for generic items (a lime, a can of cola).

            Respond with ONLY this JSON, using ids strictly from the list above:
            {"ingredients":[{"id":"<id>","brand":"<name or null>","tier":"premium|mid|value|null"}]}
            If nothing relevant is visible, return {"ingredients":[]}.
        """.trimIndent()

        val body = JSONObject()
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imageBase64),
                )
            )
        body.put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        body.put(
            "generationConfig",
            JSONObject()
                .put("temperature", 0)
                .put("responseMimeType", "application/json"),
        )
        return body.toString()
    }

    private companion object {
        /** Extracts the model's JSON answer from the API envelope and parses items. */
        fun parseItems(responseJson: String): List<GeminiItem> {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return emptyList()

            val answer = buildString {
                for (i in 0 until parts.length()) {
                    append(parts.getJSONObject(i).optString("text", ""))
                }
            }.trim()
            if (answer.isEmpty()) return emptyList()

            val ingredients = JSONObject(answer).optJSONArray("ingredients") ?: return emptyList()
            return buildList {
                for (i in 0 until ingredients.length()) {
                    val o = ingredients.getJSONObject(i)
                    val id = o.optString("id").trim()
                    if (id.isEmpty()) continue
                    add(
                        GeminiItem(
                            ingredientId = id,
                            brand = o.optString("brand").takeIf {
                                it.isNotBlank() && !it.equals("null", ignoreCase = true)
                            },
                            tier = o.optString("tier").takeIf {
                                it.isNotBlank() && !it.equals("null", ignoreCase = true)
                            },
                        )
                    )
                }
            }
        }
    }

    // Exposed for unit tests (pure parsing, no network).
    fun parseForTest(responseJson: String): List<GeminiItem> = parseItems(responseJson)

    private fun apiErrorMessage(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return when (code) {
            400 -> "Gemini rejected the request${detail?.let { ": $it" } ?: " (check the API key)."}"
            403 -> "The Gemini API key was rejected. Check it in Settings."
            429 -> "Gemini quota exceeded for this key — try again later."
            in 500..599 -> "Gemini is having trouble right now — try again in a moment."
            else -> "Gemini error ($code)${detail?.let { ": $it" } ?: ""}"
        }
    }
}
