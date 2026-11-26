package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.serve.BotProfile

/** Le nom du profil principal de Onyx : lui seul donne le visage de la bulle (`BotFace.main`). */
internal const val OnyxProfile = "default"

/**
 * Qui parle dans la conversation : le nom à montrer, sous quel profil son visage se calcule, et le
 * visage choisi quand il y en a un.
 *
 * Le nom vient des données — le libellé du bot, ou le nom d'agent des Réglages pour Onyx — et ne
 * passe jamais par la localisation.
 */
data class ThreadSpeaker(
    /** Le nom de qui parle, tiré des données (jamais localisé). */
    val label: String,
    /** Le nom dont [com.onyx.app.ui.design.BotAvatar] tire son visage et sa photo (`BotProfile.name`). */
    val faceName: String,
    /** Le visage enregistré, prioritaire sur ce que le nom donnerait. */
    val faceCode: String?,
    /**
     * Le profil Hermes de ce fil, quand c'est le fil d'un bot (`BotRef.profile` de la référence) ; `null`
     * pour une conversation simple.
     */
    val botProfile: String? = null,
) {
    /**
     * Le « @handle » du bot, comme `BotRef.handle` : le profil, sauf le profil principal qui
     * s'appelle « hermes ». C'est ce que la seconde ligne de la barre du haut montre, précédé de
     * « @ » — une conversation simple n'en a pas.
     */
    val botHandle: String?
        get() = botProfile?.let { if (it == OnyxProfile) "hermes" else it }
}

/**
 * Le bot dont c'est la conversation, sinon Onyx — ce que le visage de la barre du haut montre.
 *
 * Le bot est déjà résolu par [com.onyx.app.ui.bots.botOfConversation] (le chat serveur du bot, ou
 * le titre de son chat) : la conversation ouverte est celle du bot, ou ce n'est celle d'aucun. Une
 * conversation simple — pas de bot lié — montre Onyx : son nom d'agent et son visage de bulle,
 * jamais un visage vide.
 *
 * L'ancien en-tête du fil (un visage de 64 dp et son nom en pastille, posé en tête des messages) a
 * été retiré : ce visage vit maintenant dans la barre du haut, où il ne défile pas.
 */
fun threadHeaderSpeaker(bot: BotProfile?, assistantName: String): ThreadSpeaker =
    if (bot == null) {
        ThreadSpeaker(label = assistantName, faceName = OnyxProfile, faceCode = null)
    } else {
        ThreadSpeaker(
            label = bot.label(assistantName),
            // Le profil principal porte le nom « default » : c'est lui, et lui seul, qui donne la
            // bulle de Onyx (`BotFace.forName("default")` renvoie `BotFace.main`).
            faceName = if (bot.isDefault || bot.name == OnyxProfile) OnyxProfile else bot.name,
            faceCode = bot.faceCode,
            botProfile = if (bot.isDefault || bot.name == OnyxProfile) OnyxProfile else bot.name,
        )
    }
