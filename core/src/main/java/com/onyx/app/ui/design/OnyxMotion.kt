package com.onyx.app.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Les courbes et durées , relevées dans et reproduites ici.
 *
 * Pourquoi ce fichier: côté Android les animations étaient écrites avec des valeurs choisies
 * au jugé (`tween(150)`, `spring(0.7f, 900f)`…), alors que le système utilise un petit vocabulaire
 * précis — `easeOut`, `easeIn`, `easeInOut`, `linear`, `smooth` — avec des durées qui reviennent
 * (0,12 / 0,16 / 0,2 / 0,25 / 0,3 / 0,4 s). Résultat: l'app bouge, mais pas.
 *
 * **Courbes: exactes.** Les quatre courbes sont des cubic-bezier documentés; Compose
 * accepte les mêmes points de contrôle, donc la reproduction est à l'identique.
 *
 * **Ressorts: approchés, et je l'assume.** le système paramètre un ressort par une *durée* perçue et
 * un *rebond*; Compose le paramètre par une raideur et un amortissement. La conversion ci-dessous
 * donne le même enveloppement à l'œil, mais ce n'est pas une égalité mathématique — seule une
 * implémentation manuelle (AnimationVector + physique) le serait.
 */
object OnyxMotion {

    // — Courbes (points de contrôle officiels) —

    /** `.easeOut`: cubic-bezier(0, 0, 0.58, 1). La plus utilisée dans l'app (39 sites). */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** `.easeIn`: cubic-bezier(0.42, 0, 1, 1). */
    val EaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    /** `.easeInOut`: cubic-bezier(0.42, 0, 0.58, 1). */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** `.linear`. */
    val Linear: Easing = LinearEasing

    /**: `.timingCurve(0.4, 0, 0.2, 1, duration: 0.28)`. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // — Durées relevées dans (millisecondes) —
    const val MS_120 = 120
    const val MS_150 = 150
    const val MS_160 = 160
    const val MS_200 = 200
    const val MS_220 = 220
    const val MS_240 = 240
    const val MS_250 = 250
    const val MS_280 = 280
    const val MS_300 = 300
    const val MS_320 = 320
    const val MS_350 = 350
    const val MS_400 = 400
    const val MS_600 = 600
    const val MS_1400 = 1400
    const val MS_1600 = 1600
    const val MS_1800 = 1800
    const val MS_2200 = 2200
    const val MS_2600 = 2600
    const val MS_3400 = 3400
    const val MS_4500 = 4500
    const val MS_5000 = 5000
    const val MS_9000 = 9000

    /** `withAnimation(.easeOut(duration: x))`. */
    fun <T> easeOut(ms: Int): TweenSpec<T> = tween(durationMillis = ms, easing = EaseOut)

    /** `withAnimation(.easeIn(duration: x))`. */
    fun <T> easeIn(ms: Int): TweenSpec<T> = tween(durationMillis = ms, easing = EaseIn)

    /** `withAnimation(.easeInOut(duration: x))`. */
    fun <T> easeInOut(ms: Int): TweenSpec<T> = tween(durationMillis = ms, easing = EaseInOut)

    /** `withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: x))`. */
    fun <T> standard(ms: Int): TweenSpec<T> = tween(durationMillis = ms, easing = Standard)

    /**
     * `.spring(duration:, bounce:)` → raideur/amortissement de Compose.
     *
     * Correspondances utilisées dans l'app, avec leur site d'origine:
     * - 0,35 / snappy → `VoiceView` (titre de phase): amortissement 0,85, raideur 620
     * - 0,44 / rebond 0,20 → `MessageArrival` (message posé): amortissement 0,80, raideur 380
     * - 0,60 / rebond 0,30 → `VoiceView` (changement de voix): amortissement 0,70, raideur 200
     * - 0,70 / rebond 0,25 → appui bouton (échelle): amortissement 0,75, raideur 700
     */
    fun <T> springOnyx(durationSeconds: Double, bounce: Double): SpringSpec<T> {
        val damping = (1.0 - bounce).coerceIn(0.3, 1.0).toFloat()
        val stiffness = when {
            durationSeconds <= 0.35 -> 620f
            durationSeconds <= 0.44 -> 380f
            durationSeconds <= 0.60 -> 200f
            else -> 140f
        }
        return SpringSpec(dampingRatio = damping, stiffness = stiffness)
    }

    /**
     * `.snappy(duration:, extraBounce:)` → Compose.
     *
     * `.snappy` n'est pas « sans rebond »: le système lui donne **0,15 de rebond par défaut**, et
     * `extraBounce` s'y ajoute (`.snappy(duration: 0.35, extraBounce: 0.1)` = rebond 0,25). Les
     * sites écrits `.snappy(0,2)` / `.snappy(0,3)` se convertissaient donc à tort en
     * `dampingRatio = 1,0` (amorti critique, aucun dépassement): ils sont désormais convertis
     * avec le rebond réel.
     */
    fun <T> springSnappy(durationSeconds: Double, extraBounce: Double = 0.0): SpringSpec<T> =
        springOnyx(durationSeconds, (0.15 + extraBounce).coerceAtMost(1.0))

    /**
     * L'appui d'un bouton de l'app: `ButtonStyle` calibré sur un ressort court et sec.
     * `spring(dampingRatio = 0.75, stiffness = 700)`, la valeur que documente aussi le ressort
     * natif pour l'échelle d'appui (0,70 s / rebond 0,25 → amortissement 0,75 / raideur 700).
     */
    val Press: SpringSpec<Float> = SpringSpec(dampingRatio = 0.75f, stiffness = 700f)

    /** Sites nommés, pour que l'appel dise à quelle ligne il répond. */
    object Sites {
        /** — `.spring(duration: 0.44, bounce: 0.2)`. */
        val MessageLanded: FiniteAnimationSpec<Float> = springOnyx(0.44, 0.20f.toDouble())

        /** — même ressort, pour les transitions `IntOffset`. */
        val MessageLandedOffset: FiniteAnimationSpec<IntOffset> = springOnyx(0.44, 0.20)

        /** — `.easeOut(duration: 0.16)`. */
        val MessageVisible: FiniteAnimationSpec<Float> = easeOut(MS_160)

        /** — `.easeOut(duration: 0.25).delay(0.24)`. */
        val MessageVisibleDelayed: FiniteAnimationSpec<Float> =
            tween(durationMillis = MS_250, delayMillis = 240, easing = EaseOut)

        /** — `.timingCurve(0.4, 0, 0.2, 1, duration: 0.28)`. */
        val CoverGrowth: FiniteAnimationSpec<Float> = standard(MS_280)

        /** — `.easeOut(duration: 0.12)`. */
        val CoverSwap: FiniteAnimationSpec<Float> = easeOut(MS_120)

        /** — `.easeOut(duration: 0.24)`. */
        val CoverFade: FiniteAnimationSpec<Float> = easeOut(MS_240)

        /** — `.easeIn(duration: 0.22)`. */
        val TitleLeaving: FiniteAnimationSpec<Float> = easeIn(MS_220)

        /** — `.easeInOut(duration: 0.4).repeatForever(autoreverses: true)`. */
        val TitlePulse: FiniteAnimationSpec<Float> = easeInOut(MS_400)

        /** — `.easeOut(duration: 0.12)` sur le niveau sonore. */
        val VoiceLevel: FiniteAnimationSpec<Float> = easeOut(MS_120)

        /** / `:503` — `.easeOut(duration: 0.3)`. */
        val VoicePhase: FiniteAnimationSpec<Float> = easeOut(MS_300)

        /** — `.snappy(duration: 0.35)`. */
        val VoicePhaseTitle: FiniteAnimationSpec<Float> = springOnyx(0.35, 0.15)

        /** — `.spring(duration: 0.6, bounce: 0.3)`. */
        val VoiceSpeaker: FiniteAnimationSpec<Float> = springOnyx(0.60, 0.30)

        /** — le titre pousse l’ancien vers le haut. */
        val VoicePhaseTitleOffset: FiniteAnimationSpec<IntOffset> = springOnyx(0.35, 0.15)

        /** — `.snappy(duration: 0.3)` (rebond 0,15 ). */
        val MessageFold: FiniteAnimationSpec<Float> = springSnappy(0.30)
        val MessageFoldOffset: FiniteAnimationSpec<IntOffset> = springSnappy(0.30)
        val MessageFoldSize: FiniteAnimationSpec<IntSize> = springSnappy(0.30)

        /** — fondu des actions à la fin du direct. */
        val MessageActions: FiniteAnimationSpec<Float> = easeOut(MS_300)

        /** — `.snappy(duration: 0.2)` (coche de copie). */
        val Snappy200: FiniteAnimationSpec<Float> = springSnappy(0.20)

        /** — `.easeOut(duration: 0.15)` (métriques détaillées). */
        val MetricsToggle: FiniteAnimationSpec<Float> = easeOut(MS_150)

        /** — `.easeOut(duration: 0.4).delay(0.05)` (coche d'un outil, tracée). */
        val StepCheck: FiniteAnimationSpec<Float> =
            tween(durationMillis = MS_400, delayMillis = 50, easing = EaseOut)

        /** — `.easeOut(duration: 0.4)`, les pièces de l'écran de départ. */
        val ArrivingPiece: FiniteAnimationSpec<Float> = easeOut(MS_400)
        val ArrivingPieceOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_400)
        val ArrivingPieceDp: FiniteAnimationSpec<Dp> = easeOut(MS_400)

        /**: `withAnimation { … }` — la courbe par défaut de. */
        val DefaultSpec: FiniteAnimationSpec<Float> = easeInOut(MS_350)
        val DefaultSpecSize: FiniteAnimationSpec<IntSize> = easeInOut(MS_350)

        /** — `.spring(duration: 0.6, bounce: 0.3)` (passation d'un visage). */
        val VoiceHandoff: FiniteAnimationSpec<Float> = springOnyx(0.60, 0.30)
        val VoiceHandoffOffset: FiniteAnimationSpec<IntOffset> = springOnyx(0.60, 0.30)
        val VoiceHandoffDp: FiniteAnimationSpec<Dp> = springOnyx(0.60, 0.30)

        /** — `.snappy(duration: 0.35, extraBounce: 0.1)` (rebond 0,25). */
        val FollowUpChip: FiniteAnimationSpec<Float> = springSnappy(0.35, 0.10)
        val FollowUpChipOffset: FiniteAnimationSpec<IntOffset> = springSnappy(0.35, 0.10)

        /** et — `.easeOut(duration: 0.2)`. */
        val Toast: FiniteAnimationSpec<Float> = easeOut(MS_200)
        val ToastOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_200)
        val JumpToLatest: FiniteAnimationSpec<Float> = easeOut(MS_200)
        val JumpToLatestOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_200)

        /** — `.smooth(duration: 0.32)`, approché par une courbe. */
        val SidePanel: FiniteAnimationSpec<Float> = easeInOut(MS_320)
        val SidePanelOffset: FiniteAnimationSpec<IntOffset> = easeInOut(MS_320)

        /**
         * La course du volet gauche quand il se replie: la même durée et la même courbe que le
         * panneau latéral (`SidePanel`), pour que la largeur du volet s'anime au lieu de sauter.
         */
        val SidePanelWidth: FiniteAnimationSpec<Dp> = easeInOut(MS_320)

        /**
         * Feuilles et segments natifs: ne publie pas leur ressort dans le source.
         * La durée perçue de 0,35 / 0,30 s est donc une approximation, pas une courbe mesurée.
         */
        val Sheet: FiniteAnimationSpec<Float> = springOnyx(0.35, 0.0)
        val SheetOffset: FiniteAnimationSpec<IntOffset> = springOnyx(0.35, 0.0)
        val TabSelection: FiniteAnimationSpec<Float> = springOnyx(0.30, 0.0)
        val TabSelectionDp: FiniteAnimationSpec<Dp> = springOnyx(0.30, 0.0)
        val TabSelectionOffset: FiniteAnimationSpec<IntOffset> = springOnyx(0.30, 0.0)

        // — Sites du troisième lot: la légende mot à mot, les arrivées sans délai, les
        // enchaînements du composeur, et les paires « Reduce Motion ». —

        /** — `.easeOut(duration: 0.25)` sur le nombre de mots (`RisingCaption`). */
        val CaptionWord: FiniteAnimationSpec<Float> = easeOut(MS_250)

        /** — `reduceMotion ? .easeInOut(duration: 0.35)` (passation de visage). */
        val VoiceHandoffReduced: FiniteAnimationSpec<Float> = easeInOut(MS_350)

        /** — le même fondu de 0,25 s, sans le délai de 240 ms. */
        val MessageVisibleImmediate: FiniteAnimationSpec<Float> = easeOut(MS_250)

        /** — `.snappy(duration: 0.25)` (pas encore de site Android). */
        val Snappy250: FiniteAnimationSpec<Float> = springSnappy(0.25)

        /** — `.snappy(duration: 0.3)` (hauteur du composeur). */
        val ComposerRowSize: FiniteAnimationSpec<IntSize> = springSnappy(0.30)

        /** — `.snappy(duration: 0.3, extraBounce: 0.15)` (bouton de droite). */
        val ComposerTrailing: FiniteAnimationSpec<Float> = springSnappy(0.30, 0.15)

        /** — `.easeOut(duration: 1.6).repeatForever(autoreverses: false)`. */
        val LiveMarkPulse: FiniteAnimationSpec<Float> = easeOut(MS_1600)

        /** — `.easeOut(duration: 2.6).repeatForever(autoreverses: false)`. */
        val MarkPulse: DurationBasedAnimationSpec<Float> = easeOut(MS_2600)

        /**
         * — `.linear(duration: 1.8).repeatForever(autoreverses: false)`.
         * `DurationBasedAnimationSpec`: c'est la forme qu'attend `infiniteRepeatable`.
         */
        val Shimmer: DurationBasedAnimationSpec<Float> = tween(durationMillis = MS_1800, easing = Linear)

        /** — `.easeInOut(duration: 0.6).repeatForever(autoreverses: true)`. */
        val NewBelowDot: FiniteAnimationSpec<Float> = easeInOut(MS_600)
    }
}
