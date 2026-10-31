package com.onyx.app.ui.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/**
 * Ce qu'une image de la caméra a donné — les trois cas sont **distincts** et se disent
 * différemment à l'utilisateur :
 *
 *  * [Nothing]    — aucun code repéré : la caméra ne regarde pas encore le code ;
 *  * [Unreadable] — un code repéré, mais illisible (trop petit, flou, ou reflet) ;
 *  * [Text]       — lu.
 *
 * Sans cette distinction, la feuille ne pouvait que deviner (« approchez-le »), ce qui est faux
 * quand le code est déjà dans le champ mais trop petit, flou ou voilé.
 */
sealed interface ScanOutcome {
    data object Nothing : ScanOutcome
    data object Unreadable : ScanOutcome
    data class Text(val value: String) : ScanOutcome
}

/** Décodage ZXing réel des octets Y d'une frame CameraX ; aucun résultat n'est simulé. */
object QrCodeDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    /**
     * Copie la bande Y d'une frame CameraX en **respectant `rowStride`**.
     *
     * Une caméra réelle rembourre chaque ligne : le pas (`row_stride`) dépasse la largeur. Lire
     * `width` octets de suite sans sauter ce rembourrage ** incline** l'image — l'aperçu reste
     * correct à l'écran, mais le QR n'est jamais décodé, et rien ne le signale. On avance donc de
     * `rowStride` par ligne et on ne lit que `width` octets utiles. `null` quand la trame est
     * incohérente (pas plus court que large, ou tronquée) : une frame bizarre ne fait pas planter.
     */
    fun packLuminance(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int): ByteArray? {
        if (width <= 0 || height <= 0 || rowStride < width) return null
        val result = ByteArray(width * height)
        val source = buffer.duplicate()
        for (row in 0 until height) {
            val position = row * rowStride
            if (position < 0 || position + width > source.limit()) return null
            source.position(position)
            source.get(result, row * width, width)
        }
        return result
    }

    /**
     * Décodage qui **distingue** « rien vu » de « vu mais illisible ».
     *
     * `MultiFormatReader` aplatit les deux : il attrape `ChecksumException` et `FormatException`
     * (deux sous-classes de `ReaderException`) et finit par lever `NotFoundException` pour tout. On
     * appelle donc le lecteur QR **directement** — il laisse remonter ces deux exceptions, et
     * c'est précisément l'information que la feuille de scan doit pouvoir dire à l'utilisateur.
     */
    fun scanYuv(luminance: ByteArray, width: Int, height: Int): ScanOutcome {
        if (width <= 0 || height <= 0 || luminance.size < width * height) return ScanOutcome.Nothing
        val source = PlanarYUVLuminanceSource(
            luminance,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        return try {
            ScanOutcome.Text(
                QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text,
            )
        } catch (_: ChecksumException) {
            ScanOutcome.Unreadable
        } catch (_: FormatException) {
            ScanOutcome.Unreadable
        } catch (_: NotFoundException) {
            ScanOutcome.Nothing
        } catch (_: Exception) {
            ScanOutcome.Nothing
        }
    }

    /** Le texte, ou `null` — pour les appelants qui n'ont pas besoin de savoir pourquoi. */
    fun decodeYuv(luminance: ByteArray, width: Int, height: Int): String? =
        (scanYuv(luminance, width, height) as? ScanOutcome.Text)?.value
}

/** Un code immobile est présent dans plusieurs frames : chaque texte n'est signalé qu'une fois. */
class QrCodeDeduplicator {
    private val seen = mutableSetOf<String>()

    @Synchronized
    fun accept(text: String): Boolean = seen.add(text)
}
