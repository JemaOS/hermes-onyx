package com.onyx.app.ui.terminal

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** États visibles de la coquille, comme `TerminalModel.Phase` côté la référence. */
enum class TerminalPhase { NEEDS_ACCOUNT, CONNECTING, FAILED, LIVE }

data class TerminalFailure(val message: String, val wantsKey: Boolean)

/** Le modèle possède la session ; aucun mot de passe ni `PromiscuousVerifier` ne subsiste. */
class TerminalModel(
    private val terminal: SshTerminal,
    private val identity: SshIdentity,
    private val knownHosts: TerminalKnownHostsStore,
) {
    val buffer = TerminalBuffer()
    var revision by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(TerminalPhase.CONNECTING)
    var failure by mutableStateOf<TerminalFailure?>(null)
    var title by mutableStateOf("Terminal")
    var ended by mutableStateOf<String?>(null)
    var askingTrust by mutableStateOf<String?>(null)
        private set
    private var asked: TerminalTarget? = null
    private var attempt = 0L
    private var connecting: Job? = null
    private var reading: Job? = null

    val showsScreen: Boolean get() = phase == TerminalPhase.LIVE

    fun connect(target: TerminalTarget, scope: CoroutineScope) {
        val current = ++attempt
        connecting?.cancel()
        reading?.cancel()
        connecting = scope.launch {
            phase = TerminalPhase.CONNECTING
            failure = null
            ended = null
            askingTrust = null
            title = "${target.user}@${target.host}"
            try {
                val key = withContext(Dispatchers.IO) { identity.keyPair() } ?: throw TerminalProblem.NoKey
                val generation = terminal.connect(target, key, buffer.columns, buffer.rows)
                if (current != attempt) return@launch
                buffer.clear()
                revision++
                phase = TerminalPhase.LIVE
                reading = scope.launch {
                    terminal.read(
                        generation,
                        onOutput = { text ->
                            withContext(Dispatchers.Main.immediate) {
                                if (current == attempt) {
                                    buffer.write(text)
                                    revision++
                                }
                            }
                        },
                        onEnded = { why ->
                            withContext(Dispatchers.Main.immediate) {
                                if (current == attempt) ended = why
                            }
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: TerminalProblem.UnknownServer) {
                if (current == attempt) {
                    asked = target
                    askingTrust = error.fingerprint
                }
            } catch (error: Exception) {
                if (current == attempt) fail(error)
            }
        }
    }

    fun answerTrust(yes: Boolean, scope: CoroutineScope) {
        val fingerprint = askingTrust
        val target = asked ?: return
        askingTrust = null
        asked = null
        if (!yes) {
            phase = TerminalPhase.FAILED
            failure = TerminalFailure("Not connected: the server's key wasn't trusted.", false)
            return
        }
        if (fingerprint != null) {
            knownHosts.trust(fingerprint, TerminalKnownHosts.name(target.host, target.port))
        }
        connect(target, scope)
    }

    fun send(bytes: ByteArray, scope: CoroutineScope) {
        if (bytes.isEmpty()) return
        scope.launch {
            try {
                terminal.send(bytes)
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    fun resize(columns: Int, rows: Int, scope: CoroutineScope) {
        scope.launch { terminal.resize(columns, rows) }
    }

    fun close() {
        attempt++
        connecting?.cancel()
        reading?.cancel()
        connecting = null
        reading = null
        askingTrust = null
        asked = null
        terminal.close()
    }

    private fun fail(error: Exception) {
        phase = TerminalPhase.FAILED
        failure = TerminalFailure(
            error.message ?: OnyxLocalization["Couldn't connect."],
            (error as? TerminalProblem)?.wantsKey == true,
        )
    }
}

/** Terminal plein écran : compte, confiance TOFU, coquille, fin, reconnexion et menu More. */
@Composable
fun TerminalScreen(
    server: ServerConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    folder: String? = null,
    folderLookup: (suspend () -> String?)? = null,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val identity = remember { SshIdentity(context) }
    val knownHosts = remember { TerminalKnownHostsStore(context) }
    val accounts = remember { TerminalAccountStore(context) }
    val model = remember { TerminalModel(SshTerminal(knownHosts), identity, knownHosts) }
    var account by remember { mutableStateOf(TerminalAccount()) }
    var startFolder by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    fun target() = TerminalTarget(
        host = account.host.trim(),
        port = account.port,
        user = account.user.trim(),
        folder = startFolder,
    )
    fun close() {
        model.close()
        onBack()
    }

    LaunchedEffect(server.id) {
        // Comme la référence : rien n'est prérempli tant qu'aucun compte n'a été enregistré.
        // L'hôte que le serveur suggère s'affiche en **gris dans le champ** (son exemple), et
        // reste une valeur vide — si bien que la première alerte nomme le nom d'hôte.
        account = accounts.saved(server.id) ?: TerminalAccount()
        startFolder = folder ?: folderLookup?.invoke()
        if (account.isComplete) model.connect(target(), scope) else model.phase = TerminalPhase.NEEDS_ACCOUNT
    }
    DisposableEffect(model) { onDispose { model.close() } }
    BackHandler { close() }

    Column(modifier = modifier.fillMaxSize().onyxPage(theme)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = if (model.showsScreen) model.title else "Terminal",
                onBack = ::close,
                backLabel = "Close",
                action = "More",
                onAction = { showMenu = true },
                localizeTitle = !model.showsScreen,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TerminalSurface(
                model = model,
                scope = scope,
                modifier = Modifier.alpha(if (model.showsScreen) 1f else 0f),
            )
            if (!model.showsScreen || model.ended != null) {
                TerminalStatusCard(
                    model = model,
                    account = account,
                    identity = identity,
                    accounts = accounts,
                    serverId = server.id,
                    hostPlaceholder = TerminalAccount
                        .suggestedHost(server.serveBaseUrl, server.baseUrl)
                        .ifEmpty { "hermes.example.ts.net" },
                    onAccount = { account = it },
                    onConnect = { model.connect(target(), scope) },
                )
            }
        }

        if (model.showsScreen && model.ended == null) {
            TerminalInputBar(model, scope)
        }
    }

    if (showMenu) {
        OnyxMenu(
            items = buildList {
                add(
                    MenuItem("Reconnect", Symbols.ArrowClockwise) {
                        showMenu = false
                        if (model.phase != TerminalPhase.CONNECTING && account.isComplete) {
                            model.connect(target(), scope)
                        }
                    },
                )
                add(
                    MenuItem("Copy This Phone's Key", Symbols.Key) {
                        showMenu = false
                        val line = identity.publicKeyLine() ?: return@MenuItem
                        scope.launch {
                            clipboard.setClipEntry(
                                androidx.compose.ui.platform.ClipEntry(
                                    ClipData.newPlainText("SSH public key", line),
                                ),
                            )
                        }
                    },
                )
            },
            onDismiss = { showMenu = false },
        )
    }

    model.askingTrust?.let { fingerprint ->
        OnyxAlert(
            title = "Trust this server?",
            message = "This is the first time this phone connects to ${account.host}. Its key's fingerprint is $fingerprint. " +
                "If you can, check it on the server with ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub.",
            actions = listOf(
                AlertAction("Trust") { model.answerTrust(true, scope) },
                AlertAction("Cancel") { model.answerTrust(false, scope) },
            ),
            // On ne ferme pas une question de confiance en touchant à côté : ses deux boutons
            // sont les seules issues, comme l'alerte de la référence.
            onDismiss = null,
        )
    }
}

@Composable
private fun TerminalStatusCard(
    model: TerminalModel,
    account: TerminalAccount,
    identity: SshIdentity,
    accounts: TerminalAccountStore,
    serverId: String,
    hostPlaceholder: String,
    onAccount: (TerminalAccount) -> Unit,
    onConnect: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        when (model.phase) {
            TerminalPhase.NEEDS_ACCOUNT -> TerminalAccountForm(
                account = account,
                onAccount = onAccount,
                onConnect = onConnect,
                accounts = accounts,
                serverId = serverId,
                identity = identity,
                hostPlaceholder = hostPlaceholder,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            TerminalPhase.CONNECTING -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TerminalSpinner()
                BasicText(
                    text = "Connecting to ${account.host}…",
                    style = theme.body.copy(color = theme.secondaryText),
                )
            }
            TerminalPhase.FAILED -> Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnyxIcon(Symbols.ExclamationTriangle, tint = theme.secondaryText, size = 20.dp)
                    BasicText(
                        text = model.failure?.message.orEmpty(),
                        style = theme.body.copy(color = theme.secondaryText),
                    )
                }
                if (model.failure?.wantsKey == true) {
                    TerminalKeyCard(identity = identity, user = account.user, host = account.host)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TerminalButton("Try Again", primary = true, onClick = onConnect)
                    TerminalButton("Edit Account", primary = false) {
                        model.phase = TerminalPhase.NEEDS_ACCOUNT
                    }
                }
            }
            TerminalPhase.LIVE -> model.ended?.let { ended ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FieldCorner))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        text = ended,
                        style = theme.footnote.copy(color = theme.secondaryText),
                    )
                    TerminalButton("Reconnect", primary = true, onClick = onConnect)
                }
            }
        }
    }
}

/** Formulaire Host/Port/User partagé par le terminal et ses réglages, enregistré à chaque frappe. */
@Composable
private fun TerminalAccountForm(
    account: TerminalAccount,
    onAccount: (TerminalAccount) -> Unit,
    accounts: TerminalAccountStore,
    serverId: String,
    modifier: Modifier = Modifier,
    onConnect: (() -> Unit)? = null,
    askedToConnect: Int = 0,
    identity: SshIdentity? = null,
    /**
     * Ce qui est écrit en gris dans le champ *Host* tant qu'il est vide : l'hôte que le serveur
     * suggère. C'est un **exemple**, pas une valeur — comme la référence, qui montre là
     * `hermes.example.ts.net` et laisse le champ vide, si bien que le premier mot d'alerte
     * nomme le nom d'hôte, et non le reste.
     */
    hostPlaceholder: String = "hermes.example.ts.net",
) {
    val theme = LocalOnyxTheme.current
    val hostFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    var portText by remember { mutableStateOf(account.port.toString()) }

    fun focusMissing() {
        when (account.missing) {
            TerminalMissing.HOST -> hostFocus.requestFocus()
            TerminalMissing.PORT -> portFocus.requestFocus()
            TerminalMissing.USER -> userFocus.requestFocus()
            null -> Unit
        }
    }
    LaunchedEffect(account, serverId) { accounts.save(account, serverId) }
    LaunchedEffect(askedToConnect) { if (askedToConnect > 0) focusMissing() }

    Column(
        modifier = modifier.widthIn(max = 560.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(
            text = "Sign in to your server over SSH, with a key made on this phone. Passwords aren't used.",
            style = theme.subheadline.copy(color = theme.secondaryText),
        )
        TerminalField("Host", account.host, hostPlaceholder, hostFocus) {
            onAccount(account.copy(host = it))
        }
        TerminalField("Port", portText, "22", portFocus, KeyboardType.Number) {
            portText = it
            onAccount(account.copy(port = it.toIntOrNull() ?: 0))
        }
        TerminalField("User", account.user, "required, such as hermes", userFocus) {
            onAccount(account.copy(user = it))
        }
        account.missing?.let { missing ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnyxIcon(Symbols.ExclamationCircle, tint = theme.warning, size = 16.dp)
                BasicText(
                    text = "To connect, fill in ${missing.words}.",
                    style = theme.footnote.copy(color = theme.warning),
                )
            }
        }
        if (identity != null) {
            TerminalKeyCard(identity = identity, user = account.user, host = account.host)
        }
        if (onConnect != null) {
            TerminalButton("Connect", primary = true) {
                if (account.missing != null) focusMissing() else onConnect()
            }
        }
    }
}

@Composable
private fun TerminalField(
    label: String,
    value: String,
    placeholder: String,
    focus: FocusRequester,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(text = label, style = theme.body.copy(color = theme.textColor))
        OnyxTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            style = theme.body,
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
    }
}

/** Carte de la clé publique : copie, partage et destination authorized_keys. */
@Composable
private fun TerminalKeyCard(
    identity: SshIdentity,
    user: String,
    host: String,
    keyGeneration: Int = 0,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(keyGeneration) {
        line = withContext(Dispatchers.IO) { identity.publicKeyLine() }
        copied = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.fill, RoundedCornerShape(OnyxSpace.FormCorner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            text = "This phone's key",
            style = theme.subheadline.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
        )
        SelectionContainer {
            BasicText(
                text = line ?: "Unlock this phone to make its key.",
                style = theme.caption.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                maxLines = 4,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText(
                text = if (copied) "Copied" else "Copy",
                style = theme.footnote.copy(color = theme.accent),
                modifier = Modifier.onyxClickable(enabled = line != null) {
                    val key = line ?: return@onyxClickable
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(ClipData.newPlainText("SSH public key", key)),
                        )
                        copied = true
                    }
                },
            )
            if (line != null) {
                BasicText(
                    text = "Share",
                    style = theme.footnote.copy(color = theme.accent),
                    modifier = Modifier.onyxClickable {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, line)
                        }
                        context.startActivity(Intent.createChooser(send, "Share SSH public key"))
                    },
                )
            }
        }
        BasicText(
            text = "Add it as one line to ~/.ssh/authorized_keys for ${user.ifEmpty { "that user" }} on ${host.ifEmpty { "the server" }}. " +
                "The key was made on this phone and never leaves it.",
            style = theme.caption.copy(color = theme.secondaryText),
        )
    }
}

@Composable
private fun TerminalSurface(model: TerminalModel, scope: CoroutineScope, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    var size by remember { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(size, model.phase) {
        val current = size ?: return@LaunchedEffect
        if (model.showsScreen) {
            model.resize((current.width / 8).coerceAtLeast(20), (current.height / 19).coerceAtLeast(5), scope)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp)
            .onSizeChanged { size = it }
            .background(theme.pageColor),
    ) {
        val lines = remember(model.revision) {
            model.buffer.lines().mapIndexed { row, line -> paintLine(model.buffer, row, line) }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            lines.forEach { line ->
                BasicText(
                    text = if (line.isEmpty()) AnnotatedString(" ") else line,
                    style = theme.footnote.copy(color = theme.textColor, fontFamily = FontFamily.Monospace),
                    softWrap = false,
                )
            }
        }
    }
}

/** Champ clavier et touches SwiftTerm : texte direct, Backspace, Esc, Ctrl, Tab et flèches. */
@Composable
private fun TerminalInputBar(model: TerminalModel, scope: CoroutineScope) {
    val theme = LocalOnyxTheme.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var sink by remember { mutableStateOf(" ") }
    var control by remember { mutableStateOf(false) }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        var rest = text
        if (control) {
            val code = TerminalKeys.control(rest.first())
            if (code != null) {
                model.send(byteArrayOf(code.toByte()), scope)
                rest = rest.drop(1)
            }
            control = false
        }
        if (rest.isNotEmpty()) model.send(rest.replace("\n", "\r").toByteArray(), scope)
    }

    LaunchedEffect(Unit) {
        delay(200)
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.pageColor)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        BasicTextField(
            value = sink,
            onValueChange = { next ->
                val previous = sink
                sink = " "
                when {
                    next.length < previous.length -> model.send(byteArrayOf(TerminalKeys.BACKSPACE.toByte()), scope)
                    next != previous -> {
                        val inserted = when {
                            next.startsWith(previous) -> next.substring(previous.length)
                            next.endsWith(previous) -> next.dropLast(previous.length)
                            else -> next
                        }
                        sendText(inserted)
                    }
                }
            },
            textStyle = theme.caption.copy(color = Color.Transparent),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { model.send(byteArrayOf(TerminalKeys.RETURN.toByte()), scope) }),
            modifier = Modifier.size(1.dp).focusRequester(focus),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TerminalKey("Esc") { model.send(byteArrayOf(TerminalKeys.ESCAPE.toByte()), scope) }
            TerminalKey("Ctrl", selected = control) { control = !control }
            TerminalKey("Tab") { model.send(byteArrayOf(TerminalKeys.TAB.toByte()), scope) }
            TerminalKey("←") { model.send(TerminalKeys.arrow('D'), scope) }
            TerminalKey("↓") { model.send(TerminalKeys.arrow('B'), scope) }
            TerminalKey("↑") { model.send(TerminalKeys.arrow('A'), scope) }
            TerminalKey("→") { model.send(TerminalKeys.arrow('C'), scope) }
            TerminalKey("⌫") { model.send(byteArrayOf(TerminalKeys.BACKSPACE.toByte()), scope) }
            TerminalKey("Return") { model.send(byteArrayOf(TerminalKeys.RETURN.toByte()), scope) }
        }
    }
}

@Composable
private fun TerminalKey(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = label,
        style = theme.footnote.copy(color = if (selected) theme.pageColor else theme.textColor),
        modifier = Modifier
            .background(if (selected) theme.accent else theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
            .onyxClickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun TerminalButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = label,
        style = theme.body.copy(color = if (primary) theme.pageColor else theme.accent),
        modifier = Modifier
            .background(if (primary) theme.accent else theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
            .onyxClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun TerminalSpinner() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "terminal-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "terminal-spinner-angle",
    )
    Canvas(modifier = Modifier.size(22.dp).rotate(angle)) {
        drawArc(
            color = theme.secondaryText,
            startAngle = 20f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Réglages › Terminal : compte, clé du téléphone, clé connue du serveur et renouvellement. */
@Composable
fun TerminalSettingsView(
    server: ServerConfig,
    onOpenTerminal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val identity = remember { SshIdentity(context) }
    val knownHosts = remember { TerminalKnownHostsStore(context) }
    val accounts = remember { TerminalAccountStore(context) }
    var account by remember { mutableStateOf<TerminalAccount?>(null) }
    var trusted by remember { mutableStateOf<String?>(null) }
    var confirmNewKey by remember { mutableStateOf(false) }
    var askedToConnect by remember { mutableIntStateOf(0) }
    var keyGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(server.id) {
        // Comme la référence : l'hôte suggéré est l'exemple en gris du champ, pas une valeur.
        account = accounts.saved(server.id) ?: TerminalAccount()
    }
    val current = account
    val knownName = TerminalKnownHosts.name(current?.host?.trim().orEmpty(), current?.port ?: 22)
    LaunchedEffect(knownName, current) { trusted = knownHosts.trusted(knownName) }

    Column(modifier = modifier.fillMaxSize().onyxPage(theme)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Terminal", onBack = onBack)
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 40.dp)) {
            if (current != null) {
                OnyxFormSection {
                    TerminalAccountForm(
                        account = current,
                        onAccount = { account = it },
                        accounts = accounts,
                        serverId = server.id,
                        askedToConnect = askedToConnect,
                        hostPlaceholder = TerminalAccount
                            .suggestedHost(server.serveBaseUrl, server.baseUrl)
                            .ifEmpty { "hermes.example.ts.net" },
                    )
                }
                OnyxFormSection {
                    OnyxRow(onClick = {
                        if (current.isComplete) onOpenTerminal() else askedToConnect++
                    }) {
                        OnyxIcon(Symbols.Terminal, tint = theme.textColor, size = 18.dp)
                        BasicText(
                            text = "Open Terminal",
                            style = theme.body.copy(color = theme.accent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OnyxFormSection(
                    header = "Server's key",
                    footer = "A server that shows another key later is refused, in case something is in the way. " +
                        "Forget the old one only if the server was reinstalled.",
                ) {
                    if (trusted != null) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BasicText(text = "Fingerprint", style = theme.body.copy(color = theme.textColor))
                            SelectionContainer {
                                BasicText(
                                    text = trusted.orEmpty(),
                                    localize = false,
                                    style = theme.caption.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                        OnyxRowDivider()
                        OnyxRow(onClick = {
                            knownHosts.forget(knownName)
                            trusted = null
                        }) {
                            BasicText(
                                text = "Forget This Server's Key",
                                style = theme.body.copy(color = theme.error),
                            )
                        }
                    } else {
                        OnyxRow {
                            BasicText(
                                text = "Not met yet: the first connection shows its key's fingerprint and asks.",
                                style = theme.footnote.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
                OnyxFormSection(
                    footer = "Servers that have the old key won't let this phone in until they have the new one.",
                ) {
                    OnyxRow(onClick = { confirmNewKey = true }) {
                        BasicText(
                            text = "Make a New Key for This Phone",
                            style = theme.body.copy(color = theme.error),
                        )
                    }
                }
                OnyxFormSection(header = "This phone's key") {
                    TerminalKeyCard(
                        identity = identity,
                        user = current.user,
                        host = current.host,
                        keyGeneration = keyGeneration,
                    )
                }
            }
        }
    }

    if (confirmNewKey) {
        OnyxAlert(
            title = "Make a new key?",
            actions = listOf(
                AlertAction("Make a New Key", destructive = true) {
                    confirmNewKey = false
                    identity.replace()
                    keyGeneration++
                },
                AlertAction("Cancel") { confirmNewKey = false },
            ),
            onDismiss = { confirmNewKey = false },
        )
    }
}

/** Une ligne du tampon : couleurs SGR et inversion, indexée par rangée (doublons compris). */
private fun paintLine(buffer: TerminalBuffer, row: Int, line: String): AnnotatedString {
    if (line.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        line.forEachIndexed { column, char ->
            val style = buffer.cell(row, column).style
            val foreground = when {
                style.inverse -> AnsiColours.getOrElse(style.background) { 0xFF0E0E11 }
                style.foreground >= 0 -> AnsiColours.getOrElse(style.foreground) { 0xFFE8E8EE }
                else -> 0xFFE8E8EE
            }
            val background = when {
                style.inverse -> AnsiColours.getOrElse(style.foreground) { 0xFFE8E8EE }
                style.background >= 0 -> AnsiColours.getOrElse(style.background) { 0xFF0E0E11 }
                else -> null
            }
            withStyle(
                SpanStyle(
                    color = Color(foreground),
                    background = background?.let { Color(it) } ?: Color.Unspecified,
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                ),
            ) {
                append(char)
            }
        }
    }
}
