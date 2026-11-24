package com.onyx.app.ui.browser

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.BrowserFrame
import com.onyx.app.data.net.BrowserScreenApi
import com.onyx.app.data.net.BrowserScreenClient
import com.onyx.app.data.net.ScreenInput
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.VaultApi
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import com.onyx.app.ui.design.OnyxLargePageHeader
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.localization.BasicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight

/**
 * The agent's browser, live on the phone, and taking it over.
 *
 * Three ideas hold this screen together:
 *
 * 1. **Frames live here, not in the thread.** An image at 3 fps that went through the chat state
 *    would recompose the whole thread — that is the flaw it took us three versions to fix. The
 *    `Bitmap` is decoded **off the main thread**, one at a time;
 * 2. **An input carries the number of the frame it was computed on** (`seq`): the
 *    server refuses what is stale, so a tap cannot land on a field that has moved;
 * 3. **The password never goes to the model**, and saving to the vault only happens
 *    after an explicit gesture — never on its own (`VaultApi.put`, bound origin).
 */
class BrowserScreenViewModel(
    application: Application,
    private val server: ServerConfig,
) : ViewModel() {

    enum class Phase { IDLE, CONNECTING, WATCHING, DRIVING, UNAVAILABLE, FAILED }

    /** The save offer, proposed **after** control is handed back: name, origin, fingerprint. */
    data class SaveOffer(val name: String, val host: String, val fingerprint: String)

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val image: ImageBitmap? = null,
        val aspect: Float = 0.5625f,
        val url: String = "",
        val title: String = "",
        val lease: BrowserScreenApi.Lease? = null,
        val seq: Int = 0,
        val cssWidth: Int = 0,
        val cssHeight: Int = 0,
        val problem: String? = null,
        val refusal: String? = null,
        val offer: SaveOffer? = null,
        val stored: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * The stream client, and its declaration **before** the client.
     *
     * The `onPageElsewhere` callback talks to the client — but it is passed **to** the client's
     * constructor. Capturing the field in there gives the type checker a loop
     * (`client` depends on `client`), and the compiler stops on "recursive problem".
     * The field is therefore built by a function, and assigned right after: the callback only
     * happens once the client is built, and it reads the field at that moment.
     */
    private val client: BrowserScreenClient = buildClient()

    /** The area where the app draws the page, in **dp** (hence in CSS px) and its density. */
    private var zone: Triple<Int, Int, Float>? = null
    private var viewportJob: Job? = null

    /**
     * **The page takes the shape of the area that displays it.**
     *
     * A fixed template (390×844) made the page tiny in the middle of a large panel: on an
     * unfolded foldable, it occupied a band surrounded by black. And folding or unfolding changes the
     * width without changing pages — hence the shape is re-sent when it moves, with a short
     * delay: a fold sends several measurements in a row.
     */
    fun screenSized(widthPx: Int, heightPx: Int, density: Float) {
        if (density <= 0f) return
        val largeur = (widthPx / density).toInt()
        val hauteur = (heightPx / density).toInt()
        if (largeur < 200 || hauteur < 200) return
        val avant = zone
        zone = Triple(largeur, hauteur, density)
        if (avant == null || (avant.first == largeur && avant.second == hauteur)) return
        if (_state.value.phase != Phase.DRIVING) return
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(250)
            BrowserScreenApi.viewport(server, largeur, hauteur, density)
        }
    }

    private fun buildClient() = BrowserScreenClient(
        server = server,
        onFrame = ::onFrame,
        onLease = { lease -> _state.value = _state.value.copy(lease = lease) },
        /**
         * **The page is in another process.** The server says so, then closes the socket.
         *
         * The app must handle it: otherwise it waits for a stream that will never come, on a
         * process that has nothing to show. We ask for a ticket again — the relay will then
         * send the screen back to the process that holds the page.
         */
        onPageElsewhere = {
            client.close()
            _state.value = _state.value.copy(
                phase = Phase.FAILED,
                problem = OnyxLocalization["The page moved to another process. Reconnecting…"],
            )
            scheduleRetry()
        },
        onRefused = { seq, reason ->
            _state.value = _state.value.copy(
                refusal = OnyxLocalization[
                    when (reason) {
                        "no_lease" -> "Take over first, then act on the page."
                        "stale" -> "The page moved: try again."
                        else -> "The page refused that."
                    }
                ],
            )
        },
        onClosed = { reason ->
            _state.value = _state.value.copy(
                phase = Phase.FAILED,
                problem = reason?.let { OnyxLocalization.format("The browser stopped: %1\$s", it) },
            )
            // **A disconnection repairs itself.** The ticket is single-use, so we ask for a new
            // one and reopen: without that the screen stayed frozen on "Waiting…" after the slightest
            // gateway restart, even though the page itself was still alive.
            scheduleRetry()
        },
        // **The end is stated, otherwise the screen lies.** The server hands back control on its own when the page
        // dies (Hermes closes an inactive browser after two minutes) or after fifteen
        // minutes. Without this message, the screen stayed on the last image, marked "waiting".
        onEnded = { lost, _ ->
            val phase = if (_state.value.phase == Phase.DRIVING) Phase.WATCHING else _state.value.phase
            _state.value = _state.value.copy(
                phase = phase,
                lease = null,
                problem = if (lost) {
                    OnyxLocalization["The page closed during the takeover. Ask the agent to open it again."]
                } else {
                    _state.value.problem
                },
            )
        },
        onIdle = {
            val current = _state.value
            if (current.image == null && current.problem == null) {
                _state.value = current.copy(
                    problem = OnyxLocalization["No page is open. Ask the agent to open one."],
                )
            }
        },
        // **The stream is alive.** Any message received — frame, `idle`, lease — counts. That is what
        // the watchdog measures: without it, it only saw frames and never triggered
        // on a screen that has no page to show.
        onAlive = {
            lastAliveAt = System.currentTimeMillis()
        },
    )

    /** The last text typed while driving — **the only** place where a password exists. */
    private var typed = StringBuilder()

    /**
     * Reconnection, and why it exists.
     *
     * The socket dies for reasons that have nothing to do with an error: the screen going to the
     * background, a network change, a gateway restart. The ticket is single-use, so
     * a new ticket must be **requested** — a simple socket reconnection would fail. Without this
     * mechanism the screen stayed on "Waiting for the agent's browser…" indefinitely, which
     * reads as a server failure even though the page was alive.
     */
    private var retryJob: Job? = null
    private var attempts = 0
    /** The person was driving: the reconnection must take the lease back, not just watch. */
    private var wantDriving = false
    /**
     * The stream watchdog, and what it catches.
     *
     * **A socket can die without saying why.** The relay is a proxy: if the connection
     * drops between the phone and the machine, the client socket can stay "open" on the
     * application side — no error, no frame, and the screen stays on "Waiting…" for
     * ever. This is exactly what we saw: the server served perfect frames (verified
     * by an independent client) while the app received nothing and asked for nothing.
     *
     * The guard counts the time elapsed since the last frame: beyond a few seconds,
     * the stream is dead even if the socket does not say so. We then close it **cleanly** — a
     * `close()` leaves the socket in a state the phone can reuse, and the recovery
     * requests a fresh ticket, because the old one is single-use.
     */
    private var watchdogJob: Job? = null
    private var lastFrameAt: Long = 0L

    /**
     * The last **activity** of the stream — any message received.
     *
     * It is distinct from [lastFrameAt]: a frame proves that a page is displayed, but an
     * `idle` also proves that the stream is alive. Counting frames alone made the watchdog
     * blind: a screen that only receives `idle` messages (no page open) never
     * triggered the guard, and a socket dead in silence did not trigger it either.
     */
    private var lastAliveAt: Long = 0L

    /** The delay without a frame beyond which the stream is considered dead. */
    private val silenceMs = 12_000L

    fun start() {
        attempts = 0
        wantDriving = false
        connectNow()
    }

    /**
     * The watchdog: a stream that no longer talks is a dead stream.
     *
     * We do not trust the socket: it can stay open without delivering anything (the relay is a
     * proxy, and a drop along the way does not surface as an error). The only reliable sign
     * is silence. Beyond a few seconds without a frame, we close and
     * request a ticket again — that is what was missing for the screen to recover on its own.
     */
    private fun armWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (true) {
                delay(2_000)
                val phase = _state.value.phase
                if (phase != Phase.WATCHING && phase != Phase.DRIVING) continue
                // **Silence is measured on activity, not on frames.** A repeated `idle`
                // is proof of life: it is what distinguishes "the stream is running but there is
                // no page" from "the stream is dead". Before, a frame was missing and the guard
                // never triggered — the screen stayed frozen on "Waiting…".
                val last = lastAliveAt
                if (last == 0L) continue
                if (System.currentTimeMillis() - last < silenceMs) continue
                // **The stream is dead.** We close it for good, and start over: the
                // ticket is single-use, the recovery asks for a new one.
                client.close()
                _state.value = _state.value.copy(
                    phase = Phase.FAILED,
                    problem = OnyxLocalization["The browser stopped sending images. Reconnecting…"],
                )
                scheduleRetry()
                return@launch
            }
        }
    }

    /** The explicit "Retry" gesture: starts over, without waiting for the next delay. */
    fun retry() {
        retryJob?.cancel()
        retryJob = null
        attempts = 0
        connectNow()
    }

    private fun connectNow() {
        if (_state.value.phase == Phase.CONNECTING) return
        _state.value = _state.value.copy(phase = Phase.CONNECTING, problem = null, refusal = null)
        viewModelScope.launch {
            val tickets = runCatching { BrowserScreenApi.observe(server) }.getOrNull()
            if (tickets == null) {
                _state.value = _state.value.copy(
                    phase = Phase.UNAVAILABLE,
                    problem = OnyxLocalization["The browser plugin isn't answering on this server."],
                )
                scheduleRetry()
                return@launch
            }
            if (!client.connect(tickets.value)) {
                _state.value = _state.value.copy(phase = Phase.FAILED)
                scheduleRetry()
                return@launch
            }
            client.watch(on = true, takeover = false)
            _state.value = _state.value.copy(phase = Phase.WATCHING)
            // **The watchdog starts with the stream.** Without it, a socket that dies in
            // silence leaves the screen frozen on "Waiting…" forever.
            //
            // We **arm the measurement** at the same instant: activity starts over from zero on each
            // new connection, otherwise the guard would compare the time of a previous stream and
            // conclude too soon — or, if the clock had never been armed, never conclude
            // (`lastAliveAt == 0`).
            lastAliveAt = System.currentTimeMillis()
            armWatchdog()
            if (wantDriving) {
                // The lease belongs to the socket: a new connection must request it again, otherwise
                // the screen would show "you're driving" with gestures refused as `no_lease`.
                takeOver()
            }
        }
    }

    /**
     * Retries with a delay that doubles (1, 2, 4, 8, 16 s), then stops and hands back control.
     *
     * Six attempts cover a gateway restart; beyond that, insisting every sixteen
     * seconds on a server that is off drains the battery for nothing — the screen then offers
     * "Retry".
     */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        if (attempts >= MAX_RETRIES) {
            _state.value = _state.value.copy(
                problem = OnyxLocalization["The browser is unreachable. Try again."],
            )
            return
        }
        val pause = 1_000L shl attempts.coerceAtMost(4)
        attempts += 1
        retryJob = viewModelScope.launch {
            delay(pause)
            if (_state.value.phase == Phase.FAILED || _state.value.phase == Phase.UNAVAILABLE) {
                connectNow()
            }
        }
    }

    fun takeOver() {
        wantDriving = true
        viewModelScope.launch {
            val forme = zone
            val lease = runCatching {
                BrowserScreenApi.acquire(server, forme?.first, forme?.second, forme?.third)
            }.getOrNull()
            if (lease == null) {
                _state.value = _state.value.copy(
                    problem = OnyxLocalization["Another screen is driving this browser."],
                )
                return@launch
            }
            typed = StringBuilder()
            client.watch(on = true, takeover = true)
            _state.value = _state.value.copy(phase = Phase.DRIVING, lease = lease, refusal = null)
        }
    }

    fun handBack(note: String = "") {
        wantDriving = false
        val host = hostOf(_state.value.url)
        val hadPassword = typed.isNotEmpty()
        client.handBack(id = null, note = note)
        client.watch(on = true, takeover = false)
        _state.value = _state.value.copy(phase = Phase.WATCHING, lease = null)
        if (hadPassword && host.isNotBlank()) {
            // **The offer, not the write**: the name comes from the host, and nothing is sent before the gesture.
            _state.value = _state.value.copy(
                offer = SaveOffer(
                    name = host.removePrefix("www.").substringBefore('.').ifBlank { host },
                    host = host,
                    fingerprint = fingerprintOf(typed.toString()),
                ),
            )
        }
    }

    /** The explicit save gesture: `vault.put`, with the origin, without ever reading it. */
    fun storeInVault() {
        val offer = _state.value.offer ?: return
        val value = typed.toString()
        if (value.isEmpty()) return
        viewModelScope.launch {
            val written = runCatching {
                VaultApi(ServeClient(server)).put(
                    name = offer.name,
                    value = value,
                    usedBy = listOf(offer.host),
                )
            }.isSuccess
            typed = StringBuilder()
            _state.value = _state.value.copy(
                offer = null,
                stored = written,
                problem = if (written) null else OnyxLocalization["The vault refused the write."],
            )
        }
    }

    fun dismissOffer() {
        typed = StringBuilder()
        _state.value = _state.value.copy(offer = null)
    }

    fun tap(x: Float, y: Float) = send(ScreenInput.Tap(x, y))

    fun scroll(x: Float, y: Float, dy: Float) = send(ScreenInput.Scroll(x, y, dy))

    fun type(text: String) {
        if (text.isEmpty()) return
        send(ScreenInput.Type(text))
    }

    fun key(name: String) = send(ScreenInput.Key(name))

    fun stop() {
        retryJob?.cancel()
        retryJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        attempts = 0
        wantDriving = false
        client.watch(on = false, takeover = false)
        client.close()
        typed = StringBuilder()
        _state.value = _state.value.copy(phase = Phase.IDLE, image = null, offer = null)
    }

    private fun send(input: ScreenInput) {
        val current = _state.value
        if (current.phase != Phase.DRIVING) {
            _state.value = current.copy(refusal = OnyxLocalization["Take over first, then act on the page."])
            return
        }
        if (input is ScreenInput.Type) typed.append(input.text.take(2000))
        client.send(input, current.seq, css = current.cssWidth to current.cssHeight)
    }

    private fun onFrame(frame: BrowserFrame) {
        lastFrameAt = System.currentTimeMillis()
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.Default) { decode(frame.data) }
            _state.value = _state.value.copy(
                image = decoded ?: _state.value.image,
                aspect = if (frame.height > 0) frame.width / frame.height.toFloat() else _state.value.aspect,
                url = frame.url.ifBlank { _state.value.url },
                title = frame.title,
                seq = frame.seq,
                cssWidth = if (frame.cssWidth > 0) frame.cssWidth else _state.value.cssWidth,
                cssHeight = if (frame.cssHeight > 0) frame.cssHeight else _state.value.cssHeight,
                phase = if (_state.value.lease?.human == true) Phase.DRIVING else Phase.WATCHING,
                problem = null,
            )
        }
    }

    private fun decode(base64: String): ImageBitmap? = runCatching {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    override fun onCleared() {
        stop()
    }

    companion object {
        /** How many automatic reconnections before handing control back to the person. */
        const val MAX_RETRIES = 6

        /** The host of a URL, without `www.` — what will go into `used_by`. */
        fun hostOf(url: String): String = runCatching {
            java.net.URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")

        /** The last 4 characters, or `****`: the same rule as the vault. */
        fun fingerprintOf(value: String): String =
            if (value.length < 8) "****" else value.takeLast(4)
    }
}

/** The screen, in the reference's shape: header, address, page, then the controls. */
@Composable
fun BrowserScreen(
    viewModel: BrowserScreenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    // **The screen connects when it opens.** You had to tap "Show the browser" to
    // start the stream; the screen therefore opened on a black frame and one thought it was a failure.
    LaunchedEffect(Unit) { viewModel.start() }

    val theme = LocalOnyxTheme.current
    val driving = state.lease?.human == true || state.phase == BrowserScreenViewModel.Phase.DRIVING
    // **The palette tokens, not invented colors.** The screen is a viewer: it
    // takes the base's dark page and its text, like the other full-screen scenes. A color
    // hardcoded here would be painted without going through the palette — the reference lock refuses it,
    // rightly so.
    val night = ReferencePalette.DarkPage
    val ink = ReferencePalette.DarkText

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(night)
            // **The system bars, like everywhere else.** Without them, the screen's header
            // was drawn **under** the status bar: "Bouygues", the time and the icons passed
            // over the title and the "YOU'RE DRIVING" badge, and both were read
            // together. No other full-page screen makes this mistake: this one lacked it.
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        // -- the header: close, who is driving, the state -----------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { viewModel.stop(); onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Txt("✕", ink, size = 15)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(
                    text = if (driving) {
                        OnyxLocalization["You're in control"]
                    } else {
                        OnyxLocalization["The agent's browser"]
                    },
                    color = ink,
                    size = 16,
                    weight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val sous = state.title.ifBlank { BrowserScreenViewModel.hostOf(state.url) }
                if (sous.isNotBlank()) {
                    Txt(
                        text = sous,
                        color = ink.copy(alpha = 0.55f),
                        size = 12,
                        maxLines = 1,
                    )
                }
            }
            if (driving) Etiquette(OnyxLocalization["YOU'RE DRIVING"])
        }

        // -- the address bar ---------------------------------------------------------------
        if (state.url.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Txt(if (state.url.startsWith("https")) "🔒" else "🌐", ink.copy(alpha = 0.6f), size = 12)
                Spacer(Modifier.width(8.dp))
                Txt(
                    text = state.url,
                    color = ink.copy(alpha = 0.85f),
                    size = 12,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // -- the page, centered: the black around it is letterboxing, not emptiness ---------
        val densite = LocalDensity.current.density
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // The area's measurement goes to the server: it is what gives the page its shape,
                // and re-sends it when a foldable opens or closes.
                .onSizeChanged { taille -> viewModel.screenSized(taille.width, taille.height, densite) },
            contentAlignment = Alignment.Center,
        ) {
            val image = state.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = OnyxLocalization["The agent's browser"],
                    contentScale = ContentScale.Fit,
                    // **A sharp capture can be rendered soft by the display.** Compose's default
                    // filter (`FilterQuality.Low`) does a simple bilinear smoothing:
                    // on a browser frame — fine text, 1 px strokes — it spreads
                    // the glyphs and the form becomes blurry, especially if the capture is a bit
                    // smaller than the screen. `Medium` keeps the outline sharp without jaggies, and
                    // it is the last step of the path: capture at screen resolution,
                    // full-size decoding, display without upscaling.
                    filterQuality = FilterQuality.Medium,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!driving) Modifier
                            else Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        viewModel.tap(
                                            offset.x / size.width.toFloat(),
                                            offset.y / size.height.toFloat(),
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    var carried = 0f
                                    detectVerticalDragGestures(
                                        onDragEnd = { carried = 0f },
                                        onVerticalDrag = { change, delta ->
                                            carried -= delta / size.height
                                            if (kotlin.math.abs(carried) > 0.04f) {
                                                viewModel.scroll(
                                                    change.position.x / size.width,
                                                    change.position.y / size.height,
                                                    carried,
                                                )
                                                carried = 0f
                                            }
                                        },
                                    )
                                },
                        ),
                )
            } else {
                // The state is stated: "Waiting" during a reconnection read as a failure.
                val waiting = when (state.phase) {
                    BrowserScreenViewModel.Phase.CONNECTING,
                    BrowserScreenViewModel.Phase.FAILED,
                    BrowserScreenViewModel.Phase.UNAVAILABLE,
                    -> OnyxLocalization["Reconnecting…"]
                    else -> OnyxLocalization["Waiting for the agent's browser…"]
                }
                Txt(waiting, ink.copy(alpha = 0.45f), size = 15)
            }
        }

        state.problem?.let { Txt(it, ink.copy(alpha = 0.8f), size = 12) }
        state.refusal?.let { Txt(it, ink.copy(alpha = 0.8f), size = 12) }
        if (state.stored) {
            Txt(OnyxLocalization["Saved to the vault."], theme.accent, size = 12)
        }

        state.offer?.let { offer ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(
                        OnyxLocalization.format(
                            "Save to the vault? %1\$s ·••••%2\$s",
                            offer.host,
                            offer.fingerprint,
                        ),
                        ink,
                        size = 12,
                    )
                    Txt(
                        OnyxLocalization.format("It will only be reused on %1\$s.", offer.host),
                        ink.copy(alpha = 0.55f),
                        size = 12,
                    )
                }
                Txt(
                    OnyxLocalization["Save"],
                    theme.accent,
                    size = 15,
                    modifier = Modifier.clickable { viewModel.storeInVault() }.padding(8.dp),
                )
                Txt(
                    OnyxLocalization["Not now"],
                    ink.copy(alpha = 0.6f),
                    size = 15,
                    modifier = Modifier.clickable { viewModel.dismissOffer() }.padding(8.dp),
                )
            }
        }

        // -- les commandes -----------------------------------------------------------------
        when (state.phase) {
            BrowserScreenViewModel.Phase.IDLE -> {
                BoutonPlein(OnyxLocalization["Show the browser"], theme.accent) { viewModel.start() }
            }
            BrowserScreenViewModel.Phase.UNAVAILABLE,
            BrowserScreenViewModel.Phase.FAILED -> {
                // **Reconnection is automatic; this gesture is the emergency exit** when the
                // six attempts are exhausted (server truly off, no network).
                BoutonPlein(OnyxLocalization["Retry"], theme.accent) { viewModel.retry() }
            }
            BrowserScreenViewModel.Phase.CONNECTING -> {
                Txt(OnyxLocalization["Connecting…"], ink.copy(alpha = 0.55f), size = 12)
            }
            BrowserScreenViewModel.Phase.WATCHING -> {
                BoutonPlein(OnyxLocalization["Take over"], theme.accent) { viewModel.takeOver() }
            }
            BrowserScreenViewModel.Phase.DRIVING -> {
                // **The reference's trio**: a hint, the field, then Tab / Esc / Hand back
                // the control. The hint is not decorative — without it, one cannot guess
                // that the text goes into the page and not into the conversation.
                Txt(
                    text = OnyxLocalization[
                        "Tap a field on the page, then type here: the text goes straight to its page, not into the conversation."
                    ],
                    color = ink.copy(alpha = 0.45f),
                    size = 11,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // **Masked field, password keyboard**: what the reference leaves in the clear.
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = TextStyle(color = ink, fontSize = 15.sp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Send,
                            ),
                            keyboardActions = KeyboardActions(onSend = {
                                viewModel.type(text)
                                text = ""
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Pastille("⌫") { viewModel.key("Backspace") }
                    Spacer(Modifier.width(6.dp))
                    // **Send without going through the keyboard**: the button does the same gesture as the
                    // "Send" key, useful when the keyboard is collapsed.
                    Pastille("⏎") {
                        viewModel.type(text)
                        text = ""
                    }
                }
                Spacer(Modifier.height(OnyxSpace.Half))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Pastille(OnyxLocalization["Tab"]) { viewModel.key("Tab") }
                    // **Escape closes the site's suggestions and dialogs** — without it, a page
                    // that opens a banner cannot be unblocked from the phone.
                    Pastille(OnyxLocalization["Esc"]) { viewModel.key("Escape") }
                    Spacer(Modifier.weight(1f))
                    BoutonPlein(OnyxLocalization["Hand back"], theme.accent, compact = true) {
                        viewModel.handBack()
                    }
                }
            }
        }
    }
}

/** A dark, clickable pill: the auxiliary keys (⌫, Tab, Esc). */
@Composable
private fun Pastille(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, ReferencePalette.DarkText, size = 12)
    }
}

/** The main button: a flat color fill, like "Hand back" in the reference. */
@Composable
private fun BoutonPlein(
    label: String,
    color: Color,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            // Two shapes, one single modifier: `fillMaxWidth(0f)` then `width(118.dp)` were
            // fighting each other, and the compact button could go off screen.
            .then(if (compact) Modifier.width(118.dp) else Modifier.fillMaxWidth())
            .height(if (compact) 40.dp else 46.dp)
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, ReferencePalette.DarkPage, size = 15, weight = FontWeight.SemiBold)
    }
}

/** The red "you're driving" label, with its dot. */
@Composable
private fun Etiquette(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
            .background(ReferencePalette.DarkSurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(ReferencePalette.Accent))
        Spacer(Modifier.width(6.dp))
        Txt(label, ReferencePalette.AccentTextDark, size = 11, weight = FontWeight.Medium, maxLines = 1)
    }
}

/** A line of text: the house's `BasicText`, which localizes and takes its color. */
@Composable
private fun Txt(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 15,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight),
    )
}