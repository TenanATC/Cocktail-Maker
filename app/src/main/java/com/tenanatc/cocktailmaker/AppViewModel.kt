package com.tenanatc.cocktailmaker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tenanatc.cocktailmaker.data.Brand
import com.tenanatc.cocktailmaker.data.BrandDetector
import com.tenanatc.cocktailmaker.data.CocktailData
import com.tenanatc.cocktailmaker.data.CocktailRepository
import com.tenanatc.cocktailmaker.data.Ingredient
import com.tenanatc.cocktailmaker.data.MatchMode
import com.tenanatc.cocktailmaker.data.MatchResult
import com.tenanatc.cocktailmaker.data.Recipe
import com.tenanatc.cocktailmaker.data.RecipeMatcher
import com.tenanatc.cocktailmaker.data.BrandTier
import com.tenanatc.cocktailmaker.data.RiffGenerator
import com.tenanatc.cocktailmaker.data.UnlockSuggestion
import com.tenanatc.cocktailmaker.vision.ClarifaiClient
import com.tenanatc.cocktailmaker.vision.GeminiClient
import com.tenanatc.cocktailmaker.vision.GeminiItem
import com.tenanatc.cocktailmaker.vision.GeminiResult
import com.tenanatc.cocktailmaker.vision.ImageUtils
import com.tenanatc.cocktailmaker.vision.LabelMapper
import com.tenanatc.cocktailmaker.vision.OcrReader
import com.tenanatc.cocktailmaker.vision.VisionResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The screens of the app; a simple back-stack is kept in [AppViewModel.screenStack]. */
sealed class Screen {
    data object Home : Screen()
    data object Ingredients : Screen()
    data object Results : Screen()
    data class Detail(val recipe: Recipe, val match: MatchResult? = null) : Screen()
    data object Browse : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("cocktail_maker", Context.MODE_PRIVATE)

    val data: CocktailData = CocktailRepository.get(app)
    private val matcher = RecipeMatcher(data)
    private val labelMapper = LabelMapper(data)
    private val brandDetector = BrandDetector(data.brands)
    private val riffGenerator = RiffGenerator(data)

    var screenStack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set
    val currentScreen: Screen get() = screenStack.last()

    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var detecting by mutableStateOf(false)
        private set
    var detectionError by mutableStateOf<String?>(null)
        private set

    /** Ingredients currently "on hand" — detected from the photo and/or user-edited. */
    var selectedIngredients by mutableStateOf<List<Ingredient>>(emptyList())
        private set

    /** Bottles identified from label text (OCR), keyed by the ingredient they fill. */
    var detectedBrands by mutableStateOf<Map<String, List<Brand>>>(emptyMap())
        private set

    var results by mutableStateOf<List<MatchResult>>(emptyList())
        private set

    /** Off-menu drinks invented from the shelf via classic formulas. */
    var riffs by mutableStateOf<List<Recipe>>(emptyList())
        private set

    /** "One bottle away" shopping suggestions. */
    var unlocks by mutableStateOf<List<UnlockSuggestion>>(emptyList())
        private set

    var matchMode by mutableStateOf(
        runCatching { MatchMode.valueOf(prefs.getString("match_mode", null) ?: "") }
            .getOrDefault(MatchMode.STRICT)
    )
        private set

    var apiKey by mutableStateOf(
        prefs.getString("clarifai_pat", null) ?: BuildConfig.CLARIFAI_PAT
    )
        private set

    /** Primary recognizer key (Google AI Studio / Gemini). */
    var geminiKey by mutableStateOf(prefs.getString("gemini_key", null).orEmpty())
        private set

    /** True once photo recognition can run (Gemini preferred, Clarifai legacy). */
    val hasRecognitionKey: Boolean get() = geminiKey.isNotBlank() || apiKey.isNotBlank()

    // ----- Navigation -----

    fun navigate(screen: Screen) {
        screenStack = screenStack + screen
    }

    fun back(): Boolean {
        if (screenStack.size <= 1) return false
        screenStack = screenStack.dropLast(1)
        return true
    }

    fun goHome() {
        screenStack = listOf(Screen.Home)
    }

    // ----- Settings -----

    fun saveApiKey(key: String) {
        apiKey = key.trim()
        prefs.edit().putString("clarifai_pat", apiKey).apply()
    }

    fun saveGeminiKey(key: String) {
        geminiKey = key.trim()
        prefs.edit().putString("gemini_key", geminiKey).apply()
    }

    fun updateMatchMode(mode: MatchMode) {
        matchMode = mode
        prefs.edit().putString("match_mode", mode.name).apply()
        // Keep already-computed results consistent with the new mode.
        if (results.isNotEmpty() || riffs.isNotEmpty() || unlocks.isNotEmpty()) recompute()
    }

    // ----- Ingredient detection flow -----

    /** Called with the Uri of a captured or picked photo. */
    fun onImageChosen(uri: Uri) {
        val context = getApplication<Application>()
        detectionError = null
        detecting = true
        if (currentScreen != Screen.Ingredients) navigate(Screen.Ingredients)

        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { ImageUtils.loadScaledBitmap(context, uri) }
            if (bitmap == null) {
                detecting = false
                detectionError = "Couldn't read that image."
                return@launch
            }
            photo = bitmap

            // Gemini is the primary recognizer — it reads stylized labels and
            // reasons about what a bottle actually is. Fall back to the on-device
            // OCR/Clarifai pipeline only when no Gemini key is set.
            if (geminiKey.isNotBlank()) {
                recognizeWithGemini(bitmap)
            } else {
                recognizeWithOcrAndClarifai(bitmap)
            }
            detecting = false
        }
    }

    private suspend fun recognizeWithGemini(bitmap: Bitmap) {
        val base64 = withContext(Dispatchers.Default) { ImageUtils.toBase64Jpeg(bitmap) }
        val catalog = data.ingredients.map { it.id to it.name }
        when (val result = GeminiClient(geminiKey).recognize(base64, catalog)) {
            is GeminiResult.Success -> {
                val recognized = result.items.mapNotNull { data.ingredientsById[it.ingredientId] }
                    .distinctBy { it.id }
                selectedIngredients = (selectedIngredients + recognized).distinctBy { it.id }
                applyAiBrands(result.items)
                detectionError = if (recognized.isEmpty()) {
                    "Gemini didn't spot any cocktail ingredients — try a closer, better-lit " +
                        "shot, or add them by hand below."
                } else {
                    null
                }
            }
            // If Gemini fails, still salvage whatever the offline reader can get.
            is GeminiResult.Error -> {
                recognizeWithOcrAndClarifai(bitmap)
                detectionError = buildString {
                    append(result.message)
                    if (selectedIngredients.isNotEmpty()) {
                        append(" (Filled in what the offline reader could recognize.)")
                    }
                }
            }
        }
    }

    /** Turns Gemini's free-text brand + tier into on-the-fly [Brand]s for quality guidance. */
    private fun applyAiBrands(items: List<GeminiItem>) {
        val merged = detectedBrands.toMutableMap()
        for (item in items) {
            val ingredient = data.ingredientsById[item.ingredientId] ?: continue
            val tier = when (item.tier?.lowercase(Locale.US)) {
                "premium" -> BrandTier.PREMIUM
                "mid" -> BrandTier.MID
                "value" -> BrandTier.VALUE
                else -> null
            } ?: continue
            val name = item.brand?.takeIf { it.isNotBlank() } ?: continue
            val brand = Brand(
                id = "ai_" + name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_"),
                name = name,
                ingredientId = ingredient.id,
                tier = tier,
                keywords = emptyList(),
            )
            val existing = merged[ingredient.id].orEmpty()
            if (existing.none { it.id == brand.id }) {
                merged[ingredient.id] = existing + brand
            }
        }
        detectedBrands = merged
    }

    private suspend fun recognizeWithOcrAndClarifai(bitmap: Bitmap) = coroutineScope {
        // Run cloud concept recognition and on-device label OCR in parallel.
        val conceptsJob = async {
            val base64 = withContext(Dispatchers.Default) { ImageUtils.toBase64Jpeg(bitmap) }
            ClarifaiClient(apiKey).recognize(base64)
        }
        val ocrJob = async { OcrReader.readText(bitmap) }

        val ocrText = ocrJob.await().orEmpty()
        val brands = brandDetector.detect(ocrText)
        if (brands.isNotEmpty()) {
            val merged = detectedBrands.toMutableMap()
            for (brand in brands) {
                val existing = merged[brand.ingredientId].orEmpty()
                if (existing.none { it.id == brand.id }) {
                    merged[brand.ingredientId] = existing + brand
                }
            }
            detectedBrands = merged
        }

        val fromBrands = brands.mapNotNull { data.ingredientsById[it.ingredientId] }
        val fromOcrWords = labelMapper.mapText(ocrText)

        var apiError: String? = null
        val fromConcepts = when (val result = conceptsJob.await()) {
            is VisionResult.Success -> labelMapper.map(result.labels)
            is VisionResult.Error -> {
                apiError = result.message
                emptyList()
            }
        }

        val detected = (fromBrands + fromOcrWords + fromConcepts).distinctBy { it.id }
        selectedIngredients = (selectedIngredients + detected).distinctBy { it.id }

        detectionError = when {
            detected.isEmpty() && apiError != null -> apiError
            detected.isEmpty() ->
                "No cocktail ingredients recognized in the photo — add them by hand below."
            apiError != null ->
                "Label text was read offline, but full image recognition failed: $apiError"
            else -> null
        }
    }

    /** Bottles recognized for one ingredient, best tier first. */
    fun brandsFor(ingredientId: String): List<Brand> =
        detectedBrands[ingredientId].orEmpty().sortedBy { it.tier.ordinal }

    /** Manual path: skip the camera and pick ingredients from the dictionary. */
    fun startManualSelection() {
        detectionError = null
        if (currentScreen != Screen.Ingredients) navigate(Screen.Ingredients)
    }

    fun addIngredient(ingredient: Ingredient) {
        if (selectedIngredients.none { it.id == ingredient.id }) {
            selectedIngredients = selectedIngredients + ingredient
        }
    }

    fun removeIngredient(ingredient: Ingredient) {
        selectedIngredients = selectedIngredients.filterNot { it.id == ingredient.id }
        detectedBrands = detectedBrands - ingredient.id
    }

    fun clearSession() {
        photo = null
        selectedIngredients = emptyList()
        detectedBrands = emptyMap()
        results = emptyList()
        riffs = emptyList()
        unlocks = emptyList()
        detectionError = null
    }

    // ----- Matching -----

    private fun recompute() {
        val availableIds = selectedIngredients.map { it.id }.toSet()
        results = matcher.match(availableIds, detectedBrands, matchMode)
        riffs = riffGenerator.generate(availableIds, detectedBrands)
        unlocks = matcher.unlockSuggestions(availableIds, matchMode)
    }

    fun findCocktails() {
        recompute()
        navigate(Screen.Results)
    }

    /** From the "one bottle away" list: claim the bottle and refresh in place. */
    fun addUnlockedIngredient(ingredient: Ingredient) {
        addIngredient(ingredient)
        recompute()
    }
}
