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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.repo.ServerRepository
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.onyx.app.ui.design.IdentifierText

/**
 * Les serveurs Hermes enregistrés (`ServersView`) : basculer, ajouter, renommer, retirer.
 *
 * Le serveur actif se règle avec l'écran de connexion habituel, qui en est la copie de travail.
 */
@Composable
fun ServersScreen(
    repository: ServerRepository,
    servers: List<ServerConfig>,
    activeId: String,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var menuFor by remember { mutableStateOf<ServerConfig?>(null) }
    var renaming by remember { mutableStateOf<ServerConfig?>(null) }
    var adding by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Servers", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            OnyxFormSection(
                footer = "Tap a server to switch to it; that starts a new conversation. Touch and hold to rename or to show its setup code for another device, swipe to remove one you're not using. Each server keeps its own addresses, keys, profile and model.",
            ) {
                servers.forEachIndexed { index, server ->
                    if (index > 0) OnyxRowDivider()
                    val active = server.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onyxTappable { scope.launch { repository.setActive(server.id) } }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = server.name.ifEmpty { server.displayName },
                                localize = false,
                                style = theme.body.copy(color = theme.textColor),
                            )
                            IdentifierText(
                                text = detailOf(server),
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                        if (active) {
                            OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                        }
                        OnyxIcon(
                            symbol = Symbols.Ellipsis,
                            tint = theme.secondaryText,
                            size = 15.dp,
                            modifier = Modifier.padding(start = 10.dp).onyxClickable { menuFor = server },
                        )
                    }
                }
            }

            OnyxFormSection(
                footer = "A new server can be filled in from a setup code: scan or paste one on its setup screen.",
            ) {
                OnyxRow(onClick = onEditConnection) {
                    OnyxIcon(Symbols.Globe, tint = theme.textColor, size = 18.dp)
                    BasicText("Edit connection…", style = theme.body.copy(color = theme.accent), modifier = Modifier.weight(1f))
                }
                OnyxRowDivider()
                OnyxRow(onClick = {
                    nameText = ""
                    adding = true
                }) {
                    OnyxIcon(Symbols.Plus, tint = theme.textColor, size = 18.dp)
                    BasicText("Add server…", style = theme.body.copy(color = theme.accent), modifier = Modifier.weight(1f))
                }
            }
        }
    }

    menuFor?.let { server ->
        OnyxMenu(
            items = buildList {
                add(MenuItem("Rename", Symbols.Pencil) {
                    menuFor = null
                    nameText = server.name
                    renaming = server
                })
                add(MenuItem("Show setup code", Symbols.Camera) {
                    menuFor = null
                })
                if (server.id != activeId) {
                    add(MenuItem("Remove", Symbols.Trash, destructive = true) {
                        menuFor = null
                        scope.launch { repository.remove(server.id) }
                    })
                }
            },
            onDismiss = { menuFor = null },
        )
    }

    if (adding) {
        OnyxPrompt(
            title = "New server",
            message = "Then set up how Onyx reaches it.",
            placeholder = "Name, e.g. Home or Office",
            value = nameText,
            onValueChange = { nameText = it },
            confirmLabel = "Add",
            onConfirm = {
                adding = false
                val id = repository.newId()
                scope.launch {
                    repository.upsert(ServerConfig(id = id, name = nameText.ifBlank { "Onyx" }))
                    repository.setActive(id)
                }
                onEditConnection()
            },
            onDismiss = { adding = false },
        )
    }

    renaming?.let { server ->
        OnyxPrompt(
            title = "Rename server",
            placeholder = "Name, e.g. Home or Office",
            value = nameText,
            onValueChange = { nameText = it },
            confirmLabel = "Save",
            onConfirm = {
                renaming = null
                scope.launch { repository.upsert(server.copy(name = nameText.ifBlank { server.name })) }
            },
            onDismiss = { renaming = null },
        )
    }
}

/** La connexion et l'adresse sous le nom, plus le profil quand ce n'est pas Default. */
private fun detailOf(server: ServerConfig): String {
    val address = if (server.transport == Transport.HERMES_SESSIONS) server.serveBaseUrl else server.baseUrl
    val connection = OnyxLocalization[
        if (server.transport == Transport.HERMES_SESSIONS) "Hermes API" else "Hermes Dashboard"
    ]
    val parts = buildList {
        add(connection)
        add(address.ifEmpty { OnyxLocalization["not set up"] })
        val profile = server.hermesProfile.trim()
        if (profile.isNotEmpty() && !profile.equals("default", ignoreCase = true)) add("profile $profile")
    }
    return parts.joinToString(" · ")
}
