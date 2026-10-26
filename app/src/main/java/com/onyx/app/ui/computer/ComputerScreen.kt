package com.onyx.app.ui.computer

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.net.ComputerInput
import com.onyx.app.data.net.ComputerBridgeClient
import com.onyx.app.data.net.ComputerGestures
import com.onyx.app.data.net.ComputerHello
import com.onyx.app.data.net.ComputerPoint
import com.onyx.app.data.net.ComputerScale
import com.onyx.app.data.net.ComputerState
import com.onyx.app.data.net.text
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxProgressView
import com.onyx.app.ui.design.OnyxPrompt
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * L'écran « Ordinateur »: l'écran du PC en direct, et le doigt qui le pilote.
 *
 * Trois choses y vivent, dans cet ordre:
 *
 * 1. **l'aperçu** — l'image du PC, rafraîchie en continu, avec sous elle la dernière action
 * (un clic, une frappe, une commande) et qui a la main;
 * 2. **la main** — « Prendre la main » / « Rendre la main ». Sans elle, l'aperçu regarde: le pont
 * refuse chaque entrée, et l'écran le dit au lieu de laisser croire à une panne;
 * 3. **l'approbation** — une commande ne part qu'après un « oui », et un refus ne part pas du tout.
 *
 * La session, elle, n'appartient pas à cet écran (voir [ComputerSession]): sortir de l'écran et y
 * revenir retrouve la même connexion, la même image et la même question posée.
 */
@Composable
fun ComputerScreen(
    session: ComputerSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current

    val phase by session.phase.collectAsStateWithLifecycle()
    val hello by session.hello.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()
    val frame by session.frame.collectAsStateWithLifecycle()
    val notice by session.notice.collectAsStateWithLifecycle()
    val refusal by session.refused.collectAsStateWithLifecycle()
    val blocked by session.blocked.collectAsStateWithLifecycle()
    val approval by session.approval.collectAsStateWithLifecycle()
    val started by session.started.collectAsStateWithLifecycle()
    val runOutput by session.runOutput.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(!session.configured) }
    var draftAddress by remember { mutableStateOf(session.address) }
    var draftToken by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var askingCommand by remember { mutableStateOf(false) }
    var askingShortcut by remember { mutableStateOf(false) }
    var commandDraft by remember { mutableStateOf("") }
    var shortcutDraft by remember { mutableStateOf("") }

    // La session se reprend au premier passage sur l'écran: elle n'est pas refermée en sortant.
    LaunchedEffect(Unit) { if (session.configured && !started) session.open() }
    DisposableEffect(session) {
        onDispose {
            // Rien ici, exprès: quitter l'écran ne rend pas la main et ne coupe pas la connexion.
            // Seul « Fermer la session » appelle `stop`.
        }
    }
    BackHandler { onBack() }

    val bitmap = remember(frame) {
        frame?.bytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(theme.pageColor)) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Computer",
                onBack = onBack,
                backLabel = "Back",
                action = "More",
                onAction = { showMenu = true },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                editing -> ComputerSetup(
                    address = draftAddress,
                    onAddress = { draftAddress = it },
                    token = draftToken,
                    onToken = { draftToken = it },
                    hasStoredToken = session.maskedToken.isNotEmpty(),
                    keystoreRefused = session.keystoreRefused,
                    onCopyToken = {
                        clipboard.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText("Computer bridge token", session.tokenForCopy()),
                        )
                    },
                    onSave = {
                        session.configure(draftAddress, draftToken)
                        editing = false
                        draftToken = ""
                        session.open()
                    },
                    onCancel = if (session.configured) ({ editing = false }) else null,
                )

                bitmap != null && hello != null -> ComputerPreview(
                    bitmap = bitmap,
                    screenWidth = hello!!.screenWidth,
                    screenHeight = hello!!.screenHeight,
                    onPoint = { point -> session.tap(point) },
                    onDrag = { from, to -> session.drag(from, to) },
                    modifier = Modifier.fillMaxSize(),
                )

                bitmap != null -> ComputerPreview(
                    bitmap = bitmap,
                    screenWidth = bitmap.width,
                    screenHeight = bitmap.height,
                    onPoint = { point -> session.tap(point) },
                    onDrag = { from, to -> session.drag(from, to) },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> ComputerWaiting(
                    phase = phase,
                    notice = notice,
                    onRetry = { session.retry() },
                )
            }
        }

        ComputerStatusLine(state = state, hello = hello)

        refusal?.let { detail ->
            ComputerNote(
                text = OnyxLocalization.format("Refused: nothing was sent. (%1\$s)", detail),
                tone = theme.secondaryText,
                onDismiss = { session.clearRefusal() },
            )
        }
        blocked?.let { command ->
            ComputerNote(
                text = OnyxLocalization.format("Onyx refuses this command. (%1\$s)", command),
                tone = theme.secondaryText,
                onDismiss = { session.clearBlocked() },
            )
        }
        notice?.let { message ->
            ComputerNote(text = message, tone = theme.secondaryText, onDismiss = null)
        }
        runOutput?.let { output -> ComputerOutput(output) }

        ComputerControls(
            session = session,
            holding = state.humanHolds,
            live = phase == ComputerBridgeClient.Phase.LIVE,
            typed = typed,
            onTyped = { typed = it },
        )
    }

    if (showMenu) {
        OnyxMenu(
            items = buildList {
                // Chaque entrée referme le menu: sans cela il reste ouvert sous la question qu'il
                // vient d'ouvrir, et l'utilisateur répond à travers lui.
                if (state.humanHolds) {
                    add(MenuItem("Hand back to the agent", Symbols.HandRaised) {
                        showMenu = false
                        session.release()
                    })
                } else {
                    add(MenuItem("Take over", Symbols.HandRaised) {
                        showMenu = false
                        session.take()
                    })
                }
                add(MenuItem("Run a command…", Symbols.Terminal) {
                    showMenu = false
                    commandDraft = ""
                    askingCommand = true
                })
                add(MenuItem("Keyboard shortcut…", Symbols.Keyboard) {
                    showMenu = false
                    shortcutDraft = ""
                    askingShortcut = true
                })
                add(MenuItem("Bridge address", Symbols.DesktopComputer) {
                    showMenu = false
                    editing = true
                })
                add(MenuItem("Close the session", Symbols.Xmark, destructive = true) {
                    showMenu = false
                    session.stop()
                    editing = true
                })
            },
            onDismiss = { showMenu = false },
        )
    }

    if (askingCommand) {
        OnyxPrompt(
            title = "Run a command",
            message = "It leaves for the PC only after you approve it.",
            placeholder = "uname -a",
            value = commandDraft,
            onValueChange = { commandDraft = it },
            confirmLabel = "Continue",
            onConfirm = {
                session.run(commandDraft)
                askingCommand = false
            },
            onDismiss = { askingCommand = false },
            confirmEnabled = commandDraft.isNotBlank(),
        )
    }

    if (askingShortcut) {
        OnyxPrompt(
            title = "Keyboard shortcut",
            message = "For example: ctrl+c, alt+F4, Return, Escape.",
            placeholder = "ctrl+c",
            value = shortcutDraft,
            onValueChange = { shortcutDraft = it },
            confirmLabel = "Press",
            onConfirm = {
                session.key(shortcutDraft)
                askingShortcut = false
            },
            onDismiss = { askingShortcut = false },
            confirmEnabled = shortcutDraft.isNotBlank(),
        )
    }

    // L'approbation d'une action sensible: deux réponses, aucune sortie par le dehors.
    approval?.let { pending ->
        OnyxAlert(
            title = if (pending.action == ComputerInput.RUN) "Run this command?" else "Press this shortcut?",
            message = pending.detail,
            actions = listOf(
                AlertAction("Refuse") { session.refuse() },
                AlertAction("Approve", destructive = true) { session.approve() },
            ),
            onDismiss = null,
            localizeMessage = false,
        )
    }
}

/** L'aperçu: l'image du PC, et les gestes qui deviennent des clics et des glissers. */
@Composable
private fun ComputerPreview(
    bitmap: Bitmap,
    screenWidth: Int,
    screenHeight: Int,
    onPoint: (ComputerPoint) -> Unit,
    onDrag: (ComputerPoint, ComputerPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slop = ComputerGestures.slopPx(density.density)

    BoxWithConstraints(modifier = modifier) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val fitted = ComputerScale.fit(boxWidth, boxHeight, bitmap.width, bitmap.height)

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = OnyxLocalization["The computer screen"],
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(fitted, bitmap.width, bitmap.height, screenWidth, screenHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val pressedAt = System.currentTimeMillis()
                        var last = down.position
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = change.position
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                        if (!released) return@awaitEachGesture
                        val gesture = ComputerGestures.classify(
                            dx = last.x - down.position.x,
                            dy = last.y - down.position.y,
                            durationMs = System.currentTimeMillis() - pressedAt,
                            slopPx = slop,
                        )
                        val point = ComputerScale.toScreen(
                            touchX = last.x,
                            touchY = last.y,
                            fit = fitted,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                        )
                        when (gesture) {
                            com.onyx.app.data.net.ComputerGesture.TAP -> point?.let(onPoint)
                            com.onyx.app.data.net.ComputerGesture.DRAG -> {
                                val from = ComputerScale.toScreen(
                                    touchX = down.position.x,
                                    touchY = down.position.y,
                                    fit = fitted,
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height,
                                    screenWidth = screenWidth,
                                    screenHeight = screenHeight,
                                )
                                if (from != null && point != null) onDrag(from, point)
                            }
                            com.onyx.app.data.net.ComputerGesture.IGNORED -> Unit
                        }
                    }
                },
        )
    }
}

/** L'attente: la connexion, la panne, et la façon d'en sortir. */
@Composable
private fun ComputerWaiting(
    phase: ComputerBridgeClient.Phase,
    notice: String?,
    onRetry: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (phase == ComputerBridgeClient.Phase.CONNECTING) {
            OnyxProgressView()
        } else {
            OnyxIcon(Symbols.DesktopComputer, tint = theme.tertiaryText, size = 30.dp)
        }
        BasicText(
            text = notice ?: if (phase == ComputerBridgeClient.Phase.CONNECTING) {
                "Connecting to the computer…"
            } else {
                "The computer's screen isn't coming through."
            },
            localize = notice == null,
            style = theme.footnote.copy(color = theme.secondaryText),
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 320.dp),
        )
        BasicText(
            text = "Try again",
            style = theme.body.copy(color = theme.accent),
            modifier = Modifier.padding(top = 14.dp).onyxClickable(onClick = onRetry),
        )
    }
}

/** Le réglage du pont: son adresse, et son jeton — jamais montré, mais copiable. */
@Composable
private fun ComputerSetup(
    address: String,
    onAddress: (String) -> Unit,
    token: String,
    onToken: (String) -> Unit,
    hasStoredToken: Boolean,
    keystoreRefused: Boolean,
    onCopyToken: () -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OnyxFormSection(
            header = "Computer bridge",
            footer = "A small service on the PC hands out the screen and takes the input. " +
                "It listens on that machine only. The token is a password for the PC: it stays " +
                "in this phone's keystore and is never shown in full.",
        ) {
            OnyxRow {
                BasicText(text = "Address", style = theme.body, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1.4f)) {
                    OnyxTextField(
                        value = address,
                        onValueChange = onAddress,
                        placeholder = "http://10.0.2.2:8795",
                        alignment = TextAlign.End,
                        keyboardType = KeyboardType.Uri,
                    )
                }
            }
            OnyxRowDivider()
            OnyxRow {
                BasicText(text = "Token", style = theme.body, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1.4f)) {
                    OnyxTextField(
                        value = token,
                        onValueChange = onToken,
                        placeholder = if (hasStoredToken) "••••••••" else "token",
                        alignment = TextAlign.End,
                        secret = true,
                        keyboardType = KeyboardType.Password,
                    )
                }
            }
            if (keystoreRefused) {
                OnyxRowDivider()
                OnyxRow {
                    BasicText(
                        text = "This phone's keystore wouldn't keep the token: it lasts for this session only.",
                        style = theme.footnote.copy(color = theme.tertiaryText),
                    )
                }
            }
            if (hasStoredToken) {
                OnyxRowDivider()
                OnyxRow(onClick = onCopyToken) {
                    OnyxIcon(Symbols.Key, tint = theme.tertiaryText, size = 16.dp)
                    BasicText(
                        text = "Copy the saved token",
                        style = theme.body.copy(color = theme.accent),
                    )
                }
            }
            OnyxRowDivider()
            OnyxRow(onClick = onSave) {
                BasicText(text = "Connect", style = theme.body.copy(color = theme.accent))
            }
            if (onCancel != null) {
                OnyxRowDivider()
                OnyxRow(onClick = onCancel) {
                    BasicText(text = "Cancel", style = theme.body)
                }
            }
        }
    }
}

/** Sous l'aperçu: qui a la main, ce qui vient de s'y passer. */
@Composable
private fun ComputerStatusLine(state: ComputerState, hello: ComputerHello?) {
    val theme = LocalOnyxTheme.current
    val action = state.last.text()
    val detail = buildString {
        append(OnyxLocalization[if (state.humanHolds) "You have the hand." else "The agent has the hand."])
        action?.let {
            append(" · ")
            append(OnyxLocalization[it.verb])
            append(" ")
            append(it.detail)
        }
        if (state.busy) {
            append(" · ")
            append(OnyxLocalization["Working…"])
        }
        hello?.let {
            append(" · ")
            append("${it.screenWidth}×${it.screenHeight}")
        }
    }
    BasicText(
        text = detail,
        localize = false,
        style = theme.footnote.copy(color = theme.secondaryText),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Une note passagère: un refus, une panne, une phrase du pont. */
@Composable
private fun ComputerNote(text: String, tone: Color, onDismiss: (() -> Unit)?) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(theme.fill, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            localize = false,
            style = theme.footnote.copy(color = tone),
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            OnyxIcon(
                Symbols.Xmark,
                tint = theme.tertiaryText,
                size = 13.dp,
                modifier = Modifier.onyxClickable(onClick = onDismiss),
            )
        }
    }
}

/** Ce que la dernière commande approuvée a rendu. */
@Composable
private fun ComputerOutput(output: String) {
    val theme = LocalOnyxTheme.current
    if (output.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(max = 120.dp)
            .background(theme.formSurfaceColor, RoundedCornerShape(9.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = output.trim(),
            localize = false,
            style = theme.footnote.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
        )
    }
}

/** La barre du bas: la main, les touches qui servent, et le clavier du téléphone. */
@Composable
private fun ComputerControls(
    session: ComputerSession,
    holding: Boolean,
    live: Boolean,
    typed: String,
    onTyped: (String) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val send = {
        if (typed.isNotEmpty()) {
            session.typeText(typed)
            onTyped("")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComputerBarButton(
                label = if (holding) "Hand back" else "Take over",
                tint = theme.accent,
                filled = true,
                onClick = { if (holding) session.release() else session.take() },
            )
            ComputerBarButton(label = "Return", tint = theme.accent, enabled = holding && live) {
                session.key("Return")
            }
            ComputerBarButton(label = "Escape", tint = theme.accent, enabled = holding && live) {
                session.key("Escape")
            }
            ComputerBarButton(label = "Up", tint = theme.accent, enabled = holding && live) {
                session.scroll(ComputerPoint(0, 0), -3)
            }
            ComputerBarButton(label = "Down", tint = theme.accent, enabled = holding && live) {
                session.scroll(ComputerPoint(0, 0), 3)
            }
        }

        if (holding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OnyxTextField(
                        value = typed,
                        onValueChange = onTyped,
                        placeholder = "Type on the computer…",
                        imeAction = ImeAction.Send,
                        onSend = send,
                    )
                }
                OnyxIcon(
                    Symbols.ArrowUpCircle,
                    tint = if (typed.isNotEmpty()) theme.accent else theme.tertiaryText,
                    size = 22.dp,
                    contentDescription = "Type it on the computer",
                    modifier = Modifier.padding(start = 10.dp).onyxClickable(onClick = send),
                )
            }
        } else {
            BasicText(
                text = "Take the hand to type and to click.",
                style = theme.footnote.copy(color = theme.tertiaryText),
            )
        }
    }
}

/** Un bouton de la barre, dessiné à la main — rien de Material dans cet écran. */
@Composable
private fun ComputerBarButton(
    label: String,
    tint: Color,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .background(if (filled) tint.copy(alpha = 0.16f) else theme.fill, RoundedCornerShape(9.dp))
            .then(if (enabled) Modifier.onyxClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        BasicText(
            text = label,
            style = theme.footnote.copy(color = if (enabled) tint else theme.tertiaryText),
            maxLines = 1,
        )
    }
}
