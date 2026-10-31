package com.onyx.app.ui.setup

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.onyx.app.data.model.SetupCode
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.data.model.SetupCodeParseError
import com.onyx.app.data.model.Transport
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeDecoderTest {

    /**
     * Le lien qu'encode le QR de `src/test/resources/setup-link-qr.png` — la **même forme** que
     * celui de la page `/connect` de la machine (`server`, `key`, `model`, `voices`, `bridge`,
     * `bridgeToken`, `lang`, `capabilities`). La clé et le jeton sont des valeurs de test : aucun
     * secret réel n'est committé, et le contenu d'une valeur ne change rien au décodage.
     */
    private val setupLink =
        "onyx://connect?v=1" +
            "&server=https%3A%2F%2Fdocument-ensure-purple-rear.trycloudflare.com" +
            "&key=test-install-key-123" +
            "&model=hermes-agent" +
            "&voices=https%3A%2F%2Fhart-bruce-durham-directory.trycloudflare.com" +
            "&bridge=https%3A%2F%2Fgive-overnight-reveal-nano.trycloudflare.com" +
            "&bridgeToken=testtoken1" +
            "&lang=fr" +
            "&capabilities=chat%2Cbots%2Cgroups%2Cvoices%2Ccomputer"

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

    /**
     * Le QR réellement affiché (PNG engendré par segno) passe **par le même chemin que
     * l'appareil** : pixels du PNG → bande Y rembourrée (`rowStride` > largeur, comme une vraie
     * caméra) → recomposition en respectant le pas → décodage ZXing. C'est ce chemin-là qui
     * manquait : l'aperçu s'affichait, mais aucun code ne décodait, et rien ne le disait.
     */
    @Test
    fun `le QR segno est decode quand la bande Y a un rowStride plus grand que la largeur`() {
        val qr = PngLuminance.read(resource("/setup-link-qr.png"))
        val width = qr.width
        val height = qr.height
        val rowStride = width + 48

        val padded = paddedLuminance(qr.pixels, width, height, rowStride)
        assertTrue("la trame doit être rembourrée", padded.size > width * height)

        val packed = QrCodeDecoder.packLuminance(ByteBuffer.wrap(padded), width, height, rowStride)
        assertNotNull("rowStride respecté : la bande se recompose", packed)
        assertEquals("le décodeur rend le lien complet", setupLink, QrCodeDecoder.decodeYuv(packed!!, width, height))

        // La preuve que le respect de `rowStride` compte : lire la bande rembourrée comme si elle
        // était compacte incline l'image et ne rend jamais le lien.
        assertNotEquals(setupLink, QrCodeDecoder.decodeYuv(padded, width, height))
    }

    /**
     * Le lien **allégé** que sert désormais la page `/connect` : l'adresse, la clé, le format. Le
     * modèle, les voix, le pont et la langue ne sont plus portés — l'app les découvre seule sur
     * `GET /api/onyx/endpoints`. C'est ce qui fait passer le QR de la version 15 (77 modules) à la
     * version 8 (49 modules), donc des modules deux fois plus gros à l'écran.
     */
    private val connectPageLink =
        "onyx://connect?v=1" +
            "&server=https%3A%2F%2Fdocument-ensure-purple-rear.trycloudflare.com" +
            "&key=cle-de-recette-sans-secret-0123456789abcdefghij"

    /**
     * `src/test/resources/connect-page-qr.png` est le **bitmap servi** par la page corrigée
     * (engendré par la fonction `qr_page_block` de `onyx_one.py`, marge 4 modules, 6 px par module,
     * 342×342). Il doit se décoder par le chemin exact de l'appareil : pixels → bande Y rembourrée
     * (`rowStride`) → recomposition → ZXing.
     */
    @Test
    fun `le QR servi par la page corrigee est decode par le chemin de l appareil`() {
        val qr = PngLuminance.read(resource("/connect-page-qr.png"))
        val rowStride = qr.width + 48
        val packed = QrCodeDecoder.packLuminance(
            ByteBuffer.wrap(paddedLuminance(qr.pixels, qr.width, qr.height, rowStride)),
            qr.width,
            qr.height,
            rowStride,
        )

        assertNotNull(packed)
        assertEquals(connectPageLink, QrCodeDecoder.decodeYuv(packed!!, qr.width, qr.height))
    }

    /**
     * **Le test qui mord.** `connect-page-cut-qr.png` est le rendu de la page d'avant, reproduit
     * tel quel : `segno.svg_inline()` sans `viewBox`, ramené à 280 px comme le fait le navigateur
     * (`width:min(280px,70vw)`). Le dessin reste à son échelle d'origine (486 unités) et se fait
     * **rogner** : l'encre touche le bord, deux motifs de repérage sur trois sont perdus. Le
     * décodeur de l'app doit continuer de refuser ce code — s'il se met un jour à le lire, c'est
     * que la géométrie du fixture a changé et que ce test ne mord plus.
     */
    @Test
    fun `le QR rogne de la page d avant ne se decode pas`() {
        val qr = PngLuminance.read(resource("/connect-page-cut-qr.png"))

        assertNull(QrCodeDecoder.decodeYuv(luminance(qr), qr.width, qr.height))
        assertTrue("le fixture doit être un carré rogné de 280 px", qr.width == 280 && qr.height == 280)
    }

    /** Le lien de la page est **aussi** accepté collé à la main : c'est la voie de secours. */
    @Test
    fun `le lien de la page est accepte par le collage`() {
        val offer = SetupCodeOffer.fromText(connectPageLink)

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://document-ensure-purple-rear.trycloudflare.com", offer?.code?.apiURL)
        assertEquals("cle-de-recette-sans-secret-0123456789abcdefghij", offer?.code?.apiKey)
        assertEquals(Transport.HERMES_SESSIONS, offer?.code?.transport)
        assertTrue("il n'annonce pas d'adresses qu'il ne porte pas", offer?.code?.hasExtras == false)
    }

    /** Et le lien **décodé** du QR mène au même résultat que le lien collé : un seul chemin. */
    @Test
    fun `le lien decode du QR s installe comme le lien colle`() {
        val qr = PngLuminance.read(resource("/connect-page-qr.png"))
        val decoded = QrCodeDecoder.decodeYuv(luminance(qr), qr.width, qr.height)

        assertNotNull(decoded)
        val offer = SetupCodeOffer.fromText(decoded!!)
        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("https://document-ensure-purple-rear.trycloudflare.com", offer?.code?.apiURL)
        assertEquals("cle-de-recette-sans-secret-0123456789abcdefghij", offer?.code?.apiKey)
    }

    /** Les pixels d'un fixture (0/255) tels que la caméra les livre (un octet par pixel). */
    private fun luminance(qr: QrImage): ByteArray = ByteArray(qr.pixels.size) { qr.pixels[it].toByte() }

    /** La luminance d'une matrice ZXing : noir = 0, blanc = 255. */
    private fun luminanceOf(matrix: com.google.zxing.common.BitMatrix): ByteArray =
        ByteArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0 else 0xFF.toByte()
        }

    /**
     * Invalide le code **sans toucher à ses repères** : la ligne et la colonne de modules n° 8
     * (l'information de format, plus deux bandes de données) passent au blanc. Les trois motifs de
     * repérage, la trame de synchronisation et les motifs d'alignement restent intacts : ZXing
     * **trouve** donc le code, et échoue à le lire. C'est le cas « vu mais illisible ».
     */
    private fun spoiledBand(clean: ByteArray, width: Int, height: Int): ByteArray {
        val out = clean.copyOf()
        var firstDarkRow = -1
        var runStart = 0
        var run = 0
        for (y in 0 until height) {
            var x = 0
            while (x < width && clean[y * width + x] != 0.toByte()) x++
            if (x >= width) continue
            firstDarkRow = y
            runStart = x
            while (x < width && clean[y * width + x] == 0.toByte()) {
                run++
                x++
            }
            break
        }
        require(firstDarkRow >= 0 && run >= 7) { "repère introuvable dans l'image de test" }
        val module = run / 7
        val column = runStart + 8 * module
        for (y in firstDarkRow until height) {
            for (x in column until minOf(column + module, width)) out[y * width + x] = 0xFF.toByte()
        }
        val row = firstDarkRow + 8 * module
        for (y in row until minOf(row + module, height)) {
            for (x in runStart until width) out[y * width + x] = 0xFF.toByte()
        }
        return out
    }

    /**
     * Le décodeur dit **pourquoi** il n'a rien rendu : « rien vu » n'est pas « vu mais illisible ».
     * La feuille s'en sert pour ne plus deviner (« approchez-le » alors que le code est déjà dans
     * le champ, simplement trop petit ou voilé).
     */
    @Test
    fun `rien vu et vu mais illisible sont deux reponses distinctes`() {
        val blank = ByteArray(200 * 200) { 0xFF.toByte() }
        assertEquals(ScanOutcome.Nothing, QrCodeDecoder.scanYuv(blank, 200, 200))

        val payload = "onyx://connect?v=1&server=https%3A%2F%2Fdocument-ensure-purple-rear.trycloudflare.com"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 240, 240)
        val clean = luminanceOf(matrix)
        assertEquals(ScanOutcome.Text(payload), QrCodeDecoder.scanYuv(clean, matrix.width, matrix.height))

        // Les repères intacts, l'information de format et deux bandes de données effacées : ZXing
        // **voit** le code et n'arrive pas à le lire. C'est le troisième cas, celui que la feuille
        // doit nommer — et que « approchez-le, évitez le reflet » ne disait jamais.
        val spoiled = QrCodeDecoder.scanYuv(
            spoiledBand(clean, matrix.width, matrix.height),
            matrix.width,
            matrix.height,
        )
        assertEquals(
            "un code vu mais illisible se dit « illisible », pas « rien vu »",
            ScanOutcome.Unreadable,
            spoiled,
        )
    }

    @Test
    fun `un rowStride plus court que la largeur est refuse au lieu de planter`() {
        val qr = PngLuminance.read(resource("/setup-link-qr.png"))
        val padded = paddedLuminance(qr.pixels, qr.width, qr.height, qr.width + 16)

        assertNull(QrCodeDecoder.packLuminance(ByteBuffer.wrap(padded), qr.width, qr.height, qr.width - 1))
    }

    /** Le lien décodé remplit **tout** ce qu'un scan doit remplir, et par le vrai lecteur de code. */
    @Test
    fun `le lien decode remplit serveur cle modele voix pont jeton et langue`() {
        val offer = SetupCodeOffer.fromText(setupLink)

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        val code = offer?.code
        assertNotNull(code)
        assertEquals("https://document-ensure-purple-rear.trycloudflare.com", code?.apiURL)
        assertEquals("test-install-key-123", code?.apiKey)
        assertEquals("hermes-agent", code?.model)
        assertEquals("https://hart-bruce-durham-directory.trycloudflare.com", code?.voicesURL)
        assertEquals("https://give-overnight-reveal-nano.trycloudflare.com", code?.bridgeURL)
        assertEquals("testtoken1", code?.bridgeToken)
        assertEquals("fr", code?.language)
        assertEquals(Transport.HERMES_SESSIONS, code?.transport)
        assertTrue("le récapitulatif doit voir plus que le serveur", code?.hasExtras == true)
        assertTrue(code?.hasVoices == true)
        assertTrue(code?.hasBridge == true)
        assertTrue(code?.hasLanguage == true)
    }

    /** L'ancien schéma reste lu exactement comme le nouveau. */
    @Test
    fun `l ancien schema est accepte comme le nouveau`() {
        val offer = SetupCodeOffer.fromText(setupLink.replace("onyx://", "${SetupCode.LEGACY_SCHEME}://"))

        assertEquals(SetupCodeParseError.NONE, offer?.error)
        assertEquals("test-install-key-123", offer?.code?.apiKey)
        assertEquals("https://give-overnight-reveal-nano.trycloudflare.com", offer?.code?.bridgeURL)
        assertEquals("fr", offer?.code?.language)
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

    private fun resource(path: String): ByteArray =
        javaClass.getResourceAsStream(path)?.readBytes() ?: error("ressource absente : $path")

    /** La bande Y telle qu'une caméra la livre : `width` octets utiles, puis un rembourrage. */
    private fun paddedLuminance(pixels: IntArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val bytes = ByteArray(rowStride * height) { 0xFF.toByte() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[y * rowStride + x] = pixels[y * width + x].toByte()
            }
        }
        return bytes
    }
}

/** Les pixels d'un QR : la luminance (0 noir, 255 blanc) et la largeur qui va avec. */
private class QrImage(val pixels: IntArray, val width: Int) {
    val height: Int get() = if (width == 0) 0 else pixels.size / width
}

/**
 * Lecteur PNG minimal (gris 1 bit, non entrelacé) — le PNG de segno.
 *
 * AWT / `javax.imageio` ne sont pas sur le classpath des tests unitaires Android (le module
 * `java.desktop` n'est pas embarqué) : les pixels sont donc lus ici, à la main, avec le seul
 * `java.util.zip.Inflater`. Ce n'est pas un décodeur PNG générique — il refuse tout ce qui n'est
 * pas le format exact du QR de recette.
 */
private object PngLuminance {
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun read(bytes: ByteArray): QrImage {
        require(bytes.size > 8) { "PNG vide" }
        for (i in signature.indices) require(bytes[i] == signature[i]) { "ce n'est pas un PNG" }

        var width = 0
        var height = 0
        val idat = ByteArrayOutputStream()
        var pos = signature.size
        while (pos + 8 <= bytes.size) {
            val length = beInt(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val data = pos + 8
            when (type) {
                "IHDR" -> {
                    width = beInt(bytes, data)
                    height = beInt(bytes, data + 4)
                    val bitDepth = bytes[data + 8].toInt()
                    val colourType = bytes[data + 9].toInt()
                    val interlace = bytes[data + 12].toInt()
                    require(bitDepth == 1 && colourType == 0 && interlace == 0) {
                        "PNG inattendu : gris 1 bit non entrelacé attendu"
                    }
                }
                "IDAT" -> idat.write(bytes, data, length)
                "IEND" -> pos = bytes.size
            }
            pos += length + 12
        }
        require(width > 0 && height > 0) { "IHDR manquant" }
        return QrImage(unfilter(inflate(idat.toByteArray()), width, height), width)
    }

    private fun beInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun inflate(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun unfilter(raw: ByteArray, width: Int, height: Int): IntArray {
        val stride = (width + 7) / 8
        val pixels = IntArray(width * height)
        val previous = ByteArray(stride)
        val current = ByteArray(stride)
        var pos = 0
        for (y in 0 until height) {
            val filter = raw[pos++].toInt() and 0xFF
            for (i in 0 until stride) current[i] = raw[pos + i]
            pos += stride
            when (filter) {
                0 -> Unit
                1 -> for (i in 1 until stride) current[i] = (current[i] + current[i - 1]).toByte()
                2 -> for (i in 0 until stride) current[i] = (current[i] + previous[i]).toByte()
                3 -> for (i in 0 until stride) {
                    val left = if (i >= 1) current[i - 1].toInt() and 0xFF else 0
                    val up = previous[i].toInt() and 0xFF
                    current[i] = ((current[i].toInt() and 0xFF) + (left + up) / 2).toByte()
                }
                4 -> for (i in 0 until stride) {
                    val left = if (i >= 1) current[i - 1].toInt() and 0xFF else 0
                    val up = previous[i].toInt() and 0xFF
                    val upLeft = if (i >= 1) previous[i - 1].toInt() and 0xFF else 0
                    current[i] = ((current[i].toInt() and 0xFF) + paeth(left, up, upLeft)).toByte()
                }
                else -> error("filtre PNG inconnu : $filter")
            }
            for (x in 0 until width) {
                val bit = (current[x / 8].toInt() shr (7 - (x % 8))) and 1
                pixels[y * width + x] = if (bit == 1) 0xFF else 0x00
            }
            System.arraycopy(current, 0, previous, 0, stride)
        }
        return pixels
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}