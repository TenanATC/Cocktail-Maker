package com.tenanatc.cocktailmaker.data

import org.json.JSONObject
import java.text.Normalizer

/** Quality tier of a recognized bottle, ordered best-first. */
enum class BrandTier { PREMIUM, MID, VALUE }

/**
 * A known bottle brand. [keywords] are matched against OCR text read off the
 * label; [ingredientId] links the brand to the canonical ingredient it fills.
 */
data class Brand(
    val id: String,
    val name: String,
    val ingredientId: String,
    val tier: BrandTier,
    val keywords: List<String>,
)

object BrandParser {
    fun parse(brandsJson: String): List<Brand> {
        val arr = JSONObject(brandsJson).getJSONArray("brands")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Brand(
                id = o.getString("id"),
                name = o.getString("name"),
                ingredientId = o.getString("ingredient"),
                tier = BrandTier.valueOf(o.getString("tier").uppercase()),
                keywords = o.getJSONArray("keywords").let { k ->
                    List(k.length()) { j -> k.getString(j) }
                },
            )
        }
    }
}

/**
 * Finds known brands in the text OCR'd from bottle labels.
 *
 * Matching is done on normalized text (lowercased, accents stripped,
 * punctuation collapsed) with word boundaries. When two matches overlap in the
 * text, the longer (more specific) one wins — so "JOHNNIE WALKER BLACK LABEL"
 * resolves to the Black Label entry, not the generic Johnnie Walker fallback.
 */
class BrandDetector(private val brands: List<Brand>) {

    private data class Occurrence(val brand: Brand, val range: IntRange)

    fun detect(rawText: String): List<Brand> {
        if (rawText.isBlank()) return emptyList()
        val text = " ${normalize(rawText)} "

        val occurrences = mutableListOf<Occurrence>()
        for (brand in brands) {
            for (keyword in brand.keywords) {
                val needle = " ${normalize(keyword)} "
                var from = 0
                while (true) {
                    val at = text.indexOf(needle, from)
                    if (at < 0) break
                    occurrences += Occurrence(brand, at until (at + needle.length))
                    from = at + 1
                }
            }
        }

        val kept = occurrences.filter { candidate ->
            occurrences.none { other ->
                other.brand.id != candidate.brand.id &&
                    other.range.count() > candidate.range.count() &&
                    candidate.range.first <= other.range.last &&
                    other.range.first <= candidate.range.last
            }
        }

        return kept
            .sortedBy { it.range.first }
            .map { it.brand }
            .distinctBy { it.id }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
