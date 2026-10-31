package com.onyx.app.ui.sidebar

import com.onyx.app.ui.design.onyxPage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.Conversation
import com.onyx.app.ui.common.OnyxCardRow
import com.onyx.app.ui.common.OnyxLazyFormPage
import com.onyx.app.ui.common.onyxLazyCardFooter
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **« Archives » — la destination de la tuile *Archived* de la référence**, celle qui manquait.
 *
 * Chez la référence, la tuile *Archived* range la liste du volet sur les conversations archivées ;
 * chez Onyx, l'**état** existait depuis le début (`Conversation.archived`, la rangée et le lot de la
 * sélection savent archiver) mais **aucun écran** n'en montrait la liste : une fois rangée, une
 * conversation ne se revoyait que par sa case au fond de la liste. Cette page est le **seul** point
 * d'entrée qui manquait, et elle n'invente rien : elle lit les conversations de l'appareil, garde
 * celles qui sont archivées, et laisse les ressortir.
 *
 * C'est un écran **porté par le volet** (comme Planifiés, Compétences ou Aperçus) : il prend la
 * place de la tête, de la recherche, du sélecteur et de la liste, tandis que le pied du volet
 * (« Nouvelle conversation » et le rond d'initiale) ne bouge pas. Sa flèche **dépile le volet**
 * (`paneBack`), jamais une route de l'app.
 *
 * L'**état vide** est dit, avec les mots de la référence : rien d'archivé n'est un fait, pas un
 * défaut d'affichage. Et « Désarchiver » ramène la conversation dans la liste, tout de suite — la
 * même action que la feuille d'une rangée, au même endroit pour l'œil.
 *
 * [conversations] arrive **déjà réduite** par l'appelant (le volet connaît son magasin) : l'écran ne
 * lit pas de dépôt, il dessine ce qu'on lui donne — c'est ce qui le rend vérifiable sans appareil.
 */
@Composable
fun ArchivedScreen(
    conversations: List<Conversation>,
    onOpen: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // La liste se relit **quand l'entrée change**, pas à chaque recomposition : l'horloge n'est
    // lue que là — un âge « 12m » ne fait pas recomposer la page chaque seconde.
    val archived = remember(conversations) { archivedConversations(conversations) }
    val now = remember(conversations) { System.currentTimeMillis() / 1000.0 }

    Column(modifier = modifier.fillMaxSize().onyxPage(theme)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // Le titre est le mot de la maison pour l'ensemble des conversations rangées.
            OnyxPageHeader(title = "Archives", onBack = onBack)
        }

        // **La page est une liste paresseuse** : 500 conversations archivées ne composent que
        // leurs rangées visibles (défaut T9 de l'audit — la liste vivait dans un
        // `Column(verticalScroll)` et composait tout). La carte garde sa forme : `OnyxCardRow` en
        // pose la surface rangée par rangée, le pied reste celui de la référence.
        OnyxLazyFormPage(modifier = Modifier.weight(1f)) {
            if (archived.isEmpty()) {
                item(key = "empty") {
                    // L'état vide de la référence, mot pour mot, et traduit comme partout ailleurs.
                    OnyxEmptyState(
                        symbol = Symbols.ArchiveBox,
                        title = "Nothing archived",
                        description = "Archived sessions are hidden from Recent but stay resumable.",
                    )
                }
            } else {
                itemsIndexed(archived, key = { _, conversation -> "archived:" + conversation.id }) { index, conversation ->
                    OnyxCardRow(first = index == 0, last = index == archived.lastIndex) {
                    if (index > 0) OnyxRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // La rangée entière ouvre la conversation — c'est le geste de la
                            // liste du volet, au même endroit.
                            .onyxTappable { onOpen(conversation) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = conversation.title,
                                localize = false,
                                style = theme.body.copy(color = theme.textColor),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val age = sidebarAgeLabel(now - conversation.updatedAt / 1000.0)
                            if (age.isNotEmpty()) {
                                BasicText(
                                    text = age,
                                    localize = false,
                                    style = theme.footnote.copy(color = theme.secondaryText),
                                    maxLines = 1,
                                )
                            }
                        }
                        // « Désarchiver », tout de suite : la même action que la feuille d'une
                        // rangée, ici au bout de sa ligne — c'est tout l'objet de la page.
                        BasicText(
                            text = "Unarchive",
                            style = theme.footnote.copy(
                                // L'accent **mesuré** : le bleu de la maison sur la page quasi
                                // blanche vaut ~3,4:1, sous le seuil du texte. Il est écarté du
                                // fond du plus petit pas qui suffit — il reste bleu et devient
                                // lisible, comme partout ailleurs dans le volet.
                                color = sidebarInk(theme.accent, theme.pageColor, OnyxContrast.TEXT_MIN),
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = Modifier
                                .onyxTappable { onUnarchive(conversation) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        OnyxIcon(symbol = Symbols.ArchiveBox, tint = theme.tertiaryText, size = 14.dp)
                    }
                    }
                }
                onyxLazyCardFooter("Archived sessions are hidden from Recent but stay resumable.")
            }
        }
    }
}
