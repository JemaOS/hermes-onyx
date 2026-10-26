package com.onyx.app.ui.voice

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import com.onyx.app.ui.design.OnyxMotion
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.core.R
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.ui.bots.GroupVoiceLine
import com.onyx.app.ui.bots.GroupVoiceScene
import com.onyx.app.ui.bots.MineRow
import com.onyx.app.ui.bots.RoomMemberRow
import com.onyx.app.ui.bots.groupVoicePhaseTitle
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.botFaceAuraColour
import com.onyx.app.ui.design.ComposerCircleButton
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.GlassPill
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSlider
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.OnyxValueRow
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.OrbAvatar
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.WaveformPulse
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.voice.TalkOver
import com.onyx.app.voice.VoicePhase
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Le bouton rouge arrête toujours l'activité; au repos, « End voice mode » ferme aussi la scène. */
internal fun soloVoiceStop(phase: VoicePhase, allowEnd: Boolean = true, stop: () -> Unit, end: () -> Unit) {
    stop()
    if (phase == VoicePhase.IDLE && allowEnd) end()
}

/**
 * Le mode vocal: l'orbe au centre, ce qui est dit dessous, et trois boutons en bas.
 *
 * Comme: les mêmes anneaux concentriques, le même orbe de verre dont les
 * barres suivent la voix, et le même mot d'état en petites capitales espacées. S'y ajoutent
 * l'en-tête (titre + état mains-libres), le niveau par barre sous l'orbe, l'accès aux réglages
 * vocaux, et l'aide quand le micro est refusé ou que le moteur ne répond pas.
 *
 * Ce second passage complète le implémentation avec, dans l'ordre où les montre sous l'orbe:
 * le choix de l'orbe (une rangée de pastilles), le panneau de réglages vocaux en ligne, la liste
 * des derniers échanges en « live captions », le chronomètre d'écoute et le compteur de tours, et
 * les états d'échec du moteur (pas de reconnaissance, permission refusée, réseau requis).
 *
 * Tous les ajouts sont des paramètres **optionnels avec valeur par défaut**: l'appelant existant
 * ne casse pas, et chaque réglage porte son propre rappel (`on…`), `null` laissant le panneau
 * fonctionner en local sur son propre état.
 */
@Composable
fun VoiceScreen(
    history: List<VoiceTurn> = emptyList(),
    phase: VoicePhase,
    heard: String,
    spoken: String,
    level: Float,
    /**
     * Le niveau de la voix lue, de 0 à 1: c'est ce qui fait battre la bouche du visage qui parle.
     * Rien quand aucune voix ne sort.
     */
    speakingLevel: Float = 0f,
    handsFree: Boolean,
    conversationTitle: String,
    problem: String?,
    /** La pastille « Hands-free »; `null`: aucun contrôle (une salle est toujours main-libre). */
    onToggleHandsFree: (() -> Unit)? = null,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onBackToKeyboard: () -> Unit,
    /** `VoiceView.group`: la salle, ses visages et son round, plutôt que la scène solo. */
    group: GroupVoiceScene? = null,
    /** Pause/Play pendant la lecture du bot courant. */
    isPaused: Boolean = false,
    onPauseResume: () -> Unit = {},
    /** L'orbe de groupe interrompt la lecture et rouvre le micro. */
    onOrbClick: () -> Unit = {},
    /** Réglages → Voix: `null` cache l'accès (aucun bouton n'est dessiné). */
    onOpenSettings: (() -> Unit)? = null,
    // — Choix de l'orbe (design/icons/orbs) —
    /** L'orbe affiché: Onyx (dessiné par `LiquidOrb`) ou l'une des faces-image. */
    orb: VoiceOrbChoice = VoiceOrbChoice.ONYX,
    /** Appelé quand une pastille est touchée; `null` laisse le choix se faire en local. */
    onOrbChange: ((VoiceOrbChoice) -> Unit)? = null,
    /** `Settings.shared.orbLight`: ce qui bouge dans la face de l'orbe Onyx. */
    orbLight: OrbLight = OrbLight.NEBULA,
    /** Appelé quand une lumière est choisie; `null` laisse le choix se faire en local. */
    onOrbLightChange: ((OrbLight) -> Unit)? = null,
    // — Panneau de réglages vocaux en ligne (la section sous l'orbe) —
    /** Réglages détaillés: uniquement dans l'écran Voice settings, pas sur la scène vocale. */
    showVoiceSettings: Boolean = false,
    /** Langue d'écoute: vide affiche la langue de l'appareil Android. */
    listeningLanguage: String = "",
    /** Langue de réponse: vide affiche « Automatic », comme. */
    replyLanguage: String = "",
    /** Vitesse de la voix, de 0,5 à 2,0 (le pas du `Slider` ). */
    voiceSpeed: Float = 1f,
    /** « Start voice mode in hands-free »: arrivé directement en mains-libres. */
    handsFreeByDefault: Boolean = false,
    /** « Talk over replies »: quand Onyx se tait pour vous écouter. */
    talkOverReplies: TalkOverChoice = TalkOverChoice.HEADPHONES,
    /** Langue d'écoute: présente le choix de langue; `null` rend la rangée inerte. */
    onPickListeningLanguage: (() -> Unit)? = null,
    /** Langue de réponse: présente le choix de langue; `null` rend la rangée inerte. */
    onPickReplyLanguage: (() -> Unit)? = null,
    onVoiceSpeedChange: ((Float) -> Unit)? = null,
    onHandsFreeByDefaultChange: ((Boolean) -> Unit)? = null,
    onTalkOverChange: ((TalkOverChoice) -> Unit)? = null,
    // — Liste des derniers échanges (« live captions ») —
    /** Le nom de l'agent, à la place de « You » pour ses répliques. */
    agentName: String = "Onyx",
    /** Vrai tant que la dernière réplique s'écrit: allume l'indicateur sur cet échange. */
    streaming: Boolean = false,
    // — Chronomètre et tours de la session —
    /** Durée d'écoute en cours, en secondes: affiche « Listening for 12 s ». */
    listeningSeconds: Int? = null,
    /** Le nombre de tours envoyés dans la session: affiche « 3 turns ». */
    sessionTurns: Int? = null,
    // — États d'échec du moteur —
    /** Interdit d'écouter: pas de reconnaissance, permission refusée, réseau requis. */
    engineFailure: VoiceEngineFailure? = null,
    /** « Try again » sous l'échec du moteur; `null` n'affiche pas de bouton. */
    onRetryEngine: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val text = if (phase == VoicePhase.SPEAKING) spoken else heard
    val statusTitle = group?.let {
        groupVoicePhaseTitle(phase, it.speaker?.label(), it.writer?.label(), it.isTalking)
    } ?: phaseTitle(phase, agentName)
    val statusColor = if (group != null && phase == VoicePhase.IDLE && group.isTalking) {
        theme.warning
    } else {
        phaseColor(theme, phase)
    }
    // « Quelque chose tourne »: on écoute, on réfléchit ou on lit la réponse. C'est ce qui fait
    // du bouton rouge un carré « Stop » plutôt qu'une croix « quitter », comme `solIsWorking`.
    val working = if (group != null) {
        phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING ||
            (group.isTalking && !group.isHeld)
    } else {
        phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING || phase == VoicePhase.SPEAKING || streaming
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .statusBarsPadding(),
    ) {
        // `VoiceView.header`: une salle a son FaceStack et son nom; pas de toggles solo.
        if (group != null) {
            GroupVoiceHeader(group)
        } else {
            VoiceHeader(
                conversationTitle = conversationTitle,
                handsFree = handsFree,
                onToggleHandsFree = onToggleHandsFree,
                onOpenSettings = onOpenSettings,
            )
        }

        // L'aide hands-free est dans le footer, comme `VoiceView.footer`.

        // `Settings.shared.orbLight`: choix de la lumière dessinée dans l'orbe Onyx.
        // Il vient des réglages quand l'appelant le fournit; sinon le panneau le règle en local.
        var localOrbLight by rememberSaveable { mutableStateOf(orbLight) }
        val shownOrbLight = if (onOrbLightChange != null) orbLight else localOrbLight

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // `VoiceView` place l'orbe à 28 pt sous l'en-tête; la conversation occupe le reste.
            Spacer(Modifier.size(28.dp))
            // `OrbFace`: les visages de toute la salle, puis handoff sur celui qui est lu.
            if (group != null) {
                GroupVoiceOrbDisplay(
                    group = group,
                    phase = phase,
                    level = level,
                    speakingLevel = speakingLevel,
                    onClick = onOrbClick,
                )
            } else {
                VoiceOrbDisplay(
                    agentName = agentName,
                    choice = orb,
                    light = shownOrbLight,
                    level = level,
                    phase = phase,
                    listening = phase == VoicePhase.LISTENING,
                    //: l'orbe est un bouton, son action celle de la phase.
                    onClick = onOrbClick,
                )
            }

            // Skins et réglages détaillés restent dans Voice settings, comme.
            if (showVoiceSettings) {
                Spacer(Modifier.size(10.dp))
                OrbChooserRow(
                    agentName = agentName,
                    selected = orb,
                    onSelect = { choice ->
                        onOrbChange?.invoke(choice)
                    },
                )
                Spacer(Modifier.size(6.dp))
                LevelBars(level = level, phase = phase)
            }

            // Le mot d'état suit directement l'orbe: 18 pt, comme `VoiceView`.
            Spacer(Modifier.size(18.dp))
            // `phaseTitle`: le nouveau mot pousse l'ancien vers le haut, avec `.snappy(0,35)`.
            AnimatedContent(
                targetState = OnyxLocalization[statusTitle],
                transitionSpec = {
                    (
                        slideInVertically(animationSpec = OnyxMotion.Sites.VoicePhaseTitleOffset) { it } +
                            fadeIn(animationSpec = OnyxMotion.Sites.VoicePhaseTitle)
                        ).togetherWith(
                        slideOutVertically(animationSpec = OnyxMotion.Sites.VoicePhaseTitleOffset) { -it } +
                            fadeOut(animationSpec = OnyxMotion.Sites.VoicePhaseTitle),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
                label = "voicePhaseTitle",
            ) { title ->
                BasicText(
                    text = title.uppercase(),
                    localize = false,
                    //: `.footnote.weight(.semibold)` et `.tracking(1.2)`.
                    // **Centré sous l'orbe**, comme le modèle: le noeud occupe toute la largeur
                    // (c'est ce qui laisse le mot suivant pousser le précédent d'un bloc), donc
                    // sans cet alignement le texte se collait à la marge de gauche, à hauteur du
                    // fil, alors que le modèle le pose au milieu sous le visage.
                    style = theme.footnote.copy(
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            // Le chronomètre d'écoute et le compteur de tours de la session (item 4), alignés sous
            // le mot d'état: « Listening for 12 s » pendant qu'on écoute, puis « 3 turns ».
            if (listeningSeconds != null || sessionTurns != null) {
                Spacer(Modifier.size(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listeningSeconds?.let { seconds ->
                        BasicText(
                            text = "Listening for $seconds s",
                            style = theme.monospacedFootnote.copy(color = theme.secondaryText),
                        )
                    }
                    sessionTurns?.let { turns ->
                        BasicText(
                            text = if (turns == 1) "1 turn" else "$turns turns",
                            style = theme.monospacedFootnote.copy(color = theme.tertiaryText),
                        )
                    }
                }
            }

            // `captionArea`: pendant l'écoute, la transcription en grand, **mot à mot**
            // (`RisingCaption`); sinon le dernier échange. Le placeholder ne cohabite jamais
            // avec un historique.
            if (phase == VoicePhase.LISTENING || (group == null && history.isEmpty())) {
                Spacer(Modifier.size(18.dp))
                if (phase == VoicePhase.LISTENING && text.isNotEmpty()) {
                    RisingCaption(
                        text = text,
                        style = theme.title2.copy(color = theme.textColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    BasicText(
                        text = text.ifEmpty { placeholder(phase) },
                        localize = text.isEmpty(),
                        style = if (text.isEmpty()) {
                            theme.title2.copy(color = theme.tertiaryText, textAlign = TextAlign.Center)
                        } else {
                            theme.title2.copy(color = theme.textColor, textAlign = TextAlign.Center)
                        },
                    )
                }
            }

            // `groupExchange`: question, réponse de chaque membre et brouillon, dans le round.
            if (group != null && phase != VoicePhase.LISTENING) {
                Spacer(Modifier.size(14.dp))
                GroupVoiceExchange(group = group, modifier = Modifier.weight(1f))
            } else if (group == null && phase != VoicePhase.LISTENING && history.isNotEmpty()) {
                Spacer(Modifier.size(18.dp))
                LiveCaptions(
                    turns = history,
                    agentName = agentName,
                    streaming = streaming,
                )
            }
        }

        //: une salle dont un bot attend la personne le dit ici, en orange,
        // et le micro reste fermé tant que la commande n'est pas répondue dans le chat.
        if (group != null && group.waitsOnPerson) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                OnyxIcon(symbol = Symbols.HandRaised, tint = theme.warning, size = 13.dp)
                BasicText(
                    text = "A bot needs you: answer in the chat.",
                    style = theme.footnote.copy(color = theme.warning, fontWeight = FontWeight.Medium),
                )
            }
        }

        // Le panneau de réglages vocaux en ligne (item 1): langue d'écoute, langue de réponse,
        // vitesse de la voix, « Start voice mode in hands-free », « Talk over replies » — les
        // mêmes libellés et les mêmes valeurs que Réglages → Voix.
        if (showVoiceSettings) {
            VoiceSettingsPanel(
                listeningLanguage = listeningLanguage,
                replyLanguage = replyLanguage,
                voiceSpeed = voiceSpeed,
                handsFreeByDefault = handsFreeByDefault,
                talkOverReplies = talkOverReplies,
                onPickListeningLanguage = onPickListeningLanguage,
                onPickReplyLanguage = onPickReplyLanguage,
                onVoiceSpeedChange = onVoiceSpeedChange,
                onHandsFreeByDefaultChange = onHandsFreeByDefaultChange,
                onTalkOverChange = onTalkOverChange,
                orbLight = shownOrbLight,
                onOrbLightChange = { picked ->
                    localOrbLight = picked
                    onOrbLightChange?.invoke(picked)
                },
            )
        }

        // Les états d'échec du moteur (item 5): pas de reconnaissance installée, permission
        // refusée, réseau requis — avec les textes et un « Try again ».
        if (engineFailure != null) {
            EngineFailureHelp(failure = engineFailure, onRetry = onRetryEngine)
        }

        // L'aide du bas: le micro refusé ou le moteur qui ne répond pas. C'est le même `problem`
        // que porte l'appelant, posé en rouge au-dessus des commandes, comme `lookProblem`.
        ProblemHelp(problem = problem)

        if (handsFree) {
            BasicText(
                text = "Say “stop listening” or “that's all” to end hands-free.",
                style = theme.caption.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceRoundButton(
                symbol = Symbols.Keyboard,
                label = "Switch to typing",
                tint = theme.textColor,
                onClick = onBackToKeyboard,
            )
            Spacer(Modifier.width(28.dp))
            // `VoiceView.controls`: dans une salle, le bouton du milieu est **Pause / Reprendre**,
            // le ⏸ du modèle — c'est lui qui tient la réponse lue là où elle en est
            // (`SpeechOutput.pause`, `VoiceSession.pauseSpeaking`), et le carré rouge qui l'arrête.
            // Le gris de la relance n'y figure plus: le modèle met un ⏸ à cette place, et le
            // seul moment où il peut tenir quelque chose est la lecture même.
            if (group != null || phase == VoicePhase.SPEAKING) {
                VoiceRoundButton(
                    symbol = if (isPaused) Symbols.Play else Symbols.Pause,
                    label = if (isPaused) "Resume" else "Pause",
                    tint = theme.textColor,
                    // Rien à tenir tant qu'aucune voix ne sort: le bouton reste celui de la
                    // référence, sans promettre une action qui n'aurait pas lieu.
                    enabled = phase == VoicePhase.SPEAKING,
                    onClick = onPauseResume,
                )
            } else {
                // Le solo garde la relance: elle n'a de sens que là où rien n'est lu par la salle.
                VoiceRoundButton(
                    symbol = Symbols.ArrowCounterclockwise,
                    label = "Replay the last answer",
                    tint = theme.textColor,
                    enabled = phase == VoicePhase.IDLE && history.isNotEmpty(),
                    onClick = onRestart,
                )
            }
            Spacer(Modifier.width(28.dp))
            ComposerCircleButton(
                symbol = if (working) Symbols.Stop else Symbols.Xmark,
                label = if (working) "Stop" else "End voice mode",
                tint = theme.error,
                size = 60.dp,
                contentTint = Color.White,
                // `stop.fill` du modèle: le carré y occupe **0,27 du bouton** (mesuré sur ses
                // captures: 21 à 22 px dans un disque de 80). Le glyphe dessiné ne remplit que
                // 10/24 de sa boîte — à 20 points, le carré n'en faisait que 8, la moitié de la
                // référence. La croix du repos garde la boîte des autres glyphes.
                iconSize = if (working) VoiceStopGlyph.dp else 20.dp,
                onClick = onStop,
            )
        }
    }
}

/** `VoiceView.roundButton`: surface pleine, filet discret et glyphe title3 de 20 points. */
@Composable
private fun VoiceRoundButton(
    @DrawableRes symbol: Int,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(60.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .background(theme.surfaceColor, CircleShape)
            .border(0.5.dp, theme.separator, CircleShape)
            .onyxClickable(enabled = enabled, onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(symbol = symbol, tint = tint, size = 20.dp)
    }
}

/** `VoiceView.header` en salle: trois visages superposés et le nom de la salle. */
@Composable
private fun GroupVoiceHeader(group: GroupVoiceScene) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-7.7).dp)) {
            group.bots.take(3).forEach { bot ->
                Box(modifier = Modifier.background(theme.pageColor, CircleShape).padding(1.dp)) {
                    BotLook(name = bot.name, size = 22.dp, faceCode = bot.faceCode)
                }
            }
        }
        BasicText(
            text = group.roomName,
            localize = false,
            style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * L'orbe de `VoiceView.group`: tous les bots au repos, puis **le seul qui parle**, en grand, avec
 * sa couleur d'aura et sa bouche qui bat — l'orbe du modèle.
 */
@Composable
private fun GroupVoiceOrbDisplay(
    group: GroupVoiceScene,
    phase: VoicePhase,
    level: Float,
    speakingLevel: Float,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val tint = if (phase == VoicePhase.IDLE && group.isTalking) theme.warning else phaseColor(theme, phase)
    val amplitude by animateFloatAsState(
        targetValue = if (phase == VoicePhase.LISTENING) level.coerceIn(0f, 1f) else 0f,
        animationSpec = OnyxMotion.Sites.VoiceLevel,
        label = "groupVoiceAmplitude",
    )
    val speaker = group.speaker.takeIf { phase == VoicePhase.SPEAKING }
    // L'aura est **de la couleur du visage qui parle**, comme sur le modèle; la légende et les
    // anneaux gardent, eux, la couleur de la phase. La **teinte** de cette couleur suit la page
    // (`botFaceAuraColour`): le clair sur une page sombre, le profond sur une page claire — une
    // couleur profonde sur du noir ne fait qu'un halo éteint, et la claire sur du blanc un orbe
    // délavé. C'est le défaut que le propriétaire voit, et il est dans les deux thèmes.
    val aura = speaker?.let { botFaceAuraColour(it.name, it.faceCode, theme) } ?: tint
    Box(
        modifier = Modifier
            .size(233.dp)
            .onyxClickable(onClick = onClick)
            .semanticsLabel("Talk to the group"),
        contentAlignment = Alignment.Center,
    ) {
        // `OrbFace`: l'aura — large et vive pendant l'écoute, enflant avec la voix, un halo ténu
        // autrement. Un dégradé radial de la couleur du visage, large de 216 pt (297 + la voix
        // pendant l'écoute), et rien au-delà: c'est ce qui fait la lueur de la capture.
        val auraDiameter = if (phase == VoicePhase.LISTENING) 297f + amplitude * 55f else 216f
        val auraRadius = if (phase == VoicePhase.LISTENING) 143f + amplitude * 44f else 110f
        Box(
            modifier = Modifier
                .size(auraDiameter.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            aura.copy(alpha = if (phase == VoicePhase.LISTENING || speaker != null) 0.45f else 0.16f),
                            aura.copy(alpha = 0f),
                        ),
                        center = Offset.Unspecified,
                        radius = with(LocalDensity.current) { auraRadius.dp.toPx() },
                    ),
                    CircleShape,
                ),
        )
        // `Halos`: deux anneaux pendant la lecture, un seul
        // discret au repos — les mêmes que les autres faces de l'orbe.
        VoiceHalos(
            color = tint,
            subtle = phase == VoicePhase.IDLE,
            diameter = OrbHaloDiameter.dp,
        )
        // `OnyxVoiceFace`: `.shadow(color: phase == .idle ? .clear: phaseColor.opacity(0.9),
        // radius: 10)` — la lueur vive collée au visage pendant qu'il parle. Un flou de 10 pt: le
        // bord du visage est au plus vif, puis la lumière s'éteint en ~35 pt (un dégradé plus
        // court se lisait comme un anneau néon, pas comme une lueur).
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.62f to Color.Transparent,
                            0.68f to aura.copy(alpha = if (phase == VoicePhase.IDLE) 0f else 0.5f),
                            0.80f to aura.copy(alpha = if (phase == VoicePhase.IDLE) 0f else 0.16f),
                            1f to Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        val handoffDistance = with(LocalDensity.current) { 150.dp.roundToPx() }
        // `.id(replySpeaker)` + `.transition(.handoff)`: le visage qui était là s'efface vers la
        // gauche en rétrécissant, celui qui parle entre par la droite, en ressort.
        //
        // `transition(reduceMotion ? .opacity: .handoff)` et
        // `animation(reduceMotion ? .easeInOut(duration: 0.35): .spring(duration: 0.6, bounce: 0.3))`
        //: sous Reduce Motion, le visage ne glisse pas, il se fond.
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = speaker,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(OnyxMotion.Sites.VoiceHandoffReduced)
                        .togetherWith(fadeOut(OnyxMotion.Sites.VoiceHandoffReduced))
                } else {
                    (
                        slideInHorizontally(OnyxMotion.Sites.VoiceHandoffOffset) { handoffDistance } +
                            scaleIn(OnyxMotion.Sites.VoiceHandoff, initialScale = 0.55f) +
                            fadeIn(OnyxMotion.Sites.VoiceHandoff)
                        ).togetherWith(
                        slideOutHorizontally(OnyxMotion.Sites.VoiceHandoffOffset) { -handoffDistance } +
                            scaleOut(OnyxMotion.Sites.VoiceHandoff, targetScale = 0.55f) +
                            fadeOut(OnyxMotion.Sites.VoiceHandoff),
                    )
                }
            },
            label = "groupVoiceHandoff",
        ) { current ->
            if (current != null) {
                // Le grand visage de l'orbe: celui du bot qui parle, **vivant** — yeux qui
                // clignent, regard qui suit, bouche qui bat avec sa voix (`VoiceFace`, le
                // `OnyxVoiceFace`). Les autres se sont effacés vers le côté.
                VoiceFace(
                    name = current.name,
                    faceCode = current.faceCode,
                    phase = phase,
                    level = { speakingLevel },
                    size = 196.dp,
                )
            } else {
                GroupVoiceFaces(
                    group = group,
                    phase = phase,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1f + amplitude * 0.04f
                        scaleY = 1f + amplitude * 0.04f
                    },
                )
            }
        }
    }
}

/** `GroupVoiceFaces`: jusqu'à six créatures réparties dans l'empreinte de l'orbe. */
@Composable
private fun GroupVoiceFaces(group: GroupVoiceScene, phase: VoicePhase, modifier: Modifier = Modifier) {
    val shown = group.bots.take(6)
    val faceSize = when {
        shown.size <= 2 -> 104.dp
        shown.size <= 4 -> 88.dp
        else -> 74.dp
    }
    val radius = when (shown.size) {
        0, 1 -> 0f
        2 -> 56f
        else -> 66f
    }
    Box(modifier = modifier.size(233.dp), contentAlignment = Alignment.Center) {
        shown.forEachIndexed { index, bot ->
            val step = 2 * Math.PI / shown.size
            val angle = Math.PI / 2 + (if (shown.size % 2 == 0) step / 2 else 0.0) + index * step
            BotLook(
                name = bot.name,
                faceCode = bot.faceCode,
                size = faceSize,
                // Dans l'orbe, les visages au repos disent la phase: à l'écoute ils sont attentifs,
                // en réflexion ils réfléchissent, sinon ils se tiennent tranquilles.
                mood = when (phase) {
                    VoicePhase.LISTENING -> BotMood.LISTENING
                    VoicePhase.THINKING -> BotMood.THINKING
                    else -> BotMood.IDLE
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (radius * cos(angle)).dp, y = (radius * sin(angle)).dp),
            )
        }
    }
}

/** `groupExchange`: uniquement le dernier round, avec les réponses encore en cours. */
@Composable
private fun GroupVoiceExchange(group: GroupVoiceScene, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val round = group.round
    if (round.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            BasicText(
                text = "Tap the orb and talk to the group.",
                style = theme.title3.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val textLength = round.sumOf { it.text.length }
    LaunchedEffect(round.size, textLength) {
        listState.animateScrollToItem(round.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(round, key = { it.id }) { line ->
            // La réponse dont la voix sort à cet instant se distingue des autres.
            GroupVoiceLineRow(
                line = line,
                server = group.server,
                speaking = line.profile != null && line.profile == group.speakerId,
            )
        }
    }
}

/**
 * Une ligne du round de la salle: la question de la personne dans sa bulle, la réponse d'un bot
 * à plat sous son visage — exactement les lignes du fil de la salle (`MineRow` / `RoomMemberRow`),
 * comme `VoiceView.groupExchange` reprend le `MessageRow` du chat.
 */
@Composable
private fun GroupVoiceLineRow(line: GroupVoiceLine, server: ServerConfig, speaking: Boolean = false) {
    if (line.isMine) {
        MineRow(
            text = line.text,
            paths = line.paths,
            files = line.files,
            server = server,
            waiting = line.waiting,
            whenMillis = line.atMillis,
        )
    } else {
        RoomMemberRow(
            name = line.name,
            profile = line.profile.orEmpty(),
            faceCode = line.faceCode,
            text = line.text,
            live = line.live,
            speaking = speaking,
        )
    }
}

// MARK: - Le choix de l'orbe (item 2)

/**
 * Les orbes que le mode vocal peut montrer, repris de `design/icons/orbs`: le même vocabulaire,
 * le même ordre et les mêmes libellés que `VoiceOrb` (SettingsSections → VoiceOrbSettings).
 *
 * Onyx n'a pas d'image: il est **dessiné** par `LiquidOrb` (comme `GlassOrb`/`OnyxVoiceFace`
 * est dessiné ); les autres sont les images `…Blank`, sur lesquelles on pose les barres
 * vivantes. L'`id` est la valeur brute , gardée telle quelle pour que le réglage enregistré
 * corresponde à `Settings.voiceOrb`.
 *
 * « Dark Glass » manque: le dessine en code (`GlassOrb`) et ce port n'a pas cette forme; il
 * n'est donc pas proposé (les douze autres le sont).
 */
enum class VoiceOrbChoice(val id: String, val label: String, @DrawableRes val face: Int?) {
    /** L'orbe-maison, dessiné: c'est `LiquidOrb` (`onyxFace`), pas une image. */
    ONYX("onyxFace", "Onyx", null),
    /** `waveform` (« Softer Glass »), en image. */
    WAVEFORM("waveform", "Softer Glass", R.drawable.ic_waveformblank),
    /** `glassWave`. */
    GLASS("glassWave", "Glass", R.drawable.ic_glasswaveblank),
    /** `matteWave`. */
    MATTE("matteWave", "Matte", R.drawable.ic_mattewaveblank),
    /** `flatWave`. */
    FLAT("flatWave", "Flat", R.drawable.ic_flatwaveblank),
    /** `bubbleWave`. */
    BUBBLE("bubbleWave", "Bubble", R.drawable.ic_bubblewaveblank),
    /** `bubbleMatteWave`. */
    BUBBLE_MATTE("bubbleMatteWave", "Matte Bubble", R.drawable.ic_bubblemattewaveblank),
    /** `navyGlassWave` (« Navy Glass »). */
    NAVY_GLASS("navyGlassWave", "Navy Glass", R.drawable.ic_navyglasswaveblank),
    /** `navyMatteWave`. */
    NAVY_MATTE("navyMatteWave", "Navy Matte", R.drawable.ic_navymattewaveblank),
    /** `steelGlassWave`. */
    STEEL_GLASS("steelGlassWave", "Steel Glass", R.drawable.ic_steelglasswaveblank),
    /** `inkWave`. */
    INK("inkWave", "Ink", R.drawable.ic_inkwaveblank),
    /** `goldWave`: ses barres sont sombres sur le disque doré. */
    GOLD("goldWave", "Gold", R.drawable.ic_goldwaveblank),
    ;

    /** La couleur des barres sur cette face: sombres sur l'or, claires partout ailleurs. */
    val waveColor: Color
        get() = if (this == GOLD) Color(0xFF1B2230) else Color(0xFFDCE3EE)

    /** La face-bulle a une queue: son contour est un rectangle arrondi, pas un rond. */
    val isRound: Boolean get() = this != BUBBLE && this != BUBBLE_MATTE && this != ONYX

    companion object {
        /** L'orbe enregistré, ou Onyx quand rien ne correspond (défaut ). */
        fun fromId(raw: String?): VoiceOrbChoice = entries.firstOrNull { it.id == raw } ?: ONYX
    }
}

/**
 * L'orbe affiché: `LiquidOrb` pour Onyx, l'image choisie avec son anneau de phase et ses barres
 * sinon. Le même encombrement dans les deux cas (233 dp), pour que rien sous l'orbe ne bouge quand
 * on change de face.
 */
@Composable
private fun VoiceOrbDisplay(
    choice: VoiceOrbChoice,
    light: OrbLight = OrbLight.NEBULA,
    level: Float,
    phase: VoicePhase,
    listening: Boolean,
    agentName: String,
    /** L'orbe est un bouton , jamais une image sans action. */
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .onyxClickable(onClick = onClick)
            .semanticsLabel(OnyxLocalization.format("Talk to %1\$s", agentName)),
        contentAlignment = Alignment.Center,
    ) {
        if (choice.face == null) {
            VoiceOrb(light = light, level = level, listening = listening, phase = phase, agentName = agentName)
        } else {
            OrbPictureFace(
                choice = choice,
                level = level,
                phase = phase,
                listening = listening,
            )
        }
    }
}

/** Une face-image: l'orbe `…Blank`, l'anneau de la couleur de phase, et les barres vivantes. */
@Composable
private fun OrbPictureFace(
    choice: VoiceOrbChoice,
    level: Float,
    phase: VoicePhase,
    listening: Boolean,
) {
    val theme = LocalOnyxTheme.current
    val amplitude by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = OnyxMotion.Sites.VoiceLevel,
        label = "orbFaceAmplitude",
    )
    val ringColor = phaseColor(theme, phase)

    Box(modifier = Modifier.size(233.dp), contentAlignment = Alignment.Center) {
        // `OrbFace`: l'aura du modèle, la même pour **toutes** les faces — une image aussi.
        // Un seul halo diffus, dans la couleur de la phase, large et vif pendant l'écoute.
        val auraRadius = if (listening) 143f + amplitude * 44f else 110f
        Box(
            modifier = Modifier
                .size((if (listening) 297f + amplitude * 55f else 216f).dp)
                .background(
                    // `startRadius: 66`: la couleur tient jusqu'à 66 points, puis s'éteint
                    // jusqu'au bord du halo — c'est ce plateau qui fait la lueur large de la
                    // référence. Sur un dégradé qui part du centre, elle s'éteignait avant le bord
                    // du disque et ne laissait qu'un liseré.
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            VoiceAuraPlateau / auraRadius to ringColor.copy(alpha = if (listening) 0.45f else 0.16f),
                            1f to Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                        radius = with(LocalDensity.current) { auraRadius.dp.toPx() },
                    ),
                    CircleShape,
                ),
        )
        // `Halos`: les ondulations vivent **hors** du visage, pour toutes les faces —
        // y compris les images, qui ne les portaient pas. Au repos: une seule, discrète.
        // `Halos(diameter: 198)` pour une face-image.
        VoiceHalos(
            color = ringColor,
            subtle = phase == VoicePhase.IDLE,
            diameter = OrbPictureHaloDiameter.dp,
        )
        Image(
            painter = painterResource(choice.face!!),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(232.dp),
        )
        // L'anneau de phase: absent au repos (comme `phaseColor.opacity(phase == .idle ? 0 …)`),
        // puis vif à l'écoute ou pendant la lecture.
        Box(
            modifier = Modifier
                .size(236.dp)
                .border(
                    width = if (listening) 4.dp else 3.dp,
                    color = ringColor.copy(alpha = if (phase == VoicePhase.IDLE) 0f else 0.8f),
                    shape = CircleShape,
                ),
        )
        // Les barres par-dessus la face, dans la couleur de cette face (sombres sur l'or).
        WaveformPulse(
            color = choice.waveColor,
            height = 78f + 26f * amplitude,
            spacing = 6f,
            barWidth = 7f,
            shape = floatArrayOf(0.28f, 0.5f, 0.78f, 1f, 0.72f, 0.44f, 0.24f),
        )
    }
}

/**
 * La rangée de pastilles: chaque choix montre l'orbe qu'il sélectionne, entouré d'un anneau
 * quand il est retenu. Toucher une pastille change l'orbe affiché et prévient l'appelant.
 */
@Composable
private fun OrbChooserRow(
    selected: VoiceOrbChoice,
    onSelect: (VoiceOrbChoice) -> Unit,
    agentName: String = "Onyx",
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceOrbChoice.entries.forEach { choice ->
            val active = choice == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onyxClickable(onClick = { onSelect(choice) })
                    .semanticsLabel("${choice.label} orb"),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) theme.accent else theme.separator,
                            shape = CircleShape,
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (choice.face == null) {
                        // Onyx: le visage de l'agent, en miniature comme l'orbe — toujours un
                        // visage, jamais une initiale (c'est un aperçu d'orbe).
                        com.onyx.app.ui.design.BotLook(
                            name = "default",
                            faceCode = com.onyx.app.ui.design.BotFace.main.code(),
                            size = 34.dp,
                            showsInitial = false,
                        )
                    } else {
                        OrbAvatar(name = choice.label, size = 34.dp, override = choice.face)
                    }
                }
                Spacer(Modifier.size(3.dp))
                BasicText(
                    text = choice.label,
                    style = theme.caption2.copy(
                        color = if (active) theme.textColor else theme.secondaryText,
                    ),
                )
            }
        }
    }
}

// MARK: - Le panneau de réglages vocaux en ligne (item 1)

/** « Talk over replies »: les trois choix , dans l'ordre et avec les mêmes libellés. */
enum class TalkOverChoice(val label: String) {
    OFF("Off"),
    HEADPHONES("With headphones"),
    EVERYWHERE("Headphones and speaker"),
    ;

    /** Le réglage enregistré: c'est lui, avec la route du moment, qui décide du micro. */
    val setting: TalkOver
        get() = when (this) {
            OFF -> TalkOver.OFF
            HEADPHONES -> TalkOver.HEADPHONES
            EVERYWHERE -> TalkOver.EVERYWHERE
        }

    companion object {
        fun of(setting: TalkOver): TalkOverChoice = when (setting) {
            TalkOver.OFF -> OFF
            TalkOver.HEADPHONES -> HEADPHONES
            TalkOver.EVERYWHERE -> EVERYWHERE
        }
    }
}

/**
 * Le panneau de réglages vocaux, en ligne sous l'orbe: les mêmes rangées que Réglages → Voix du
 * . La vitesse a un `OnyxSlider` de 0,5 à 2,0 au pas de 0,05 (comme le `Slider` ), et
 * « Talk over replies » un petit menu déroulant maison — les deux se règlent en local quand
 * l'appelant ne fournit pas de rappel.
 */
@Composable
private fun VoiceSettingsPanel(
    listeningLanguage: String,
    replyLanguage: String,
    voiceSpeed: Float,
    handsFreeByDefault: Boolean,
    talkOverReplies: TalkOverChoice,
    onPickListeningLanguage: (() -> Unit)?,
    onPickReplyLanguage: (() -> Unit)?,
    onVoiceSpeedChange: ((Float) -> Unit)?,
    onHandsFreeByDefaultChange: ((Boolean) -> Unit)?,
    onTalkOverChange: ((TalkOverChoice) -> Unit)?,
    orbLight: OrbLight = OrbLight.NEBULA,
    onOrbLightChange: (OrbLight) -> Unit = {},
) {
    val theme = LocalOnyxTheme.current
    // Les replis locaux: le panneau reste utilisable seul (aperçus, appelant qui ne fournit que
    // la valeur). Chaque changement est aussi poussé au rappel quand il existe.
    var speed by remember { mutableStateOf(voiceSpeed) }
    var handsFreeDefault by remember { mutableStateOf(handsFreeByDefault) }
    var talkOver by remember { mutableStateOf(talkOverReplies) }
    var showTalkOverMenu by remember { mutableStateOf(false) }
    var showOrbLightMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(theme.surfaceColor, RoundedCornerShape(12.dp)),
    ) {
        // Langue d'écoute: vide ⇒ langue de l'appareil (adaptation Android du libellé ).
        OnyxValueRow(
            label = "Listening language",
            value = listeningLanguage.ifEmpty { "Device language" },
            symbol = Symbols.Globe,
            onClick = { onPickListeningLanguage?.invoke() },
        )
        OnyxRowDivider()
        // Langue de réponse: vide ⇒ « Automatic », comme `ReplyLanguageView`.
        OnyxValueRow(
            label = "Reply language",
            value = replyLanguage.ifEmpty { "Automatic" },
            symbol = Symbols.Globe,
            onClick = { onPickReplyLanguage?.invoke() },
        )
        OnyxRowDivider()
        // Vitesse de la voix: la valeur en chiffres monospacés, puis le curseur, comme le
        // `LabeledContent("Voice speed")` suivi du `Slider`.
        OnyxRow {
            BasicText(
                text = "Voice speed",
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = String.format(Locale.US, "%.2f×", speed),
                style = theme.monospacedFootnote.copy(color = theme.secondaryText),
            )
        }
        OnyxSlider(
            value = speed,
            range = 0.5f..2.0f,
            onValueChange = { next ->
                // Pas de 0,05, le pas du `Slider`.
                val snapped = (Math.round(next / 0.05f) * 0.05f).coerceIn(0.5f, 2.0f)
                speed = snapped
                onVoiceSpeedChange?.invoke(snapped)
            },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        OnyxRowDivider()
        // « Start voice mode in hands-free »: le même `Toggle` que.
        OnyxToggleRow(
            label = "Start voice mode in hands-free",
            checked = handsFreeDefault,
            onCheckedChange = { next ->
                handsFreeDefault = next
                onHandsFreeByDefaultChange?.invoke(next)
            },
        )
        OnyxRowDivider()
        // « Talk over replies »: un `Picker`-menu chez , ici une rangée qui ouvre un menu.
        OnyxRow(onClick = { showTalkOverMenu = true }) {
            BasicText(
                text = "Talk over replies",
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = talkOver.label,
                style = theme.body.copy(color = theme.secondaryText),
            )
            OnyxIcon(Symbols.ChevronUpDown, tint = theme.tertiaryText, size = 12.dp)
        }
        OnyxRowDivider()
        // `Picker("Orb light")` de: même libellé et mêmes trois choix.
        OnyxRow(onClick = { showOrbLightMenu = true }) {
            BasicText(
                text = "Orb light",
                style = theme.body.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = orbLight.label,
                style = theme.body.copy(color = theme.secondaryText),
            )
            OnyxIcon(Symbols.ChevronUpDown, tint = theme.tertiaryText, size = 12.dp)
        }
    }

    if (showOrbLightMenu) {
        OnyxMenu(
            items = OrbLight.entries.map { light ->
                MenuItem(
                    label = light.label,
                    checked = light == orbLight,
                    onSelect = {
                        onOrbLightChange(light)
                        showOrbLightMenu = false
                    },
                )
            },
            onDismiss = { showOrbLightMenu = false },
        )
    }

    if (showTalkOverMenu) {
        OnyxMenu(
            items = TalkOverChoice.entries.map { choice ->
                MenuItem(
                    label = choice.label,
                    checked = choice == talkOver,
                    onSelect = {
                        talkOver = choice
                        onTalkOverChange?.invoke(choice)
                        showTalkOverMenu = false
                    },
                )
            },
            onDismiss = { showTalkOverMenu = false },
        )
    }
}

// MARK: - Les derniers échanges, en « live captions » (item 3)

/**
 * Les derniers tours, façon sous-titres: « You » puis le nom de l'agent, chaque texte tronqué,
 * et un indicateur vivant (`LiveMark`) sur l'échange en cours — celui dont la réplique s'écrit
 * encore quand `streaming` est vrai. Les trois derniers tours seulement, comme la fenêtre.
 */
@Composable
private fun LiveCaptions(
    turns: List<VoiceTurn>,
    agentName: String,
    streaming: Boolean,
) {
    val theme = LocalOnyxTheme.current
    val shown = turns.takeLast(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEachIndexed { index, turn ->
            // Le dernier tour est « en cours » quand la réplique s'écrit encore.
            val live = streaming && index == shown.lastIndex
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    BasicText(text = "You", style = theme.caption2.copy(color = theme.tertiaryText))
                    if (live) LiveMark(color = theme.accent)
                }
                BasicText(
                    text = turn.question,
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    BasicText(text = agentName, localize = false, style = theme.caption2.copy(color = theme.tertiaryText))
                    if (live) LiveMark(color = theme.success)
                }
                BasicText(
                    text = turn.answer,
                    localize = false,
                    style = theme.caption.copy(color = theme.textColor),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - Les états d'échec du moteur (item 5)

/**
 * Ce que le moteur vocal ne peut pas faire, avec le texte que affiche pour chacune de ces
 * pannes. Repris de `SpeechRecognizer.Failure` (`assetsUnavailable`, `permissionDenied`); pour le
 * réseau, n'a pas de cas dédié — c'est le texte que ce moteur-ci pose déjà
 * (`SpeechEngine`, branche `ERROR_NETWORK`).
 */
enum class VoiceEngineFailure(val message: String, @DrawableRes val symbol: Int) {
    /** Pas de reconnaissance installée: `Failure.assetsUnavailable`. */
    NO_RECOGNISER(
        "The on-device speech model isn't installed yet.",
        Symbols.ExclamationTriangle,
    ),
    /** Permission refusée: `Failure.permissionDenied`. */
    PERMISSION_DENIED(
        "Microphone access was denied. Enable it for Onyx in Settings.",
        Symbols.Mic,
    ),
    /** Réseau requis: la langue a besoin du moteur en ligne. */
    NETWORK_REQUIRED(
        "The speech recogniser needs a network for this language.",
        Symbols.WifiExclamation,
    ),
}

/** Le bloc rouge d'un échec du moteur: l'icône, le texte, et « Try again » s'il y a un rappel. */
@Composable
private fun EngineFailureHelp(failure: VoiceEngineFailure?, onRetry: (() -> Unit)?) {
    if (failure == null) return
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            OnyxIcon(symbol = failure.symbol, tint = theme.error, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            BasicText(
                text = failure.message,
                style = theme.footnote.copy(color = theme.error, textAlign = TextAlign.Center),
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.size(6.dp))
            BasicText(
                text = "Try again",
                style = theme.footnote.copy(color = theme.accent, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .onyxTappable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * L'en-tête: le titre de la conversation, puis l'état mains-libres à droite, et l'accès aux
 * réglages vocaux quand l'appelant le fournit.
 */
@Composable
private fun VoiceHeader(
    conversationTitle: String,
    agentName: String = "Onyx",
    handsFree: Boolean,
    onToggleHandsFree: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `soloHeader`: Hands-free à gauche, titre de conversation à droite. Sans rappel, la
        // pastille n'est pas dessinée du tout: jamais un interrupteur sans effet.
        onToggleHandsFree?.let { toggle ->
            HandsFreePill(handsFree = handsFree, onToggle = toggle)
            Spacer(Modifier.width(12.dp))
        }
        BasicText(
            text = conversationTitle,
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText, textAlign = TextAlign.End),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // `frame(maxWidth: 170, alignment: .trailing)` de `soloHeader`: le début de la
            // transcription se tronque là où le tronque.
            modifier = Modifier.weight(1f).widthIn(max = 170.dp),
        )
        if (onOpenSettings != null) {
            Spacer(Modifier.width(8.dp))
            GlassCircleButton(
                symbol = Symbols.Gearshape,
                label = "Voice settings",
                size = 36.dp,
                iconSize = 16.dp,
                tint = theme.secondaryText,
                onClick = onOpenSettings,
            )
        }
    }
}

/**
 * La pastille « Hands-free »: elle montre « On » ou « Off », prend la teinte de l'accent quand
 * elle est active, et son libellé d'aide est lu par les lecteurs d'écran.
 */
@Composable
private fun HandsFreePill(handsFree: Boolean, onToggle: () -> Unit) {
    val theme = LocalOnyxTheme.current
    GlassPill(
        modifier = Modifier
            .onyxClickable(onClick = onToggle)
            .semanticsLabel(if (handsFree) "Hands-free, on" else "Hands-free, off"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnyxIcon(
                symbol = Symbols.Waveform,
                tint = if (handsFree) theme.accent else theme.secondaryText,
                size = 15.dp,
            )
            BasicText(
                text = "Hands-free",
                style = theme.footnote.copy(
                    color = if (handsFree) theme.textColor else theme.secondaryText,
                    fontWeight = if (handsFree) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

/**
 * Le niveau par barre: sept barres centrées, dont la hauteur suit `level`. Chaque barre a son
 * propre poids, la plus haute au centre et chaque barre vers l'extérieur un cran plus basse,
 * symétrique — la même allure que les barres du `LiveWaveform` , mais posée ici sous l'orbe.
 */
@Composable
private fun LevelBars(level: Float, phase: VoicePhase) {
    val theme = LocalOnyxTheme.current
    val live = phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING
    val drive by animateFloatAsState(
        targetValue = if (live) level.coerceIn(0f, 1f) else 0f,
        animationSpec = OnyxMotion.Sites.VoiceLevel,
        label = "levelBars",
    )
    val weights = floatArrayOf(0.13f, 0.27f, 0.51f, 1f, 0.51f, 0.27f, 0.13f)
    // Au repos chaque barre est un tiret; le centre peut monter jusqu'à 44 dp.
    val rest = 3f
    val tallest = 44f

    Row(
        modifier = Modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        weights.forEach { weight ->
            val barHeight = rest + (tallest - rest) * weight * drive
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = barHeight.dp)
                    .background(theme.accent, CircleShape),
            )
        }
    }
}

/**
 * L'aide du bas quand le micro est refusé ou que le moteur ne répond pas: le texte porté par
 * `problem`, en rouge, sous une icône d'avertissement. Rien n'est dessiné quand il est absent.
 */
@Composable
private fun ProblemHelp(problem: String?) {
    if (problem == null) return
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        OnyxIcon(
            symbol = Symbols.ExclamationTriangle,
            tint = theme.error,
            size = 13.dp,
        )
        Spacer(Modifier.width(6.dp))
        BasicText(
            text = problem,
            style = theme.footnote.copy(color = theme.error, textAlign = TextAlign.Center),
        )
    }
}

/**
 * L'orbe du mode vocal: le visage de l'agent dans sa sphère, sous deux anneaux.
 *
 * C'est ce que montre l'app: pas un cercle nu, mais le bot lui-même — ses yeux s'ouvrent
 * quand il travaille, se ferment quand il attend — et l'aura respire avec la voix.
 */
@Composable
private fun VoiceOrb(
    light: OrbLight = OrbLight.NEBULA,
    level: Float,
    listening: Boolean,
    phase: VoicePhase = VoicePhase.IDLE,
    agentName: String = "Onyx",
) {
    val theme = LocalOnyxTheme.current
    val amplitude by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = OnyxMotion.Sites.VoiceLevel,
        label = "amplitude",
    )
    val mood = when (phase) {
        VoicePhase.LISTENING -> com.onyx.app.ui.design.BotMood.IDLE
        VoicePhase.THINKING -> com.onyx.app.ui.design.BotMood.WAITING
        VoicePhase.SPEAKING -> com.onyx.app.ui.design.BotMood.WORKING
        VoicePhase.IDLE -> com.onyx.app.ui.design.BotMood.PLEASED
    }
    // `phaseColor`: accent à l'écoute, orange système qui réfléchit, vert système qui parle.
    val phaseColor = when (phase) {
        VoicePhase.THINKING -> Color(0xFFFF9F0A)
        VoicePhase.SPEAKING -> Color(0xFF30D158)
        else -> theme.accent
    }
    // Formule exacte de — `live` = micro actif ou réponse en lecture.
    val live = listening || phase == VoicePhase.SPEAKING
    val strength = if (live) 0.8f + level * 0.35f else if (phase == VoicePhase.IDLE) 0.42f else 0.62f

    Box(modifier = Modifier.size(233.dp), contentAlignment = Alignment.Center) {
        // `OrbFace`: l'**aura** — un seul halo diffus dans la couleur de la phase, large et vif
        // pendant l'écoute (297 pt + la voix), ténu au repos (216 pt). Elle manquait à la voix
        // solo, qui ne portait que ses trois cercles nets sur le noir; mêmes nombres et même forme
        // que la scène d'une salle, et découpée au cercle: jamais de bord carré.
        val auraRadius = if (listening) 143f + amplitude * 44f else 110f
        Box(
            modifier = Modifier
                .size((if (listening) 297f + amplitude * 55f else 216f).dp)
                .background(
                    // `startRadius: 66`: la couleur tient jusqu'à 66 points, puis s'éteint
                    // jusqu'au bord du halo — c'est ce plateau qui fait la lueur large de la
                    // référence. Sur un dégradé qui part du centre, elle s'éteignait avant le bord
                    // du disque et ne laissait qu'un liseré.
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            VoiceAuraPlateau / auraRadius to phaseColor.copy(alpha = if (listening) 0.45f else 0.16f),
                            1f to Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                        radius = with(LocalDensity.current) { auraRadius.dp.toPx() },
                    ),
                    CircleShape,
                ),
        )
        // Les trois anneaux du modèle. Le premier est l'anneau de phase, **collé au bord du
        // disque** — `GlassOrb.overlay { Circle.strokeBorder(phaseColor.opacity(phase == .idle
        // ? 0: 0.8), lineWidth: 3).padding(GlassOrb.ringOutset) }` — éteint au repos.
        Box(
            modifier = Modifier
                .size(OrbRingDiameter.dp)
                .border(
                    width = 3.dp,
                    color = phaseColor.copy(alpha = if (phase == VoicePhase.IDLE) 0f else 0.8f),
                    shape = CircleShape,
                ),
        )
        // Les deux autres sont les `Halos(color: phaseColor, diameter: GlassOrb.haloDiameter)`:
        // deux cercles qui partent du bord du disque et s'ouvrent bien plus loin que lui, puis
        // s'éteignent. Au repos, le seul anneau discret du `Halos(subtle: true)`.
        if (live || phase == VoicePhase.IDLE) {
            VoiceHalos(
                color = phaseColor,
                subtle = phase == VoicePhase.IDLE,
                diameter = OrbHaloDiameter.dp,
            )
        }
        // La lumière « nebula »: géométrie exacte de DriftingBlobs.
        when (light) {
            OrbLight.NEBULA -> DriftingBlobs(
                color = phaseColor,
                strength = strength,
                swirling = phase == VoicePhase.THINKING,
                modifier = Modifier.size(172.dp),
            )
            OrbLight.AURORA -> AuroraLight(
                color = phaseColor,
                strength = strength,
                swirling = phase == VoicePhase.THINKING,
                modifier = Modifier.size(172.dp),
            )
            OrbLight.CORE -> CoreLight(
                color = phaseColor,
                strength = strength,
                swirling = phase == VoicePhase.THINKING,
                modifier = Modifier.size(172.dp),
            )
        }
        // `darkGlass` garde ses pixels d'origine; seule sa waveform figée devient `LiveWaveform`.
        DarkGlassWaveOrb(
            phase = phase,
            level = level,
            modifier = Modifier.size((190f + amplitude * 12f).dp),
        )
        if (phase == VoicePhase.THINKING) {
            VoiceThinkingArc(
                color = phaseColor,
                modifier = Modifier.size(205.dp),
            )
        }
    }
}


/** La couleur de l'état, comme `phaseColor`: accent à l'écoute, orange qui réfléchit, vert qui parle. */
// MARK: - Le carré du bouton rouge et la géométrie des anneaux de l'orbe (`GlassOrb` )

/**
 * La boîte du carré d'arrêt: `stop.fill` du modèle dessine son carré à **0,27 du bouton**
 * de 60 points (mesuré: 21 à 22 px de carré dans un disque de 80). Le glyphe embarqué ne remplit
 * que 10/24 de sa boîte (7 → 17 sur 24): la boîte doit donc mesurer 39 points pour que le carré
 * en fasse 16 — deux fois ce qu'il faisait à 20 points (8 points, 0,13 du bouton).
 */
private const val VoiceStopGlyph = 39f

/**
 * `OrbFace`: le rayon où l'aura tient son plein avant de s'éteindre (`startRadius: 66` du
 * dégradé radial du modèle). Sans ce plateau, la lueur s'éteignait avant le bord du disque.
 */
private const val VoiceAuraPlateau = 66f

/** `GlassOrb.diameter`: le disque de verre dessiné. */
private const val OrbFaceDiameter = 190f

/**
 * `GlassOrb.pictureDisc`: le disque d'une face-image dans son carré de 172 points. Les mesures de
 * l'orbe dessiné s'y rapportent, pour que ses anneaux soient aux mêmes distances proportionnelles
 * que ceux des orbes-image.
 */
private const val OrbPictureDisc = 172f * 46f / 48f

/** `GlassOrb.ringOutset`: ce que l'anneau de phase dépasse du disque. */
private const val OrbRingOutset = (172f + 8f - OrbPictureDisc) / 2f

/** L'anneau de phase: le diamètre du disque plus deux fois ce débord (≈ 205 points). */
private const val OrbRingDiameter = OrbFaceDiameter + 2f * OrbRingOutset

/** `GlassOrb.haloDiameter`: la base des anneaux qui s'ouvrent autour du disque (≈ 228 points). */
private const val OrbHaloDiameter = OrbFaceDiameter * 198f / OrbPictureDisc

/** `Halos(diameter: 198)`: la base des anneaux d'une face-image. */
private const val OrbPictureHaloDiameter = 198f

/** Le mot d'état affiché, avec le nom de l'agent quand il réfléchit. */
private fun phaseTitle(phase: VoicePhase, agentName: String): String = when (phase) {
    VoicePhase.IDLE -> "Ready"
    VoicePhase.LISTENING -> "Listening"
    VoicePhase.THINKING -> "$agentName is thinking"
    VoicePhase.SPEAKING -> "Speaking"
}

private fun phaseColor(theme: ResolvedTheme, phase: VoicePhase): Color = when (phase) {
    VoicePhase.LISTENING -> theme.accent
    VoicePhase.THINKING -> theme.warning
    VoicePhase.SPEAKING -> theme.success
    VoicePhase.IDLE -> theme.secondaryText
}

/** Ce qu'on affiche sous l'orbe avant que la première parole ne soit captée. */
private fun placeholder(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Go ahead."
    VoicePhase.THINKING -> "Tap the orb and talk."
    VoicePhase.SPEAKING -> "Tap the orb and talk."
    VoicePhase.IDLE -> "Tap the orb and talk."
}

/** Un échange passé, gardé sous l'orbe pour se souvenir de ce qui a été dit. */
data class VoiceTurn(val question: String, val answer: String)




