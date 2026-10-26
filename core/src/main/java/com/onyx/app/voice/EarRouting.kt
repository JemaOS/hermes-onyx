package com.onyx.app.voice

/**
 * Où la réponse doit sortir.
 *
 * [LEAVE_ALONE] est le cas d'un casque filaire, d'un Bluetooth ou d'Android Auto: la sortie ne
 * nous appartient pas, on n'y touche pas (c'est ce que fait `applyOutputRoute` quand un
 * CarPlay ou un casque est branché).
 */
enum class EarOutput { SPEAKER, EARPIECE, LEAVE_ALONE }

/**
 * « Levé à l'oreille »: le capteur de proximité dit « tout près » **et** le téléphone n'est pas
 * posé face contre la table — posé à plat, il lit « près » aussi, et ce n'est pas une oreille.
 * C'est la correction (`proximityChanged`, 2026-10-21 et -22: une réponse demandée puis le
 * téléphone posé pour l'attendre sortait dans l'écouteur).
 */
fun atEarFor(near: Boolean, faceDown: Boolean): Boolean = near && !faceDown

/**
 * Le choix de sortie, comme `applyOutputRoute`:
 * un casque, un Bluetooth ou une voiture l'emportent; sinon, téléphone à l'oreille et réglage
 * allumé → écouteur, partout ailleurs → haut-parleur, comme l'app Téléphone.
 *
 * Fonction pure: aucun capteur, aucun `AudioManager`, donc éprouvable sur JVM.
 */
fun earOutputFor(externalRoute: Boolean, atEar: Boolean, enabled: Boolean): EarOutput = when {
    externalRoute -> EarOutput.LEAVE_ALONE
    enabled && atEar -> EarOutput.EARPIECE
    else -> EarOutput.SPEAKER
}

/**
 * Le capteur de proximité, derrière une interface: le vrai passe par `SensorManager`, celui des
 * tests par un faux. C'est ce qui permet d'éprouver le chemin complet sans matériel.
 */
interface ProximityWatcher {
    /** Se mettre à écouter les changements de proximité. */
    fun start()

    /** Ne plus écouter. */
    fun stop()

    /** Vrai si le capteur existe et a bien pu être ouvert. */
    val isAvailable: Boolean
}

/** L'application de la sortie, derrière une interface: `AudioManager` au vrai, un faux en test. */
interface EarRouteApplier {
    /** Imposer [output]. */
    fun apply(output: EarOutput)

    /** Rendre la main: la sortie redevient celle du téléphone, sans mode audio imposé. */
    fun restore()
}

/**
 * Le téléphone à l'oreille fait jouer la réponse dans l'écouteur, comme un appel.
 *
 * Comme `AudioSession.setEarRouting` / `applyEarRouting` / `proximityChanged`: le capteur n'est
 * surveillé **que pendant qu'une réponse se lit** (l'écran du téléphone s'éteint dès que quelque
 * chose passe devant, ce qui noircissait l'écran pendant une longue attente de réponse), et l'on
 * ne touche à la sortie du téléphone que si l'on y a touché: au repos, Onyx ne l'impose pas.
 *
 * Le routeur lui-même est pur: le capteur et l'application de la sortie lui sont injectés. Un
 * appareil sans capteur de proximité ([ProximityWatcher.isAvailable] faux) laisse le téléphone
 * faire, et rien ne casse.
 */
class ProximityEarRouter(
    private val watcher: ProximityWatcher,
    private val route: EarRouteApplier,
    /** Un casque filaire, un Bluetooth ou Android Auto porte déjà le son: on n'y touche pas. */
    private val externalRoute: () -> Boolean = { false },
    /** « Écouteur quand on lève le téléphone à l'oreille » (Réglages → Voix). */
    private val enabled: () -> Boolean = { true },
    /** Vrai quand l'appareil est posé face contre la table. */
    private val faceDown: () -> Boolean = { false },
) {
    /** Vrai entre [setWanted] vrai et faux: le capteur est ouvert. */
    var isWatching: Boolean = false
        private set

    /** Vrai quand la dernière mesure disait « à l'oreille ». */
    var isAtEar: Boolean = false
        private set

    /** La dernière sortie appliquée, `null` quand rien n'a été imposé. */
    var lastApplied: EarOutput? = null
        private set

    /**
     * Appelé à chaque changement de « à l'oreille »: c'est ce que la lecture écoute pour se
     * réacheminer (une voix Android se joue avec les attributs d'un appel, pas d'un média).
     */
    var onAtEarChanged: ((Boolean) -> Unit)? = null

    /**
     * Le mode vocal dit qu'une réponse se lit (vrai), ou qu'elle est finie/arrêtée (faux).
     *
     * Au repos, on rend la main: la sortie redevient celle du téléphone et le capteur se ferme.
     */
    fun setWanted(on: Boolean) {
        val watch = on && enabled() && watcher.isAvailable
        if (!watch) {
            handBack()
            return
        }
        if (!isWatching) {
            watcher.start()
            isWatching = true
        }
        apply()
    }

    /** Une mesure du capteur: tout près, ou non. */
    fun proximityChanged(near: Boolean) {
        if (!isWatching) return
        isAtEar = atEarFor(near, faceDown())
        apply()
    }

    /** L'écran vocal se ferme: plus rien à surveiller ni à imposer. */
    fun release() = handBack()

    private fun handBack() {
        val had = isWatching
        if (had) watcher.stop()
        isWatching = false
        val wasAtEar = isAtEar
        isAtEar = false
        lastApplied = null
        if (wasAtEar) onAtEarChanged?.invoke(false)
        // On ne rend la main que si l'on avait pris la sortie.
        if (had) route.restore()
    }

    private fun apply() {
        val output = earOutputFor(externalRoute(), isAtEar, enabled())
        route.apply(output)
        lastApplied = output
        onAtEarChanged?.invoke(isAtEar)
    }
}
