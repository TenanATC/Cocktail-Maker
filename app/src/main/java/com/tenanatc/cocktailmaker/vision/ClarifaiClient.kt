package com.tenanatc.cocktailmaker.vision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A single label returned by the recognition API. */
data class VisionLabel(val name: String, val confidence: Double)

sealed class VisionResult {
    data class Success(val labels: List<VisionLabel>) : VisionResult()
    data class Error(val message: String) : VisionResult()
}

/**
 * Thin client for Clarifai's image recognition REST API. Clarifai's community
 * tier is free (1000 requests/month at the time of writing); users supply their
 * own Personal Access Token.
 *
 * Two public models are queried and their concepts merged: the general model
 * knows bottles, spirits, and drinks; the food model is much better at fruit
 * and produce.
 */
class ClarifaiClient(private val pat: String) {

    private companion object {
        const val USER_ID = "clarifai"
        const val APP_ID = "main"
        val MODELS = listOf("general-image-recognition", "food-item-recognition")
        const val MIN_CONFIDENCE = 0.55
    }

    suspend fun recognize(imageBase64: String): VisionResult = coroutineScope {
        if (pat.isBlank()) {
            return@coroutineScope VisionResult.Error(
                "No Clarifai API key configured. Add one in Settings, or pick ingredients by hand."
            )
        }
        val results = MODELS.map { model ->
            async { callModel(model, imageBase64) }
        }.map { it.await() }

        val labels = results.filterIsInstance<VisionResult.Success>()
            .flatMap { it.labels }
            .groupBy { it.name.lowercase() }
            .map { (name, hits) -> VisionLabel(name, hits.maxOf { l -> l.confidence }) }
            .filter { it.confidence >= MIN_CONFIDENCE }
            .sortedByDescending { it.confidence }

        if (labels.isNotEmpty()) {
            VisionResult.Success(labels)
        } else {
            // Surface the first error if every model call failed.
            results.filterIsInstance<VisionResult.Error>().firstOrNull()
                ?: VisionResult.Success(emptyList())
        }
    }

    private suspend fun callModel(modelId: String, imageBase64: String): VisionResult =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.clarifai.com/v2/users/$USER_ID/apps/$APP_ID/models/$modelId/outputs"
                )
                val body = JSONObject().put(
                    "inputs",
                    JSONArray().put(
                        JSONObject().put(
                            "data",
                            JSONObject().put("image", JSONObject().put("base64", imageBase64))
                        )
                    )
                ).toString()

                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Authorization", "Key $pat")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        return@withContext VisionResult.Error(apiErrorMessage(code, text))
                    }
                    VisionResult.Success(parseConcepts(text))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                VisionResult.Error("Couldn't reach the recognition service: ${e.message ?: "unknown error"}")
            }
        }

    private fun parseConcepts(json: String): List<VisionLabel> {
        val outputs = JSONObject(json).optJSONArray("outputs") ?: return emptyList()
        if (outputs.length() == 0) return emptyList()
        val concepts = outputs.getJSONObject(0)
            .optJSONObject("data")
            ?.optJSONArray("concepts") ?: return emptyList()
        return List(concepts.length()) { i ->
            val c = concepts.getJSONObject(i)
            VisionLabel(c.getString("name"), c.getDouble("value"))
        }
    }

    private fun apiErrorMessage(code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("status")?.optString("description")
        }.getOrNull()
        return when (code) {
            401, 403 -> "The Clarifai API key was rejected. Check it in Settings."
            429 -> "Recognition quota exceeded for this key — try again later."
            else -> "Recognition service error ($code)${detail?.let { ": $it" } ?: ""}"
        }
    }
}
