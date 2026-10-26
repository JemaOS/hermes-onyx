package com.onyx.app.ui.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeDecoderTest {
    @Test
    fun `un vrai QR ZXing est decode depuis les octets de luminance camera`() {
        val payload = "onyx://connect?dashboard=https%3A%2F%2Fhermes.example"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 240, 240)
        val luminance = ByteArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0 else 0xFF.toByte()
        }

        assertEquals(payload, QrCodeDecoder.decodeYuv(luminance, matrix.width, matrix.height))
    }

    @Test
    fun `une image sans QR ne produit pas de faux scan`() {
        val white = ByteArray(160 * 160) { 0xFF.toByte() }

        assertNull(QrCodeDecoder.decodeYuv(white, 160, 160))
    }

    @Test
    fun `le meme texte visible sur plusieurs frames nest rendu quune fois`() {
        val gate = QrCodeDeduplicator()

        assertTrue(gate.accept("onyx://connect?api=https%3A%2F%2Fa.example"))
        assertFalse(gate.accept("onyx://connect?api=https%3A%2F%2Fa.example"))
        assertTrue(gate.accept("https://example.com"))
    }
}
