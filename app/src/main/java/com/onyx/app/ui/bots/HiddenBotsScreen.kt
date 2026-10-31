package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxProgressView
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Hidden Bots », port de `HiddenBotsView` (Echo/Views/BotEditor:575).
 *
 * Les bots cachés de la liste des conversations — ici comme dans Hermes Desktop — chacun avec son
 * bouton « Show », le pied qui dit ce qu'un bot caché garde, et le problème du serveur quand il
 * refuse. Quand le dernier est montré, l'écran se ferme comme le `dismiss()` de la référence.
 */
@Composable
fun HiddenBotsScreen(
    viewModel: BotsViewModel,
    mainName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showing by viewModel.showing.collectAsStateWithLifecycle()
    val problem by viewModel.showProblem.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            // La zone du système est prise par la racine : la dernière rangée du corps ne peut
            // plus être enterrée sous la barre de navigation ni sous la zone du geste d'accueil.
            // L'encart est lu à l'exécution (24 dp en gestes, 48 dp à trois boutons) — jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Hidden Bots", onBack = null, action = "Done", onAction = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // **L'attente** : le roster vient du gateway, et une lecture lente laissait l'écran
            // vide — donc muet. Des rangées grises, comme partout ailleurs.
            if (state.loading && state.hidden.isEmpty()) {
                OnyxLoadingRows(label = "Loading from Onyx…")
                return@Column
            }

            OnyxFormSection(
                footer = "Hidden here and in Hermes Desktop alike. A hidden bot keeps its chat, " +
                    "and the other bots can still message it.",
            ) {
                if (state.hidden.isEmpty()) {
                    OnyxRow {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            BasicText(
                                text = "No hidden bots",
                                style = theme.body.copy(color = theme.primaryText),
                            )
                            // La phrase **explique d'où viendraient des bots cachés** au lieu de
                            // constater « aucun élément » : c'est le roster du gateway (son
                            // `ui_meta.hidden`) qui les porte, et ce que le téléphone a masqué
                            // lui-même. Rien n'est caché maintenant — et voilà ce que ça veut dire.
                            BasicText(
                                text = "Hidden bots are the gateway roster's own, shared with " +
                                    "Hermes Desktop. Nothing is hidden right now, so there is " +
                                    "nothing to bring back here.",
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
                state.hidden.forEachIndexed { index, bot ->
                    if (index > 0) OnyxRowDivider()
                    HiddenBotRow(
                        bot = bot,
                        mainName = mainName,
                        showing = showing == bot.name,
                        enabled = showing == null,
                        onShow = { viewModel.showBot(bot, onAllShown = onBack) },
                    )
                }
            }

            problem?.let { message ->
                OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 16.dp)
                        BasicText(
                            text = message,
                            localize = false,
                            style = theme.footnote.copy(color = theme.warning),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Une ligne de bot caché : son visage de 30 points, son nom, et « Show » à droite. */
@Composable
private fun HiddenBotRow(
    bot: BotProfile,
    mainName: String,
    showing: Boolean,
    enabled: Boolean,
    onShow: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BotLook(
            name = bot.name,
            faceCode = bot.faceCode,
            size = 30.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = bot.label(mainName),
                localize = false,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (bot.description.isNotEmpty()) {
                BasicText(
                    text = bot.description,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showing) {
            OnyxProgressView(size = 18.dp)
        } else {
            BasicText(
                text = "Show",
                style = theme.body.copy(color = if (enabled) theme.accent else theme.tertiaryText, fontWeight = FontWeight.Medium),
                modifier = Modifier.onyxClickable(enabled = enabled, onClick = onShow),
            )
        }
    }
}
