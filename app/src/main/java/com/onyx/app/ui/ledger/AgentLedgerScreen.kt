package com.onyx.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.AgentLedger
import com.onyx.app.data.model.serve.AgentLedger.Companion.SEPARATOR
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.LedgerRow
import com.onyx.app.data.model.serve.LedgerScope
import com.onyx.app.data.model.serve.LedgerSection
import com.onyx.app.data.net.ModelCatalog
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.ServeSessionsApi
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.data.net.TransportError
import com.onyx.app.data.model.serve.LedgerFormat
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.bots.botOfConversation
import com.onyx.app.ui.bots.conversationProfile
import com.onyx.app.ui.bots.ledgerProfiles
import com.onyx.app.ui.common.AdaptiveSegmented
import com.onyx.app.ui.design.IdentifierText
import com.onyx.app.ui.design.ProfileInForce
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.ServerPrice
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * **Le relevé d'agent** — l'écran qui répond à « qu'est-ce que mon agent a fait, avec quoi, combien
 * de temps, et combien ça a coûté ? », pour **une conversation** ou pour **un bot en cumul**.
 *
 * Tout le calcul vit dans [AgentLedger] (module `core`, éprouvé sans appareil) ; cet écran ne fait
 * que trois choses :
 *
 * 1. **lire les deux sources du serveur** — le registre des sessions (`GET api/sessions`) et la
 *    table de prix des modèles (`GET api/model/options`) —, chacune avec son issue : réponse,
 *    route absente, panne ;
 * 2. **décider de la portée** : la conversation ouverte (son fil, plus sa ligne du registre), ou
 *    un bot et tout ce que ce téléphone lui rattache ;
 * 3. **dessiner les rangées** que le relevé rend, sans jamais recalculer un chiffre.
 *
 * Ce que l'écran **ne fait pas**, et c'est volontaire : il n'écrit **aucun montant** que le serveur
 * n'a pas donné ou rendu calculable à partir d'un prix qu'il publie. Sans prix, la ligne écrit
 * « non fourni » et dit pourquoi. Une donnée absente est nommée, jamais remplacée par un zéro.
 */
@Composable
fun AgentLedgerScreen(
    server: ServerConfig,
    conversations: List<Conversation>,
    bots: List<BotProfile>,
    mainName: String,
    openConversation: Conversation?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    standIn: LedgerStandIn? = null,
) {
    val theme = LocalOnyxTheme.current
    val locale = OnyxLanguage.preferred

    // La doublure de QA **remplace** le monde lu sur l'appareil : elle apporte son fil, son roster
    // et sa portée de départ. Sans elle, le relevé lit le serveur — rien d'autre ne change.
    val thread = standIn?.conversations ?: conversations
    val roster = standIn?.bots ?: bots
    val open = standIn?.openConversation ?: openConversation

    // La portée : la conversation d'abord — c'est de là qu'on ouvre le relevé.
    var scope by rememberSaveable { mutableStateOf((standIn?.initialScope ?: LedgerScope.CONVERSATION).name) }
    var reload by remember { mutableIntStateOf(0) }

    // Le registre des sessions, et la table de prix. `null` = pas encore d'essai.
    var registry by remember(server.id) { mutableStateOf<LedgerRead<List<SessionSummary>>?>(null) }
    var prices by remember(server.id) { mutableStateOf<LedgerRead<Map<String, ServerPrice>>?>(null) }
    var priceSource by remember(server.id) { mutableStateOf<String?>(null) }
    var priceReadAt by remember(server.id) { mutableStateOf<Long?>(null) }

    // Les conversations rattachées à chaque bot : la règle de l'app (`botOfConversation`), pas une
    // seconde règle inventée pour cet écran.
    val attributions = remember(thread, roster, mainName) {
        thread.groupBy { conversation -> botOfConversation(conversation, roster, mainName)?.name }
    }

    val botChat = remember(open, roster, mainName) {
        open?.let { conversation -> botOfConversation(conversation, roster, mainName) }
    }

    /**
     * **Les profils à interroger** — les mêmes pour le registre des sessions **et** pour la table de
     * prix : une seule liste, donc une seule vérité sur « quel profil porte ce fil ».
     *
     * C'est le correctif de l'attribution par bot : une session est écrite dans la base **de son
     * profil** (`~/.hermes/profiles/<bot>/state.db`). Le registre était lu sans profil, donc dans la
     * base `default`, où la ligne d'un bot n'existe pas — d'où « Modèle : non consigné », « Origine :
     * ce téléphone » et des outils à zéro sur une session parfaitement enregistrée.
     */
    val profiles = remember(scope, open, roster, mainName, server.id, server.configSignature) {
        when (scope) {
            LedgerScope.BOT.name -> ledgerProfiles(roster, server)
            else -> listOf(
                open?.let { conversation -> conversationProfile(conversation, roster, server, mainName) }
                    ?: server.effectiveProfile
                    ?: ProfileInForce.Default,
            )
        }
    }

    LaunchedEffect(server.id, server.configSignature, reload, scope, standIn) {
        registry = null
        prices = null
        priceSource = null
        priceReadAt = null
        if (standIn != null) {
            registry = LedgerRead.Ready(standIn.registry)
            prices = LedgerRead.Ready(standIn.prices)
            priceSource = standIn.priceSource
            priceReadAt = standIn.priceReadAt
            return@LaunchedEffect
        }
        val client = ServeClient(server)
        if (!server.hasServeCredentials) {
            // Aucun identifiant de tableau de bord : les deux routes sont hors d'atteinte, et
            // l'écran le dit au lieu de peindre des chiffres du téléphone comme s'ils venaient du
            // serveur.
            val silent = LedgerRead.Unavailable(OnyxLocalization.translate(AgentLedger.KEY_NO_DASHBOARD, locale))
            registry = silent
            prices = silent
            return@LaunchedEffect
        }
        registry = readRegistry(client, profiles)
        val readAt = System.currentTimeMillis()
        val table = readPrices(server, profiles)
        prices = table
        priceReadAt = readAt
        // La provenance des prix : **trois** valeurs dans un gabarit à trois trous. Le gabarit en
        // portait trois et n'en recevait que deux — `MissingFormatArgumentException` levée dans cet
        // effet, donc au fil principal, avant la première image : l'app mourait sur « Relevé de
        // l'agent ». La composition passe par [ledgerPriceSource], que le verrou du menu appelle
        // sans appareil.
        priceSource = ledgerPriceSource(server, profiles, locale)
    }

    val answer = (registry as? LedgerRead.Ready)?.value
    val priceTable = (prices as? LedgerRead.Ready)?.value.orEmpty()

    // Le relevé de la portée choisie.
    val scopeConversations: List<Conversation> = when (scope) {
        LedgerScope.BOT.name -> botChat?.let { bot -> attributions[bot.name].orEmpty() }.orEmpty()
        else -> listOfNotNull(open)
    }
    val subject = when (scope) {
        LedgerScope.BOT.name -> botChat?.label(mainName) ?: OnyxLocalization.translate(KEY_PER_BOT, locale)
        else -> open?.title ?: OnyxLocalization.translate(KEY_THIS_CONVERSATION, locale)
    }

    val ledger: AgentLedger = when (scope) {
        LedgerScope.BOT.name -> AgentLedger.cumulative(
            subject = subject,
            ledgers = scopeConversations.map { conversation ->
                ledgerOf(conversation, server, answer, registry != null, priceTable, priceSource, priceReadAt)
            },
        )

        else -> open?.let { conversation ->
            ledgerOf(conversation, server, answer, registry != null, priceTable, priceSource, priceReadAt)
        } ?: AgentLedger(scope = LedgerScope.CONVERSATION, subject = subject)
    }

    // La comparaison : chaque bot et son cumul, du plus de travail au moins.
    val compared = remember(scope, thread, roster, mainName, answer, priceTable, priceReadAt, priceSource) {
        if (scope != LedgerScope.BOT.name) {
            emptyList()
        } else {
            roster.mapNotNull { bot ->
                val mine = attributions[bot.name].orEmpty()
                if (mine.isEmpty()) return@mapNotNull null
                bot.name to AgentLedger.cumulative(
                    subject = bot.label(mainName),
                    ledgers = mine.map { conversation ->
                        ledgerOf(conversation, server, answer, registry != null, priceTable, priceSource, priceReadAt)
                    },
                )
            }.sortedByDescending { (_, ledger) -> ledger.workSeconds ?: 0.0 }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Agent ledger", onBack = onBack)
            BasicText(
                text = "What the agent did, with what, how long, and what it cost.",
                style = theme.footnote.copy(color = ledgerNoteInk(theme, theme.formPageColor), textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(start = OnyxSpace.BarInset, end = OnyxSpace.BarInset, bottom = OnyxSpace.Half),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AdaptiveSegmented(
                options = listOf(
                    OnyxLocalization.translate(KEY_THIS_CONVERSATION, locale),
                    OnyxLocalization.translate(KEY_PER_BOT, locale),
                ),
                selectedIndex = if (scope == LedgerScope.BOT.name) 1 else 0,
                onSelect = { index -> scope = if (index == 1) LedgerScope.BOT.name else LedgerScope.CONVERSATION.name },
            )
            // **De quoi parle ce relevé.** Un écran de données qui ne dit pas son sujet se lit de
            // travers : ici, le titre de la conversation ou le nom du bot, et le nombre de
            // conversations couvertes (le cumul d'un bot en additionne plusieurs).
            BasicText(
                text = listOf(
                    ledger.subject,
                    OnyxLocalization.format(
                        if (ledger.conversations == 1) "1 conversation" else "%d conversations",
                        ledger.conversations,
                        locale = locale,
                    ),
                ).filter { it.isNotBlank() }.joinToString(SEPARATOR),
                localize = false,
                style = theme.footnote.copy(
                    color = ledgerNoteInk(theme, theme.formPageColor),
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = OnyxSpace.BarInset, end = OnyxSpace.BarInset, top = OnyxSpace.Half),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = OnyxSpace.ScreenMargin, end = OnyxSpace.ScreenMargin, bottom = OnyxSpace.Loose),
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            if (scope == LedgerScope.BOT.name && ledger.conversations == 0) {
                // Aucune conversation rattachée : le dire, plutôt que de dessiner un relevé vide.
                OnyxEmptyState(
                    Symbols.Person2,
                    "Nothing to report yet",
                    description = "This bot has no conversation on this phone yet.",
                )
            } else if (ledger.isEmpty && scope != LedgerScope.BOT.name) {
                // Rien n'a encore tourné : le dire, plutôt que six sections qui ne diraient rien.
                OnyxEmptyState(
                    Symbols.ChartBar,
                    "Nothing to report yet",
                    description = "This conversation has not run anything yet: its ledger fills up as the agent works.",
                )
            } else {
                ledger.sections(locale).forEach { section -> LedgerSectionView(section) }
            }

            if (scope == LedgerScope.BOT.name) {
                CompareBots(
                    rows = compared,
                    botsWithoutConversations = roster.size - compared.size,
                    locale = locale,
                )
            }

            // Ce que l'écran a tenté, et ce qu'il a obtenu — la règle de la maison pour toute route.
            SourcesNote(registry = registry, prices = prices, locale = locale, onRetry = { reload++ })
        }
    }
}

/** Une section du relevé : son en-tête, ses rangées, leurs filets. */
@Composable
private fun LedgerSectionView(section: LedgerSection) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(header = section.header) {
        section.rows.forEachIndexed { index, row ->
            LedgerRowView(row)
            if (index != section.rows.lastIndex) OnyxRowDivider()
        }
    }
}

/** Une rangée : le libellé à gauche, la valeur **en chasse fixe** à droite, sa note dessous. */
@Composable
private fun LedgerRowView(row: LedgerRow) {
    val theme = LocalOnyxTheme.current
    val ground = theme.formSurfaceColor
    Column(modifier = Modifier.fillMaxWidth()) {
        OnyxRow {
            BasicText(
                text = row.label,
                style = theme.body.copy(color = ledgerLabelInk(theme, ground)),
                modifier = Modifier.weight(1f),
            )
            if (row.label == "Model" && !row.absent) {
                // Un identifiant de modèle se rend comme tel : jamais coupé au milieu d'un mot.
                IdentifierText(
                    text = row.value,
                    style = theme.body.copy(color = ledgerValueInk(theme, ground, absent = false)),
                    maxLines = 1,
                )
            } else {
                BasicText(
                    text = row.value,
                    localize = false,
                    style = theme.body.copy(
                        color = ledgerValueInk(theme, ground, absent = row.absent),
                        fontFamily = if (row.monospace) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = if (row.absent) FontWeight.Normal else FontWeight.Medium,
                    ),
                )
            }
        }
        row.note?.let { note ->
            BasicText(
                text = note,
                localize = false,
                style = theme.footnote.copy(color = ledgerNoteInk(theme, ground)),
                modifier = Modifier.padding(
                    start = OnyxSpace.CardPadding,
                    end = OnyxSpace.CardPadding,
                    bottom = OnyxSpace.Snug,
                ),
            )
        }
    }
}

/**
 * Le tableau de comparaison : un bot par rangée, **les mêmes chiffres que le relevé**, alignés — le
 * temps de travail, les appels d'outils, les jetons et le montant (ou « non fourni »).
 *
 * Les bots sans conversation sur ce téléphone sont **comptés et dits** en pied de section : ils ne
 * peuvent pas figurer au tableau, et une ligne à zéro laisserait croire qu'ils n'ont rien fait.
 */
@Composable
private fun CompareBots(rows: List<Pair<String, AgentLedger>>, botsWithoutConversations: Int, locale: Locale) {
    val theme = LocalOnyxTheme.current
    val ground = theme.formSurfaceColor
    if (rows.isNotEmpty()) {
        OnyxFormSection(
            header = "Compare the bots",
            footer = "A bot’s total adds up the conversations this phone ties to it.",
        ) {
            rows.forEachIndexed { index, (_, ledger) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    OnyxRow {
                        BasicText(
                            text = ledger.subject,
                            style = theme.body.copy(color = ledgerLabelInk(theme, ground)),
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = OnyxLocalization.format("%d conversations", ledger.conversations, locale = locale),
                            localize = false,
                            style = theme.caption.copy(color = ledgerNoteInk(theme, ground)),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = OnyxSpace.CardPadding,
                                end = OnyxSpace.CardPadding,
                                bottom = OnyxSpace.Snug,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val figures = listOf(
                            ledger.workSeconds?.let { seconds ->
                                LedgerFormat.duration(seconds, locale)
                            },
                            OnyxLocalization.format("%d tools", ledger.toolsTotal, locale = locale),
                            LedgerFormat.tokens((ledger.tokensInput ?: 0L) + (ledger.tokensOutput ?: 0L), locale),
                            ledger.money.usd?.let { amount ->
                                LedgerFormat.amount(amount, locale)
                            },
                        )
                        BasicText(
                            text = figures.filterNotNull().joinToString(" · "),
                            localize = false,
                            style = theme.caption.copy(
                                color = ledgerNoteInk(theme, ground),
                                fontFamily = FontFamily.Monospace,
                            ),
                            maxLines = 2,
                        )
                    }
                    // Un cumul sans montant écrit **pourquoi** : le tableau ne mélange pas un bot
                    // tarifé et un bot muet sans le dire.
                    if (!ledger.money.hasAmount) {
                        BasicText(
                            text = OnyxLocalization.translate("not provided", locale),
                            localize = false,
                            style = theme.caption.copy(
                                color = ledgerValueInk(theme, theme.formPageColor, absent = true),
                            ),
                            modifier = Modifier.padding(
                                start = OnyxSpace.CardPadding,
                                end = OnyxSpace.CardPadding,
                                bottom = OnyxSpace.Snug,
                            ),
                        )
                    }
                }
                if (index != rows.lastIndex) OnyxRowDivider()
            }
        }
    }
    if (botsWithoutConversations > 0) {
        OnyxFormSection {
            OnyxRow {
                BasicText(
                    text = OnyxLocalization.format(
                        AgentLedger.KEY_BOTS_WITHOUT_CHATS,
                        botsWithoutConversations,
                        locale = locale,
                    ),
                    localize = false,
                    style = theme.footnote.copy(color = ledgerNoteInk(theme, theme.formSurfaceColor)),
                )
            }
        }
    }
}

/**
 * Ce que l'écran a tenté et ce qu'il a obtenu, sous les chiffres : deux routes, deux issues.
 *
 * Un échec porte une action de **relire** réelle ; une route absente dit qu'elle manque sur ce
 * serveur — jamais une carte peinte sur une lecture qui n'a pas eu lieu.
 */
@Composable
private fun SourcesNote(
    registry: LedgerRead<List<SessionSummary>>?,
    prices: LedgerRead<Map<String, ServerPrice>>?,
    locale: Locale,
    onRetry: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val ground = theme.formSurfaceColor
    val lines = buildList {
        add(
            when (registry) {
                null -> OnyxLocalization.translate(AgentLedger.KEY_READING_REGISTRY, locale)
                is LedgerRead.Ready -> OnyxLocalization.translate(AgentLedger.KEY_REGISTRY_READ, locale)
                is LedgerRead.Unavailable -> OnyxLocalization.format(
                    AgentLedger.KEY_REGISTRY_MISSING,
                    registry.reason,
                    locale = locale,
                )
            },
        )
        add(
            when (prices) {
                null -> OnyxLocalization.translate(AgentLedger.KEY_READING_PRICES, locale)
                is LedgerRead.Ready -> OnyxLocalization.translate(AgentLedger.KEY_PRICES_READ, locale)
                is LedgerRead.Unavailable -> OnyxLocalization.format(
                    AgentLedger.KEY_PRICES_MISSING,
                    prices.reason,
                    locale = locale,
                )
            },
        )
    }
    OnyxFormSection {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OnyxSpace.CardPadding),
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            lines.forEach { line ->
                BasicText(
                    text = line,
                    localize = false,
                    style = theme.footnote.copy(color = ledgerNoteInk(theme, ground)),
                )
            }
            if (registry is LedgerRead.Unavailable || prices is LedgerRead.Unavailable) {
                BasicText(
                    text = "Try again",
                    style = theme.footnote.copy(color = ledgerValueInk(theme, ground, absent = false)),
                    modifier = Modifier.padding(top = OnyxSpace.Hair).onyxTappable(onClick = onRetry),
                )
            }
        }
    }
}

/** Un état vide de lecture, ou une panne : l'écran s'en sert pour dire ce qu'il a tenté. */
internal sealed interface LedgerRead<out T> {
    data class Ready<T>(val value: T) : LedgerRead<T>

    /** La route manque, a été refusée, ou le corps n'était pas lisible — [reason] le dit. */
    data class Unavailable(val reason: String) : LedgerRead<Nothing>
}

/** Le relevé d'une conversation, à partir du registre et de la table de prix déjà lus. */
private fun ledgerOf(
    conversation: Conversation,
    server: ServerConfig,
    registry: List<SessionSummary>?,
    registryAttempted: Boolean,
    prices: Map<String, ServerPrice>,
    priceSource: String?,
    priceReadAt: Long?,
): AgentLedger {
    val row = conversation.serverSessionId?.takeIf { it.isNotBlank() }?.let { id -> registry?.firstOrNull { it.id == id } }
    val model = row?.model?.takeIf { it.isNotBlank() }
    return AgentLedger.of(
        conversation = conversation,
        registry = row,
        // La route a-t-elle été lue ? Le relevé n'en fait un essai pour cette conversation que si
        // elle porte une session à y chercher (voir `AgentLedger.of`).
        registryAttempted = registryAttempted,
        price = model?.let { prices[it] },
        priceSource = priceSource,
        priceReadAt = priceReadAt,
    )
}

/** Le profil du tableau de bord visé ; « default » quand le serveur n'en nomme aucun. */
private fun serverProfile(server: ServerConfig): String =
    server.effectiveProfile?.takeIf { it.isNotBlank() } ?: ModelCatalog.DEFAULT_PROFILE

/**
 * `GET api/sessions`, **profil par profil** : ses trois issues, dites.
 *
 * Chaque profil est lu (voir [ServeSessionsApi.summariesIn]) : c'est lui qui porte la ligne d'une
 * session de bot. Un profil dont la lecture échoue ne fait pas tomber les autres — la route est
 * dite indisponible seulement si **aucun** n'a répondu.
 */
private suspend fun readRegistry(
    client: ServeClient,
    profiles: List<String>,
): LedgerRead<List<SessionSummary>> = try {
    LedgerRead.Ready(ServeSessionsApi(client).summariesIn(profiles))
} catch (error: TransportError.Http) {
    LedgerRead.Unavailable(OnyxLocalization.format("HTTP %1\$d", error.status))
} catch (error: TransportError) {
    LedgerRead.Unavailable(error.userMessage())
}

/**
 * La table de prix de chaque profil visé, lue **en parallèle**, un profil qui échoue ne faisant pas
 * tomber les autres.
 *
 * C'est **la seule source de prix de l'app** : un profil dont la table est vide rend une table
 * vide, donc des modèles **sans prix** — jamais un tarif de remplacement.
 */
private suspend fun readPrices(server: ServerConfig, profiles: List<String>): LedgerRead<Map<String, ServerPrice>> {
    val tables = coroutineScope {
        profiles.distinct().map { profile ->
            async { runCatching { ModelCatalog.fetchModelOptions(server, profile) }.getOrNull() }
        }.map { it.await() }
    }
    if (tables.all { it == null }) {
        return LedgerRead.Unavailable(OnyxLocalization["the server's price table did not answer"])
    }
    val merged = LinkedHashMap<String, ServerPrice>()
    tables.filterNotNull().forEach { providers ->
        providers.forEach { provider ->
            provider.pricing.forEach { (model, price) -> merged.putIfAbsent(model, price) }
        }
    }
    return LedgerRead.Ready(merged)
}

// ————————————————————————————————————————————————————————————————————————————————
// Les encres de l'écran — une seule par rôle, pour que la mesure de contraste porte
// sur ce que l'écran compose vraiment.
// ————————————————————————————————————————————————————————————————————————————————

/** L'encre d'un **libellé** de rangée. */
internal fun ledgerLabelInk(theme: ResolvedTheme, ground: Color): Color =
    bodyInk(theme, ground, theme.primaryText)

/** L'encre d'une **valeur** ; reculée quand la valeur dit une absence. */
internal fun ledgerValueInk(theme: ResolvedTheme, ground: Color, absent: Boolean): Color =
    bodyInk(theme, ground, if (absent) theme.tertiaryText else theme.secondaryText)

/** L'encre d'une **note** : la provenance, ou ce qui manque. */
internal fun ledgerNoteInk(theme: ResolvedTheme, ground: Color): Color =
    bodyInk(theme, ground, theme.tertiaryText)

/** Les deux portées de l'écran, et l'adresse des prix — les mêmes clés que le relevé. */
private const val KEY_THIS_CONVERSATION = "This conversation"
private const val KEY_PER_BOT = "Per bot"
/** `GET api/model/options` : sa provenance, écrite une fois — la même chaîne pour les deux portées. */
private const val KEY_PRICE_SOURCE = "%1\$s · %2\$s?profile=%3\$s"

/**
 * **La provenance des prix**, telle que l'écran l'écrit sous les montants : l'adresse du serveur,
 * la route réellement lue, et le ou les profils interrogés.
 *
 * Le gabarit ([KEY_PRICE_SOURCE]) a **trois** trous et reçoit **trois** valeurs, dans cet ordre.
 * C'était le plantage : deux valeurs pour trois trous, `MissingFormatArgumentException` levée dans
 * l'effet de chargement — donc l'app se fermait avant d'avoir peint l'écran. Extraite ici pour que
 * le verrou du menu l'exécute sans appareil : une régression d'arité rougit le test.
 */
internal fun ledgerPriceSource(server: ServerConfig, profiles: List<String>, locale: Locale): String =
    OnyxLocalization.format(
        KEY_PRICE_SOURCE,
        server.serveBase,
        ModelCatalog.MODEL_OPTIONS_ROUTE,
        profiles.joinToString(", "),
        locale = locale,
    )
