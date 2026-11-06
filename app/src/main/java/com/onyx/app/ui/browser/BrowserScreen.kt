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

    /**
     * Le client du flux, et sa déclaration **avant** lui.
     *
     * Le callback `onPageElsewhere` parle au client — mais il est passé **au** constructeur du
     * client. Capturer le champ là-dedans donne au vérificateur de types une boucle
     * (`client` dépend de `client`), et le compilateur s'arrête sur « recursive problem ».
     * Le champ est donc construit par une fonction, et affecté juste après : le callback ne
     * survient qu'une fois le client construit, et il lit le champ à ce moment-là.
     */
    private val client: BrowserScreenClient = buildClient()

    /** La zone où l'app dessine la page, en **dp** (donc en px CSS) et sa densité. */
    private var zone: Triple<Int, Int, Float>? = null
    private var viewportJob: Job? = null

    /**
     * **La page prend la forme de la zone qui l'affiche.**
     *
     * Un gabarit fixe (390×844) rendait la page minuscule au milieu d'une grande dalle : sur un
     * pliable déplié, elle occupait une bande cernée de noir. Et plier ou déplier change la
     * largeur sans changer de page — d'où le renvoi de la forme quand elle bouge, avec un court
     * délai : une bascule envoie plusieurs mesures d'affilée.
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
         * **La page est chez un autre processus.** Le serveur le dit, puis ferme le socket.
         *
         * L'app doit le traiter : sinon elle attend un flux qui ne viendra plus, sur un
         * processus qui n'a rien à montrer. On redemande un ticket — le relais, lui, renverra
         * alors l'écran vers le processus qui tient la page.
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
            // **Une coupure se répare seule.** Le ticket est à usage unique, donc on en redemande
            // un et on rouvre : sans ça l'écran restait figé sur « En attente… » après le moindre
            // redémarrage du gateway, alors que la page, elle, était toujours vivante.
            scheduleRetry()
        },
        // **La fin se dit, sinon l'écran ment.** Le serveur rend la main tout seul quand la page
        // meurt (Hermes ferme un navigateur inactif après deux minutes) ou au bout de quinze
        // minutes. Sans ce message, l'écran restait sur la dernière image, marqué « en attente ».
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
        // **Le flux est vivant.** Tout message reçu — trame, `idle`, bail — compte. C'est ce que
        // le chien de garde mesure : sans lui, il ne voyait que les trames et ne se déclenchait
        // jamais sur un écran qui n'a pas de page à montrer.
        onAlive = {
            lastAliveAt = System.currentTimeMillis()
        },
    )

    /** Le dernier texte tapé pendant la conduite — **le seul** endroit où un mot de passe existe. */
    private var typed = StringBuilder()

    /**
     * La reconnexion, et pourquoi elle existe.
     *
     * Le socket meurt pour des raisons qui n'ont rien à voir avec une erreur : mise en arrière-plan
     * de l'écran, changement de réseau, redémarrage du gateway. Le ticket est à usage unique, donc
     * il faut **redemander un ticket** — une simple reconnexion du socket échouerait. Sans ce
     * mécanisme l'écran restait sur « En attente du navigateur de l'agent… » indéfiniment, ce qui
     * se lit comme une panne du serveur alors que la page était vivante.
     */
    private var retryJob: Job? = null
    private var attempts = 0
    /** La personne conduisait : la reconnexion doit reprendre le bail, pas seulement regarder. */
    private var wantDriving = false
    /**
     * Le chien de garde du flux, et ce qu'il attrape.
     *
     * **Un socket peut mourir sans dire pourquoi.** Le relais est un proxy : si la connexion
     * tombe entre le téléphone et la machine, le socket client peut rester « ouvert » côté
     * application — aucune erreur, aucune trame, et l'écran reste sur « En attente… » pour
     * toujours. C'est exactement ce qu'on a vu : le serveur servait des trames parfaites (vérifié
     * par un client indépendant) pendant que l'app ne recevait rien et ne redemandait rien.
     *
     * Le garde compte le temps écoulé depuis la dernière trame : au-delà de quelques secondes,
     * le flux est mort même si le socket ne le dit pas. On referme alors **proprement** — un
     * `close()` laisse le socket dans un état que le téléphone peut réutiliser, et la reprise
     * redemande un ticket neuf, parce que l'ancien est à usage unique.
     */
    private var watchdogJob: Job? = null
    private var lastFrameAt: Long = 0L

    /**
     * La dernière **activité** du flux — n'importe quel message reçu.
     *
     * Elle est distincte de [lastFrameAt] : une trame prouve qu'une page s'affiche, mais un
     * `idle` prouve aussi que le flux est vivant. Compter les trames seules rendait le chien
     * de garde aveugle : un écran qui ne reçoit que des `idle` (aucune page ouverte) ne
     * déclenchait jamais la garde, et un socket mort en silence ne la déclenchait pas non plus.
     */
    private var lastAliveAt: Long = 0L

    /** Le délai sans trame au-delà duquel le flux est considéré comme mort. */
    private val silenceMs = 12_000L

    fun start() {
        attempts = 0
        wantDriving = false
        connectNow()
    }

    /**
     * Le chien de garde : un flux qui ne parle plus est un flux mort.
     *
     * On ne se fie pas au socket : il peut rester ouvert sans rien livrer (le relais est un
     * proxy, et une coupure en cours de route ne remonte pas comme une erreur). Le seul signe
     * fiable est le silence. Au-delà de quelques secondes sans trame, on referme et on
     * redemande un ticket — c'est ce qui manquait pour que l'écran se rattrape tout seul.
     */
    private fun armWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            while (true) {
                delay(2_000)
                val phase = _state.value.phase
                if (phase != Phase.WATCHING && phase != Phase.DRIVING) continue
                // **Le silence se mesure sur l'activité, pas sur les trames.** Un `idle` répété
                // est une preuve de vie : c'est lui qui distingue « le flux tourne mais il n'y a
                // pas de page » de « le flux est mort ». Avant, une trame manquait et la garde
                // ne se déclenchait jamais — l'écran restait figé sur « En attente… ».
                val last = lastAliveAt
                if (last == 0L) continue
                if (System.currentTimeMillis() - last < silenceMs) continue
                // **Le flux est mort.** On le ferme pour de bon, et on repart de zéro : le
                // ticket est à usage unique, la reprise en demande un neuf.
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

    /** Le geste explicite « Réessayer » : repart de zéro, sans attendre le prochain délai. */
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
            // **Le chien de garde démarre avec le flux.** Sans lui, un socket qui meurt en
            // silence laisse l'écran figé sur « En attente… » pour toujours.
            //
            // On **arme la mesure** au même instant : l'activité repart de zéro à chaque
            // nouvelle connexion, sinon le garde comparerait l'heure d'un flux précédent et
            // conclurait trop vite — ou, si l'horloge n'avait jamais été armée, ne conclurait
            // jamais (`lastAliveAt == 0`).
            lastAliveAt = System.currentTimeMillis()
            armWatchdog()
            if (wantDriving) {
                // Le bail appartient au socket : une nouvelle connexion doit le redemander, sinon
                // l'écran afficherait « tu conduis » avec des gestes refusés en `no_lease`.
                takeOver()
            }
        }
    }

    /**
     * Réessaie avec un délai qui double (1, 2, 4, 8, 16 s), puis s'arrête et laisse la main.
     *
     * Six tentatives couvrent un redémarrage de gateway ; au-delà, insister toutes les seize
     * secondes sur un serveur éteint vide la batterie pour rien — l'écran propose alors
     * « Réessayer ».
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
        /** Combien de reconnexions automatiques avant de rendre la main à la personne. */
        const val MAX_RETRIES = 6

        /** L'hôte d'une URL, sans `www.` — ce qui ira dans `used_by`. */
        fun hostOf(url: String): String = runCatching {
            java.net.URI(url).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")

        /** Les 4 derniers caractères, ou `****` : la même règle que le coffre. */
        fun fingerprintOf(value: String): String =
            if (value.length < 8) "****" else value.takeLast(4)
    }
}

/** L'écran, dans la forme de la référence : bandeau, adresse, page, puis les commandes. */
@Composable
fun BrowserScreen(
    viewModel: BrowserScreenViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    // **L'écran se connecte en s'ouvrant.** Il fallait toucher « Afficher le navigateur » pour
    // lancer le flux ; l'écran s'ouvrait donc sur un cadre noir et l'on croyait à une panne.
    LaunchedEffect(Unit) { viewModel.start() }

    val theme = LocalOnyxTheme.current
    val driving = state.lease?.human == true || state.phase == BrowserScreenViewModel.Phase.DRIVING
    // **Les jetons de la palette, pas des couleurs inventées.** L'écran est une visionneuse : il
    // prend la page sombre du socle et son texte, comme les autres scènes plein écran. Une couleur
    // écrite en dur ici serait peinte sans passer par la palette — le verrou de référence le refuse,
    // à raison.
    val night = ReferencePalette.DarkPage
    val ink = ReferencePalette.DarkText

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(night)
            // **Les barres du système, comme partout ailleurs.** Sans elles, le bandeau de l'écran
            // se dessinait **sous** la barre d'état : « Bouygues », l'heure et les icônes passaient
            // par-dessus le titre et la pastille « VOUS CONDUISEZ », et les deux se lisaient
            // ensemble. Aucun autre écran pleine page ne fait cette faute : celle-ci en manquait.
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
    ) {
        // -- le bandeau : fermer, qui conduit, l'état --------------------------------------
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

        // -- la barre d'adresse ------------------------------------------------------------
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

        // -- la page, centrée : le noir autour est le letterbox, pas un vide ----------------
        val densite = LocalDensity.current.density
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // La mesure de la zone part vers le serveur : c'est elle qui donne sa forme à la
                // page, et qui la redonne quand un pliable s'ouvre ou se ferme.
                .onSizeChanged { taille -> viewModel.screenSized(taille.width, taille.height, densite) },
            contentAlignment = Alignment.Center,
        ) {
            val image = state.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = OnyxLocalization["The agent's browser"],
                    contentScale = ContentScale.Fit,
                    // **Une capture nette peut être rendue molle par l'affichage.** Le filtre par
                    // défaut de Compose (`FilterQuality.Low`) fait un lissage bilinéaire simple :
                    // sur une trame de navigateur — du texte fin, des traits de 1 px — il étale
                    // les glyphes et le formulaire devient flou, surtout si la capture est un peu
                    // plus petite que l'écran. `Medium` garde le contour net sans créneler, et
                    // c'est la dernière marche du chemin : capture à la définition de l'écran,
                    // décodage pleine taille, affichage sans élargissement.
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
                // L'état se dit : « En attente » pendant une reconnexion se lisait comme une panne.
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
                // **La reconnexion est automatique ; ce geste est la sortie de secours** quand les
                // six tentatives sont épuisées (serveur vraiment éteint, réseau absent).
                BoutonPlein(OnyxLocalization["Retry"], theme.accent) { viewModel.retry() }
            }
            BrowserScreenViewModel.Phase.CONNECTING -> {
                Txt(OnyxLocalization["Connecting…"], ink.copy(alpha = 0.55f), size = 12)
            }
            BrowserScreenViewModel.Phase.WATCHING -> {
                BoutonPlein(OnyxLocalization["Take over"], theme.accent) { viewModel.takeOver() }
            }
            BrowserScreenViewModel.Phase.DRIVING -> {
                // **Le trio de la référence** : un indice, le champ, puis Tab / Échap / Rendre la
                // main. L'indice n'est pas décoratif — sans lui, on ne devine pas que le texte part
                // dans la page et non dans la conversation.
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
                        // **Champ masqué, clavier mot de passe** : ce que la référence laisse en clair.
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
                    // **Envoyer sans passer par le clavier** : le bouton fait le même geste que la
                    // touche « Envoyer », utile quand le clavier est replié.
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
                    // **Échap ferme les suggestions et les dialogues du site** — sans lui, une page
                    // qui ouvre une bannière ne se débloque pas depuis le téléphone.
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

/** Une pastille sombre et cliquable : les touches annexes (⌫, Tab, Échap). */
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

/** Le bouton principal : un aplat de couleur, comme « Hand back » dans la référence. */
@Composable
private fun BoutonPlein(
    label: String,
    color: Color,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            // Deux formes, un seul modificateur : `fillMaxWidth(0f)` puis `width(118.dp)` se
            // combattaient, et le bouton compact pouvait sortir de l'écran.
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

/** L'étiquette rouge « vous conduisez », avec sa pastille. */
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

/** Une ligne de texte : le `BasicText` de la maison, qui localise et prend sa couleur. */
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