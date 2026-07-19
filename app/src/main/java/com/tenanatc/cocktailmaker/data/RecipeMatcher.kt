package com.tenanatc.cocktailmaker.data

/**
 * The offline brain of the app: given the set of ingredients on hand, rank every
 * recipe in the dictionary, applying substitutions where a called-for ingredient
 * is missing but a documented stand-in is available.
 */
class RecipeMatcher(private val data: CocktailData) {

    /** missingId -> substitutions that can cover it, in dictionary order (preferred first). */
    private val subsByMissing: Map<String, List<Substitution>> =
        data.substitutions.groupBy { it.missingId }

    /** Weight applied to an ingredient covered via substitution rather than directly. */
    private val substitutionWeight = 0.75

    /**
     * @param availableIds canonical ingredient ids on hand (detected in the photo
     *   or hand-picked by the user).
     * @param maxMissing recipes missing more than this many required ingredients
     *   are dropped entirely.
     */
    fun match(availableIds: Set<String>, maxMissing: Int = 2): List<MatchResult> {
        if (availableIds.isEmpty()) return emptyList()

        return data.recipes.mapNotNull { recipe ->
            scoreRecipe(recipe, availableIds, maxMissing)
        }.sortedWith(
            compareByDescending<MatchResult> { it.makeableNow }
                .thenByDescending { it.score }
                .thenBy { it.missing.size }
                .thenBy { it.substitutions.size }
                .thenBy { it.recipe.name }
        )
    }

    private fun scoreRecipe(
        recipe: Recipe,
        availableIds: Set<String>,
        maxMissing: Int,
    ): MatchResult? {
        val required = recipe.ingredients.filter {
            !it.optional && it.ingredientId !in data.pantry
        }
        if (required.isEmpty()) return null

        val directHits = mutableListOf<Ingredient>()
        val applied = mutableListOf<AppliedSubstitution>()
        val missing = mutableListOf<Ingredient>()
        var points = 0.0

        for (line in required) {
            val wanted = data.ingredientsById.getValue(line.ingredientId)
            when {
                line.ingredientId in availableIds -> {
                    directHits += wanted
                    points += 1.0
                }
                else -> {
                    val sub = subsByMissing[line.ingredientId]
                        ?.firstOrNull { it.useInsteadId in availableIds }
                    if (sub != null) {
                        applied += AppliedSubstitution(
                            wanted = wanted,
                            useInstead = data.ingredientsById.getValue(sub.useInsteadId),
                            note = sub.note,
                        )
                        points += substitutionWeight
                    } else {
                        missing += wanted
                    }
                }
            }
        }

        // Not interesting unless at least one real ingredient from the photo is used.
        if (directHits.isEmpty() && applied.isEmpty()) return null
        if (missing.size > maxMissing) return null
        // A recipe where most ingredients are absent is noise, even under maxMissing.
        if ((directHits.size + applied.size) * 2 < required.size) return null

        return MatchResult(
            recipe = recipe,
            score = points / required.size,
            requiredCount = required.size,
            directHits = directHits,
            substitutions = applied,
            missing = missing,
        )
    }
}
