package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.chat.SlashItem
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **Le menu des commandes « / »**, sur deux lignes par commande.
 *
 * Défaut relevé sur l'appareil (« pas bien aligné ») : le nom, puis le détail, vivaient dans une
 * seule rangée. Le détail d'une commande du serveur fait deux lignes — « Start a new session
 * (fresh session ID + history) (usage: /new [name]) » — donc la rangée grandissait de travers,
 * la suite du texte repassait sous l'icône, et les lignes n'avaient plus la même hauteur.
 *
 * Le dessin est donc **une colonne** : le nom sur sa ligne, le détail dessous, tous deux coupés à
 * une ligne. Deux informations, deux lignes, une hauteur stable — et le détail ne peut plus
 * déborder sur le nom de la commande suivante.
 *
 * Le détail passe par le catalogue ([OnyxLocalization]) : une commande du serveur arrive en
 * anglais et s'affiche en français quand la clé existe ; une **description de compétence** est une
 * donnée du serveur, inconnue du catalogue, et ressort donc telle quelle.
 */
@Composable
fun SlashMenu(
    items: List<SlashItem>,
    onPick: (String) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            // La hauteur au plus est **mesurée** par l'écran (l'espace entre les deux vitres) :
            // au-delà, la liste défile au lieu de pousser le fil hors de l'écran.
            .heightIn(max = maxHeight)
            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
            .border(1.dp, theme.separator, RoundedCornerShape(OnyxSpace.FieldCorner))
            .verticalScroll(rememberScrollState())
            .semanticsLabel("Slash commands"),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(pressedScale = 0.98f) { onPick(item.text) }
                    .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            ) {
                OnyxIcon(
                    symbol = Symbols.Terminal,
                    tint = theme.secondaryText,
                    size = 14.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = item.display,
                        // Un nom de commande est un identifiant : jamais un libellé du catalogue.
                        // Une ligne, sans ellipse à la fin — la règle de la maison pour un nom
                        // court (le menu `@` dessine son nom de bot de la même façon).
                        localize = false,
                        style = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    val detail = OnyxLocalization[item.detail]
                    if (detail.isNotBlank()) {
                        BasicText(
                            text = detail,
                            // Déjà traduit ci-dessus : la clé inconnue ressort inchangée, ce qui
                            // est exactement ce qu'on veut d'une description de compétence.
                            localize = false,
                            style = theme.caption.copy(color = theme.secondaryText),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (index != items.lastIndex) {
                // Le filet de la maison, qui commence après l'icône.
                OnyxRowDivider(inset = OnyxSpace.Snug + 14.dp + OnyxSpace.Half)
            }
        }
    }
}
