package com.onyx.app.data.image

import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.max

/**
 * Une image TIFF décodée : [width] × [height] pixels ARGB, et le tag `Orientation` du fichier
 * (1–8, tel qu'EXIF le numérote).
 *
 * Le décodeur vit ici, dans le module de base, et **non** côté Android : la politique de décodage
 * borné de l'app ([BoundedImage]) est pure et éprouvée hors appareil, et le décodage des octets
 * l'est aussi. Seule la conversion finale en `Bitmap` reste côté app (`BoundedImages`).
 */
class TiffRaster(
    val width: Int,
    val height: Int,
    val argb: IntArray,
    val orientation: Int,
)

/**
 * Vrai quand [bytes] commence par un en-tête TIFF classique, petit-boutiste (`II*\0`) ou
 * gros-boutiste (`MM\0*`).
 *
 * C'est le **seul** test d'appartenance dont l'app a besoin : `BitmapFactory` ne sait pas lire un
 * TIFF, donc la signature décide à elle seule qui décode.
 */
fun isTiff(bytes: ByteArray): Boolean = bytes.size >= 4 && (
    bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 42.toByte() && bytes[3] == 0.toByte() ||
        bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() &&
        bytes[2] == 0.toByte() && bytes[3] == 42.toByte()
    )

/**
 * La première image d'un TIFF de base — celui que les décodeurs d'Android ne savent pas lire.
 *
 * Sont couverts : les bandes seules (`strips`, jamais les tuiles), sans compression, PackBits, LZW
 * ou Deflate (avec ou sans le prédicteur horizontal) ; le gris 8 bits, le gris + alpha, le RVB et
 * le RVBA ; le gris 1, 2, 4 et 8 bits et le palette. **Tout le reste, ou un fichier abîmé, rend
 * `null`** — jamais une exception.
 *
 * Les octets viennent d'une réponse d'agent : rien dans le fichier n'est cru sur parole. Une image
 * au-delà de [maxPixels] est refusée **depuis son en-tête**, avant toute allocation, chaque lecture
 * est bornée, et une bande est lue ligne par ligne, si bien qu'une bande compressée qui prétend
 * contenir plus de lignes que les siennes ne peut pas grandir. Le résultat est réduit par moyenne
 * de blocs pour que son plus grand côté tienne dans [maxEdge].
 *
 * @param maxEdge le plus grand côté accepté pour le résultat (le sous-échantillonnage vient d'ici).
 * @param maxPixels le plafond de pixels de la **source**, lu dans l'en-tête avant toute allocation.
 */
fun decodeTiff(bytes: ByteArray, maxEdge: Int, maxPixels: Long = MAX_TIFF_PIXELS): TiffRaster? {
    if (!isTiff(bytes) || maxEdge <= 0) return null
    return try {
        TiffReader(bytes).decode(maxEdge, maxPixels)
    } catch (malformed: Malformed) {
        null
    }
}

/** Plus grand que n'importe quelle photo de téléphone ; à 4 octets par pixel, encore tenable. */
const val MAX_TIFF_PIXELS = 40_000_000L

/** Levée pour tout fichier abîmé ; on ne regarde jamais sa pile d'appels. */
private class Malformed : Exception() {
    override fun fillInStackTrace(): Throwable = this
}

private fun bad(): Nothing = throw Malformed()

private class TiffReader(private val data: ByteArray) {
    private val bigEndian = data[0] == 'M'.code.toByte()
    private val entries = HashMap<Int, Entry>()

    /** Une entrée d'annuaire : son [type], [count] valeurs, et où commence la première. */
    private class Entry(val type: Int, val count: Long, val at: Long)

    fun decode(maxEdge: Int, maxPixels: Long): TiffRaster {
        readDirectory(u32(4))
        val width = number(WIDTH) ?: bad()
        val height = number(HEIGHT) ?: bad()
        // Chaque côté est vérifié d'abord : deux côtés de 32 bits peuvent dépasser un Long une fois multipliés.
        if (width <= 0 || height <= 0 || width > maxPixels || height > maxPixels || width * height > maxPixels) bad()
        val w = width.toInt()
        val h = height.toInt()

        val samples = (number(SAMPLES_PER_PIXEL) ?: 1).toInt()
        if (samples !in 1..MAX_SAMPLES) bad()
        val depth = entries[BITS_PER_SAMPLE]?.let { bits ->
            if (bits.count < samples) bad()
            val first = value(bits, 0)
            for (i in 1 until samples) if (value(bits, i) != first) bad()
            first.toInt()
        } ?: 1
        if ((number(PLANAR) ?: 1) != 1L || (number(FILL_ORDER) ?: 1) != 1L || TILE_WIDTH in entries) bad()
        if ((number(SAMPLE_FORMAT) ?: 1) != 1L) bad()
        val predictor = number(PREDICTOR) ?: 1
        if (predictor != 1L && !(predictor == 2L && depth == 8)) bad()

        val photometric = number(PHOTOMETRIC) ?: bad()
        val pixels = Pixels.of(
            photometric,
            samples,
            depth,
            extraAlpha(),
            if (photometric == PALETTE) palette(depth) else null,
        )
        val rowBytes = (w.toLong() * samples * depth + 7) / 8
        if (rowBytes > MAX_ROW_BYTES) bad()
        val compression = (number(COMPRESSION) ?: 1).toInt()
        if (compression !in COMPRESSIONS) bad()

        val rowsPerStrip = (number(ROWS_PER_STRIP) ?: height).coerceAtMost(height)
        if (rowsPerStrip <= 0) bad()
        val strips = ((height + rowsPerStrip - 1) / rowsPerStrip).toInt()
        // Chaque bande coûte un peu à elle seule ; un million de bandes d'un octet prendrait des minutes.
        if (strips > MAX_STRIPS) bad()
        val offsets = entries[STRIP_OFFSETS] ?: bad()
        val counts = entries[STRIP_BYTE_COUNTS]
        if (offsets.count < strips || counts != null && counts.count < strips) bad()
        if (counts == null && compression != NONE) bad()

        val out = Downscaler(w, h, maxEdge)
        val row = ByteArray(rowBytes.toInt())
        // Un seul lecteur pour toute l'image, relancé à chaque bande.
        val strip = when (compression) {
            NONE -> RawStrip(data)
            PACKBITS -> PackBitsStrip(data)
            LZW -> LzwStrip(data)
            else -> DeflateStrip(data)
        }
        try {
            for (s in 0 until strips) {
                val rows = minOf(rowsPerStrip, height - s * rowsPerStrip).toInt()
                val start = value(offsets, s)
                val length = counts?.let { value(it, s) } ?: (rowBytes * rows)
                if (start < 0 || length < 0 || start + length > data.size) bad()
                strip.start(start.toInt(), length.toInt())
                repeat(rows) {
                    if (!strip.fill(row)) bad()
                    if (predictor == 2L) {
                        for (i in samples until row.size) row[i] = (row[i] + row[i - samples]).toByte()
                    }
                    out.addRow { x -> pixels.argb(row, x) }
                }
            }
        } finally {
            strip.close()
        }
        val orientation = (number(ORIENTATION) ?: 1).toInt().takeIf { it in 1..8 } ?: 1
        return TiffRaster(out.width, out.height, out.pixels, orientation)
    }

    /** L'échantillon supplémentaire après les couleurs est-il l'alpha, et est-il prémultiplié ? */
    private fun extraAlpha(): Alpha {
        val extra = entries[EXTRA_SAMPLES] ?: return Alpha.None
        if (extra.count < 1) return Alpha.None
        return when (value(extra, 0)) {
            1L -> Alpha.Premultiplied
            2L -> Alpha.Straight
            else -> Alpha.None
        }
    }

    /** La table de couleurs en ARGB, pour une image palette de [depth] bits ; `null` sans table. */
    private fun palette(depth: Int): IntArray? {
        val map = entries[COLOR_MAP] ?: return null
        if (depth !in 1..8 || map.type != SHORT) bad()
        val size = 1 shl depth
        if (map.count != 3L * size) bad()
        return IntArray(size) { i ->
            val r = value(map, i).toInt() ushr 8
            val g = value(map, size + i).toInt() ushr 8
            val b = value(map, 2 * size + i).toInt() ushr 8
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun readDirectory(at: Long) {
        if (at < 8) bad()
        val count = u16(at)
        for (i in 0 until count) {
            val entry = at + 2 + 12L * i
            val type = u16(entry + 2)
            val n = u32(entry + 4)
            val size = n * typeSize(type)
            entries[u16(entry)] = Entry(type, n, if (size in 0..4) entry + 8 else u32(entry + 8))
        }
    }

    /** La valeur unique de [tag], si le fichier la porte. */
    private fun number(tag: Int): Long? = entries[tag]?.let { if (it.count < 1) bad() else value(it, 0) }

    private fun value(entry: Entry, index: Int): Long {
        if (index >= entry.count) bad()
        return when (entry.type) {
            BYTE -> u8(entry.at + index).toLong()
            SHORT -> u16(entry.at + 2L * index).toLong()
            LONG -> u32(entry.at + 4L * index)
            else -> bad()
        }
    }

    private fun typeSize(type: Int): Long = when (type) {
        BYTE, ASCII, SBYTE, UNDEFINED -> 1
        SHORT, SSHORT -> 2
        LONG, SLONG, FLOAT -> 4
        RATIONAL, SRATIONAL, DOUBLE -> 8
        else -> 0
    }

    private fun u8(at: Long): Int {
        if (at < 0 || at >= data.size) bad()
        return data[at.toInt()].toInt() and 0xFF
    }

    private fun u16(at: Long): Int =
        if (bigEndian) (u8(at) shl 8) or u8(at + 1) else u8(at) or (u8(at + 1) shl 8)

    private fun u32(at: Long): Long =
        if (bigEndian) (u16(at).toLong() shl 16) or u16(at + 2).toLong() else u16(at).toLong() or (u16(at + 2).toLong() shl 16)
}

private enum class Alpha { None, Straight, Premultiplied }

/** Transforme les échantillons d'une ligne décodée en ARGB. */
private sealed class Pixels {
    abstract fun argb(row: ByteArray, x: Int): Int

    /** Gris de 1 à 8 bits, blanc ou noir selon le zéro, suivi d'un alpha 8 bits quand [alpha]. */
    class Gray(
        private val depth: Int,
        private val whiteIsZero: Boolean,
        private val samples: Int,
        private val alpha: Alpha,
    ) : Pixels() {
        private val top = (1 shl depth) - 1

        override fun argb(row: ByteArray, x: Int): Int {
            var v = if (depth == 8) row[x * samples].toInt() and 0xFF else bits(row, x, depth) * 255 / top
            if (whiteIsZero) v = 255 - v
            val a = if (alpha != Alpha.None) row[x * samples + 1].toInt() and 0xFF else 0xFF
            if (alpha == Alpha.Premultiplied) v = unpremultiply(v, a)
            return (a shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    class Palette(private val depth: Int, private val colors: IntArray) : Pixels() {
        override fun argb(row: ByteArray, x: Int): Int = colors[bits(row, x, depth)]
    }

    class Rgb(private val samples: Int, private val alpha: Alpha) : Pixels() {
        override fun argb(row: ByteArray, x: Int): Int {
            val i = x * samples
            var r = row[i].toInt() and 0xFF
            var g = row[i + 1].toInt() and 0xFF
            var b = row[i + 2].toInt() and 0xFF
            val a = if (alpha != Alpha.None) row[i + 3].toInt() and 0xFF else 0xFF
            if (alpha == Alpha.Premultiplied) {
                r = unpremultiply(r, a)
                g = unpremultiply(g, a)
                b = unpremultiply(b, a)
            }
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    companion object {
        fun of(photometric: Long, samples: Int, depth: Int, alpha: Alpha, palette: IntArray?): Pixels = when (photometric) {
            WHITE_IS_ZERO, BLACK_IS_ZERO -> when {
                samples == 1 && depth in GRAY_DEPTHS -> Gray(depth, photometric == WHITE_IS_ZERO, 1, Alpha.None)
                samples >= 2 && depth == 8 -> Gray(8, photometric == WHITE_IS_ZERO, samples, alpha)
                else -> bad()
            }
            RGB -> if (samples >= 3 && depth == 8) Rgb(samples, if (samples >= 4) alpha else Alpha.None) else bad()
            PALETTE -> if (samples == 1 && depth in GRAY_DEPTHS && palette != null) Palette(depth, palette) else bad()
            else -> bad()
        }

        /** L'échantillon [x] d'une ligne empaquetée à [depth] bits, premier pixel dans les bits hauts. */
        private fun bits(row: ByteArray, x: Int, depth: Int): Int {
            val bit = x * depth
            val byte = row[bit ushr 3].toInt() and 0xFF
            return (byte ushr (8 - depth - (bit and 7))) and ((1 shl depth) - 1)
        }

        private fun unpremultiply(c: Int, a: Int): Int = if (a == 0) 0 else minOf(255, c * 255 / a)
    }
}

/**
 * Réduit par moyenne de blocs les lignes d'une image [sourceWidth] × [sourceHeight] en blocs
 * `step` × `step`, pour que le plus grand côté tienne dans [maxEdge]. Une seule ligne de sommes
 * est gardée pendant que les lignes arrivent.
 */
private class Downscaler(private val sourceWidth: Int, private val sourceHeight: Int, maxEdge: Int) {
    private val step = (max(sourceWidth, sourceHeight) + maxEdge - 1) / maxEdge
    val width = (sourceWidth + step - 1) / step
    val height = (sourceHeight + step - 1) / step
    val pixels = IntArray(width * height)
    private val sums = LongArray(if (step == 1) 0 else width * 4)
    private var y = 0

    inline fun addRow(pixel: (Int) -> Int) {
        if (step == 1) {
            val base = y * width
            for (x in 0 until width) pixels[base + x] = pixel(x)
        } else {
            // Les couleurs sont pondérées par leur alpha, pour qu'un pixel transparent ne teinte pas le bord de ce qui l'entoure.
            for (x in 0 until sourceWidth) {
                val p = pixel(x)
                val a = (p ushr 24).toLong()
                val i = (x / step) * 4
                sums[i] += a
                sums[i + 1] += (p ushr 16 and 0xFF) * a
                sums[i + 2] += (p ushr 8 and 0xFF) * a
                sums[i + 3] += (p and 0xFF) * a
            }
            val rows = y % step + 1
            if (rows == step || y == sourceHeight - 1) flush(rows)
        }
        y++
    }

    private fun flush(rows: Int) {
        val base = (y / step) * width
        for (x in 0 until width) {
            val n = minOf(step, sourceWidth - x * step).toLong() * rows
            val i = x * 4
            val alpha = sums[i]
            pixels[base + x] = if (alpha == 0L) {
                0
            } else {
                ((alpha / n).toInt() shl 24) or ((sums[i + 1] / alpha).toInt() shl 16) or
                    ((sums[i + 2] / alpha).toInt() shl 8) or (sums[i + 3] / alpha).toInt()
            }
        }
        sums.fill(0)
    }
}

/** Remplit une ligne à la fois depuis la bande sur laquelle elle vient d'être [start]ée ; faux dès qu'elle s'épuise. */
private abstract class StripReader(protected val data: ByteArray) {
    protected var at = 0
    protected var end = 0

    open fun start(offset: Int, length: Int) {
        at = offset
        end = offset + length
    }

    abstract fun fill(row: ByteArray): Boolean
    open fun close() {}
}

private class RawStrip(data: ByteArray) : StripReader(data) {
    override fun fill(row: ByteArray): Boolean {
        if (end - at < row.size) return false
        System.arraycopy(data, at, row, 0, row.size)
        at += row.size
        return true
    }
}

/** PackBits (compression 32773) : une série peut se poursuivre sur la ligne suivante. */
private class PackBitsStrip(data: ByteArray) : StripReader(data) {
    private var literal = 0
    private var repeat = 0
    private var repeated: Byte = 0

    override fun start(offset: Int, length: Int) {
        super.start(offset, length)
        literal = 0
        repeat = 0
    }

    override fun fill(row: ByteArray): Boolean {
        var n = 0
        while (n < row.size) {
            when {
                literal > 0 -> {
                    val take = minOf(literal, row.size - n)
                    if (end - at < take) return false
                    System.arraycopy(data, at, row, n, take)
                    at += take
                    n += take
                    literal -= take
                }
                repeat > 0 -> {
                    val take = minOf(repeat, row.size - n)
                    row.fill(repeated, n, n + take)
                    n += take
                    repeat -= take
                }
                else -> {
                    if (at >= end) return false
                    val header = data[at++].toInt()
                    if (header >= 0) {
                        literal = header + 1
                    } else if (header != -128) {
                        if (at >= end) return false
                        repeated = data[at++]
                        repeat = 1 - header
                    }
                }
            }
        }
        return true
    }
}

/** Le LZW de TIFF (compression 5) : des codes de 9 à 12 bits, bit haut d'abord, élargis un code tôt. */
private class LzwStrip(data: ByteArray) : StripReader(data) {
    private var bit = 0L
    private var bitEnd = 0L
    private val prefix = IntArray(TABLE)
    private val suffix = ByteArray(TABLE)
    private val first = ByteArray(TABLE)
    private val lengths = IntArray(TABLE)
    private var next = FIRST_CODE
    private var width = 9
    private var previous = -1
    private var ended = false

    // La chaîne du code courant, écrite à l'envers ; il reste [pending] octets à recopier.
    private val string = ByteArray(TABLE)
    private var pending = 0
    private var stringStart = 0

    init {
        // Les 256 premiers codes ne changent jamais ; seuls les codes à partir de FIRST_CODE sont écrits.
        for (i in 0 until 256) {
            suffix[i] = i.toByte()
            first[i] = i.toByte()
            lengths[i] = 1
        }
    }

    override fun start(offset: Int, length: Int) {
        super.start(offset, length)
        bit = offset.toLong() * 8
        bitEnd = end.toLong() * 8
        next = FIRST_CODE
        width = 9
        previous = -1
        ended = false
        pending = 0
    }

    override fun fill(row: ByteArray): Boolean {
        var n = 0
        while (n < row.size) {
            if (pending > 0) {
                val take = minOf(pending, row.size - n)
                System.arraycopy(string, stringStart, row, n, take)
                stringStart += take
                pending -= take
                n += take
                continue
            }
            if (ended) return false
            val code = readCode()
            when {
                code < 0 || code == END -> ended = true
                code == CLEAR -> {
                    next = FIRST_CODE
                    width = 9
                    previous = -1
                }
                previous < 0 -> {
                    if (code > 255) bad()
                    emit(code)
                    previous = code
                }
                code < next -> {
                    add(previous, first[code])
                    emit(code)
                    previous = code
                }
                code == next -> {
                    add(previous, first[previous])
                    emit(code)
                    previous = code
                }
                else -> bad()
            }
        }
        return true
    }

    private fun readCode(): Int {
        if (bit + width > bitEnd) return -1
        // Un code d'au plus 12 bits, commençant n'importe où dans un octet, tient dans 3 octets ; au-delà de la bande, on lit 0.
        val i = (bit ushr 3).toInt()
        val window = (byteAt(i) shl 16) or (byteAt(i + 1) shl 8) or byteAt(i + 2)
        val code = (window ushr (24 - (bit and 7).toInt() - width)) and ((1 shl width) - 1)
        bit += width
        return code
    }

    private fun byteAt(i: Int): Int = if (i < end) data[i].toInt() and 0xFF else 0

    private fun add(prefixCode: Int, last: Byte) {
        // Une table pleine sans Clear garde ses codes, comme le fait la bibliothèque de référence.
        if (next >= TABLE) return
        prefix[next] = prefixCode
        suffix[next] = last
        first[next] = first[prefixCode]
        lengths[next] = lengths[prefixCode] + 1
        next++
        if (next >= (1 shl width) - 1 && width < 12) width++
    }

    private fun emit(code: Int) {
        val length = lengths[code]
        var c = code
        for (i in length - 1 downTo 0) {
            string[i] = suffix[c]
            c = prefix[c]
        }
        stringStart = 0
        pending = length
    }

    private companion object {
        const val TABLE = 4096
        const val CLEAR = 256
        const val END = 257
        const val FIRST_CODE = 258
    }
}

/** Deflate (compression 8, ou 32946 chez les anciens écrivains), gonflé une ligne à la fois. */
private class DeflateStrip(data: ByteArray) : StripReader(data) {
    private val inflater = Inflater()

    override fun start(offset: Int, length: Int) {
        inflater.reset()
        inflater.setInput(data, offset, length)
    }

    override fun fill(row: ByteArray): Boolean {
        var n = 0
        try {
            while (n < row.size) {
                val read = inflater.inflate(row, n, row.size - n)
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) return false
                n += read
            }
        } catch (e: DataFormatException) {
            return false
        }
        return true
    }

    override fun close() = inflater.end()
}

private const val BYTE = 1
private const val ASCII = 2
private const val SHORT = 3
private const val LONG = 4
private const val RATIONAL = 5
private const val SBYTE = 6
private const val UNDEFINED = 7
private const val SSHORT = 8
private const val SLONG = 9
private const val SRATIONAL = 10
private const val FLOAT = 11
private const val DOUBLE = 12

private const val WIDTH = 256
private const val HEIGHT = 257
private const val BITS_PER_SAMPLE = 258
private const val COMPRESSION = 259
private const val PHOTOMETRIC = 262
private const val FILL_ORDER = 266
private const val STRIP_OFFSETS = 273
private const val ORIENTATION = 274
private const val SAMPLES_PER_PIXEL = 277
private const val ROWS_PER_STRIP = 278
private const val STRIP_BYTE_COUNTS = 279
private const val PLANAR = 284
private const val PREDICTOR = 317
private const val COLOR_MAP = 320
private const val TILE_WIDTH = 322
private const val EXTRA_SAMPLES = 338
private const val SAMPLE_FORMAT = 339

private const val NONE = 1
private const val LZW = 5
private const val DEFLATE = 8
private const val ADOBE_DEFLATE = 32946
private const val PACKBITS = 32773
private val COMPRESSIONS = setOf(NONE, LZW, DEFLATE, ADOBE_DEFLATE, PACKBITS)

private const val WHITE_IS_ZERO = 0L
private const val BLACK_IS_ZERO = 1L
private const val RGB = 2L
private const val PALETTE = 3L
private val GRAY_DEPTHS = setOf(1, 2, 4, 8)
private const val MAX_SAMPLES = 8

/** Une bande par ligne d'une image d'un million de lignes ; au-delà, le fichier n'est fait que pour être lent. */
private const val MAX_STRIPS = 1 shl 20

/** Les échantillons d'une seule ligne, la seule partie d'une bande gardée : 2 millions de pixels de large à 8 échantillons. */
private const val MAX_ROW_BYTES = 16L * 1024 * 1024
