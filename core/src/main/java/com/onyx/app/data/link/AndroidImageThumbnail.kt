package com.onyx.app.data.link

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Réduit l'image d'une page au format gardé: un JPEG dont le plus grand côté tient dans
 * [maxSide] points, qualité 0,8 (`LinkPreviews.thumbnail`, ).
 *
 * Isolé dans son fichier parce qu'il touche `android.graphics`: les tests de lecture d'une page
 * tournent sur la JVM et ne descendent jamais ici.
 */
object AndroidImageThumbnail {

    fun jpeg(bytes: ByteArray, maxSide: Int): ByteArray? {
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return null
        return runCatching {
            val longest = max(decoded.width, decoded.height).coerceAtLeast(1)
            val scale = minOf(1f, maxSide.toFloat() / longest)
            val scaled = if (scale < 1f) {
                val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, width, height, true)
            } else {
                decoded
            }
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        }.getOrNull()
    }
}
