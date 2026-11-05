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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.onyx.app.ui.design.OnyxLargePageHeader
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.localization.BasicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Le navigateur de l'agent, en direct sur le téléphone, et sa prise en main.
 *
 * Trois idées tiennent cet écran :
 *
 * 1. **Les trames vivent ici, pas dans le fil.** Une image à 3 img/s qui passerait par l'état du
 *    chat recomposerait tout le fil — c'est le défaut qu'on a mis trois versions à corriger. Le
 *    `Bitmap` est décodé **hors du thread principal**, un seul à la fois ;
 * 2. **Une saisie porte le numéro de la trame sur laquelle elle a été calculée** (`seq`) : le
 *    serveur refuse ce qui est périmé, donc un tap ne peut pas atterrir sur un champ qui a bougé ;
 * 3. **Le mot de passe ne va jamais au modèle**, et l'enregistrement dans le coffre n'arrive
 *    qu'après un geste explicite — jamais tout seul (`VaultApi.put`, origine liée).
 */
class BrowserScreenViewModel(
    application: Application,
    private val server: ServerConfig,
) : ViewModel() {

    enum class Phase { IDLE, CONNECTING, WATCHING, DRIVING, UNAVAILABLE, FAILED }

    /** L'offre d'enregistrement, proposée **après** la main rendue : nom, origine, empreinte. */
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

    private val client = BrowserScreenClient(
        server = server,
        onFrame = ::onFrame,
        onLease = { lease -> _state.value = _state.value.copy(lease = lease) },
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
        },
    )

    /** Le dernier texte tapé pendant la conduite — **le seul** endroit où un mot de passe existe. */
    private var typed = StringBuilder()

    fun start() {
        if (_state.value.phase != Phase.IDLE && _state.value.phase != Phase.FAILED) return
        _state.value = _state.value.copy(phase = Phase.CONNECTING, problem = null, refusal = null)
        viewModelScope.launch {
            val tickets = runCatching { BrowserScreenApi.observe(server) }.getOrNull()
            if (tickets == null) {
                _state.value = _state.value.copy(
                    phase = Phase.UNAVAILABLE,
                    problem = OnyxLocalization["The browser plugin isn't answering on this server."],
                )
                return@launch
            }
            if (!client.connect(tickets.value)) {
                _state.value = _state.value.copy(phase = Phase.FAILED)
                return@launch
            }
            client.watch(on = true, takeover = false)
            _state.value = _state.value.copy(phase = Phase.WATCHING)
        }
    }

    fun takeOver() {
        viewModelScope.launch {
            val lease = runCatching { BrowserScreenApi.acquire(server) }.getOrNull()
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
        val host = hostOf(_state.value.url)
        val hadPassword = typed.isNotEmpty()
        client.handBack(id = null, note = note)
        client.watch(on = true, takeover = false)
        _state.value = _state.value.copy(phase = Phase.WATCHING, lease = null)
        if (hadPassword && host.isNotBlank()) {
            // **L'offre, pas l'écriture** : le nom vient de l'hôte, et rien ne part avant le geste.
            _state.value = _state.value.copy(
                offer = SaveOffer(
                    name = host.removePrefix("www.").substringBefore('.').ifBlank { host },
                    host = host,
                    fingerprint = fingerprintOf(typed.toString()),
                ),
            )
        }
    }

    /** Le geste explicite d'enregistrement : `vault.put`, avec l'origine, sans jamais la lire. */
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
        /** L'hôte d'une URL, sans `www.` — ce qui ira dans `used_by`. */
        fun hostOf(url: String): String = runCatching {
            java.net.URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")

        /** Les 4 derniers caractères, ou `****` : la même règle que le coffre. */
        fun fingerprintOf(value: String): String =
            if (value.length < 8) "****" else value.takeLast(4)
    }
}

/** Une ligne de texte : le `BasicText` de la maison, qui localise et prend sa couleur. */
@Composable
private fun Txt(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = TextStyle(color = color, fontSize = 15.sp))
}

/** L'écran : l'image, la barre d'adresse, le bail, les commandes. */
@Composable
fun BrowserScreen(
    viewModel: BrowserScreenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            .imePadding()
            .padding(OnyxSpace.Base),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        OnyxLargePageHeader(
            title = if (state.lease?.human == true) {
                OnyxLocalization["You're driving"]
            } else {
                OnyxLocalization["The agent's browser"]
            },
            onBack = { viewModel.stop(); onBack() },
        )
        Txt(
            text = state.title.ifBlank { BrowserScreenViewModel.hostOf(state.url) },
            color = theme.textColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = OnyxSpace.Half),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(state.aspect.coerceIn(0.4f, 2.5f))
                .clip(RoundedCornerShape(OnyxSpace.PanelCorner))
                .background(Color.Black),
        ) {
            val image = state.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = OnyxLocalization["The agent's browser"],
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (state.phase != BrowserScreenViewModel.Phase.DRIVING) Modifier
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
                Txt(
                    text = OnyxLocalization["Waiting for the agent's browser…"],
                    color = theme.textColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        state.problem?.let { problem ->
            Txt(problem, color = theme.textColor)
        }
        state.refusal?.let { refusal ->
            Txt(refusal, color = theme.textColor)
        }
        if (state.stored) {
            Txt(
                OnyxLocalization["Saved to the vault."],
                color = theme.accent,
            )
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
                        color = theme.textColor,
                    )
                    Txt(
                        OnyxLocalization.format("It will only be reused on %1\$s.", offer.host),
                        color = theme.textColor.copy(alpha = 0.6f),
                    )
                }
                Txt(
                    OnyxLocalization["Save"],
                    color = theme.accent,
                    modifier = Modifier.clickable { viewModel.storeInVault() }.padding(8.dp),
                )
                Txt(
                    OnyxLocalization["Not now"],
                    color = theme.textColor.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { viewModel.dismissOffer() }.padding(8.dp),
                )
            }
        }

        when (state.phase) {
            BrowserScreenViewModel.Phase.IDLE,
            BrowserScreenViewModel.Phase.UNAVAILABLE,
            BrowserScreenViewModel.Phase.FAILED -> {
                Txt(
                    OnyxLocalization["Show the browser"],
                    color = theme.accent,
                    modifier = Modifier.clickable { viewModel.start() }.padding(8.dp),
                )
            }
            BrowserScreenViewModel.Phase.CONNECTING -> {
                Txt(OnyxLocalization["Connecting…"], color = theme.textColor.copy(alpha = 0.6f))
            }
            BrowserScreenViewModel.Phase.WATCHING -> {
                Txt(
                    OnyxLocalization["Take over"],
                    color = theme.accent,
                    modifier = Modifier.clickable { viewModel.takeOver() }.padding(8.dp),
                )
            }
            BrowserScreenViewModel.Phase.DRIVING -> {
                // **Champ masqué, clavier mot de passe** : ce que Dots laisse en clair.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(OnyxSpace.ControlCorner))
                            .background(theme.tertiarySurfaceColor)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = theme.textColor,
                                fontSize = 15.sp,
                            ),
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
                    Spacer(Modifier.width(8.dp))
                    Txt(
                        OnyxLocalization["Tab"],
                        color = theme.textColor,
                        modifier = Modifier
                            .clickable { viewModel.key("Tab") }
                            .padding(8.dp),
                    )
                    Txt(
                        OnyxLocalization["Hand back"],
                        color = theme.accent,
                        modifier = Modifier
                            .clickable { viewModel.handBack() }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
