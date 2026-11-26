package com.onyx.app.data.model

/**
 * Le type MIME sous lequel **un fichier récupéré du serveur** est confié au système.
 *
 * Pourquoi une table, et pas un `application/octet-stream` pour tout : c'est ce type qui décide ce
 * qu'Android propose ensuite. Un APK annoncé en `octet-stream` ne propose **jamais** de s'installer ;
 * annoncé en `application/vnd.android.package-archive`, le système ouvre l'installeur. Le défaut
 * n'était pas cosmétique : il rendait impossible le seul geste qu'on demandait — récupérer une
 * version d'Onyx depuis son propre serveur.
 *
 * L'inconnu reste `application/octet-stream` : on ne devine jamais un type qu'on ne connaît pas, et
 * l'écran dit alors au système « ouvre ce que tu peux ».
 */
fun downloadMimeFor(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/vnd.rar"
        "tar" -> "application/x-tar"
        "gz", "tgz" -> "application/gzip"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "txt", "md", "log", "kt", "py", "sh", "yaml", "yml", "toml" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "application/octet-stream"
    }