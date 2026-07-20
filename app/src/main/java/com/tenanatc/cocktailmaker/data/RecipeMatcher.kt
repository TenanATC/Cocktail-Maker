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

    /** Weight for a same-family stand-in — a bigger flavor gamble than a curated swap. */
    private val familyFallbackWeight = 0.55

    /** family name -> its member ingredients. */
    private val familyMembers: Map<String, List<Ingredient>> =
        data.ingredients.filter { it.family != null }.groupBy { it.family!! }

    /** Required ingredients that dominate a drink and hide the base spirit. */
    private val maskingIds = setOf(
        "cola", "orange_juice", "pineapple_juice", "cranberry_juice", "tomato_juice",
        "grapefruit_juice", "coconut_cream", "cream", "coffee", "coffee_liqueur",
        "irish_cream", "ginger_beer", "ginger_ale", "peach", "strawberry",
    )

    /** Categories that leave the spirit fully exposed in a stirred, boozy drink. */
    private val showcaseCategories = setOf(
        "Spirits", "Liqueurs & Fortified", "Bitters & Extras", "Sweeteners & Syrups",
    )

    /**
     * @param availableIds canonical ingredient ids on hand (detected in the photo
     *   or hand-picked by the user).
     * @param brandsByIngredient bottle brands read off the labels, keyed by the
     *   ingredient they fill; used for quality guidance, never for availability.
     * @param mode how far the matcher may stray from the letter of the recipe.
     */
    fun match(
        availableIds: Set<String>,
        brandsByIngredient: Map<String, List<Brand>> = emptyMap(),
        mode: MatchMode = MatchMode.STRICT,
    ): List<MatchResult> {
        if (availableIds.isEmpty()) return emptyList()

        return data.recipes.mapNotNull { recipe ->
            scoreRecipe(recipe, availableIds, brandsByIngredient, mode)
        }.sortedWith(
            compareByDescending<MatchResult> { it.makeableNow }
                .thenByDescending { it.score + guidanceAdjust(it) }
                .thenBy { it.missing.size }
                .thenBy { it.substitutions.size }
                .thenBy { it.recipe.name }
        )
    }

    /**
     * Small ranking nudge from bottle-quality fit: drinks that showcase a premium
     * bottle float up; drinks that would waste one sink. Never affects [MatchResult.score]
     * itself, which stays a pure availability measure.
     */
    private fun guidanceAdjust(match: MatchResult): Double =
        match.guidance.sumOf {
            when (it.type) {
                GuidanceType.PREMIUM_SHOWCASED -> 0.06
                GuidanceType.VALUE_WELL_PLACED -> 0.03
                GuidanceType.VALUE_EXPOSED -> -0.05
                GuidanceType.PREMIUM_WASTED -> -0.08
            }
        }

    private fun scoreRecipe(
        recipe: Recipe,
        availableIds: Set<String>,
        brandsByIngredient: Map<String, List<Brand>>,
        mode: MatchMode,
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
                    val fallback = if (sub == null && mode != MatchMode.STRICT) {
                        familyFallbackFor(wanted, availableIds)
                    } else null
                    when {
                        sub != null -> {
                            applied += AppliedSubstitution(
                                wanted = wanted,
                                useInstead = data.ingredientsById.getValue(sub.useInsteadId),
                                note = sub.note,
                            )
                            points += substitutionWeight
                        }
                        fallback != null -> {
                            applied += fallback
                            points += familyFallbackWeight
                        }
                        else -> missing += wanted
                    }
                }
            }
        }

        // Not interesting unless at least one real ingredient from the photo is used.
        if (directHits.isEmpty() && applied.isEmpty()) return null
        val maxMissing = if (mode == MatchMode.ADVENTUROUS) 3 else 2
        if (missing.size > maxMissing) return null
        // A recipe where most ingredients are absent is noise, even under maxMissing.
        val covered = directHits.size + applied.size
        val coverageOk = when (mode) {
            MatchMode.ADVENTUROUS -> covered * 3 >= required.size
            else -> covered * 2 >= required.size
        }
        if (!coverageOk) return null

        val style = styleOf(recipe)
        return MatchResult(
            recipe = recipe,
            score = points / required.size,
            requiredCount = required.size,
            directHits = directHits,
            substitutions = applied,
            missing = missing,
            style = style,
            guidance = buildGuidance(style, directHits, applied, brandsByIngredient),
        )
    }

    /**
     * Last-resort stand-in from the same family (any whiskey for any whiskey…),
     * used only in FLEXIBLE/ADVENTUROUS modes when no curated rule applies.
     */
    private fun familyFallbackFor(
        wanted: Ingredient,
        availableIds: Set<String>,
    ): AppliedSubstitution? {
        val family = wanted.family ?: return null
        val stand = familyMembers[family]
            ?.firstOrNull { it.id != wanted.id && it.id in availableIds }
            ?: return null
        return AppliedSubstitution(
            wanted = wanted,
            useInstead = stand,
            note = "Same $family family — expect a noticeably different character.",
            familyFallback = true,
        )
    }

    /**
     * "One bottle away": for each ingredient not on hand, which recipes would
     * become makeable right now if it were added? Sorted by how many drinks the
     * bottle unlocks. Pure shopping-list math — no guessing involved.
     */
    fun unlockSuggestions(
        availableIds: Set<String>,
        mode: MatchMode = MatchMode.STRICT,
        max: Int = 5,
    ): List<UnlockSuggestion> {
        if (availableIds.isEmpty()) return emptyList()
        val alreadyMakeable = match(availableIds, mode = mode)
            .filter { it.makeableNow }
            .map { it.recipe.id }
            .toSet()

        return data.ingredients.asSequence()
            .filter { it.id !in availableIds && it.id !in data.pantry }
            .map { candidate ->
                val newlyMakeable = match(availableIds + candidate.id, mode = mode)
                    .filter { it.makeableNow && it.recipe.id !in alreadyMakeable }
                    .map { it.recipe }
                UnlockSuggestion(candidate, newlyMakeable)
            }
            .filter { it.unlocked.isNotEmpty() }
            .sortedWith(
                compareByDescending<UnlockSuggestion> { it.unlocked.size }
                    .thenBy { it.ingredient.name }
            )
            .take(max)
            .toList()
    }

    /** Classifies how much a recipe exposes its base spirit. */
    fun styleOf(recipe: Recipe): RecipeStyle {
        val required = recipe.ingredients.filter { !it.optional && it.ingredientId !in data.pantry }
        if (required.any { it.ingredientId in maskingIds }) return RecipeStyle.MASKED
        val allShowcase = required.all {
            data.ingredientsById.getValue(it.ingredientId).category in showcaseCategories
        }
        return if (allShowcase) RecipeStyle.SHOWCASE else RecipeStyle.BALANCED
    }

    /**
     * Bottle-quality advice for the spirits this drink actually uses (directly or
     * via substitution), based on the brands read off the photographed labels.
     */
    private fun buildGuidance(
        style: RecipeStyle,
        directHits: List<Ingredient>,
        applied: List<AppliedSubstitution>,
        brandsByIngredient: Map<String, List<Brand>>,
    ): List<SpiritGuidance> {
        val spiritsUsed = (directHits + applied.map { it.useInstead })
            .filter { it.category == "Spirits" }
            .distinctBy { it.id }

        return spiritsUsed.mapNotNull { spirit ->
            val bottles = brandsByIngredient[spirit.id].orEmpty()
            val best = bottles.minByOrNull { it.tier.ordinal } ?: return@mapNotNull null
            val valueAlt = bottles.firstOrNull { it.tier == BrandTier.VALUE && it.id != best.id }

            when {
                best.tier == BrandTier.PREMIUM && style == RecipeStyle.SHOWCASE ->
                    SpiritGuidance(
                        spirit, best, GuidanceType.PREMIUM_SHOWCASED,
                        "A perfect stage for your ${best.name} — nothing here will hide it.",
                    )
                best.tier == BrandTier.PREMIUM && style == RecipeStyle.MASKED ->
                    SpiritGuidance(
                        spirit, best, GuidanceType.PREMIUM_WASTED,
                        "Big mixers will bury your ${best.name}" +
                            (valueAlt?.let { " — pour the ${it.name} here instead." }
                                ?: " — save it for something spirit-forward."),
                    )
                best.tier == BrandTier.VALUE && style == RecipeStyle.SHOWCASE ->
                    SpiritGuidance(
                        spirit, best, GuidanceType.VALUE_EXPOSED,
                        "This drink puts the ${spirit.name.lowercase()} front and center — " +
                            "${best.name} will show its edges here.",
                    )
                best.tier == BrandTier.VALUE && style == RecipeStyle.MASKED ->
                    SpiritGuidance(
                        spirit, best, GuidanceType.VALUE_WELL_PLACED,
                        "A great spot for the ${best.name} — no need to open anything fancier.",
                    )
                else -> null
            }
        }
    }
}
