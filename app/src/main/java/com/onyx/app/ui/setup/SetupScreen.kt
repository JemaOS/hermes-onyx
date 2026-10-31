package com.onyx.app.ui.setup

import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.R
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SetupCode
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.data.net.ConnectionTester
import com.onyx.app.data.net.DiscoveredSetup
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.onyx.app.ui.design.IdentifierText

/** L'ordre de la liste « How does Onyx reach it? » : Dashboard, API, OpenAI-compatible. */
internal val BackendTransports = listOf(
    Transport.HERMES_SERVE,
    Transport.HERMES_SESSIONS,
    Transport.CHAT_COMPLETIONS,
)

/** Le libellé d'un transport, mot pour mot du libellé de la référence. */
internal fun transportLabel(transport: Transport): String = when (transport) {
    Transport.HERMES_SERVE -> "Hermes Dashboard (WebSocket)"
    Transport.HERMES_SESSIONS -> "Hermes API (shared sessions)"
    Transport.CHAT_COMPLETIONS -> "OpenAI-compatible (direct to a model)"
}

/**
 * D'où vient une adresse proposée. Une source **nommée** : l'utilisateur doit pouvoir dire
 * pourquoi cette adresse lui est offerte, sinon la proposition est du bruit.
 */
internal fun suggestionLabel(source: SuggestionSource): String = when (source) {
    SuggestionSource.CURRENT -> "This server's address"
    SuggestionSource.LOCAL -> "This phone's network"
    SuggestionSource.LAST_GATEWAY -> "The gateway you used last"
    SuggestionSource.SAVED -> "One of your saved servers"
}

/**
 * L'adresse d'un serveur enregistré, telle qu'on la proposerait : celle de l'API pour un serveur de
 * chat, celle du Dashboard sinon. Une seule, jamais les deux — une proposition n'est pas un doublon.
 */
internal fun setupAddressOf(server: ServerConfig): String =
    if (server.baseUrl.isNotBlank()) server.normalizedBaseUrl else server.serveBase

/**
 * Le lien de configuration d'un brouillon — ce que le bouton « Copier » de la barre met dans le
 * presse-papiers.
 *
 * Il porte **les adresses et le modèle, pas les secrets** : un mot de passe ou une clé ne part pas
 * dans le presse-papiers parce qu'on a appuyé sur une capsule. C'est la règle de « Set up another
 * device » dans la référence, dont le code est exclu tant qu'on ne l'a pas demandé.
 */
internal fun setupLinkFor(config: ServerConfig): String = SetupCode(
    name = config.name,
    dashboardURL = config.serveBaseUrl,
    dashboardUser = config.serveUsername,
    apiURL = config.normalizedBaseUrl,
    profile = config.hermesProfile,
    accessID = config.accessID,
    model = config.model,
    use = config.transport,
).link()

/**
 * L'exemple du champ d'adresse : chaque serveur a le sien. C'est le texte d'aide **du** champ, jamais
 * une valeur posée dedans.
 */
internal fun transportPlaceholder(config: ServerConfig): String = when (config.transport) {
    Transport.HERMES_SERVE -> "http://your-onyx:9119"
    Transport.HERMES_SESSIONS -> "https://your-gateway:8642"
    Transport.CHAT_COMPLETIONS -> "http://your-server:8080"
}

/**
 * Écrit l'adresse **là où le transport retenu la lit** — [transportAddress] dit laquelle, et c'est
 * la même fonction qui la relit. Le champ de la page est **unique** : en mode avancé, l'adresse
 * n'est plus redemandée une seconde fois.
 */
internal fun withTransportAddress(config: ServerConfig, value: String): ServerConfig =
    when (config.transport) {
        Transport.HERMES_SERVE -> config.copy(serveBaseUrl = value)
        else -> config.copy(baseUrl = value)
    }

/**
 * Le premier lancement, et « Set up connection » depuis les Réglages.
 *
 * **Refait sur l'anatomie de la mise en route de la référence**, et rien n'a été retiré : tout a été
 * **rangé**. L'ordre de la page est celui de la référence :
 *
 *  1. l'icône de l'app **en haut à gauche** (un petit carré arrondi — pas d'illustration, pas de
 *     logo géant) ;
 *  2. un **très grand titre en gras**, et sous lui la phrase qui rassure : « Rien n'est envoyé tant
 *     que vous ne vous êtes pas connecté. » ;
 *  3. **le parcours principal** : l'adresse de la passerelle, son libellé au-dessus, le globe à
 *     gauche, **« Tester » dans le champ** — puis la **ligne d'aide** en gris et l'**état** en clair
 *     (connecté avec la latence, ou en échec avec la raison) ;
 *  4. **les trois façons d'entrer l'adresse au même endroit** : la saisie (le champ), le **QR** de
 *     configuration et le **collage du code** — le QR n'est plus un écran isolé ;
 *  5. **des sections repliables** pour tout le reste : les explications d'adressage (LAN, Tailscale,
 *     HTTPS), la protection d'accès (Cloudflare Access, avec sa note de chiffrement), le choix du
 *     transport et ses identifiants, ce que la découverte du serveur a trouvé, et ce que l'app fait.
 *
 * Ce qui est **gardé** de l'écran d'avant, mot pour mot : la clé détectée dans un code de
 * configuration et l'avertissement qui va avec, la vérification **réelle** de la passerelle (avec sa
 * latence), la liste multi-serveurs, et le fait qu'**aucun secret ne se relit** — les champs secrets
 * sont masqués et ne s'offrent que par la copie.
 *
 * Contrat de sortie : « Skip » ferme **toujours** l'écran, sans test ; « Done » conclut, ou dit en
 * une phrase pourquoi il ne peut pas. Aucun des deux ne reste muet.
 */
@Composable
fun SetupScreen(
    config: ServerConfig,
    onChange: ((ServerConfig) -> ServerConfig) -> Unit,
    onDone: () -> Unit,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    onScanCode: () -> Unit,
    onPasteCode: (String) -> Boolean,
    /** Le geste du mode « Simple » : adresse + clé, et la découverte de tout le reste. */
    onDiscover: suspend (address: String, key: String) -> DiscoveredSetup = { _, _ -> DiscoveredSetup() },
    /** Applique ce que la découverte a rapporté (voix, pont, langue) — au-delà du serveur. */
    onApplyDiscovery: (DiscoveredSetup) -> Unit = {},
    /**
     * « Skip » : sortir de la configuration sans connexion établie. Toujours disponible,
     * immédiatement — c'est la porte de sortie quand aucune configuration ne convient.
     */
    onSkip: () -> Unit = onDone,
    /**
     * **Ce que le mode « Simple » propose** sous son champ d'adresse : les adresses déjà connues de
     * cette installation (le serveur en cours, le réseau du téléphone, la passerelle précédente,
     * les autres serveurs). Le propriétaire a dit « ça propose rien » : c'est la réponse.
     */
    suggestions: List<AddressSuggestion> = emptyList(),
    /**
     * Le lien de configuration de ce brouillon, pour le bouton « Copier » **en haut** — la même
     * chose que « Set up another device », fabriqué depuis l'écran de configuration lui-même.
     */
    setupLink: String = "",
) {
    val theme = LocalOnyxTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    var testing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ConnectionProbe?>(null) }
    /**
     * Pourquoi un collage n'a pas été accepté, en une phrase ; `null` quand tout va bien.
     *
     * C'était un booléen — l'écran ne pouvait donc dire que « ce n'est pas un lien », jamais **ce
     * qui venait d'être collé** ni **où cela va** ([SetupPaste.problemFor]).
     */
    var pasteProblem by remember { mutableStateOf<String?>(null) }
    /** Pourquoi « Done » n'a pas conclu, en une phrase ; `null` quand rien ne l'empêche. */
    var reason by remember { mutableStateOf<String?>(null) }
    /**
     * L'adresse que le presse-papiers porte, s'il en porte une. Le mode « Simple » l'offre par la
     * capsule « Coller » **dans** le champ : une adresse copiée depuis un autre appareil se pose
     * d'un appui, au lieu de rester invisible.
     */
    // Le bandeau « lien copié », qui se retire tout seul.
    var copiedLink by remember { mutableStateOf(false) }
    // La découverte du mode « Simple » : son état vit **avant** le geste qui la lance, pour que le
    // test d'une adresse réussie puisse l'appeler.
    var discovering by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<DiscoveredSetup?>(null) }
    var discoverProblem by remember { mutableStateOf<String?>(null) }

    // Un champ corrigé efface le verdict du test précédent : il ne portait pas sur cette adresse.
    LaunchedEffect(config.transport, config.baseUrl, config.serveBaseUrl) {
        reason = null
        probe = null
    }

    /**
     * Le geste du mode « Simple » : on découvre, puis on remplit tout — sans bloquer sur un vide.
     *
     * **Et on le dit.** Quand le serveur n'annonce rien de plus que « je réponds », l'écran écrit
     * ce qu'il n'a pas trouvé au lieu de rester muet : « Connecté. Le serveur n'a rien annoncé
     * d'autre… ». Le silence était exactement le défaut signalé.
     */
    fun discover() {
        if (discovering) return
        discovering = true
        discoverProblem = null
        val address = transportAddress(config).trim()
        val key = config.apiKey.trim()
        scope.launch {
            val found = try {
                onDiscover(address, key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DiscoveredSetup()
            }
            discovered = found
            if (found.model.isNotEmpty()) onChange { it.copy(model = found.model) }
            onApplyDiscovery(found)
            discoverProblem = when {
                !found.reachable ->
                    "Nothing answered at that address. Check it, or add the connection by hand under Advanced."
                !found.hasExtras && found.model.isEmpty() ->
                    "Connected. The server announced nothing else — set the model and the voices by hand under Advanced."
                else -> null
            }
            discovering = false
        }
    }


    /**
     * Le test de connexion, pour le transport retenu (le Dashboard a sa propre sonde).
     *
     * La sonde est **enveloppée** : une adresse mal formée lève avant même la requête, et sans ce
     * filet le bouton restait estompé et « Test en cours… » tournait pour toujours. L'échec est alors
     * un verdict à part entière — avec sa raison — jamais un silence.
     */
    fun runTest() {
        if (testing) return
        testing = true
        probe = null
        reason = null
        val draft = config
        scope.launch {
            val outcome = try {
                ConnectionTester.current(draft)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ConnectionProbe(
                    ok = false,
                    message = error.message ?: OnyxLocalization["Couldn't connect."],
                    latencyMs = 0L,
                    models = emptyList(),
                )
            }
            probe = outcome
            testing = false
            // **Un test réussi n'est pas la fin : c'est le début du mode « Simple ».** Onyx va
            // chercher le reste — modèle, voix, pont, langue — et **le dit**, y compris quand il
            // trouve moins que prévu : le silence était le défaut d'origine.
            if (outcome.ok) discover()
        }
    }

    /**
     * « Done » : conclut, ou dit en une phrase pourquoi il ne peut pas. Jamais un appui muet.
     */
    fun finish() {
        val refusal = doneRefusal(config, testing, probe)
        reason = refusal
        if (refusal == null) onDone()
    }

    // **Les cinq repliables de la page**, tous fermés à l'ouverture : c'est ce qui rend la première
    // ouverture sobre. Rien n'a disparu, tout est derrière une ligne, avec son chevron à droite.
    var addressHelpOpen by remember { mutableStateOf(false) }
    var accessOpen by remember { mutableStateOf(false) }
    var transportOpen by remember { mutableStateOf(false) }
    var discoverOpen by remember { mutableStateOf(false) }
    var featuresOpen by remember { mutableStateOf(false) }

    // Le mode « Simple » par défaut : le champ de la page suffit, puis la découverte. « Avancé »
    // ouvre le choix du transport et ses identifiants, repliés.
    var advanced by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        scope.launch {
            val clip = runCatching { clipboard.getClipEntry() }.getOrNull()?.clipData
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString().orEmpty()
            } else {
                ""
            }
            // Le problème du collage est nommé par `SetupPaste` : il dit **ce qui** a été collé et
            // **où** cela va, au lieu d'un simple « ce n'est pas un lien » (apport du lot, gardé).
            // Un lien accepté rend `null`.
            pasteProblem = SetupPaste.problemFor(text, onPasteCode(text))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            // La zone du système est prise par la racine : la dernière rangée du corps ne peut
            // plus être enterrée sous la barre de navigation ni sous la zone du geste d'accueil.
            // L'encart est lu à l'exécution (24 dp en gestes, 48 dp à trois boutons) — jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Set up Onyx",
                // « Skip » est une sortie : elle ferme l'écran et entre dans l'app, immédiatement,
                // quoi qu'il y ait dans les champs. Elle n'attend aucun test.
                onBack = onSkip,
                backLabel = "Skip",
                showBackChevron = false,
                action = "Done",
                onAction = { finish() },
                // **Le bouton en haut, « Copier »** : le lien de configuration de ce brouillon, à
                // coller sur un autre appareil. Il vit **dans la barre de titre**, à sa place, et
                // non dans le corps défilant — c'est ce que la capture du propriétaire montrait
                // passer à moitié sous l'en-tête.
                trailing = if (setupLink.isNotEmpty()) {
                    {
                        SetupLinkCopyButton(onClick = {
                            clipboard.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Onyx setup link", setupLink),
                            )
                            copiedLink = true
                        })
                    }
                } else {
                    null
                },
            )
            // La confirmation de copie, sous la barre : le « lien copié » que la référence affiche
            // en bandeau, et qui se retire tout seul.
            LaunchedEffect(copiedLink) {
                if (!copiedLink) return@LaunchedEffect
                kotlinx.coroutines.delay(2_000)
                copiedLink = false
            }
            copiedLink.takeIf { it }?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnyxIcon(Symbols.CheckmarkCircle, tint = theme.accent, size = 16.dp)
                    BasicText(
                        text = "Setup link copied.",
                        style = theme.footnote.copy(color = theme.accent),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // La phrase qui dit pourquoi « Done » n'a pas conclu, sous la barre (donc toujours
            // visible, sans défilement).
            reason?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.error.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnyxIcon(Symbols.XmarkOctagonFill, tint = theme.error, size = 16.dp)
                    BasicText(
                        text = message,
                        style = theme.footnote.copy(color = theme.error),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // **Le corps défile SOUS l'en-tête, jamais dedans.** Deux choses le garantissent :
        // `weight(1f)` — le corps prend la place qui RESTE, il ne peut donc pas recouvrir la
        // barre — et une marge de tête lue dans l'échelle de la maison, pour que la **première
        // rangée** (l'icône et le grand titre) garde son air au lieu de coller au filet de la barre
        // quand la liste remonte. C'est le défaut de la capture : la première rangée passait sous
        // la barre de titre.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = OnyxSpace.Base, bottom = 40.dp),
        ) {
            // **1.** L'icône, petite, **en haut à gauche** — puis le très grand titre et la phrase
            // qui rassure. Rien d'autre : c'est l'anatomie de la référence, et elle tient en trois
            // lignes, alignées à gauche.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OnyxSpace.ScreenMargin)
                    .padding(bottom = OnyxSpace.Hair),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(OnyxSpace.Snug),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_graphite),
                    contentDescription = null,
                    modifier = Modifier
                        .size(OnyxMarkSize)
                        .clip(RoundedCornerShape(OnyxMarkCorner)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair)) {
                    BasicText(
                        text = "Welcome to Onyx",
                        style = theme.largeTitle.copy(
                            color = theme.primaryText,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    BasicText(
                        text = "Connect to your Hermes gateway. Nothing is sent until you connect.",
                        style = theme.subheadline.copy(color = theme.secondaryText),
                    )
                }
            }

            // **2.** Le parcours principal, et **les trois façons** d'entrer l'adresse au même
            // endroit : la saisie (le champ), le QR, le collage du code. Le libellé au-dessus du
            // champ, le globe à gauche, « Tester » **dans** le champ, la ligne d'aide en dessous,
            // puis l'état du test.
            OnyxFormSection(
                footer = "One link sets up everything at once: server, key, model, voices, the Computer bridge and the language. Make one on a device that is already set up: Settings › Connection › Set up another device.",
            ) {
                SetupAddressField(
                    label = "Gateway address",
                    value = transportAddress(config),
                    placeholder = transportPlaceholder(config),
                    testing = testing,
                    onTest = ::runTest,
                    // L'adresse qui porte le jeton du serveur de voix **est** un secret : elle se
                    // masque comme la clé, jamais relue.
                    secret = SetupSuggestions.carriesSecret(transportAddress(config)),
                    // **« Coller » dans le champ : le geste que réclame le mode « Simple ».** La
                    // capsule est **toujours** là — une capsule qui n'apparaît que si le
                    // presse-papiers a été lu au bon instant est une capsule qui manque au mauvais
                    // moment (la lecture du presse-papiers est refusée à une appli qui n'a pas le
                    // focus). Un appui lit le presse-papiers, pose l'adresse **et la teste** ; si
                    // le presse-papiers ne porte pas d'adresse, l'écran le dit.
                    onPaste = {
                        scope.launch {
                            val raw = runCatching { setupClipboardText(clipboard) }.getOrDefault("")
                            val pasted = SetupSuggestions.addressFromClipboard(raw)
                            // L'échec s'écrit : `SetupPaste` nomme ce qui a été collé (apport du lot
                            // gardé, la capsule restant, elle, toujours là — apport de la chaîne).
                            pasteProblem = SetupPaste.problemFor(raw, pasted.isNotEmpty())
                            if (pasted.isNotEmpty()) {
                                onChange { withTransportAddress(it, pasted) }
                                discovered = null
                                runTest()
                            }
                        }
                    },
                    onValueChange = { value ->
                        onChange { withTransportAddress(it, value) }
                        discovered = null
                    },
                )
                // **La seconde moitié du mode « Simple » : la clé.** Sa définition, mot pour mot,
                // est « Adresse et clé. Onyx trouve le reste. » — la clé est donc un champ de
                // plein droit dès que « Simple » est choisi, entièrement masqué et copiable, sans
                // aucune icône œil. En « Avancé », la même clé se relit dans la section du transport
                // : c'est **le même `apiKey`**, jamais deux mémoires.
                if (!advanced) {
                    OnyxRowDivider()
                    SetupKeyField(
                        label = "Key",
                        value = config.apiKey,
                        placeholder = "Gateway key",
                        onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                    )
                    // Le serveur réclame cette clé **par son nom** : sans elle il répond
                    // « clé d'installation absente ou invalide » (401). On le dit ici, à côté du
                    // champ, au lieu de laisser l'utilisateur le découvrir en échec.
                    SetupHelpLine("The key your gateway asks for by name. Without it the server answers 401.")
                }
                SetupHelpLine("Where your dashboard runs: LAN, Tailscale or HTTPS.")
                // **Ce que la rangée d'adresse accepte**, dit sur sa propre ligne : la rangée du
                // dessous attend autre chose (un lien complet), et elles se ressemblaient.
                SetupHelpLine(SetupPaste.ADDRESS_ACCEPTS)
                probe?.let { result -> SetupVerdict(result) }
                // **Ce que le mode « Simple » propose.** Une adresse connue s'appuie, elle se pose
                // dans le champ : « ça propose rien » n'est plus vrai. Les quatre sources sont
                // nommées — le serveur en cours, le réseau du téléphone, la passerelle précédente,
                // les autres serveurs enregistrés.
                val offered = if (advanced) emptyList() else suggestions
                if (offered.isNotEmpty()) {
                    OnyxRowDivider()
                    offered.take(4).forEachIndexed { index, suggestion ->
                        if (index > 0) OnyxRowDivider()
                        SetupSuggestionRow(
                            address = suggestion.address,
                            source = suggestionLabel(suggestion.source),
                            onClick = {
                                onChange { withTransportAddress(it, suggestion.address) }
                                discovered = null
                            },
                        )
                    }
                }
                OnyxRowDivider()
                if (codeScannerSupported(context)) {
                    OnyxRow(onClick = onScanCode) {
                        OnyxIcon(Symbols.QrCodeViewfinder, tint = theme.accent, size = 18.dp)
                        BasicText(
                            text = "Scan a setup code",
                            style = theme.body.copy(color = theme.accent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OnyxRowDivider()
                }
                // « Paste a setup link » et son bouton « Paste » en capsule, comme la capsule de
                // collage de la référence. Toute la rangée colle aussi (comportement d'avant).
                OnyxRow(onClick = { pasteFromClipboard() }) {
                    OnyxIcon(Symbols.Link, tint = theme.accent, size = 18.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Hair),
                    ) {
                        BasicText(
                            text = "Paste a setup link",
                            style = theme.body.copy(color = theme.primaryText),
                        )
                        // **Ce que cette rangée accepte**, dit sous son titre : un lien COMPLET venu
                        // d'un autre appareil — pas une adresse (elle va dans le champ du dessus).
                        BasicText(
                            text = SetupPaste.LINK_ACCEPTS,
                            // Encre mesurée sur la surface du formulaire, comme les autres pieds
                            // de la page : le gris secondaire du système tombe sous le seuil.
                            style = theme.footnote.copy(
                                color = OnyxInkOn(theme.secondaryText, theme.formSurfaceColor, OnyxContrast.TEXT_MIN),
                            ),
                        )
                    }
                    SetupPasteButton(onClick = { pasteFromClipboard() })
                }
                pasteProblem?.let { problem ->
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(Symbols.XmarkOctagonFill, tint = theme.error, size = 17.dp)
                        BasicText(
                            // La phrase vient de SetupPaste : elle nomme ce qui a été collé et où
                            // le mettre, au lieu d'un « ce n'est pas un lien » sans suite.
                            text = problem,
                            style = theme.footnote.copy(color = theme.error),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // **3.** Les explications d'adressage, repliées : les trois cas réels, avec **ses**
            // exemples et **ses** adresses. C'était un mur de texte ; c'est une ligne.
            SetupDisclosure(
                title = "Which address do I use?",
                expanded = addressHelpOpen,
                onToggle = { addressHelpOpen = !addressHelpOpen },
            ) {
                SetupTopic(
                    title = "Same Wi-Fi / local network",
                    example = "http://192.168.1.20:9119",
                    detail = "The address of the machine that runs the dashboard, on your own network. Start the dashboard with --host 0.0.0.0 so it answers on the network, and not only on the machine itself.",
                )
                OnyxRowDivider()
                SetupTopic(
                    title = "Tailscale",
                    example = "http://100.64.0.1:9119",
                    detail = "A tailnet address (100.64.0.0/10), or a machine name such as machine.tailnet-name.ts.net. It works from anywhere, as long as Tailscale is up on both ends.",
                )
                OnyxRowDivider()
                SetupTopic(
                    title = "HTTPS",
                    example = "https://your-gateway.example.com",
                    detail = "Behind a reverse proxy. The proxy has to let the WebSockets through on /api/ws — without that, the reply is written but never streams.",
                )
            }

            // **4.** La protection d'accès, repliée : les deux champs du jeton de service, et la
            // note de chiffrement. Le secret est masqué **entièrement** et ne s'offre que par la
            // copie — c'est la règle déjà corrigée de la maison, gardée telle quelle.
            SetupDisclosure(
                title = "Behind Cloudflare Access?",
                expanded = accessOpen,
                onToggle = { accessOpen = !accessOpen },
            ) {
                SetupTopic(
                    title = "Cloudflare Access (optional)",
                    example = "",
                    detail = "If the dashboard sits behind Cloudflare Access instead of a tailnet, make a service token in Zero Trust. The pair is sent as CF-Access-Client-Id / -Secret on every request and WebSocket.",
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "Client ID",
                    value = config.accessID,
                    placeholder = "Client ID",
                    paste = true,
                    onValueChange = { value -> onChange { it.copy(accessID = value) } },
                )
                OnyxRowDivider()
                // Le secret : masqué, **jamais** offert par un œil, et copiable
                // (`OnyxFieldRow(secret = true)` : `PasswordVisualTransformation` + bouton copier).
                OnyxFieldRow(
                    label = "Client secret",
                    value = config.accessSecret,
                    placeholder = "Client secret",
                    secret = true,
                    onValueChange = { value -> onChange { it.copy(accessSecret = value) } },
                )
                SetupNote("Stored encrypted on this phone, and sent only to this address.")
            }

            // **5.** Le transport et ses identifiants, repliés : la question de la référence comme
            // titre de section, et la liste Simple / Avancé avec ses trois transports.
            SetupDisclosure(
                title = "Connect another way",
                expanded = transportOpen,
                onToggle = { transportOpen = !transportOpen },
                header = "How does Onyx reach it?",
                footer = if (advanced) {
                    "Set the transport by hand. Simple fills these in from the server."
                } else {
                    "Enter the server's address and its key. Onyx finds the model, the voices, the Computer bridge and the language on its own — leave anything it doesn't announce empty."
                },
            ) {
                // « Simple » et « Avancé » : la **même** liste à coche que le reste de l'écran,
                // aux mots du formulaire — pas un contrôle segmenté, qui n'existe pas ici.
                OnyxRow(onClick = { advanced = false }) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = "Simple",
                            style = theme.body.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
                        )
                        BasicText(
                            text = "Address and key. Onyx finds the rest.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                    if (!advanced) OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                }
                OnyxRowDivider()
                OnyxRow(onClick = { advanced = true }) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = "Advanced",
                            style = theme.body.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
                        )
                        BasicText(
                            text = "Choose the transport and fill everything by hand.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                        )
                    }
                    if (advanced) OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                }

                if (advanced) {
                    // Le choix du transport : la liste à coche de la référence, dans la **même**
                    // section que le mode.
                    BackendTransports.forEachIndexed { index, transport ->
                        OnyxRowDivider()
                        OnyxRow(onClick = { onChange { it.copy(transport = transport) } }) {
                            BasicText(
                                text = transportLabel(transport),
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                            if (config.transport == transport) {
                                OnyxIcon(Symbols.Checkmark, tint = theme.accent, size = 15.dp)
                            }
                        }
                    }
                }
            }

            // Les identifiants du transport retenu. L'**adresse** n'est pas redemandée : c'est celle
            // du champ, plus haut ([withTransportAddress] écrit dans le bon des deux champs).
            if (advanced && transportOpen) {
                when (config.transport) {
                    Transport.HERMES_SESSIONS -> OnyxFormSection(
                        header = "Hermes API server",
                        footer = "The gateway's API server and its API_SERVER_KEY. Sessions from every platform appear in Onyx.",
                    ) {
                        OnyxFieldRow(
                            label = "API key",
                            value = config.apiKey,
                            placeholder = "API key",
                            secret = true,
                            paste = true,
                            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                        )
                    }

                    Transport.HERMES_SERVE -> OnyxFormSection(
                        header = "Hermes Dashboard",
                        footer = "The same login as the Hermes Dashboard: its username and password, or Sign in with a browser where it uses Google or another provider. Adds tool approvals and slash commands.",
                    ) {
                        OnyxFieldRow(
                            label = "Username",
                            value = config.serveUsername,
                            placeholder = "Dashboard username",
                            onValueChange = { value -> onChange { it.copy(serveUsername = value) } },
                        )
                        OnyxRowDivider()
                        OnyxFieldRow(
                            label = "Password",
                            value = config.servePassword,
                            placeholder = "Dashboard password",
                            secret = true,
                            paste = true,
                            onValueChange = { value -> onChange { it.copy(servePassword = value) } },
                        )
                    }

                    Transport.CHAT_COMPLETIONS -> OnyxFormSection(
                        header = "OpenAI-compatible endpoint",
                        footer = "Straight to a model with no agent in between: llama.cpp, llama-swap, vLLM, Ollama, or a hosted provider.",
                    ) {
                        OnyxFieldRow(
                            label = "Model",
                            value = config.model,
                            placeholder = "Model name",
                            paste = true,
                            onValueChange = { value -> onChange { it.copy(model = value) } },
                        )
                        OnyxRowDivider()
                        OnyxFieldRow(
                            label = "API key",
                            value = config.apiKey,
                            placeholder = "None",
                            secret = true,
                            paste = true,
                            onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                        )
                    }
                }
            }

            // **6.** La découverte — le modèle, les voix, le pont « Ordinateur » et la langue
            // arrivent d'eux-mêmes. C'était un bloc au milieu de la page ; c'est une étape repliée
            // qui **dit** ce qu'elle a trouvé.
            SetupDisclosure(
                title = "What else is on the server?",
                expanded = discoverOpen,
                onToggle = { discoverOpen = !discoverOpen },
            ) {
                OnyxRow(
                    modifier = Modifier.alpha(if (discovering) 0.35f else 1f),
                    onClick = if (discovering) null else ({ discover() }),
                ) {
                    if (discovering) SetupSpinner()
                    BasicText(
                        text = if (discovering) "Finding the rest…" else "Find the rest",
                        style = theme.body.copy(
                            color = if (config.baseUrl.isNotEmpty()) theme.accent else theme.tertiaryText,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OnyxIcon(Symbols.MagnifyingGlass, tint = theme.accent, size = 15.dp)
                }

                if (discovering) {
                    OnyxRowDivider()
                    OnyxRow {
                        BasicText(
                            text = "Finding the rest…",
                            style = theme.body.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                        SetupSpinner()
                    }
                }

                discovered?.let { found ->
                    if (found.hasExtras || found.model.isNotEmpty()) {
                        if (found.model.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow("Model", found.model)
                        }
                        if (found.voices.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow("Voices", found.voices)
                        }
                        if (found.bridge.isNotEmpty()) {
                            OnyxRowDivider()
                            // « token included » est un libellé : il passe par le dictionnaire
                            // (la valeur, elle, reste verbatim).
                            val note = if (found.bridgeToken.isNotEmpty()) {
                                " · " + OnyxLocalization["token included"]
                            } else {
                                ""
                            }
                            SetupLabeledRow("Computer", found.bridge + note)
                        }
                        if (found.language.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow("Language", found.language)
                        }
                    }
                }

                discoverProblem?.let { problem ->
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(Symbols.XmarkOctagonFill, tint = theme.error, size = 17.dp)
                        BasicText(
                            text = problem,
                            style = theme.footnote.copy(color = theme.error),
                        )
                    }
                }
            }

            // **7.** Ce que l'app fait — les trois promesses de la référence, repliées elles aussi :
            // elles rassurent, elles n'ont pas à occuper le premier écran.
            SetupDisclosure(
                title = "What does Onyx do?",
                expanded = featuresOpen,
                onToggle = { featuresOpen = !featuresOpen },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SetupFeature(
                        Symbols.ServerRack,
                        "Your own server",
                        "Works with Hermes, the open-source agent you run on your own machine, or any OpenAI-compatible model server.",
                    )
                    SetupFeature(
                        Symbols.LockShield,
                        "Private by design",
                        "Your conversations go only to the server you enter below.",
                    )
                    SetupFeature(
                        Symbols.Waveform,
                        "Speech stays on device",
                        "Your voice is recognized on this device, not in the cloud.",
                    )
                }
            }
        }
    }
}

/** Une valeur trouvée par la découverte : en lecture seule, à côté de son libellé. */
@Composable
private fun SetupLabeledRow(label: String, value: String) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        // Le libellé garde sa largeur naturelle : une valeur longue (l'adresse des voix) ne doit
        // pas le comprimer jusqu'à l'écrire une lettre par ligne.
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 1,
            softWrap = false,
        )
        IdentifierText(
            text = value,
            style = theme.body.copy(color = theme.secondaryText),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Le bouton « Paste » en capsule, comme la capsule de collage de la référence. */
@Composable
private fun SetupPasteButton(onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(theme.accent)
            .onyxClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Paste",
            style = theme.subheadline.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun SetupFeature(symbol: Int, title: String, detail: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(width = 30.dp, height = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = symbol, tint = theme.accent, size = 20.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(text = title, style = theme.subheadline.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold))
            BasicText(text = detail, style = theme.footnote.copy(color = theme.secondaryText))
        }
    }
}
