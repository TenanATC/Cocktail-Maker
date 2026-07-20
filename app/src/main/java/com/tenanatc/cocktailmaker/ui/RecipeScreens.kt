package com.tenanatc.cocktailmaker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.tenanatc.cocktailmaker.data.CocktailData
import com.tenanatc.cocktailmaker.data.Ingredient
import com.tenanatc.cocktailmaker.data.MatchResult
import com.tenanatc.cocktailmaker.data.Recipe
import com.tenanatc.cocktailmaker.data.SpiritGuidance
import com.tenanatc.cocktailmaker.data.UnlockSuggestion

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    results: List<MatchResult>,
    riffs: List<Recipe>,
    unlocks: List<UnlockSuggestion>,
    onOpen: (MatchResult) -> Unit,
    onOpenRiff: (Recipe) -> Unit,
    onAddUnlock: (Ingredient) -> Unit,
) {
    if (results.isEmpty() && riffs.isEmpty() && unlocks.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "No matches yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Try adding a few more ingredients — even one spirit plus a citrus " +
                    "or mixer opens up a lot of drinks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val ready = results.filter { it.makeableNow }
    val almost = results.filterNot { it.makeableNow }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ready.isNotEmpty()) {
            item { SectionHeader("Ready to pour (${ready.size})") }
            items(ready, key = { it.recipe.id }) { match ->
                MatchCard(match, onClick = { onOpen(match) })
            }
        }
        if (almost.isNotEmpty()) {
            item { SectionHeader("Almost there (${almost.size})") }
            items(almost, key = { it.recipe.id }) { match ->
                MatchCard(match, onClick = { onOpen(match) })
            }
        }
        if (riffs.isNotEmpty()) {
            item { SectionHeader("Off-menu riffs (${riffs.size})") }
            item {
                Text(
                    "Not in any book — invented from your shelf using classic formulas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(riffs, key = { it.id }) { riff ->
                RiffCard(riff, onClick = { onOpenRiff(riff) })
            }
        }
        if (unlocks.isNotEmpty()) {
            item { SectionHeader("One bottle away") }
            items(unlocks, key = { it.ingredient.id }) { unlock ->
                UnlockRow(unlock, onAdd = { onAddUnlock(unlock.ingredient) })
            }
        }
    }
}

@Composable
private fun RiffCard(riff: Recipe, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    riff.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "RIFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                riff.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnlockRow(unlock: UnlockSuggestion, onAdd: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    unlock.ingredient.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Unlocks ${unlock.unlocked.size}: " +
                        unlock.unlocked.take(3).joinToString { it.name } +
                        if (unlock.unlocked.size > 3) " +${unlock.unlocked.size - 3} more" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(onClick = onAdd) { Text("Have it") }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MatchCard(match: MatchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (match.makeableNow) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    match.recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (match.makeableNow) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (match.makeableNow) "Makeable now" else "Missing ingredients",
                    tint = if (match.makeableNow) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                match.recipe.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Uses ${match.directHits.size} of your ingredients" +
                    if (match.requiredCount > 0) " (${match.directHits.size + match.substitutions.size}/${match.requiredCount} covered)" else "",
                style = MaterialTheme.typography.labelMedium,
            )
            match.substitutions.forEach { sub ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Swap ${sub.wanted.name} → ${sub.useInstead.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (match.missing.isNotEmpty()) {
                Text(
                    "Missing: ${match.missing.joinToString { it.name }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            match.guidance.forEach { GuidanceRow(it) }
        }
    }
}

/** One line of bottle-quality advice ("save the G4 for something spirit-forward"). */
@Composable
private fun GuidanceRow(guidance: SpiritGuidance) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (guidance.positive) Icons.Default.Star else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (guidance.positive) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            guidance.message,
            style = MaterialTheme.typography.labelMedium,
            color = if (guidance.positive) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
fun DetailScreen(
    modifier: Modifier = Modifier,
    recipe: Recipe,
    match: MatchResult?,
    data: CocktailData,
) {
    val missingIds = match?.missing?.map { it.id }?.toSet() ?: emptySet()
    val subsByWanted = match?.substitutions?.associateBy { it.wanted.id } ?: emptyMap()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            recipe.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Serve in: ${recipe.glass}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (match != null && match.guidance.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Your bottles",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    match.guidance.forEach { GuidanceRow(it) }
                }
            }
        }
        HorizontalDivider()

        Text("Ingredients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        recipe.ingredients.forEach { line ->
            val ingredient = data.ingredientsById.getValue(line.ingredientId)
            val substitution = subsByWanted[line.ingredientId]
            val isMissing = line.ingredientId in missingIds
            Column {
                Row {
                    Text(
                        "• ${line.amount}  ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        ingredient.name + if (line.optional) " (optional)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (substitution != null) TextDecoration.LineThrough else null,
                        color = when {
                            isMissing -> MaterialTheme.colorScheme.error
                            line.optional -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                substitution?.let { sub ->
                    Text(
                        "   ↳ use ${sub.useInstead.name} instead — ${sub.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isMissing) {
                    Text(
                        "   (you're missing this one)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        HorizontalDivider()
        Text("Method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        recipe.instructions.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}. ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (recipe.tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                recipe.tags.joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun BrowseScreen(
    modifier: Modifier = Modifier,
    data: CocktailData,
    onOpen: (Recipe) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val query = search.trim().lowercase()
    val recipes = data.recipes
        .filter { recipe ->
            query.isEmpty() ||
                recipe.name.lowercase().contains(query) ||
                recipe.tags.any { it.contains(query) } ||
                recipe.ingredients.any {
                    data.ingredientsById.getValue(it.ingredientId).name.lowercase().contains(query)
                }
        }
        .sortedBy { it.name }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search by name, tag, or ingredient…") },
            singleLine = true,
        )
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(recipe) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            recipe.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            recipe.ingredients.joinToString {
                                data.ingredientsById.getValue(it.ingredientId).name
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
