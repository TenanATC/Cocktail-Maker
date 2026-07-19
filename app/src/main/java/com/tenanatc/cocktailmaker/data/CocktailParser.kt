package com.tenanatc.cocktailmaker.data

import org.json.JSONObject

/**
 * Parses the bundled JSON dictionaries. Kept free of Android dependencies so it
 * can be exercised by plain JVM unit tests.
 */
object CocktailParser {

    fun parse(
        ingredientsJson: String,
        cocktailsJson: String,
        brandsJson: String? = null,
    ): CocktailData {
        val ingRoot = JSONObject(ingredientsJson)

        val ingredients = buildList {
            val arr = ingRoot.getJSONArray("ingredients")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Ingredient(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        category = o.getString("category"),
                        aliases = o.optJSONArray("aliases").toStringList(),
                    )
                )
            }
        }

        val substitutions = buildList {
            val arr = ingRoot.getJSONArray("substitutions")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Substitution(
                        missingId = o.getString("missing"),
                        useInsteadId = o.getString("useInstead"),
                        note = o.getString("note"),
                    )
                )
            }
        }

        val pantry = ingRoot.optJSONArray("pantry").toStringList().toSet()

        val recipes = buildList {
            val arr = JSONObject(cocktailsJson).getJSONArray("recipes")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val recipeIngredients = buildList {
                    val ings = o.getJSONArray("ingredients")
                    for (j in 0 until ings.length()) {
                        val ri = ings.getJSONObject(j)
                        add(
                            RecipeIngredient(
                                ingredientId = ri.getString("id"),
                                amount = ri.getString("amount"),
                                optional = ri.optBoolean("optional", false),
                            )
                        )
                    }
                }
                add(
                    Recipe(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        glass = o.getString("glass"),
                        description = o.getString("description"),
                        ingredients = recipeIngredients,
                        instructions = o.getJSONArray("instructions").let { steps ->
                            List(steps.length()) { idx -> steps.getString(idx) }
                        },
                        tags = o.optJSONArray("tags").toStringList(),
                    )
                )
            }
        }

        val brands = brandsJson?.let { BrandParser.parse(it) } ?: emptyList()

        return CocktailData(ingredients, recipes, substitutions, pantry, brands)
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { getString(it) }
    }
}
