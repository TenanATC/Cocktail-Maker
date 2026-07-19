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

/**
 * How much a recipe lets the base spirit shine.
 * SHOWCASE: stirred/boozy, the spirit is the drink (Old Fashioned, Martini).
 * BALANCED: citrus sours and other drinks where quality still reads (Daiquiri).
 * MASKED: big juices, cola, cream, or coffee dominate (Tequila Sunrise).
 */
enum class RecipeStyle { SHOWCASE, BALANCED, MASKED }

enum class GuidanceType {
    /** A premium bottle in a spirit-forward drink — exactly where it belongs. */
    PREMIUM_SHOWCASED,
    /** A premium bottle about to drown in mixers. */
    PREMIUM_WASTED,
    /** A value bottle with nowhere to hide. */
    VALUE_EXPOSED,
    /** A value bottle doing honest work under big mixers. */
    VALUE_WELL_PLACED,
}

/** Bottle-quality advice for one spirit used by a matched recipe. */
data class SpiritGuidance(
    val ingredient: Ingredient,
    val brand: Brand,
    val type: GuidanceType,
    val message: String,
) {
    val positive: Boolean
        get() = type == GuidanceType.PREMIUM_SHOWCASED || type == GuidanceType.VALUE_WELL_PLACED
}

/** How well a recipe matches the ingredients on hand. */
data class MatchResult(
    val recipe: Recipe,
    /** 0.0–1.0; 1.0 means every required ingredient is covered (possibly via substitution). */
    val score: Double,
    val requiredCount: Int,
    val directHits: List<Ingredient>,
    val substitutions: List<AppliedSubstitution>,
    val missing: List<Ingredient>,
    val style: RecipeStyle = RecipeStyle.BALANCED,
    /** Bottle-quality advice based on the brands read off the labels. */
    val guidance: List<SpiritGuidance> = emptyList(),
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
    /** Known bottle brands with quality tiers, recognized from label text. */
    val brands: List<Brand> = emptyList(),
) {
    val ingredientsById: Map<String, Ingredient> = ingredients.associateBy { it.id }
}
