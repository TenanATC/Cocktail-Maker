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

        // First pass: read only the dimensions. Note decodeStream ALWAYS returns
        // null under inJustDecodeBounds, so we must not treat that null as failure
        // here — only a missing stream or an exception counts as unreadable.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        } catch (_: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }

        // Second pass: actually decode the downscaled bitmap.
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            resolver.openInputStream(uri).use { stream ->
                if (stream == null) null else BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            null
        }
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
