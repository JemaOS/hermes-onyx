package com.onyx.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import com.onyx.app.data.image.BoundedImage
import com.onyx.app.data.image.ImageFormat
import com.onyx.app.data.image.ImageFormats
import com.onyx.app.data.image.decodeTiff
import com.onyx.app.data.image.isTiff
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only path by which an image enters memory in the app.
 *
 * Each call goes through two steps: we first read **the header alone** (`inJustDecodeBounds`)
 * to know the dimensions, we ask [BoundedImage] for the downscale factor, then we
 * decode once with that factor. The full-resolution image is therefore never built.
 *
 * Three rules, and an image that does not satisfy them becomes `null` — never an exception:
 * 1. the plan comes from [BoundedImage.plan], bounded in pixels **and** in bytes;
 * 2. the resulting `Bitmap` is re-checked ([BoundedImage.canDraw]): a file can announce one set of
 *    dimensions and deliver another;
 * 3. everything that touches the disk, a `Uri` or the decoder is under `runCatching`.
 *
 * A `null` is a normal state: the caller then shows an error card or its symbol.
 */
object BoundedImages {

    /** The longest side of a feed thumbnail (64 dp at 3× density). */
    const val THUMB_PX = 192

    /** The longest side of a row in the Files tab (46 dp at 3×). */
    const val ROW_PX = 160

    /** The longest side of a delivered file preview. */
    const val PREVIEW_PX = 512

    /** The longest side of the image viewer: beyond it, the screen shows nothing more. */
    const val VIEWER_PX = 2048

    /**
     * The viewer's budget: wider than a thumbnail's (you zoom up to 2.5×), but
     * always under the render engine's drawing ceiling.
     */
    const val VIEWER_BYTES = 24 * 1024 * 1024

    /**
     * The largest TIFF we agree to load in one go.
     *
     * The house decoder works on whole bytes (the format offers no partial reading
     * like `BitmapFactory`), so the bound is set **before** reading: beyond it, we
     * return `null` rather than loading an arbitrarily large file into memory.
     */
    const val MAX_TIFF_BYTES = 64L * 1024 * 1024

    /**
     * What we agree to read **in full** for the formats that require it (the house TIFF,
     * and the system `ImageDecoder` fallback for HEIC / AVIF that a given version refuses).
     */
    private const val MAX_READ_BYTES = 96 * 1024 * 1024

    /** Decodes a file from the phone, bounded. */
    fun decodeFile(
        file: File,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        if (!file.exists() || file.length() <= 0L) return null
        // A TIFF does not go through `BitmapFactory`: the signature decides, and the house
        // decoder handles it, with its own bounds (pixel ceiling read from the header, then
        // reduction by averaging down to the requested edge).
        if (fileLooksLikeTiff(file)) return decodeTiffFile(file, maxPx, maxBytes)
        val format = sniffFile(file)
        val plan = planOfFile(file, maxPx, maxBytes) ?: return null
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        accept(bitmap, maxBytes, plan)?.let { return it }

        // HEIC / AVIF that `BitmapFactory` cannot read on this version: the system decoder.
        if (format == ImageFormat.HEIF || format == ImageFormat.AVIF) {
            return decodeWithImageDecoder({ file.inputStream() }, maxPx, maxBytes)
        }
        null
    }.getOrNull()

    /** Decodes a stream reopened at each step (a `content://` is re-read, it cannot be rewound). */
    fun decodeStream(
        open: () -> InputStream?,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        // The signature is read on the first bytes; the stream is then reopened for the rest.
        // A TIFF is read in one go (bounded); the other formats go back through the header alone.
        val format = sniffStream(open)
        if (format == ImageFormat.TIFF || streamLooksLikeTiff(open)) {
            val bytes = readBounded(open) ?: return null
            return tiffToBitmap(bytes, maxPx, maxBytes)
        }
        val plan = planOfStream(open, maxPx, maxBytes) ?: return null
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) }
        accept(bitmap, maxBytes, plan)?.let { return it }

        // HEIC / AVIF that `BitmapFactory` cannot read on this version: the system decoder.
        if (format == ImageFormat.HEIF || format == ImageFormat.AVIF) {
            return decodeWithImageDecoder(open, maxPx, maxBytes)
        }
        null
    }.getOrNull()

    /** Decodes bytes already in memory (a profile photo, a server response), bounded. */
    fun decodeBytes(
        bytes: ByteArray,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        if (bytes.isEmpty()) return null
        if (isTiff(bytes)) return tiffToBitmap(bytes, maxPx, maxBytes)
        decodeStream({ bytes.inputStream() }, maxPx, maxBytes)
    }.getOrNull()

    /** Decodes the image of a phone `Uri` (gallery, picker, share sheet), bounded. */
    fun decodeUri(
        context: Context,
        uri: Uri,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        val resolver = context.contentResolver
        decodeStream({ resolver.openInputStream(uri) }, maxPx, maxBytes)
    }.getOrNull()

    /**
     * Decodes off the main thread: a gallery image can be heavy, and a remote `Uri`
     * reads slowly. Cancellation of a screen that goes away is not an error, it propagates.
     */
    suspend fun decodeUriOffMain(
        context: Context,
        uri: Uri,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = try {
        withContext(Dispatchers.IO) { decodeUri(context, uri, maxPx, maxBytes) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    // -----------------------------------------------------------------------------------------
    // TIFF: the only image format the system does not decode
    // -----------------------------------------------------------------------------------------

    /** True when the file starts with a TIFF signature. */
    private fun fileLooksLikeTiff(file: File): Boolean = runCatching {
        if (file.length() > MAX_TIFF_BYTES) return false
        file.inputStream().use { stream -> isTiff(stream.readHead()) }
    }.getOrDefault(false)

    /** True when the stream starts with a TIFF signature (the stream is reopened by the caller). */
    private fun streamLooksLikeTiff(open: () -> InputStream?): Boolean = runCatching {
        open()?.use { stream -> isTiff(stream.readHead()) } == true
    }.getOrDefault(false)

    /** The first four bytes of a stream: enough to read a signature, and nothing more. */
    private fun InputStream.readHead(): ByteArray {
        val head = ByteArray(4)
        var read = 0
        while (read < head.size) {
            val n = read(head, read, head.size - read)
            if (n <= 0) break
            read += n
        }
        return if (read == head.size) head else head.copyOf(read)
    }

    /**
     * The content of a stream, refused beyond [MAX_TIFF_BYTES].
     *
     * We read in blocks and stop **as soon as** the ceiling is crossed: a file announced as small
     * but endless cannot fill memory, it returns `null`.
     */
    private fun readBounded(open: () -> InputStream?): ByteArray? = runCatching {
        open()?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                total += n
                if (total > MAX_TIFF_BYTES) return null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

    /** The TIFF file: read in one go (bounded), then decoded under the same ceilings as the rest. */
    private fun decodeTiffFile(file: File, maxPx: Int, maxBytes: Int): Bitmap? {
        if (file.length() > MAX_TIFF_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return tiffToBitmap(bytes, maxPx, maxBytes)
    }

    /**
     * The TIFF decoded into a `Bitmap`, bounded **twice**.
     *
     * The decoder has already reduced the image to [maxPx] on its longest side (its own
     * subsampling, applied during reading); the resulting weight is then checked against the
     * byte budget and the engine's drawing ceiling, as for any other format.
     */
    private fun tiffToBitmap(bytes: ByteArray, maxPx: Int, maxBytes: Int): Bitmap? {
        val image = decodeTiff(bytes, maxPx.coerceAtLeast(1)) ?: return null
        val count = image.width.toLong() * image.height.toLong() * BoundedImage.BYTES_PER_PIXEL
        if (count <= 0L || count > maxBytes.toLong() || !BoundedImage.canDraw(count)) return null
        val bitmap = runCatching {
            Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        return oriented(bitmap, image.orientation)
    }

    /**
     * The TIFF `Orientation` tag applied to the `Bitmap` (1–8, as EXIF numbers them).
     *
     * `BitmapFactory` does not apply the EXIF orientation either: the behaviour stays the same on
     * both sides — but a TIFF marked as "rotated" (a scanner, for example) is common, and
     * showing it sideways for want of ten lines would be a visible defect.
     */
    private fun oriented(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation !in 2..8) return bitmap
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(270f)
        }
        return runCatching {
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned != bitmap) bitmap.recycle()
            turned
        }.getOrDefault(bitmap)
    }

    /** The plan of a file, computed from its header alone. */
    private fun planOfFile(file: File, maxPx: Int, maxBytes: Int): BoundedImage.Plan? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return BoundedImage.plan(bounds.outWidth, bounds.outHeight, maxPx, maxBytes)
    }

    /** The plan of a stream, computed from its header alone. */
    private fun planOfStream(open: () -> InputStream?, maxPx: Int, maxBytes: Int): BoundedImage.Plan? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return BoundedImage.plan(bounds.outWidth, bounds.outHeight, maxPx, maxBytes)
    }

    /**
     * The final check, the one that keeps drawing from throwing: a `Bitmap` heavier than the
     * budget is freed and refused. This is the belt after the plan's braces — a JPEG that
     * announces 512×512 and delivers 8000×6000 stops here.
     */
    private fun accept(bitmap: Bitmap?, maxBytes: Int, plan: BoundedImage.Plan?): Bitmap? {
        if (bitmap == null) return null
        val bytes = bitmap.allocationByteCount.toLong().coerceAtLeast(bitmap.byteCount.toLong())
        if (bytes > maxBytes.toLong() || !BoundedImage.canDraw(bytes)) {
            bitmap.recycle()
            return null
        }
        // A truncated JPEG does not throw: the decoder returns the bands it could read, and the
        // thumbnail showed a half-drawn image (observed on a photo cut at two
        // thirds). When the dimensions obtained are clearly below those announced by
        // the header, the file is incomplete — we prefer the error card to a lying preview.
        if (plan != null && isTruncated(bitmap, plan)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** True when decoding produced far fewer pixels than the header announced. */
    private fun isTruncated(bitmap: Bitmap, plan: BoundedImage.Plan): Boolean =
        BoundedImage.isTruncated(plan.width, plan.height, bitmap.width, bitmap.height)

    // -----------------------------------------------------------------------------------------
    // Format detection, and the system fallback (HEIC / AVIF)
    // -----------------------------------------------------------------------------------------

    /** Recognises the format from the first bytes of the stream (re-read), without loading it whole. */
    private fun sniffStream(open: () -> InputStream?): ImageFormat = runCatching {
        open()?.use { stream ->
            val head = ByteArray(16)
            val read = stream.read(head)
            ImageFormats.sniff(head, if (read > 0) read else 0)
        } ?: ImageFormat.UNKNOWN
    }.getOrDefault(ImageFormat.UNKNOWN)

    /** The same reading, on the first bytes of a file. */
    private fun sniffFile(file: File): ImageFormat = runCatching {
        file.inputStream().use { stream ->
            val head = ByteArray(16)
            val read = stream.read(head)
            ImageFormats.sniff(head, if (read > 0) read else 0)
        }
    }.getOrDefault(ImageFormat.UNKNOWN)

    /**
     * HEIC / AVIF through the system decoder (`ImageDecoder`, API 28+). Subsampling
     * follows the same bounded plan as the rest; on a version without `ImageDecoder`, nothing to do.
     */
    private fun decodeWithImageDecoder(open: () -> InputStream?, maxPx: Int, maxBytes: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val bytes = readAllBounded(open) ?: return null
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val plan = BoundedImage.plan(info.size.width, info.size.height, maxPx, maxBytes)
            if (plan == null) decoder.setTargetSize(1, 1) else decoder.setTargetSampleSize(plan.sampleSize)
        }
        accept(bitmap, maxBytes, plan = null)
    }.getOrNull()

    /** Reads the whole stream, bounded: beyond the read ceiling, we refuse rather than bloat. */
    private fun readAllBounded(open: () -> InputStream?): ByteArray? = runCatching {
        open()?.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n <= 0) break
                if (out.size() + n > MAX_READ_BYTES) return null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()
}
