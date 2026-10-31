package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLanguage
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ce que la vignette d'une vidéo affiche : sa durée et son poids.
 *
 * Les mêmes libellés que la référence (`AttachmentTile` / `VideoTile`) : la durée en `m:ss`, et le
 * poids en unités décimales (ko, Mo, Go), arrondi à une décimale.
 */
object VideoMeta {

    /** La durée d'une vidéo, en `0:07`, `1:02`, `1:02:03`. Vide si elle n'est pas connue. */
    fun durationLabel(millis: Long): String {
        if (millis <= 0L) return ""
        val total = millis / 1000
        val seconds = total % 60
        val minutes = (total / 60) % 60
        val hours = total / 3600
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Le poids d'un fichier, comme le dit la référence : `820 o`, `12,4 Mo`.
     *
     * La langue de l'appareil donne le séparateur décimal — c'est un nombre, pas un identifiant.
     */
    fun sizeLabel(bytes: Long, locale: java.util.Locale = OnyxLanguage.preferred): String {
        if (bytes <= 0L) return ""
        val units = listOf("o", "ko", "Mo", "Go", "To")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000.0 && unit < units.lastIndex) {
            value /= 1000.0
            unit += 1
        }
        return if (unit == 0) "$bytes ${units[0]}" else String.format(locale, "%.1f %s", value, units[unit])
    }

    /**
     * Ce qu'un lecteur d'écran annonce pour une vignette de vidéo : « Vidéo : son nom, son poids »,
     * mot pour mot la phrase de la référence.
     */
    fun accessibilityLabel(
        name: String,
        bytes: Long,
        locale: java.util.Locale = OnyxLanguage.preferred,
    ): String {
        val size = sizeLabel(bytes, locale)
        return if (size.isEmpty()) "Video: $name" else "Video: $name, $size"
    }
}

/**
 * La vignette d'une vidéo : la première image du film.
 *
 * `MediaMetadataRetriever` lit un plan-clé sans décoder tout le film, et se contente d'un
 * descripteur de fichier — le même chemin que pour le lecteur, donc aucun fichier temporaire.
 */
object VideoPoster {

    /** La première image de la vidéo à [uri], ou `null` quand il n'y en a pas. */
    suspend fun frame(context: Context, uri: Uri, maxPixel: Int = 512): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                open(context, uri)?.use { descriptor -> retriever.setDataSource(descriptor.fileDescriptor) }
                    ?: return@withContext null
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: return@withContext null
                scale(frame, maxPixel).asImageBitmap()
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }

    /** La durée de la vidéo en millisecondes, ou 0 quand elle n'est pas lisible. */
    suspend fun durationMillis(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            open(context, uri)?.use { descriptor -> retriever.setDataSource(descriptor.fileDescriptor) }
                ?: return@withContext 0L
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Le poids du fichier, ou 0 quand il n'est pas lisible. */
    suspend fun byteCount(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        runCatching {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> File(uri.path.orEmpty()).length()
                else -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            }
        }.getOrDefault(0L)
    }

    /** Ouvre l'adresse en lecture : le fichier directement, sinon par le fournisseur de contenu. */
    private fun open(context: Context, uri: Uri): ParcelFileDescriptor? = runCatching {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> ParcelFileDescriptor.open(
                File(uri.path.orEmpty()),
                ParcelFileDescriptor.MODE_READ_ONLY,
            )

            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
    }.getOrNull()

    /** Ramène l'image sous [maxPixel] sur son plus grand côté : une vignette n'a pas besoin de plus. */
    private fun scale(bitmap: Bitmap, maxPixel: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPixel || longest == 0) return bitmap
        val ratio = maxPixel.toFloat() / longest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
