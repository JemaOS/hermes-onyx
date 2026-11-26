package com.onyx.app.data.model

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Un fichier échangé dans une conversation : ce qu'on montre de lui dans l'onglet **Fichiers**,
 * et de quoi décider où il s'ouvre.
 *
 * [name] et [path] sont ce que le fil connaît déjà (les pastilles de fichier livré et les pièces
 * jointes) ; [mime] vient de la pièce jointe quand elle en porte un ; [appearedAtMillis] est la
 * date du message qui l'a amené — la seule date dont l'app dispose sans interroger le serveur ;
 * [sizeBytes] reste `null` tant que ni le téléphone ni le serveur ne l'a dit (jamais un zéro
 * inventé : « 0 o » se lirait comme un fichier vide).
 */
data class ConversationFile(
    val name: String,
    val path: String,
    val mime: String = "",
    val appearedAtMillis: Long = 0L,
    val sizeBytes: Long? = null,
) {
    /** La famille déduite du nom **et** du type annoncé. */
    val family: FileFamily get() = FileFamily.of(name, mime)
}

/**
 * Les familles de fichiers que l'onglet range — et, en tête, [ALL] qui les montre toutes.
 *
 * [OTHER] n'a **pas** de filtre à lui : ce qui n'est ni image, ni vidéo, ni son, ni document, ni
 * archive reste visible dans « Tout », comme un échec du Code reste dans « Tout » plutôt que
 * d'inventer un bouton de plus ([FileFamily.OTHER] est compté dans [ALL], jamais ailleurs).
 */
enum class FileFamily(val key: String) {
    ALL("all"),
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    DOCUMENT("document"),
    ARCHIVE("archive"),
    OTHER("other"),
    ;

    /** La famille a-t-elle sa puce en haut ? */
    val isFilter: Boolean get() = this != OTHER

    /** Le filtre retient-il cette famille ? */
    fun accepts(family: FileFamily): Boolean = this == ALL || this == family

    companion object {

        /** Les images, par leur nom : le type MIME d'une pièce jointe peut manquer. */
        private val imageExtensions = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg", "tif", "tiff", "avif",
        )

        /** Les vidéos, par leur nom (tout type `video/…` les attrape aussi). */
        private val videoExtensions = setOf(
            "mp4", "m4v", "mov", "qt", "webm", "mkv", "avi", "3gp", "3g2", "mpg", "mpeg", "m2ts", "ogv",
        )

        /** Les sons, par leur nom (tout type `audio/…` les attrape aussi). */
        private val audioExtensions = setOf(
            "mp3", "m4a", "m4b", "aac", "wav", "wave", "ogg", "oga", "opus", "flac", "amr", "aiff",
            "aif", "wma", "mid", "midi",
        )

        /** Les documents : ce qu'on lit, ou ce qu'on ouvre avec l'application qui sait le lire. */
        private val documentExtensions = setOf(
            "pdf", "doc", "docx", "odt", "rtf", "txt", "text", "md", "markdown", "csv", "tsv",
            "json", "yaml", "yml", "xml", "html", "htm", "log", "ini", "toml", "epub",
            "ppt", "pptx", "odp", "xls", "xlsx", "ods", "pages", "numbers", "key",
        )

        /** Les archives : ce qui se liste quand c'est un alignement de fichiers, sinon s'ouvre ailleurs. */
        private val archiveExtensions = setOf(
            "zip", "jar", "apk", "gz", "tar", "tgz", "bz2", "xz", "zst", "7z", "rar", "lz", "lzma",
        )

        private val documentMimes = setOf(
            "application/pdf",
            "application/rtf",
            "application/json",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/epub+zip",
        )

        private val archiveMimes = setOf(
            "application/zip",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-7z-compressed",
            "application/vnd.rar",
            "application/x-rar-compressed",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/java-archive",
            "application/vnd.android.package-archive",
        )

        /** L'extension d'un nom, en minuscules et sans le point. */
        fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

        /**
         * La famille d'un fichier : **le type MIME d'abord, l'extension ensuite**.
         *
         * Les deux sont lus parce que ni l'un ni l'autre n'est toujours là : une pièce jointe du
         * téléphone annonce `image/jpeg` sans que le nom le dise, et un chemin livré par l'agent
         * porte `IMG-20261026-WA0001.jpg` sans le moindre type. Un type explicite (`video/…`)
         * l'emporte donc sur un nom trompeur, et un nom connu prend le relais quand le type
         * manque ou reste générique (`application/octet-stream`).
         */
        fun of(name: String, mime: String = ""): FileFamily {
            val type = mime.trim().lowercase()
            val extension = extensionOf(name)
            return when {
                type.startsWith("image/") || extension in imageExtensions -> IMAGE
                type.startsWith("video/") || extension in videoExtensions -> VIDEO
                type.startsWith("audio/") || extension in audioExtensions -> AUDIO
                type.startsWith("text/") || type in documentMimes || extension in documentExtensions -> DOCUMENT
                type in archiveMimes || extension in archiveExtensions -> ARCHIVE
                else -> OTHER
            }
        }

        /** Vrai pour un PDF, par son type ou par son nom. */
        fun isPdf(name: String, mime: String = ""): Boolean =
            mime.trim().equals("application/pdf", ignoreCase = true) || extensionOf(name) == "pdf"

        /**
         * Vrai pour un document que l'app sait montrer en texte.
         *
         * Un type `text/…` suffit ; sinon c'est le nom qui parle (le même partage que partout
         * ailleurs dans l'app : le type d'abord, le nom ensuite).
         */
        fun isReadableText(name: String, mime: String = ""): Boolean {
            val type = mime.trim().lowercase()
            if (type.startsWith("text/")) return true
            return extensionOf(name) in setOf(
                "txt", "text", "md", "markdown", "csv", "tsv", "json", "yaml", "yml", "xml",
                "html", "htm", "log", "ini", "toml", "sh", "kt", "py", "js", "css",
            )
        }

        /** Vrai pour une archive d'alignement de fichiers, qu'on peut donc lister sur place. */
        fun isListableArchive(name: String, mime: String = ""): Boolean =
            extensionOf(name) in setOf("zip", "jar", "apk") ||
                mime.trim().lowercase() in setOf("application/zip", "application/java-archive")
    }
}

/**
 * L'ordre de la liste. Cinq états, tous **visibles** en haut de l'onglet : la date dans les deux
 * sens (« récent d'abord » et son inverse), le nom, la taille, et la famille.
 */
enum class FileSort(val key: String) {
    RECENT("recent"),
    OLDEST("oldest"),
    NAME("name"),
    SIZE("size"),
    TYPE("type"),
}

/**
 * Où l'appui sur une ligne emmène.
 *
 * Chaque famille a sa destination, et [EXTERNAL] — l'application du système — est une destination
 * comme une autre, jamais un « rien » : c'est ce qui interdit qu'un appui reste sans effet.
 */
enum class FileOpenRoute(val key: String) {
    PHOTO("photo"),
    VIDEO("video"),
    AUDIO("audio"),
    PDF("pdf"),
    TEXT("text"),
    ARCHIVE("archive"),
    EXTERNAL("external"),
}

/** La destination d'un appui, pour un fichier donné. Jamais nulle : tout fichier ouvre quelque chose. */
fun fileOpenRoute(name: String, mime: String = ""): FileOpenRoute = when (FileFamily.of(name, mime)) {
    FileFamily.IMAGE -> FileOpenRoute.PHOTO
    FileFamily.VIDEO -> FileOpenRoute.VIDEO
    FileFamily.AUDIO -> FileOpenRoute.AUDIO
    FileFamily.ARCHIVE -> FileOpenRoute.ARCHIVE
    FileFamily.DOCUMENT -> when {
        FileFamily.isPdf(name, mime) -> FileOpenRoute.PDF
        FileFamily.isReadableText(name, mime) -> FileOpenRoute.TEXT
        // Un document bureautique n'a pas de lecteur dans l'app : il part au système, qui sait
        // lequel ouvrir — et l'onglet le dit au lieu de ne rien faire.
        else -> FileOpenRoute.EXTERNAL
    }
    // Un type inconnu n'est jamais un cul-de-sac : « Ouvrir avec ».
    FileFamily.OTHER, FileFamily.ALL -> FileOpenRoute.EXTERNAL
}

/** Le compte de chaque famille, pour les puces de filtre (« Images 3 »). */
fun fileFamilyCounts(files: List<ConversationFile>): Map<FileFamily, Int> =
    FileFamily.entries.associateWith { family -> files.count { family.accepts(it.family) } }

/** Ce que le filtre montre, dans l'ordre demandé. Fonction pure : c'est elle que les tests éprouvent. */
fun visibleConversationFiles(
    files: List<ConversationFile>,
    filter: FileFamily = FileFamily.ALL,
    sort: FileSort = FileSort.RECENT,
): List<ConversationFile> {
    val kept = files.filter { filter.accepts(it.family) }
    return when (sort) {
        FileSort.RECENT -> kept.sortedWith(
            compareByDescending<ConversationFile> { it.appearedAtMillis }.thenBy { it.name.lowercase() },
        )

        FileSort.OLDEST -> kept.sortedWith(
            compareBy<ConversationFile> { it.appearedAtMillis }.thenBy { it.name.lowercase() },
        )

        FileSort.NAME -> kept.sortedBy { it.name.lowercase() }

        FileSort.SIZE -> kept.sortedWith(
            // Une taille inconnue n'est pas zéro : elle passe en dernier, quel que soit le sens.
            compareByDescending<ConversationFile> { it.sizeBytes != null }
                .thenByDescending { it.sizeBytes ?: 0L }
                .thenBy { it.name.lowercase() },
        )

        FileSort.TYPE -> kept.sortedWith(
            compareBy<ConversationFile> { it.family.ordinal }.thenBy { it.name.lowercase() },
        )
    }
}

/** « Tout 7 » : le libellé d'une puce de filtre, traduit, suivi de son compte. */
fun fileFamilyLabel(family: FileFamily, count: Int, locale: Locale = OnyxLanguage.preferred): String {
    val name = when (family) {
        FileFamily.ALL -> "Everything"
        FileFamily.IMAGE -> "Images"
        FileFamily.VIDEO -> "Videos"
        FileFamily.AUDIO -> "Audio"
        FileFamily.DOCUMENT -> "Documents"
        FileFamily.ARCHIVE -> "Archives"
        FileFamily.OTHER -> "Other"
    }
    return OnyxLocalization.translate(name, locale) + " " + count
}

/** Le libellé d'un tri, traduit. */
fun fileSortLabel(sort: FileSort, locale: Locale = OnyxLanguage.preferred): String {
    val name = when (sort) {
        FileSort.RECENT -> "Recent first"
        FileSort.OLDEST -> "Oldest first"
        FileSort.NAME -> "Name"
        FileSort.SIZE -> "Size"
        FileSort.TYPE -> "Type"
    }
    return OnyxLocalization.translate(name, locale)
}

/**
 * Une date courte, formée avec sa langue (« 26/10/2026 »). Vide pour une date absente.
 *
 * C'est le seul nombre que ce fichier forme lui-même : **le poids** vient de `VideoMeta.sizeLabel`
 * (l'app module), la seule mise en forme de taille de l'app — deux façons d'écrire « 12,4 Mo »
 * finiraient par diverger.
 */
fun formatFileDate(millis: Long, locale: Locale = OnyxLanguage.preferred): String {
    if (millis <= 0L) return ""
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
}

/** « 12,4 Mo · 26/10/2026 » : la ligne de détail, sans jamais laisser un séparateur orphelin. */
fun fileDetailLine(
    sizeLabel: String,
    appearedAtMillis: Long,
    locale: Locale = OnyxLanguage.preferred,
): String = listOfNotNull(
    sizeLabel.takeIf { it.isNotEmpty() },
    formatFileDate(appearedAtMillis, locale).takeIf { it.isNotEmpty() },
).joinToString(" · ")
