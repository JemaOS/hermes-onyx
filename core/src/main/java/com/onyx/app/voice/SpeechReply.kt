package com.onyx.app.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * **La lecture, derrière une interface** : le son du téléphone ([SpeechOutput]) ou un lecteur de
 * test qui tient la phase [VoicePhase.SPEAKING] le temps d'une réponse simulée.
 *
 * C'est le pendant de [SpeechInput] de l'autre côté du tour : sans lui, une boucle complète ne peut
 * pas être rejouée sans `TextToSpeech` — or un émulateur peut n'en avoir aucun, et le test doit
 * pouvoir prouver l'enchaînement « réponse → reprise de l'écoute » sans dépendre d'une voix de
 * synthèse.
 *
 * L'interface ne porte que ce que [VoiceSession] demande déjà au lecteur.
 */
interface SpeechReply {

    /** Vrai pendant qu'une phrase sort. */
    val isSpeaking: StateFlow<Boolean>

    /** Le niveau de la voix lue, de 0 à 1 : ce qui fait battre la bouche du visage qui parle. */
    val replyLevel: StateFlow<Float>

    /** Appelé quand la phrase est finie (ou refusée), pour clore la phase. */
    var onFinished: (() -> Unit)?

    /** Vrai quand la lecture sort par l'écouteur (`setEarRouting`). */
    val earRouting: Boolean

    /** La langue de lecture. */
    fun setLanguage(tag: String)

    /** La réponse se joue dans l'écouteur (capteur de proximité) ou revient au haut-parleur. */
    fun setEarRouting(atEar: Boolean)

    /** Lit [text]. [speaker] : la réplique d'un bot sort de **sa** voix. */
    fun speak(text: String, languageTag: String?, speaker: String?)

    /** Suspend la phrase en cours. */
    fun pause()

    /** Reprend la phrase suspendue ; faux quand il n'y a plus rien à reprendre. */
    fun resume(): Boolean

    /** Coupe la phrase en cours. */
    fun stop()

    /** Le mode vocal se ferme : le lecteur peut être détruit. */
    fun shutdown()
}

/**
 * Le lecteur Android derrière [SpeechReply].
 *
 * [SpeechOutput] garde son API publique — c'est par elle que l'écran règle la voix (vitesse,
 * serveurs, roster des bots) — et cette façade ne fait que **passer les arguments** : une fonction
 * qui redéfinit n'a pas le droit de porter des valeurs par défaut, or le lecteur, lui, doit en
 * garder pour ses autres appelants.
 *
 * `internal` : c'est la plomberie entre [SpeechOutput] et [VoiceSession], pas une API.
 */
internal class AndroidSpeechReply(val delegate: SpeechOutput) : SpeechReply {

    override val isSpeaking: StateFlow<Boolean> get() = delegate.isSpeaking

    override val replyLevel: StateFlow<Float> get() = delegate.replyLevel

    override var onFinished: (() -> Unit)?
        get() = delegate.onFinished
        set(value) {
            delegate.onFinished = value
        }

    override val earRouting: Boolean get() = delegate.earRouting

    override fun setLanguage(tag: String) = delegate.setLanguage(tag)

    override fun setEarRouting(atEar: Boolean) = delegate.setEarRouting(atEar)

    override fun speak(text: String, languageTag: String?, speaker: String?) =
        delegate.speak(text, languageTag, speaker)

    override fun pause() = delegate.pause()

    override fun resume(): Boolean = delegate.resume()

    override fun stop() = delegate.stop()

    override fun shutdown() = delegate.shutdown()
}
