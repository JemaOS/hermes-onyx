package com.onyx.app.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Add Bot » sur la seule API Hermes (`APIBotEditor`).
 *
 * Un bot est l'un des profils du serveur: le nom de son dossier sous `~/.hermes/profiles`. Onyx
 * ouvre son chat, celui que Hermes Desktop et le Dashboard montrent pour lui.
 */
@Composable
fun APIBotEditorScreen(
    onAdd: (profile: String, title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var profile by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Add Bot",
                onBack = onBack,
                action = "Add",
                onAction = {
                    if (profile.isNotBlank()) onAdd(profile.trim(), title.trim())
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                footer = "A bot is one of your server's profiles: the name its folder has under ~/.hermes/profiles. Onyx opens its chat, the one Hermes Desktop and the Dashboard show for it.",
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = profile,
                        onValueChange = { profile = it },
                        placeholder = "Profile name",
                        style = theme.body,
                    )
                }
                OnyxRowDivider()
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Name (optional)",
                        style = theme.body,
                    )
                }
            }

            OnyxFormSection(
                header = "Its API key",
                footer = "Hermes takes the API_SERVER_KEY in the profile's own .env (~/.hermes/profiles/<name>/.env). Put your main API key there and the name alone does here; Hermes 0.21.3 and later leave it out when they copy a profile. Hermes 0.21.5 and later serve every profile over the API; on 0.21.3 and earlier, set gateway.multiplex_profiles: true in config.yaml and restart the gateway.",
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OnyxTextField(
                        value = key,
                        onValueChange = { key = it },
                        placeholder = "API key (only if it has its own)",
                        style = theme.body,
                    )
                }
            }

            if (profile.isBlank()) {
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = "Give it the name of a profile on your server.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                }
            }
        }
    }
}
