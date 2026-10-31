package com.onyx.app.ui.design

import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key

/**
 * **La grammaire des états du fil — quatre états, une signature visuelle chacun.**
 *
 * Le défaut que ce fichier ferme : l'état de l'agent se lisait à **un spinner** et à une carte
 * d'activité isolée. Rien ne reliait ce que le fil disait à ce que le tour faisait vraiment, et
 * l'en-tête restait muet — un tour en réflexion et un tour bloqué sur une question se
 * ressemblaient.
 *
 * **Les quatre états**, et eux seuls (le cinquième n'existe pas : tout ce qu'un tour sait faire
 * tombe dans l'un des quatre) :
 *
 * | état | ce qui le déclenche, en vrai |
 * |---|---|
 * | [REST] | aucun tour en cours (ou tour fini) — **l'en-tête s'éteint** |
 * | [THINKING] | le tour a commencé, rien n'est encore écrit : le serveur réfléchit |
 * | [WORKING] | un outil tourne, ou la réponse s'écrit — **le travail produit quelque chose** |
 * | [DECIDING] | une approbation **attend une décision** — le travail est arrêté tant qu'on n'a pas répondu |
 *
 * La correspondance est portée par le fil lui-même
 * ([com.onyx.app.ui.chat.turnFlowState]) : ce fichier ne fait que la **signature** — mouvement,
 * intensité, rythme.
 *
 * **Ce qu'une signature ne porte jamais.** Aucune couleur : les quatre états se dessinent avec
 * **un seul accent** ([Feedback.FlowGlowMax]) et se distinguent par le **mouvement**, pas par une
 * teinte. Une couleur par état serait exactement ce que le lot interdit, et ferait lire « alarme »
 * là où il n'y a qu'une étape.
 *
 * **Reduce Motion.** Figé, sans exception : [spec] rend `snap()` et la pose se lit à sa valeur de
 * repos (voir [frozen] et [pose]) — aucune image intermédiaire, aucune boucle composée
 * ([rememberFlowPose] ne monte même pas la transition).
 *
 * **La lecture passe avant l'état.** Quand on remonte lire le fil, l'état **ralentit**
 * ([ReadingUpFactor]) : une animation permanente qui continue à pleine cadence pendant qu'on lit
 * est exactement ce qui distrait. Le ralentissement est dans [periodMs], donc vérifiable sans
 * appareil, et il n'accélère **jamais** ([periodMs] ne descend pas sous la période nominale).
 */
enum class FlowState(val label: String) {
    /** Rien ne tourne : l'état **s'éteint**. */
    REST("au repos"),

    /** Le tour réfléchit : l'état **respire**. */
    THINKING("réflexion"),

    /** Un outil tourne, ou la réponse s'écrit : l'état **pulse lentement**. */
    WORKING("travail"),

    /** Une décision est attendue : l'état **s'immobilise avec une lueur**. */
    DECIDING("attente de décision"),
    ;

    /** La signature de cet état : mouvement, période, courbe, amplitudes. */
    val signature: FlowSignature get() = FlowGrammar.signature(this)

    /** La boucle de cet état, ou `null` quand il est **figé** par nature ([REST], [DECIDING]). */
    val loopFamily: MotionFamily? get() = signature.family
}

/** Le mouvement d'un état — trois formes, et rien d'autre. */
enum class FlowMotion(val label: String) {
    /** Aucun mouvement : l'état est figé sur sa pose. */
    STILL("figé"),

    /** Un souffle lent, aller-retour : l'état respire. */
    BREATH("respiration"),

    /** Une poussée puis un retour : l'état pulse. */
    PULSE("pulsation"),
}

/**
 * La signature visuelle d'un état — **des nombres, pas des impressions**.
 *
 * @param motion le mouvement, parmi les trois de [FlowMotion] ;
 * @param family la famille de mouvement qui porte la période et la courbe ; `null` quand l'état est
 *        figé par nature ([FlowMotion.STILL]), ce qui est le cas de [FlowState.REST] et de
 *        [FlowState.DECIDING] ;
 * @param scaleAmplitude l'amplitude d'échelle : `0` quand rien ne bouge ;
 * @param alphaFrom l'opacité du point au début de son aller ;
 * @param alphaTo l'opacité du point à la fin de son aller ;
 * @param glowAlpha l'opacité du halo — **la lueur** de l'attente, constante ;
 */
data class FlowSignature(
    val motion: FlowMotion,
    val family: MotionFamily?,
    val scaleAmplitude: Float,
    val alphaFrom: Float,
    val alphaTo: Float,
    val glowAlpha: Float,
) {
    /** La période nominale d'un cycle complet, en millisecondes (`0` quand l'état est figé). */
    val periodMs: Int get() = family?.ms ?: 0

    /** Le nom de la courbe, tel qu'il se lit dans le tableau du rapport. */
    val curveName: String get() = family?.curveName ?: "—"

    /** La courbe elle-même, ou `null` quand il n'y a rien à animer. */
    val curve get() = family?.curve

    /**
     * La période **effective**, ralentie quand la personne remonte lire : la règle est ici, une
     * seule fois, et elle ne descend jamais sous [periodMs].
     */
    fun effectivePeriodMs(readingUp: Boolean): Int =
        if (periodMs == 0) 0 else if (readingUp) periodMs * FlowGrammar.ReadingUpFactor else periodMs

    /**
     * La durée d'une **image d'animation** : la respiration est un aller-retour (donc deux
     * demi-périodes), la pulsation est une poussée (donc une période entière).
     */
    fun legMs(readingUp: Boolean): Int = when (motion) {
        FlowMotion.BREATH -> effectivePeriodMs(readingUp) / 2
        FlowMotion.PULSE -> effectivePeriodMs(readingUp)
        FlowMotion.STILL -> 0
    }
}

/** La pose dessinée d'un état : ce qu'un point et son halo prennent comme valeurs. */
data class FlowPose(val scale: Float, val alpha: Float, val glow: Float)

/** La grammaire : la table des signatures, et les règles qui vont avec. */
object FlowGrammar {

    /**
     * **Le facteur de ralentissement quand on remonte lire.** Un état qui tourne à pleine cadence
     * pendant qu'on lit le fil est un état qui **distrait** ; ×3 met la respiration à 7,8 s et la
     * pulsation à 4,8 s, c'est-à-dire des mouvements qu'on ne surprend plus du coin de l'œil.
     */
    const val ReadingUpFactor: Int = 3

    /** La table `état → signature`, celle que le rapport recopie. */
    val signatures: Map<FlowState, FlowSignature> = mapOf(
        // Au repos, l'en-tête **s'éteint** : rien ne bouge et rien ne luit.
        FlowState.REST to FlowSignature(
            motion = FlowMotion.STILL,
            family = null,
            scaleAmplitude = 0f,
            alphaFrom = 0f,
            alphaTo = 0f,
            glowAlpha = 0f,
        ),
        // Réflexion : la **respiration** de l'orbe ([MotionFamily.Orb] : EaseInOut 2,6 s), la même
        // famille que le souffle de la salle vocale — une seule respiration dans l'app.
        FlowState.THINKING to FlowSignature(
            motion = FlowMotion.BREATH,
            family = MotionFamily.Orb,
            scaleAmplitude = Feedback.FlowBreath,
            alphaFrom = 0.62f,
            alphaTo = 1f,
            glowAlpha = 0.22f,
        ),
        // Travail : la **pulsation** de la marque vivante ([MotionFamily.WorkPulse] : EaseOut
        // 1,6 s), la cadence de `LiveMark` — le travail bat, il ne respire pas.
        FlowState.WORKING to FlowSignature(
            motion = FlowMotion.PULSE,
            family = MotionFamily.WorkPulse,
            scaleAmplitude = Feedback.FlowPulse,
            alphaFrom = 0.55f,
            alphaTo = 1f,
            glowAlpha = 0.30f,
        ),
        // Attente de décision : **immobile**, avec une lueur tenue. C'est le seul état qui ne bouge
        // pas et qui luit : le travail est arrêté, et c'est visible.
        FlowState.DECIDING to FlowSignature(
            motion = FlowMotion.STILL,
            family = null,
            scaleAmplitude = 0f,
            alphaFrom = 1f,
            alphaTo = 1f,
            glowAlpha = 0.28f,
        ),
    )

    /** La signature d'un état. */
    fun signature(state: FlowState): FlowSignature = signatures.getValue(state)

    /**
     * **La période d'un état, ralentie quand on remonte lire.** `0` pour un état figé ; jamais
     * moins que la période nominale — l'état ne s'accélère sous aucun doigt.
     */
    fun periodMs(state: FlowState, readingUp: Boolean): Int =
        signature(state).effectivePeriodMs(readingUp)

    /**
     * **Vrai quand l'état ne doit pas bouger** : sous « Réduire les animations », ou parce que sa
     * nature est figée ([FlowMotion.STILL]).
     */
    fun frozen(state: FlowState, reduceMotion: Boolean): Boolean =
        reduceMotion || signature(state).motion == FlowMotion.STILL

    /** Le mode de répétition d'un état : la respiration fait l'aller-retour, la pulsation repart. */
    fun repeatMode(state: FlowState): RepeatMode =
        if (signature(state).motion == FlowMotion.BREATH) RepeatMode.Reverse else RepeatMode.Restart

    /**
     * **La pose à un instant donné**, pure — donc vérifiable sans appareil.
     *
     * @param progress l'avancement d'une image d'animation, `0..1`. Il n'est **pas** lu quand
     *        l'état est figé : la pose y est la pose de repos, et un état figé qui lirait sa
     *        progression bougerait encore.
     */
    fun pose(state: FlowState, readingUp: Boolean, reduceMotion: Boolean, progress: Float): FlowPose {
        val signature = signature(state)
        if (frozen(state, reduceMotion)) {
            return FlowPose(scale = 1f, alpha = signature.alphaTo, glow = signature.glowAlpha)
        }
        val t = progress.coerceIn(0f, 1f)
        return FlowPose(
            scale = 1f + signature.scaleAmplitude * t,
            alpha = signature.alphaFrom + (signature.alphaTo - signature.alphaFrom) * t,
            glow = signature.glowAlpha * (0.55f + 0.45f * t),
        )
    }

    /**
     * **L'intervalle d'une image d'animation d'un état** — `snap()` quand l'état est figé (par
     * nature ou par « Réduire les animations »), sinon le `tween` de sa famille, **à la période
     * ralentie par la lecture**.
     */
    fun animationSpec(state: FlowState, readingUp: Boolean, reduceMotion: Boolean): DurationBasedAnimationSpec<Float> {
        val signature = signature(state)
        val curve = signature.curve
        if (frozen(state, reduceMotion) || curve == null) return snap()
        return tween(
            durationMillis = signature.legMs(readingUp).coerceAtLeast(1),
            easing = curve,
        )
    }

    /** Les états qui bougent tout seuls : ceux dont une boucle doit être montée. */
    val loopingStates: List<FlowState> = FlowState.entries.filter { signature(it).motion != FlowMotion.STILL }
}

/**
 * **La pose vivante d'un état** — la seule fonction qu'un écran appelle.
 *
 * Elle lit « Réduire les animations » ([LocalReduceMotion]) : sous le réglage, **aucune** boucle
 * n'est composée et la pose est la pose de repos — c'est le `guard !reduceMotion else { return }`
 * de la référence, tenu au même endroit pour les quatre états.
 *
 * [readingUp] est le signal « la personne remonte lire » : la période est alors multipliée par
 * [FlowGrammar.ReadingUpFactor]. Il n'accélère jamais rien : une lecture ne se paie pas d'un
 * mouvement plus rapide.
 */
@Composable
fun rememberFlowPose(state: FlowState, readingUp: Boolean = false): FlowPose {
    val reduceMotion = LocalReduceMotion.current
    if (FlowGrammar.frozen(state, reduceMotion)) {
        return FlowGrammar.pose(state, readingUp = true, reduceMotion = true, progress = 0f)
    }
    val animation = FlowGrammar.animationSpec(state, readingUp, reduceMotion)
    val repeat = FlowGrammar.repeatMode(state)
    return key(state, readingUp) {
        val transition = rememberInfiniteTransition(label = "flow-" + state.name)
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = animation, repeatMode = repeat),
            label = "flowProgress",
        )
        FlowGrammar.pose(state, readingUp, reduceMotion = false, progress = progress)
    }
}
