package com.onyx.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.ConnectionProbe
import com.onyx.app.data.net.ConnectionTester
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.AlertAction
import com.onyx.app.ui.design.AttachmentAction
import com.onyx.app.ui.design.AttachmentSheet
import com.onyx.app.ui.design.OnyxAlert
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.fitWords
import com.onyx.app.ui.design.sheetSurface
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.localization.resolveOnyxText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La **feuille de compte du volet** : le rond de l'initiale l'ouvre, et l'adresse de la passerelle
 * — dans la tête du volet — aussi. Elle dit **l'état réellement mesuré** de la passerelle et
 * propose les gestes de la connexion.
 *
 * Ce qu'elle n'est pas : une seconde implémentation de feuille. Elle rejoint la **famille
 * existante** ([AttachmentSheet]) — mêmes mesures, même poignée, même montée, même voile, même
 * fermeture (appui à côté, glissement vers le bas, retour matériel par la `Dialog` ; le retour
 * ferme **la feuille avant tout le reste**). Seule la ligne d'état est nouvelle, et elle est un
 * **paramètre** de la famille (`status`), pas une copie.
 *
 * **Aucun état n'est inventé.** Le titre, le voyant et le motif viennent d'une mesure réelle
 * ([ConnectionTester], la même sonde que l'écran de configuration) : joignable, ne répond pas, ou
 * identifiants refusés. Le compte à rebours, lui, est celui d'une **vraie** politique de réessai —
 * la seule de l'app pour cette connexion, et elle vit ici : tant que la feuille est ouverte et la
 * passerelle muette, une tentative part toutes les [AccountRetry.INTERVAL_SECONDS] secondes, à
 * l'heure réelle, et la ligne annonce le temps qui reste avant **cette** tentative-là.
 */
@Composable
fun AccountSheet(
    /** L'adresse telle que les réglages la portent : montrée en sous-titre, jamais retouchée. */
    address: String,
    /** La passerelle visée par la mesure. */
    server: ServerConfig,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Recharge vraiment la liste et rend **le nombre de conversations** qu'elle contient après. */
    onRefreshConversations: suspend () -> Int,
    /** Appelé **après** la confirmation : l'app retourne à son écran de configuration. */
    onDisconnect: () -> Unit,
    onChangeGateway: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // La mesure : `null` tant qu'aucune réponse n'est arrivée, sinon la sonde telle quelle.
    var probe by remember(server.id, server.configSignature) { mutableStateOf<ConnectionProbe?>(null) }
    /** La tentative en cours. On l'incrémente pour **relancer maintenant** (le bouton, ou la boucle). */
    var round by remember(server.id, server.configSignature) { mutableIntStateOf(0) }
    /** L'heure réelle de la prochaine tentative ; `0` veut dire « rien de programmé ». */
    var nextAttemptAt by remember(server.id, server.configSignature) { mutableLongStateOf(0L) }
    /** L'horloge lue pour le compte à rebours : elle ne fait que **lire** l'heure du rendez-vous. */
    var now by remember(server.id, server.configSignature) { mutableLongStateOf(System.currentTimeMillis()) }
    /** Le retour visible de l'actualisation des conversations. */
    var refreshed by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var confirmingDisconnect by remember { mutableStateOf(false) }

    // La mesure, et la programmation de la tentative suivante si la passerelle n'a pas répondu.
    LaunchedEffect(round, server.id, server.configSignature) {
        probe = null
        nextAttemptAt = 0L
        val measured = measureGateway(server)
        probe = measured
        if (!measured.ok) {
            nextAttemptAt = AccountRetry.nextAttemptAt(System.currentTimeMillis())
        }
    }

    // La boucle de réessai **réelle** : quand l'heure du rendez-vous est passée, une nouvelle
    // tentative part — ce n'est pas une décoration, c'est la tentative qui la redéclenche.
    LaunchedEffect(nextAttemptAt) {
        while (nextAttemptAt != 0L) {
            delay(1_000)
            now = System.currentTimeMillis()
            if (now >= nextAttemptAt) {
                round += 1
                break
            }
        }
    }

    val secondsLeft = if (nextAttemptAt == 0L) null else AccountRetry.secondsUntil(now, nextAttemptAt)
    val status = accountStatusOf(probe, secondsLeft)

    AttachmentSheet(
        title = status.title,
        subtitle = address,
        actions = accountActions(
            onRow = { action ->
                when (action) {
                    // « Réglages » : l'écran des Réglages, comme la roue dentée de la tête du volet.
                    AccountAction.SETTINGS -> {
                        onDismiss()
                        onOpenSettings()
                    }
                    // « Actualiser les conversations » : la liste est **relue**, et la feuille le dit
                    // avec le compte réellement obtenu — un appui muet ne se distingue pas d'un échec.
                    AccountAction.REFRESH -> {
                        if (!refreshing) {
                            refreshing = true
                            refreshed = null
                            scope.launch {
                                val count = runCatching { onRefreshConversations() }.getOrDefault(-1)
                                refreshing = false
                                refreshed = if (count >= 0) {
                                    OnyxLocalization.format("Conversations refreshed · %1\$d", count)
                                } else {
                                    OnyxLocalization["Couldn't refresh the conversations."]
                                }
                            }
                        }
                    }
                    // « Réessayer la connexion maintenant » : la tentative part **tout de suite**, et
                    // le compte à rebours repart de l'intervalle réel.
                    AccountAction.RETRY -> {
                        probe = null
                        nextAttemptAt = 0L
                        refreshed = null
                        round += 1
                    }
                    // « Se déconnecter » : la question d'abord — c'est le seul geste qu'on ne
                    // rattrape pas —, puis l'écran de configuration.
                    AccountAction.DISCONNECT -> confirmingDisconnect = true
                    // « Changer ou ajouter une passerelle » : l'écran des serveurs, sa liste et son ajout.
                    AccountAction.GATEWAYS -> {
                        onDismiss()
                        onChangeGateway()
                    }
                }
            },
        ),
        onDismiss = onDismiss,
        status = {
            AccountStatusLine(
                status = status,
                notice = if (refreshing) OnyxLocalization["Refreshing the conversations…"] else refreshed,
            )
        },
    )

    if (confirmingDisconnect) {
        OnyxAlert(
            title = "Disconnect from this gateway?",
            message = "Onyx goes back to its setup screen. The address and keys stay in its settings, and nothing is removed from the server.",
            actions = listOf(
                AlertAction("Disconnect", destructive = true) {
                    confirmingDisconnect = false
                    onDismiss()
                    onDisconnect()
                },
                AlertAction("Cancel") { confirmingDisconnect = false },
            ),
            onDismiss = { confirmingDisconnect = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Ce que la feuille montre — pur, donc mesurable
// ---------------------------------------------------------------------------------------------

/** L'état d'une mesure : en cours, joignable, ou muette. */
enum class AccountTone { CHECKING, REACHABLE, UNREACHABLE }

/** Ce qu'une ligne d'état dit : son titre, sa teinte, sa ligne et le **motif exact** de la mesure. */
data class AccountStatus(
    /** Le titre d'état — une clé anglaise, traduite à l'affichage (« Connecté » / « Hors ligne »). */
    val title: String,
    val tone: AccountTone,
    /** La ligne d'état, déjà composée (elle porte le compte à rebours réel quand il y en a un). */
    val line: String,
    /** Le motif tel que la mesure l'a rapporté, ou `null` quand il n'y a rien à dire. */
    val detail: String?,
)

/**
 * La ligne d'état, à partir de **ce qui a été mesuré**.
 *
 * **L'état n'est plus répété trois fois** (défaut relevé sur la capture du propriétaire : « Connecté »
 * au titre, « Connecté · 231 ms » puis « Connecté — 1 modèle(s). »). Chaque endroit dit **une** chose,
 * et une seule :
 *
 *  - le **titre** porte l'état (« Connecté », « Hors ligne »), une fois ;
 *  - la **ligne** porte la **mesure** : la latence réellement relevée, ou le compte à rebours de la
 *    prochaine tentative réelle — jamais le mot de l'état une seconde fois ;
 *  - le **motif** porte ce que la sonde a rapporté en plus : le nombre de modèles, la version, ou la
 *    phrase d'échec.
 *
 * Le motif — la phrase de la mesure — est rendu **tel quel** : c'est la sonde qui parle, pas la feuille.
 */
fun accountStatusOf(
    probe: ConnectionProbe?,
    secondsLeft: Int?,
    locale: Locale = OnyxLanguage.preferred,
): AccountStatus = when {
    probe == null -> AccountStatus(
        title = "Checking",
        tone = AccountTone.CHECKING,
        // Le titre dit déjà l'attente : la ligne n'a rien à répéter.
        line = "",
        detail = null,
    )

    probe.ok -> AccountStatus(
        title = "Connected",
        tone = AccountTone.REACHABLE,
        line = OnyxLocalization.format("%1\$d ms", probe.latencyMs, locale),
        detail = probe.message,
    )

    // Hors ligne : le compte à rebours n'apparaît que s'il y a **vraiment** une tentative
    // programmée. Sans politique de réessai, la ligne dit **le motif de la mesure** — c'est
    // l'information utile, et le titre porte déjà « Hors ligne ».
    secondsLeft != null -> AccountStatus(
        title = "Offline",
        tone = AccountTone.UNREACHABLE,
        line = OnyxLocalization.format("new attempt in %1\$d s", secondsLeft, locale),
        detail = probe.message,
    )

    else -> AccountStatus(
        title = "Offline",
        tone = AccountTone.UNREACHABLE,
        line = probe.message.orEmpty(),
        detail = null,
    )
}

/**
 * La politique de réessai **de la connexion**, telle qu'elle est : un rendez-vous toutes les
 * [INTERVAL_SECONDS] secondes, à l'heure réelle.
 *
 * L'app n'en avait aucune pour la passerelle : la seule boucle de reconnexion existante est celle
 * de la session « Computer », qui ne regarde pas le Dashboard. C'est donc la feuille qui la porte,
 * et c'est pour ça que le compte à rebours qu'elle affiche est **vrai** : sans cette boucle, la
 * feuille n'aurait pas le droit d'en montrer un.
 */
object AccountRetry {
    /** L'intervalle entre deux tentatives réelles, en secondes. */
    const val INTERVAL_SECONDS = 30

    /** L'heure de la prochaine tentative, à partir de [nowMs]. */
    fun nextAttemptAt(nowMs: Long): Long = nowMs + INTERVAL_SECONDS * 1_000L

    /** Les secondes qui restent avant [nextAttemptAtMs], arrondies **au-dessus** et jamais négatives. */
    fun secondsUntil(nowMs: Long, nextAttemptAtMs: Long): Int =
        (((nextAttemptAtMs - nowMs) + 999L) / 1_000L).toInt().coerceAtLeast(0)
}

/** Ce qu'une rangée de la feuille fait. */
enum class AccountAction { SETTINGS, REFRESH, RETRY, DISCONNECT, GATEWAYS }

/** Une rangée : son libellé, son icône, et l'action qu'elle porte. */
data class AccountRow(
    val label: String,
    val symbol: Int,
    val action: AccountAction,
    val destructive: Boolean = false,
)

/**
 * Les cinq rangées de la feuille, **dans l'ordre de la référence**.
 *
 * Elles sont déclarées ici, hors du dessin, pour qu'un test puisse les compter et vérifier que
 * chacune porte une action distincte : une ligne qui ne fait rien ne se distingue pas d'une ligne
 * cassée.
 */
fun accountSheetRows(): List<AccountRow> = listOf(
    AccountRow("Settings", Lucide.Settings, AccountAction.SETTINGS),
    AccountRow("Refresh conversations", Lucide.RefreshCw, AccountAction.REFRESH),
    AccountRow("Retry the connection now", Lucide.Radio, AccountAction.RETRY),
    AccountRow("Disconnect", Lucide.CircleX, AccountAction.DISCONNECT, destructive = true),
    AccountRow("Change or add a gateway", Lucide.Server, AccountAction.GATEWAYS),
)

/** Les rangées, reliées à ce qu'elles font — c'est ce que la famille de feuilles dessine. */
internal fun accountActions(onRow: (AccountAction) -> Unit): List<AttachmentAction> =
    accountSheetRows().map { row ->
        AttachmentAction(
            label = row.label,
            symbol = row.symbol,
            destructive = row.destructive,
            onSelect = { onRow(row.action) },
        )
    }

/**
 * La mesure d'une passerelle, **réelle** — la même sonde que l'écran de configuration
 * ([ConnectionTester]) : elle distingue « ne répond pas » (l'adresse ne mène à rien) de
 * « identifiants refusés » (l'adresse répond, la connexion est refusée).
 *
 * Une erreur inattendue ne devient jamais un code brut : elle est dite en clair, dans la langue de
 * l'app.
 */
internal suspend fun measureGateway(server: ServerConfig): ConnectionProbe =
    try {
        ConnectionTester.current(server)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ConnectionProbe(
            ok = false,
            message = OnyxLocalization["Couldn't reach the gateway."],
            latencyMs = 0L,
            models = emptyList(),
        )
    }

// ---------------------------------------------------------------------------------------------
// Le dessin de la ligne d'état — le seul apport à la famille
// ---------------------------------------------------------------------------------------------

/** La taille du voyant d'état, et l'écart au texte qui le suit. */
private val AccountDotSize = 9.dp
private val AccountDotGap = OnyxSpace.IconLabelGap

/**
 * La ligne d'état : le voyant, la ligne, puis le motif de la mesure.
 *
 * Le voyant est **mesuré** sur le panneau (`signalInk`, 3:1) et la ligne suit l'encre du texte
 * (4,5:1) : la couleur ne porte jamais seule l'information — le titre, au-dessus, dit le même état
 * en mots, et c'est lui que lisent les lecteurs d'écran.
 */
@Composable
private fun AccountStatusLine(status: AccountStatus, notice: String?) {
    val theme = LocalOnyxTheme.current
    // Le fond **réellement peint** par la feuille : la pastille d'état, la ligne et le motif sont
    // mesurés contre lui, jamais contre un autre barreau.
    val ground = sheetSurface(theme)
    // **Rien à dire, rien à dessiner.** Tant que la mesure n'a pas répondu, la feuille n'a ni
    // latence, ni compte à rebours, ni motif : la ligne ne portait alors qu'un **point ambre seul**,
    // avec son rembourrage — un vide de 36 dp entre l'adresse et la première rangée, et une couleur
    // qui portait seule l'information (contraire à la règle de la maison). Le titre dit déjà
    // « Vérification » : la ligne n'existe que quand elle a quelque chose à ajouter.
    val hasWords = status.line.isNotBlank() || status.detail != null || notice != null
    if (!hasWords) return
    val dot = accountSignalInk(ground, accountToneHue(theme, status.tone))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OnyxSpace.CardPadding,
                end = OnyxSpace.CardPadding,
                top = OnyxSpace.Half,
                bottom = OnyxSpace.Half,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AccountDotGap),
        ) {
            Box(Modifier.size(AccountDotSize).background(dot, CircleShape))
            // La ligne n'est dessinée que si elle **dit** quelque chose : un état dont le titre suffit
            // ne répète pas son mot ici (défaut relevé sur la capture : « Connecté » trois fois).
            if (status.line.isNotBlank()) {
                BasicText(
                    text = status.line,
                    localize = false,
                    style = theme.footnote.copy(color = theme.textColor, fontWeight = FontWeight.Medium),
                    // Un **état** se replie sur deux lignes (la politique de texte de la maison) :
                    // la ligne est courte, et un état coupé sur une ligne est exactement ce que le
                    // verrou des troncatures refuse.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        status.detail?.let { motif ->
            // Le motif est coupé **à une frontière de mot** (`fitWords`, 3 lignes) : l'ellipse du
            // moteur rognait « dan… » dans « dans les réglages » à police 1,3×. La phrase vient de
            // la mesure : elle est traduite par le dictionnaire, jamais réécrite ici.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val resolved = resolveOnyxText(motif)
                val detailStyle = theme.caption.copy(color = accountDetailInk(theme.textColor, ground))
                val available = with(density) { maxWidth.toPx() }
                val fitted = remember(resolved, available) {
                    fitWords(
                        text = resolved,
                        measure = { line ->
                            measurer.measure(AnnotatedString(line), detailStyle).size.width.toFloat()
                        },
                        available = available,
                        maxLines = 3,
                    )
                }
                BasicText(
                    text = fitted,
                    localize = false,
                    style = detailStyle,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        notice?.let { feedback ->
            BasicText(
                text = feedback,
                localize = false,
                // L'accent **mesuré** sur le panneau : le bleu de la maison sur un panneau quasi
                // blanc vaut ~3,4:1, sous le seuil du texte. Il est écarté du fond du plus petit pas
                // qui suffit — même règle que le reste de la feuille et du volet.
                style = theme.caption.copy(color = sidebarInk(theme.accent, ground, OnyxContrast.TEXT_MIN)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * L'encre du motif de la mesure : le texte du thème, atténué **le plus loin possible sans passer
 * sous 4,5:1** sur le panneau. C'est la règle de la famille (`sheetSubtitleInk`), exposée ici pour
 * que la mesure du contraste la vérifie telle quelle.
 */
internal fun accountDetailInk(text: Color, ground: Color): Color =
    OnyxContrast.dimmed(text, ground, OnyxContrast.TEXT_MIN)

/**
 * La couleur **du thème** qui porte un état : le vert du joignable, le rouge de la muette, l'ambre
 * de ce qu'on ne sait pas encore. Jamais une couleur choisie hors du thème.
 */
internal fun accountToneHue(theme: ResolvedTheme, tone: AccountTone): Color = when (tone) {
    AccountTone.REACHABLE -> theme.success
    AccountTone.UNREACHABLE -> theme.error
    AccountTone.CHECKING -> theme.warning
}

/**
 * L'encre du voyant : la teinte de l'état **mesurée à 3:1** sur le panneau de la feuille — un vert
 * de thème prévu pour une page claire tombe sous le seuil sur le panneau d'un autre thème.
 */
internal fun accountSignalInk(ground: Color, hue: Color): Color =
    sidebarInk(hue, ground, OnyxContrast.ICON_MIN)
