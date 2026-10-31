package com.onyx.app.data.image

import java.util.zip.Inflater

/**
 * Un décodeur TIFF **borné**, en Kotlin pur (sans Android), pour le seul format d'image que la
 * plateforme ne décode pas.
 *
 * Android décode le JPEG, le PNG, le GIF, le WebP, le BMP, et — selon la version — le HEIC et
 * l'AVIF ; **aucun** décodeur système ne lit un TIFF. Ce fichier comble ce trou : il lit le premier
 * répertoire (la première page d'une image à plusieurs pages), applique la décompression de chaque
 * bande (aucune, PackBits, LZW, Deflate), dé-prédit si besoin (Predictor 2), et rend des pixels
 * ARGB — jamais plus que ce que [maxBytes] autorise.
 *
 * Ce qui est porté : les compressions 1 (aucune), 5 (LZW), 8 et 32946 (Deflate), 32773 (PackBits) ;
 * les photométries 0 (WhiteIsZero), 1 (BlackIsZero), 2 (RVB), 3 (palette) et 5 (CMJN, conversion
 * simple) ; les profondeurs 1, 4, 8 et 16 bits par échantillon ; la disposition par bandes, planaire
 * et non planaire. Tout le reste rend `null` — jamais une exception, jamais un dessin inventé.
 *
 * Le décodage se fait **pleine résolution dans la bande**, puis l'image est réduite au pas demandé
 * ([sample]) au moment d'écrire la sortie : la mémoire reste du poids d'une bande, pas de l'image.
 * Une bande qui, décompressée, dépasse le budget rend `null` (on préfère refuser que de risquer).
 */
object TiffImage {

    /** Une image décodée : ses dimensions, et ses pixels ARGB (0xAARRGGBB), ligne par ligne. */
    class Decoded(val width: Int, val height: Int, val argb: IntArray)

    /** Vrai si [bytes] commence par un en-tête TIFF. */
    fun isTiff(bytes: ByteArray): Boolean = ImageFormats.sniff(bytes) == ImageFormat.TIFF

    /** Les dimensions de la première page, ou `null` si l'en-tête n'est pas lisible. */
    fun dimensions(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val reader = Reader(bytes)
        val ifd = reader.ifd0() ?: return null
        val width = ifd.value(Tag.WIDTH)?.first() ?: return null
        val height = ifd.value(Tag.HEIGHT)?.first() ?: return null
        if (width <= 0 || height <= 0) return null
        width to height
    }.getOrNull()

    /**
     * Décode [bytes] au pas [sample] (1 = pleine résolution), dans la limite de [maxBytes] pour la
     * sortie comme pour une bande décompressée.
     */
    fun decode(bytes: ByteArray, sample: Int = 1, maxBytes: Int = BoundedImage.MAX_BYTES): Decoded? = runCatching {
        val reader = Reader(bytes)
        val ifd = reader.ifd0() ?: return null
        val width = ifd.value(Tag.WIDTH)?.first() ?: return null
        val height = ifd.value(Tag.HEIGHT)?.first() ?: return null
        if (width <= 0 || height <= 0) return null
        val step = sample.coerceAtLeast(1)

        val outWidth = (width + step - 1) / step
        val outHeight = (height + step - 1) / step
        if (outWidth.toLong() * outHeight.toLong() * BoundedImage.BYTES_PER_PIXEL > maxBytes.toLong()) return null
        if (!BoundedImage.canDraw(outWidth.toLong() * outHeight.toLong() * BoundedImage.BYTES_PER_PIXEL)) return null

        val bits = ifd.value(Tag.BITS) ?: intArrayOf(8)
        val samplesPerPixel = ifd.value(Tag.SAMPLES)?.first() ?: bits.size.coerceAtLeast(1)
        val bitsPerSample = bits.firstOrNull() ?: 8
        val bitsUniform = bits.all { it == bitsPerSample }
        if (!bitsUniform || bitsPerSample !in intArrayOf(1, 4, 8, 16)) return null

        val sampleFormat = ifd.value(Tag.SAMPLE_FORMAT)?.firstOrNull() ?: 1
        if (sampleFormat != 1 && sampleFormat != 2 && sampleFormat != 4) return null // non signé, ou « undefined » (traité en non signé)

        val compression = ifd.value(Tag.COMPRESSION)?.first() ?: 1
        if (compression !in intArrayOf(1, 5, 8, 32773, 32946)) return null

        val photometric = ifd.value(Tag.PHOTOMETRIC)?.first() ?: 1
        if (photometric !in intArrayOf(0, 1, 2, 3, 5)) return null

        val planar = ifd.value(Tag.PLANAR)?.first() ?: 1
        if (planar != 1) return null // seule la disposition entrelacée est portée

        val predictor = ifd.value(Tag.PREDICTOR)?.firstOrNull() ?: 1
        if (predictor != 1 && predictor != 2) return null

        val stripOffsets = ifd.value(Tag.STRIP_OFFSETS) ?: return null
        val stripCounts = ifd.value(Tag.STRIP_BYTE_COUNTS) ?: return null
        val rowsPerStrip = ifd.value(Tag.ROWS_PER_STRIP)?.firstOrNull()?.takeIf { it > 0 } ?: height

        val colorMap = ifd.value(Tag.COLOR_MAP)
        if (photometric == 3 && colorMap == null) return null

        // Les alpha supplémentaires : un RVB à quatre échantillons porte un alpha.
        val extraSamples = ifd.value(Tag.EXTRA_SAMPLES)?.firstOrNull() ?: 0
        val hasAlpha = samplesPerPixel == 4 && extraSamples != 0 && photometric == 2
        val cmyk = photometric == 5

        val bytesPerRow = ((width.toLong() * samplesPerPixel * bitsPerSample) + 7) / 8
        if (bytesPerRow > Int.MAX_VALUE / 2) return null
        val maxRowBytes = bytesPerRow * rowsPerStrip
        if (maxRowBytes > maxBytes.toLong()) return null // une bande plus lourde que le budget : refus

        val out = IntArray(outWidth * outHeight)
        var strip = 0
        val stripSize = maxOf(1, (height + rowsPerStrip - 1) / rowsPerStrip)
        var y = 0
        while (y < height && strip < stripOffsets.size && strip < stripCounts.size && strip < stripSize) {
            val rows = minOf(rowsPerStrip, height - y)
            val offset = stripOffsets[strip]
            val count = stripCounts[strip]
            if (offset < 0 || count < 0 || offset.toLong() + count.toLong() > bytes.size.toLong()) return null
            val raw = bytes.copyOfRange(offset, offset + count)
            val rowBytes = bytesPerRow.toInt()
            val expected = rowBytes * rows
            val data = when (compression) {
                1 -> raw.takeIf { it.size >= expected } ?: return null
                32773 -> PackBits.decode(raw, expected) ?: return null
                5 -> Lzw.decode(raw, expected) ?: return null
                else -> inflate(raw, expected) ?: return null
            }
            if (data.size < expected) return null
            if (predictor == 2) unPredict(data, expected, rowBytes, rows, bitsPerSample, samplesPerPixel)

            for (r in 0 until rows) {
                val srcY = y + r
                val outY = srcY / step
                if (srcY % step != 0) continue
                val rowBase = r * rowBytes
                var x = 0
                while (x < width) {
                    val outX = x / step
                    if (x % step == 0) {
                        val argb = sampleAt(data, rowBase, x, bitsPerSample, samplesPerPixel, photometric, colorMap, cmyk, hasAlpha)
                        if (argb != null) out[outY * outWidth + outX] = argb
                    }
                    x++
                }
            }
            y += rows
            strip++
        }
        if (y < height) return null // toutes les bandes ne tenaient pas : pas d'image à moitié
        Decoded(outWidth, outHeight, out)
    }.getOrNull()

    // ---- la lecture d'un pixel -------------------------------------------------------------------

    /** L'échantillon [index] d'une ligne, en 0..65535. */
    private fun sampleRaw(data: ByteArray, rowBase: Int, index: Int, bits: Int): Int {
        return when (bits) {
            8 -> data[rowBase + index].toInt() and 0xFF
            16 -> {
                val p = rowBase + index * 2
                ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
            }
            4 -> {
                val byteIndex = rowBase + index / 2
                val b = data[byteIndex].toInt() and 0xFF
                if (index % 2 == 0) (b shr 4) and 0x0F else b and 0x0F
            }
            else -> { // 1
                val byteIndex = rowBase + index / 8
                val b = data[byteIndex].toInt() and 0xFF
                val bit = 7 - (index % 8)
                (b shr bit) and 0x01
            }
        }
    }

    private fun scaleTo8(value: Int, bits: Int): Int = when (bits) {
        8 -> value
        16 -> value shr 8
        4 -> value * 17 // 0..15 → 0..255
        1 -> value * 255
        else -> value
    }

    private fun sampleAt(
        data: ByteArray,
        rowBase: Int,
        x: Int,
        bits: Int,
        samplesPerPixel: Int,
        photometric: Int,
        colorMap: IntArray?,
        cmyk: Boolean,
        hasAlpha: Boolean,
    ): Int? {
        fun s(i: Int) = sampleRaw(data, rowBase, x * samplesPerPixel + i, bits)
        return when {
            cmyk -> {
                val c = s(0); val m = s(1); val yl = s(2); val k = s(3)
                val kk = 255 - scaleTo8(k, bits)
                val r = kk * (255 - scaleTo8(c, bits)) / 255
                val g = kk * (255 - scaleTo8(m, bits)) / 255
                val b = kk * (255 - scaleTo8(yl, bits)) / 255
                argb(0xFF, r, g, b)
            }

            photometric == 3 -> { // palette : index dans la table
                val index = sampleRaw(data, rowBase, x * samplesPerPixel, bits)
                val map = colorMap ?: return null
                val entries = map.size / 3
                if (entries <= 0 || index >= entries) return null
                val r = map[index].shr(8) and 0xFF
                val g = map[entries + index].shr(8) and 0xFF
                val b = map[2 * entries + index].shr(8) and 0xFF
                argb(0xFF, r, g, b)
            }

            photometric == 2 -> { // RVB (+ alpha)
                val r = scaleTo8(s(0), bits)
                val g = scaleTo8(s(1), bits)
                val b = scaleTo8(s(2), bits)
                val a = if (hasAlpha) scaleTo8(s(3), bits) else 255
                argb(a, r, g, b)
            }

            photometric == 1 -> { // noir sur blanc
                val v = scaleTo8(s(0), bits)
                argb(0xFF, v, v, v)
            }

            else -> { // 0 : blanc sur noir
                val v = 255 - scaleTo8(s(0), bits)
                argb(0xFF, v, v, v)
            }
        }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Dé-prédit une bande « horizontal differencing » (Predictor 2), échantillon par échantillon. */
    private fun unPredict(data: ByteArray, length: Int, rowBytes: Int, rows: Int, bits: Int, spp: Int) {
        if (bits != 8 && bits != 16) return
        val bps = bits / 8
        val stride = spp * bps
        for (r in 0 until rows) {
            val base = r * rowBytes
            var i = base + stride
            while (i + bps <= base + rowBytes && i + bps <= length) {
                val previous = readSample(data, i - stride, bps)
                val current = readSample(data, i, bps)
                val sum = (previous + current) and (if (bits == 8) 0xFF else 0xFFFF)
                writeSample(data, i, bps, sum)
                i += bps
            }
        }
    }

    private fun readSample(data: ByteArray, at: Int, bps: Int): Int =
        if (bps == 1) data[at].toInt() and 0xFF
        else ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

    private fun writeSample(data: ByteArray, at: Int, bps: Int, value: Int) {
        if (bps == 1) data[at] = value.toByte()
        else {
            data[at] = (value shr 8).toByte()
            data[at + 1] = value.toByte()
        }
    }

    private fun inflate(raw: ByteArray, expected: Int): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(raw)
        val out = ByteArray(expected)
        var written = 0
        while (written < expected && !inflater.finished()) {
            val n = inflater.inflate(out, written, expected - written)
            if (n == 0 && inflater.needsInput()) break
            written += n
        }
        inflater.end()
        if (written < expected) null else out
    }.getOrNull()

    // ---- les décompressions ----------------------------------------------------------------------

    /** PackBits (compression 32773) : la course des octets littéraux et des répétitions. */
    private object PackBits {
        fun decode(raw: ByteArray, expected: Int): ByteArray? {
            val out = ByteArray(expected)
            var i = 0
            var o = 0
            while (i < raw.size && o < expected) {
                val n = raw[i].toInt()
                i++
                when {
                    n in 0..127 -> {
                        val count = n + 1
                        if (i + count > raw.size || o + count > expected) return null
                        System.arraycopy(raw, i, out, o, count)
                        i += count; o += count
                    }
                    n in -127..-1 -> {
                        if (i >= raw.size) return null
                        val count = 1 - n
                        if (o + count > expected) return null
                        val value = raw[i]; i++
                        java.util.Arrays.fill(out, o, o + count, value)
                        o += count
                    }
                    else -> Unit // -128 : ignoré
                }
            }
            return if (o >= expected) out else null
        }
    }

    /**
     * LZW du TIFF (compression 5) : codes **octets de poids fort d'abord**, largeur 9 puis croissante
     * avec le « early change » (la largeur augmente un code avant la saturation), ClearCode 256,
     * EOI 257, premier code libre 258.
     */
    private object Lzw {
        fun decode(raw: ByteArray, expected: Int): ByteArray? {
            val out = ByteArray(expected)
            var o = 0
            var bitPos = 0
            var codeWidth = 9
            var next = 258
            val table = arrayOfNulls<ByteArray>(4096)
            for (i in 0 until 256) table[i] = byteArrayOf(i.toByte())
            var previous: ByteArray? = null

            fun readCode(): Int? {
                if (bitPos + codeWidth > raw.size * 8) return null
                var code = 0
                repeat(codeWidth) {
                    val byteIndex = bitPos / 8
                    val bit = 7 - (bitPos % 8)
                    code = (code shl 1) or ((raw[byteIndex].toInt() shr bit) and 1)
                    bitPos++
                }
                return code
            }

            while (true) {
                val code = readCode() ?: return null
                when {
                    code == 256 -> { codeWidth = 9; next = 258; previous = null }
                    code == 257 -> return if (o >= expected) out else null
                    else -> {
                        val entry: ByteArray = when {
                            code < next && table[code] != null -> table[code]!!
                            code == next && previous != null -> previous + previous[0]
                            else -> return null
                        }
                        if (o + entry.size > expected) return null
                        System.arraycopy(entry, 0, out, o, entry.size)
                        o += entry.size
                        if (previous != null && next < 4096) {
                            table[next] = previous + entry[0]
                            next++
                        }
                        previous = entry
                        // « early change » : la largeur augmente un code avant la saturation.
                        if (next + 1 >= (1 shl codeWidth) && codeWidth < 12) codeWidth++
                    }
                }
            }
        }
    }

    // ---- l'en-tête -------------------------------------------------------------------------------

    /** Un lecteur d'octets qui respecte l'ordre des octets annoncé dans l'en-tête. */
    private class Reader(private val bytes: ByteArray) {
        private val little: Boolean
        private val ifdOffset: Long

        init {
            if (bytes.size < 8) throw IllegalArgumentException("trop court")
            val b0 = bytes[0].toInt() and 0xFF
            little = when {
                b0 == 0x49 -> true
                b0 == 0x4D -> false
                else -> throw IllegalArgumentException("ordre inconnu")
            }
            val magic = u16(2)
            if (magic != 42) throw IllegalArgumentException("pas un TIFF")
            ifdOffset = u32(4)
        }

        fun ifd0(): Ifd? {
            if (ifdOffset <= 0 || ifdOffset + 2 > bytes.size) return null
            val count = u16(ifdOffset.toInt())
            val entries = LinkedHashMap<Int, Pending>()
            var at = ifdOffset.toInt() + 2
            for (i in 0 until count) {
                if (at + 12 > bytes.size) return null
                val tag = u16(at)
                val type = u16(at + 2)
                val n = u32(at + 4).toInt()
                val valueAt = at + 8
                entries[tag] = Pending(type, n, valueAt)
                at += 12
            }
            return Ifd(entries) { pending -> readValues(pending) }
        }

        private fun readValues(pending: Pending): IntArray? {
            val size = typeSize(pending.type) ?: return null
            val total = size * pending.count
            val dataAt: Int = if (total <= 4) pending.valueAt else u32(pending.valueAt).toInt()
            if (dataAt < 0 || dataAt.toLong() + total > bytes.size) return null
            val values = IntArray(pending.count)
            for (i in 0 until pending.count) {
                val o = dataAt + i * size
                values[i] = when (pending.type) {
                    1, 6, 7 -> bytes[o].toInt() and 0xFF
                    3, 8 -> u16(o)
                    4, 9 -> u32(o).toInt()
                    else -> return null
                }
            }
            return values
        }

        private fun typeSize(type: Int): Int? = when (type) {
            1, 2, 6, 7 -> 1
            3, 8 -> 2
            4, 9 -> 4
            else -> null // RATIONAL, FLOAT… : non portés
        }

        private fun u16(at: Int): Int {
            val a = bytes[at].toInt() and 0xFF
            val b = bytes[at + 1].toInt() and 0xFF
            return if (little) a or (b shl 8) else (a shl 8) or b
        }

        private fun u32(at: Int): Long {
            val a = bytes[at].toLong() and 0xFF
            val b = bytes[at + 1].toLong() and 0xFF
            val c = bytes[at + 2].toLong() and 0xFF
            val d = bytes[at + 3].toLong() and 0xFF
            return if (little) a or (b shl 8) or (c shl 16) or (d shl 24)
            else (a shl 24) or (b shl 16) or (c shl 8) or d
        }
    }

    private class Pending(val type: Int, val count: Int, val valueAt: Int)

    private class Ifd(
        private val entries: Map<Int, Pending>,
        private val read: (Pending) -> IntArray?,
    ) {
        private val cached = HashMap<Int, IntArray?>()

        /** Les valeurs d'un tag, ou `null` s'il est absent. */
        fun value(tag: Int): IntArray? {
            if (cached.containsKey(tag)) return cached[tag]
            val pending = entries[tag]
            val values = pending?.let(read)
            cached[tag] = values
            return values
        }
    }

    private object Tag {
        const val WIDTH = 256
        const val HEIGHT = 257
        const val BITS = 258
        const val COMPRESSION = 259
        const val PHOTOMETRIC = 262
        const val STRIP_OFFSETS = 273
        const val SAMPLES = 277
        const val ROWS_PER_STRIP = 278
        const val STRIP_BYTE_COUNTS = 279
        const val PLANAR = 284
        const val PREDICTOR = 317
        const val COLOR_MAP = 320
        const val EXTRA_SAMPLES = 338
        const val SAMPLE_FORMAT = 339
    }
}
