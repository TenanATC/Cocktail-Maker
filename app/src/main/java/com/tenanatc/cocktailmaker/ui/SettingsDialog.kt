package com.tenanatc.cocktailmaker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    currentGeminiKey: String,
    currentClarifaiKey: String,
    onSaveGemini: (String) -> Unit,
    onSaveClarifai: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var gemini by remember { mutableStateOf(currentGeminiKey) }
    var clarifai by remember { mutableStateOf(currentClarifaiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Photo recognition",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Recognition uses Google's Gemini vision model — it reads bottle " +
                        "labels and identifies the spirit, brand, and quality. Get a free " +
                        "key at aistudio.google.com → \"Get API key\", then paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = gemini,
                    onValueChange = { gemini = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API key") },
                    singleLine = true,
                )
                Text(
                    "Stored only on this device. Recipes, matching, and manual ingredient " +
                        "picking always work offline without any key.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                Text(
                    "Legacy: Clarifai (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Only used if no Gemini key is set — the older on-device OCR path. " +
                        "You can leave this blank.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = clarifai,
                    onValueChange = { clarifai = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Clarifai token (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveGemini(gemini)
                onSaveClarifai(clarifai)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
