package com.tenanatc.cocktailmaker.vision

import com.tenanatc.cocktailmaker.data.CocktailData
import com.tenanatc.cocktailmaker.data.Ingredient

/**
 * Translates free-form vision labels ("limes", "whisky", "club soda") into
 * canonical ingredients from the offline dictionary via each ingredient's
 * alias list.
 */
class LabelMapper(data: CocktailData) {

    private val aliasIndex: Map<String, Ingredient> = buildMap {
        for (ingredient in data.ingredients) {
            put(normalize(ingredient.name), ingredient)
            put(ingredient.id.replace('_', ' '), ingredient)
            for (alias in ingredient.aliases) put(normalize(alias), ingredient)
        }
    }

    /** Returns matched ingredients ordered by the confidence of their best label. */
    fun map(labels: List<VisionLabel>): List<Ingredient> {
        val matched = LinkedHashMap<String, Ingredient>()
        for (label in labels.sortedByDescending { it.confidence }) {
            val ingredient = lookup(label.name) ?: continue
            matched.putIfAbsent(ingredient.id, ingredient)
        }
        return matched.values.toList()
    }

    private fun lookup(rawLabel: String): Ingredient? {
        val label = normalize(rawLabel)
        aliasIndex[label]?.let { return it }
        // Try a naive singular form: "limes" -> "lime", "cherries" -> "cherry".
        val singular = when {
            label.endsWith("ies") -> label.dropLast(3) + "y"
            label.endsWith("es") -> label.dropLast(2)
            label.endsWith("s") -> label.dropLast(1)
            else -> null
        }
        singular?.let { aliasIndex[it]?.let { hit -> return hit } }
        return null
    }

    private fun normalize(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ")
}
