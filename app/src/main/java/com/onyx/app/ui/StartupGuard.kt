package com.onyx.app.ui

import android.content.Context

/**
 * Ce que le démarrage doit faire, décidé avant de restaurer quoi que ce soit.
 *
 * [interrupted] est vrai quand le démarrage précédent n'a jamais atteint son premier dessin :
 * l'app est morte pendant qu'elle restaurait son état. Dans ce cas on **retombe sur Chat**, on
 * **écarte la pièce jointe du brouillon** (le fil, lui, garde la sienne : rien n'est supprimé) et
 * on oublie l'onglet mémorisé.
 */
data class StartPlan(
    val interrupted: Boolean,
    val chatTab: Boolean,
    val dropDraftAttachments: Boolean,
)

/** Le petit magasin du drapeau et de l'onglet, pour que la décision soit éprouvable sans appareil. */
interface StartupStore {
    fun interrupted(): Boolean
    fun markRestoring()
    fun markSettled()
    fun lastTab(): Int
    fun rememberTab(index: Int)
}

/**
 * Le briseur de boucle « fermeture / réouverture ».
 *
 * L'app restaure sa route, son onglet et son brouillon à chaque démarrage. Si l'état restauré
 * contient de quoi tuer le processus (une pièce jointe que le dessin refuse, un lecteur qui
 * lève), la restauration elle-même plante : le système relance l'app, elle restaure le même
 * état, elle remeurt. C'est une boucle, pas un plantage isolé.
 *
 * La parade tient en trois temps, dans cet ordre :
 * 1. [begin] **écrit le drapeau avant** que quoi que ce soit ne soit restauré (écriture
 *    bloquante : le drapeau doit être sur le disque même si le processus meurt à la frame
 *    suivante) ;
 * 2. le premier dessin passé, [settled] **efface** le drapeau ;
 * 3. si un démarrage retrouve le drapeau, il démarre **en secours** : Chat, brouillon sans sa
 *    pièce jointe, onglet remis à zéro — et il efface le drapeau, donc le démarrage suivant est
 *    normal. Un état fautif ne peut donc pas condamner l'app plus d'une fois.
 */
class StartupGuard(private val store: StartupStore) {

    /** À appeler **au tout début** de la composition, avant toute restauration. */
    fun begin(): StartPlan {
        val interrupted = store.interrupted()
        store.markRestoring()
        if (interrupted) {
            // L'onglet mémorisé est celui où le dernier démarrage est mort : on l'oublie.
            store.rememberTab(CHAT_TAB)
        }
        return StartPlan(
            interrupted = interrupted,
            chatTab = interrupted,
            dropDraftAttachments = interrupted,
        )
    }

    /** Le premier dessin a eu lieu : ce démarrage n'est plus « en cours de restauration ». */
    fun settled() = store.markSettled()

    /** L'onglet courant, pour le retrouver au prochain lancement. */
    fun noteTab(index: Int) {
        if (index >= 0) store.rememberTab(index)
    }

    /** L'onglet du dernier lancement — Chat quand aucun n'a été noté. */
    fun lastTab(): Int = store.lastTab().coerceAtLeast(CHAT_TAB)

    companion object {
        /** L'onglet Chat : le repli de tout démarrage en secours. */
        const val CHAT_TAB = 0

        fun with(context: Context): StartupGuard = StartupGuard(PrefsStartupStore(context))
    }
}

/** Le magasin réel : deux clés dans les préférences de l'app. */
private class PrefsStartupStore(context: Context) : StartupStore {

    private val prefs = context.getSharedPreferences("onyx-startup", Context.MODE_PRIVATE)

    override fun interrupted(): Boolean = prefs.getBoolean(KEY_RESTORING, false)

    override fun markRestoring() {
        // `commit`, pas `apply` : le drapeau doit être écrit **avant** le premier dessin, et
        // `apply` le laisserait en mémoire le temps d'un plantage.
        prefs.edit().putBoolean(KEY_RESTORING, true).commit()
    }

    override fun markSettled() {
        prefs.edit().putBoolean(KEY_RESTORING, false).apply()
    }

    override fun lastTab(): Int = prefs.getInt(KEY_LAST_TAB, StartupGuard.CHAT_TAB)

    override fun rememberTab(index: Int) {
        prefs.edit().putInt(KEY_LAST_TAB, index).apply()
    }

    private companion object {
        const val KEY_RESTORING = "restoring"
        const val KEY_LAST_TAB = "last_tab"
    }
}
