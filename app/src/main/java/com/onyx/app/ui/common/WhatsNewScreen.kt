package com.onyx.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Une nouveauté d'une version (`WhatsNew.Item`). */
data class WhatsNewItem(val symbol: Int, val title: String, val detail: String)

/**
 * « What's New in Onyx » (`WhatsNewView`).
 *
 * Une version, ses points forts avec leur symbole, et **Continue** en bas.
 */
@Composable
fun WhatsNewScreen(
    version: String,
    items: List<WhatsNewItem>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = "What's New in Onyx",
                    style = theme.largeTitle.copy(color = theme.textColor, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "Version $version",
                    style = theme.subheadline.copy(color = theme.secondaryText),
                )
            }

            items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.padding(top = 2.dp)) {
                        OnyxIcon(symbol = item.symbol, tint = theme.accent, size = 22.dp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(item.title, style = theme.headline.copy(color = theme.textColor))
                        BasicText(item.detail, style = theme.subheadline.copy(color = theme.secondaryText))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceColor)
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.accent, RoundedCornerShape(12.dp))
                    .onyxClickable(onClick = onContinue)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Continue",
                    style = theme.headline.copy(color = theme.userText),
                )
            }
        }
    }
}

/** Les points forts de la version courante. */
val CURRENT_RELEASE_ITEMS = listOf(
    WhatsNewItem(
        symbol = Symbols.Sparkles,
        title = "Bots, with faces",
        detail = "Every Hermes profile shows up as a bot, with an orb or a face you pick. Group chats let several answer in turn.",
    ),
    WhatsNewItem(
        symbol = Symbols.Doc,
        title = "Code, diagrams and math",
        detail = "Code is coloured and numbered, with a copy button; Mermaid and formulas are drawn on the phone, offline.",
    ),
    WhatsNewItem(
        symbol = Symbols.Mic,
        title = "Voice that stops when you say so",
        detail = "Hands-free listening ends on your own stop phrases, and reading a reply aloud stops the moment you speak.",
    ),
    WhatsNewItem(
        symbol = Symbols.ServerRack,
        title = "Several servers",
        detail = "Switch between gateways, each with its own addresses, keys, profile and model.",
    ),
)
