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
        fun asset(name: String) =
            context.assets.open(name).bufferedReader().use { it.readText() }
        return CocktailParser.parse(
            asset("ingredients.json"),
            asset("cocktails.json"),
            asset("brands.json"),
        )
    }
}
