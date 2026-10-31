package com.onyx.app.data.local

/**
 * Le peu qu'un magasin clé-valeur doit offrir pour **reporter l'état d'un profil renommé** sur son
 * nom courant.
 *
 * L'app garde une partie de son état local indexé par **nom de profil** : le visage et la
 * visibilité d'un bot (`onyx_bot_faces`), l'activité vue et l'ordre de la section
 * (`onyx_bot_activity`). Un renommage côté serveur (`ancien-nom` → `nom-courant`) laisse donc ces
 * entrées sous l'*ancien* nom : elles deviennent orphelines et le profil renommé paraît neuf.
 *
 * `SharedPreferences` s'y prête tel quel ; un test s'en donne une copie en mémoire, sans Android.
 */
interface RenameStore {
    fun contains(key: String): Boolean
    fun string(key: String): String?
    fun boolean(key: String, fallback: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun stringSet(key: String): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
}

/**
 * Les clés du visage et de la visibilité d'un bot, **indexées par nom de profil** (ce que
 * [com.onyx.app.ui.bots.BotFaceStore] écrit, dans `onyx_bot_faces`).
 *
 * Une seule source de vérité : le report d'un renommage lit exactement ces clés.
 */
object FaceKeys {
    const val FILE = "onyx_bot_faces"
    const val API_BOTS = "api_bots"
    const val SEPARATOR = "\u001F"

    /** Le visage : la clé est le nom du profil lui-même. */
    fun face(bot: String): String = bot

    fun title(bot: String): String = "title_$bot"
    fun about(bot: String): String = "about_$bot"
    fun model(bot: String): String = "model_$bot"
    fun shown(bot: String): String = "shown_$bot"

    /** « Hidden Bots » : un choix de visibilité fait ici, qui prime sur le `ui_meta` du serveur. */
    fun hideOverride(bot: String): String = "force_hidden_$bot"

    /** « Pin to top » : un choix d'épinglage fait ici, qui prime sur le `ui_meta` du serveur. */
    fun pinOverride(bot: String): String = "force_pinned_$bot"
}

/**
 * Les clés de l'activité vue et de l'ordre de la section, par connexion (ce que
 * [com.onyx.app.ui.bots.BotActivityStore] écrit, dans `onyx_bot_activity`).
 *
 * Les identifiants de ligne sont ceux de `BotListItem` : `bot:<profil>` et `room:<salle>`.
 */
object ActivityKeys {
    const val FILE = "onyx_bot_activity"
    const val SEPARATOR = "\u001F"

    fun seen(server: String, item: String): String = "seen|$server|$item"

    fun order(server: String): String = "order|$server"

    /** L'identifiant de ligne d'un bot : le même que `BotListItem.Bot.id`. */
    fun botItem(profile: String): String = "bot:$profile"
}
