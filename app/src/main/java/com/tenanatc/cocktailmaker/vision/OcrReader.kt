package com.tenanatc.cocktailmaker.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Reads label text off bottle photos using ML Kit's on-device text recognizer.
 * Runs entirely offline, free, no API key — this is what makes brand and
 * quality detection possible, since generic image models can't tell a G4 label
 * from a Jose Cuervo one.
 */
object OcrReader {

    /** Returns all recognized text, or null if recognition failed. */
    suspend fun readText(bitmap: Bitmap): String? = try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
    } catch (_: Exception) {
        null
    }
}
