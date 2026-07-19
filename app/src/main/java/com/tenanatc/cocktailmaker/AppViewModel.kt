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
import com.tenanatc.cocktailmaker.data.CocktailData
import com.tenanatc.cocktailmaker.data.CocktailRepository
import com.tenanatc.cocktailmaker.data.Ingredient
import com.tenanatc.cocktailmaker.data.MatchResult
import com.tenanatc.cocktailmaker.data.Recipe
import com.tenanatc.cocktailmaker.data.RecipeMatcher
import com.tenanatc.cocktailmaker.vision.ClarifaiClient
import com.tenanatc.cocktailmaker.vision.ImageUtils
import com.tenanatc.cocktailmaker.vision.LabelMapper
import com.tenanatc.cocktailmaker.vision.VisionResult
import kotlinx.coroutines.Dispatchers
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

    var results by mutableStateOf<List<MatchResult>>(emptyList())
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

            val base64 = withContext(Dispatchers.Default) { ImageUtils.toBase64Jpeg(bitmap) }
            when (val result = ClarifaiClient(apiKey).recognize(base64)) {
                is VisionResult.Success -> {
                    val detected = labelMapper.map(result.labels)
                    // Merge with anything the user already picked by hand.
                    val merged = (selectedIngredients + detected).distinctBy { it.id }
                    selectedIngredients = merged
                    if (detected.isEmpty()) {
                        detectionError =
                            "No cocktail ingredients recognized in the photo — add them by hand below."
                    }
                }
                is VisionResult.Error -> detectionError = result.message
            }
            detecting = false
        }
    }

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
    }

    fun clearSession() {
        photo = null
        selectedIngredients = emptyList()
        results = emptyList()
        detectionError = null
    }

    // ----- Matching -----

    fun findCocktails() {
        results = matcher.match(selectedIngredients.map { it.id }.toSet())
        navigate(Screen.Results)
    }
}
