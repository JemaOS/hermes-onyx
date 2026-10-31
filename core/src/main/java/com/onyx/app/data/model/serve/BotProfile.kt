package com.onyx.app.data.model.serve

/**
 * Un bot, tel que Hermes les connaît : un profil, avec sa conversation propre.
 *
 * Miroir de `HermesBot` (Echo/Services/Transport/ServerBots:6). Le nom du profil est ce que
 * toutes les routes nomment ; le titre et le visage viennent de `ui_meta`.
 */
data class BotProfile(
    val name: String,
    val displayName: String? = null,
    val title: String? = null,
    val description: String = "",
    val isDefault: Boolean = false,
    val model: String? = null,
    val isHidden: Boolean = false,
    /**
     * Le bot est **épinglé en haut** de la section (« Pin to top »).
     *
     * C'est le réglage de la **référence**, écrit dans le même `ui_meta` que la visibilité
     * (`hermes-bots.pinned`) : Hermes Desktop le voit, comme il voit `hidden`. Le choix fait sur ce
     * téléphone prime ([BotFaceStore.pinOverride]) — c'est ce qui le garde hors ligne.
     */
    val pinned: Boolean = false,
    /** La révision d'Hermes pour les réglages de bot de ce profil : une écriture la dit attendue. */
    val setUpRevision: Int = 0,
    val setUp: Boolean = false,
    val chatId: String? = null,
    /**
     * Ajouté ici même, sans Dashboard pour lister le serveur (`Add Bot` de l'API Hermes) : ses
     * réglages et son visage sont ceux du Dashboard, et il se retire de Onyx — comme le
     * `HermesBot.viaAPI` de la référence, dont le menu dépend (`botMenu`).
     */
    val viaAPI: Boolean = false,
    val preview: String? = null,
    val lastActive: Long? = null,
    val hasPicture: Boolean = false,
    val faceCode: String? = null,
    /** Les noms de profil qu'il a portés avant un renommage : une ancienne poignée le retrouve. */
    val previousNames: List<String> = emptyList(),
    /**
     * Quand un worker du bot (une carte kanban qu'on lui a donnée) s'est fait entendre pour la
     * dernière fois (`worker_session`), en millisecondes : un bot dont un worker s'est fait entendre
     * récemment est « au travail » (`ChatActivity.workerLive`, 150 s côté référence).
     */
    val workerActiveMillis: Long? = null,
) {
    /** Ce qu'il est appelé : son titre de bot, sinon le nom de son profil. */
    fun label(mainName: String = "Onyx"): String =
        title?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: if (isDefault) mainName else name

    /** La poignée `@` que Hermes connaît : le nom du profil, « hermes » pour le principal. */
    val handle: String get() = if (isDefault) "hermes" else name
}
