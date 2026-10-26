package com.onyx.app.ui.chat

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.onyx.app.ui.design.OnyxToggleRow
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
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
 * session sur le serveur; l'API Hermes conserve le mode rapide localement pour chaque session.
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
        footer = chatControlsFooter(controls, offersAutoApprove),
    ) {
        OnyxToggleRow(
            label = "Fast mode",
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
            OnyxToggleRow(
                label = "Run commands without asking",
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
