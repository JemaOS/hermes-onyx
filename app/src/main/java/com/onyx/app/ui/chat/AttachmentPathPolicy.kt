package com.onyx.app.ui.chat

/**
 * **Où vit une pièce jointe** — la règle, pure, sans Android.
 *
 * Elle existe pour être éprouvée sans appareil (un test JVM la lit), et pour être la seule à
 * décider ce qui est « durable » : une pièce jointe ne se référence **jamais** par une URI
 * transitoire (`content://` du sélecteur, dont l'autorisation meurt avec le processus) ni par un
 * chemin sous `cacheDir` (que le système purge quand il veut). Elle se référence par le `file://`
 * d'une copie dans le stockage privé de l'app (`filesDir`).
 *
 * C'est la mécanique du défaut mesuré : trois prises de vue écrites dans `cacheDir`, un sélecteur
 * qui ne gardait que son URI, et des bulles « Illisible » après fermeture de l'app.
 */
object AttachmentPathPolicy {

    /** Vrai pour une référence qui **ne survit pas** à la fermeture de l'app ou au nettoyage du cache. */
    fun isTransient(path: String): Boolean =
        path.startsWith("content://") ||
            path.startsWith("android.resource://") ||
            path.contains("/cache/") ||
            path.startsWith("cache/")

    /**
     * Vrai pour un `file://` du stockage privé durable (`filesDir`), seul forme acceptée pour une
     * pièce jointe : elle survit à `am force-stop`, au redémarrage du téléphone, et au nettoyage
     * du cache par le système.
     */
    fun isDurable(path: String): Boolean =
        path.startsWith("file://") && path.contains("/files/") && !isTransient(path)
}
