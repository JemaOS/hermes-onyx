package com.onyx.app.ui.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.GlassCircleButton
import com.onyx.app.ui.design.GlassPill
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.barBackground
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.localization.OnyxLocalization
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal val TopBarCapsuleWidth = 86.dp
internal val TopBarActionSlotWidth = 38.dp
internal val TopBarActionSlotHeight = 30.dp
internal val TopBarPrincipalMaxWidth = 240.dp

/** Le visage du bot dans la barre: 30 dp de face, dans une boîte de 38 dp pour que l'aura respire. */
internal val TopBarAvatarSize = 30.dp

/** La boîte qui porte l'avatar et son aura: quelques points de marge pour que le halo tienne. */
private val TopBarAvatarBoxSize = 38.dp

/** `Conversation.refreshHeader` garde 60 caractères; `Text.lineLimit(1)` fait le reste. */
internal fun chatHeaderTitle(text: String): String = text.take(60).lineSequence().firstOrNull().orEmpty()

/**
 * La barre de titre, refaite à la main comme celle.
 *
 * Rien de Material: le titre est centré sur deux lignes (la conversation, puis l'agent et le
 * modèle avec un chevron), un bouton rond en verre ouvre la liste des conversations à gauche, et
 * une pastille en verre porte « … » et le crayon à droite — exactement le `toolbar` de ContentView.
 */
@Composable
fun OnyxTopBar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onOpenConversations: (() -> Unit)? = null,
    /**
     * Le contrôle du volet des deux panneaux, replié ou déplié (`sidebar.left`): quand il est
     * fourni, il remplace le bouton « Conversations » à gauche — le volet est là, c'est lui qu'on
     * ouvre, et la barre ne propose pas deux fois la même destination.
     */
    sidebarToggle: (@Composable () -> Unit)? = null,
    onPickModel: (() -> Unit)? = null,
    onNewConversation: () -> Unit,
    onMenu: () -> Unit,
    menuItems: List<com.onyx.app.ui.design.MenuItem> = emptyList(),
    titleIsPlaceholder: Boolean = false,
    conversationKey: String = "",
    contextUsed: Int? = null,
    contextWindow: Int? = null,
    /**
     * Le visage du bot de la conversation ([ThreadSpeaker.faceName] / [ThreadSpeaker.faceCode]):
     * posé à gauche du titre, il dit qui parle **sans jamais défiler** — c'est ce qui remplace
     * l'ancien en-tête du fil. `null`: pas d'avatar (écrans sans conversation).
     */
    avatarName: String? = null,
    avatarFaceCode: String? = null,
    /**
     * Combien de sous-agents travaillent en ce moment. Zéro (ou `null`): pas de capsule — une
     * conversation qui ne délègue rien garde sa barre telle quelle.
     */
    subagentRuns: Int? = null,
    /** Ouvre l'écran des tâches lancées. Fourni avec [subagentRuns] quand il y a des tâches. */
    onOpenSubagents: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .barBackground()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            sidebarToggle != null -> sidebarToggle()

            onOpenConversations != null -> GlassCircleButton(
                symbol = Symbols.ListBullet,
                label = "Conversations",
                size = 38.dp,
                onClick = onOpenConversations,
            )

            else -> Spacer(Modifier.size(38.dp))
        }

        // Le visage du bot de la conversation, à gauche du titre: fixe, il ne défile pas avec le
        // fil — le nom sous l'avatar ne vit plus dans le fil du tout.
        if (avatarName != null) {
            TopBarAvatar(name = avatarName, faceCode = avatarFaceCode)
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            com.onyx.app.ui.common.TypedTitle(
                title = title,
                conversationKey = conversationKey,
                isPlaceholder = titleIsPlaceholder,
                style = theme.headline.copy(color = theme.textColor),
                modifier = Modifier.widthIn(max = TopBarPrincipalMaxWidth),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .widthIn(max = TopBarPrincipalMaxWidth)
                    .then(if (onPickModel != null) Modifier.onyxClickable(onClick = onPickModel) else Modifier),
            ) {
                BasicText(
                    text = subtitle,
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OnyxIcon(Symbols.ChevronDown, tint = theme.secondaryText, size = 8.dp)
                if (contextUsed != null && contextWindow != null && contextWindow > 0) {
                    ContextRing(used = contextUsed, window = contextWindow)
                }
            }
        }

        // La capsule des tâches: elle n'apparaît que si la conversation a délégué quelque chose
        // (sinon la barre ne bouge pas). Un point vivant et un liseré d'accent quand l'un
        // travaille encore; sinon le même compte, en gris — la liste reste à un tap.
        if (onOpenSubagents != null) {
            val live = (subagentRuns ?: 0) > 0
            Row(
                modifier = Modifier
                    .background(
                        color = if (live) theme.accent.copy(alpha = 0.14f) else theme.fill,
                        shape = RoundedCornerShape(50),
                    )
                    .onyxClickable(onClick = onOpenSubagents)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (live) {
                    LiveMark(color = theme.accent)
                } else {
                    OnyxIcon(symbol = Symbols.Person2, tint = theme.secondaryText, size = 13.dp)
                }
                BasicText(
                    text = (subagentRuns ?: 0).toString(),
                    localize = false,
                    style = theme.caption.copy(
                        color = if (live) theme.accent else theme.secondaryText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        GlassPill(modifier = Modifier.width(TopBarCapsuleWidth)) {
            Box(
                modifier = Modifier
                    .width(TopBarActionSlotWidth)
                    .height(TopBarActionSlotHeight)
                    .onyxClickable(onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(Symbols.Ellipsis, tint = theme.secondaryText, size = 16.dp, contentDescription = "More")
            }
            Box(
                modifier = Modifier
                    .width(TopBarActionSlotWidth)
                    .height(TopBarActionSlotHeight)
                    .onyxClickable(onClick = onNewConversation),
                contentAlignment = Alignment.Center,
            ) {
                OnyxIcon(
                    Symbols.SquareAndPencil,
                    tint = theme.accent,
                    size = 16.dp,
                    contentDescription = "New conversation",
                )
            }
        }
    }
}

/**
 * Le visage du bot dans la barre du haut: **à plat, immobile, sans halo**.
 *
 * Le même dessin que partout ailleurs ([BotLook]) — la barre ne réinvente pas un avatar — mais
 * montré nu: aucune aura, aucun cercle de couleur derrière la face, aucune pulsation, et pas de
 * clignement. non plus n'entoure d'aucun cercle le visage de son en-tête; un visage qui
 * respire sous le pouce détourne l'œil du titre.
 */
@Composable
private fun TopBarAvatar(
    name: String,
    faceCode: String?,
) {
    Box(
        modifier = Modifier.size(TopBarAvatarBoxSize),
        contentAlignment = Alignment.Center,
    ) {
        BotLook(
            name = name,
            faceCode = faceCode,
            size = TopBarAvatarSize,
            showsInitial = false,
            blinks = false,
        )
    }
}

internal enum class ContextTint { ACCENT, WARNING, ERROR }

internal fun contextShare(used: Int, window: Int): Float =
    if (window > 0) (used.toFloat() / window.toFloat()).coerceIn(0f, 1f) else 0f

internal fun contextTint(used: Int, window: Int): ContextTint {
    val share = contextShare(used, window)
    return when {
        share >= 0.9f -> ContextTint.ERROR
        share >= 0.75f -> ContextTint.WARNING
        else -> ContextTint.ACCENT
    }
}

/** « 54,000 of 128,000 tokens (42%) », avec les séparateurs de la locale. */
internal fun contextSentence(used: Int, window: Int, locale: Locale = Locale.getDefault()): String {
    val numbers = NumberFormat.getIntegerInstance(locale)
    val percent = (contextShare(used, window) * 100).roundToInt()
    val key = "%1\$s of %2\$s tokens (%3\$d%%)"
    return String.format(
        locale,
        OnyxLocalization.translate(key, locale),
        numbers.format(used),
        numbers.format(window),
        percent,
    )
}

/** L'anneau de remplissage du contexte et son popover d'explication (`ContextRing`). */
@Composable
private fun ContextRing(used: Int, window: Int) {
    val theme = LocalOnyxTheme.current
    var showDetail by remember { mutableStateOf(false) }
    val fraction = contextShare(used, window)
    val tint = when (contextTint(used, window)) {
        ContextTint.ERROR -> theme.error
        ContextTint.WARNING -> theme.warning
        ContextTint.ACCENT -> theme.accent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .onyxClickable { showDetail = true }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            val stroke = 2.dp.toPx()
            drawArc(
                color = theme.secondaryText.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        BasicText(
            text = "${(fraction * 100).roundToInt()}%",
            style = theme.caption.copy(color = theme.secondaryText),
        )
    }

    if (showDetail) {
        Dialog(onDismissRequest = { showDetail = false }) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(text = "Context", style = theme.headline.copy(color = theme.textColor))
                BasicText(
                    text = contextSentence(used, window),
                    localize = false,
                    style = theme.subheadline.copy(color = theme.textColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(theme.secondaryText.copy(alpha = 0.20f), RoundedCornerShape(50)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(tint, RoundedCornerShape(50)),
                    )
                }
                BasicText(
                    text = "Everything said in this conversation is read again with each message. When it fills up the oldest part is summarised or dropped; a new conversation starts empty.",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        }
    }
}

/** La barre réduite d'un écran qui défile: la grande version en haut de page. */

@Composable
fun LargeTitleRow(title: String, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        BasicText(
            text = title,
            style = theme.largeTitle.copy(color = theme.textColor, fontWeight = FontWeight.Bold),
        )
    }
}
