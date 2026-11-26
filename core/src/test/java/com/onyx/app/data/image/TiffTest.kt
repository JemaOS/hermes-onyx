package com.onyx.app.data.image

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le décodeur TIFF de la maison, éprouvé **hors appareil** : `BitmapFactory` ne sait pas lire un
 * TIFF, donc toute la charge repose sur ce décodeur — il doit rendre les bons pixels pour chaque
 * variante admise, et rendre `null`, **sans jamais lever**, pour tout fichier abîmé.
 *
 * Les fichiers d'épreuve (`src/test/resources/tiff/…`) sont de vrais TIFF écrits par la
 * bibliothèque de référence ; les tests « cassés » sont des variantes fabriquées à la main.
 */
class TiffTest {

    @Test
    fun `toutes les compressions donnent les mêmes pixels`() {
        val attendu = IntArray(W * H) { rgb(it % W, it / W) }
        listOf(
            "rgb-none", "rgb-packbits", "rgb-packbits-strips", "rgb-lzw", "rgb-lzw-strips", "rgb-deflate",
            "rgb-deflate-strips", "rgb-lzw-predictor", "rgb-deflate-predictor", "rgb-bigendian",
        ).forEach { nom ->
            val image = decoded(nom, maxEdge = 100)
            assertEquals(nom, W, image.width)
            assertEquals(nom, H, image.height)
            assertArrayEquals(nom, attendu, image.argb)
            assertEquals(nom, 1, image.orientation)
        }
    }

    @Test
    fun `gris palette bi-niveau et alpha`() {
        val grisImage = decoded("gray-deflate", 100)
        assertArrayEquals(IntArray(W * H) { gray(it % W * 50 + it / W) }, grisImage.argb)

        val couleurs = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())
        val palette = decoded("palette-packbits", 100)
        assertArrayEquals(IntArray(W * H) { couleurs[(it % W + it / W) % 4] }, palette.argb)

        val biNiveau = decoded("bilevel-none", 100)
        assertArrayEquals(IntArray(W * H) { if ((it % W + it / W) % 2 == 0) gray(255) else gray(0) }, biNiveau.argb)

        val rgba = decoded("rgba-lzw", 100)
        assertEquals(0x800A141E.toInt(), rgba.argb[0])
        assertArrayEquals(
            IntArray(W * H - 1) { rgb((it + 1) % W, (it + 1) / W) },
            rgba.argb.copyOfRange(1, W * H),
        )
    }

    @Test
    fun `un long ruban LZW élargit ses codes et vide sa table`() {
        // Le LZW de la bibliothèque de référence sur 48 Ko : les codes atteignent 12 bits, la table se remplit et se vide.
        val image = decoded("gray-lzw-large", maxEdge = 256)
        assertEquals(256, image.width)
        assertEquals(192, image.height)
        val attendu = IntArray(256 * 192) { i ->
            val x = i % 256
            val y = i / 256
            gray(
                if ((x / 32 + y / 32) % 2 == 0) (x + y) and 255
                else (((x * 73856093L) xor (y * 19349663L)) and 0xFFFF).toInt() shr 4 and 255,
            )
        }
        assertArrayEquals(attendu, image.argb)
    }

    @Test
    fun `une grande image est moyennée jusqu'au bord`() {
        // 5 × 4 réduits à un bord de 2 : des blocs de 3 × 3, les derniers plus étroits et plus courts.
        val image = decoded("rgb-lzw", maxEdge = 2)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(argb(60, 80, 215), image.argb[0])
        // x 3–4, y 3 : r (180 + 240) / 2, g 240, b (135 + 95) / 2.
        assertEquals(argb(210, 240, 115), image.argb[3])
    }

    @Test
    fun `la moyenne pèse les couleurs par leur alpha`() {
        // Les 20 pixels en un seul : le pixel à moitié transparent compte pour moitié en couleur.
        val image = decoded("rgba-lzw", maxEdge = 1)
        val pixels = (0 until W * H).map {
            if (it == 0) intArrayOf(128, 10, 20, 30)
            else rgb(it % W, it / W).let { p -> intArrayOf(255, p shr 16 and 255, p shr 8 and 255, p and 255) }
        }
        val alpha = pixels.sumOf { it[0] }
        fun canal(c: Int) = pixels.sumOf { it[c] * it[0] } / alpha
        assertEquals(
            ((alpha / (W * H)) shl 24) or (canal(1) shl 16) or (canal(2) shl 8) or canal(3),
            image.argb.single(),
        )
    }

    @Test
    fun `une table de couleurs sur une image non palette est ignorée`() {
        val ruban = ByteArray(16) { (it * 16).toByte() }
        val image = decodeTiff(grayTiff(4, 4, ruban, compression = 1, extra = listOf(Tag(320, 3, 7, 0))), 100)
        assertNotNull(image)
        assertEquals(gray(0x10), image!!.argb[1])
    }

    @Test
    fun `les fichiers abîmés rendent null`() {
        listOf(
            "broken-truncated", "broken-header-only", "broken-huge", "broken-lying-strip", "broken-lzw-noise",
            "broken-garbage", "broken-ifd-offset", "broken-ifd-count",
        ).forEach { assertNull(it, decodeTiff(fixture(it), 100)) }
        assertNull(decodeTiff(ByteArray(0), 100))
        assertNull(decodeTiff("pas un tiff".encodeToByteArray(), 100))
    }

    @Test
    fun `une image au-delà du plafond est refusée depuis son en-tête`() {
        assertNull(decodeTiff(fixture("rgb-none"), 100, maxPixels = W * H - 1L))
        assertNotNull(decodeTiff(fixture("rgb-none"), 100, maxPixels = W * H.toLong()))
        // Une bande entière et valide de 100 × 100, refusée par le seul plafond.
        val ruban = deflate(ByteArray(10_000), times = 1)
        assertNull(decodeTiff(grayTiff(100, 100, ruban, compression = 8), 100, maxPixels = 9_999))
        assertNotNull(decodeTiff(grayTiff(100, 100, ruban, compression = 8), 100, maxPixels = 10_000))
        // Deux côtés dont le produit dépasse un Long.
        assertNull(decodeTiff(grayTiff(0xFFFFFFFFL, 0xFFFFFFFFL, ByteArray(16), compression = 1), 100))
    }

    @Test
    fun `un million de minuscules bandes est rapide, une de plus est refusée`() {
        val debut = System.nanoTime()
        val image = decodeTiff(manyStrips(MAX_STRIPS_IN_TEST), 100)
        assertNotNull(image)
        assertEquals(gray(0), image!!.argb[0])
        assertTrue("a pris ${(System.nanoTime() - debut) / 1_000_000} ms", System.nanoTime() - debut < 5_000_000_000L)
        assertNull(decodeTiff(manyStrips(MAX_STRIPS_IN_TEST + 1), 100))
    }

    /**
     * Une image LZW de 1 × [rows] avec une bande par ligne. Chaque valeur de StripOffsets et de
     * StripByteCounts vaut l'octet 200 d'un même tableau, et 200 octets de zéros à 200 forment une
     * bande dont le premier code de 9 bits est un littéral 0.
     */
    private fun manyStrips(rows: Int): ByteArray {
        val tableau = 400L
        val fichier = grayTiff(
            1,
            rows.toLong(),
            ByteArray(0),
            compression = 5,
            stripTags = listOf(Tag(273, 1, rows.toLong(), tableau), Tag(279, 1, rows.toLong(), tableau)),
        )
        return fichier.copyOf(tableau.toInt() + rows).also { it.fill(200.toByte(), tableau.toInt(), it.size) }
    }

    @Test
    fun `une bande qui gonfle au-delà de ses lignes n'est lue que pour elles`() {
        val image = decodeTiff(grayTiff(4, 4, deflate(ByteArray(1 shl 20), times = 64), compression = 8), 100)
        assertNotNull(image)
        assertArrayEquals(IntArray(16) { gray(0) }, image!!.argb)
    }

    @Test
    fun `les fichiers modifiés ou coupés ne lèvent jamais`() {
        val aleatoire = Random(42)
        val bons = listOf(
            "rgb-packbits", "rgb-lzw", "rgb-lzw-strips", "rgb-deflate-predictor", "rgb-bigendian",
            "palette-packbits", "bilevel-none", "rgba-lzw",
        ).map(::fixture)
        repeat(5000) { i ->
            val octets = bons[i % bons.size].copyOf()
            val mutant = if (aleatoire.nextInt(5) == 0) {
                octets.copyOf(aleatoire.nextInt(4, octets.size))
            } else {
                repeat(aleatoire.nextInt(1, 9)) { octets[aleatoire.nextInt(4, octets.size)] = aleatoire.nextInt(256).toByte() }
                octets
            }
            // Null ou une image ; toute exception fait échouer le test.
            decodeTiff(mutant, maxEdge = 64)?.let { assertTrue(it.width in 1..64 && it.height in 1..64) }
        }
    }

    @Test
    fun `les deux ordres d'octets sont reconnus`() {
        assertTrue(isTiff(fixture("rgb-none")))
        assertTrue(isTiff(fixture("rgb-bigendian")))
        assertFalse(isTiff(byteArrayOf(0x49, 0x49, 0x2B, 0x00)))
        assertFalse(isTiff(byteArrayOf(0x49, 0x49, 0x2A)))
    }

    /**
     * Le budget du module : le résultat ne dépasse **jamais** le plus grand côté demandé, quel que
     * soit le cadrage — c'est ce qui garantit qu'un TIFF livré par un agent ne peut pas faire
     * exploser la mémoire d'une vignette ou d'un lecteur.
     */
    @Test
    fun `le résultat tient toujours dans le bord demandé`() {
        for (bord in listOf(1, 2, 3, 8, 64, 100, 512)) {
            val image = decoded("gray-lzw-large", maxEdge = bord)
            assertTrue("bord=$bord → ${image.width}×${image.height}", image.width <= bord && image.height <= bord)
            assertTrue(image.width >= 1 && image.height >= 1)
        }
        assertNull(decodeTiff(fixture("rgb-none"), maxEdge = 0))
    }

    private fun decoded(name: String, maxEdge: Int, maxPixels: Long = MAX_TIFF_PIXELS): TiffRaster {
        val image = decodeTiff(fixture(name), maxEdge, maxPixels)
        assertNotNull(name, image)
        return image!!
    }

    private fun fixture(nom: String): ByteArray {
        val flux = javaClass.getResourceAsStream("/tiff/$nom.tif")
        assertNotNull(nom, flux)
        return flux!!.readBytes()
    }

    private fun rgb(x: Int, y: Int) = argb(x * 60, y * 80, 255 - x * 40)

    private fun gray(v: Int) = argb(v, v, v)

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun deflate(chunk: ByteArray, times: Int): ByteArray {
        val deflater = Deflater(9)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        repeat(times) {
            deflater.setInput(chunk)
            while (!deflater.needsInput()) out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.finish()
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private data class Tag(val tag: Int, val type: Int, val count: Long, val value: Long)

    /**
     * Un TIFF 8 bits gris petit-boutiste dont la bande [strip] suit l'annuaire, avec des entrées
     * [extra]. [stripTags] remplace les StripOffsets et StripByteCounts qui pointent sur [strip],
     * et RowsPerStrip vaut alors 1.
     */
    private fun grayTiff(
        width: Long,
        height: Long,
        strip: ByteArray,
        compression: Int,
        extra: List<Tag> = emptyList(),
        stripTags: List<Tag>? = null,
    ): ByteArray {
        val count = 7 + 2 + extra.size
        val dataAt = 8L + 2 + 12 * count + 4
        val entries = listOf(
            Tag(256, 4, 1, width), Tag(257, 4, 1, height), Tag(258, 3, 1, 8), Tag(259, 3, 1, compression.toLong()),
            Tag(262, 3, 1, 1), Tag(277, 3, 1, 1), Tag(278, 4, 1, if (stripTags == null) height else 1),
        ) + (stripTags ?: listOf(Tag(273, 4, 1, dataAt), Tag(279, 4, 1, strip.size.toLong()))) + extra
        val out = ByteArrayOutputStream()
        fun le(value: Long, bytes: Int) = repeat(bytes) { out.write((value ushr (8 * it)).toInt() and 0xFF) }
        out.write("II".encodeToByteArray())
        le(42, 2)
        le(8, 4)
        le(entries.size.toLong(), 2)
        entries.forEach { (tag, type, n, value) ->
            le(tag.toLong(), 2)
            le(type.toLong(), 2)
            le(n, 4)
            le(value, 4)
        }
        le(0, 4)
        out.write(strip)
        return out.toByteArray()
    }

    private companion object {
        const val W = 5
        const val H = 4

        /** Le plafond de bandes du décodeur lui-même. */
        const val MAX_STRIPS_IN_TEST = 1 shl 20
    }
}
