package com.tenanatc.cocktailmaker.data

import android.content.Context

/**
 * Loads the offline cocktail dictionary from bundled assets. Everything the app
 * knows about recipes lives on-device; only ingredient recognition needs the
 * network.
 */
object CocktailRepository {

    @Volatile
    private var cached: CocktailData? = null

    fun get(context: Context): CocktailData {
        return cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    private fun load(context: Context): CocktailData {
        val ingredientsJson = context.assets.open("ingredients.json")
            .bufferedReader().use { it.readText() }
        val cocktailsJson = context.assets.open("cocktails.json")
            .bufferedReader().use { it.readText() }
        return CocktailParser.parse(ingredientsJson, cocktailsJson)
    }
}
