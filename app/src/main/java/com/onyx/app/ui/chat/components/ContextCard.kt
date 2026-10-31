package com.onyx.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.Feedback
import com.onyx.app.ui.design.FlowState
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.SelectableText
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.rememberFlowPose
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **La carte de contexte — un composant, plusieurs contenus.**
 *
 * Le défaut que ce fichier ferme : un appel d'outil, un sous-agent, une approbation, une page et un
 * diff étaient **cinq objets différents** — quatre rayons, trois filets, deux façons de replier, et
 * aucune règle commune sur ce qui se met à gauche ou à droite. Le propriétaire ne lisait donc pas
 * la même grammaire d'une carte à l'autre.
 *
 * **Les règles, une seule fois :**
 *
 *  1. **le rayon et le filet** — [OnyxSpace.CardCorner] et [OnyxSpace.Stroke] d'un filet qui vire à
 *     l'accent (`accent.copy(alpha = 0.35f)`) dès que la carte **vit** (en cours, en attente) ;
 *  2. **l'en-tête** — l'état à **gauche** ([ContextStateDot]), le titre et l'identifiant technique,
 *     l'action à **droite** ;
 *  3. **la même sémantique de repli** — le chevron de la maison (`FoldHeader`/`FoldBody` du fil) et
 *     l'ouverture `MessageFold`, figée sous « Réduire les animations » ;
 *  4. **une puce d'état qui dit le vrai état** — `En cours`, `Terminé`, `Échoué`, `En attente`,
 *     chacune dérivée de l'état réel de la donnée (voir [toolCardState], [subagentCardState],
 *     [approvalCardState]), jamais d'un ornement ;
 *  5. **aucun bouton mort** — une carte n'a d'action que là où il y a quelque chose à faire
 *     (`action == null` ne dessine rien).
 *
 * **Ce que la puce porte comme matière.** Les deux états **vivants** d'une carte (en cours, en
 * attente) prennent le mouvement de l'état correspondant de la grammaire du fil
 * ([FlowState.WORKING] pulse, [FlowState.DECIDING] s'immobilise avec une lueur) : la puce d'une
 * carte d'outil et la pastille de l'en-tête battent donc **ensemble**. Les deux états **archivés**
 * (terminé, échoué) ne bougent pas — un travail fini ne pulse plus.
 */
enum class ContextCardState(val labelKey: String) {
    /** L'objet travaille : un outil tourne, un enfant tourne, une page se lit. */
    RUNNING("Running"),

    /** L'objet a fini son travail. */
    DONE("Done"),

    /** L'objet a échoué : le rouge de la maison, jamais un état du fil. */
    FAILED("Failed"),
    /** Une décision est attendue : le travail est **arrêté** tant qu'on n'a pas répondu. */
    WAITING("Waiting"),
    ;

    /**
     * Le mouvement de la puce, quand il y en a un — l'état **réel** du fil, ou `null` pour une
     * carte archivée. Une carte terminée ou échouée ne bouge pas : c'est ce qui distingue « ça
     * tourne » de « c'est fini » sans changer de couleur.
     */
    val motion: FlowState?
        get() = when (this) {
            RUNNING -> FlowState.WORKING
            WAITING -> FlowState.DECIDING
            DONE, FAILED -> null
        }

    /** La teinte de la puce, en **rôle** (testable sans thème) — l'accent, ou un signal de la maison. */
    val ink: ContextInk
        get() = when (this) {
            RUNNING, WAITING -> ContextInk.ACCENT
            DONE -> ContextInk.SUCCESS
            FAILED -> ContextInk.ERROR
        }

    /** Le libellé lu par un lecteur d'écran. */
    val label: String get() = OnyxLocalization[labelKey]
}

/** Le rôle d'encre d'une puce d'état. Un seul accent dans l'app ; le vert et le rouge sont des signaux. */
enum class ContextInk { ACCENT, SUCCESS, ERROR }

/** La couleur du rôle, résolue dans le thème courant. */
@Composable
fun ContextInk.color(): Color {
    val theme = LocalOnyxTheme.current
    return colorIn(theme)
}

/** La couleur du rôle, dans un thème donné — la règle, séparée du dessin. */
fun ContextInk.colorIn(theme: ResolvedTheme): Color = when (this) {
    ContextInk.ACCENT -> theme.accent
    ContextInk.SUCCESS -> theme.success
    ContextInk.ERROR -> theme.error
}

// --------------------------------------------------------------------------------------------
// Le déclencheur de chaque contenu — ce qui rend la puce **vraie**
// --------------------------------------------------------------------------------------------

/** L'état de la carte d'un **appel d'outil**, lu dans le statut que le transport a donné. */
fun toolCardState(status: ToolStatus): ContextCardState = when (status) {
    ToolStatus.RUNNING -> ContextCardState.RUNNING
    ToolStatus.COMPLETED -> ContextCardState.DONE
    ToolStatus.FAILED -> ContextCardState.FAILED
}

/** L'état de la carte d'un **sous-agent** : un enfant qui n'est plus vivant a fini ou échoué. */
fun subagentCardState(status: SubagentStatus): ContextCardState = when (status) {
    SubagentStatus.RUNNING, SubagentStatus.DISPATCHED -> ContextCardState.RUNNING
    SubagentStatus.COMPLETED -> ContextCardState.DONE
    SubagentStatus.FAILED -> ContextCardState.FAILED
}

/**
 * L'état de la carte d'une **approbation** : `En attente` tant que la décision manque, `Terminé`
 * une fois répondue. C'est la seule carte qui puisse porter l'attente — et c'est cette attente qui
 * gèle la pastille de l'en-tête ([com.onyx.app.ui.chat.turnFlowState]).
 */
fun approvalCardState(request: ApprovalRequest): ContextCardState =
    if (request.isPending) ContextCardState.WAITING else ContextCardState.DONE

/** L'état de la fiche d'une **page** : en cours tant qu'elle n'a pas été lue, terminée après. */
fun linkCardState(loaded: Boolean): ContextCardState =
    if (loaded) ContextCardState.DONE else ContextCardState.RUNNING

/** L'état d'un **diff** : il est toujours là, complet, dès qu'il s'affiche. */
fun patchCardState(files: Int): ContextCardState =
    if (files > 0) ContextCardState.DONE else ContextCardState.FAILED

// --------------------------------------------------------------------------------------------
// La surface commune
// --------------------------------------------------------------------------------------------

/** Le rayon de la maison pour une carte de contexte. */
val ContextCardCorner: Dp = OnyxSpace.CardCorner

/** La surface commune : le rayon, le fond et le filet — la même règle pour les cinq contenus. */
@Composable
fun Modifier.contextCardSurface(state: ContextCardState): Modifier {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(ContextCardCorner)
    return this
        .background(theme.surfaceColor, shape)
        .border(
            width = OnyxSpace.Stroke,
            color = if (state.motion != null) theme.accent.copy(alpha = 0.35f) else theme.separator,
            shape = shape,
        )
}

// --------------------------------------------------------------------------------------------
// La puce d'état
// --------------------------------------------------------------------------------------------

/**
 * **La puce d'état d'une carte** : un point et son halo, dont le mouvement est celui de l'état.
 *
 * L'échelle et l'opacité viennent de [rememberFlowPose] — la **même** fonction que la pastille de
 * l'en-tête, donc les deux battent au même rythme. Sous « Réduire les animations », la pose est
 * figée : le point reste là, plein, sans une image intermédiaire.
 */
@Composable
fun ContextStateDot(state: ContextCardState, modifier: Modifier = Modifier, readingUp: Boolean = false) {
    StateSignatureDot(
        flow = state.motion ?: FlowState.REST,
        tint = state.ink.color(),
        description = state.label,
        readingUp = readingUp,
        modifier = modifier,
    )
}

/**
 * **La signature d'un état, dessinée** — la seule façon de poser une puce d'état dans l'app.
 *
 * L'en-tête du fil ([com.onyx.app.ui.chat.components.ThreadStatusPill]) et chaque carte de contexte
 * passent par ici : le point, son anneau et son halo battent donc **au même rythme** partout. C'est
 * ce qui fait de l'état une matière continue plutôt qu'un ornement par écran.
 *
 * @param flow l'état réel — il donne le mouvement (respiration, pulsation, lueur figée ou rien) ;
 * @param tint l'encre : un seul accent pour les états du fil, ou le signal de la maison pour un
 *        travail terminé ou échoué ;
 * @param readingUp vrai quand la personne remonte lire : la période est **multipliée**, jamais
 *        divisée ;
 * @param description ce qu'un lecteur d'écran entend ; `null` pour une puce purement décorative.
 */
@Composable
fun StateSignatureDot(
    flow: FlowState,
    tint: Color,
    modifier: Modifier = Modifier,
    readingUp: Boolean = false,
    size: Dp = 14.dp,
    description: String? = null,
) {
    val pose = rememberFlowPose(flow, readingUp)
    // Une pose de repos est une pose : on ne la corrige pas au doigt mouillé. Le plancher existe
    // pour l'état `REST` (dont la signature éteint la puce), quand la puce dit malgré tout quelque
    // chose — un avertissement du fil, par exemple.
    val moving = flow.signature.motion != com.onyx.app.ui.design.FlowMotion.STILL
    val alpha = if (moving) pose.alpha.coerceIn(0.45f, 1f) else 1f
    val glow = if (moving) pose.glow.coerceAtMost(Feedback.FlowGlowMax) else 0f
    val scale = if (moving) pose.scale else 1f
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier.clearAndSetSemantics {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (glow > 0f) {
            Box(
                modifier = Modifier
                    .size(size * scale)
                    .background(tint.copy(alpha = glow), CircleShape)
                    .clearAndSetSemantics {},
            )
        }
        Box(
            modifier = Modifier
                .size(size - 2.dp)
                .background(tint.copy(alpha = 0.22f * alpha), CircleShape)
                .padding(3.dp)
                .background(tint.copy(alpha = alpha), CircleShape)
                .clearAndSetSemantics {},
        )
    }
}

// --------------------------------------------------------------------------------------------
// Le composant
// --------------------------------------------------------------------------------------------

/**
 * **La carte de contexte** — le seul objet du fil qui présente un travail de l'agent.
 *
 * Elle s'emploie partout de la même façon : `state` dit le **vrai** état, [title] dit ce qui se
 * passe, [identifier] porte le nom technique en **monospace** (jamais en prose : un identifiant
 * n'est pas un mot), [action] met à droite ce qu'on peut faire — et rien quand il n'y a rien à
 * faire.
 *
 * @param leading un contenu à gauche de la puce (une vignette, une icône de genre d'outil) ;
 * @param collapsible vrai quand [body] existe : la carte prend alors le chevron et le repli de la
 *        maison, et le repli est **ouvert** au premier rendu si [initiallyOpen] ;
 * @param minHeight une hauteur plancher, pour les cartes qui réservent leur place (une fiche qui se
 *        remplit ne doit pas déplacer la conversation).
 */
@Composable
fun ContextCard(
    state: ContextCardState,
    title: String,
    modifier: Modifier = Modifier,
    identifier: String? = null,
    titleMonospace: Boolean = false,
    detail: String? = null,
    trailing: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    initiallyOpen: Boolean = false,
    minHeight: Dp = 0.dp,
    /** Vrai quand la personne remonte lire le fil : la puce **ralentit**. */
    readingUp: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /**
     * Le repli **piloté de l'extérieur** : une carte qui n'ouvre pas son propre contenu mais
     * quelque chose chez son parent (le groupe de sous-agents ouvre les cartes de ses enfants).
     * `null` laisse la carte maîtresse de son repli.
     */
    expandState: Boolean? = null,
    onExpandChange: ((Boolean) -> Unit)? = null,
    /** Le corps **replié** : sa présence pose le chevron, et lui seul. */
    foldBody: (@Composable () -> Unit)? = null,
    /** Le corps **toujours** visible de la carte (une commande, une ligne de pied, une barre). */
    body: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val reduceMotion = LocalReduceMotion.current
    val openable = foldBody != null
    var chosen by remember { mutableStateOf<Boolean?>(null) }
    val localOpen = chosen ?: initiallyOpen
    val open = expandState ?: localOpen
    fun setOpen(value: Boolean) {
        if (onExpandChange != null) onExpandChange(value) else chosen = value
    }
    val foldSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
        if (reduceMotion) androidx.compose.animation.core.snap() else OnyxMotion.Sites.MessageFold
    val chevronAngle by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = foldSpec,
        label = "contextChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .contextCardSurface(state)
            .defaultMinSize(minHeight = minHeight)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.onyxTappable(onLongPress = onLongClick, onClick = onClick ?: {})
                } else {
                    Modifier
                },
            )
            .padding(OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ContextCardRowHeight)
                .then(
                    // Le geste de l'en-tête : ouvrir ce qui est replié. **Sauf** quand la carte a
                    // elle-même une action (`onClick`) — alors c'est elle, et le chevron garde le
                    // repli pour lui seul.
                    if (openable && onClick == null) {
                        Modifier.onyxClickable(pressedScale = 0.98f) { setOpen(!open) }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            // 1. L'ÉTAT, À GAUCHE. Une carte dessine **un seul** état : par défaut la puce de la
            // maison ([ContextStateDot]), ou la marque que le contenu apporte quand elle dit mieux
            // le même état (la coche tracée d'un appel d'outil, la vignette d'une page). Jamais les
            // deux — deux marques d'état, c'est deux vérités à l'écran.
            if (leading != null) leading() else ContextStateDot(state = state, readingUp = readingUp)
            // 2. Le titre, et l'identifiant technique en monospace — jamais la même chose deux fois.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                BasicText(
                    text = title,
                    localize = false,
                    style = theme.footnote.copy(
                        color = theme.textColor,
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (titleMonospace) FontFamily.Monospace else null,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.takeIf { it.isNotBlank() }?.let { line ->
                    SelectableText(
                        text = line,
                        localize = false,
                        style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 3. La durée, le compte — un chiffre qui se lit à droite.
            identifier?.takeIf { it.isNotBlank() }?.let { id ->
                BasicText(
                    text = id,
                    localize = false,
                    style = theme.caption2.copy(color = theme.tertiaryText, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
            trailing?.takeIf { it.isNotBlank() }?.let { tail ->
                BasicText(
                    text = tail,
                    localize = false,
                    style = theme.caption2.copy(color = theme.tertiaryText, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
            // 4. L'ACTION, À DROITE — rien à faire, rien de dessiné.
            action?.invoke()
            if (openable) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = ContextCardRowHeight)
                        .semantics {
                            contentDescription = resolveOnyxText(if (open) "Hide details" else "Show details")
                        }
                        .onyxClickable(onClick = { setOpen(!open) }),
                    contentAlignment = Alignment.Center,
                ) {
                    OnyxIcon(
                        symbol = Symbols.ChevronRight,
                        tint = theme.tertiaryText,
                        size = 11.dp,
                        modifier = Modifier.rotate(chevronAngle),
                    )
                }
            }
        }
        if (openable) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(
                    animationSpec = OnyxMotion.Sites.MessageFoldSize,
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = OnyxMotion.Sites.MessageFold),
                exit = shrinkVertically(
                    animationSpec = OnyxMotion.Sites.MessageFoldSize,
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = OnyxMotion.Sites.MessageFold),
            ) {
                Column(modifier = Modifier.padding(start = OnyxSpace.Half, bottom = OnyxSpace.Hair)) {
                    foldBody?.invoke()
                }
            }
        }
        if (body != null) {
            Column(modifier = Modifier.padding(start = OnyxSpace.Half, bottom = OnyxSpace.Hair)) {
                body()
            }
        }
    }
}

/**
 * La hauteur d'en-tête d'une carte de contexte : **48 dp**, la cible tactile de la maison. L'état
 * et l'action s'y posent sans jamais se chevaucher (deux contrôles sous 48 dp se recouvrent de
 * 6 dp, l'arrondi du système aidant).
 */
val ContextCardRowHeight: Dp = OnyxSpace.TouchTarget
