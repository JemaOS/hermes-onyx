package com.onyx.app.data.image

/**
 * Ce qu'un fichier d'image est **vraiment**, d'après ses premiers octets — jamais d'après son nom.
 *
 * Android porte lui-même le JPEG, le PNG, le GIF, le WebP et, selon la version, le HEIC et l'AVIF ;
 * le **TIFF**, lui, n'est porté par aucun décodeur de la plateforme. C'est cette reconnaissance qui
 * permet d'envoyer chaque fichier au bon décodeur — et de ne **jamais** croire une extension seule :
 * un `.tif` qui contient un PNG reste un PNG.
 *
 * Classe pure : aucune dépendance Android, donc éprouvable sur JVM.
 */
enum class ImageFormat(val label: String) {
    JPEG("jpeg"),
    PNG("png"),
    GIF("gif"),
    WEBP("webp"),
    BMP("bmp"),
    HEIF("heif"),
    AVIF("avif"),
    TIFF("tiff"),
    UNKNOWN("unknown"),
}

object ImageFormats {

    /** Reconnaît le format sur les premiers octets ; [UNKNOWN] quand rien ne correspond. */
    fun sniff(bytes: ByteArray, length: Int = bytes.size): ImageFormat {
        val n = minOf(length, bytes.size)
        if (n < 4) return ImageFormat.UNKNOWN
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        // JPEG : FF D8 FF
        if (u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF) return ImageFormat.JPEG
        // PNG : 89 50 4E 47
        if (n >= 8 && u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return ImageFormat.PNG
        // GIF : « GIF8 »
        if (u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x38) return ImageFormat.GIF
        // BMP : « BM »
        if (u(0) == 0x42 && u(1) == 0x4D) return ImageFormat.BMP
        // TIFF : « II*\0 » (petit-boutiste) ou « MM\0* » (grand-boutiste)
        if (u(0) == 0x49 && u(1) == 0x49 && u(2) == 0x2A && u(3) == 0x00) return ImageFormat.TIFF
        if (u(0) == 0x4D && u(1) == 0x4D && u(2) == 0x00 && u(3) == 0x2A) return ImageFormat.TIFF
        // ISO-BMFF (HEIC / AVIF) : « ....ftyp » dans les huit premiers octets.
        if (n >= 12 && u(4) == 0x66 && u(5) == 0x74 && u(6) == 0x79 && u(7) == 0x70) {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII).lowercase()
            return when {
                brand.startsWith("avif") || brand.startsWith("avis") -> ImageFormat.AVIF
                brand.startsWith("heic") || brand.startsWith("heix") ||
                    brand.startsWith("hevc") || brand.startsWith("hevx") ||
                    brand.startsWith("mif1") || brand.startsWith("msf1") -> ImageFormat.HEIF
                else -> ImageFormat.UNKNOWN
            }
        }
        // WebP : « RIFF »…« WEBP »
        if (n >= 12 && u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
            u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50
        ) {
            return ImageFormat.WEBP
        }
        return ImageFormat.UNKNOWN
    }

    /**
     * Le format que la plateforme sait décoder sans que l'app s'en mêle : JPEG, PNG, GIF, WebP, BMP,
     * et HEIC/AVIF **à partir d'Android 8** (le décodeur d'images du système). Le TIFF reste dehors :
     * c'est [TiffImage] qui le décode.
     */
    fun handledByPlatform(format: ImageFormat): Boolean = when (format) {
        ImageFormat.TIFF, ImageFormat.UNKNOWN -> false
        else -> true
    }
}
