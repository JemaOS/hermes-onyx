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
 * "Hidden Bots", a port of `HiddenBotsView` (Echo/Views/BotEditor:575).
 *
 * The conversation list's hidden bots — here as in Hermes Desktop — each with its
 * "Show" button, the footer that says what a hidden bot keeps, and the server's problem when it
 * refuses. When the last one is shown, the screen closes like the `dismiss()` of the reference.
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
            // The system area is taken by the root: the body's last row can no longer
            // be buried under the navigation bar or under the home-gesture area.
            // The inset is read at runtime (24 dp with gestures, 48 dp with three buttons) — never
            // a hard-coded value that only holds for one screen.
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
            // **The wait**: the roster comes from the gateway, and a slow read left the screen
            // empty — hence mute. Grey rows, as everywhere else.
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
                            // The sentence **explains where hidden bots would come from** instead of
                            // stating "no items": it is the gateway's roster (its
                            // `ui_meta.hidden`) that carries them, and what the phone hid
                            // itself. Nothing is hidden right now — and this is what that means.
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

/** A hidden bot row: its 30-point face, its name, and "Show" on the right. */
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
