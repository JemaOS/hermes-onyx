package com.onyx.app.data.local

import android.content.Context
import android.content.SharedPreferences

/** [RenameStore] backed by a `SharedPreferences` file of the phone. */
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
 * The carry-over of a renamed profile's local state, backed by the phone's **three** files:
 * the faces ([FaceKeys.FILE]), the activity ([ActivityKeys.FILE]) and the log of renames
 * already carried over ([ProfileRenameLedger.FILE]).
 *
 * The first two are the same files that
 * [com.onyx.app.ui.bots.BotFaceStore] and [com.onyx.app.ui.bots.BotActivityStore] read: the migration
 * writes where the app reads.
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
