package com.onyx.app.data.local

import android.content.Context

/**
 * **La marque des conversations supprimées** : les identifiants que ce téléphone a retirés.
 *
 * C'est ce qui rend une suppression **durable**. Le magasin local se contente de ne plus écrire la
 * conversation dans son fichier ; à lui seul, cela ne suffit pas : la même conversation peut être
 * réécrite plus tard par un chemin qui ne savait pas qu'elle avait été supprimée — la fin d'un tour
 * qui enregistre le fil (`ChatViewModel.persistCurrent`), un rattachement de bot relu du serveur, une
 * migration de profil. Avec la marque, ces chemins trouvent porte close : `save` refuse un
 * identifiant marqué, et aucune resynchronisation ne peut la ramener.
 *
 * La marque vit **hors du fichier des conversations** (les préférences du téléphone) : elle survit
 * donc au redémarrage, et au « Tout effacer » qui vide le fichier.
 *
 * Seul le **retour en arrière du bandeau** défait une marque ([unmark]) : c'est la seule chose qui
 * remet vraiment une conversation que la personne venait de supprimer.
 */
interface DeletedConversations {

    /** Les identifiants marqués comme supprimés. */
    fun marked(): Set<String>

    /** Marque une conversation comme supprimée. */
    fun mark(id: String)

    /** Défait la marque — le « Annuler » du bandeau, et lui seul. */
    fun unmark(id: String)

    companion object {
        /** Un magasin sans disque : les tests, et les fenêtres sans dépôt. */
        val InMemory: DeletedConversations get() = InMemoryDeletedConversations()
    }
}

/** [DeletedConversations] en mémoire. */
class InMemoryDeletedConversations(
    private val ids: MutableSet<String> = mutableSetOf(),
) : DeletedConversations {

    override fun marked(): Set<String> = ids.toSet()

    override fun mark(id: String) {
        ids += id
    }

    override fun unmark(id: String) {
        ids -= id
    }
}

/**
 * [DeletedConversations] sur les **préférences du téléphone** : la marque survit au redémarrage de
 * l'app, comme les autres états que le serveur ne tient pas (l'activité vue, l'ordre des bots).
 */
class SharedPreferencesDeletedConversations(context: Context) : DeletedConversations {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // `getStringSet` rend l'ensemble **vivant** du fichier : on le copie avant de s'en servir, sinon
    // une écriture suivante le modifierait sous nos pieds.
    override fun marked(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun mark(id: String) {
        prefs.edit().putStringSet(KEY, marked() + id).apply()
    }

    override fun unmark(id: String) {
        prefs.edit().putStringSet(KEY, marked() - id).apply()
    }

    private companion object {
        const val FILE = "onyx_deleted_conversations"
        const val KEY = "deleted"
    }
}
