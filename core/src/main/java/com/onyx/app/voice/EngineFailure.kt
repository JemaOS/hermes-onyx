package com.onyx.app.voice

/**
 * Le repli quand le moteur de reconnaissance refuse de démarrer (code 5 notamment) — et la
 * **table des codes d'arrêt** du moteur, un par un, nommés, avec la décision de chacun.
 *
 * Le moteur Android rend `ERROR_CLIENT` (code 5) quand on lui demande une session alors que la
 * précédente n'a pas fini de rendre le micro — et cette fenêtre est inévitable : elle s'ouvre
 * après chaque phrase, à chaque relance du mains-libres et à chaque réouverture derrière une
 * réponse. Relancer dans la foulée reproduit le refus, et le refus relançait : c'est la boucle
 * « ça bug tout le temps » (le bandeau « nouvelle tentative » qui revient sans fin).
 *
 * La règle est donc celle de la référence : **une** reprise, espacée, puis on s'arrête avec un
 * message qui dit ce qui s'est passé et quoi faire. Un échec qui se répète toutes les secondes
 * n'est pas un repli.
 *
 * La seconde cause, celle du téléphone du propriétaire : le moteur **hors ligne** ne sait pas
 * servir la langue (`13 language-unavailable`, `12 language-not-supported`) et, n'ayant pas de
 * serveur, avoue ensuite `11 server-disconnected`. Réessayer hors ligne ne répare rien : ce n'est
 * pas une reprise qu'il faut, c'est une **bascule** en ligne. Les deux décisions vivent ici, et
 * nulle part ailleurs.
 *
 * Tout ce fichier est pur (ni micro, ni horloge, ni Android) : la politique se teste sur JVM, et
 * [SpeechEngine] ne fait que la suivre.
 */

/**
 * Le nombre de départs pour une même demande d'écoute : l'essai, puis **une** reprise espacée.
 * Au-delà, on s'arrête : c'est la répétition qui fait le défaut.
 */
const val MAX_ENGINE_ATTEMPTS = 3

/**
 * Le délai de base de la reprise, en millisecondes. Plus long que le délai ordinaire de relance
 * ([SpeechEngine.RESTART_DELAY_MS], 250 ms) : le moteur doit avoir réellement rendu le micro,
 * sinon il refuse encore.
 */
const val ENGINE_RETRY_DELAY_MS = 1500L

/**
 * Le délai de la reprise après [attempts] refus d'affilée : **il s'allonge** (1,5 s, puis 3 s).
 *
 * Un moteur qui refuse une fois se rétablit vite ; un moteur qui refuse en rafale a besoin qu'on
 * lui laisse le temps. Relancer à cadence fixe reproduisait le refus — c'est la boucle signalée.
 */
internal fun engineRetryDelayMs(attempts: Int): Long =
    ENGINE_RETRY_DELAY_MS * attempts.coerceAtLeast(1)

/** Reste-t-il une reprise après [attempts] refus consécutifs du moteur ? */
internal fun shouldRetryEngine(attempts: Int, maxAttempts: Int = MAX_ENGINE_ATTEMPTS): Boolean =
    attempts < maxAttempts

/**
 * Un refus passager : ça vaut la reprise espacée. Les autres causes (permission, langue non prise
 * en charge, appareil sans moteur) ne se réparent pas en réessayant, et sont montrées tout de suite.
 */
internal fun isTransientEngineFailure(error: Int): Boolean =
    error == android.speech.SpeechRecognizer.ERROR_CLIENT ||
        error == android.speech.SpeechRecognizer.ERROR_SERVER ||
        error == android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
        error == android.speech.SpeechRecognizer.ERROR_NETWORK ||
        error == android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
        error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
        error == android.speech.SpeechRecognizer.ERROR_TOO_MANY_REQUESTS

/**
 * Une **fin de session normale**, jamais une panne : le moteur Android termine sa session tout
 * seul au silence (`7 no-match`) ou à la durée maximale (`6 speech-timeout`). L'écoute doit se
 * rouvrir dans la foulée, et ces deux codes ne comptent pas comme des échecs.
 */
internal fun isEngineSilence(error: Int): Boolean =
    error == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
        error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT

/**
 * Le moteur **hors ligne** a-t-il avoué ne pas pouvoir servir cette langue ?
 *
 * C'est la signature relevée sur le téléphone du propriétaire :
 * `13 language-unavailable` (aucun pack hors ligne pour le français), puis
 * `11 server-disconnected` (le moteur hors ligne n'a pas de serveur pour prendre la suite).
 * `12 language-not-supported`, `4 server`, `2 network`, `1 network-timeout` et `5 client` disent la
 * même chose par un autre code : dans les sept cas, réessayer **hors ligne** ne réparera rien.
 */
internal fun offlineEngineExplainsFailure(error: Int): Boolean =
    error == android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
        error == android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
        error == android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
        error == android.speech.SpeechRecognizer.ERROR_SERVER ||
        error == android.speech.SpeechRecognizer.ERROR_NETWORK ||
        error == android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
        error == android.speech.SpeechRecognizer.ERROR_CLIENT

/** Ce qu'il faut faire après un arrêt du moteur de reconnaissance : la décision, et rien d'autre. */
enum class EngineStopDecision {
    /** Fin de session normale : l'écoute se **rouvre**, sans rien dire (pas un échec). */
    RESUME,

    /** Refus passager : une reprise **espacée** (voir [engineRetryDelayMs]), jamais en rafale. */
    RETRY,

    /**
     * Le moteur **hors ligne** n'a pas su servir la langue : on repart **en ligne**, tout de suite
     * et sans rien redemander à la personne. Une seule bascule par écoute — jamais de va-et-vient.
     */
    GO_ONLINE,

    /** On s'arrête, et on **le dit** : message nommé à l'écran, plus « Réessayer ». */
    STOP,
}

/**
 * La **table des codes d'arrêt**, un par un :
 *
 * | code | nom | hors ligne demandé | décision |
 * |------|-----|--------------------|----------|
 * | 7 | no-match | — | `RESUME` (fin normale, ne compte pas) |
 * | 6 | speech-timeout | — | `RESUME` (fin normale, ne compte pas) |
 * | 13 | language-unavailable | oui | `GO_ONLINE` |
 * | 13 | language-unavailable | non | `STOP`, dite nommément (langue absente de l'appareil) |
 * | 12 | language-not-supported | oui | `GO_ONLINE` |
 * | 12 | language-not-supported | non | `STOP`, dite nommément |
 * | 11 | server-disconnected | oui | `GO_ONLINE` |
 * | 11 | server-disconnected | non | `RETRY` espacée, puis `STOP` au plafond |
 * | 5 | client | oui | `GO_ONLINE` |
 * | 5 | client | non | `RETRY` espacée, puis `STOP` au plafond |
 * | 9 | insufficient-permissions | — | `STOP` (rien ne se répare en réessayant) |
 * | 4/2/1 | server / network / network-timeout | oui | `GO_ONLINE` |
 * | 4/2/1 | server / network / network-timeout | non | `RETRY` espacée, puis `STOP` au plafond |
 * | 8/10 | recogniser-busy / too-many-requests | — | `RETRY` espacée, puis `STOP` au plafond |
 * | autres | (3 audio, 14 cannot-check-support, inconnu) | — | `STOP`, dite nommément |
 *
 * Aucune ligne ne boucle : la reprise est bornée par [MAX_ENGINE_ATTEMPTS], la bascule est
 * **définitive** pour l'écoute en cours, et tout ce qui s'arrête passe par `STOP` — donc par une
 * phrase à l'écran.
 */
internal fun engineStopDecision(
    error: Int,
    preferOffline: Boolean,
    attempts: Int,
    maxAttempts: Int = MAX_ENGINE_ATTEMPTS,
): EngineStopDecision = when {
    // Une fin de session normale n'est pas une erreur : l'écoute se rouvre.
    isEngineSilence(error) -> EngineStopDecision.RESUME
    // Le hors ligne demandé n'a pas su servir la langue : bascule, une fois, jamais redemandée.
    preferOffline && offlineEngineExplainsFailure(error) -> EngineStopDecision.GO_ONLINE
    // 9 (insufficient-permissions) : la permission ne se donne pas en réessayant — dit tout de suite.
    error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> EngineStopDecision.STOP
    // Le reste : une reprise espacée tant qu'il reste du budget, puis l'arrêt dit.
    else -> onlineDecision(error, attempts, maxAttempts)
}

/** La décision **en ligne** : reprise tant qu'il en reste une et que le refus est passager. */
private fun onlineDecision(error: Int, attempts: Int, maxAttempts: Int): EngineStopDecision =
    if (isTransientEngineFailure(error) && shouldRetryEngine(attempts, maxAttempts)) {
        EngineStopDecision.RETRY
    } else {
        EngineStopDecision.STOP
    }

/**
 * Le message **nommé** d'un code d'arrêt : la cause en une phrase, jamais un code brut seul.
 *
 * Les formulations disent l'état, jamais une promesse : c'est [engineMessage] qui décide si ce
 * message paraît, et un arrêt **définitif** ne doit pas laisser croire qu'une reprise s'en vient.
 */
internal fun engineNamedFailureMessage(error: Int): String = when (error) {
    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "The microphone is not allowed for this app."
    android.speech.SpeechRecognizer.ERROR_NETWORK,
    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "The speech recogniser needs a network for this language."
    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy."
    android.speech.SpeechRecognizer.ERROR_SERVER,
    android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> "The speech service disconnected or failed."
    android.speech.SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
        "The speech service is receiving too many requests."
    android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        "Speech recognition is not available in this language on this device."
    android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        "This language is not supported by the speech recogniser."
    android.speech.SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
        "Onyx cannot check speech recognition support on this device."
    android.speech.SpeechRecognizer.ERROR_CLIENT ->
        engineFailureMessage(android.speech.SpeechRecognizer.ERROR_CLIENT)
    else -> "The recogniser stopped (code $error)."
}

/**
 * Le message à mettre à l'écran — **selon la décision**, et rien d'autre.
 *
 * `null` veut dire « rien à dire » : une fin de session normale ([EngineStopDecision.RESUME]) se
 * rouvre en silence, une reprise espacée ([EngineStopDecision.RETRY]) et une bascule en ligne
 * ([EngineStopDecision.GO_ONLINE]) **agissent**, elles n'annoncent rien.
 *
 * Un `STOP` porte **toujours** un message : nommé quand le code a une cause à comprendre
 * (permission, langue, moteur occupé), celui du repli quand c'était un refus passager — et dans les
 * deux cas **sans la moindre promesse de reprise**, puisque plus rien ne réessaie. C'est la règle
 * d'origine : « un bandeau qui annonce “nouvelle tentative” alors que plus rien ne réessaie ».
 */
internal fun engineMessage(error: Int, decision: EngineStopDecision): String? = when (decision) {
    EngineStopDecision.RESUME,
    EngineStopDecision.RETRY,
    EngineStopDecision.GO_ONLINE,
    -> null

    EngineStopDecision.STOP ->
        if (isTransientEngineFailure(error)) engineFailureMessage(error) else engineNamedFailureMessage(error)
}

/**
 * Le message d'un échec **définitif** : il dit ce qui s'est passé et ce qu'il reste à faire, et
 * **ne promet aucune reprise** — un bandeau qui annonce « nouvelle tentative » alors que plus rien
 * ne réessaie est le défaut d'origine.
 */
internal fun engineFailureMessage(error: Int): String =
    if (error == android.speech.SpeechRecognizer.ERROR_CLIENT) {
        "The recogniser could not start (code 5). Tap the orb to listen again."
    } else {
        "The recogniser stopped and listening is off. Tap the orb to listen again."
    }
