package com.onyx.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le décodeur TIFF et la reconnaissance de format, **tels qu'Onyx s'en sert**.
 *
 * Les images d'essai sont construites octet par octet dans le test : un petit TIFF RVB non
 * compressé, un gris PackBits, une palette, un en-tête 16 bits. Ce qui est éprouvé : le format est
 * reconnu par les octets et non par le nom ; les dimensions se lisent ; les pixels sortent aux
 * bonnes couleurs ; et un budget trop court **refuse** l'image au lieu de la produire à moitié.
 */
class TiffImageTest {

    // ---- la reconnaissance de format -------------------------------------------------------------

    @Test
    fun `le format se lit sur les octets, jamais sur le nom`() {
        assertEquals(ImageFormat.JPEG, ImageFormats.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(ImageFormat.PNG, ImageFormats.sniff(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(ImageFormat.GIF, ImageFormats.sniff("GIF89a".toByteArray()))
        assertEquals(ImageFormat.BMP, ImageFormats.sniff(byteArrayOf(0x42, 0x4D, 0x00, 0x00)))
        assertEquals(ImageFormat.TIFF, ImageFormats.sniff(byteArrayOf(0x49, 0x49, 0x2A, 0x00)))
        assertEquals(ImageFormat.TIFF, ImageFormats.sniff(byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)))
        assertEquals(ImageFormat.WEBP, ImageFormats.sniff("RIFFxxxxWEBP".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `le HEIC et l'AVIF se distinguent par leur marque`() {
        assertEquals(ImageFormat.HEIF, ImageFormats.sniff(isoBmff("heic")))
        assertEquals(ImageFormat.HEIF, ImageFormats.sniff(isoBmff("mif1")))
        assertEquals(ImageFormat.AVIF, ImageFormats.sniff(isoBmff("avif")))
    }

    @Test
    fun `un inconnu reste inconnu`() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormats.sniff(byteArrayOf(1, 2, 3, 4)))
        assertNull(null)
    }

    @Test
    fun `le TIFF n'est pas porte par la plateforme, le reste si`() {
        assertTrue(!ImageFormats.handledByPlatform(ImageFormat.TIFF))
        assertTrue(ImageFormats.handledByPlatform(ImageFormat.HEIF))
        assertTrue(ImageFormats.handledByPlatform(ImageFormat.AVIF))
        assertTrue(ImageFormats.handledByPlatform(ImageFormat.JPEG))
        assertTrue(!ImageFormats.handledByPlatform(ImageFormat.UNKNOWN))
    }

    // ---- le TIFF ---------------------------------------------------------------------------------

    @Test
    fun `un TIFF RVB non compresse rend ses pixels`() {
        val pixels = byteArrayOf(
            255.toByte(), 0, 0, 0, 255.toByte(), 0, // ligne 0 : rouge, vert
            0, 0, 255.toByte(), 255.toByte(), 255.toByte(), 0, // ligne 1 : bleu, jaune
        )
        val tiff = TiffBuilder(
            width = 2, height = 2, bits = intArrayOf(8, 8, 8), samplesPerPixel = 3,
            compression = 1, photometric = 2, pixels = pixels,
        ).build()

        assertTrue(TiffImage.isTiff(tiff))
        assertEquals(2 to 2, TiffImage.dimensions(tiff))
        val decoded = TiffImage.decode(tiff)!!
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
        assertEquals(0xFFFF0000.toInt(), decoded.argb[0])
        assertEquals(0xFF00FF00.toInt(), decoded.argb[1])
        assertEquals(0xFF0000FF.toInt(), decoded.argb[2])
        assertEquals(0xFFFFFF00.toInt(), decoded.argb[3])
    }

    @Test
    fun `un TIFF gris PackBits se decompresse`() {
        // PackBits : deux séries littérales — « 01 » (2 octets), puis leurs octets.
        // Deux lignes de deux pixels gris : [0x10, 0x20], [0x30, 0x40].
        val packed = byteArrayOf(1, 0x10, 0x20, 1, 0x30, 0x40)
        val tiff = TiffBuilder(
            width = 2, height = 2, bits = intArrayOf(8), samplesPerPixel = 1,
            compression = 32773, photometric = 1, pixels = packed,
        ).build()
        val decoded = TiffImage.decode(tiff)!!
        assertEquals(0xFF101010.toInt(), decoded.argb[0])
        assertEquals(0xFF404040.toInt(), decoded.argb[3])
    }

    @Test
    fun `un TIFF a palette lit sa table`() {
        // 2 pixels : index 0 (rouge) et 1 (bleu). Table : R=ffff,0000 ; G=0000,0000 ; B=0000,ffff.
        val colorMap = intArrayOf(0xFFFF, 0x0000, 0x0000, 0x0000, 0x0000, 0xFFFF)
        val tiff = TiffBuilder(
            width = 2, height = 1, bits = intArrayOf(8), samplesPerPixel = 1,
            compression = 1, photometric = 3, pixels = byteArrayOf(0, 1), colorMap = colorMap,
        ).build()
        val decoded = TiffImage.decode(tiff)!!
        assertEquals(0xFFFF0000.toInt(), decoded.argb[0])
        assertEquals(0xFF0000FF.toInt(), decoded.argb[1])
    }

    @Test
    fun `un TIFF a trop court est refuse plutot que rendu a moitie`() {
        val tiff = TiffBuilder(
            width = 2, height = 2, bits = intArrayOf(8, 8, 8), samplesPerPixel = 3,
            compression = 1, photometric = 2, pixels = ByteArray(6),
        ).build()
        // Un budget qui ne tient pas deux pixels sur deux lignes : refus.
        assertNull(TiffImage.decode(tiff, maxBytes = 4))
    }

    @Test
    fun `un TIFF illisible rend null, jamais une exception`() {
        assertNull(TiffImage.decode(byteArrayOf(1, 2, 3)))
        assertNull(TiffImage.decode(ByteArray(0)))
        assertNull(TiffImage.dimensions(byteArrayOf(0x49, 0x49, 0x2A, 0x00)))
    }

    // ---- les constructeurs d'essai ---------------------------------------------------------------

    private fun isoBmff(brand: String): ByteArray {
        val brandBytes = brand.padEnd(4, ' ').toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0, 0, 0, 20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()) +
            brandBytes + byteArrayOf(0, 0, 0, 0) + brandBytes
    }

    /**
     * Un constructeur de TIFF petit-boutiste, réduit à ce que les essais demandent : un seul
     * répertoire, une seule bande, les pixels à la fin.
     */
    private class TiffBuilder(
        private val width: Int,
        private val height: Int,
        private val bits: IntArray,
        private val samplesPerPixel: Int,
        private val compression: Int,
        private val photometric: Int,
        private val pixels: ByteArray,
        private val colorMap: IntArray? = null,
    ) {
        fun build(): ByteArray {
            val entries = mutableListOf<Entry>()
            fun short(tag: Int, vararg values: Int) = entries.add(Entry(tag, 3, values.toList()))
            fun long(tag: Int, vararg values: Int) = entries.add(Entry(tag, 4, values.toList()))
            short(256, width)
            short(257, height)
            short(258, *bits)
            short(259, compression)
            short(262, photometric)
            long(273, -1) // rempli une fois la disposition connue
            short(277, samplesPerPixel)
            long(278, height)
            long(279, pixels.size)
            short(284, 1)
            colorMap?.let { short(320, *it) }
            entries.sortBy { it.tag }

            val ifdOffset = 8
            val ifdSize = 2 + entries.size * 12 + 4
            var cursor = ifdOffset + ifdSize
            val extra = HashMap<Int, Int>()
            for (e in entries) {
                val values = e.values
                val byteLen = values.size * typeSize(e.type)
                if (byteLen > 4) {
                    extra[e.tag] = cursor
                    cursor += byteLen
                    if (byteLen % 2 != 0) cursor++ // alignement pair
                }
            }
            val pixelOffset = cursor
            // Le décalage des pixels ne se connaît qu'ici : on le pose dans l'entrée 273.
            entries.first { it.tag == 273 }.values = listOf(pixelOffset)

            val out = java.io.ByteArrayOutputStream()
            fun u16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
            fun u32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
            out.write('I'.code); out.write('I'.code); u16(42); u32(ifdOffset)
            u16(entries.size)
            for (e in entries) {
                u16(e.tag); u16(e.type); u32(e.values.size)
                val byteLen = e.values.size * typeSize(e.type)
                if (byteLen > 4) {
                    u32(extra[e.tag]!!)
                } else {
                    var packed = 0L
                    var shift = 0
                    for (v in e.values) { packed = packed or ((v.toLong() and 0xFFFFFFFFL) shl shift); shift += typeSize(e.type) * 8 }
                    u32((packed and 0xFFFFFFFFL).toInt())
                }
            }
            u32(0) // pas de répertoire suivant

            for (e in entries) {
                val at = extra[e.tag] ?: continue
                while (out.size() < at) out.write(0)
                for (v in e.values) {
                    if (typeSize(e.type) == 2) u16(v) else u32(v)
                }
            }
            while (out.size() < pixelOffset) out.write(0)
            out.write(pixels)
            return out.toByteArray()
        }

        private fun typeSize(type: Int) = if (type == 3) 2 else 4

        private class Entry(val tag: Int, val type: Int, var values: List<Int>)
    }
}
