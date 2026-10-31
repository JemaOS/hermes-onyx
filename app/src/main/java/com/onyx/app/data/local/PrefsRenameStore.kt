package com.onyx.app.data.local

import android.content.Context
import android.content.SharedPreferences

/** [RenameStore] posé sur un fichier `SharedPreferences` du téléphone. */
class PrefsRenameStore(private val prefs: SharedPreferences) : RenameStore {

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun string(key: String): String? = prefs.getString(key, null)

    override fun boolean(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun stringSet(key: String): Set<String>? = prefs.getStringSet(key, null)

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}

/**
 * Le report de l'état local d'un profil renommé, posé sur les **trois** fichiers du téléphone :
 * les visages ([FaceKeys.FILE]), l'activité ([ActivityKeys.FILE]) et le journal des renommages
 * déjà reportés ([ProfileRenameLedger.FILE]).
 *
 * Les deux premiers sont les mêmes fichiers que lisent
 * [com.onyx.app.ui.bots.BotFaceStore] et [com.onyx.app.ui.bots.BotActivityStore] : la migration
 * écrit là où l'app lit.
 */
fun profileStateMigration(context: Context): ProfileStateMigration {
    val app = context.applicationContext
    fun file(name: String) = PrefsRenameStore(app.getSharedPreferences(name, Context.MODE_PRIVATE))
    return ProfileStateMigration(
        faces = file(FaceKeys.FILE),
        activity = file(ActivityKeys.FILE),
        ledger = ProfileRenameLedger(file(ProfileRenameLedger.FILE)),
    )
}
