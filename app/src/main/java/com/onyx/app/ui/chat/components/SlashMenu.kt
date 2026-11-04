package com.onyx.app.ui.chat.components

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
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.LocalHazeState
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.liquidGlass
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **Le menu des commandes « / »**, posé au-dessus de la plaque du compositeur.
 *
 * Trois défauts relevés sur la capture du propriétaire — la version d'avant les portait tous :
 *
 * 1. **Il ne ressemblait pas à la maison.** C'était un aplat opaque cerné d'un filet d'un pixel, au
 *    coin du champ (`FieldCorner`) — posé juste au-dessus d'une **vitre de verre** au coin de
 *    panneau. Deux objets, deux familles : le menu se lisait comme une boîte étrangère collée sous
 *    le fil (« ça suit pas le thème ni l'encadré où je suis »). Il est maintenant la **même vitre**
 *    que la plaque ([GlassSurface.COMPOSER], le coin de la carte du compositeur) : une seule
 *    surface, une seule matière, et le fil qui défile reste visible au travers.
 *
 * 2. **Il n'était pas à l'échelle d'un téléphone.** Sa hauteur au plus valait la bande libre entre
 *    la barre du haut et le compositeur — soit presque tout l'écran sur un Magic V3 ouvert. Une
 *    commande dont le détail passait à deux lignes portait une rangée plus haute que sa voisine, et
 *    la liste perdait son pas au milieu. Ici : **une rangée, une hauteur** ([RowHeight], de quoi
 *    loger le nom et deux lignes de détail), et la liste est plafonnée à [MaxHeight] — au-delà elle
 *    **défile en elle-même**, sans jamais monter jusqu'à la barre du haut.
 *
 * 3. **On ne voyait pas qu'il y en avait d'autres.** Une liste coupée net sur une rangée entière a
 *    l'air complète. Le plafond de [MaxHeight] tombe **entre deux rangées**, donc la dernière est
 *    toujours entamée : sa coupure dit « ça continue » sans ajouter un mot, et le geste de
 *    défilement parcourt le reste sans faire disparaître le fil.
 *
 * Le détail passe par le catalogue ([OnyxLocalization]) : une commande du serveur arrive en anglais
 * et s'affiche en français quand la clé existe ; une **description de compétence** est une donnée du
 * serveur, inconnue du catalogue, et ressort donc telle quelle.
 */
@Composable
fun SlashMenu(
    items: List<SlashItem>,
    onPick: (String) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val haze = LocalHazeState.current
    val shape = RoundedCornerShape(ComposerCardRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            // La vitre de la plaque : même matière, même coin. `masking` est **vrai** — le menu
            // flotte au-dessus du fil qui défile, et sans le masquage on lirait les messages
            // **dans** la liste des commandes.
            //
            // **`lens = false`, et ce n'est pas un choix esthétique.** Le menu vit dans l'écran
            // qu'il recouvre : la lentille l'aurait fait échantillonner la page qui le contient, et
            // le moteur de rendu est mort d'un **débordement de pile** (`SIGSEGV` dans
            // RenderThread, mesuré au lancement avec la doublure). C'est exactement le cas que le
            // contrat de `liquidGlass` interdit ; la vitre de Haze, elle, donne le même verre — le
            // flou, la teinte et l'arête — sans courber le fond.
            .liquidGlass(GlassSurface.COMPOSER, shape, state = haze, masking = true, lens = false)
            .verticalScroll(rememberScrollState())
            .padding(vertical = OnyxSpace.Hair)
            .semanticsLabel("Slash commands"),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onyxClickable(pressedScale = 0.98f) { onPick(item.text) }
                    // **La rangée du téléphone : une hauteur fixe, quelle que soit la commande.**
                    //
                    // Elle vaut [RowHeight] — de quoi loger le nom et deux lignes de détail — et ne
                    // varie plus : c'est ce qui donne à la liste son pas, alors que la rangée
                    // grandissait avant pour chaque détail un peu long. Le rembourrage vertical est
                    // la marche la plus serrée de l'échelle : la hauteur de la rangée porte le
                    // texte, pas du vide.
                    .heightIn(min = RowHeight, max = RowHeight)
                    .padding(horizontal = OnyxSpace.Snug, vertical = OnyxSpace.Hair),
                // L'icône se cale sur la ligne du **nom** : centrée sur les deux lignes, elle
                // flottait entre le nom et le détail, et deux rangées voisines ne s'alignaient plus.
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
            ) {
                OnyxIcon(
                    symbol = Symbols.Terminal,
                    tint = theme.secondaryText,
                    size = 14.dp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = item.display,
                        // Un nom de commande est un identifiant : jamais un libellé du catalogue.
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
                            // **Deux lignes, et la rangée garde sa hauteur.**
                            //
                            // Deux lignes : c'est la politique de la maison pour un libellé (jamais
                            // de coupure sur une seule ligne). Ce qui faisait perdre le pas à la
                            // liste, ce n'était pas la deuxième ligne — c'était que la rangée
                            // **grandissait** pour elle. Elle a maintenant une hauteur fixe
                            // ([RowHeight]) qui loge le nom plus deux lignes : toutes les rangées
                            // se ressemblent, et le repli ne déplace plus rien.
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

/**
 * **La hauteur d'une rangée** : le nom, deux lignes de détail, et le rembourrage le plus serré.
 *
 * Fixe, et c'est le point : la rangée **grandissait** pour chaque détail un peu long, si bien que
 * deux voisines n'avaient plus la même hauteur et que la liste perdait son pas. Elle loge
 * maintenant le cas le plus haut, et rien ne bouge d'une commande à l'autre.
 */
val RowHeight: Dp = 78.dp

/**
 * **La hauteur au plus du menu** : cinq rangées et demie, et pas davantage.
 *
 * Cinq, parce que c'est ce qui tient dans le bas d'un téléphone en gardant le fil lisible au-dessus :
 * le menu complète ce qu'on tape, il ne remplace pas la conversation. La **demi** est voulue — la
 * dernière rangée est donc toujours entamée, et sa coupure dit « il y a une suite » sans qu'un mot
 * soit ajouté.
 */
val MaxHeight: Dp = RowHeight * 5.5f