package com.onyx.app.voice

/**
 * La voix de l'appareil, derrière laquelle vit le `TextToSpeech` d'Android.
 *
 * C'est le repli promis par le pied de section des Réglages: quand le serveur ne peut pas parler,
 * c'est ce moteur-ci qui prononce la phrase. L'interface existe pour que le repli se vérifie sans
 * appareil ([AndroidDeviceVoice] étant la seule implémentation réelle).
 */
interface DeviceVoice {

    /**
     * Vrai quand un moteur peut parler. Un moteur encore en cours d'initialisation répond vrai:
     * la phrase est mise en attente et part dès qu'il est prêt, plutôt que d'annoncer à tort
     * qu'aucune voix n'existe.
     */
    val isAvailable: Boolean

    /**
     * Prononce [text] dans [languageTag]. [onDone] reçoit `true` quand la phrase a été dite, `false`
     * si le moteur est absent ou l'a refusée — c'est ce qui permet à l'écran de dire « Échec » au
     * lieu d'un silence.
     */
    fun speak(text: String, languageTag: String, onDone: (Boolean) -> Unit)

    /** Coupe la phrase en cours; aucun callback n'est rendu, l'appelant a déjà changé d'état. */
    fun stop()

    fun release()
}
