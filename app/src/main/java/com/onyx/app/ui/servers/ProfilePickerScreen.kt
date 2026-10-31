package com.onyx.app.ui.servers

import com.onyx.app.ui.design.onyxPage

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeProfilesApi
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * À quel profil Hermes Onyx parle (`ProfilePickerView`).
 *
 * La liste vient du Dashboard ; sans lui, un profil se nomme à la main, que l'API Hermes atteint
 * à `/p/<profil>/`.
 */
@Composable
fun ProfilePickerScreen(
    server: ServerConfig,
    onChoose: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var profiles by remember(server.id) { mutableStateOf<List<BotProfile>>(emptyList()) }
    var loading by remember(server.id) { mutableStateOf(false) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }
    var typedName by remember { mutableStateOf("") }

    val canList = server.hasServeCredentials

    LaunchedEffect(server.id) {
        if (!canList) return@LaunchedEffect
        loading = true
        runCatching { ServeProfilesApi(ServeClient(server)).profiles() }
            .onSuccess { profiles = it }
            .onFailure { problem = (it as? com.onyx.app.data.net.TransportError)?.userMessage() ?: it.message }
        loading = false
    }

    val current = server.effectiveProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Profile", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                footer = "Chats, sessions, projects, skills, tools, cron jobs and the context and memory files follow the profile. The Kanban board is shared by every profile. Switching starts a new conversation.",
            ) {
                ProfileRow(
                    title = "Default",
                    subtitle = "The server's main profile.",
                    selected = current == null,
                    localizeContent = true,
                ) { onChoose(null) }

                profiles.filterNot { it.isDefault }.forEach { profile ->
                    OnyxRowDivider()
                    ProfileRow(
                        title = profile.title?.takeIf { it.isNotBlank() } ?: profile.name,
                        subtitle = profile.description.takeIf { it.isNotBlank() },
                        selected = current == profile.name,
                    ) { onChoose(profile.name) }
                }

                if (loading) {
                    OnyxRowDivider()
                    OnyxRow { BasicText("Loading profiles…", style = theme.footnote.copy(color = theme.secondaryText)) }
                }
                problem?.let {
                    OnyxRowDivider()
                    OnyxRow {
                        BasicText(it, localize = false, style = theme.footnote.copy(color = theme.secondaryText))
                    }
                }
            }

            if (!canList || problem != null) {
                OnyxFormSection(
                    header = "By name",
                    footer = if (canList) {
                        "Couldn't load the list, but you can still name a profile."
                    } else {
                        "Add the Hermes Dashboard login in Connection details to pick from a list. Over the Hermes API, a profile other than Default needs gateway.multiplex_profiles turned on in the gateway's config."
                    },
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OnyxTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            placeholder = "Profile name",
                            style = theme.body,
                        )
                    }
                    OnyxRowDivider()
                    OnyxRow(onClick = {
                        if (typedName.isNotBlank()) onChoose(typedName.trim())
                    }) {
                        BasicText(
                            text = "Use this profile",
                            style = theme.body.copy(
                                color = if (typedName.isBlank()) theme.tertiaryText else theme.accent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    localizeContent: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onyxTappable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(title, localize = localizeContent, style = theme.body.copy(color = theme.textColor))
            subtitle?.let { BasicText(it, localize = localizeContent, style = theme.footnote.copy(color = theme.secondaryText)) }
        }
        if (selected) {
            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
        }
    }
}
