package com.onyx.app.ui.chat

/**
 * Où s'ouvre un fichier joint : dans un lecteur de l'app, ou dans une autre app.
 *
 * référence décide de la même façon dans `AttachmentTile` / `DeliveredFileChip` : un PDF et une image se
 * montrent dans Onyx, le reste part à l'app qui sait le lire. La décision est prise ici, sans
 * dépendance Android, pour être éprouvée par un test.
 */
enum class FileViewerKind {
    /** Un PDF : le lecteur intégré, page par page. */
    PDF,

    /** Une image : le visualiseur plein écran. */
    PHOTO,

    /** Une vidéo : le lecteur intégré, ouvert sur sa vignette. */
    VIDEO,

    /** Tout le reste : confié au système. */
    EXTERNAL,
}

object FileViewers {

    private val photoExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "tif", "tiff")

    /**
     * Les noms qui désignent une vidéo. Le type MIME suffit quand le fournisseur en donne un ;
     * cette liste prend le relais quand il n'en donne pas (fichiers locaux, pièces jointes du fil).
     */
    private val videoExtensions = setOf(
        "mp4", "m4v", "mov", "qt", "webm", "mkv", "avi", "3gp", "3g2", "mpg", "mpeg", "m2ts", "ts", "ogv",
    )

    /** L'extension d'un nom de fichier, en minuscules, sans le point. */
    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Ce qu'un fichier demande à l'ouverture. */
    fun kind(name: String, mime: String = ""): FileViewerKind = when {
        isPdf(name, mime) -> FileViewerKind.PDF
        isVideo(name, mime) -> FileViewerKind.VIDEO
        isPhoto(name, mime) -> FileViewerKind.PHOTO
        else -> FileViewerKind.EXTERNAL
    }

    /** Vrai pour un PDF, par son type ou par son nom. */
    fun isPdf(name: String, mime: String = ""): Boolean =
        mime.equals("application/pdf", ignoreCase = true) || extension(name) == "pdf"

    /**
     * Vrai pour une vidéo, par son type ou par son nom — le même partage que `Attachment.isVideo`
     * de la référence (le type d'abord, le nom ensuite), appliqué au lecteur intégré.
     */
    fun isVideo(name: String, mime: String = ""): Boolean =
        mime.startsWith("video/", ignoreCase = true) || extension(name) in videoExtensions

    /** Vrai pour une image, par son type ou par son nom. */
    fun isPhoto(name: String, mime: String = ""): Boolean =
        mime.startsWith("image/", ignoreCase = true) || extension(name) in photoExtensions

    /** Le type MIME qu'on annonce au système pour un fichier. */
    fun mimeFor(name: String): String = when (extension(name)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "tif", "tiff" -> "image/tiff"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "mp4", "m4v" -> "video/mp4"
        "mov", "qt" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "3gp", "3g2" -> "video/3gpp"
        "mpg", "mpeg" -> "video/mpeg"
        "ogv" -> "video/ogg"
        "md", "markdown", "txt", "csv", "json", "log" -> "text/plain"
        "html", "htm" -> "text/html"
        else -> "application/octet-stream"
    }
}

/**
 * Les pages d'un PDF : ce qu'on compte, ce qu'on montre.
 *
 * La page est un index à partir de zéro ; le libellé compte à partir de un, comme « 1 of 3 » dans
 * le lecteur de la référence.
 */
object PdfPaging {

    /** Le nombre de pages qu'on peut vraiment montrer : jamais moins d'une. */
    fun pageCount(pages: Int): Int = maxOf(pages, 1)

    /** Une page ramenée dans les bornes du document. */
    fun clamp(page: Int, pages: Int): Int = page.coerceIn(0, pageCount(pages) - 1)

    /** « Page 2 of 3 » : ce que la barre du lecteur affiche. */
    fun label(page: Int, pages: Int): String = "Page ${clamp(page, pages) + 1} of ${pageCount(pages)}"

    /** Vrai si l'on peut encore tourner d'un cran dans cette direction. */
    fun canStep(page: Int, pages: Int, delta: Int): Boolean =
        clamp(page + delta, pages) != clamp(page, pages)
}

/**
 * Le déplacement d'une page **zoomée** : la page suit le doigt, sans jamais laisser voir de vide
 * au-delà de ses propres bords.
 *
 * Sans lui, une page agrandie ne montrait que son centre : le zoom était un grossissement fixe, et
 * le bord de la feuille — là où se trouve la marge, souvent ce qu'on veut lire — restait
 * inatteignable. Les bornes se calculent sur la fenêtre : à un facteur `z`, la page dépasse de
 * `(z − 1) / 2` de chaque côté, donc c'est exactement ce qu'on peut parcourir.
 *
 * Fonction pure, sans dépendance Compose : elle est éprouvée hors appareil.
 */
object PdfPan {

    /** La translation maximale, en pixels, sur un axe de [sizePx] agrandi par [zoom]. */
    fun limit(sizePx: Int, zoom: Float): Float =
        if (sizePx <= 0) 0f else (sizePx * (zoom.coerceAtLeast(1f) - 1f) / 2f).coerceAtLeast(0f)

    /** [value] ramené dans les bornes de [sizePx] au facteur [zoom]. */
    fun clamp(value: Float, sizePx: Int, zoom: Float): Float {
        val max = limit(sizePx, zoom)
        return value.coerceIn(-max, max)
    }
}
