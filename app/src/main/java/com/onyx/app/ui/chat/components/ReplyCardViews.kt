package com.onyx.app.ui.chat.components

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.data.model.ReplyCard
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.cards.ReplyCardFormat
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxProgressView
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Les cartes d'une réponse sous ses mots (`ReplyCards`): une carte, une liste de lieux, la météo,
 * l'heure — comme `ReplyCardsView`.
 *
 * La carte, elle, n'est pas un port: MapKit n'existe pas sur Android et ses équivalents sont
 * payants. Elle est donc **composée** sur des tuiles OpenStreetMap ([OsmStaticMap]) — cadre calculé,
 * épingles dessinées, attribution obligatoire, et repli sur la liste des lieux quand le réseau
 * manque.
 *
 * Tout se dessine avec le thème choisi et la langue du système: aucune chaîne en dur, aucune
 * couleur en dur hors des teintes de la météo (qui ne sont pas des couleurs de thème), et rien ne
 * bouge sous « Réduire les animations ».
 */
@Composable
fun ReplyCardsView(
    cards: List<ReplyCard>,
    modifier: Modifier = Modifier,
    /** La réponse arrive encore: une carte météo ou d'heure dit qu'elle est en cours de recherche. */
    pending: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cards.forEach { card ->
            when (card) {
                is ReplyCard.Map -> MapCard(card)
                is ReplyCard.Places -> PlacesCard(card)
                is ReplyCard.Weather -> WeatherCard(card, pending = pending)
                is ReplyCard.Clock -> TimeCard(card, pending = pending)
            }
        }
    }
}

/** Le cadre de chaque carte: le `CardBox` (fond secondaire, coins de 16, filet de 0,5). */
@Composable
private fun CardBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.surfaceColor)
            .border(0.5.dp, theme.separator.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Un filet, à la largeur de la carte. */
@Composable
private fun CardDivider() {
    val theme = LocalOnyxTheme.current
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.separator))
}

// MARK: - La carte

@Composable
private fun MapCard(card: ReplyCard.Map) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var unavailable by remember(card) { mutableStateOf(false) }
    val located = card.places.filter { it.hasCoordinate }
    val route = card.route
    val routePoints = remember(route) {
        listOfNotNull(route?.from?.takeIf { it.hasCoordinate }, route?.to?.takeIf { it.hasCoordinate })
    }

    CardBox {
        card.title?.let { BasicText(text = it, localize = false, style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold)) }
        val canDrawMap = located.isNotEmpty() || routePoints.isNotEmpty()
        if (canDrawMap && !unavailable) {
            OsmStaticMap(
                points = (located + routePoints.filterNot { point -> located.any { it == point } })
                    .map { MapPoint(it.latitude!!, it.longitude!!, flag = route?.to == it) },
                height = 190.dp,
                line = if (routePoints.size >= 2) routePoints.map { MapPoint(it.latitude!!, it.longitude!!) } else emptyList(),
                onOpen = { openMaps(context, route?.let { ReplyCardFormat.routeUri(it) } ?: ReplyCardFormat.mapsUri(card.places)) },
                onUnavailable = { unavailable = true },
            )
        }
        // Pas de coordonnées, ou pas de réseau: la liste des lieux à la place, jamais un cadre vide.
        if (!canDrawMap || unavailable) {
            if (unavailable) {
                BasicText(
                    text = "Map unavailable",
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
            if (card.places.isNotEmpty()) PlaceFallbackList(card.places)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (route != null) {
                OnyxIcon(symbol = modeSymbol(route.mode), tint = theme.secondaryText, size = 14.dp)
                BasicText(
                    text = ReplyCardFormat.modeName(route.mode),
                    style = theme.footnote.copy(fontWeight = FontWeight.Medium),
                )
                val from = route.from?.name
                val straight = route.from?.takeIf { it.hasCoordinate }?.takeIf { route.to.hasCoordinate }?.let { place ->
                    ReplyCardFormat.straightLineMeters(place.latitude!!, place.longitude!!, route.to.latitude!!, route.to.longitude!!)
                }
                val line = buildString {
                    append(
                        if (from != null) OnyxLocalization.format("%1\$s to %2\$s", from, route.to.name)
                        else OnyxLocalization.format("From here to %1\$s", route.to.name),
                    )
                    straight?.let { append(" · ${OnyxLocalization.format("≈ %1\$s straight line", ReplyCardFormat.distance(it))}") }
                }
                BasicText(
                    text = line,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Le texte cède la place au bouton: mesuré après lui, il ne peut pas le rogner.
                    modifier = Modifier.weight(1f, fill = false),
                )
                CardButton(
                    text = "Directions",
                    symbol = Symbols.ArrowTriangleTurnUpRightDiamond,
                    enabled = ReplyCardFormat.routeUri(route) != null,
                ) { openMaps(context, ReplyCardFormat.routeUri(route)) }
            } else {
                BasicText(
                    text = card.places.joinToString(", ") { it.name },
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CardButton(text = "Open in Maps", symbol = Symbols.Map, enabled = located.isNotEmpty()) {
                    openMaps(context, ReplyCardFormat.mapsUri(card.places))
                }
            }
        }
    }
}

/** Le repli d'une carte sans tuiles: les lieux, en clair. */
@Composable
private fun PlaceFallbackList(places: List<ReplyCard.Place>) {
    val theme = LocalOnyxTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        places.forEachIndexed { index, place ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                BasicText(
                    text = "${index + 1}",
                    localize = false,
                    style = theme.caption2.copy(fontWeight = FontWeight.Bold, color = theme.accent),
                    modifier = Modifier.width(16.dp),
                )
                Column {
                    BasicText(
                        text = place.name,
                        localize = false,
                        style = theme.footnote.copy(fontWeight = FontWeight.Medium),
                    )
                    place.address?.let {
                        BasicText(
                            text = it,
                            localize = false,
                            style = theme.caption.copy(color = theme.secondaryText),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Les lieux trouvés

@Composable
private fun PlacesCard(card: ReplyCard.Places) {
    val theme = LocalOnyxTheme.current
    var unavailable by remember(card) { mutableStateOf(false) }
    val located = card.places.filter { it.hasCoordinate }
    val context = LocalContext.current

    CardBox {
        card.title?.let { BasicText(text = it, localize = false, style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold)) }
        if (card.places.size > 1 && located.isNotEmpty() && !unavailable) {
            OsmStaticMap(
                points = card.places.mapIndexedNotNull { index, place ->
                    place.takeIf { it.hasCoordinate }?.let {
                        MapPoint(it.latitude!!, it.longitude!!, number = index + 1)
                    }
                },
                height = 140.dp,
                numbered = true,
                onOpen = { openMaps(context, ReplyCardFormat.mapsUri(card.places)) },
                onUnavailable = { unavailable = true },
            )
        }
        card.places.forEachIndexed { index, place ->
            if (index > 0) CardDivider()
            PlaceRow(place = place, number = index + 1)
        }
    }
}

/** Un lieu de la liste: son numéro, son nom, sa note, ses actions — comme `PlacesCard.row`. */
@Composable
private fun PlaceRow(place: ReplyCard.Place, number: Int) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(theme.accent),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "$number",
                localize = false,
                style = theme.caption.copy(fontWeight = FontWeight.Bold, color = theme.userBubbleText),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            BasicText(text = place.name, localize = false, style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold))
            val line = listOfNotNull(place.category, place.rating?.let { "★ " + String.format(Locale.US, "%.1f", it) })
            if (line.isNotEmpty()) {
                BasicText(text = line.joinToString(" · "), localize = false, style = theme.caption.copy(color = theme.secondaryText))
            }
            place.address?.let {
                BasicText(text = it, localize = false, style = theme.caption.copy(color = theme.secondaryText), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            place.note?.let {
                BasicText(text = it, localize = false, style = theme.caption, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            place.phone?.takeIf { digits(it).isNotEmpty() }?.let { phone ->
                OnyxIcon(
                    symbol = Symbols.PhoneFill,
                    tint = theme.accent,
                    size = 16.dp,
                    contentDescription = OnyxLocalization.format("Call %1\$s", place.name),
                    modifier = Modifier.onyxClickable(pressedScale = 0.9f) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${digits(phone)}")))
                        }
                    },
                )
            }
            place.url?.let { site ->
                OnyxIcon(
                    symbol = Symbols.Safari,
                    tint = theme.accent,
                    size = 16.dp,
                    contentDescription = OnyxLocalization.format("%1\$s's website", place.name),
                    modifier = Modifier.onyxClickable(pressedScale = 0.9f) { openUrl(context, site) },
                )
            }
            if (place.hasCoordinate) {
                OnyxIcon(
                    symbol = Symbols.Map,
                    tint = theme.accent,
                    size = 16.dp,
                    contentDescription = OnyxLocalization.format("Open %1\$s in Maps", place.name),
                    modifier = Modifier.onyxClickable(pressedScale = 0.9f) {
                        openMaps(context, ReplyCardFormat.mapsUri(listOf(place)))
                    },
                )
            }
        }
    }
}

// MARK: - La météo

@Composable
private fun WeatherCard(card: ReplyCard.Weather, pending: Boolean) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current

    if (!card.hasReading) {
        // Que son lieu: la prévision est en route (le plugin la cherche, la réponse arrive).
        if (pending) {
            CardBox {
                LookingUp(OnyxLocalization.format("Looking up the weather in %1\$s…", card.place))
            }
        }
        return
    }

    CardBox {
        card.now?.let { now ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnyxIcon(
                    symbol = conditionSymbol(now.condition, now.night == true),
                    tint = conditionTint(now.condition, theme),
                    size = 40.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(text = card.place, localize = false, style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold))
                    BasicText(
                        text = now.summary ?: ReplyCardFormat.conditionName(now.condition),
                        style = theme.caption.copy(color = theme.secondaryText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(
                        text = ReplyCardFormat.degrees(now.temperature),
                        localize = false,
                        style = theme.largeTitle.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    )
                    val high = now.high
                    val low = now.low
                    if (high != null && low != null) {
                        BasicText(
                            text = "H ${ReplyCardFormat.degrees(high)}  L ${ReplyCardFormat.degrees(low)}",
                            localize = false,
                            style = theme.caption.copy(color = theme.secondaryText, fontFeatureSettings = "tnum"),
                        )
                    }
                }
            }
            val details = listOfNotNull(
                now.feelsLike?.let { OnyxLocalization.format("Feels like %1\$s", ReplyCardFormat.degrees(it)) },
                now.wind?.let { OnyxLocalization.format("Wind %1\$s", it) },
                now.humidity?.let { OnyxLocalization.format("Humidity %1\$d%%", it) },
                now.precipitation?.let { OnyxLocalization.format("Rain %1\$d%%", it) },
            )
            if (details.isNotEmpty()) {
                BasicText(
                    text = details.joinToString(" · "),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                )
            }
        } ?: run {
            BasicText(text = card.place, localize = false, style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold))
        }

        if (card.hours.isNotEmpty()) {
            CardDivider()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                card.hours.forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(
                            text = ReplyCardFormat.hourLabel(hour.time),
                            localize = false,
                            style = theme.caption2.copy(color = theme.secondaryText),
                            maxLines = 1,
                        )
                        OnyxIcon(symbol = conditionSymbol(hour.condition, hour.night == true), tint = conditionTint(hour.condition, theme), size = 17.dp)
                        BasicText(
                            text = ReplyCardFormat.degrees(hour.temperature),
                            localize = false,
                            style = theme.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                        )
                        if ((hour.precipitation ?: 0) > 0) {
                            BasicText(
                                text = "${hour.precipitation}%",
                                localize = false,
                                style = theme.caption2.copy(color = conditionTint(ReplyCard.Condition.RAIN, theme)),
                            )
                        }
                    }
                }
            }
        }

        if (card.days.isNotEmpty()) {
            CardDivider()
            val low = card.days.minOf { it.low }
            val high = card.days.maxOf { it.high }
            // Les colonnes fixes suivent l'échelle de police du téléphone: sans cela, « Aujourd'hui »
            // serait rogné en français et « 21° » le serait à 1,3×.
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            card.days.forEach { day ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = ReplyCardFormat.dayLabel(day.date),
                        localize = false,
                        style = theme.caption.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width((68 * fontScale).dp),
                    )
                    OnyxIcon(symbol = conditionSymbol(day.condition, false), tint = conditionTint(day.condition, theme), size = 16.dp)
                    BasicText(
                        text = (day.precipitation ?: 0).takeIf { it > 0 }?.let { "$it%" } ?: "",
                        localize = false,
                        style = theme.caption2.copy(color = conditionTint(ReplyCard.Condition.RAIN, theme)),
                        modifier = Modifier.width((34 * fontScale).dp),
                    )
                    BasicText(
                        text = ReplyCardFormat.degrees(day.low),
                        localize = false,
                        style = theme.caption.copy(color = theme.secondaryText, fontFeatureSettings = "tnum"),
                        modifier = Modifier.width((34 * fontScale).dp),
                    )
                    TemperatureBar(
                        low = day.low,
                        high = day.high,
                        from = low,
                        to = high,
                        modifier = Modifier.weight(1f).height(5.dp),
                    )
                    BasicText(
                        text = ReplyCardFormat.degrees(day.high),
                        localize = false,
                        style = theme.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                        modifier = Modifier.width((38 * fontScale).dp),
                    )
                }
            }
        }

        card.source?.let { source ->
            if (source == com.onyx.app.data.model.ReplyCards.lookedUpSource) {
                // Les données d'Open-Meteo sont sous CC BY 4.0: créditées, avec un lien.
                BasicText(
                    text = "Weather data by Open-Meteo.com",
                    style = theme.caption2.copy(color = theme.tertiaryText),
                    modifier = Modifier.onyxClickable(pressedScale = 0.97f) { openUrl(context, "https://open-meteo.com/") },
                )
            } else {
                BasicText(text = source, localize = false, style = theme.caption2.copy(color = theme.tertiaryText))
            }
        }
    }
}

/** La plage de températures d'un jour, sur l'échelle de toute la prévision. */
@Composable
private fun TemperatureBar(low: Double, high: Double, from: Double, to: Double, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    Canvas(modifier = modifier) {
        val span = (to - from).coerceAtLeast(1.0)
        val start = ((low - from) / span * size.width).toFloat()
        val width = ((high - low) / span * size.width).toFloat().coerceAtLeast(4f)
        val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = theme.separator, cornerRadius = radius, size = size)
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(Color(0xFF22D3EE), Color(0xFFFACC15), Color(0xFFF97316)),
                startX = start,
                endX = start + width,
            ),
            topLeft = Offset(start, 0f),
            cornerRadius = radius,
            size = Size(width, size.height),
        )
    }
}

// MARK: - L'heure

@Composable
private fun TimeCard(card: ReplyCard.Clock, pending: Boolean) {
    val theme = LocalOnyxTheme.current
    val system = ZoneId.systemDefault()
    val zone = card.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: if (card.place == null) system else null

    if (zone == null) {
        if (card.place != null && pending) {
            CardBox {
                LookingUp(OnyxLocalization.format("Looking up the time in %1\$s…", card.place!!))
            }
        }
        return
    }

    val now = minuteTick(zone)
    val away = card.place != null && zone != system
    val second = if (away) {
        val offsetSeconds = zone.rules.getOffset(now.toInstant()).totalSeconds - system.rules.getOffset(now.toInstant()).totalSeconds
        ReplyCard.Clock.difference(offsetSeconds)
    } else {
        now.format(DateTimeFormatter.ofPattern("z", Locale.getDefault()))
    }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClockFace(hour = now.hour, minute = now.minute, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = card.place ?: "Your time",
                    style = theme.subheadline.copy(fontWeight = FontWeight.SemiBold),
                )
                BasicText(
                    text = ReplyCardFormat.longDate(now.toLocalDate()),
                    localize = false,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                BasicText(
                    text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    localize = false,
                    style = theme.largeTitle.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
                BasicText(
                    text = second,
                    localize = true,
                    style = theme.caption.copy(color = theme.secondaryText),
                    maxLines = 1,
                )
            }
        }
    }
}

/** La petite horloge à aiguilles, sombre la nuit là-bas — `ClockFace`. */
@Composable
private fun ClockFace(hour: Int, minute: Int, modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val night = hour < 6 || hour >= 19
    val handColor = if (night) Color.White else theme.textColor
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(color = if (night) Color(0xCC3F51B5) else theme.secondaryText.copy(alpha = 0.12f), radius = radius)
        for (tick in 0 until 12) {
            val angle = tick / 12.0 * 2 * Math.PI
            drawCircle(
                color = if (night) Color.White.copy(alpha = 0.6f) else theme.secondaryText,
                radius = 1.2.dp.toPx(),
                center = Offset(
                    centre.x + Math.cos(angle).toFloat() * radius * 0.8f,
                    centre.y + Math.sin(angle).toFloat() * radius * 0.8f,
                ),
            )
        }
        fun hand(turn: Double, length: Float, width: Float) {
            val angle = turn * 2 * Math.PI - Math.PI / 2
            drawLine(
                color = handColor,
                start = centre,
                end = Offset(
                    centre.x + Math.cos(angle).toFloat() * radius * length,
                    centre.y + Math.sin(angle).toFloat() * radius * length,
                ),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
        hand(((hour % 12) + minute / 60.0) / 12.0, 0.5f, 2.5.dp.toPx())
        hand(minute / 60.0, 0.72f, 1.6.dp.toPx())
    }
}

/** L'heure, rafraîchie à chaque minute (le `TimelineView(.everyMinute)` ). */
@Composable
private fun minuteTick(zone: ZoneId): ZonedDateTime {
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            val toNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(toNextMinute.coerceAtLeast(250L))
        }
    }
    return now
}

// MARK: - Des morceaux communs

/** Une carte qui cherche encore: la météo ou l'heure d'un lieu. */
@Composable
private fun LookingUp(text: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OnyxProgressView(size = 16.dp, tint = theme.accent)
        BasicText(text = text, localize = false, style = theme.subheadline.copy(color = theme.secondaryText))
    }
}

/** Un bouton de carte, à plat et sans fond: « Directions », « Open in Maps ». */
@Composable
private fun CardButton(text: String, symbol: Int, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .onyxClickable(enabled = enabled, pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OnyxIcon(symbol = symbol, tint = if (enabled) theme.accent else theme.tertiaryText, size = 13.dp)
        BasicText(
            text = text,
            style = theme.footnote.copy(fontWeight = FontWeight.SemiBold, color = if (enabled) theme.accent else theme.tertiaryText),
            maxLines = 1,
        )
    }
}

/** Le symbole d'un temps (`WeatherCard.symbol`). */
private fun conditionSymbol(condition: ReplyCard.Condition, night: Boolean): Int = when (condition) {
    ReplyCard.Condition.CLEAR -> if (night) Symbols.MoonFill else Symbols.SunMax
    ReplyCard.Condition.PARTLY_CLOUDY -> if (night) Symbols.CloudMoon else Symbols.CloudSun
    ReplyCard.Condition.CLOUDY -> Symbols.Cloud
    ReplyCard.Condition.FOG -> Symbols.CloudFog
    ReplyCard.Condition.DRIZZLE -> Symbols.CloudDrizzle
    ReplyCard.Condition.RAIN -> Symbols.CloudRain
    ReplyCard.Condition.HEAVY_RAIN -> Symbols.CloudHeavyRain
    ReplyCard.Condition.THUNDERSTORM -> Symbols.CloudBoltRain
    ReplyCard.Condition.SNOW -> Symbols.CloudSnow
    ReplyCard.Condition.SLEET -> Symbols.CloudSleet
    ReplyCard.Condition.HAIL -> Symbols.CloudHail
    ReplyCard.Condition.WIND -> Symbols.Wind
    ReplyCard.Condition.UNKNOWN -> Symbols.ThermometerMedium
}

/**
 * La teinte d'un temps. dessine ses symboles en couleurs (`symbolRenderingMode(.multicolor)`)
 * avec les couleurs du système; ce sont des couleurs de météo, pas de thème: elles restent donc
 * fixes, éclaircies en apparence sombre pour rester lisibles.
 */
private fun conditionTint(condition: ReplyCard.Condition, theme: ResolvedTheme): Color {
    val dark = theme.dark
    return when (condition) {
        ReplyCard.Condition.CLEAR -> if (dark) Color(0xFFFFCC48) else Color(0xFFF5A700)
        ReplyCard.Condition.PARTLY_CLOUDY -> if (dark) Color(0xFFFFC94A) else Color(0xFFF0A400)
        ReplyCard.Condition.CLOUDY, ReplyCard.Condition.FOG -> if (dark) Color(0xFFB9C0CC) else Color(0xFF8A8F98)
        ReplyCard.Condition.DRIZZLE, ReplyCard.Condition.RAIN, ReplyCard.Condition.HEAVY_RAIN ->
            if (dark) Color(0xFF6BB4FF) else Color(0xFF1B74D8)
        ReplyCard.Condition.THUNDERSTORM -> if (dark) Color(0xFFFFD23F) else Color(0xFFE8A600)
        ReplyCard.Condition.SNOW, ReplyCard.Condition.SLEET, ReplyCard.Condition.HAIL ->
            if (dark) Color(0xFFA8D8FF) else Color(0xFF4C93D8)
        ReplyCard.Condition.WIND -> theme.secondaryText
        ReplyCard.Condition.UNKNOWN -> theme.secondaryText
    }
}

private fun modeSymbol(mode: ReplyCard.Route.Mode): Int = when (mode) {
    ReplyCard.Route.Mode.DRIVING -> Symbols.CarFill
    ReplyCard.Route.Mode.WALKING -> Symbols.FigureWalk
    ReplyCard.Route.Mode.TRANSIT -> Symbols.TramFill
    ReplyCard.Route.Mode.CYCLING -> Symbols.Bicycle
}

/** Les chiffres d'un téléphone, pour `tel:`. */
private fun digits(phone: String): String = phone.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

private fun openMaps(context: android.content.Context, uri: String?) {
    uri?.let { openUrl(context, it) }
}
