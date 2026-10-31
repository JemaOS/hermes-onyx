package com.onyx.app.ui.chat

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatControls
import com.onyx.app.data.net.ChatControlsApi
import com.onyx.app.data.net.ServeRpc
import com.onyx.app.data.net.TransportError
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxActionSheet
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun runFastPreferenceKey(connectionKey: String, stored: String): String = "$connectionKey|$stored"

/** Le mode rapide de l'API Hermes, conservé par connexion et conversation (`RunChatControls`). */
class RunChatControlsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("onyx_chat_controls", Context.MODE_PRIVATE)

    fun isFast(connectionKey: String, stored: String): Boolean =
        preferences.getBoolean(runFastPreferenceKey(connectionKey, stored), false)

    fun setFast(on: Boolean, connectionKey: String, stored: String): ChatControls {
        preferences.edit().putBoolean(runFastPreferenceKey(connectionKey, stored), on).apply()
        return ChatControls(fast = on)
    }
}

internal fun chatControlsFooter(controls: ChatControls?, offersAutoApprove: Boolean): String {
    if (!offersAutoApprove) return ChatControls.fastFooter + " " + ChatControls.apiFastFooter
    val detail = if (controls?.serverNeverAsks == true) {
        ChatControls.serverNeverAsksFooter
    } else {
        ChatControls.conversationAutoApproveFooter
    }
    return ChatControls.fastFooter + " " + detail
}

/**
 * Les interrupteurs du menu Modèle (`ChatControlsSection`). Le Dashboard les applique à la
 * session sur le serveur ; l'API Hermes conserve le mode rapide localement pour chaque session.
 */
@Composable
fun ChatControlsSection(
    server: ServerConfig,
    stored: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offersAutoApprove = server.transport == Transport.HERMES_SERVE
    val connectionKey = server.configSignature
    val local = remember(context) { RunChatControlsStore(context) }
    val rpc = remember(server.configSignature, stored) {
        if (offersAutoApprove) ServeRpc(server) else null
    }
    val api = remember(rpc) { rpc?.let(::ChatControlsApi) }
    DisposableEffect(rpc) { onDispose { rpc?.close() } }

    var controls by remember(server.configSignature, stored) { mutableStateOf<ChatControls?>(null) }
    var busy by remember(server.configSignature, stored) { mutableStateOf(false) }
    var problem by remember(server.configSignature, stored) { mutableStateOf<String?>(null) }
    var confirmingAutoApprove by remember(server.configSignature, stored) { mutableStateOf(false) }
    // L'aide repliée de la section : fermée par défaut — c'est son intérêt.
    var helpOpen by remember(server.configSignature, stored) { mutableStateOf(false) }

    LaunchedEffect(server.configSignature, stored) {
        problem = null
        try {
            controls = if (api != null) {
                api.read(stored).controls
            } else {
                ChatControls(fast = local.isFast(connectionKey, stored))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            problem = OnyxLocalization["Couldn't read this conversation's settings:"] + " " + transportReason(error)
        }
    }

    fun change(work: suspend () -> ChatControls) {
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            try {
                controls = work()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val raw = transportReason(error)
                val refusal = ChatControls.fastRefusal(raw)
                problem = if (refusal == raw) raw else OnyxLocalization[refusal]
            } finally {
                busy = false
            }
        }
    }

    OnyxFormSection(
        modifier = modifier,
        header = "This conversation",
        // **Plus de paragraphe de cinq lignes sous un simple interrupteur.** L'explication longue
        // vivait dans le pied de section : elle poussait les deux interrupteurs vers le haut et se
        // lisait (ou pas) d'un bloc. Chaque interrupteur porte désormais **une sous-ligne courte**,
        // et le détail complet vit dans l'**aide repliée** de la fin de section.
        footer = null,
    ) {
        ControlsSwitchRow(
            label = "Fast mode",
            subtitle = "The provider's faster tier, often dearer.",
            checked = controls?.fast == true,
            enabled = controls != null && !busy,
            onCheckedChange = { on ->
                change {
                    api?.setFast(on, stored) ?: local.setFast(on, connectionKey, stored)
                }
            },
        )
        if (offersAutoApprove) {
            OnyxRowDivider()
            ControlsSwitchRow(
                label = "Run commands without asking",
                subtitle = if (controls?.serverNeverAsks == true) {
                    "This server never asks, for every conversation."
                } else {
                    "Commands in this conversation run without your go-ahead."
                },
                checked = controls?.autoApprove == true,
                enabled = controls != null && !busy && controls?.serverNeverAsks != true,
                onCheckedChange = { on ->
                    if (on) {
                        confirmingAutoApprove = true
                    } else {
                        change { api!!.setAutoApprove(false, stored) }
                    }
                },
            )
        }
        OnyxRowDivider()
        // **L'aide repliée** : le texte qui était le pied de section, à sa place — derrière un mot
        // qui dit ce qu'on va lire. Rien n'est supprimé de l'explication, elle ne s'impose plus.
        OnyxRow(onClick = { helpOpen = !helpOpen }) {
            BasicText(
                text = if (helpOpen) "Hide what these switches change" else "What these switches change",
                style = theme.footnote.copy(color = theme.accent),
                modifier = Modifier.weight(1f),
            )
            OnyxIcon(
                symbol = if (helpOpen) Symbols.ChevronDown else Symbols.ChevronRight,
                tint = theme.secondaryText,
                size = 12.dp,
            )
        }
        if (helpOpen) {
            BasicText(
                text = chatControlsFooter(controls, offersAutoApprove),
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        problem?.let {
            OnyxRow {
                OnyxIcon(Symbols.ExclamationTriangle, tint = theme.warning, size = 15.dp)
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.footnote.copy(color = theme.warning),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmingAutoApprove) {
        OnyxActionSheet(
            title = "Run commands without asking?",
            message = "In this conversation Hermes will run every command it wants to, including the ones it would have stopped to ask you about.",
            items = listOf(
                MenuItem("Run Without Asking", destructive = true) {
                    confirmingAutoApprove = false
                    change { api!!.setAutoApprove(true, stored) }
                },
            ),
            onDismiss = { confirmingAutoApprove = false },
        )
    }
}

private fun transportReason(error: Exception): String =
    (error as? TransportError)?.userMessage() ?: error.message ?: OnyxLocalization["unknown error"]

/**
 * **Un interrupteur et sa sous-ligne courte** — la rangée du mode rapide.
 *
 * Le défaut relevé sur la feuille de modèle : un simple interrupteur portait un **paragraphe de cinq
 * lignes** en pied de section. La rangée de la maison, elle, c'est un libellé et **une sous-ligne
 * courte** ; l'explication longue vit dans l'aide repliée de la section.
 *
 * La cible fait la hauteur d'une rangée de la maison (`OnyxToggleRow`), et l'interrupteur reste le
 * contrôle de fin de rangée : c'est lui qu'on touche, le libellé suit.
 */
@Composable
private fun ControlsSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (enabled) Modifier.onyxTappable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = label,
                style = theme.body.copy(color = if (enabled) theme.primaryText else theme.secondaryText),
            )
            // La sous-ligne : **courte**, et traduite comme le reste de la feuille.
            BasicText(
                text = subtitle,
                style = theme.footnote.copy(color = theme.secondaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OnyxSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
