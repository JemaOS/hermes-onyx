package com.onyx.app.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.net.VaultApi
import com.onyx.app.data.net.VaultSecret
import com.onyx.app.data.net.humanFailure
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.capabilities.EmptyLine
import com.onyx.app.ui.capabilities.FailureBanner
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.design.ContentPill
import com.onyx.app.ui.design.ContentRow
import com.onyx.app.ui.design.ContentRowDivider
import com.onyx.app.ui.design.HouseSheet
import com.onyx.app.ui.design.HouseSheetRow
import com.onyx.app.ui.design.OnyxEditableValue
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.PillSize
import com.onyx.app.ui.design.PillVariant
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * **Les trois états d'une lecture du coffre** — et l'état d'erreur n'est jamais un écran vide.
 *
 * [LOADING] tant que la route n'a rien rendu, [ERROR] quand elle a refusé ou manqué (le message et
 * son « Réessayer » restent seuls), [EMPTY] quand le serveur a répondu « aucun secret », [LIST]
 * sinon. L'ordre compte : une erreur prime sur une liste absente, parce qu'une route en 404 laisse
 * [secrets] à `null` et qu'afficher un squelette de chargement sous un message d'échec dirait
 * « ça arrive » pour une lecture déjà finie.
 */
internal enum class VaultView { LOADING, ERROR, EMPTY, LIST }

/** L'état d'affichage, à partir de ce qu'on a et de ce qu'on a raté. */
internal fun vaultView(secrets: List<VaultSecret>?, problem: String?): VaultView = when {
    problem != null -> VaultView.ERROR
    secrets == null -> VaultView.LOADING
    secrets.isEmpty() -> VaultView.EMPTY
    else -> VaultView.LIST
}

/**
 * La ligne secondaire d'un secret : **empreinte**, **date lisible**, puis « utilisé par ».
 *
 * L'ordre n'est pas décoratif : la rangée n'a que deux lignes de sous-titre et la pastille
 * « Révoquer » mange la moitié de la largeur. Avec la date en dernier, elle était coupée
 * (« 3 nov.... ») — la seule information qui dit *quand* on a écrit le secret disparaissait la
 * première. Empreinte et date passent donc devant, et l'usage ferme la ligne : c'est lui qu'on
 * peut perdre à l'affichage, pas la date.
 *
 * L'usage vide ne s'écrit plus : « utilisé par aucune tâche » occupait 26 caractères pour ne rien
 * dire, et poussait la date hors de l'écran. Une donnée absente ne s'écrit pas.
 */
internal fun vaultSecretDetail(
    secret: VaultSecret,
    locale: Locale = OnyxLanguage.preferred,
): String {
    val parts = mutableListOf<String>()
    if (secret.fingerprint.isNotBlank()) {
        parts += OnyxLocalization.format("Fingerprint %1\$s", secret.fingerprint, locale = locale)
    }
    vaultDateLabel(secret.updatedAt, locale)?.let { parts += it }
    if (secret.usedBy.isNotEmpty()) {
        parts += OnyxLocalization.format("Used by %1\$s", secret.usedBy.joinToString(", "), locale = locale)
    }
    return parts.joinToString(" · ")
}

/**
 * **La date du serveur, rendue lisible** — jamais la chaîne ISO brute.
 *
 * Le greffon écrit `2026-11-03T12:34:56Z` ; l'écran dit « 3 nov. 2026 ». Une chaîne qui n'est pas
 * un instant rend `null` : la ligne l'omet plutôt que d'afficher une donnée de transport (c'est le
 * défaut relevé sur les tâches planifiées, où des millisecondes avaient fui jusqu'au sous-titre).
 *
 * Les mois sont une **table**, pas un `DateTimeFormatter` : la forme courte d'un formateur dépend
 * des données de locale de l'appareil, alors que la table donne exactement les deux langues de
 * l'app (même règle que [com.onyx.app.data.model.serve.shortDate]).
 */
internal fun vaultDateLabel(raw: String?, locale: Locale = OnyxLanguage.preferred): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val instant = runCatching { Instant.parse(text) }.getOrNull() ?: return null
    val date = runCatching { instant.atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() ?: return null
    val french = locale.language.equals("fr", ignoreCase = true)
    val month = (if (french) MONTHS_FRENCH else MONTHS_ENGLISH).getOrNull(date.monthValue - 1) ?: return null
    val day = if (french && date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
    return "$day $month ${date.year}"
}

private val MONTHS_ENGLISH = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val MONTHS_FRENCH = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.",
)

/** Le message d'un échec : la route absente est nommée, le reste vient du transport. */
private const val MISSING_ROUTE = "This Hermes does not serve the secrets vault route."

/**
 * **Le Coffre** — les secrets d'un profil : les lister, en écrire un, en révoquer un.
 *
 * Trois règles, et aucune n'est décorative :
 *
 * 1. **Aucune valeur n'est jamais montrée.** La liste ne rend que le nom, l'empreinte, l'usage et la
 *    date ; le champ d'écriture est masqué ([OnyxEditableValue] avec `secret = true`), et il est
 *    **vidé** dès que le serveur a accepté — la valeur saisie ne revient pas à l'écran, pas même
 *    sous forme d'un « aperçu ». Aucun journal de ce fichier ne l'écrit.
 * 2. **Un écran muet n'existe pas.** Un chargement dit qu'il charge, une route absente (404) le dit
 *    avec son « Réessayer », une liste vide le dit et renvoie au formulaire. Jamais une page blanche
 *    sans un mot.
 * 3. **Rien n'est supposé.** La révocation demande confirmation **en nommant le secret** ; une
 *    révocation d'un secret déjà absent est dite telle quelle (la route est idempotente), et la
 *    liste est relue après une écriture plutôt que devinée.
 *
 * @param server le serveur dont on lit le coffre ; le profil visé est celui de sa configuration
 *   (`VaultApi` envoie `default` quand l'app n'en nomme pas d'autre, parce que le greffon refuse
 *   une requête sans profil).
 */
@Composable
fun VaultScreen(
    server: ServerConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()

    var secrets by remember(server.id) { mutableStateOf<List<VaultSecret>?>(null) }
    var problem by remember(server.id) { mutableStateOf<String?>(null) }
    var reloadKey by remember(server.id) { mutableIntStateOf(0) }
    // Le brouillon d'écriture : la valeur vit **le temps de la requête**, et rien de plus.
    var name by remember(server.id) { mutableStateOf("") }
    var value by remember(server.id) { mutableStateOf("") }
    var writing by remember(server.id) { mutableStateOf(false) }
    var writeProblem by remember(server.id) { mutableStateOf<String?>(null) }
    var notice by remember(server.id) { mutableStateOf<String?>(null) }
    var pendingRevoke by remember(server.id) { mutableStateOf<VaultSecret?>(null) }
    var revoking by remember(server.id) { mutableStateOf(false) }
    var revokeProblem by remember(server.id) { mutableStateOf<String?>(null) }

    val api = remember(server.id, server.configSignature) { VaultApi(ServeClient(server)) }

    fun message(error: Throwable, missingRoute: String = MISSING_ROUTE): String {
        val http = error as? TransportError
        if (http is TransportError.Http && http.status == 404) return OnyxLocalization[missingRoute]
        return humanFailure(error)
    }

    suspend fun read(clearFirst: Boolean) {
        if (clearFirst) secrets = null
        problem = null
        runCatching { api.list() }
            .onSuccess { secrets = it }
            .onFailure { problem = message(it) }
    }

    LaunchedEffect(server.id, server.configSignature, reloadKey) { read(clearFirst = true) }

    fun write() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || value.isEmpty()) {
            writeProblem = OnyxLocalization["A name and a value are both required."]
            return
        }
        writeProblem = null
        notice = null
        writing = true
        scope.launch {
            runCatching { api.put(name = trimmed, value = value) }
                .onSuccess { written ->
                    // **La valeur quitte l'écran ici**, et n'y revient jamais : le champ est vidé, et
                    // ce que le serveur a rendu (empreinte, date) est relu par la liste.
                    name = ""
                    value = ""
                    notice = OnyxLocalization.format("Saved %1\$s. The value is never shown again.", written.name)
                    read(clearFirst = false)
                }
                .onFailure { writeProblem = message(it) }
            writing = false
        }
    }

    fun revoke(target: VaultSecret) {
        revoking = true
        revokeProblem = null
        scope.launch {
            runCatching { api.revoke(target.name) }
                .onSuccess { result ->
                    pendingRevoke = null
                    if (result.revoked) {
                        secrets = secrets?.filterNot { it.name == result.name }
                        notice = OnyxLocalization.format("The secret %1\$s was revoked.", result.name)
                    } else {
                        // La route est idempotente : rien à retirer n'est pas une panne, mais la
                        // liste qui affichait encore la ligne est relue au lieu d'être crue.
                        notice = OnyxLocalization.format("There was no secret named %1\$s.", result.name)
                        read(clearFirst = false)
                    }
                }
                .onFailure { revokeProblem = message(it) }
            revoking = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Secrets vault", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            notice?.let {
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = it,
                            localize = false,
                            style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            when (vaultView(secrets, problem)) {
                VaultView.ERROR -> {
                    OnyxFormSection {
                        OnyxRow {
                            FailureBanner(
                                text = problem.orEmpty(),
                                onRetry = { reloadKey += 1 },
                            )
                        }
                    }
                }

                // L'attente porte son propre gabarit de section : elle ne se pose pas dans une
                // rangée de formulaire, où elle perdrait sa marge et ses barres.
                VaultView.LOADING -> OnyxLoadingRows(label = "Loading from Onyx…")

                VaultView.EMPTY, VaultView.LIST -> {
                    val rows = secrets.orEmpty()
                    OnyxFormSection(
                        header = "Secrets",
                        footer = "Secret names and fingerprints only. A value is never shown, here or anywhere else in Onyx.",
                    ) {
                        if (rows.isEmpty()) {
                            OnyxRow { EmptyLine("No secrets for this profile yet. Add one below.") }
                        } else {
                            // La section porte **sa** surface : les rangées se posent dessus, comme
                            // les compétences de « Capacités ». Une seconde carte dans la carte
                            // peindrait deux surfaces l'une sur l'autre.
                            rows.forEachIndexed { index, secret ->
                                if (index > 0) ContentRowDivider()
                                ContentRow(
                                    // Un nom de secret est un identifiant : monospace, verbatim.
                                    title = secret.name,
                                    localizeTitle = false,
                                    monoTitle = true,
                                    subtitle = vaultSecretDetail(secret),
                                    symbol = Symbols.KeyOutline,
                                    trailing = {
                                        ContentPill(
                                            text = "Revoke",
                                            localize = true,
                                            onClick = {
                                                revokeProblem = null
                                                pendingRevoke = secret
                                            },
                                            variant = PillVariant.Outline,
                                            size = PillSize.Small,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ---- L'écriture ---------------------------------------------------------------
            OnyxFormSection(
                header = "Add a secret",
                footer = "The value is written to the vault and never shown again.",
            ) {
                VaultField(
                    label = "Name",
                    value = name,
                    placeholder = "openai",
                    onValueChange = { name = it },
                    imeAction = ImeAction.Next,
                )
                OnyxRowDivider()
                VaultField(
                    label = "Value",
                    value = value,
                    // **Jamais lisible** : le champ est masqué, il ne montre ni le début ni la fin.
                    secret = true,
                    onValueChange = { value = it },
                    imeAction = ImeAction.Done,
                )
            }

            writeProblem?.let {
                OnyxFormSection {
                    OnyxRow {
                        FailureBanner(text = it)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContentPill(
                    text = if (writing) "Saving…" else "Save",
                    localize = true,
                    onClick = { write() },
                    enabled = !writing && name.isNotBlank() && value.isNotEmpty(),
                    variant = PillVariant.Primary,
                    size = PillSize.Medium,
                )
            }
        }
    }

    // ---- La confirmation de révocation, qui **nomme** le secret ---------------------------
    val target = pendingRevoke
    if (target != null) {
        HouseSheet(
            onDismiss = { if (!revoking) pendingRevoke = null },
            title = "Revoke this secret?",
            subtitle = OnyxLocalization.format(
                "Revoke %1\$s for this profile. The agent stops seeing it.",
                target.name,
            ),
            closeLabel = "Cancel",
        ) {
            HouseSheetRow(
                label = "Revoke",
                symbol = Symbols.Trash,
                destructive = true,
                description = target.name,
                onClick = { if (!revoking) revoke(target) },
            )
            revokeProblem?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.footnote.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                    modifier = Modifier.padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half),
                )
            }
        }
    }
}

/**
 * Un champ du formulaire du coffre : le libellé à gauche, la valeur à droite — et, pour la valeur
 * du secret, un champ **masqué** qui n'offre même pas la copie.
 *
 * `OnyxFieldRow(secret = true)` conviendrait pour l'affichage, mais il ajoute un geste « copier »
 * à côté du champ : sur une valeur qu'on est en train d'écrire dans le coffre, ce geste n'a aucune
 * utilité et pose la valeur dans le presse-papier de l'appareil. On garde donc le gabarit de la
 * rangée, avec le champ seul.
 */
@Composable
private fun VaultField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val theme = LocalOnyxTheme.current
    OnyxRow {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        OnyxEditableValue(
            value = value,
            onValueChange = onValueChange,
            textStyle = theme.body.copy(color = theme.secondaryText),
            placeholder = placeholder,
            placeholderStyle = theme.body.copy(color = theme.tertiaryText),
            secret = secret,
            keyboardType = keyboardType,
            imeAction = imeAction,
            modifier = Modifier.weight(1.3f),
        )
    }
}
