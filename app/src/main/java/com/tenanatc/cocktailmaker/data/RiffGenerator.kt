package com.tenanatc.cocktailmaker.data

/**
 * Invents "off-menu" drinks from the ingredients on hand using time-tested
 * cocktail formulas (sour, highball, old fashioned, spritz). Every generated
 * riff is fully makeable by construction, clearly tagged "riff", and skipped
 * whenever the real recipe book already covers the same bottles — the goal is
 * to fill gaps, not to compete with the classics.
 *
 * Guardrails keep the creativity drinkable: templates only draw from roles
 * that belong in them, a small veto list blocks known-bad pairings, and a
 * bonus list nudges combinations bartenders actually reach for.
 */
class RiffGenerator(private val data: CocktailData) {

    private companion object {
        val SOUR_SPIRITS = setOf(
            "vodka", "gin", "white_rum", "dark_rum", "tequila", "mezcal",
            "bourbon", "rye_whiskey", "scotch", "irish_whiskey", "brandy", "cachaca",
        )

        /** Barrel-aged / structured spirits that can carry a spirit-forward template. */
        val OLD_FASHIONED_SPIRITS = setOf(
            "bourbon", "rye_whiskey", "scotch", "irish_whiskey", "brandy",
            "dark_rum", "tequila", "mezcal",
        )
        val CITRUS = listOf("lime_juice", "lemon_juice")
        val SOUR_SWEETS = listOf("simple_syrup", "honey_syrup", "agave_syrup", "orgeat")
        val OF_SWEETS = listOf("simple_syrup", "agave_syrup", "honey_syrup")
        val BITTERS = listOf("angostura_bitters", "orange_bitters", "peychauds_bitters")
        val FIZZ = listOf("soda_water", "tonic_water", "ginger_beer", "ginger_ale", "cola")
        val SPRITZ_CORES = listOf("aperol", "campari", "elderflower_liqueur")

        /** Pairings bartenders actually reach for — nudged up in the ranking. */
        val BONUS_PAIRS = setOf(
            "bourbon" to "lemon_juice", "rye_whiskey" to "lemon_juice",
            "brandy" to "lemon_juice", "irish_whiskey" to "honey_syrup",
            "scotch" to "honey_syrup", "mezcal" to "honey_syrup",
            "tequila" to "lime_juice", "mezcal" to "lime_juice",
            "white_rum" to "lime_juice", "dark_rum" to "lime_juice",
            "cachaca" to "lime_juice", "gin" to "lime_juice",
            "dark_rum" to "ginger_beer", "mezcal" to "ginger_beer",
            "scotch" to "ginger_beer", "bourbon" to "ginger_ale",
        )

        /** Known-bad pairings — never generated, no matter what's on the shelf. */
        val VETO_PAIRS = setOf(
            "gin" to "cola", "scotch" to "cola", "mezcal" to "cola",
            "scotch" to "tonic_water",
        )
    }

    private data class Candidate(val recipe: Recipe, val spiritId: String, val score: Int)

    fun generate(
        availableIds: Set<String>,
        brandsByIngredient: Map<String, List<Brand>> = emptyMap(),
        max: Int = 6,
    ): List<Recipe> {
        if (availableIds.isEmpty()) return emptyList()

        // Sets we must not duplicate: any real recipe's exact required set, or a
        // recipe the user can already make outright (a riff that is merely a
        // stripped-down version of it would be noise).
        val requiredSets = data.recipes.map { recipe ->
            recipe.ingredients
                .filter { !it.optional && it.ingredientId !in data.pantry }
                .map { it.ingredientId }
                .toSet()
        }
        val makeableSets = requiredSets.filter { it.isNotEmpty() && availableIds.containsAll(it) }

        fun isRedundant(riffIds: Set<String>): Boolean =
            requiredSets.any { it == riffIds } ||
                makeableSets.any { riffIds.all(it::contains) }

        fun premium(spiritId: String): Brand? =
            brandsByIngredient[spiritId]?.firstOrNull { it.tier == BrandTier.PREMIUM }

        fun name(id: String) = data.ingredientsById.getValue(id).name

        val candidates = mutableListOf<Candidate>()

        fun add(
            templateId: String,
            spiritId: String,
            ids: List<String>,
            recipeName: String,
            glass: String,
            kinNote: String,
            amounts: List<Pair<String, String>>,
            instructions: List<String>,
            baseScore: Int,
            premiumEligible: Boolean,
        ) {
            val idSet = ids.toSet()
            if (isRedundant(idSet)) return
            val bottle = if (premiumEligible) premium(spiritId) else null
            val description = buildString {
                append(kinNote)
                bottle?.let { append(" A fine stage for your ${it.name}.") }
            }
            val score = baseScore +
                ids.count { other -> (spiritId to other) in BONUS_PAIRS } +
                (if (bottle != null) 2 else 0)
            candidates += Candidate(
                Recipe(
                    id = "riff_${templateId}_" + ids.joinToString("_"),
                    name = recipeName,
                    glass = glass,
                    description = description,
                    ingredients = amounts.map { (id, amount) -> RecipeIngredient(id, amount) },
                    instructions = instructions,
                    tags = listOf("riff", templateId),
                ),
                spiritId,
                score,
            )
        }

        // --- Sour: 2 : 3/4 : 3/4, the most forgiving formula in the book ---
        for (spirit in SOUR_SPIRITS.intersect(availableIds)) {
            for (citrus in CITRUS.filter { it in availableIds }) {
                for (sweet in SOUR_SWEETS.filter { it in availableIds }) {
                    val sweetWord = when (sweet) {
                        "honey_syrup" -> "Honey "
                        "agave_syrup" -> "Agave "
                        "orgeat" -> "Orgeat "
                        else -> ""
                    }
                    add(
                        templateId = "sour",
                        spiritId = spirit,
                        ids = listOf(spirit, citrus, sweet),
                        recipeName = "${name(spirit)} ${sweetWord}Sour",
                        glass = "Coupe or rocks glass",
                        kinNote = "Invented from your shelf on the classic sour formula " +
                            "(2 : ¾ : ¾) — kin to the Daiquiri and the Whiskey Sour.",
                        amounts = listOf(
                            spirit to "2 oz", citrus to "3/4 oz", sweet to "3/4 oz",
                        ),
                        instructions = listOf(
                            "Shake everything hard with ice.",
                            "Strain into a chilled coupe, or over fresh ice in a rocks glass.",
                        ),
                        baseScore = 1,
                        premiumEligible = true,
                    )
                }
            }
        }

        // --- Highball: 2 oz + 4 oz of something cold and fizzy ---
        for (spirit in SOUR_SPIRITS.intersect(availableIds)) {
            for (mixer in FIZZ.filter { it in availableIds }) {
                if ((spirit to mixer) in VETO_PAIRS) continue
                val riffName = if (mixer == "soda_water") {
                    "${name(spirit)} Highball"
                } else {
                    "${name(spirit)} & ${name(mixer)}"
                }
                add(
                    templateId = "highball",
                    spiritId = spirit,
                    ids = listOf(spirit, mixer),
                    recipeName = riffName,
                    glass = "Highball glass",
                    kinNote = "Invented from your shelf on the two-part highball formula — " +
                        "kin to the G&T and the Dark 'n' Stormy.",
                    amounts = listOf(spirit to "2 oz", mixer to "4 oz"),
                    instructions = listOf(
                        "Fill a chilled highball with ice and add the spirit.",
                        "Top gently with the cold mixer and stir once.",
                    ),
                    baseScore = 0,
                    premiumEligible = false,
                )
            }
        }

        // --- Old fashioned template: spirit + sugar + bitters ---
        for (spirit in OLD_FASHIONED_SPIRITS.intersect(availableIds)) {
            val sweet = OF_SWEETS.firstOrNull { it in availableIds } ?: continue
            val bitters = BITTERS.firstOrNull { it in availableIds } ?: continue
            add(
                templateId = "old_fashioned",
                spiritId = spirit,
                ids = listOf(spirit, sweet, bitters),
                recipeName = "${name(spirit)} Old Fashioned Riff",
                glass = "Rocks glass",
                kinNote = "Invented from your shelf on the original cocktail formula — " +
                    "spirit, sugar, bitters — kin to the Old Fashioned.",
                amounts = listOf(
                    spirit to "2 oz", sweet to "1 tsp", bitters to "2 dashes",
                ),
                instructions = listOf(
                    "Stir everything with ice until well chilled.",
                    "Strain over a large cube and express a citrus peel over the top.",
                ),
                baseScore = 1,
                premiumEligible = true,
            )
        }

        // --- Spritz: 2 : 3 : 1 over lots of ice ---
        if ("sparkling_wine" in availableIds && "soda_water" in availableIds) {
            for (core in SPRITZ_CORES.filter { it in availableIds }) {
                add(
                    templateId = "spritz",
                    spiritId = core,
                    ids = listOf(core, "sparkling_wine", "soda_water"),
                    recipeName = "${name(core)} Spritz Riff",
                    glass = "Large wine glass",
                    kinNote = "Invented from your shelf on the aperitivo spritz formula " +
                        "(2 : 3 : 1) — kin to the Aperol Spritz.",
                    amounts = listOf(
                        core to "2 oz", "sparkling_wine" to "3 oz", "soda_water" to "1 oz",
                    ),
                    instructions = listOf(
                        "Fill a big wine glass with ice.",
                        "Add the sparkling wine, then the liqueur, then a splash of soda.",
                    ),
                    baseScore = 1,
                    premiumEligible = false,
                )
            }
        }

        // Rank by fit, keep variety (at most 2 riffs per base spirit), cap the list.
        return candidates
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.recipe.name })
            .groupBy { it.spiritId }
            .values
            .flatMap { it.take(2) }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.recipe.name })
            .take(max)
            .map { it.recipe }
    }
}
