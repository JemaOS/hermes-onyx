package com.onyx.app.ui.design

/**
 * La table de correspondance **la référence → Android** des animations, extraite de `Echo/Views`.
 *
 * Chaque ligne vient d'un site réel de la référence (fichier:ligne + la ligne elle-même) et nomme son
 * équivalent Kotlin. `MotionTableTest` vérifie trois choses que la prose ne peut pas garantir :
 *  1. la ligne la référence existe bien dans `MOTION-INVENTORY.tsv`, au numéro annoncé, avec le texte
 *     annoncé — la table n'invente aucune source ;
 *  2. l'équivalent Kotlin est **présent** dans le fichier annoncé, mot pour mot ;
 *  3. les conversions de ressorts (`dampingRatio`, `stiffness`) sont bien celles que produisent
 *     [OnyxMotion.springReference] / [OnyxMotion.springSnappy] pour la durée et le rebond de la référence, et
 *     que les courbes sont les cubic-bezier de la référence.
 *
 * Statuts :
 *  - [MotionStatus.PORTE] — même durée, même courbe (ou même ressort converti) des deux côtés ;
 *  - [MotionStatus.APPROXIMATIF] — la forme est là, la valeur exacte n'est pas publiable
 *    (ressorts natifs de la référence, reprise de vélocité d'un geste, positions d'items de liste) ;
 *  - [MotionStatus.REDUIT] — le site coupe ou adoucit son animation sous « Réduire les animations »,
 *    comme le `@Environment(\.accessibilityReduceMotion)` de la référence ;
 *  - [MotionStatus.ABSENT] — rien côté Android, avec la raison en note.
 */
object MotionTable {

    /** Où en est un site : porté, approché, ou franchement absent. */
    enum class MotionStatus(val label: String) {
        PORTE("porté"),
        APPROXIMATIF("approximatif"),
        REDUIT("porté + Reduce Motion"),
        ABSENT("absent"),
    }

    /**
     * Une ligne de la table.
     *
     * @param référence `Views/<fichier>.référence:<ligne>`, la source qui fait foi.
     * @param sourceSpec un fragment de la ligne de référence, tel quel — sert à la retrouver dans le relevé.
     * @param android l'équivalent Kotlin, mot pour mot dans [androidFile].
     * @param androidFile le fichier Kotlin concerné, relatif à la racine du dépôt.
     * @param springDuration la durée du ressort de référence, en secondes.
     * @param springBounce le rebond effectif côté référence (`bounce` + `extraBounce`).
     * @param snappy true si la référence nomme `.snappy` (rebond 0,15 par défaut chez la référence).
     * @param dampingRatio l'amortissement Compose attendu.
     * @param stiffness la raideur Compose attendue.
     * @param note ce qui reste à dire : pourquoi c'est approché, ou ce qui manque.
     */
    data class MotionEntry(
        val référence: String,
        val sourceSpec: String,
        val android: String,
        val status: MotionStatus,
        val androidFile: String,
        val springDuration: Double? = null,
        val springBounce: Double? = null,
        val snappy: Boolean = false,
        val dampingRatio: Float? = null,
        val stiffness: Float? = null,
        val note: String = "",
    )

    private const val MOTION = "core/src/main/java/com/onyx/app/ui/design/OnyxMotion.kt"
    private const val IOS_CONTROLS = "core/src/main/java/com/onyx/app/ui/design/OnyxControls.kt"

    private const val VOICE_SCREEN = "app/src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt"
    private const val VOICE_MOTION = "app/src/main/java/com/onyx/app/ui/voice/VoiceLiveMotion.kt"
    private const val RISING_CAPTION = "app/src/main/java/com/onyx/app/ui/voice/RisingCaption.kt"
    private const val DRIFTING_BLOBS = "app/src/main/java/com/onyx/app/ui/voice/DriftingBlobs.kt"
    private const val ORB_LIGHTS = "app/src/main/java/com/onyx/app/ui/voice/OrbLights.kt"
    private const val MESSAGE_ROW = "app/src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt"
    private const val START_SCREEN = "app/src/main/java/com/onyx/app/ui/chat/components/StartScreen.kt"
    private const val COMPOSER = "app/src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt"
    private const val FOLLOW_UP = "app/src/main/java/com/onyx/app/ui/chat/components/FollowUpChips.kt"
    private const val CHAT_SCREEN = "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt"
    private const val ARRIVAL = "app/src/main/java/com/onyx/app/ui/common/MessageArrival.kt"
    private const val TYPED_TITLE = "app/src/main/java/com/onyx/app/ui/common/TypedTitle.kt"

    /**
     * Les lignes, écran principal par écran principal : orbe et voix, légende, arrivées de
     * messages, rangée de message, écran de départ, composeur, panneau, listes.
     */
    val entries: List<MotionEntry> = listOf(

        // — Orbe vocal : les boucles décoratives, toutes coupées par la référence sous Reduce Motion —

        entry(
            "Views/VoiceView:1269",
            "TimelineView(.animation(minimumInterval: live ? 1 / 60 : 1 / 30, paused: reduceMotion))",
            "ambientLoop(rest = 0f)", MotionStatus.REDUIT, VOICE_MOTION,
            note = "`paused: reduceMotion` : la crête du waveform ne passe plus",
        ),
        entry(
            "Views/VoiceView:1280", "drive == nil && !reduceMotion ? ripple(i, t) : 0",
            "ambientState {", MotionStatus.REDUIT, VOICE_MOTION,
            note = "sous Reduce Motion les deux horloges de la vague (ondulation, respiration) " +
                "ne sont pas composées : elles restent au repos",
        ),
        entry(
            "Views/VoiceView:1159",
            ".easeOut(duration: period).repeatForever(autoreverses: false)",
            "OnyxMotion.MS_4500", MotionStatus.PORTE, VOICE_MOTION,
            note = "les halos : `easeOut` 2,6 s en activité, 4,5 s au repos",
        ),
        entry(
            "Views/VoiceView:1169",
            ".animation(reduceMotion ? nil : .easeOut(duration: period).repeatForever(autoreverses: false).delay(delay), value: expand)",
            "ambientLoop(rest = 0f)", MotionStatus.REDUIT, VOICE_MOTION,
            note = "le deuxième anneau garde son délai de 1,3 s",
        ),
        entry(
            "Views/VoiceView:740", "guard !reduceMotion else { return }",
            "ambientLoop(0f)", MotionStatus.REDUIT, DRIFTING_BLOBS,
            note = "les cinq boucles de DriftingBlobs (2,8 / 3,9 / 2,3 / 6 s, rotation 16 s)",
        ),
        entry(
            "Views/VoiceView:745", ".linear(duration: 16).repeatForever(autoreverses: false)",
            "tween(16000, easing = OnyxMotion.Linear)", MotionStatus.PORTE, DRIFTING_BLOBS,
        ),
        entry(
            "Views/VoiceView:750", ".linear(duration: 2).repeatForever(autoreverses: false)",
            "tween(2000, easing = OnyxMotion.Linear)", MotionStatus.PORTE, DRIFTING_BLOBS,
            note = "le spin n'existe que quand la salle tourne",
        ),
        entry(
            "Views/VoiceView:799", "guard !reduceMotion else { return }",
            "ambientLoop(0f)", MotionStatus.REDUIT, ORB_LIGHTS,
            note = "AuroraLight : rubans et inclinaison restent à leur repos",
        ),
        entry(
            "Views/VoiceView:801", ".easeInOut(duration: 4.0 * speed).repeatForever(autoreverses: true)",
            "loop(4.0f * speed)", MotionStatus.PORTE, ORB_LIGHTS,
            note = "4,0 / 5,5 / 3,2 / 7 s, × 0,45 quand la salle tourne",
        ),
        entry(
            "Views/VoiceView:853", "guard !reduceMotion else { return }",
            "ambientLoop(0f)", MotionStatus.REDUIT, ORB_LIGHTS,
            note = "CoreLight : le pulse, les orbites et les montées restent au repos",
        ),
        entry(
            "Views/VoiceView:847",
            ".easeIn(duration: 3.4).repeatForever(autoreverses: false).delay(Double(i) * 1.1)",
            "delayMillis = index * 1100", MotionStatus.PORTE, ORB_LIGHTS,
            note = "trois particules en `easeIn` 3,4 s, décalées de 1,1 s",
        ),
        entry(
            "Views/VoiceView:865",
            ".linear(duration: swirling ? 2.2 : 9).repeatForever(autoreverses: false)",
            "infiniteRepeatable(tween(orbitDuration, easing = OnyxMotion.Linear))",
            MotionStatus.PORTE, ORB_LIGHTS,
        ),

        // — Le mot d'orbe, le titre de phase, la légende mot à mot —

        entry(
            "Views/VoiceView:51", ".animation(.snappy(duration: 0.35), value: phaseTitle)",
            "springReference(0.35, 0.15)", MotionStatus.PORTE, MOTION,
            springDuration = 0.35, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
        ),
        entry(
            "Views/VoiceView:48", ".transition(.push(from: .bottom).combined(with: .opacity))",
            "slideInVertically(animationSpec = OnyxMotion.Sites.VoicePhaseTitleOffset)",
            MotionStatus.PORTE, VOICE_SCREEN,
        ),
        entry(
            "Views/VoiceView:1188", ".animation(.easeOut(duration: 0.25), value: words.count)",
            "captionRows(places.map { it.width }, available, gap)", MotionStatus.PORTE, RISING_CAPTION,
            note = "le flux centré de la légende (`CenteredFlow`, 1181-1234) : 6 pt entre mots, 2 pt entre lignes",
        ),
        entry(
            "Views/VoiceView:1188", ".animation(.easeOut(duration: 0.25), value: words.count)",
            "OnyxMotion.Sites.CaptionWord", MotionStatus.PORTE, RISING_CAPTION,
            note = "chaque mot monte de 8 pt, en fondu de 0,25 s",
        ),
        entry(
            "Views/VoiceView:1184",
            ".transition(reduceMotion ? .opacity : .offset(y: 8).combined(with: .opacity))",
            "val lift = if (reduceMotion) 0f", MotionStatus.REDUIT, RISING_CAPTION,
        ),
        entry(
            "Views/VoiceView:1267", "live = !reduceMotion && (phase == .listening || phase == .speaking)",
            "ambientLoop(rest = 0f)", MotionStatus.PORTE, VOICE_MOTION,
        ),

        // — Passation de visage (groupe) : le ressort, et son repli en fondu —

        entry(
            "Views/VoiceView:599",
            ".animation(reduceMotion ? .easeInOut(duration: 0.35) : .spring(duration: 0.6, bounce: 0.3), value: replySpeaker)",
            "OnyxMotion.Sites.VoiceHandoffReduced", MotionStatus.REDUIT, VOICE_SCREEN,
        ),
        entry(
            "Views/VoiceView:597", ".transition(reduceMotion ? .opacity : .handoff)",
            "springReference(0.60, 0.30)", MotionStatus.PORTE, MOTION,
            springDuration = 0.60, springBounce = 0.30, dampingRatio = 0.70f, stiffness = 200f,
            note = "offset ±150 dp, échelle 0,55, fondu — c'est le `.handoff` de la référence",
        ),
        entry(
            "Views/VoiceView:603", ".animation(.easeOut(duration: 0.12), value: level)",
            "OnyxMotion.Sites.VoiceLevel", MotionStatus.PORTE, VOICE_SCREEN,
        ),
        entry(
            "Views/VoiceView:457", ".animation(.easeOut(duration: 0.3), value: phase)",
            "OnyxMotion.Sites.VoicePhase", MotionStatus.PORTE, VOICE_SCREEN,
        ),

        // — Arrivées de messages —

        entry(
            "Views/MessageArrival:52",
            "withAnimation(.spring(duration: 0.44, bounce: 0.2)) { landed = true }",
            "OnyxMotion.Sites.MessageLanded", MotionStatus.PORTE, ARRIVAL,
            springDuration = 0.44, springBounce = 0.20, dampingRatio = 0.80f, stiffness = 380f,
        ),
        entry(
            "Views/MessageArrival:51", "withAnimation(.easeOut(duration: 0.16)) { visible = true }",
            "OnyxMotion.Sites.MessageVisible", MotionStatus.PORTE, ARRIVAL,
        ),
        entry(
            "Views/MessageArrival:54",
            "withAnimation(.easeOut(duration: 0.25).delay(reduceMotion ? 0 : 0.24)) { visible = true }",
            "OnyxMotion.Sites.MessageVisibleImmediate", MotionStatus.REDUIT, ARRIVAL,
            note = "les 240 ms du battement tombent à zéro sous Reduce Motion",
        ),
        entry(
            "Views/MessageArrival:41", "let rising = kind == .sent && !landed && !reduceMotion",
            "&& !landed && !reduceMotion", MotionStatus.REDUIT, ARRIVAL,
        ),
        entry(
            "Views/TranscriptView:322", ".animation(.spring(duration: 0.44, bounce: 0.2))",
            "OnyxMotion.Sites.MessageLanded", MotionStatus.PORTE, MOTION,
            springDuration = 0.44, springBounce = 0.20, dampingRatio = 0.80f, stiffness = 380f,
        ),
        entry(
            "Views/TranscriptView:528", "guard live, !reduceMotion else { dim = false; return }",
            "ambientLoop(1f)", MotionStatus.REDUIT, CHAT_SCREEN,
            note = "le point « nouveaux messages » reste plein au lieu de pulser",
        ),
        entry(
            "Views/TranscriptView:529",
            "withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true))",
            "OnyxMotion.MS_600", MotionStatus.PORTE, CHAT_SCREEN,
        ),

        // — Rangée de message : repli, coche, shimmer, copie, métriques —

        entry(
            "Views/MessageRow:245", "withAnimation(.snappy(duration: 0.3)) { showThinking = !open }",
            "springSnappy(0.30)", MotionStatus.PORTE, MOTION,
            springDuration = 0.30, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
        ),
        entry(
            "Views/MessageRow:335", ".animation(.snappy(duration: 0.3), value: tools.count)",
            "OnyxMotion.Sites.MessageFold", MotionStatus.PORTE, MESSAGE_ROW,
            springDuration = 0.30, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
        ),
        entry(
            "Views/MessageRow:183", "withAnimation(.snappy(duration: 0.2)) { copied = true }",
            "OnyxMotion.Sites.Snappy200", MotionStatus.PORTE, MESSAGE_ROW,
            springDuration = 0.20, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
        ),
        entry(
            "Views/MessageRow:621", "withAnimation(.easeOut(duration: 0.4).delay(0.05)) { drawn = 1 }",
            "OnyxMotion.Sites.StepCheck", MotionStatus.PORTE, MESSAGE_ROW,
        ),
        entry(
            "Views/MessageRow:620", "if animated, !reduceMotion {",
            "ambientLoop(1f)", MotionStatus.REDUIT, MESSAGE_ROW,
            note = "la coche d'un outil est posée d'un coup, sans tracé",
        ),
        entry(
            "Views/MessageRow:557",
            "withAnimation(.linear(duration: 1.8).repeatForever(autoreverses: false)) { phase = 1.2 }",
            "OnyxMotion.Sites.Shimmer", MotionStatus.PORTE, MOTION,
        ),
        entry(
            "Views/MessageRow:544", "if !reduceMotion {",
            "val reduceMotion = LocalReduceMotion.current", MotionStatus.REDUIT, IOS_CONTROLS,
            note = "le shimmer est du texte d'aplat sous Reduce Motion",
        ),
        entry(
            "Views/MessageRow:435", "withAnimation(.easeOut(duration: 0.15)) { fullMetrics.toggle() }",
            "OnyxMotion.Sites.MetricsToggle", MotionStatus.PORTE, MESSAGE_ROW,
        ),
        entry(
            "Views/MessageRow:170", ".animation(.easeOut(duration: 0.3), value: isLive)",
            "OnyxMotion.Sites.MessageActions", MotionStatus.PORTE, MESSAGE_ROW,
        ),
        entry(
            "Views/MessageRow:694", "withAnimation(.snappy(duration: 0.25)) { open.toggle() }",
            "", MotionStatus.ABSENT, MESSAGE_ROW,
            note = "la divulgation des fichiers livrés (DeliveredMessage) et la note de tour inventé " +
                "(InventedTurnNote) n'existent pas côté Android : la rangée des fichiers est posée " +
                "d'un bloc, sans rien à ouvrir — le jeton Snappy250 reste déclaré pour le jour où elle le sera",
        ),
        entry(
            "Views/MessageRow:764", "withAnimation(.snappy(duration: 0.25)) { open.toggle() }",
            "", MotionStatus.ABSENT, MESSAGE_ROW,
            note = "même cas : `InventedTurnNote` n'a pas de contrepartie Android",
        ),
        entry(
            "Views/MessageRow:200", "TimelineView(.periodic(from: message.createdAt, by: 1))",
            "System.currentTimeMillis() - message.createdAt < 1_500", MotionStatus.APPROXIMATIF,
            CHAT_SCREEN,
            note = "l'âge relatif se rafraîchit à la recomposition, pas une fois par seconde",
        ),

        // — Étincelle vivante (LiveMark) —

        entry(
            "Views/StreamingTail:135",
            "withAnimation(.easeOut(duration: 1.6).repeatForever(autoreverses: false)) { pulse = true }",
            "tween(durationMillis = OnyxMotion.MS_1600, easing = OnyxMotion.EaseOut)",
            MotionStatus.PORTE, IOS_CONTROLS,
        ),
        entry(
            "Views/StreamingTail:134", "guard !reduceMotion else { return }",
            "ambientLoop(0f)", MotionStatus.REDUIT, IOS_CONTROLS,
        ),

        // — Écran de départ —

        entry(
            "Views/StartScreen:262", ".animation(.easeOut(duration: 0.4), value: shown > index)",
            "OnyxMotion.Sites.ArrivingPiece", MotionStatus.PORTE, START_SCREEN,
        ),
        entry(
            "Views/StartScreen:121",
            "if !reduceMotion { try? await Task.sleep(for: .milliseconds(count == 1 ? 60 : 90)) }",
            "reducedStagger(60)", MotionStatus.REDUIT, START_SCREEN,
            note = "60 ms puis 90 ms par pièce, zéro sous Reduce Motion",
        ),
        entry(
            "Views/StartScreen:152",
            "withAnimation(.easeOut(duration: 2.6).repeatForever(autoreverses: false)) { pulse = true }",
            "OnyxMotion.Sites.MarkPulse", MotionStatus.PORTE, START_SCREEN,
        ),
        entry(
            "Views/StartScreen:151", "guard !reduceMotion else { return }",
            "ambientLoop(0f)", MotionStatus.REDUIT, START_SCREEN,
            note = "l'anneau de la marque reste à son repos",
        ),
        entry(
            "Views/StartScreen:112", ".animation(.easeOut(duration: 0.35), value: cards.map(\\.id))",
            ".arriving(index = 4 + index, shown = shown)", MotionStatus.APPROXIMATIF,
            START_SCREEN,
            note = "le conteneur des cartes n'anime pas la liste : chaque carte arrive par `Arriving`, " +
                "puis par la **cascade** `Modifier.rowArrival` ([MotionFamily.Row], 180 ms, pas 40 ms, " +
                "plafonnée à 160 ms) — la famille qui manquait aux listes de l'app",
        ),

        // — Composeur —

        entry(
            "Views/ComposerView:137", ".animation(.snappy(duration: 0.3), value: pendingAttachments.count)",
            "OnyxMotion.Sites.ComposerRowSize", MotionStatus.PORTE, COMPOSER,
            springDuration = 0.30, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
        ),
        entry(
            "Views/ComposerView:139", ".animation(.snappy(duration: 0.3), value: contextSuggestions)",
            "OnyxMotion.Sites.ComposerRowSize", MotionStatus.PORTE, COMPOSER,
            springDuration = 0.30, springBounce = 0.15, snappy = true,
            dampingRatio = 0.85f, stiffness = 620f,
            note = "un seul `animateContentSize` couvre les trois valeurs de ComposerView (:137-:139)",
        ),
        entry(
            "Views/ComposerView:555", ".animation(.snappy(duration: 0.3, extraBounce: 0.15), value: trailingState)",
            "OnyxMotion.Sites.ComposerTrailing", MotionStatus.PORTE, COMPOSER,
            springDuration = 0.30, springBounce = 0.30, snappy = true,
            dampingRatio = 0.70f, stiffness = 620f,
            note = "rebond 0,15 + extraBounce 0,15 = 0,30",
        ),
        entry(
            "Views/Composer:95", "still: still || reduceMotion",
            "val frozen = still || reduceMotion", MotionStatus.REDUIT, IOS_CONTROLS,
            note = "la forme d'onde du composeur se fige",
        ),

        // — Titre qui s'écrit, relances, ouvertures, panneau, listes —

        entry(
            "Views/TypedTitle:95",
            "withAnimation(.easeInOut(duration: 0.4).repeatForever(autoreverses: true)) { on = false }",
            "OnyxMotion.MS_400", MotionStatus.PORTE, TYPED_TITLE,
            note = "le curseur clignote : la référence ne le coupe PAS sous Reduce Motion",
        ),
        entry(
            "Views/TypedTitle:66", "withAnimation(.easeIn(duration: 0.22)) { leaving = nil }",
            "OnyxMotion.easeIn(OnyxMotion.MS_220)", MotionStatus.PORTE, TYPED_TITLE,
        ),
        entry(
            "Views/TypedTitle:51",
            "guard old.id == new.id, old.placeholder, !new.placeholder, !reduceMotion else {",
            "val shouldType = wasPlaceholder && !isPlaceholder && title.isNotEmpty() && !reduceMotion",
            MotionStatus.REDUIT, TYPED_TITLE,
            note = "sous Reduce Motion, le titre paraît d'un coup",
        ),
        entry(
            "Views/FollowUpChips:44",
            "withAnimation(.snappy(duration: 0.35, extraBounce: 0.1)) { shown = i }",
            "OnyxMotion.Sites.FollowUpChip", MotionStatus.PORTE, FOLLOW_UP,
            springDuration = 0.35, springBounce = 0.25, snappy = true,
            dampingRatio = 0.75f, stiffness = 620f,
            note = "rebond 0,15 + extraBounce 0,10 = 0,25",
        ),
        entry(
            "Views/FollowUpChips:43",
            "if !reduceMotion { try? await Task.sleep(for: .milliseconds(i == 1 ? 40 : 90)) }",
            "reducedStagger(40)", MotionStatus.REDUIT, FOLLOW_UP,
        ),
        entry(
            "Views/OpeningCover:80",
            "withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.28 * slow))",
            "OnyxMotion.Sites.CoverGrowth", MotionStatus.PORTE, IOS_CONTROLS,
            note = "0,28 s exact — le facteur `slow` des tests n'a pas lieu d'être",
        ),
        entry(
            "Views/SwipeToOpen:236", "static var sidePanel: Animation { .smooth(duration: 0.32) }",
            "OnyxMotion.Sites.SidePanel", MotionStatus.APPROXIMATIF, IOS_CONTROLS,
            note = "`.smooth` est un ressort du système ; reproduit en `easeInOut` 0,32 s",
        ),
        entry(
            "Views/SwipeToOpen:204",
            "animation = .interpolatingSpring(duration: 0.34, bounce: 0, initialVelocity: relative)",
            "OnyxMotion.Sites.SidePanelOffset", MotionStatus.APPROXIMATIF, IOS_CONTROLS,
            note = "la vitesse du geste n'est pas reprise : Android rejoue la courbe fixe",
        ),
        entry(
            "Views/Toast:25", ".animation(.easeOut(duration: 0.2), value: text)",
            "OnyxMotion.Sites.Toast", MotionStatus.PORTE, IOS_CONTROLS,
        ),
        entry(
            "Views/VoiceView:660", "TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion))", "",
            MotionStatus.ABSENT, VOICE_MOTION,
            note = "les lumières de l'orbe Onyx sont dessinées au Canvas, pas par une horloge de vue",
        ),
    )

    /** Le comptage par statut, tel que le rapport final le cite. */
    fun countsByStatus(): Map<MotionStatus, Int> =
        MotionStatus.entries.associateWith { status -> entries.count { it.status == status } }

    /** Les écrans couverts, pour vérifier qu'aucun n'a été oublié. */
    fun sourceViews(): Set<String> = entries.map { it.référence.substringBefore(':') }.toSet()

    private fun entry(
        référence: String,
        sourceSpec: String,
        android: String,
        status: MotionStatus,
        androidFile: String,
        springDuration: Double? = null,
        springBounce: Double? = null,
        snappy: Boolean = false,
        dampingRatio: Float? = null,
        stiffness: Float? = null,
        note: String = "",
    ) = MotionEntry(
        référence = référence,
        sourceSpec = sourceSpec,
        android = android,
        status = status,
        androidFile = androidFile,
        springDuration = springDuration,
        springBounce = springBounce,
        snappy = snappy,
        dampingRatio = dampingRatio,
        stiffness = stiffness,
        note = note,
    )
}
