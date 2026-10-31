package com.onyx.app.voice

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Le repli de la voix : quand le serveur de synthèse ne parle pas, l'appareil prend le relais — et
 * la rangée des Réglages n'affiche jamais l'erreur brute, seulement un état court et un pied de
 * section.
 *
 * Toute la décision est ici, pure et vérifiable sans appareil : le `TextToSpeech` d'Android vit
 * derrière [DeviceVoice], et les voix distantes derrière `HermesSpeechClient`/`KokoroSpeechClient`.
 */

/**
 * Vrai si [error] justifie le repli sur la voix de l'appareil : n'importe quel échec du serveur —
 * HTTP 400/403/5xx, délai dépassé, serveur injoignable, flux coupé, réponse illisible — mais jamais
 * une annulation, qui est un choix de l'utilisateur et non une panne.
 */
fun shouldFallBackToDevice(error: Throwable): Boolean = error !is CancellationException

/** Ce qu'un essai de voix a donné, une fois le repli appareil éventuel appliqué. */
sealed interface VoiceTrialOutcome {

    /** Le serveur a parlé. [provider] le nomme, ou `null` s'il ne l'a pas dit. */
    data class Server(val provider: String?) : VoiceTrialOutcome

    /** Le serveur a échoué et la voix de l'appareil a prononcé la phrase à sa place. */
    data class Device(val serverDetail: String) : VoiceTrialOutcome

    /** Personne n'a parlé : le serveur a échoué et aucun moteur local n'est là pour le relayer. */
    data class Silent(val detail: String) : VoiceTrialOutcome
}

/** Le détail d'un échec : le message lisible d'un [TransportError], sinon le sien, sinon son type. */
fun voiceServerDetail(error: Throwable, locale: Locale = OnyxLanguage.preferred): String =
    (error as? TransportError)?.userMessage(locale)
        ?: error.message?.takeIf { it.isNotBlank() }
        ?: error.javaClass.simpleName

/**
 * L'issue d'un échec du serveur : [VoiceTrialOutcome.Device] quand l'appareil peut parler,
 * [VoiceTrialOutcome.Silent] sinon — auquel cas le pied de section le dit clairement plutôt que de
 * laisser l'utilisateur devant un silence inexpliqué.
 */
fun voiceTrialOnFailure(
    error: Throwable,
    deviceAvailable: Boolean,
    locale: Locale = OnyxLanguage.preferred,
): VoiceTrialOutcome =
    if (deviceAvailable) {
        VoiceTrialOutcome.Device(voiceServerDetail(error, locale))
    } else {
        VoiceTrialOutcome.Silent(voiceServerDetail(error, locale))
    }

/** L'état court de la rangée : une valeur d'une ligne, jamais le détail de l'erreur. */
enum class VoiceTrialValue { NONE, WORKING, SPOKEN, FAILED }

/** L'état court correspondant à [outcome] — `null` au repos, avant tout essai. */
fun voiceTrialValue(outcome: VoiceTrialOutcome?): VoiceTrialValue = when (outcome) {
    null -> VoiceTrialValue.NONE
    is VoiceTrialOutcome.Server, is VoiceTrialOutcome.Device -> VoiceTrialValue.SPOKEN
    is VoiceTrialOutcome.Silent -> VoiceTrialValue.FAILED
}

/** Le libellé court de la rangée, localisé. Le ✓ des états réussis n'a pas de traduction. */
fun voiceTrialShortLabel(value: VoiceTrialValue, locale: Locale = OnyxLanguage.preferred): String =
    when (value) {
        VoiceTrialValue.NONE -> ""
        VoiceTrialValue.WORKING -> OnyxLocalization.translate("Working…", locale)
        VoiceTrialValue.SPOKEN -> "✓"
        VoiceTrialValue.FAILED -> OnyxLocalization.translate("Failed", locale)
    }

/**
 * Le pied de section : ce que le ✓ ne dit pas — le détail d'un échec serveur, la voix de l'appareil
 * qui a relayé le Dashboard, ou le provider qui a parlé. `null` quand il n'y a rien à expliquer.
 */
fun voiceTrialFootnote(outcome: VoiceTrialOutcome?, locale: Locale = OnyxLanguage.preferred): String? =
    when (outcome) {
        null -> null
        is VoiceTrialOutcome.Server -> outcome.provider
            ?.takeIf { it.isNotBlank() }
            ?.let {
                OnyxLocalization.format(
                    "Spoken by %1\$s",
                    OnyxLocalization.translate(hermesProviderName(it), locale),
                    locale = locale,
                )
            }
        is VoiceTrialOutcome.Device -> OnyxLocalization.format(
            "The Dashboard couldn't speak, so the on-device voice read it. %1\$s",
            outcome.serverDetail,
            locale = locale,
        )
        is VoiceTrialOutcome.Silent -> OnyxLocalization.format(
            "This device has no voice to fall back on. %1\$s",
            outcome.detail,
            locale = locale,
        )
    }

/** Vrai si le pied décrit un échec : l'écran le colore alors en rouge, comme un pied d'alerte. */
fun voiceTrialFootnoteIsError(outcome: VoiceTrialOutcome?): Boolean =
    outcome is VoiceTrialOutcome.Silent
