package com.tenanatc.cocktailmaker.data

/**
 * A canonical ingredient in the app's dictionary.
 *
 * @param aliases lowercase names the vision API (or a user search) might use for
 *   this ingredient, e.g. "cointreau" for triple sec.
 */
data class Ingredient(
    val id: String,
    val name: String,
    val category: String,
    val aliases: List<String> = emptyList(),
)

/** One line of a recipe: an ingredient reference plus a human-readable amount. */
data class RecipeIngredient(
    val ingredientId: String,
    val amount: String,
    /** Garnishes and nice-to-haves; never counted against a match. */
    val optional: Boolean = false,
)

data class Recipe(
    val id: String,
    val name: String,
    val glass: String,
    val description: String,
    val ingredients: List<RecipeIngredient>,
    val instructions: List<String>,
    val tags: List<String> = emptyList(),
)

/** "If you're missing [missingId], you can pour [useInsteadId] instead." */
data class Substitution(
    val missingId: String,
    val useInsteadId: String,
    val note: String,
)

/** A substitution that was actually applied while matching a specific recipe. */
data class AppliedSubstitution(
    val wanted: Ingredient,
    val useInstead: Ingredient,
    val note: String,
)

/** How well a recipe matches the ingredients on hand. */
data class MatchResult(
    val recipe: Recipe,
    /** 0.0–1.0; 1.0 means every required ingredient is covered (possibly via substitution). */
    val score: Double,
    val requiredCount: Int,
    val directHits: List<Ingredient>,
    val substitutions: List<AppliedSubstitution>,
    val missing: List<Ingredient>,
) {
    val makeableNow: Boolean get() = missing.isEmpty()
}

/** The parsed contents of the bundled dictionaries. */
data class CocktailData(
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val substitutions: List<Substitution>,
    /** Ingredient ids assumed to always be on hand (water, ice, salt...). */
    val pantry: Set<String>,
) {
    val ingredientsById: Map<String, Ingredient> = ingredients.associateBy { it.id }
}
