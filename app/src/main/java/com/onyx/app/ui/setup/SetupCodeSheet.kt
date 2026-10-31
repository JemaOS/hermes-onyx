package com.onyx.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.SetupCode
import com.onyx.app.data.model.SetupCodeOffer
import com.onyx.app.data.model.SetupCodeParseError
import com.onyx.app.data.model.SetupCodePlan
import com.onyx.app.data.model.SetupServerAction
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.data.net.ConnectionTester
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.onyx.app.ui.design.IdentifierText

sealed interface SetupCodeFinish {
    data object Cancelled : SetupCodeFinish
    data class Saved(val connected: Boolean) : SetupCodeFinish
}

data class SetupCodeReceipt(
    val addedServerId: String,
    val previousServerId: String,
)

private sealed interface SetupCodeStage {
    data object Review : SetupCodeStage
    data object Testing : SetupCodeStage
    data class Tested(val outcome: ConnectionProbe, val saved: Boolean = true) : SetupCodeStage
}

/**
 * La confirmation de `SetupCodeSheet` : le contenu non secret, ce qui changera, puis
 * installation, vraie sonde, nouvel essai, édition et retrait du serveur ajouté en cas d'échec.
 */
@Composable
fun SetupCodeSheet(
    offer: SetupCodeOffer,
    activeServer: ServerConfig,
    servers: List<ServerConfig>,
    activeServerIsUnused: Boolean,
    offersEditing: Boolean = true,
    newServerId: () -> String,
    onInstall: suspend (ServerConfig, Boolean) -> Unit,
    onRemoveAdded: suspend (String, String) -> Unit,
    onEditConnection: (ServerConfig) -> Unit,
    onFinish: (SetupCodeFinish) -> Unit,
    /** Ce que le lien applique **au-delà** du serveur : voix, pont « Ordinateur », langue. */
    onApplyExtras: suspend (SetupCode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()
    val code = offer.code
    val plan = remember(offer) {
        code?.plan(activeServer, servers, activeServerIsUnused)
    }
    var stage by remember(offer) { mutableStateOf<SetupCodeStage>(SetupCodeStage.Review) }
    var installed by remember(offer) { mutableStateOf<ServerConfig?>(null) }
    var receipt by remember(offer) { mutableStateOf<SetupCodeReceipt?>(null) }
    /** Le défilement du contenu : le résultat du test doit pouvoir amener son pied de page à l'écran. */
    val contentScroll = rememberScrollState()

    fun close(finish: SetupCodeFinish) = onFinish(finish)

    suspend fun probe(config: ServerConfig): ConnectionProbe = try {
        ConnectionTester.current(config)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ConnectionProbe(false, error.message ?: OnyxLocalization["Couldn't connect."], 0, emptyList())
    }

    fun test(config: ServerConfig) {
        if (stage == SetupCodeStage.Testing) return
        stage = SetupCodeStage.Testing
        scope.launch {
            stage = SetupCodeStage.Tested(probe(config))
        }
    }

    fun save() {
        val currentCode = code ?: return
        val currentPlan = plan ?: return
        if (stage != SetupCodeStage.Review) return
        stage = SetupCodeStage.Testing
        scope.launch {
            val target = currentCode.configurationForInstall(currentPlan, activeServer, newServerId())
            try {
                onInstall(target, currentPlan.server == SetupServerAction.ADD)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stage = SetupCodeStage.Tested(
                    ConnectionProbe(
                        false,
                        error.message ?: OnyxLocalization["Couldn't save the setup code."],
                        0,
                        emptyList(),
                    ),
                    saved = false,
                )
                return@launch
            }
            installed = target
            // Le reste du lien « tout en un » — voix, pont, langue — s'applique **après** le
            // serveur, et ne l'empêche jamais : l'un d'eux peut manquer sans que rien ne casse.
            runCatching { onApplyExtras(currentCode) }
            receipt = if (currentPlan.server == SetupServerAction.ADD) {
                SetupCodeReceipt(target.id, activeServer.id)
            } else {
                null
            }
            stage = SetupCodeStage.Tested(probe(target))
        }
    }

    BackHandler {
        if (stage == SetupCodeStage.Review) close(SetupCodeFinish.Cancelled)
        // Pendant le test et après son résultat, seul le bouton visible ferme la feuille.
    }

    // Le pied de page du test (« Le code est enregistré. Si l'adresse n'est accessible que sur votre
    // réseau domestique ou VPN… ») arrive **sous** la dernière rangée : quand le résultat tombe, on
    // amène le bas de la feuille à l'écran, pour qu'il soit lu en entier au lieu de rester coupé par
    // le bord. `snapshotFlow` attend que la mise en page ait posé la nouvelle hauteur (le pied de
    // page vient d'être ajouté) avant de défiler.
    LaunchedEffect(stage) {
        if (stage !is SetupCodeStage.Tested) return@LaunchedEffect
        snapshotFlow { contentScroll.maxValue }.first { it > 0 }
        contentScroll.animateScrollTo(contentScroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        val tested = stage as? SetupCodeStage.Tested
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(
                title = "Setup code",
                onBack = null,
                action = if (tested != null) "Done" else "Cancel",
                onAction = {
                    if (tested != null) {
                        close(SetupCodeFinish.Saved(tested.outcome.ok))
                    } else if (stage != SetupCodeStage.Testing) {
                        close(SetupCodeFinish.Cancelled)
                    }
                },
                actionEnabled = stage != SetupCodeStage.Testing,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
                .verticalScroll(contentScroll)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                if (code == null || plan == null) {
                    OnyxFormSection {
                        OnyxRow {
                            OnyxIcon(Symbols.ExclamationTriangleFill, tint = theme.warning, size = 19.dp)
                            BasicText(
                                text = offer.message.ifEmpty {
                                    if (offer.error == SetupCodeParseError.NONE) {
                                        "This setup code has no server address in it."
                                    } else {
                                        offer.message
                                    }
                                },
                                style = theme.body.copy(color = theme.warning),
                            )
                        }
                    }
                    return@Column
                }

                if (code.hasServer) {
                    OnyxFormSection(
                        header = "Server",
                        footer = if (code.modelURL.isEmpty()) setupFooter(code, plan) else null,
                    ) {
                        if (code.name.isNotEmpty()) {
                            SetupLabeledRow("Name", code.name)
                            OnyxRowDivider()
                        }
                        if (code.dashboardURL.isNotEmpty()) {
                            SetupAddressRow(
                                title = "Hermes Dashboard",
                                address = code.dashboardURL,
                                notes = listOfNotNull(
                                    code.dashboardUser.takeIf { it.isNotEmpty() }?.let { "user $it" },
                                    if (code.dashboardPassword.isEmpty()) "no password" else "password included",
                                ),
                                used = plan.transport == Transport.HERMES_SERVE ||
                                    (plan.transport == Transport.HERMES_SESSIONS && code.apiURL.isEmpty()),
                            )
                            OnyxRowDivider()
                        }
                        if (code.apiURL.isNotEmpty()) {
                            SetupAddressRow(
                                title = "Hermes API",
                                address = code.apiURL,
                                notes = listOf(
                                    if (code.apiKey.isEmpty() && code.profileKey.isEmpty()) "no key" else "key included",
                                ),
                                used = plan.transport == Transport.HERMES_SESSIONS,
                            )
                            if (code.profile.isNotEmpty()) OnyxRowDivider()
                        }
                        if (code.profile.isNotEmpty()) SetupLabeledRow("Profile", code.profile)
                        if (code.accessID.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow(
                                "Cloudflare Access",
                                if (code.accessSecret.isEmpty()) "Client ID only" else "Service token included",
                                localizeValue = true,
                            )
                        }
                        if (code.headers.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow(
                                if (code.headers.size == 1) "Custom header" else "Custom headers",
                                code.headers.joinToString(", ") { it.name },
                            )
                        }
                    }
                }

                if (code.modelURL.isNotEmpty()) {
                    OnyxFormSection(
                        header = "OpenAI-compatible endpoint",
                        footer = setupFooter(code, plan),
                    ) {
                        SetupAddressRow(
                            title = "Address",
                            address = code.modelURL,
                            notes = listOfNotNull(if (code.modelKey.isEmpty()) null else "key included"),
                            used = plan.transport == Transport.CHAT_COMPLETIONS,
                        )
                        if (code.model.isNotEmpty()) {
                            OnyxRowDivider()
                            SetupLabeledRow("Model", code.model)
                        }
                    }
                }

                // Ce qu'un lien « tout en un » ajoute au serveur : le récapitulatif le montre,
                // sans jamais afficher un secret.
                if (code.hasExtras) {
                    OnyxFormSection(header = "Also set up") {
                        if (code.voicesURL.isNotEmpty()) {
                            SetupAddressRow(
                                title = "Voice server",
                                address = code.voicesURL,
                                notes = listOf("Kokoro-compatible"),
                                used = true,
                            )
                        }
                        if (code.bridgeURL.isNotEmpty()) {
                            if (code.voicesURL.isNotEmpty()) OnyxRowDivider()
                            SetupAddressRow(
                                title = "Computer bridge",
                                address = code.bridgeURL,
                                notes = listOf(if (code.bridgeToken.isEmpty()) "no token" else "token included"),
                                used = true,
                            )
                        }
                        if (code.language.isNotEmpty()) {
                            if (code.voicesURL.isNotEmpty() || code.bridgeURL.isNotEmpty()) OnyxRowDivider()
                            SetupLabeledRow("Language", code.language)
                        }
                    }
                }

                OnyxFormSection(
                    footer = (stage as? SetupCodeStage.Tested)?.let { result ->
                        if (!result.outcome.ok && result.saved) {
                            if (code.hasSecrets) {
                                "The code is saved. If the address is only reachable on your home network or VPN, connect to that and test again."
                            } else {
                                "The code is saved, but it came without a password or key. " +
                                    if (offersEditing) "Add it under Edit connection." else "Tap Done and add it in the form."
                            }
                        } else {
                            null
                        }
                    },
                ) {
                    when (val current = stage) {
                        SetupCodeStage.Review -> SetupActionRow(
                            label = if (plan.server == SetupServerAction.ADD) "Add server" else "Set up",
                            tint = theme.accent,
                            onClick = ::save,
                        )
                        SetupCodeStage.Testing -> OnyxRow {
                            BasicText(
                                text = "Testing the connection…",
                                style = theme.body.copy(color = theme.secondaryText),
                                modifier = Modifier.weight(1f),
                            )
                            SetupSpinner()
                        }
                        is SetupCodeStage.Tested -> {
                            OnyxRow {
                                OnyxIcon(
                                    if (current.outcome.ok) Symbols.CheckmarkCircle else Symbols.XmarkOctagonFill,
                                    tint = if (current.outcome.ok) theme.success else theme.error,
                                    size = 19.dp,
                                )
                                BasicText(
                                    text = current.outcome.message,
                                    style = theme.footnote.copy(
                                        color = if (current.outcome.ok) theme.success else theme.error,
                                    ),
                                )
                            }
                            if (!current.outcome.ok) {
                                OnyxRowDivider()
                                SetupActionRow(
                                    label = if (current.saved) "Test again" else "Try Again",
                                    tint = theme.accent,
                                    onClick = {
                                        if (current.saved) installed?.let(::test) else {
                                            stage = SetupCodeStage.Review
                                            save()
                                        }
                                    },
                                )
                                if (current.saved && offersEditing) {
                                    OnyxRowDivider()
                                    SetupActionRow(
                                        label = "Edit connection…",
                                        tint = theme.accent,
                                        onClick = { installed?.let(onEditConnection) },
                                    )
                                }
                                if (current.saved && receipt != null) {
                                    OnyxRowDivider()
                                    SetupActionRow(
                                        label = "Remove this server",
                                        tint = theme.error,
                                        onClick = {
                                            val added = receipt ?: return@SetupActionRow
                                            scope.launch {
                                                onRemoveAdded(added.addedServerId, added.previousServerId)
                                                close(SetupCodeFinish.Cancelled)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupLabeledRow(label: String, value: String, localizeValue: Boolean = false) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(text = label, style = theme.body.copy(color = theme.primaryText))
        BasicText(
            text = value,
            localize = localizeValue,
            style = theme.body.copy(color = theme.secondaryText),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * L'écart horizontal entre un titre de rangée et son badge « Onyx utilisera cette connexion ».
 * C'est une **marge de séparation nette** : le badge ne touche jamais le titre.
 */
private val UsedBadgeGap = 12.dp

/** L'écart vertical quand le badge n'a pas la place sur la ligne du titre et passe dessous. */
private val UsedBadgeStackGap = 3.dp

@Composable
private fun SetupAddressRow(
    title: String,
    address: String,
    notes: List<String>,
    used: Boolean,
) {
    val theme = LocalOnyxTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Le badge bleu suit **une seule règle, la même dans toutes les rangées** : il se pose à la
        // droite du titre, sur **une seule ligne**, à [UsedBadgeGap] de lui. Quand la place manque
        // (un écran de 320 dp à police 1,3×, où « Onyx utilisera cette connexion » mesure ~225 dp
        // pour ~256 dp de rangée), il **passe entier à la ligne suivante**, aligné sous le titre —
        // il ne se replie jamais sur deux lignes, ne se pose jamais sur le titre, et garde le même
        // écart d'une rangée à l'autre. `FlowRow` est exactement cette règle : la ligne se remplit,
        // et ce qui ne tient pas descend d'un bloc. Le `padding(end)` du titre (et non un
        // `padding(start)` du badge) fait la marge : quand le badge passe dessous, il reste aligné
        // sur le titre au lieu d'être rentré de douze points.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(UsedBadgeStackGap),
            // Titre et badge sont de tailles différentes (corps et note) : centrés l'un sur
            // l'autre, comme la rangée d'origine, plutôt qu'alignés par le haut.
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title,
                style = theme.body.copy(color = theme.primaryText),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = UsedBadgeGap),
            )
            if (used) {
                BasicText(
                    text = "Onyx will use this",
                    style = theme.footnote.copy(color = theme.accent),
                    // **Une ligne, et jamais deux** : la règle du badge (mesurée) est « entier sur
                    // une ligne, sinon rejeté *entier* à la ligne suivante » par le `FlowRow` de la
                    // rangée. Le replier le ferait remonter sur le titre — le défaut d'origine.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IdentifierText(
            text = address,
            style = theme.subheadline.copy(color = theme.secondaryText, fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = 3.dp),
        )
        if (notes.isNotEmpty()) {
            BasicText(
                text = notes.joinToString(" · "),
                style = theme.footnote.copy(color = theme.secondaryText),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SetupActionRow(label: String, tint: Color, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    OnyxRow(onClick = onClick) {
        BasicText(
            text = label,
            style = theme.body.copy(color = tint, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun SetupSpinner() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "setup-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "setup-spinner-angle",
    )
    Canvas(modifier = Modifier.size(19.dp).rotate(angle)) {
        drawArc(
            color = theme.secondaryText,
            startAngle = 20f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun setupFooter(code: SetupCode, plan: SetupCodePlan): String {
    val lines = mutableListOf<String>()
    when (plan.server) {
        SetupServerAction.ADD -> {
            lines += OnyxLocalization[
                "This is added as a new server and Onyx switches to it, which starts a new conversation. Your other servers stay as they are.",
            ]
            plan.sameAs?.let {
                lines += OnyxLocalization.format("You already have a server at this address (%1\$s). This adds another.", it)
            }
        }
        SetupServerAction.FILL -> lines += OnyxLocalization["This sets up your server's connection."]
        SetupServerAction.NONE -> Unit
    }
    plan.replacesModelURL?.let { replaced ->
        val host = runCatching { java.net.URI(replaced).host }.getOrNull() ?: replaced
        lines += OnyxLocalization.format(
            "The endpoint replaces the one you have now (%1\$s), and that one's key is forgotten.",
            host,
        )
    }
    lines += OnyxLocalization[
        "Only use a code from someone you trust: what you type and say is sent to these addresses.",
    ]
    return lines.joinToString("\n\n")
}
