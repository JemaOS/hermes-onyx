package com.onyx.app.voice

/**
 * Le repli quand le moteur de reconnaissance refuse de démarrer (code 5 notamment).
 *
 * Le moteur Android rend `ERROR_CLIENT` (code 5) quand on lui demande une session alors que la
 * précédente n'a pas fini de rendre le micro — et cette fenêtre est inévitable: elle s'ouvre
 * après chaque phrase, à chaque relance du mains-libres et à chaque réouverture derrière une
 * réponse. Relancer dans la foulée reproduit le refus, et le refus relançait: c'est la boucle
 * « ça bug tout le temps » (le bandeau « nouvelle tentative » qui revient sans fin).
 *
 * La règle est donc celle du modèle: **une** reprise, espacée, puis on s'arrête avec un
 * message qui dit ce qui s'est passé et quoi faire. Un échec qui se répète toutes les secondes
 * n'est pas un repli.
 *
 * Tout ce fichier est pur (ni micro, ni horloge, ni Android): la politique se teste sur JVM, et
 * [SpeechEngine] ne fait que la suivre.
 */

/**
 * Le nombre de départs pour une même demande d'écoute: l'essai, puis **une** reprise espacée.
 * Au-delà, on s'arrête: c'est la répétition qui fait le défaut.
 */
const val MAX_ENGINE_ATTEMPTS = 2

/**
 * Le délai de la reprise, en millisecondes. Plus long que le délai ordinaire de relance
 * ([SpeechEngine.RESTART_DELAY_MS], 250 ms): le moteur doit avoir réellement rendu le micro,
 * sinon il refuse encore.
 */
const val ENGINE_RETRY_DELAY_MS = 1500L

/** Reste-t-il une reprise après [attempts] refus consécutifs du moteur ? */
internal fun shouldRetryEngine(attempts: Int, maxAttempts: Int = MAX_ENGINE_ATTEMPTS): Boolean =
    attempts < maxAttempts

/**
 * Un refus passager: ça vaut la reprise espacée. Les autres causes (permission, langue non prise
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
 * Le message d'un échec **définitif**: il dit ce qui s'est passé et ce qu'il reste à faire, et
 * **ne promet aucune reprise** — un bandeau qui annonce « nouvelle tentative » alors que plus rien
 * ne réessaie est le défaut d'origine.
 */
internal fun engineFailureMessage(error: Int): String =
    if (error == android.speech.SpeechRecognizer.ERROR_CLIENT) {
        "The recogniser could not start (code 5). Tap the orb to listen again."
    } else {
        "The recogniser stopped and listening is off. Tap the orb to listen again."
    }
