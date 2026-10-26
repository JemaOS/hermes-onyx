package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
 * « Hidden Bots », comme `HiddenBotsView`.
 *
 * Les bots cachés de la liste des conversations — ici comme dans Hermes Desktop — chacun avec son
 * bouton « Show », le pied qui dit ce qu'un bot caché garde, et le problème du serveur quand il
 * refuse. Quand le dernier est montré, l'écran se ferme comme le `dismiss`.
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
            .background(theme.formPageColor),
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
            OnyxFormSection(
                footer = "Hidden here and in Hermes Desktop alike. A hidden bot keeps its chat, " +
                    "and the other bots can still message it.",
            ) {
                if (state.hidden.isEmpty()) {
                    OnyxRow {
                        BasicText(
                            text = "No hidden bots",
                            style = theme.body.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
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

/** Une ligne de bot caché: son visage de 30 points, son nom, et « Show » à droite. */
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bot.description.isNotEmpty()) {
                BasicText(
                    text = bot.description,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 1,
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
