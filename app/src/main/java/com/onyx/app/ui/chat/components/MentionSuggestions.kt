package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.serve.BotMentions
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * La hauteur au plus de la liste. Six lignes tiennent dans une fenêtre dégagée; au-delà (une
 * police à 1,3×, un écran court, le clavier ouvert) la liste défile, plutôt que de pousser le
 * champ hors de l'écran.
 */
private val MentionMenuMaxHeight = 232.dp

/**
 * Les bots qu'un mot `@` en cours de frappe peut être, juste au-dessus du champ
 * (`ComposerView.mentionMenu`): le visage du bot, sa poignée « @handle » et son nom. Toucher une
 * ligne finit le mot dans le brouillon.
 *
 * Rien ici ne dépend du thème clair ou sombre: les couleurs viennent du thème courant, et les
 * deux panneaux (pliable déplié, grand écran) se partagent la même liste, posée sur le composeur.
 */
@Composable
fun MentionSuggestions(
    bots: List<BotProfile>,
    onPick: (BotProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = MentionMenuMaxHeight)
            .background(theme.surfaceColor, RoundedCornerShape(14.dp))
            .border(1.dp, theme.separator, RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState())
            .semanticsLabel("Bots to mention"),
    ) {
        bots.forEach { bot ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(pressedScale = 0.98f) { onPick(bot) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BotLook(name = bot.name, faceCode = bot.faceCode, size = 22.dp)
                BasicText(
                    text = "@" + BotMentions.tag(bot),
                    // Le libellé d'une ligne est la poignée du bot: un identifiant, jamais un
                    // libellé du catalogue.
                    localize = false,
                    style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                BasicText(
                    text = bot.label(),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            if (bot != bots.last()) {
                // Le filet de `mentionMenu`, qui s'arrête après le visage.
                OnyxRowDivider(inset = 44.dp)
            }
        }
    }
}
