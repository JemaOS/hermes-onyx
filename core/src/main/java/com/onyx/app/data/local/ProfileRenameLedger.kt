package com.onyx.app.data.local

/**
 * Le journal des renommages **déjà reportés**.
 *
 * C'est lui qui rend la migration *une seule fois* : une fois qu'un renommage `ancien=>courant` y
 * est écrit, il n'est plus jamais rejoué. Sans ce journal, un bot **réellement neuf** qui
 * reprendrait par hasard l'ancien nom (`ancien-nom`) hériterait de l'état du profil renommé — le
 * journal l'en empêche, pour toujours.
 *
 * La trace gardée est la paire « ancien=>courant » : c'est aussi la **traçabilité** demandée — le
 * nom d'avant survit dans les données migrées, même après que le serveur a cessé de le nommer.
 */
class ProfileRenameLedger(private val store: RenameStore) {

    /** Les renommages déjà reportés, sous la forme « ancien=>courant ». */
    fun applied(): Set<String> = store.stringSet(KEY).orEmpty()

    /** Note qu'un renommage a été reporté (une seule fois : l'appel est idempotent). */
    fun record(fact: String) {
        store.putStringSet(KEY, applied() + fact)
    }

    companion object {
        const val FILE = "onyx_profile_renames"
        const val KEY = "applied"
    }
}
