package com.onyx.app.ui.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Décodage ZXing réel des octets Y d'une frame CameraX; aucun résultat n'est simulé. */
object QrCodeDecoder {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    fun decodeYuv(luminance: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || luminance.size < width * height) return null
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
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return runCatching { MultiFormatReader().decode(bitmap, hints).text }.getOrNull()
    }
}

/** Un code immobile est présent dans plusieurs frames: chaque texte n'est signalé qu'une fois. */
class QrCodeDeduplicator {
    private val seen = mutableSetOf<String>()

    @Synchronized
    fun accept(text: String): Boolean = seen.add(text)
}
