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
 * Le seul chemin par lequel une image entre en mémoire dans l'app.
 *
 * Chaque appel passe par deux temps : on lit d'abord **l'en-tête seul** (`inJustDecodeBounds`)
 * pour connaître les dimensions, on demande à [BoundedImage] le facteur de réduction, puis on
 * décode une fois avec ce facteur. L'image pleine résolution n'est donc jamais construite.
 *
 * Trois règles, et une image qui ne les tient pas devient `null` — jamais une exception :
 * 1. le plan vient de [BoundedImage.plan], borné en pixels **et** en octets ;
 * 2. le `Bitmap` obtenu est revérifié ([BoundedImage.canDraw]) : un fichier peut annoncer des
 *    dimensions et en livrer d'autres ;
 * 3. tout ce qui touche au disque, à un `Uri` ou au décodeur est sous `runCatching`.
 *
 * Un `null` est un état normal : l'appelant affiche alors une carte d'erreur ou son symbole.
 */
object BoundedImages {

    /** Le côté le plus long d'une vignette de fil (64 dp à 3× de densité). */
    const val THUMB_PX = 192

    /** Le côté le plus long d'une ligne de l'onglet Fichiers (46 dp à 3×). */
    const val ROW_PX = 160

    /** Le côté le plus long d'un aperçu de fichier livré. */
    const val PREVIEW_PX = 512

    /** Le côté le plus long du lecteur d'image : au-delà, l'écran ne montre rien de plus. */
    const val VIEWER_PX = 2048

    /**
     * Le budget du lecteur : plus large que celui d'une vignette (on zoome jusqu'à 2,5×), mais
     * toujours sous le plafond de dessin du moteur de rendu.
     */
    const val VIEWER_BYTES = 24 * 1024 * 1024

    /**
     * Le plus gros TIFF qu'on accepte de charger d'un coup.
     *
     * Le décodeur de la maison travaille sur les octets entiers (le format n'offre pas de lecture
     * partielle comme `BitmapFactory`), donc la borne est posée **avant** la lecture : au-delà, on
     * rend `null` plutôt que de charger un fichier arbitrairement gros en mémoire.
     */
    const val MAX_TIFF_BYTES = 64L * 1024 * 1024

    /**
     * Ce qu'on accepte de lire **en entier** pour les formats qui l'exigent (le TIFF de la maison,
     * et le repli `ImageDecoder` du système pour le HEIC / l'AVIF qu'une version refuse).
     */
    private const val MAX_READ_BYTES = 96 * 1024 * 1024

    /** Décode un fichier du téléphone, borné. */
    fun decodeFile(
        file: File,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        if (!file.exists() || file.length() <= 0L) return null
        // Un TIFF ne passe pas par `BitmapFactory` : la signature décide, et le décodeur de la
        // maison s'en charge, avec ses propres bornes (plafond de pixels lu dans l'en-tête, puis
        // réduction par moyenne jusqu'au bord demandé).
        if (fileLooksLikeTiff(file)) return decodeTiffFile(file, maxPx, maxBytes)
        val format = sniffFile(file)
        val plan = planOfFile(file, maxPx, maxBytes) ?: return null
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
        accept(bitmap, maxBytes, plan)?.let { return it }

        // HEIC / AVIF que `BitmapFactory` ne sait pas lire sur cette version : le décodeur système.
        if (format == ImageFormat.HEIF || format == ImageFormat.AVIF) {
            return decodeWithImageDecoder({ file.inputStream() }, maxPx, maxBytes)
        }
        null
    }.getOrNull()

    /** Décode un flux rouvert à chaque temps (un `content://` se relit, il ne se rembobine pas). */
    fun decodeStream(
        open: () -> InputStream?,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        // La signature se lit sur les premiers octets ; le flux est ensuite rouvert pour la suite.
        // Un TIFF se lit d'un bloc (borné) ; les autres formats repassent par l'en-tête seul.
        val format = sniffStream(open)
        if (format == ImageFormat.TIFF || streamLooksLikeTiff(open)) {
            val bytes = readBounded(open) ?: return null
            return tiffToBitmap(bytes, maxPx, maxBytes)
        }
        val plan = planOfStream(open, maxPx, maxBytes) ?: return null
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) }
        accept(bitmap, maxBytes, plan)?.let { return it }

        // HEIC / AVIF que `BitmapFactory` ne sait pas lire sur cette version : le décodeur système.
        if (format == ImageFormat.HEIF || format == ImageFormat.AVIF) {
            return decodeWithImageDecoder(open, maxPx, maxBytes)
        }
        null
    }.getOrNull()

    /** Décode des octets déjà en mémoire (photo d'un profil, réponse du serveur), borné. */
    fun decodeBytes(
        bytes: ByteArray,
        maxPx: Int = THUMB_PX,
        maxBytes: Int = BoundedImage.MAX_BYTES,
    ): Bitmap? = runCatching {
        if (bytes.isEmpty()) return null
        if (isTiff(bytes)) return tiffToBitmap(bytes, maxPx, maxBytes)
        decodeStream({ bytes.inputStream() }, maxPx, maxBytes)
    }.getOrNull()

    /** Décode l'image d'un `Uri` du téléphone (galerie, sélecteur, partage), borné. */
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
     * Décode hors du fil principal : une image de galerie peut peser lourd, et un `Uri` distant
     * se lit lentement. L'annulation d'un écran qui part n'est pas une erreur, elle remonte.
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
    // Le TIFF : le seul format d'image que le système ne décode pas
    // -----------------------------------------------------------------------------------------

    /** Vrai quand le fichier commence par une signature TIFF. */
    private fun fileLooksLikeTiff(file: File): Boolean = runCatching {
        if (file.length() > MAX_TIFF_BYTES) return false
        file.inputStream().use { stream -> isTiff(stream.readHead()) }
    }.getOrDefault(false)

    /** Vrai quand le flux commence par une signature TIFF (le flux est rouvert par l'appelant). */
    private fun streamLooksLikeTiff(open: () -> InputStream?): Boolean = runCatching {
        open()?.use { stream -> isTiff(stream.readHead()) } == true
    }.getOrDefault(false)

    /** Les quatre premiers octets d'un flux : de quoi lire une signature, et rien de plus. */
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
     * Le contenu d'un flux, refusé au-delà de [MAX_TIFF_BYTES].
     *
     * On lit par blocs et on s'arrête **dès** que le plafond est franchi : un fichier annoncé petit
     * mais interminable ne peut pas remplir la mémoire, il rend `null`.
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

    /** Le fichier TIFF : lu d'un bloc (borné), puis décodé sous les mêmes plafonds que le reste. */
    private fun decodeTiffFile(file: File, maxPx: Int, maxBytes: Int): Bitmap? {
        if (file.length() > MAX_TIFF_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return tiffToBitmap(bytes, maxPx, maxBytes)
    }

    /**
     * Le TIFF décodé en `Bitmap`, borné **deux fois**.
     *
     * Le décodeur a déjà réduit l'image à [maxPx] sur son plus grand côté (son propre
     * sous-échantillonnage, appliqué pendant la lecture) ; le poids obtenu est ensuite confronté au
     * budget d'octets et au plafond de dessin du moteur, comme pour n'importe quel autre format.
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
     * Le tag `Orientation` du TIFF appliqué au `Bitmap` (1–8, comme EXIF les numérote).
     *
     * `BitmapFactory` n'applique pas non plus l'orientation EXIF : le comportement reste le même des
     * deux côtés — mais un TIFF marqué « tourné » (un scanner, par exemple) est fréquent, et le
     * montrer de travers pour n'avoir pas écrit dix lignes serait un défaut visible.
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

    /** Le plan d'un fichier, calculé sur son seul en-tête. */
    private fun planOfFile(file: File, maxPx: Int, maxBytes: Int): BoundedImage.Plan? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return BoundedImage.plan(bounds.outWidth, bounds.outHeight, maxPx, maxBytes)
    }

    /** Le plan d'un flux, calculé sur son seul en-tête. */
    private fun planOfStream(open: () -> InputStream?, maxPx: Int, maxBytes: Int): BoundedImage.Plan? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        return BoundedImage.plan(bounds.outWidth, bounds.outHeight, maxPx, maxBytes)
    }

    /**
     * Le dernier contrôle, celui qui empêche le dessin de lever : un `Bitmap` plus lourd que le
     * budget est libéré et refusé. C'est la ceinture après les bretelles du plan — un JPEG qui
     * annonce 512×512 et en livre 8000×6000 s'arrête ici.
     */
    private fun accept(bitmap: Bitmap?, maxBytes: Int, plan: BoundedImage.Plan?): Bitmap? {
        if (bitmap == null) return null
        val bytes = bitmap.allocationByteCount.toLong().coerceAtLeast(bitmap.byteCount.toLong())
        if (bytes > maxBytes.toLong() || !BoundedImage.canDraw(bytes)) {
            bitmap.recycle()
            return null
        }
        // Un JPEG tronqué ne lève pas : le décodeur rend les bandes qu'il a pu lire, et la
        // vignette montrait une image à moitié dessinée (constaté sur une photo coupée aux deux
        // tiers). Quand les dimensions obtenues sont nettement sous celles annoncées par
        // l'en-tête, le fichier est incomplet — on préfère la carte d'erreur à un aperçu menteur.
        if (plan != null && isTruncated(bitmap, plan)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /** Vrai quand le décodage a rendu beaucoup moins de pixels que l'en-tête n'en annonçait. */
    private fun isTruncated(bitmap: Bitmap, plan: BoundedImage.Plan): Boolean =
        BoundedImage.isTruncated(plan.width, plan.height, bitmap.width, bitmap.height)

    // -----------------------------------------------------------------------------------------
    // La reconnaissance de format, et le repli du système (HEIC / AVIF)
    // -----------------------------------------------------------------------------------------

    /** Reconnaît le format sur les premiers octets du flux (relecture), sans le charger entier. */
    private fun sniffStream(open: () -> InputStream?): ImageFormat = runCatching {
        open()?.use { stream ->
            val head = ByteArray(16)
            val read = stream.read(head)
            ImageFormats.sniff(head, if (read > 0) read else 0)
        } ?: ImageFormat.UNKNOWN
    }.getOrDefault(ImageFormat.UNKNOWN)

    /** Le même relevé, sur les premiers octets d'un fichier. */
    private fun sniffFile(file: File): ImageFormat = runCatching {
        file.inputStream().use { stream ->
            val head = ByteArray(16)
            val read = stream.read(head)
            ImageFormats.sniff(head, if (read > 0) read else 0)
        }
    }.getOrDefault(ImageFormat.UNKNOWN)

    /**
     * Le HEIC / AVIF par le décodeur du système (`ImageDecoder`, API 28+). Le sous-échantillonnage
     * suit le même plan borné que le reste ; sur une version sans `ImageDecoder`, rien à faire.
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

    /** Lit le flux entier, borné : au-delà du plafond de lecture, on refuse plutôt que de gonfler. */
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
