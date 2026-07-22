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
    /**
     * Tried in order. Each model has its own separate free-tier quota bucket, so
     * if one is rate-limited (HTTP 429) or unavailable (404), the next often
     * still works.
     */
    private val models: List<String> = DEFAULT_MODELS,
) {

    /** The model fallback order, exposed for tests. */
    val modelChain: List<String> get() = models

    /** Outcome of a single model attempt. */
    private sealed class Attempt {
        data class Ok(val items: List<GeminiItem>) : Attempt()
        /** Worth trying another model (quota, model-not-found, server hiccup). */
        data class TryNext(val message: String) : Attempt()
        /** Won't improve by switching models (bad key, no network). */
        data class Fatal(val message: String) : Attempt()
    }

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
        val body = requestBody(imageBase64, catalog)
        var lastMessage = "Gemini returned no result."
        for (model in models) {
            when (val attempt = callModel(model, body)) {
                is Attempt.Ok -> return@withContext GeminiResult.Success(attempt.items)
                is Attempt.Fatal -> return@withContext GeminiResult.Error(attempt.message)
                is Attempt.TryNext -> lastMessage = attempt.message
            }
        }
        GeminiResult.Error(lastMessage)
    }

    private fun callModel(model: String, body: String): Attempt {
        return try {
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
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                when {
                    code in 200..299 -> Attempt.Ok(parseItems(text))
                    // Auth failures repeat across every model — stop now.
                    code == 401 || code == 403 ->
                        Attempt.Fatal(apiErrorMessage(code, text))
                    // Quota, model-not-found, or transient server errors: try the next model.
                    else -> Attempt.TryNext(apiErrorMessage(code, text))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Attempt.Fatal("Couldn't reach Gemini: ${e.message ?: "unknown error"}")
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
        /**
         * Fallback chain. Vision-capable flash models, each with its own free-tier
         * quota bucket, ordered strongest-first.
         */
        val DEFAULT_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash-lite",
        )

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
            // Free-tier limits are per-model and often per-minute; the detail says which.
            429 -> "Gemini free-tier limit hit — wait a minute and retry" +
                (detail?.let { ". $it" } ?: ", or enable billing for higher limits.")
            in 500..599 -> "Gemini is having trouble right now — try again in a moment."
            else -> "Gemini error ($code)${detail?.let { ": $it" } ?: ""}"
        }
    }
}
