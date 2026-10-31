package com.onyx.app.data.local

/**
 * Ce que le téléphone garde d'un **choix de modèle** : les favoris et les récents, **par serveur**.
 *
 * Le modèle retenu, lui, ne vit pas ici : par bot il est écrit sur le serveur
 * (`profiles.configure`, voir `ServeBotsRpc.setModel`) **et** gardé par le magasin des visages
 * (`FaceKeys.model`) ; c'est ce qui le fait survivre à une relance. Ce fichier ne tient que ce qui
 * n'appartient qu'à cet appareil : l'étoile d'un fournisseur, et l'ordre des derniers modèles
 * servis — de quoi mettre **les favoris puis les récents en tête** de la liste.
 *
 * Les clés sont dans [ModelKeys] ; [ModelPreferenceStore] n'est qu'un accès, posé sur n'importe
 * quel [RenameStore] — `SharedPreferences` sur l'appareil, une copie en mémoire dans un test.
 */
object ModelKeys {
    /** Le fichier de préférences de l'appareil. */
    const val FILE = "onyx_model_choices"

    /** Le séparateur interne : un identifiant de modèle ne peut pas le contenir. */
    const val SEPARATOR = "\u001F"

    /** Combien de récents on garde, au plus — assez pour remplir le haut d'une liste. */
    const val RECENT_LIMIT = 8

    fun favourites(server: String): String = "favourites|$server"
    fun recents(server: String): String = "recents|$server"
}

/**
 * Les favoris et les récents d'un serveur, lus et écrits par un [RenameStore].
 *
 * Un favori est un ensemble : il se pose et se retire, il n'a pas d'ordre. Un récent est une
 * **liste** — le plus récent en tête — sérialisée avec [ModelKeys.SEPARATOR] ; noter un modèle déjà
 * présent le fait remonter sans le dédoubler, et la liste est bornée à [ModelKeys.RECENT_LIMIT].
 */
class ModelPreferenceStore(private val store: RenameStore) {

    /** Les identifiants favoris d'un serveur. Vide quand rien n'a été posé. */
    fun favourites(server: String): Set<String> =
        store.stringSet(ModelKeys.favourites(server)).orEmpty()

    fun isFavourite(server: String, id: String): Boolean = id in favourites(server)

    /** Pose ou retire l'étoile d'un modèle. Un identifiant vide n'est jamais gardé. */
    fun setFavourite(server: String, id: String, favourite: Boolean) {
        val clean = id.trim()
        if (clean.isEmpty()) return
        val current = favourites(server).toMutableSet()
        if (favourite) current += clean else current -= clean
        val key = ModelKeys.favourites(server)
        if (current.isEmpty()) store.remove(key) else store.putStringSet(key, current)
    }

    /** Les derniers modèles servis, du plus récent au plus ancien. */
    fun recents(server: String): List<String> =
        store.string(ModelKeys.recents(server))
            ?.split(ModelKeys.SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Note qu'un modèle vient d'être servi : il passe en tête, sans doublon, liste bornée. */
    fun noteRecent(server: String, id: String) {
        val clean = id.trim()
        if (clean.isEmpty()) return
        val next = (listOf(clean) + recents(server).filter { it != clean })
            .take(ModelKeys.RECENT_LIMIT)
        store.putString(ModelKeys.recents(server), next.joinToString(ModelKeys.SEPARATOR))
    }
}
