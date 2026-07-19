package com.tenanatc.cocktailmaker.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 85

    /**
     * Decodes the image behind [uri], downscaled to at most [MAX_DIMENSION] on
     * the long edge so uploads stay small. Returns null if the Uri can't be read.
     */
    fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** JPEG-compresses [bitmap] and returns it Base64-encoded (no line wraps). */
    fun toBase64Jpeg(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
