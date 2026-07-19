package com.tenanatc.cocktailmaker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tenanatc.cocktailmaker.AppViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(
    modifier: Modifier = Modifier,
    vm: AppViewModel,
    onAddPhoto: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val selectedIds = vm.selectedIngredients.map { it.id }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        vm.photo?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Your ingredient photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }

        if (vm.detecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Recognizing ingredients…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        vm.detectionError?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Text(
            "On hand (${vm.selectedIngredients.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (vm.selectedIngredients.isEmpty() && !vm.detecting) {
            Text(
                "Nothing yet. Snap a photo or tap ingredients below to add them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                vm.selectedIngredients.forEach { ingredient ->
                    val bottles = vm.brandsFor(ingredient.id)
                    val label = when {
                        bottles.isEmpty() -> ingredient.name
                        else -> "${ingredient.name} · " + bottles.joinToString { bottle ->
                            if (bottle.tier == com.tenanatc.cocktailmaker.data.BrandTier.PREMIUM) {
                                "${bottle.name} ★"
                            } else {
                                bottle.name
                            }
                        }
                    }
                    InputChip(
                        selected = true,
                        onClick = { vm.removeIngredient(ingredient) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ${ingredient.name}",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAddPhoto) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Add photo")
            }
            Button(
                onClick = { vm.findCocktails() },
                enabled = vm.selectedIngredients.isNotEmpty() && !vm.detecting,
                modifier = Modifier.weight(1f),
            ) {
                Text("Find cocktails")
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search the ingredient list…") },
            singleLine = true,
        )

        val query = search.trim().lowercase()
        vm.data.ingredients
            .filter { ingredient ->
                query.isEmpty() ||
                    ingredient.name.lowercase().contains(query) ||
                    ingredient.aliases.any { it.contains(query) }
            }
            .groupBy { it.category }
            .forEach { (category, ingredients) ->
                Text(
                    category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ingredients.forEach { ingredient ->
                        val selected = ingredient.id in selectedIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) vm.removeIngredient(ingredient)
                                else vm.addIngredient(ingredient)
                            },
                            label = { Text(ingredient.name) },
                        )
                    }
                }
            }
    }
}
