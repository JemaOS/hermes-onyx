package com.onyx.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.CustomHeader
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServerCapability
import com.onyx.app.data.net.ServerProbe
import com.onyx.app.data.net.Support
import com.onyx.app.ui.design.OnyxFieldRow
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * « Connection details », port de `ConnectionDetailsView` (Echo/Views/ConnectionSettings:9).
 *
 * Toutes les adresses, clés et identifiants d'une connexion sur une page à part, hors du premier
 * écran des Réglages : l'adresse et la clé de l'API Hermes, la connexion au Dashboard, Cloudflare
 * Access, et les en-têtes d'un proxy inverse.
 *
 * Une adaptation assumée : côté référence ces réglages vivent dans le Keychain d'un seul appareil et
 * valent pour tous les serveurs ; ici ils appartiennent au serveur actif ([server]), comme tout le
 * reste de la configuration Android. Chaque champ écrit dans le dépôt des serveurs dès la saisie —
 * la clé API part dans le Keystore, jamais en clair dans les préférences.
 */
@Composable
fun ConnectionDetailsScreen(
    server: ServerConfig,
    onSave: (ServerConfig) -> Unit,
    onBack: () -> Unit,
    /**
     * La sonde de capacités — ce que **ce** serveur sait faire.
     *
     * `null` : la section n'est pas dessinée du tout (aperçu, test, écran sans réseau). C'est le
     * motif d'entrée conditionnelle de la maison : une entrée absente plutôt qu'un tap mort.
     */
    probe: ServerProbe? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // Le brouillon est local : chaque frappe remonte au dépôt et l'écran repart de sa valeur.
    var draft by remember(server.id) { mutableStateOf(server) }
    var headerName by remember { mutableStateOf("") }
    var headerValue by remember { mutableStateOf("") }
    var headerProblem by remember { mutableStateOf<String?>(null) }
    // `null` = on n'a pas encore la réponse : la section dit « checking… », elle ne bloque rien.
    var capabilities by remember(server.id) { mutableStateOf<Map<ServerCapability, Support>?>(null) }

    fun edit(transform: (ServerConfig) -> ServerConfig) {
        draft = transform(draft)
        onSave(draft)
    }

    // Une sonde ne bloque **jamais** l'ouverture d'un écran : elle part sur la portée de
    // composition, et la section affiche « Checking… » jusque-là. Changer d'adresse, de compte ou
    // de serveur relance la sonde, parce que ce n'est plus la même machine en face.
    LaunchedEffect(probe, draft.serveBaseUrl, draft.serveUsername, draft.servePassword) {
        val sonde = probe ?: return@LaunchedEffect
        capabilities = null
        capabilities = sonde.snapshot()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            // La zone du système est prise par la racine : la dernière rangée du corps ne peut
            // plus être enterrée sous la barre de navigation. Encart lu à l'exécution, jamais
            // une valeur en dur qui ne vaut que pour un écran.
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Connection details", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            // `GatewayKeySettings` : l'adresse de l'API et sa clé.
            OnyxFormSection(
                header = "Hermes API",
                footer = if (draft.apiKey.isNotBlank()) {
                    "A key is stored in this phone's Keystore. It is never shown again."
                } else {
                    "Paste the scoped API_SERVER_KEY. It is stored in this phone's Keystore only."
                },
            ) {
                OnyxFieldRow(
                    label = "Address",
                    value = draft.baseUrl,
                    placeholder = "https://your-gateway:8642",
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { value -> edit { it.copy(baseUrl = value) } },
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "API key",
                    value = draft.apiKey,
                    placeholder = "API key",
                    secret = true,
                    onValueChange = { value -> edit { it.copy(apiKey = value) } },
                )
            }

            // `ServeLoginSettings` : le Dashboard, sa connexion, et son état.
            OnyxFormSection(
                header = "Hermes Dashboard",
                footer = "The same login as the Hermes Dashboard: its username and password. " +
                    "It unlocks the context and memory files, and the files the agent sends.",
            ) {
                OnyxFieldRow(
                    label = "Address",
                    value = draft.serveBaseUrl,
                    placeholder = "http://your-onyx:9119",
                    keyboardType = KeyboardType.Uri,
                    onValueChange = { value -> edit { it.copy(serveBaseUrl = value) } },
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "Username",
                    value = draft.serveUsername,
                    placeholder = "Dashboard username",
                    onValueChange = { value -> edit { it.copy(serveUsername = value) } },
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "Password",
                    value = draft.servePassword,
                    placeholder = "Dashboard password",
                    secret = true,
                    onValueChange = { value -> edit { it.copy(servePassword = value) } },
                )
                OnyxRowDivider()
                OnyxRow {
                    BasicText(
                        text = "Connection",
                        style = theme.body.copy(color = theme.primaryText),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        // Le `stateLabel` de la référence dit l'état du WebSocket ; ce port ouvre une
                        // session par appel et n'en tient donc aucun : la ligne dit ce qui est
                        // configuré, pas un état qu'elle ne peut pas connaître.
                        text = if (draft.hasServeCredentials) "configured" else "not connected",
                        style = theme.body.copy(color = theme.secondaryText),
                    )
                }
            }

            // Les capacités du serveur : ce qu'il sait faire, dit avant qu'un écran ne l'exige.
            // La section n'existe pas quand aucune sonde n'est fournie — un aperçu n'en dessine
            // pas les lignes, et un serveur d'une autre version n'ouvre pas un écran vide.
            if (probe != null) {
                OnyxFormSection(
                    header = "Server capabilities",
                    footer = "What this server can do. A capability it does not have is not " +
                        "drawn anywhere in the app, rather than shown empty.",
                ) {
                    val known = capabilities
                    ServerCapability.entries.forEachIndexed { index, capability ->
                        if (index > 0) OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = capabilityLabel(capability),
                                style = theme.body.copy(color = theme.primaryText),
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(
                                text = supportLabel(known?.get(capability)),
                                style = theme.callout.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }

            // `CloudflareSettings` : le jeton de service de Zero Trust.
            OnyxFormSection(
                header = "Cloudflare Access (optional)",
                footer = "If the Hermes Dashboard sits behind Cloudflare Access instead of a " +
                    "tailnet, create a service token in Zero Trust and paste its ID and secret. " +
                    "Sent as CF-Access-Client-Id / -Secret on every request and WebSocket.",
            ) {
                OnyxFieldRow(
                    label = "Client ID",
                    value = draft.accessID,
                    placeholder = "Client ID",
                    onValueChange = { value -> edit { it.copy(accessID = value) } },
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "Client secret",
                    value = draft.accessSecret,
                    placeholder = "Client secret",
                    secret = true,
                    onValueChange = { value -> edit { it.copy(accessSecret = value) } },
                )
            }

            // `CustomHeadersSettings` : les en-têtes du proxy de l'utilisateur.
            OnyxFormSection(
                header = "Custom headers (optional)",
                footer = "If a reverse proxy in front of your Hermes server asks for a header of " +
                    "its own, add it here. It is sent with every request to this server and kept " +
                    "in the Keystore. Onyx's own headers, such as the API key, take precedence.",
            ) {
                draft.customHeaders.forEachIndexed { index, header ->
                    if (index > 0) OnyxRowDivider()
                    OnyxRow {
                        BasicText(
                            text = header.name,
                            localize = false,
                            style = theme.body.copy(color = theme.primaryText),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = "Remove",
                            style = theme.callout.copy(color = theme.error),
                            modifier = Modifier.onyxClickable {
                                edit { it.copy(customHeaders = it.customHeaders.filterNot { held -> held.id == header.id }) }
                            },
                        )
                    }
                }
                if (draft.customHeaders.isNotEmpty()) OnyxRowDivider()
                OnyxFieldRow(
                    label = "Header name",
                    value = headerName,
                    placeholder = "Header name",
                    onValueChange = { headerName = it },
                )
                OnyxRowDivider()
                OnyxFieldRow(
                    label = "Value",
                    value = headerValue,
                    placeholder = "Value",
                    secret = true,
                    onValueChange = { headerValue = it },
                )
                if (headerName.isNotEmpty() || headerValue.isNotEmpty()) {
                    OnyxRowDivider()
                    OnyxRow(onClick = {
                        val fresh = CustomHeader(headerName, headerValue)
                        headerProblem = when {
                            headerName.trim().isEmpty() || headerValue.trim().isEmpty() -> "Give the header a name and a value."
                            !fresh.isUsable -> "That name can't be used for a custom header."
                            draft.customHeaders.any { it.id == fresh.id } -> "That header is already there."
                            else -> null
                        }
                        if (headerProblem == null) {
                            edit { it.copy(customHeaders = it.customHeaders + fresh) }
                            headerName = ""
                            headerValue = ""
                        }
                    }) {
                        OnyxIcon(Symbols.Plus, tint = theme.accent, size = 17.dp)
                        BasicText(
                            text = "Add header",
                            style = theme.callout.copy(color = theme.accent),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                headerProblem?.let { problem ->
                    OnyxRowDivider()
                    OnyxRow {
                        OnyxIcon(Symbols.XmarkOctagonFill, tint = theme.error, size = 16.dp)
                        BasicText(
                            text = problem,
                            localize = false,
                            style = theme.footnote.copy(color = theme.error),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Le mot d'une capacité dans la liste : le **rôle** qu'elle joue pour la personne, jamais le nom
 * de la route qui la sert. Une ligne « api/vault » ne dirait rien ; « Secrets vault » si.
 */
fun capabilityLabel(capability: ServerCapability): String = when (capability) {
    ServerCapability.VAULT -> "Secrets vault"
    ServerCapability.RECORD -> "Teach a task"
    ServerCapability.HANDOFF -> "Room handoff"
}

/**
 * Ce que la ligne dit d'une capacité.
 *
 * `null` : la sonde n'a pas encore répondu — on l'écrit, on ne laisse pas la ligne muette.
 * [Support.UNKNOWN] n'est **pas** un oui : une décision le traite comme un non
 * (`Support.isUsable`), mais l'écran, lui, dit la vérité — « not confirmed » plutôt que de
 * promettre une fonction que le serveur n'a pas prouvée.
 */
fun supportLabel(support: Support?): String = when (support) {
    null -> "Checking…"
    Support.YES -> "Available"
    Support.NO -> "Not on this server"
    Support.UNKNOWN -> "Not confirmed"
}
