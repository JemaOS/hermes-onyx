package com.onyx.app.ui.computer

import com.onyx.app.ui.design.OnyxSpace

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.net.ComputerBridgeClient
import com.onyx.app.data.net.ComputerFinger
import com.onyx.app.data.net.ComputerFling
import com.onyx.app.data.net.ComputerGain
import com.onyx.app.data.net.ComputerGestures
import com.onyx.app.data.net.ComputerHello
import com.onyx.app.data.net.ComputerInput
import com.onyx.app.data.net.ComputerLayout
import com.onyx.app.data.net.ComputerPacketBudget
import com.onyx.app.data.net.ComputerPoint
import com.onyx.app.data.net.ComputerPointer
import com.onyx.app.data.net.ComputerState
import com.onyx.app.data.net.ComputerStream
import com.onyx.app.data.net.ComputerTaps
import com.onyx.app.data.net.ComputerTwoFinger
import com.onyx.app.data.net.ComputerTwoFingerPolicy
import com.onyx.app.data.net.ComputerVelocity
import com.onyx.app.data.net.ComputerViewport
import com.onyx.app.data.net.ComputerWheel
import com.onyx.app.data.net.text
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.BarInk
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
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * L'écran « Ordinateur » : l'écran du PC en direct, et le doigt qui le pilote.
 *
 * Trois choses y vivent, dans cet ordre :
 *
 * 1. **l'aperçu** — l'image du PC, ajustée à la place disponible (jamais noyée dans du blanc), avec
 *    dessous la dernière action et qui a la main ;
 * 2. **la main** — « Prendre la main » / « Rendre la main ». Sans elle, l'aperçu regarde : le pont
 *    refuse chaque entrée, donc l'appui devient un geste d'aperçu (double appui = zoom) au lieu
 *    d'un clic qui n'arriverait pas ;
 * 3. **l'approbation** — une commande ne part qu'après un « oui », et un refus ne part pas du tout.
 *
 * Deux choses se décident par la **place**, pas par le nom de l'appareil ([ComputerLayout]) : fermé,
 * une seule colonne ; déplié, l'aperçu dans son volet **entier** et les commandes dans un volet
 * borné à droite. Le mode plein écran, lui, ne dépend que du geste de l'utilisateur.
 *
 * La session n'appartient pas à cet écran (voir [ComputerSession]) : sortir et revenir retrouve la
 * même connexion, la même image et la même question posée.
 */
@Composable
fun ComputerScreen(
    session: ComputerSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val clipboard = LocalClipboard.current
    val density = LocalDensity.current

    val phase by session.phase.collectAsStateWithLifecycle()
    val hello by session.hello.collectAsStateWithLifecycle()
    val state by session.state.collectAsStateWithLifecycle()
    // **Pas de `session.frame` ici.** L'image est lue dans l'aperçu seulement : sinon tout l'écran
    // — en-tête, ligne d'état, barre du bas — se recomposait à chaque image du PC.
    val notice by session.notice.collectAsStateWithLifecycle()
    val refusal by session.refused.collectAsStateWithLifecycle()
    val blocked by session.blocked.collectAsStateWithLifecycle()
    val approval by session.approval.collectAsStateWithLifecycle()
    val started by session.started.collectAsStateWithLifecycle()
    val runOutput by session.runOutput.collectAsStateWithLifecycle()
    // Les mesures du flux et le cran d'échelle : lus ici seulement pour le panneau « Détails du
    // flux » (le panneau les suit lui-même, à 1 Hz — rien qui recompose l'écran en continu).
    val stats by session.stats.collectAsStateWithLifecycle()
    val ladder by session.ladder.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf(!session.configured) }
    var draftAddress by remember { mutableStateOf(session.address) }
    var draftToken by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showStream by remember { mutableStateOf(false) }
    var askingCommand by remember { mutableStateOf(false) }
    var askingShortcut by remember { mutableStateOf(false) }
    var commandDraft by remember { mutableStateOf("") }
    var shortcutDraft by remember { mutableStateOf("") }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val view = remember { ComputerViewportState() }
    val pointer = remember { ComputerPointerState() }
    var movedAt by remember { mutableLongStateOf(0L) }

    // La session se reprend au premier passage sur l'écran : elle n'est pas refermée en sortant.
    LaunchedEffect(Unit) { if (session.configured && !started) session.open() }
    DisposableEffect(session) {
        onDispose {
            // Rien ici, exprès : quitter l'écran ne rend pas la main et ne coupe pas la connexion.
            // Seul « Fermer la session » appelle `stop()`.
        }
    }
    // Le retour sort d'abord du **plein écran** : il n'y a qu'un état à quitter, et le geste du
    // système ne doit pas jeter l'écran pour autant. (La condition était inversée — plein écran,
    // le retour rendait l'écran entier ; la garde ne se posait que hors plein écran, où elle
    // faisait exactement ce que le retour système faisait déjà.)
    BackHandler {
        if (fullscreen) {
            fullscreen = false
        } else {
            onBack()
        }
    }

    // Le geste en cours : la cadence monte le temps du mouvement, puis retombe.
    LaunchedEffect(movedAt) {
        if (movedAt == 0L) return@LaunchedEffect
        session.setMoving(true)
        delay(ComputerStream.TURBO_HOLD_MS)
        session.setMoving(false)
    }

    val activity = LocalContext.current.findActivity()
    val rootView = LocalView.current
    DisposableEffect(fullscreen, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, rootView) }
        if (fullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (fullscreen) theme.stageColor else theme.pageColor),
    ) {
        val windowWidth = maxWidth
        // Fermé : une colonne. Déplié : deux volets, et l'aperçu prend le sien **en entier**.
        val twoPanes = !editing && !fullscreen && ComputerLayout.twoPanes(windowWidth)
        val previewPane = when {
            editing -> windowWidth
            twoPanes -> ComputerLayout.previewWidth(windowWidth)
            else -> windowWidth
        }
        val previewPanePx = remember(previewPane, density.density) {
            ComputerLayout.previewWidthPx(previewPane, density.density)
        }

        // L'aperçu annonce la taille qu'il veut : décoder des pixels que l'écran ne montre pas se
        // paie en kilo-octets par seconde sur le réseau du propriétaire.
        LaunchedEffect(previewPanePx, phase) {
            if (phase == ComputerBridgeClient.Phase.LIVE) session.requestStream(previewPanePx)
        }

        val onGesture: () -> Unit = { movedAt = System.currentTimeMillis() }

        val stage: @Composable (Modifier) -> Unit = { stageModifier ->
            ComputerStage(
                frames = session.frames,
                screenWidth = hello?.screenWidth ?: 0,
                screenHeight = hello?.screenHeight ?: 0,
                phase = phase,
                notice = notice,
                onRetry = { session.retry() },
                piloting = state.humanHolds && phase == ComputerBridgeClient.Phase.LIVE,
                onDecoded = { session.noteDecoded(it) },
                view = view,
                pointer = pointer,
                onPoint = { point, button -> session.tap(point, button) },
                onMove = { point -> session.move(point) },
                onWheel = { point, amount -> session.scroll(point, amount) },
                onGesture = onGesture,
                modifier = stageModifier,
            )
        }

        when {
            // La **pose** du pont est un écran comme les autres : sans en-tête, elle n'avait ni
            // titre ni retour visible, et sa première ligne (« PONT ORDINATEUR ») se collait à la
            // barre d'état (relevé à 411 dp, 1.0.0+546). Le retour reste le geste de bord pour qui
            // le connaît, mais il se **voit** désormais, comme sur tous les écrans de la maison.
            editing -> Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                OnyxPageHeader(
                    title = "Computer",
                    onBack = onBack,
                    backLabel = "Back",
                    action = if (session.configured) "More" else null,
                    onAction = { showMenu = true },
                )
                ComputerSetup(
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
            }

            twoPanes -> Row(modifier = Modifier.fillMaxSize()) {
                stage(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .width(ComputerLayout.controlsWidth(windowWidth))
                        .fillMaxHeight()
                        .background(theme.pageColor)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    // Déplié, le volet des commandes porte **son propre en-tête** : sans lui, le
                    // retour disparaît et le menu « Plus » devient inatteignable — donc plus
                    // d'« Exécuter une commande… », de raccourci clavier, d'adresse du pont ni de
                    // « Fermer la session ». Un volet n'est pas une moitié d'écran : il a ses
                    // commandes à lui.
                    OnyxPageHeader(
                        title = "Computer",
                        onBack = onBack,
                        backLabel = "Back",
                        action = "More",
                        onAction = { showMenu = true },
                    )
                    ComputerSidePane(
                        session = session,
                        state = state,
                        hello = hello,
                        phase = phase,
                        refusal = refusal,
                        blocked = blocked,
                        notice = notice,
                        runOutput = runOutput,
                        typed = typed,
                        onTyped = { typed = it },
                        pointer = pointer,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (!fullscreen) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        OnyxPageHeader(
                            title = "Computer",
                            onBack = onBack,
                            backLabel = "Back",
                            action = "More",
                            onAction = { showMenu = true },
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    stage(Modifier.fillMaxSize())

                    // Le bandeau du plein écran : discret, mais toujours là pour ressortir.
                    if (fullscreen) {
                        FullscreenBanner(
                            onExit = { fullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .displayCutoutPadding()
                                .padding(top = 10.dp),
                        )
                    }
                }

                val bar = remember(theme, fullscreen) { computerBarInk(theme, fullscreen) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fullscreen) {
                                Modifier
                                    .background(theme.stageColor.copy(alpha = 0.86f))
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            } else {
                                // La barre du bas **ne colle pas** à la barre de gestes : l'app
                                // est bord à bord, donc cet inset est à prendre ici — le défaut
                                // classique est une rangée d'actions posée sur la barre système.
                                Modifier.navigationBarsPadding()
                            },
                        )
                        .imePadding(),
                ) {
                    ComputerStatusLine(state = state, hello = hello, bar = bar)
                    ComputerNotes(
                        refusal = refusal,
                        blocked = blocked,
                        notice = notice,
                        runOutput = runOutput,
                        onClearRefusal = { session.clearRefusal() },
                        onClearBlocked = { session.clearBlocked() },
                        bar = bar,
                    )
                    ComputerControls(
                        session = session,
                        holding = state.humanHolds,
                        live = phase == ComputerBridgeClient.Phase.LIVE,
                        typed = typed,
                        onTyped = { typed = it },
                        pointer = pointer,
                        bar = bar,
                    )
                }
            }
        }
    }

    if (showMenu) {
        OnyxMenu(
            items = buildList {
                // Chaque entrée referme le menu : sans cela il reste ouvert sous la question qu'il
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
                // Les commandes repliées de la rangée du bas. Elles sont **nommées** — un mot
                // entier, jamais un symbole seul — donc retrouvables et traduites, et elles ne
                // coûtent plus un pixel de la rangée : c'est ce qui la fait tenir sans défiler.
                add(MenuItem("Scroll up", Symbols.ChevronUp, groupBreak = true) {
                    showMenu = false
                    session.scroll(ComputerPoint(0, 0), -3)
                })
                add(MenuItem("Scroll down", Symbols.ChevronDown) {
                    showMenu = false
                    session.scroll(ComputerPoint(0, 0), 3)
                })
                add(MenuItem("Fit the screen", Symbols.ArrowCounterclockwise) {
                    showMenu = false
                    view.reset()
                })
                add(MenuItem(
                    label = if (fullscreen) "Exit full screen" else "Full screen",
                    symbol = if (fullscreen) Symbols.FullScreenExit else Symbols.FullScreenEnter,
                ) {
                    showMenu = false
                    fullscreen = !fullscreen
                })
                // Les chiffres du flux, et la liaison économe : discrets, dans « Plus » — pas un
                // onglet, pas un mot sur la rangée du bas qui ne sert qu'au pilotage.
                add(MenuItem("Stream details", Symbols.ChartBar, groupBreak = true) {
                    showMenu = false
                    showStream = true
                })
                add(MenuItem("Run a command…", Symbols.Terminal, groupBreak = true) {
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

    if (showStream) {
        ComputerStreamPanel(
            stats = stats,
            ladder = ladder,
            onEconomical = { session.setEconomical(it) },
            onDone = { showStream = false },
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

    // L'approbation d'une action sensible : deux réponses, aucune sortie par le dehors.
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

/**
 * Le zoom et le déplacement de l'aperçu, tenus à part du dessin.
 *
 * Ils vivent dans un objet stable pour que le détecteur de gestes **ne soit pas redémarré** quand
 * ils changent : un `pointerInput` qui reprend ses clés au milieu d'un pincement perd le geste.
 */
@Stable
private class ComputerViewportState {
    var zoom by mutableStateOf(ComputerViewport.FIT)
        private set
    var panX by mutableStateOf(0f)
        private set
    var panY by mutableStateOf(0f)
        private set

    val fitted: Boolean get() = ComputerViewport.isFitted(zoom, panX, panY)

    fun set(zoom: Float, panX: Float, panY: Float) {
        this.zoom = ComputerViewport.zoomOf(zoom)
        this.panX = panX
        this.panY = panY
    }

    fun reset() {
        zoom = ComputerViewport.FIT
        panX = 0f
        panY = 0f
    }
}

/**
 * Le pointeur **relatif** et son mode de vitesse, tenus hors du dessin.
 *
 * Le doigt ne désigne plus un point de l'image : il **déplace** le pointeur. Sa place initiale est
 * le centre du bureau (jamais sous le doigt, donc aucun saut au premier geste), et il ne bouge que
 * par le mouvement du doigt — c'est ce qui permet de viser une cible sans la couvrir.
 *
 * Le mode ([ComputerGain]) règle la proportion : « précision » avance lentement, « rapide »
 * traverse un bureau 4K d'un glissement. Il reste sur l'objet pour que le détecteur de gestes ne
 * soit pas redémarré quand on change de cran.
 */
@Stable
private class ComputerPointerState {
    var point by mutableStateOf(ComputerPoint(0, 0))
        private set
    var placed by mutableStateOf(false)
        private set
    var gain by mutableStateOf(ComputerGain.NORMAL)
        private set

    /** Place le pointeur au centre une seule fois, quand la taille du PC est connue. */
    fun placeAtCenter(screenWidth: Int, screenHeight: Int) {
        if (placed || screenWidth <= 0 || screenHeight <= 0) return
        point = ComputerPointer.centered(screenWidth, screenHeight)
        placed = true
    }

    fun moveTo(next: ComputerPoint) {
        point = next
    }

    fun nextGain() {
        gain = gain.next()
    }
}

/** Un appui long mûr : c'est un clic droit. Sous cette vitesse, un glissement ne lance pas d'élan. */
private const val FlingStartVelocity = 0.15f

/** Deux appuis comptent comme un double appui dans cette fenêtre (ms). */
private const val DoubleTapMs = 300L

/**
 * L'aperçu : la **scène**, l'image du PC dessinée au rectangle calculé, et les gestes.
 *
 * La scène est le fond assombri du thème (`stageColor`) : les bandes d'une image 16:9 dans un
 * téléphone sont sombres, jamais du blanc — une capture de bureau posée sur un blanc immense se lit
 * « page vide », et c'est exactement ce que le propriétaire a vu.
 *
 * L'image est **peinte sur un canvas**, pas posée dans une mise en page : aucun mesurage à refaire
 * pour chaque image. Le tampon est déjà à l'échelle du volet (l'app a demandé la taille au pont),
 * donc le filtre le moins cher suffit.
 */
@Composable
private fun ComputerStage(
    frames: StateFlow<ComputerBridgeClient.Frame?>,
    screenWidth: Int,
    screenHeight: Int,
    phase: ComputerBridgeClient.Phase,
    notice: String?,
    onRetry: () -> Unit,
    piloting: Boolean,
    onDecoded: (Int) -> Unit,
    view: ComputerViewportState,
    pointer: ComputerPointerState,
    onPoint: (ComputerPoint, Int) -> Unit,
    onMove: (ComputerPoint) -> Unit,
    onWheel: (ComputerPoint, Int) -> Unit,
    onGesture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val slop = ComputerGestures.slopPx(density.density)
    val scope = rememberCoroutineScope()

    // **La seule lecture de l'image de tout l'écran**, et elle est décodée hors du fil principal
    // (voir [rememberComputerImage]).
    val image by rememberComputerImage(frames, onDecoded)
    val gesture = rememberUpdatedState(onGesture)
    // La progression du clic droit : lisible par le curseur, écrite par le geste.
    val pressProgress = remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.background(theme.stageColor)) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val bitmap = image
        val imageWidth = bitmap?.width ?: 16
        val imageHeight = bitmap?.height ?: 9
        val fit = ComputerViewport.fitOf(
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            zoom = view.zoom,
            panX = view.panX,
            panY = view.panY,
        )

        // Le pointeur apparaît au centre du bureau dès que sa taille est connue — jamais sous le
        // doigt, donc aucun saut au premier geste.
        LaunchedEffect(screenWidth, screenHeight) {
            pointer.placeAtCenter(screenWidth, screenHeight)
        }

        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                    dstSize = IntSize(
                        fit.width.roundToInt().coerceAtLeast(1),
                        fit.height.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Low,
                )
            }
        } else {
            ComputerWaiting(phase = phase, notice = notice, onRetry = onRetry)
        }

        // **La reprise se voit.** Pendant une reconnexion, l'image reste — on ne quitte pas la
        // session, on ne perd ni le jeton ni la main — mais elle est grisée et la ligne le dit.
        // Un écran figé sans un mot, c'était exactement le défaut : on ne savait pas s'il fallait
        // attendre ou refaire la connexion.
        if (phase == ComputerBridgeClient.Phase.RECONNECTING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.stageColor.copy(alpha = 0.5f)),
            )
            ReconnectPill(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Les clés du détecteur sont **stables** (les tailles), jamais le zoom : un
                // pincement ne doit pas redémarrer le geste qu'il est en train de faire.
                .pointerInput(imageWidth, imageHeight, screenWidth, screenHeight, slop) {
                    var lastTapAt = 0L
                    var lastTapAt2 = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startedAt = System.currentTimeMillis()
                        var last = down.position
                        var maxPointers = 1
                        gesture.value()

                        // L'état du geste : un doigt, ou deux — et ce qu'ils font.
                        var moved = false
                        var rightClicked = false
                        var twoFinger = ComputerTwoFinger.UNDECIDED
                        var startSpan = 0f
                        var startZoom = view.zoom
                        var middle = down.position
                        var wheelCarry = 0f
                        val motion = ComputerPacketBudget(ComputerPacketBudget.MOTION_MS)
                        val wheel = ComputerPacketBudget(ComputerPacketBudget.WHEEL_MS)
                        val velocity = ComputerVelocity(90L)
                        velocity.add(startedAt, down.position.x, down.position.y)

                        while (true) {
                            // L'appui long n'a pas besoin d'un événement pour mûrir : un délai le
                            // fait aboutir même si le doigt ne bouge plus du tout.
                            val canLongPress = piloting && !moved && !rightClicked
                            val timeout = if (canLongPress) {
                                (ComputerTaps.LONG_PRESS_MS - (System.currentTimeMillis() - startedAt))
                                    .coerceAtLeast(0L)
                            } else {
                                Long.MAX_VALUE
                            }
                            val event = if (timeout == Long.MAX_VALUE) {
                                awaitPointerEvent()
                            } else {
                                withTimeoutOrNull(timeout) { awaitPointerEvent() }
                            }

                            if (event == null) {
                                // L'appui long est mûr : c'est le clic droit du PC, là où pointe le
                                // curseur — pas sous le doigt.
                                pressProgress.floatValue = 0f
                                onPoint(pointer.point, 3)
                                rightClicked = true
                                moved = true
                                continue
                            }

                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isNotEmpty()) maxPointers = maxOf(maxPointers, pressed.size)
                            val stillPressed = event.changes.any { it.pressed }

                            if (pressed.size >= 2) {
                                val span = (pressed[0].position - pressed[1].position).getDistance()
                                val centre = Offset(
                                    (pressed[0].position.x + pressed[1].position.x) / 2f,
                                    (pressed[0].position.y + pressed[1].position.y) / 2f,
                                )
                                if (startSpan == 0f) {
                                    startSpan = span
                                    startZoom = view.zoom
                                    middle = centre
                                }
                                if (twoFinger == ComputerTwoFinger.UNDECIDED) {
                                    twoFinger = ComputerTwoFingerPolicy.decide(
                                        startSpanPx = startSpan,
                                        currentSpanPx = span,
                                        driftPx = (centre - middle).getDistance(),
                                        slopPx = slop,
                                    )
                                }
                                when (twoFinger) {
                                    // Pincer : l'aperçu se rapproche **sous les doigts**, et rien
                                    // ne part vers le PC — c'est une lunette, pas une action.
                                    ComputerTwoFinger.PINCH -> {
                                        val zoom = ComputerViewport.pinchZoomBounded(startZoom, startSpan, span)
                                        val (panX, panY) = ComputerViewport.panToFocus(
                                            boxWidth = boxWidth,
                                            boxHeight = boxHeight,
                                            imageWidth = imageWidth,
                                            imageHeight = imageHeight,
                                            zoom = zoom,
                                            focusX = centre.x,
                                            focusY = centre.y,
                                        )
                                        view.set(zoom, panX, panY)
                                    }
                                    // Glisser à deux doigts : la molette, avec son élan au lâcher.
                                    ComputerTwoFinger.SCROLL -> {
                                        if (piloting) {
                                            wheelCarry += centre.y - middle.y
                                            val notches = ComputerWheel.notches(wheelCarry)
                                            if (notches != 0 &&
                                                wheel.takeIfDue(System.currentTimeMillis()) != null
                                            ) {
                                                onWheel(pointer.point, notches)
                                                wheelCarry += notches * ComputerWheel.NOTCH_VIEW_PX
                                            }
                                        }
                                    }
                                    ComputerTwoFinger.UNDECIDED -> Unit
                                }
                                middle = centre
                                velocity.add(System.currentTimeMillis(), centre.x, centre.y)
                                moved = true
                                pressProgress.floatValue = 0f
                                gesture.value()
                            } else if (pressed.size == 1 && twoFinger == ComputerTwoFinger.UNDECIDED) {
                                val position = pressed.first().position
                                if (!moved && ComputerTaps.moving((position - down.position).getDistance(), slop)) {
                                    moved = true
                                    pressProgress.floatValue = 0f
                                }
                                if (moved) {
                                    if (piloting) {
                                        // Le pointeur avance **proportionnellement au doigt**, depuis
                                        // là où il est : aucune visée, aucun saut au départ.
                                        pointer.moveTo(
                                            ComputerPointer.advance(
                                                from = pointer.point,
                                                dxView = position.x - last.x,
                                                dyView = position.y - last.y,
                                                imageWidthPx = fit.width,
                                                imageHeightPx = fit.height,
                                                screenWidth = screenWidth,
                                                screenHeight = screenHeight,
                                                gain = pointer.gain,
                                            ),
                                        )
                                        motion.add(position.x - last.x, position.y - last.y)
                                        motion.takeIfDue(System.currentTimeMillis())
                                            ?.let { onMove(pointer.point) }
                                        gesture.value()
                                    }
                                } else if (piloting) {
                                    // L'anneau de progression : le clic droit se voit venir, et
                                    // s'efface dès que le doigt s'éloigne (annulation).
                                    pressProgress.floatValue =
                                        ((System.currentTimeMillis() - startedAt).toFloat() /
                                            ComputerTaps.LONG_PRESS_MS).coerceIn(0f, 1f)
                                }
                                last = position
                                velocity.add(System.currentTimeMillis(), position.x, position.y)
                            }

                            event.changes.forEach { it.consume() }
                            if (!stillPressed) break
                        }

                        pressProgress.floatValue = 0f

                        // Un geste à deux doigts n'est jamais un clic.
                        if (maxPointers > 1 || twoFinger != ComputerTwoFinger.UNDECIDED) {
                            if (twoFinger == ComputerTwoFinger.SCROLL && piloting) {
                                // Ce qui reste dû part tout de suite, puis l'élan prend le relais.
                                val rest = ComputerWheel.notches(wheelCarry)
                                if (rest != 0) onWheel(pointer.point, rest)
                                val (_, vy) = velocity.velocity()
                                if (abs(vy) >= FlingStartVelocity) {
                                    scope.launch {
                                        val fling = ComputerFling(vy, pointer.gain)
                                        while (!fling.finished) {
                                            delay(ComputerFling.STEP_MS.toLong())
                                            val step = fling.step(ComputerFling.STEP_MS)
                                            if (step != 0) onWheel(pointer.point, step)
                                        }
                                    }
                                }
                            }
                            return@awaitEachGesture
                        }

                        // Ce qui restait à envoyer au pointeur part avant que le geste ne s'achève.
                        if (moved && piloting) motion.takeRemaining()?.let { onMove(pointer.point) }

                        val released = ComputerTaps.onRelease(
                            distancePx = (last - down.position).getDistance(),
                            durationMs = System.currentTimeMillis() - startedAt,
                            slopPx = slop,
                            longPressMs = ComputerTaps.LONG_PRESS_MS,
                        )
                        when (released) {
                            ComputerFinger.CLICK -> {
                                val now = System.currentTimeMillis()
                                // Deux appuis rapprochés : l'aperçu se rapproche (ou revient à
                                // l'ajustement). Ce n'est un geste d'aperçu que **sans** la main —
                                // quand on pilote, un appui est un clic, tout de suite.
                                if (!piloting &&
                                    now - lastTapAt <= DoubleTapMs &&
                                    (last - lastTapAt2).getDistance() <= slop * 3f
                                ) {
                                    lastTapAt = 0L
                                    val target = ComputerViewport.doubleTapZoom(view.zoom)
                                    if (ComputerViewport.isFitted(target, 0f, 0f)) {
                                        view.reset()
                                    } else {
                                        val (panX, panY) = ComputerViewport.panToFocus(
                                            boxWidth = boxWidth,
                                            boxHeight = boxHeight,
                                            imageWidth = imageWidth,
                                            imageHeight = imageHeight,
                                            zoom = target,
                                            focusX = last.x,
                                            focusY = last.y,
                                        )
                                        view.set(target, panX, panY)
                                    }
                                } else {
                                    lastTapAt = now
                                    lastTapAt2 = last
                                    // Sans la main, le pont refuserait ce clic : l'appui reste donc
                                    // un geste d'aperçu plutôt qu'une action qui n'arrive pas.
                                    if (piloting) onPoint(pointer.point, 1)
                                }
                            }
                            // Déjà parti : l'appui long a cliqué à droite en mûrissant.
                            ComputerFinger.RIGHT_CLICK ->
                                if (!rightClicked && piloting) onPoint(pointer.point, 3)
                            ComputerFinger.MOVE -> Unit
                        }
                    }
                },
        )

        // Le curseur, par-dessus l'image : on voit où l'on clique, toujours.
        ComputerCursorOverlay(
            pointer = pointer.point,
            fit = fit,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            image = bitmap,
            gain = pointer.gain,
            precise = pointer.gain == ComputerGain.PRECISION,
            pressProgress = pressProgress.floatValue,
        )
    }
}

/**
 * « Reconnexion… » — la mention discrète d'une reprise en cours.
 *
 * Elle est posée sur l'image grisée, pas à la place : on continue de voir l'écran qu'on avait. Et
 * elle porte un **mot**, pas seulement une couleur : un état qui ne se lit qu'à la nuance ne se lit
 * pas.
 */
@Composable
private fun ReconnectPill(modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .background(theme.stageColor.copy(alpha = 0.82f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BasicText(
            text = "Reconnecting…",
            style = theme.caption.copy(color = theme.stageText),
            maxLines = 1,
        )
    }
}

/** Le bandeau du plein écran : une pastille discrète, et la façon d'en sortir au doigt. */
@Composable
private fun FullscreenBanner(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .background(theme.stageColor.copy(alpha = 0.72f), RoundedCornerShape(50))
            .onyxClickable(onClick = onExit)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semanticsLabel("Exit full screen"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        OnyxIcon(Symbols.FullScreenExit, tint = theme.stageText, size = 14.dp)
        BasicText(
            text = "Exit full screen",
            style = theme.caption.copy(color = theme.stageText),
            maxLines = 1,
        )
    }
}

/** L'attente : la connexion, la panne, et la façon d'en sortir. */
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
            OnyxIcon(Symbols.DesktopComputer, tint = theme.stageText.copy(alpha = 0.55f), size = 30.dp)
        }
        BasicText(
            text = notice ?: if (phase == ComputerBridgeClient.Phase.CONNECTING) {
                "Connecting to the computer…"
            } else {
                "The computer's screen isn't coming through."
            },
            localize = notice == null,
            style = theme.footnote.copy(color = theme.stageText.copy(alpha = 0.78f)),
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 320.dp),
        )
        BasicText(
            text = "Try again",
            style = theme.body.copy(color = theme.accent),
            modifier = Modifier.padding(top = 14.dp).onyxClickable(onClick = onRetry),
        )
    }
}

/** Le réglage du pont : son adresse, et son jeton — jamais montré, mais copiable. */
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

/**
 * Sous l'aperçu — ou dans le volet de droite quand l'écran est déplié : qui a la main, et la
 * dernière action.
 *
 * **Une ligne, jamais un pavé.** Le point coloré dit la main d'un regard ; le détail — ce qui vient
 * de se passer — est le seul élément souple, donc le seul qui puisse être coupé par une ellipse,
 * avec `maxLines = 1` pour qu'un repli mot à mot soit impossible. La résolution du PC n'est pas du
 * remplissage : elle ne s'affiche que si la place existe.
 */
@Composable
private fun ComputerStatusLine(state: ComputerState, hello: ComputerHello?, bar: BarInk) {
    val theme = LocalOnyxTheme.current
    val action = state.last.text()
    val holder = OnyxLocalization[if (state.humanHolds) "You have the hand." else "The agent has the hand."]
    val detail = buildString {
        action?.let {
            append(OnyxLocalization[it.verb])
            append(" ")
            append(it.detail)
        }
        if (state.busy) {
            if (isNotEmpty()) append(" · ")
            append(OnyxLocalization["Working…"])
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    // La pastille dit la main d'un regard : la couleur du thème quand elle s'y voit
                    // vraiment, l'encre atténuée sinon — jamais une couleur sous le seuil de lecture.
                    if (state.humanHolds) bar.signal else bar.muted,
                    RoundedCornerShape(50),
                ),
        )
        BasicText(
            text = holder,
            localize = false,
            style = theme.caption.copy(color = bar.ink),
            maxLines = 1,
        )
        if (detail.isNotEmpty()) {
            BasicText(
                text = "· $detail",
                localize = false,
                style = theme.caption.copy(
                    color = bar.muted,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 2,
                // **Un état ne se coupe pas** : à une ligne, « Cliqué 1280, 512 · Travail… » se
                // terminait en « Cliqu… ». Il se replie donc au plus sur deux lignes ; la main et
                // la résolution, elles, gardent chacune la leur (l'invariant de la barre).
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        hello?.let {
            BasicText(
                text = "· ${it.screenWidth}×${it.screenHeight}",
                localize = false,
                style = theme.caption2.copy(
                    color = bar.muted,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Les notes passagères : un refus, une panne, une phrase du pont, la sortie d'une commande. */
@Composable
private fun ComputerNotes(
    refusal: String?,
    blocked: String?,
    notice: String?,
    runOutput: String?,
    onClearRefusal: () -> Unit,
    onClearBlocked: () -> Unit,
    bar: BarInk,
) {
    val theme = LocalOnyxTheme.current
    refusal?.let { detail ->
        ComputerNote(
            text = OnyxLocalization.format("Refused: nothing was sent. (%1\$s)", detail),
            tone = bar.mutedOnSwatch,
            onDismiss = onClearRefusal,
            bar = bar,
        )
    }
    blocked?.let { command ->
        ComputerNote(
            text = OnyxLocalization.format("Onyx refuses this command. (%1\$s)", command),
            tone = bar.mutedOnSwatch,
            onDismiss = onClearBlocked,
            bar = bar,
        )
    }
    notice?.let { message ->
        ComputerNote(text = message, tone = bar.mutedOnSwatch, onDismiss = null, bar = bar)
    }
    runOutput?.let { output -> ComputerOutput(output, bar) }
}

/** Une note passagère : un refus, une panne, une phrase du pont. */
@Composable
private fun ComputerNote(text: String, tone: Color, onDismiss: (() -> Unit)?, bar: BarInk) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(bar.swatch, RoundedCornerShape(OnyxSpace.ControlCorner))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            localize = false,
            style = theme.footnote.copy(color = tone),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            OnyxIcon(
                Symbols.Xmark,
                tint = bar.iconIdle,
                size = 13.dp,
                modifier = Modifier.onyxClickable(onClick = onDismiss),
            )
        }
    }
}

/** Ce que la dernière commande approuvée a rendu. */
@Composable
private fun ComputerOutput(output: String, bar: BarInk) {
    val theme = LocalOnyxTheme.current
    if (output.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .heightIn(max = 120.dp)
            .background(bar.swatch, RoundedCornerShape(OnyxSpace.ControlCorner))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText(
            text = output.trim(),
            localize = false,
            style = theme.footnote.copy(
                color = bar.ink,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

/**
 * Le volet des commandes d'un écran déplié : l'état, les notes, et la barre au bas du volet.
 *
 * Il porte **ses propres insets** (mesurés sur la fenêtre entière, pas sur la moitié) : c'est un
 * volet, pas une moitié d'écran, et rien ne doit coller à la barre de gestes ni passer sous la
 * barre d'état.
 */
@Composable
private fun ComputerSidePane(
    session: ComputerSession,
    state: ComputerState,
    hello: ComputerHello?,
    phase: ComputerBridgeClient.Phase,
    refusal: String?,
    blocked: String?,
    notice: String?,
    runOutput: String?,
    typed: String,
    onTyped: (String) -> Unit,
    pointer: ComputerPointerState,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = modifier) {
        val bar = remember(theme) { computerBarInk(theme, fullscreen = false) }
        ComputerStatusLine(state = state, hello = hello, bar = bar)
        ComputerNotes(
            refusal = refusal,
            blocked = blocked,
            notice = notice,
            runOutput = runOutput,
            onClearRefusal = { session.clearRefusal() },
            onClearBlocked = { session.clearBlocked() },
            bar = bar,
        )
        Box(modifier = Modifier.weight(1f))
        ComputerControls(
            session = session,
            holding = state.humanHolds,
            live = phase == ComputerBridgeClient.Phase.LIVE,
            typed = typed,
            onTyped = onTyped,
            pointer = pointer,
            bar = bar,
        )
    }
}

/**
 * La barre du bas : la main, les touches qui servent **tout le temps**, et le champ pour écrire.
 *
 * La rangée **ne défile pas et ne déborde pas** : elle ne porte que ce qu'on touche sans y penser —
 * prendre ou rendre la main, la touche Retour, Échap, et la visée. Ces quatre-là tiennent dans la
 * place d'un téléphone à police 1,3× sans jamais se couper, quelle que soit la largeur du volet et
 * quelle que soit la taille de police du système.
 *
 * Le reste — défiler, ajuster à l'écran, le plein écran — vit dans « Plus » (voir
 * [ComputerScreen]) : une commande qui quitte la rangée doit rester **nommée** et à un geste, pas
 * disparaître derrière un symbole.
 */
@Composable
private fun ComputerControls(
    session: ComputerSession,
    holding: Boolean,
    live: Boolean,
    typed: String,
    onTyped: (String) -> Unit,
    pointer: ComputerPointerState,
    bar: BarInk,
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComputerHandButton(
                holding = holding,
                enabled = live,
                onClick = { if (holding) session.release() else session.take() },
                bar = bar,
            )
            // Le ressort qui garde la rangée dans l'écran : les icônes restent collées au bord
            // droit, la pastille de la main à gauche, et rien ne se chevauche jamais.
            Box(modifier = Modifier.weight(1f))
            ComputerKeyButton(
                symbol = Symbols.ArrowTurnDownRight,
                label = "Return",
                enabled = holding && live,
                onClick = { session.key("Return") },
                bar = bar,
            )
            ComputerKeyButton(
                symbol = Symbols.Xmark,
                label = "Escape",
                enabled = holding && live,
                onClick = { session.key("Escape") },
                bar = bar,
            )
            // La visée : le même bouton que les autres, avec un fond accentué quand le cran
            // « précision » est actif, et un tour complet des trois crans à chaque appui.
            // Le mode précision se lit aussi sur le curseur (anneau accentué et loupe).
            ComputerKeyButton(
                symbol = Symbols.MagnifyingGlass,
                label = pointer.gain.labelKey,
                enabled = holding && live,
                onClick = { pointer.nextGain() },
                bar = bar,
                highlight = pointer.gain == ComputerGain.PRECISION,
            )
        }

        if (holding) {
            // La ligne d'aide : **une seule ligne**, en français, qui dit les quatre gestes d'un
            // regard — sans jargon, sans manuel. Elle défile horizontalement si la place manque,
            // donc elle n'est jamais tronquée à moitié (un geste expliqué à moitié ne s'apprend pas).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                BasicText(
                    text = "Drag to move · tap to click · long-press for right-click · two fingers to scroll",
                    style = theme.caption.copy(color = bar.muted),
                    maxLines = 1,
                )
            }
            // Le champ tient sur **une ligne** : c'est un champ de saisie, pas un paragraphe, et
            // c'est ce que le propriétaire a fait réparer — on n'y touche pas.
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
                    tint = if (typed.isNotEmpty()) bar.ink else bar.iconIdleOnSurface,
                    size = 24.dp,
                    contentDescription = "Type it on the computer",
                    modifier = Modifier.padding(start = 10.dp).onyxClickable(onClick = send),
                )
            }
        } else {
            BasicText(
                text = "Take the hand to type and to click.",
                style = theme.caption.copy(color = bar.muted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * « Prendre la main » / « Rendre la main » : un mot, une icône, une seule couleur.
 *
 * C'est le seul bouton **plein** de la barre : au repos il porte le fond des cases et l'encre
 * pleine du thème, la main prise il porte la couleur du thème et l'encre qui s'y lit le mieux
 * (mesurée : un blanc sur un accent clair ne tient pas 4,5:1).
 */
@Composable
private fun ComputerHandButton(holding: Boolean, enabled: Boolean, onClick: () -> Unit, bar: BarInk) {
    val theme = LocalOnyxTheme.current
    val active = holding && enabled
    val content = when {
        active -> bar.onAccent
        enabled -> bar.ink
        else -> bar.mutedOnSwatch
    }
    Row(
        modifier = Modifier
            .background(
                if (active) bar.accentFill else bar.swatch,
                RoundedCornerShape(OnyxSpace.ControlCorner),
            )
            .border(BorderStroke(1.dp, bar.edge), RoundedCornerShape(OnyxSpace.ControlCorner))
            // L'encre au repos est **déjà** mesurée (elle tient 3:1) : l'opacité globale d'un
            // contrôle désactivé la ramènerait sous le seuil, donc on ne l'applique pas ici.
            .onyxClickable(enabled = enabled, disabledAlpha = 1f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics { contentDescription = OnyxLocalization[if (holding) "Hand back" else "Take over"] },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnyxIcon(symbol = Symbols.HandRaised, tint = content, size = 15.dp)
        BasicText(
            text = if (holding) "Release" else "Take",
            style = theme.footnote.copy(color = content),
            maxLines = 1,
        )
    }
}

/**
 * Un bouton de la barre : une case carrée, une icône, et un nom pour qui ne voit pas l'icône.
 *
 * La taille est **fixe** ([ComputerKeyButtonSize]) : c'est ce qui garantit qu'aucune rangée ne
 * déborde et que rien n'est coupé quand la police du système grandit.
 *
 * Les couleurs viennent toutes de [BarInk] — la case, sa **bordure fine**, l'icône active et
 * l'icône au repos. Aucune n'est choisie ici : c'est ce qui rend la barre lisible sur la page
 * comme sur la scène du plein écran, en clair comme en sombre.
 */
@Composable
private fun ComputerKeyButton(
    symbol: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    bar: BarInk,
    highlight: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .size(ComputerKeyButtonSize)
            .background(
                if (highlight && enabled) theme.accent.copy(alpha = 0.20f) else bar.swatch,
                RoundedCornerShape(OnyxSpace.ControlCorner),
            )
            .border(BorderStroke(1.dp, bar.edge), RoundedCornerShape(OnyxSpace.ControlCorner))
            // Voir `ComputerHandButton` : l'encre au repos est mesurée, l'opacité globale d'un
            // contrôle désactivé ne s'y ajoute pas.
            .onyxClickable(enabled = enabled, disabledAlpha = 1f, onClick = onClick)
            .semanticsLabel(label),
        contentAlignment = Alignment.Center,
    ) {
        OnyxIcon(
            symbol = symbol,
            tint = if (enabled) bar.icon else bar.iconIdle,
            size = 17.dp,
        )
    }
}

/**
 * L'encre de la barre du bas : elle suit le fond sur lequel la barre se pose.
 *
 * Hors plein écran, c'est la page et son encre. **En plein écran, c'est la scène** — toujours
 * sombre dans les deux apparences : une barre qui garderait l'encre de l'apparence écrirait du
 * noir sur du noir dès que le thème est clair.
 */
private fun computerBarInk(theme: ResolvedTheme, fullscreen: Boolean): BarInk = BarInk(
    surface = if (fullscreen) theme.stageColor else theme.pageColor,
    ink = if (fullscreen) theme.stageText else theme.textColor,
    accent = theme.accent,
)

/** Une case de la barre : 40 points, tout le monde pareil. */
private val ComputerKeyButtonSize = 40.dp

/** L'activité qui porte la fenêtre, à travers les enveloppes de contexte de Compose. */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
