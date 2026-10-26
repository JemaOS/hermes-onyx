package com.onyx.app.voice

/**
 * La fin d'écoute: quand la personne a fini de parler, et pas avant.
 *
 * Le défaut signalé: « ça se bloque toujours en plein milieu d'une phrase » — l'énoncé était coupé
 * au milieu parce que la transcription s'arrêtait de bouger un instant (une hésitation, une
 * respiration) et que le minuteur se contentait de ça. Le modèle exige **deux** choses
 * (`SpeechRecognizer.startEndpointing`):
 *
 * 1. la transcription **stable** depuis [DEFAULT_ENDPOINT_SILENCE_MS] (1,2 s), et
 * 2. la pièce **calme** depuis 0,6 × ce temps (0,72 s): si le micro entend encore une voix —
 * même sans nouveau mot reconnu — la personne parle toujours, et l'énoncé continue.
 *
 * Le niveau n'est pas comparé à un seuil absolu: il est comparé au **plancher de bruit de la
 * pièce**, qui suit l'endroit (le bruit d'un micro de téléphone varie de 20 dB d'un lieu à l'autre).
 * Le plancher tombe à ce qui est plus calme et remonte lentement.
 *
 * Et pour qu'une phrase qui n'en finit jamais se termine quand même, il y a un plafond
 * ([MAX_UTTERANCE_MS], les 45 s du modèle): sans lui, une pièce bruyante empêcherait tout
 * envoi. C'est ce qui rend l'attente sûre.
 *
 * Classe pure: ni micro, ni horloge, ni Android — éprouvable sur JVM.
 */
class EndpointWatcher(
    /** La transcription doit être stable depuis ce temps-là (1,2 s chez le modèle). */
    val silenceMs: Long = DEFAULT_ENDPOINT_SILENCE_MS,
    /** Et la pièce calme depuis 0,6 × ce temps (0,72 s). */
    val quietMs: Long = (DEFAULT_ENDPOINT_SILENCE_MS * ENDPOINT_QUIET_FRACTION).toLong(),
    /** Le plafond d'un énoncé, en millisecondes: au-delà, on termine quoi qu'il arrive. */
    val maxUtteranceMs: Long = MAX_UTTERANCE_MS,
    /** De combien le niveau doit dépasser le plancher pour compter comme une voix. */
    val margin: Float = ENDPOINT_SPEECH_MARGIN,
    /** Ce qu'une voix atteint au minimum, quelle que soit la pièce: le plancher bas. */
    val minimumLevel: Float = ENDPOINT_MINIMUM_LEVEL,
) {
    private var floor: Float = 1f
    private var lastVoiceAt: Long = 0L
    private var startedAt: Long = 0L

    /** Une écoute commence: le plancher repart du plus haut et se posera sur la pièce. */
    fun start(now: Long) {
        floor = 1f
        lastVoiceAt = now
        startedAt = now
    }

    /**
     * Le niveau du micro ([SpeechState.level], de 0 à 1) et sa durée. Chaque mesure fait descendre le
     * plancher vers elle, et remonte le « on vient d'entendre une voix » quand elle dépasse le
     * plancher du lieu.
     */
    fun level(level: Float, seconds: Double, now: Long) {
        // Le plancher descend tout de suite, et remonte de 4 % par seconde au plus: le bruit de la
        // pièce, pas les mots.
        floor = minOf(level, floor + (ENDPOINT_FLOOR_RISE_PER_SECOND * seconds).toFloat())
        val speaking = level > floor + margin && level > minimumLevel
        if (speaking) lastVoiceAt = now
    }

    /** Depuis combien de temps la voix s'est tue — ce que le modèle appelle `quietFor`. */
    fun quietFor(now: Long): Long = now - lastVoiceAt

    /**
     * L'énoncé est-il terminé ? La transcription stable [silenceMs] **et** la pièce calme [quietMs] —
     * ou le plafond atteint.
     */
    fun shouldEnd(
        transcript: String,
        sinceLastChangeMs: Long,
        sinceFirstSpeechMs: Long,
        now: Long,
    ): Boolean {
        if (sinceFirstSpeechMs >= maxUtteranceMs) return true
        return shouldEndBySilence(transcript, sinceLastChangeMs, sinceFirstSpeechMs, silenceMs) &&
            quietFor(now) >= quietMs
    }

    /** Le temps écoulé depuis le début de l'écoute: `ranFor` du modèle. */
    fun ranFor(now: Long): Long = now - startedAt
}

/** La part du silence qu'il faut avoir passée sans voix: 0,6 chez le modèle. */
const val ENDPOINT_QUIET_FRACTION = 0.6f

/** Le plafond d'un énoncé: les 45 s du modèle, pour qu'une pièce bruyante n'empêche rien. */
const val MAX_UTTERANCE_MS = 45_000L

/** De combien le niveau doit dépasser le plancher de la pièce pour compter comme une voix. */
const val ENDPOINT_SPEECH_MARGIN = 0.12f

/** Ce qu'une voix atteint au minimum, quelle que soit la pièce. */
const val ENDPOINT_MINIMUM_LEVEL = 0.15f

/** De combien le plancher de bruit peut remonter par seconde. */
const val ENDPOINT_FLOOR_RISE_PER_SECOND = 0.04f
