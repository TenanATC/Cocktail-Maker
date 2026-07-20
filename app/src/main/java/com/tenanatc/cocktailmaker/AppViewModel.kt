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
import com.tenanatc.cocktailmaker.data.RiffGenerator
import com.tenanatc.cocktailmaker.data.UnlockSuggestion
import com.tenanatc.cocktailmaker.vision.ClarifaiClient
import com.tenanatc.cocktailmaker.vision.ImageUtils
import com.tenanatc.cocktailmaker.vision.LabelMapper
import com.tenanatc.cocktailmaker.vision.OcrReader
import com.tenanatc.cocktailmaker.vision.VisionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

            // Run cloud concept recognition and on-device label OCR in parallel.
            // OCR is what identifies brands (and their quality tier) — generic
            // vision models only see "a tequila bottle".
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

            // Ingredients come from three signals: brand hits ("Espolòn" implies
            // tequila), plain words OCR'd off labels ("LONDON DRY GIN"), and the
            // image-recognition concepts.
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
                // OCR salvaged something even though the cloud call failed; tell
                // the user quietly rather than failing the whole scan.
                apiError != null -> "Label text was read offline, but full image recognition failed: $apiError"
                else -> null
            }
            detecting = false
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
