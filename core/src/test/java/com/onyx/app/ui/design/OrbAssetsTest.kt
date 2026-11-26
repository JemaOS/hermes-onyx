package com.onyx.app.ui.design

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les orbes doivent garder le détourage et les couleurs des SVG, pas un rectangle noir opaque. */
class OrbAssetsTest {
    private val names = listOf(
        "ic_glasswave", "ic_glasswaveblank",
        "ic_flatwave", "ic_flatwaveblank",
        "ic_goldwave", "ic_goldwaveblank",
        "ic_inkwave", "ic_inkwaveblank",
        "ic_mattewave", "ic_mattewaveblank",
        "ic_navyglasswave", "ic_navyglasswaveblank",
        "ic_navymattewave", "ic_navymattewaveblank",
        "ic_steelglasswave", "ic_steelglasswaveblank",
        "ic_bubblewave", "ic_bubblewaveblank",
        "ic_bubblemattewave", "ic_bubblemattewaveblank",
        "ic_waveform", "ic_waveformblank",
    )

    @Test
    fun `les 22 orbes rendus conservent transparence et couleurs`() {
        val directory = listOf(
            File("src/main/res/drawable"),
            File("core/src/main/res/drawable"),
        ).firstOrNull { it.isDirectory() } ?: error("Drawable resources not found")

        names.forEach { name ->
            val file = File(directory, "$name.png")
            assertTrue("$name doit être rendu depuis son SVG source", file.isFile())
            val image = readRgbaPng(file)
            assertEquals("$name largeur", 256, image.width)
            assertEquals("$name hauteur", 256, image.height)

            listOf(0 to 0, 255 to 0, 0 to 255, 255 to 255).forEach { (x, y) ->
                assertEquals("$name doit être transparent aux coins", 0, image.alpha(x, y))
            }
            val colours = buildSet {
                var y = 8
                while (y < 256) {
                    var x = 8
                    while (x < 256) {
                        if (image.alpha(x, y) > 128) add(image.rgb(x, y))
                        x += 8
                    }
                    y += 8
                }
            }
            assertTrue("$name doit garder les couleurs du SVG (${colours.size} couleurs)", colours.size > 1)
        }
    }
}

/** Petit lecteur PNG RGBA non entrelacé, autonome dans les tests JVM Android. */
private class RgbaPng(val width: Int, val height: Int, private val pixels: ByteArray) {
    private fun offset(x: Int, y: Int) = (y * width + x) * 4
    fun alpha(x: Int, y: Int) = pixels[offset(x, y) + 3].toInt() and 0xFF
    fun rgb(x: Int, y: Int): Int {
        val at = offset(x, y)
        return ((pixels[at].toInt() and 0xFF) shl 16) or
            ((pixels[at + 1].toInt() and 0xFF) shl 8) or
            (pixels[at + 2].toInt() and 0xFF)
    }
}

private fun readRgbaPng(file: File): RgbaPng {
    val bytes = file.readBytes()
    val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    require(bytes.take(8).toByteArray().contentEquals(signature)) { "${file.name}: signature PNG" }
    var width = 0
    var height = 0
    var at = 8
    val compressed = ByteArrayOutputStream()
    while (at + 12 <= bytes.size) {
        val length = bytes.intAt(at)
        val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
        val data = at + 8
        when (type) {
            "IHDR" -> {
                width = bytes.intAt(data)
                height = bytes.intAt(data + 4)
                require(bytes[data + 8].toInt() == 8 && bytes[data + 9].toInt() == 6) {
                    "${file.name}: PNG RGBA 8 bits attendu"
                }
                require(bytes[data + 12].toInt() == 0) { "${file.name}: PNG non entrelacé attendu" }
            }
            "IDAT" -> compressed.write(bytes, data, length)
            "IEND" -> break
        }
        at = data + length + 4
    }
    require(width > 0 && height > 0)
    val inflated = ByteArray(height * (1 + width * 4))
    val inflater = Inflater()
    inflater.setInput(compressed.toByteArray())
    var written = 0
    while (written < inflated.size) {
        val count = inflater.inflate(inflated, written, inflated.size - written)
        require(count > 0) { "${file.name}: données PNG tronquées" }
        written += count
    }
    inflater.end()

    val stride = width * 4
    val pixels = ByteArray(width * height * 4)
    for (y in 0 until height) {
        val filter = inflated[y * (stride + 1)].toInt() and 0xFF
        for (x in 0 until stride) {
            val source = y * (stride + 1) + 1 + x
            val target = y * stride + x
            val raw = inflated[source].toInt() and 0xFF
            val left = if (x >= 4) pixels[target - 4].toInt() and 0xFF else 0
            val up = if (y > 0) pixels[target - stride].toInt() and 0xFF else 0
            val upLeft = if (y > 0 && x >= 4) pixels[target - stride - 4].toInt() and 0xFF else 0
            val predictor = when (filter) {
                0 -> 0
                1 -> left
                2 -> up
                3 -> (left + up) / 2
                4 -> paeth(left, up, upLeft)
                else -> error("${file.name}: filtre PNG $filter")
            }
            pixels[target] = ((raw + predictor) and 0xFF).toByte()
        }
    }
    return RgbaPng(width, height, pixels)
}

private fun ByteArray.intAt(at: Int) =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = kotlin.math.abs(p - a)
    val pb = kotlin.math.abs(p - b)
    val pc = kotlin.math.abs(p - c)
    return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
}
