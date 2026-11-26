package com.onyx.app.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * **L'écoute, derrière une interface** — le point de découpe qui rend la chaîne vocale éprouvable
 * sans micro.
 *
 * Le défaut rapporté (« je ne peux pas parler, il ne capte pas ma voix ») se joue **entre le micro et
 * le moteur** : tant que ce tronçon était un `SpeechRecognizer` construit au fond de
 * [VoiceSession], rien de la boucle ne pouvait être rejoué sans un vrai appareil — et un émulateur
 * n'a pas de micro. L'interface est le seul changement de fond : deux implémentations la remplissent,
 *
 *  - [SpeechEngine] — le **moteur réel**, le reconnaisseur de l'appareil (ce qui tourne sur le
 *    téléphone de la personne) ;
 *  - [ScenarioSpeechInput] — le **moteur de test**, alimenté par un **fichier audio** (un WAV
 *    français livré dans les ressources), qui rejoue un scénario silence → phrase → silence avec un
 *    niveau qui bouge. Il permet de faire tourner **toute** la boucle (écoute, fin de parole, envoi,
 *    réflexion, réponse, reprise, arrêts, plafond d'échecs) sur la JVM **et** dans un build de
 *    débogage piloté depuis l'émulateur.
 *
 * L'interface ne dit rien de plus que ce que [VoiceSession] consomme déjà du moteur : son état, sa
 * disponibilité, ses compteurs d'échecs et ses quatre gestes de vie (`start`/`stop`/`cancel`/
 * `release`).
 */
interface SpeechInput {

    /** Ce que le moteur entend et où il en est : niveau, transcription, panne. */
    val state: StateFlow<SpeechState>

    /**
     * « Reconnaître hors ligne » — un **souhait** de la personne, faux par défaut (la
     * reconnaissance se fait en ligne). Le moteur réel peut basculer tout seul en ligne
     * (`language-unavailable`) ; un moteur rejoué l'ignore.
     */
    var offlineWanted: Boolean

    /** Le moteur a-t-il **répondu** au moins une fois depuis la demande neuve ? */
    val answered: Boolean

    /** Les mesures de niveau reçues depuis le dernier départ : les « trames » du micro. */
    val frameCount: Int

    /** Reste-t-il une reprise après les refus consécutifs déjà comptés ? */
    val canRetry: Boolean

    /** Les refus consécutifs du moteur. */
    val attempts: Int

    /** Un moteur de reconnaissance existe-t-il sur cet appareil ? */
    val isAvailable: Boolean

    /** Le mode **effectif** du prochain départ (hors ligne demandé, moins une bascule déjà faite). */
    val prefersOffline: Boolean

    /** La langue demandée au moteur — un moteur muet est parfois une affaire de langue. */
    val requestedLocale: String

    /** Appelé quand le moteur rend la main (fin de phrase ou d'arrêt), pour une relance. */
    var onFinished: (() -> Unit)?

    /**
     * Ouvre l'écoute. [fresh] : une demande neuve (remet le compteur de refus à zéro).
     *
     * Sans valeur par défaut : une fonction qui **redéfinit** n'a pas le droit d'en porter une, or
     * c'est bien [SpeechEngine] (concret, avec défaut) que l'interface décrit.
     */
    fun start(fresh: Boolean)

    /** Arrête d'écouter sans jeter ce qui a été entendu. */
    fun stop()

    /** Arrête, jette la phrase en cours et repart à zéro. */
    fun cancel()

    /** Le mode vocal se ferme : le moteur peut être détruit. */
    fun release()
}
