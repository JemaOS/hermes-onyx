package com.onyx.app.ui.insights

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.InsightsFormat
import com.onyx.app.data.model.serve.InsightsPeriod
import com.onyx.app.data.model.serve.ModelUsage
import com.onyx.app.data.model.serve.UsageDay
import com.onyx.app.data.model.serve.UsageReport
import com.onyx.app.data.model.serve.UsageTotals
import com.onyx.app.data.model.serve.dailySeries
import com.onyx.app.data.model.serve.isoDateOf
import com.onyx.app.data.model.serve.peakOf
import com.onyx.app.data.model.serve.shortDate
import com.onyx.app.data.net.ServeAnalyticsApi
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.UsageOutcome
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.AdaptiveSegmented
import com.onyx.app.ui.design.OnyxEmptyState
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.barInk
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.recededInk
import com.onyx.app.ui.design.signalInk
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.design.IdentifierText

/**
 * « Aperçus » — ce que ce profil a consommé sur 7, 30 ou 90 jours.
 *
 * Les chiffres viennent **tous** de `GET api/analytics/usage` (voir [ServeAnalyticsApi]) : le coût
 * estimé (le réel du fournisseur quand il le donne), les sessions, les jetons entrée/sortie, la
 * part servie par le cache, les jetons **jour par jour** et les modèles. Aucune valeur n'est
 * calculée à partir d'autre chose que ce rapport : quand la route manque ou que la réponse est
 * illisible, l'écran le dit et **n'affiche aucune carte** — un coût faux serait pire que pas de
 * coût.
 *
 * La période est mémorisée par l'appelant ([onSelectPeriod]) : elle survit à une relance.
 */
@Composable
fun InsightsScreen(
    server: ServerConfig,
    period: InsightsPeriod,
    onSelectPeriod: (InsightsPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // `null` : pas encore de réponse. Un rechargement repart de là (l'écran montre qu'il attend).
    var outcome by remember(server.id) { mutableStateOf<UsageOutcome?>(null) }
    var attempt by remember(server.id) { mutableIntStateOf(0) }

    // Une seule voie de chargement : la période, le serveur ou le compteur d'essais changent, on
    // relit. Le serveur borne lui-même `days` à 1…365.
    LaunchedEffect(server.id, server.configSignature, period, attempt) {
        outcome = null
        outcome = ServeAnalyticsApi(ServeClient(server)).usage(period.days)
    }

    val report = (outcome as? UsageOutcome.Ready)?.report

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Insights", onBack = onBack)
            InsightsSubtitle(report = report, period = period, place = placeOf(server))
            AdaptiveSegmented(
                options = InsightsPeriod.entries.map { it.label },
                selectedIndex = InsightsPeriod.entries.indexOf(period),
                onSelect = { index -> onSelectPeriod(InsightsPeriod.entries[index]) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                // Pas encore de réponse : l'écran ne montre rien d'autre qu'une ligne d'attente.
                outcome == null -> Notice("Loading the insights…")

                outcome is UsageOutcome.Missing -> OnyxEmptyState(
                    Symbols.WifiExclamation,
                    "The analytics route isn’t available on this Hermes.",
                    description = "This profile only, not every profile.",
                )

                outcome is UsageOutcome.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OnyxEmptyState(
                        Symbols.ExclamationTriangle,
                        "Couldn’t load the insights",
                        description = (outcome as UsageOutcome.Failed).message,
                    )
                    BasicText(
                        text = "Try again",
                        style = theme.body.copy(color = signalInk(theme, theme.formPageColor)),
                        modifier = Modifier.onyxTappable { attempt++ },
                    )
                }

                report != null && report.isEmpty -> OnyxEmptyState(
                    Symbols.ChartBar,
                    "Nothing used yet",
                    description = "Chats from this period, and what they cost, show up here.",
                )

                report != null -> {
                    CostHeadline(report.totals)
                    Tiles(report.totals)
                    val days = remember(report) { report.dailySeries(todayEpochDay()) }
                    if (days.any { it.tokens > 0 }) DailyChart(days)
                    RankedModels(report.byModel)
                    BasicText(
                        text = "This profile only, not every profile.",
                        style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor, theme.tertiaryText)),
                    )
                }
            }
        }
    }
}

/**
 * « 3 oct. – 30 oct. · http://10.0.2.2:8890 » — la période **réellement couverte** et l'adresse où
 * les chiffres ont été lus, sous le titre.
 *
 * Tant que le rapport n'est pas là, la fenêtre est celle demandée : le titre ne saute pas quand il
 * arrive.
 */
@Composable
private fun InsightsSubtitle(report: UsageReport?, period: InsightsPeriod, place: String) {
    val theme = LocalOnyxTheme.current
    val today = todayEpochDay()
    val range = remember(report, period, today) {
        val days = report?.dailySeries(today).orEmpty()
        if (days.isEmpty()) {
            isoDateOf(today - period.days + 1) to
                isoDateOf(today)
        } else {
            days.first().day to days.last().day
        }
    }
    BasicText(
        text = listOf("${shortDate(range.first)} – ${shortDate(range.second)}", place)
            .filter { it.isNotEmpty() }
            .joinToString(" · "),
        localize = false,
        style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor), textAlign = TextAlign.Center),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    )
}

/** Le grand nombre : le coût de la période, puis ce qu'il est (facturé ou estimé). */
@Composable
private fun CostHeadline(totals: UsageTotals) {
    val theme = LocalOnyxTheme.current
    val billed = InsightsFormat.isBilled(totals.actualCost)
    Column(
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            text = InsightsFormat.usd(totals.cost),
            localize = false,
            // Le nombre se rétrécit au lieu de se replier au milieu d'un montant.
            style = theme.largeTitle.copy(
                color = theme.textColor,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            // L'auto-ajustement ne sort pas de l'échelle : deux crans, 26 et 30, pas un continuum
            // de 24 à 44 (qui produisait 31,2 / 37,5 / 41 — quatre corps de plus).
            autoSize = TextAutoSize.StepBased(
                minFontSize = OnyxSocle.Heading,
                maxFontSize = OnyxSocle.Display,
                stepSize = (OnyxSocle.Display.value - OnyxSocle.Heading.value).sp,
            ),
        )
        BasicText(
            text = if (billed) "billed" else "Estimated cost",
            style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)),
        )
    }
}

/**
 * Les trois cartes du haut : les sessions, les jetons entrée/sortie et la part venue du cache.
 *
 * Trois de front tant qu'elles ont la place ; une par ligne dès que la police agrandit les mots ou
 * que la page est étroite — trois boîtes écrasées ne se lisent pas.
 */
@Composable
private fun Tiles(totals: UsageTotals) {
    val tiles = listOf(
        InsightsFormat.tokens(totals.sessions.toLong()) to "Sessions",
        InsightsFormat.tokens(totals.tokens) to "Tokens in and out",
        InsightsFormat.percent(totals.cachePercent) to "Prompt from cache",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (LocalDensity.current.fontScale > 1.3f || maxWidth / 3 < 96.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tiles.forEach { (value, caption) -> Tile(value, caption, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tiles.forEach { (value, caption) ->
                    Tile(value, caption, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Un nombre de tête et sa légende, sur la surface des formulaires. */
@Composable
private fun Tile(value: String, note: String, modifier: Modifier) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(OnyxSpace.FormCorner)
    Column(
        modifier = modifier
            .background(theme.formSurfaceColor, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            text = value,
            localize = false,
            style = theme.title3.copy(color = theme.primaryText, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        BasicText(text = note, style = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor)), maxLines = 2)
    }
}

/**
 * « Jetons par jour » : une barre par jour sur une ligne de base commune, les jours sans usage
 * compris (une barre absente veut dire « rien ce jour-là »).
 *
 * Le pic est annoté, et **un appui sur un jour** montre ses chiffres (jetons, coût, sessions) en
 * tête de carte ; les autres jours reculent alors, ce qui marche quel que soit l'accent.
 *
 * Le dessin est **au repos** : aucune animation, donc rien à figer sous « Réduire les animations » —
 * la géométrie ne dépend que des chiffres ([barFraction]).
 */
@Composable
private fun DailyChart(days: List<UsageDay>) {
    val theme = LocalOnyxTheme.current
    val peakDay = remember(days) { peakOf(days) }
    var picked by remember(days) { mutableStateOf<UsageDay?>(null) }
    val gap = if (days.size > 45) 1.dp else 3.dp
    val shape = RoundedCornerShape(OnyxSpace.FormCorner)
    val peak = peakDay?.tokens?.coerceAtLeast(1) ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.formSurfaceColor, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Tokens by day",
                style = theme.headline.copy(color = theme.primaryText),
                modifier = Modifier.semantics { heading() },
            )
            peakDay?.let { day ->
                BasicText(
                    text = listOf(
                        OnyxLocalization["Peak"],
                        InsightsFormat.tokens(day.tokens),
                        "·",
                        shortDate(day.day),
                    ).joinToString(" "),
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor)),
                )
            }
        }
        // Toujours là, pour qu'un choix ne pousse pas les barres vers le bas ; elle dit que les
        // barres peuvent être touchées tant qu'aucune ne l'est.
        BasicText(
            // L'invite passe par le dictionnaire : rendue verbatim, elle restait en anglais au
            // milieu d'un écran français.
            text = picked?.let { day ->
                listOf(
                    shortDate(day.day),
                    OnyxLocalization.format("%s tokens", InsightsFormat.tokens(day.tokens)),
                    InsightsFormat.usd(day.cost),
                    OnyxLocalization.format(
                        if (day.sessions == 1) "1 session" else "%d sessions",
                        day.sessions,
                    ),
                ).joinToString(" · ")
            } ?: OnyxLocalization["Tap a day for its numbers."],
            localize = false,
            style = theme.footnote.copy(
                color = bodyInk(
                    theme,
                    theme.formSurfaceColor,
                    if (picked != null) theme.textColor else theme.tertiaryText,
                ),
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            val panel = theme.formSurfaceColor
            // Les trois teintes d'une barre, **mesurées** : le pic et le jour choisi à l'accent
            // plein, les autres reculés — jamais sous 3:1, quel que soit le thème.
            val full = barInk(theme, panel)
            val rest = recededInk(theme, panel)
            days.forEach { day ->
                val fraction = barFraction(day, peak)
                // Le pic se détache parce que les autres reculent — et non parce qu'il change de
                // couleur, ce qui tiendrait à l'accent du thème.
                val ink = when {
                    picked != null -> if (picked == day) full else rest
                    day == peakDay -> full
                    else -> rest
                }
                // Toute la colonne est la cible tactile, pas seulement la barre : 120 dp de haut
                // sur la largeur d'un jour dépasse largement les 44 dp exigés.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = "${shortDate(day.day)}: ${InsightsFormat.tokens(day.tokens)}"
                            selected = picked == day
                        }
                        .onyxTappable { picked = if (picked == day) null else day },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .background(
                                    ink,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
                                ),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val dates = theme.caption.copy(color = bodyInk(theme, theme.formSurfaceColor, theme.tertiaryText))
            BasicText(
                text = shortDate(days.first().day),
                localize = false,
                style = dates,
                modifier = Modifier.weight(1f),
            )
            BasicText(text = shortDate(days.last().day), localize = false, style = dates)
        }
    }
}

/**
 * Les modèles par ordre de jetons, en barres horizontales, chacune à l'échelle du premier.
 *
 * Les modèles sans un seul jeton ne s'affichent pas : une barre de rien ne dit rien, et un modèle
 * qui n'a servi à rien n'a pas sa place dans un classement.
 */
@Composable
private fun RankedModels(models: List<ModelUsage>) {
    val theme = LocalOnyxTheme.current
    val shown = remember(models) { models.filter { it.tokens > 0 }.sortedByDescending { it.tokens }.take(5) }
    if (shown.isEmpty()) return
    val top = shown.first().tokens.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            text = "Models",
            style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)),
            modifier = Modifier.semantics { heading() },
        )
        shown.forEach { model ->
            Column(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IdentifierText(
                        text = model.model,
                        style = theme.body.copy(color = theme.textColor),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = OnyxLocalization.format("%s tokens", InsightsFormat.tokens(model.tokens)),
                        localize = false,
                        style = theme.caption.copy(color = bodyInk(theme, theme.formPageColor)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(theme.fill, CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction(model.tokens, top))
                            .fillMaxHeight()
                            .background(barInk(theme, OnyxContrast.over(theme.fill, theme.formSurfaceColor)), CircleShape),
                    )
                }
            }
        }
    }
}

/** Une ligne d'attente, discrète. */
@Composable
private fun Notice(text: String) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = theme.footnote.copy(color = bodyInk(theme, theme.formPageColor)))
    }
}

/** L'adresse des chiffres : celle du Dashboard, sinon le nom du serveur. */
private fun placeOf(server: ServerConfig): String =
    server.serveBase.ifEmpty { server.displayName }

/** Le jour courant, en jours depuis 1970-01-01 (le fuseau de l'appareil). */
internal fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

/**
 * La hauteur d'une barre, en fraction de la plus haute : **pure**, donc éprouvable sans écran.
 *
 * Un jour à zéro donne **exactement** 0 (la barre n'est pas dessinée) ; un pic de zéro non plus,
 * puisqu'on ne divise jamais par zéro. Rien ne dépend du temps : c'est la même valeur à chaque
 * composition, ce qui est ce qui rend « Réduire les animations » sans effet sur ce dessin.
 */
internal fun barFraction(tokens: Long, peak: Long): Float {
    if (tokens <= 0L || peak <= 0L) return 0f
    return (tokens.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
}

/** La même règle, pour un jour et un pic de la série. */
internal fun barFraction(day: UsageDay, peak: Long): Float = barFraction(day.tokens, peak)
