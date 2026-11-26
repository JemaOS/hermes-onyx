package com.onyx.app.voice

import java.text.Normalizer

/**
 * Le demi-duplex, l'endpointing et la garde anti-écho — les trois garde-fous du mode vocal.
 *
 * Sans eux, Onyx s'écoutait parler : la voix de synthèse, captée par le micro, était transcrite
 * comme une phrase de la personne puis renvoyée à l'agent. C'est un effet Larsen, et il ne
 * s'arrête jamais : « bonjour » → « Bonjour ! Comment puis-je t'aider ? » → « bonjour comment
 * puis-je t'aider aujourd'hui » → et ainsi de suite.
 *
 * Tout ce fichier est pur : ni micro, ni horloge murale, ni Android. Les décisions se testent donc
 * sur JVM (`HalfDuplexTest`), et [VoiceSession] ne fait que les brancher.
 */

/**
 * Le seuil de ressemblance à partir duquel ce qui a été entendu est pris pour la réponse qu'on
 * vient de lire. Au-delà, la phrase est jetée : c'est Onyx qui s'entend, pas quelqu'un qui parle.
 */
const val ECHO_SIMILARITY = 0.6f

/**
 * Le silence qui termine une écoute, en millisecondes. La personne s'arrête de parler, on attend
 * ce temps-là, puis la phrase part — une fois.
 *
 * **1,2 s, la valeur de la référence** (`SpeechRecognizer.silenceTimeout`). À 1,1 s, une hésitation
 * au milieu d'une phrase suffisait à la couper (« ça se bloque toujours en plein milieu d'une
 * phrase ») ; la référence exige en plus que la pièce soit calme depuis 0,6 × ce temps — voir
 * [EndpointWatcher], qui porte les deux conditions.
 */
const val DEFAULT_ENDPOINT_SILENCE_MS = 1200L

/** Il faut avoir entendu quelque chose avant qu'un silence puisse terminer l'écoute. */
const val ENDPOINT_MIN_SPEECH_MS = 300L

/**
 * La parole qu'il faut tenir par-dessus la réponse avant de la couper, en millisecondes.
 *
 * Sans seuil, une toux ou un mot de la réponse qui fuit suffisait : c'est ce qui rendait
 * l'interruption incontrôlable. 400 ms, c'est trois ou quatre syllabes — une phrase commencée.
 */
const val BARGE_IN_HOLD_MS = 400L

/** Ce qu'une phrase vaut pour la comparaison : minuscules, sans accents, sans ponctuation. */
fun normalizeForEcho(text: String): String {
    val flat = Normalizer.normalize(text.lowercase().replace('’', '\''), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return flat.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/** Les mots d'une phrase normalisée. */
fun echoTokens(text: String): List<String> =
    normalizeForEcho(text).split(' ').filter { it.isNotEmpty() }

/**
 * La part de mots que deux phrases ont en commun, de 0 à 1 : le rapport des mots communs sur tous
 * ceux des deux (indice de Jaccard).
 *
 * Une ressemblance, pas une égalité : « Bonjour ! Comment puis-je t'aider aujourd'hui ? » et
 * « bonjour comment puis-je t'aider aujourd'hui » sont la même phrase à la ponctuation près, et
 * c'est exactement ce que la transcription rend du texte lu.
 */
fun transcriptSimilarity(first: String, second: String): Float {
    val a = echoTokens(first).toSet()
    val b = echoTokens(second).toSet()
    if (a.isEmpty() || b.isEmpty()) return 0f
    val common = a.count { token -> b.any { it.matchesEchoToken(token) } }
    return common.toFloat() / (a.size + b.size - common)
}

/**
 * Deux mots du moteur de reconnaissance désignent-ils le même mot ?
 *
 * L'égalité ne suffit pas : le moteur écrit ce qu'il entend (« plus » pour « plutôt », « est » pour
 * « était », un pluriel perdu). Un préfixe commun de trois lettres suffit donc — c'est ce qui fait
 * qu'un **fragment** de la réponse entendu par le micro est reconnu pour elle, et pas seulement la
 * phrase entière. Sans cela, « je suis plus » (entendu du haut-parleur pendant la lecture de
 * « … Je suis plutôt à l'aise … ») passait pour une question et coupait la réponse en plein milieu.
 */
internal fun String.matchesEchoToken(other: String): Boolean {
    if (this == other) return true
    if (length < 3 || other.length < 3) return false
    return take(3) == other.take(3)
}

/**
 * Vrai quand [heard] n'est que l'écho de [lastReply], la dernière phrase que l'assistant a lue.
 *
 * La comparaison est une [transcriptSimilarity], et non une égalité : le moteur de reconnaissance
 * ne rend jamais exactement le texte lu (ponctuation, élisions, un mot avalé). Un mot isolé
 * (« bonjour ») qui se retrouve dans une longue réponse ne compte pas : il faut que la phrase
 * entendue soit **couverte** par la réponse, c'est-à-dire que presque tous ses mots y soient.
 *
 * C'est la garde qui casse la boucle : la réponse ne peut plus devenir la question suivante.
 */
fun isEchoOfReply(heard: String, lastReply: String, threshold: Float = ECHO_SIMILARITY): Boolean {
    if (heard.isBlank() || lastReply.isBlank()) return false
    val said = echoTokens(heard)
    val spoken = echoTokens(lastReply)
    if (said.isEmpty() || spoken.isEmpty()) return false

    // 1. Le cas de la boucle : le micro reprend un **morceau de la réponse en cours**, tel quel.
    //    Les mêmes mots, dans le même ordre, quelque part dans ce qui a été lu. Deux mots au
    //    moins — « bonjour » tout seul est une phrase comme une autre — et les mots se comparent
    //    par leur préfixe ([matchesEchoToken]) : le moteur écrit ce qu'il entend, pas ce qui a
    //    été lu.
    if (said.size >= 2 && said.size <= spoken.size) {
        for (start in 0..(spoken.size - said.size)) {
            val run = spoken.subList(start, start + said.size)
            if (run.indices.all { said[it].matchesEchoToken(run[it]) }) return true
        }
    }

    // 2. La phrase entendue est presque entièrement faite des mots de la réponse, même dans un
    //    autre ordre (le moteur réordonne parfois).
    val coveredIn = said.count { token -> spoken.any { it.matchesEchoToken(token) } }.toFloat() / said.size
    if (said.size >= 3 && coveredIn >= 0.9f) return true

    // 3. Sinon : la ressemblance des deux phrases, dans les deux sens.
    return transcriptSimilarity(heard, lastReply) >= threshold
}

/**
 * Faut-il rouvrir le micro pendant que la réponse se lit ?
 *
 * **Demi-duplex par défaut** : non. Le micro reste fermé pendant toute la lecture et ne rouvre
 * qu'après la fin (+ le court délai), parce que c'est le seul moyen sûr que la voix de synthèse ne
 * revienne pas par le micro. Le barge-in, lui, se demande (Réglages → Voix → « Parler pendant les
 * réponses ») : c'est le seul cas où le micro se rouvre pendant la lecture, et il est alors tenu
 * par le seuil de [BARGE_IN_HOLD_MS] et par [isEchoOfReply].
 *
 * [micBehindReply] vient de [talkOverAllowsMic] : le réglage **et** la route du moment.
 */
internal fun shouldReopenMicForSpeech(
    phase: VoicePhase,
    isListening: Boolean,
    listenAfterSpeech: Boolean = true,
    micBehindReply: Boolean = false,
): Boolean = listenAfterSpeech && micBehindReply && phase == VoicePhase.SPEAKING && !isListening

/**
 * « Parler pendant les réponses » : le réglage dit où, la route dit si on y est.
 *
 * Repris de `Settings.TalkOver` et de `VoiceSession.canBeTalkedOver()` de la référence, y compris
 * son test (« the mic stays shut where talking over is not on ») : **éteint** jamais ; **avec un
 * casque** seulement quand le son sort par un casque ; **partout** toujours (avec annulation
 * d'écho sur le haut-parleur).
 */
fun talkOverAllowsMic(talkOver: TalkOver, externalRoute: Boolean): Boolean = when (talkOver) {
    TalkOver.OFF -> false
    TalkOver.HEADPHONES -> externalRoute
    TalkOver.EVERYWHERE -> true
}

/**
 * Où « parler pendant les réponses » s'applique — les trois choix de la référence, et son défaut
 * (`.headphones`) : le micro ne s'ouvre derrière la réponse qu'avec un casque.
 */
enum class TalkOver(val id: String) {
    OFF("OFF"),
    HEADPHONES("HEADPHONES"),
    EVERYWHERE("EVERYWHERE"),
    ;

    companion object {
        val DEFAULT = HEADPHONES

        fun fromId(raw: String?): TalkOver = entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: DEFAULT

        /** L'ancien réglage booléen : vrai valait « partout », faux « jamais ». */
        fun fromLegacy(on: Boolean): TalkOver = if (on) EVERYWHERE else OFF
    }
}

/**
 * Le silence a-t-il assez duré pour terminer l'écoute ?
 *
 * [sinceLastChangeMs] est le temps écoulé depuis la dernière fois que la transcription a bougé ;
 * [sinceFirstSpeechMs], depuis la première chose entendue. Il faut avoir entendu quelque chose
 * ([ENDPOINT_MIN_SPEECH_MS]) avant qu'un silence puisse terminer quoi que ce soit — sinon un
 * silence au tout début fermerait l'écoute avant qu'on ait parlé.
 */
internal fun shouldEndBySilence(
    transcript: String,
    sinceLastChangeMs: Long,
    sinceFirstSpeechMs: Long,
    silenceMs: Long = DEFAULT_ENDPOINT_SILENCE_MS,
): Boolean = transcript.isNotBlank() &&
    sinceFirstSpeechMs >= ENDPOINT_MIN_SPEECH_MS &&
    sinceLastChangeMs >= silenceMs

/**
 * Faut-il envoyer la phrase entendue ?
 *
 * Une phrase ne part qu'une fois ([alreadySent] garde le tour), et jamais si c'est la réponse qu'on
 * vient de lire qui revient par le micro ([isEchoOfReply]).
 */
internal fun shouldSendHeardTranscript(
    transcript: String,
    lastReply: String,
    alreadySent: Boolean,
    threshold: Float = ECHO_SIMILARITY,
): Boolean = transcript.isNotBlank() &&
    !alreadySent &&
    !isEchoOfReply(transcript, lastReply, threshold)
