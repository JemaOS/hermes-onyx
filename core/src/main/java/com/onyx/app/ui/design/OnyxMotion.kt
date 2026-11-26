package com.onyx.app.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Les courbes et durées de la référence, relevées dans la référence et reproduites ici.
 *
 * Pourquoi ce fichier : côté Android les animations étaient écrites avec des valeurs choisies
 * au jugé (`tween(150)`, `spring(0.7f, 900f)`…), alors qu'référence utilise un petit vocabulaire
 * précis — `easeOut`, `easeIn`, `easeInOut`, `linear`, `smooth` — avec des durées qui reviennent
 * (0,12 / 0,16 / 0,2 / 0,25 / 0,3 / 0,4 s). Résultat : l'app bouge, mais pas comme référence.
 *
 * **Courbes : exactes.** Les quatre courbes de la référence sont des cubic-bezier documentés ; Compose
 * accepte les mêmes points de contrôle, donc la reproduction est à l'identique.
 *
 * **Ressorts : approchés, et je l'assume.** la référence paramètre un ressort par une *durée* perçue et
 * un *rebond* ; Compose le paramètre par une raideur et un amortissement. La conversion ci-dessous
 * donne le même enveloppement à l'œil, mais ce n'est pas une égalité mathématique — seule une
 * implémentation manuelle (AnimationVector + physique) le serait.
 */
object OnyxMotion {

    // — Courbes de la référence (points de contrôle officiels) —

    /** `.easeOut` : cubic-bezier(0, 0, 0.58, 1). La plus utilisée dans l'app (39 sites). */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** `.easeIn` : cubic-bezier(0.42, 0, 1, 1). */
    val EaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    /** `.easeInOut` : cubic-bezier(0.42, 0, 0.58, 1). */
    val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** `.linear`. */
    val Linear: Easing = LinearEasing

    /** `OpeningCover` : `.timingCurve(0.4, 0, 0.2, 1, duration: 0.28)`. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // — Durées relevées dans la référence (millisecondes) —
    const val MS_120 = 120
    const val MS_150 = 150
    const val MS_160 = 160
    const val MS_180 = 180
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
     * `.spring(duration:, bounce:)` de la référence → raideur/amortissement de Compose.
     *
     * Correspondances utilisées dans l'app, avec leur site d'origine :
     *  - 0,35 / snappy        → `VoiceView` (titre de phase)      : amortissement 0,85, raideur 620
     *  - 0,44 / rebond 0,20   → `MessageArrival` (message posé)    : amortissement 0,80, raideur 380
     *  - 0,60 / rebond 0,30   → `VoiceView` (changement de voix)   : amortissement 0,70, raideur 200
     *  - 0,70 / rebond 0,25   → appui bouton (échelle)             : amortissement 0,75, raideur 700
     */
    fun <T> springReference(durationSeconds: Double, bounce: Double): SpringSpec<T> {
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
     * `.snappy(duration:, extraBounce:)` de la référence → Compose.
     *
     * `.snappy` n'est pas « sans rebond » : la référence lui donne **0,15 de rebond par défaut**, et
     * `extraBounce` s'y ajoute (`.snappy(duration: 0.35, extraBounce: 0.1)` = rebond 0,25). Les
     * sites écrits `.snappy(0,2)` / `.snappy(0,3)` se convertissaient donc à tort en
     * `dampingRatio = 1,0` (amorti critique, aucun dépassement) : ils sont désormais convertis
     * avec le rebond réel de la référence.
     */
    fun <T> springSnappy(durationSeconds: Double, extraBounce: Double = 0.0): SpringSpec<T> =
        springReference(durationSeconds, (0.15 + extraBounce).coerceAtMost(1.0))

    /**
     * L'appui d'un bouton de l'app : `ButtonStyle` calibré sur un ressort court et sec.
     * `spring(dampingRatio = 0.75, stiffness = 700)`, la valeur que documente aussi le ressort
     * natif de la référence pour l'échelle d'appui (0,70 s / rebond 0,25 → amortissement 0,75 / raideur 700).
     */
    val Press: SpringSpec<Float> = SpringSpec(dampingRatio = 0.75f, stiffness = 700f)

    /** Sites nommés, pour que l'appel dise à quelle ligne de la référence il répond. */
    object Sites {
        /** `MessageArrival:52` — `.spring(duration: 0.44, bounce: 0.2)`. */
        val MessageLanded: FiniteAnimationSpec<Float> = springReference(0.44, 0.20f.toDouble())

        /** `MessageArrival:52` — même ressort, pour les transitions `IntOffset`. */
        val MessageLandedOffset: FiniteAnimationSpec<IntOffset> = springReference(0.44, 0.20)

        /** `MessageArrival:51` — `.easeOut(duration: 0.16)`. */
        val MessageVisible: FiniteAnimationSpec<Float> = easeOut(MS_160)

        /** `MessageArrival:54` — `.easeOut(duration: 0.25).delay(0.24)`. */
        val MessageVisibleDelayed: FiniteAnimationSpec<Float> =
            tween(durationMillis = MS_250, delayMillis = 240, easing = EaseOut)

        /** `OpeningCover:80` — `.timingCurve(0.4, 0, 0.2, 1, duration: 0.28)`. */
        val CoverGrowth: FiniteAnimationSpec<Float> = standard(MS_280)

        /** `OpeningCover:84` — `.easeOut(duration: 0.12)`. */
        val CoverSwap: FiniteAnimationSpec<Float> = easeOut(MS_120)

        /** `OpeningCover:88` — `.easeOut(duration: 0.24)`. */
        val CoverFade: FiniteAnimationSpec<Float> = easeOut(MS_240)

        /** `TypedTitle:66` — `.easeIn(duration: 0.22)`. */
        val TitleLeaving: FiniteAnimationSpec<Float> = easeIn(MS_220)

        /** `TypedTitle:95` — `.easeInOut(duration: 0.4).repeatForever(autoreverses: true)`. */
        val TitlePulse: FiniteAnimationSpec<Float> = easeInOut(MS_400)

        /** `VoiceView:603` — `.easeOut(duration: 0.12)` sur le niveau sonore. */
        val VoiceLevel: FiniteAnimationSpec<Float> = easeOut(MS_120)

        /** `VoiceView:457` / `:503` — `.easeOut(duration: 0.3)`. */
        val VoicePhase: FiniteAnimationSpec<Float> = easeOut(MS_300)

        /** `VoiceView:51` — `.snappy(duration: 0.35)`. */
        val VoicePhaseTitle: FiniteAnimationSpec<Float> = springReference(0.35, 0.15)

        /** `VoiceView:599` — `.spring(duration: 0.6, bounce: 0.3)`. */
        val VoiceSpeaker: FiniteAnimationSpec<Float> = springReference(0.60, 0.30)

        /** `VoiceView:51` — le titre pousse l’ancien vers le haut. */
        val VoicePhaseTitleOffset: FiniteAnimationSpec<IntOffset> = springReference(0.35, 0.15)

        /** `MessageRow:245,289,335-337` — `.snappy(duration: 0.3)` (rebond 0,15 de la référence). */
        val MessageFold: FiniteAnimationSpec<Float> = springSnappy(0.30)
        val MessageFoldOffset: FiniteAnimationSpec<IntOffset> = springSnappy(0.30)
        val MessageFoldSize: FiniteAnimationSpec<IntSize> = springSnappy(0.30)

        /** `MessageRow:170` — fondu des actions à la fin du direct. */
        val MessageActions: FiniteAnimationSpec<Float> = easeOut(MS_300)

        /** `MessageRow:183,186` — `.snappy(duration: 0.2)` (coche de copie). */
        val Snappy200: FiniteAnimationSpec<Float> = springSnappy(0.20)

        /** `MessageRow:435` — `.easeOut(duration: 0.15)` (métriques détaillées). */
        val MetricsToggle: FiniteAnimationSpec<Float> = easeOut(MS_150)

        /** `MessageRow:621` — `.easeOut(duration: 0.4).delay(0.05)` (coche d'un outil, tracée). */
        val StepCheck: FiniteAnimationSpec<Float> =
            tween(durationMillis = MS_400, delayMillis = 50, easing = EaseOut)

        /** `StartScreen:262` — `.easeOut(duration: 0.4)`, les pièces de l'écran de départ. */
        val ArrivingPiece: FiniteAnimationSpec<Float> = easeOut(MS_400)
        val ArrivingPieceOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_400)
        val ArrivingPieceDp: FiniteAnimationSpec<Dp> = easeOut(MS_400)

        /** `ConversationsView` : `withAnimation { … }` — la courbe par défaut de la référence. */
        val DefaultSpec: FiniteAnimationSpec<Float> = easeInOut(MS_350)
        val DefaultSpecSize: FiniteAnimationSpec<IntSize> = easeInOut(MS_350)

        /** `VoiceView:597` — `.spring(duration: 0.6, bounce: 0.3)` (passation d'un visage). */
        val VoiceHandoff: FiniteAnimationSpec<Float> = springReference(0.60, 0.30)
        val VoiceHandoffOffset: FiniteAnimationSpec<IntOffset> = springReference(0.60, 0.30)
        val VoiceHandoffDp: FiniteAnimationSpec<Dp> = springReference(0.60, 0.30)

        /** `FollowUpChips:44` — `.snappy(duration: 0.35, extraBounce: 0.1)` (rebond 0,25). */
        val FollowUpChip: FiniteAnimationSpec<Float> = springSnappy(0.35, 0.10)
        val FollowUpChipOffset: FiniteAnimationSpec<IntOffset> = springSnappy(0.35, 0.10)

        /** `Toast:25` et `TranscriptView:263` — `.easeOut(duration: 0.2)`. */
        val Toast: FiniteAnimationSpec<Float> = easeOut(MS_200)
        val ToastOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_200)
        val JumpToLatest: FiniteAnimationSpec<Float> = easeOut(MS_200)
        val JumpToLatestOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_200)

        /** `SwipeToOpen:236` — `.smooth(duration: 0.32)`, approché par une courbe. */
        val SidePanel: FiniteAnimationSpec<Float> = easeInOut(MS_320)
        val SidePanelOffset: FiniteAnimationSpec<IntOffset> = easeInOut(MS_320)

        /**
         * La course du volet gauche quand il se replie : la même durée et la même courbe que le
         * panneau latéral (`SidePanel`), pour que la largeur du volet s'anime au lieu de sauter.
         */
        val SidePanelWidth: FiniteAnimationSpec<Dp> = easeInOut(MS_320)

        /**
         * Feuilles et segments natifs : la référence ne publie pas leur ressort dans le source.
         * La durée perçue de 0,35 / 0,30 s est donc une approximation, pas une courbe mesurée.
         */
        val Sheet: FiniteAnimationSpec<Float> = springReference(0.35, 0.0)
        val SheetOffset: FiniteAnimationSpec<IntOffset> = springReference(0.35, 0.0)
        val TabSelection: FiniteAnimationSpec<Float> = springReference(0.30, 0.0)
        val TabSelectionDp: FiniteAnimationSpec<Dp> = springReference(0.30, 0.0)
        val TabSelectionOffset: FiniteAnimationSpec<IntOffset> = springReference(0.30, 0.0)

        // — Sites du troisième lot : la légende mot à mot, les arrivées sans délai, les
        //   enchaînements du composeur, et les paires « Reduce Motion » de la référence. —

        /** `VoiceView:1188` — `.easeOut(duration: 0.25)` sur le nombre de mots (`RisingCaption`). */
        val CaptionWord: FiniteAnimationSpec<Float> = easeOut(MS_250)

        /** `VoiceView:599` — `reduceMotion ? .easeInOut(duration: 0.35)` (passation de visage). */
        val VoiceHandoffReduced: FiniteAnimationSpec<Float> = easeInOut(MS_350)

        /** `MessageArrival:54` — le même fondu de 0,25 s, sans le délai de 240 ms. */
        val MessageVisibleImmediate: FiniteAnimationSpec<Float> = easeOut(MS_250)

        /** `MessageRow:694,764` — `.snappy(duration: 0.25)` (pas encore de site Android). */
        val Snappy250: FiniteAnimationSpec<Float> = springSnappy(0.25)

        /** `ComposerView:137-139,386` — `.snappy(duration: 0.3)` (hauteur du composeur). */
        val ComposerRowSize: FiniteAnimationSpec<IntSize> = springSnappy(0.30)

        /** `ComposerView:555` — `.snappy(duration: 0.3, extraBounce: 0.15)` (bouton de droite). */
        val ComposerTrailing: FiniteAnimationSpec<Float> = springSnappy(0.30, 0.15)

        /**
         * **L'apparition d'une rangée de liste** — la famille [MotionFamily.Row], en `IntOffset`
         * pour les listes qui se décalent et en `Dp` pour celles qui montent d'un demi-pas.
         *
         * Ce n'est pas une arrivée de message (0,44 s, un ressort) : une rangée de liste qui
         * apparaît ne doit pas **retarder** la lecture, elle doit seulement ne pas se poser d'un
         * bloc. 180 ms, `easeOut`, et sous Reduce Motion `snap()` — la liste est là, complète.
         */
        val RowArrival: FiniteAnimationSpec<Float> = easeOut(MS_180)
        val RowArrivalOffset: FiniteAnimationSpec<IntOffset> = easeOut(MS_180)
        val RowArrivalDp: FiniteAnimationSpec<Dp> = easeOut(MS_180)

        /**
         * **La respiration de l'orbe au repos** — `easeInOut(duration: 2.6).repeatForever`. C'est
         * la seule animation de l'app qui ne finit pas : elle dit « je suis là, je t'écoute ».
         */
        val OrbRest: DurationBasedAnimationSpec<Float> = easeInOut(MS_2600)

        /** `StreamingTail:135` — `.easeOut(duration: 1.6).repeatForever(autoreverses: false)`. */
        val LiveMarkPulse: FiniteAnimationSpec<Float> = easeOut(MS_1600)

        /** `StartScreen:152` — `.easeOut(duration: 2.6).repeatForever(autoreverses: false)`. */
        val MarkPulse: DurationBasedAnimationSpec<Float> = easeOut(MS_2600)

        /**
         * `MessageRow:557` — `.linear(duration: 1.8).repeatForever(autoreverses: false)`.
         * `DurationBasedAnimationSpec` : c'est la forme qu'attend `infiniteRepeatable`.
         */
        val Shimmer: DurationBasedAnimationSpec<Float> = tween(durationMillis = MS_1800, easing = Linear)

        /** `TranscriptView:529` — `.easeInOut(duration: 0.6).repeatForever(autoreverses: true)`. */
        val NewBelowDot: FiniteAnimationSpec<Float> = easeInOut(MS_600)
    }
}

/**
 * **Les familles de mouvement** — une courbe et une durée par famille, nommées ici, et une seule
 * fois.
 *
 * Pourquoi une famille plutôt qu'un site : le relevé de ce lot a trouvé, dans les seuls gestes
 * visibles sur la capture du propriétaire, **onze** durées et cinq courbes différentes pour huit
 * intentions — une feuille du bas montait en 280 ms `easeOut` dans une feuille et en 300 ms dans
 * l'autre, le volet glissait en 320 ms `easeInOut` mais son fondu de contenu durait 200 ms, une
 * sélection d'onglet n'animait rien du tout, et le bouton d'envoi changeait d'état d'un coup. C'est
 * exactement ce que le propriétaire appelle « sans vie » : ce n'est pas qu'il manque des
 * animations, c'est qu'aucune ne répond à une autre.
 *
 * **La règle de la maison.** Une famille = **une** courbe + **une** durée, et tous les sites de
 * cette famille s'y tiennent ([spec]). Un site qui a besoin d'une autre durée n'a pas besoin d'une
 * autre durée : il appartient à une autre famille. `MotionFamilyTest` refuse une durée hors bornes,
 * refuse deux familles identiques, et refuse qu'une famille dite « micro » dépasse
 * [MicroMaxMs] — c'est le « rien de plus de ~250 ms » de la consigne, tenu par un test et pas par
 * une intention.
 *
 * **Reduce Motion.** [spec] prend le drapeau en paramètre (pur, donc vérifiable sans appareil) : il
 * rend alors `snap()`, c'est-à-dire **aucune** image intermédiaire. La version composable,
 * [animated], lit [LocalReduceMotion] ; c'est la seule qu'un écran appelle. Une animation qui
 * retarde une action n'existe donc pas sous Reduce Motion — elle ne retarde rien du tout.
 */
enum class MotionFamily(
    /** Ce que la famille anime, en un mot — pour le tableau du rapport. */
    val label: String,
    /** La courbe, telle qu'elle se lit dans [OnyxMotion]. */
    val curveName: String,
    /** La durée perçue, en millisecondes. **Une seule** par famille. */
    val ms: Int,
    /** La courbe elle-même. */
    val curve: Easing,
    /** L'amortissement, si la famille est un ressort (sinon `null`). */
    val dampingRatio: Float? = null,
    /** La raideur, si la famille est un ressort (sinon `null`). */
    val stiffness: Float? = null,
) {
    /** La feuille du bas : une montée franche, avec un **rebond discret** en fin de course. */
    Open("feuille du bas", "ressort 380 / 0,88", 280, OnyxMotion.EaseOut, 0.88f, 380f),

    /** Le volet latéral : le glissement **et** le fondu de son contenu, la même durée. */
    Panel("volet latéral", "EaseInOut", 320, OnyxMotion.EaseInOut),

    /** Une transition d'écran : la courbe du système, tiède au départ, franche à l'arrivée. */
    Screen("transition d'écran", "Standard", 280, OnyxMotion.Standard),

    /** L'appui d'un bouton, d'une tuile, d'une pastille : le ressort court et sec de la maison. */
    Press("appui (échelle)", "ressort 700 / 0,75", 200, OnyxMotion.EaseOut, 0.75f, 700f),

    /**
     * **La sélection d'un onglet — et la capsule qui se déplace.**
     *
     * Pourquoi un ressort et non la teinte en fondu du lot précédent : le propriétaire demande que
     * « la capsule active **se déplace** et se déforme légèrement au lieu de s'allumer d'un coup ».
     * Un fondu de couleur ne déplace rien : il laisse une pastille s'éteindre et la suivante
     * s'allumer, ce qui se lit comme deux objets. La famille porte donc **les trois** mouvements de
     * la sélection — l'**offset** de la capsule, sa **déformation** ([Feedback.TabSquash]) et la
     * **teinte** de l'encre — avec une seule courbe et une seule durée, et un **léger dépassement**
     * ([Feedback.TabOvershoot]) qui fait qu'elle se pose au lieu de s'arrêter.
     *
     * Elle reste **micro** (240 ms) : c'est un geste de la main, il ne doit pas retarder l'onglet
     * qu'on vient de toucher.
     */
    Tab("sélection d'onglet (capsule)", "ressort 500 / 0,82", 240, OnyxMotion.EaseOut, 0.82f, 500f),

    /** L'arrivée d'un message : le ressort de `MessageArrival` (0,44 s / rebond 0,20). */
    Message("arrivée d'un message", "ressort 380 / 0,80", 440, OnyxMotion.EaseOut, 0.80f, 380f),

    /** L'arrivée du raisonnement : le bloc se pose, en fondu court. */
    Reasoning("arrivée du raisonnement", "EaseOut", 200, OnyxMotion.EaseOut),

    /**
     * La **sortie** d'un panneau (feuille, alerte, menu) : un panneau qui s'en va se sent plus
     * court qu'un panneau qui arrive, et il part en accélérant au lieu de se poser.
     *
     * Pourquoi une famille à part plutôt que [Open] dans l'autre sens : une sortie n'est pas une
     * entrée inversée. Elle est plus courte (260 contre 280 ms), sa courbe accélère au lieu de
     * freiner, et c'est ce qui fait qu'elle ne **retarde** jamais la sensation d'avoir fermé.
     * Un seul geste peut ainsi avoir deux temps justes, chacun avec sa courbe et sa durée.
     */
    Dismiss("sortie de panneau", "EaseIn", 260, OnyxMotion.EaseIn),

    /** L'état du bouton d'envoi : du rond au rond, sans saut. */
    Send("état du bouton d'envoi", "ressort 620 / 0,70", 240, OnyxMotion.EaseInOut, 0.70f, 620f),

    /**
     * **L'apparition d'une rangée de liste.** Un décalage en **cascade**, très court : la rangée
     * monte d'un demi-pas et se fond, la suivante part [Feedback.StaggerMs] plus tard, et la cascade
     * entière est **bornée** à [Feedback.StaggerMaxMs] — au-delà, la dernière rangée d'une longue
     * liste arriverait après que l'œil a fini de lire les premières, et la liste paraîtrait lente.
     *
     * C'est la famille qui manquait le plus : sur la capture du propriétaire, une feuille de sept
     * lignes posait ses sept rangées **d'un bloc**, et c'est précisément ce qui se lit « pas chiadé ».
     * Sous Reduce Motion, `snap()` et un décalage **nul** : la liste est là, complète, d'un coup.
     */
    Row("arrivée d'une rangée", "EaseOut", 180, OnyxMotion.EaseOut),

    /**
     * **La respiration de l'orbe au repos.**
     *
     * L'orbe vocale au repos était parfaitement immobile : un disque figé, alors que c'est l'objet
     * le plus grand de l'écran et celui qui doit dire « je suis là ». Elle respire maintenant — une
     * échelle de [Feedback.OrbBreath] sur 2,6 s, aller-retour, sans fin.
     *
     * C'est la seule famille **ambiante** : elle ne fait pas partie des gestes (elle n'est jamais
     * déclenchée par un doigt) ni des ouvertures (elle ne mène nulle part). Elle est donc classée à
     * part ([ambientFamilies]) et **figée sous Reduce Motion** comme les autres : une respiration
     * qui continue sous « Réduire les animations » est exactement ce que le réglage interdit.
     */
    Orb("respiration de l'orbe", "EaseInOut", 2600, OnyxMotion.EaseInOut),

    /**
     * **La pulsation d'un état qui travaille.**
     *
     * La cadence est celle de `LiveMark` (1,6 s, `EaseOut`) : une poussée, puis un retour sec au
     * repos. Le travail **bat**, il ne respire pas — c'est la différence de rythme qui fait lire
     * « outil en cours » là où la réflexion se contente de souffler ([Orb], 2,6 s `EaseInOut`).
     *
     * Elle est **ambiante** (aucun doigt ne la déclenche, elle dure tant que l'état est là) et donc
     * **figée sous Reduce Motion** comme [Orb].
     */
    WorkPulse("pulsation d'un état", "EaseOut", 1600, OnyxMotion.EaseOut),

    /**
     * **Le changement d'état du fil** — le fondu court et l'échelle qui accompagnent le passage
     * d'un état à l'autre.
     *
     * Le défaut que ce nom ferme : un tour passait de la réflexion au travail, puis à l'attente
     * d'une décision, **d'un saut**. Une transition de 260 ms, `EaseOut`, laisse le nouvel état
     * **arriver** au lieu de remplacer le précédent ; c'est une arrivée, pas un geste (aucun doigt
     * ne la déclenche) — elle vit donc avec les ouvertures, et elle est **figée sous Reduce
     * Motion** : les états sont alors posés d'un coup, sans une image intermédiaire.
     */
    FlowShift("changement d'état du fil", "EaseOut", 260, OnyxMotion.EaseOut),
    ;

    /** Le délai maximal d'un geste de la main : au-delà, l'animation retarde l'action. */
    val isMicro: Boolean get() = ms <= MicroMaxMs

    /**
     * La spec de la famille, **pure** : `snap()` sous Reduce Motion (aucune image intermédiaire),
     * sinon le ressort ou le `tween` de la famille. C'est la fonction que le test interroge — le
     * chiffre annoncé et le comportement réel sont donc le même code.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> spec(reduceMotion: Boolean): FiniteAnimationSpec<T> {
        if (reduceMotion) return snap<T>() as FiniteAnimationSpec<T>
        val damping = dampingRatio
        val stiff = stiffness
        return if (damping != null && stiff != null) {
            SpringSpec<T>(dampingRatio = damping, stiffness = stiff)
        } else {
            TweenSpec<T>(durationMillis = ms, easing = curve)
        }
    }

    /** La spec de la famille telle qu'un écran l'obtient : elle lit le réglage système. */
    @Composable
    fun <T> animated(): FiniteAnimationSpec<T> = spec(LocalReduceMotion.current)

    companion object {
        /** Le plafond d'un mouvement « micro », en millisecondes : 250. */
        const val MicroMaxMs: Int = 250

        /** Les familles d'un geste de la main — celles qui doivent tenir sous [MicroMaxMs]. */
        val microFamilies: List<MotionFamily> = listOf(Press, Tab, Reasoning, Send)

        /** Les familles d'ouverture, d'arrivée **et de sortie** : plus longues, jamais des gestes. */
        val openingFamilies: List<MotionFamily> =
            listOf(Open, Dismiss, Panel, Screen, Message, FlowShift)

        /**
         * Les familles de **cascade** : l'arrivée d'une rangée de liste. Ce n'est pas un geste (aucun
         * doigt ne la déclenche) mais elle est **courte** par construction — une liste qui se pose
         * lentement se lit comme une appli qui charge. Elle a donc sa classe : un test y refuse toute
         * cascade plus longue qu'un geste.
         */
        val cascadeFamilies: List<MotionFamily> = listOf(Row)

        /**
         * Les familles **ambiantes** : ni un geste, ni une ouverture — un mouvement qui dure tant
         * que l'écran est là, et qui ne répond à aucun doigt. La respiration de l'orbe ([Orb]) et la
         * pulsation d'un état qui travaille ([WorkPulse]). Elles sont figées sous Reduce Motion
         * comme les autres.
         */
        val ambientFamilies: List<MotionFamily> = listOf(Orb, WorkPulse)
    }
}

/**
 * **Les valeurs d'effet, nommées — rien en dur dans un écran.**
 *
 * Le propriétaire demande « l'effet waouh », et il a raison de le demander **par des règles** : une
 * échelle choisie sur place dans un bouton et une autre dans un onglet, c'est la même dérive que les
 * quatre rayons et les quatre ombres, appliquée au mouvement. Ces constantes ont chacune un chiffre
 * et une raison mesurable.
 */
object Feedback {

    /**
     * **L'échelle de l'appui : 0,96.**
     *
     * Un appui doit se **sentir** sans déplacer la mise en page — c'est la règle de `pro-rules.md`
     * (« pressed-state visuals do not shift layout bounds or cause jitter »). 0,96 retire 4 % de la
     * cible : à 44 dp cela vaut 1,8 dp, ce qui se voit sur un bord franc et ne se voit pas sur un
     * fond. Au-delà de 0,94 l'élément « saute » ; en dessous de 0,98 il ne se sent plus. La valeur
     * est **la même** pour un bouton rond, une tuile de feuille, une carte et un onglet.
     */
    const val PressScale: Float = 0.96f

    /**
     * **La vibration de l'appui : 12 ms.**
     *
     * Assez longue pour être perçue, assez courte pour ne pas « bourdonner » quand on balaie une
     * liste du doigt. Android nomme cette classe `EFFECT_CLICK` : c'est un **clic**, pas un cliquetis.
     */
    const val PressHapticsMs: Long = 12L

    /**
     * **Le pas de la cascade : 40 ms.**
     *
     * L'écart entre deux rangées d'une liste qui apparaît. `gsap` (dans `ui-ux-pro-max`, domaine
     * `gsap`, preset *Stagger List*) donne 60 ms par élément pour une grille web ; une liste de
     * téléphone est lue plus vite et ses rangées sont plus hautes — 40 ms pour la même sensation,
     * avec un plafond.
     */
    const val StaggerMs: Int = 40

    /**
     * **Le plafond de la cascade : 160 ms.**
     *
     * Au-delà, la dernière rangée d'une feuille de sept lignes arrive après que l'œil a fini de lire
     * la première — la liste paraît **lente**, pas soignée. Le décalage sature donc : les rangées
     * au-delà de la quatrième partent ensemble.
     */
    const val StaggerMaxMs: Int = 160

    /**
     * **La déformation de la capsule au passage : ×0,94 en largeur.**
     *
     * La capsule qui se déplace se **comprime** légèrement dans le sens du mouvement et reprend sa
     * largeur en se posant : c'est ce qui fait qu'un objet se déplace au lieu de sauter. 6 % de
     * compression est le plus petit écart qui se voie sur une capsule de 60 dp (3,6 dp) sans que la
     * forme paraisse écrasée.
     */
    const val TabSquash: Float = 0.94f

    /** Le dépassement du ressort de la capsule : assez pour se **poser**, pas pour rebondir. */
    const val TabOvershoot: Float = 0.18f

    /** **La respiration de l'orbe au repos : ×1,04.** Un souffle, pas une pulsation. */
    const val OrbBreath: Float = 1.04f

    /**
     * **La respiration d'un état en réflexion : +0,04.** Le même souffle que l'orbe
     * ([OrbBreath]) : l'en-tête qui respire et l'orbe qui respire ne doivent pas être deux
     * mouvements différents.
     */
    const val FlowBreath: Float = 0.04f

    /**
     * **La pulsation d'un état en travail : +0,08.** Le double de la respiration, à une cadence
     * plus vive (1,6 s contre 2,6 s) : c'est ce qui fait qu'un travail se **voit** à côté d'une
     * réflexion, sans changer de couleur ni écrire un mot de plus.
     */
    const val FlowPulse: Float = 0.08f

    /**
     * **Le halo de l'état, au maximum : 0,30.** Il n'y a qu'**un** accent dans l'app ; l'état le
     * porte en opacité, jamais en teinte. 0,30 sur la page reste une lueur — au-delà, la pastille
     * devient un aplat coloré et l'état se lit comme une alarme.
     */
    const val FlowGlowMax: Float = 0.30f
}
