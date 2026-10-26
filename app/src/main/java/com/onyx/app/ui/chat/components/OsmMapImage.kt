package com.onyx.app.ui.chat.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.data.net.OnyxHttp
import com.onyx.app.ui.cards.OsmTiles
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Un point sur la carte statique: ses coordonnées, le numéro à écrire dedans quand la liste sous
 * la carte est numérotée, et s'il porte le drapeau d'arrivée d'un chemin.
 */
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val number: Int? = null,
    val flag: Boolean = false,
)

/**
 * Les tuiles OpenStreetMap, téléchargées une fois chacune.
 *
 * La politique d'usage d'OpenStreetMap demande un agent identifiable et interdit le téléchargement
 * massif: un petit cache LRU (96 tuiles) et un en-tête `User-Agent` explicite suffisent pour
 * quelques cartes statiques. Aucune clé d'API, aucun SDK payant.
 */
object OsmTileCache {

    private const val USER_AGENT = "Onyx/1.0.0 Android (OpenStreetMap tiles; com.onyx.app)"

    private val client = OnyxHttp.shortClient(12).newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private val cache = LruCache<String, ImageBitmap>(96)
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val gate = Semaphore(4)

    /** La tuile, du cache ou du réseau; `null` quand elle n'a pas pu être obtenue. */
    suspend fun tile(tile: OsmTiles.Tile, scope: CoroutineScope): ImageBitmap? {
        cache.get(tile.key)?.let { return it }
        val pending = synchronized(inFlight) {
            inFlight[tile.key] ?: scope.async(Dispatchers.IO) { fetch(tile) }.also { inFlight[tile.key] = it }
        }
        val bitmap = runCatching { pending.await() }.getOrNull()
        synchronized(inFlight) { inFlight.remove(tile.key) }
        bitmap?.let { cache.put(tile.key, it) }
        return bitmap
    }

    private suspend fun fetch(tile: OsmTiles.Tile): ImageBitmap? = gate.withPermit {
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(tile.url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

/**
 * La carte statique d'une réponse, bâtie sur des tuiles OpenStreetMap: le cadre est calculé sur la
 * boîte englobante des points ([OsmTiles]), les tuiles posées côte à côte, les épingles dessinées
 * par-dessus. Aucune interaction — elle vit dans un fil qui défile — mais un appui l'ouvre ailleurs
 * ([onOpen]).
 *
 * La carte est une **adaptation Android assumée**: la dessine avec MapKit, qui n'existe pas
 * ici et dont les équivalents sont payants. Les tuiles OSM sont libres et sans clé; l'attribution
 * « © OpenStreetMap » est obligatoire et toujours dessinée.
 *
 * Quand aucune tuile n'arrive (pas de réseau), [onUnavailable] est appelé: l'appelant dessine
 * alors la liste des lieux à la place, jamais un cadre vide.
 */
@Composable
fun OsmStaticMap(
    points: List<MapPoint>,
    height: Dp,
    modifier: Modifier = Modifier,
    /** Le chemin, tracé entre ses points (une ligne droite: sans moteur de routage). */
    line: List<MapPoint> = emptyList(),
    /** Les épingles portent leur numéro, comme la liste sous la carte. */
    numbered: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onUnavailable: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    val measurer = rememberTextMeasurer()
    var tiles by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surfaceColor)
            .then(if (onOpen != null) Modifier.onyxTappable(onClick = onOpen) else Modifier),
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val view = remember(points, widthPx, heightPx) {
            OsmTiles.Bounds.of(points.map { it.latitude to it.longitude })
                ?.let { OsmTiles.view(it, widthPx, heightPx) }
        }

        LaunchedEffect(view) {
            val planned = view ?: run { onUnavailable?.invoke(); return@LaunchedEffect }
            val loaded = coroutineScope {
                planned.tiles.map { tile -> async { tile.key to OsmTileCache.tile(tile, this) } }.awaitAll()
            }.mapNotNull { (key, bitmap) -> bitmap?.let { key to it } }.toMap()
            if (loaded.isEmpty()) {
                onUnavailable?.invoke()
            } else {
                tiles = loaded
            }
        }

        if (view == null) return@BoxWithConstraints

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (tile in view.tiles) {
                val image = tiles[tile.key] ?: continue
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(tile.left.toInt(), tile.top.toInt()),
                    dstSize = IntSize(image.width, image.height),
                )
            }
            if (line.size >= 2) {
                for (i in 0 until line.size - 1) {
                    val (ax, ay) = view.pixelOf(line[i].latitude, line[i].longitude)
                    val (bx, by) = view.pixelOf(line[i + 1].latitude, line[i + 1].longitude)
                    drawLine(
                        color = theme.accent,
                        start = Offset(ax, ay),
                        end = Offset(bx, by),
                        strokeWidth = 3.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)),
                    )
                }
            }
            points.forEach { point ->
                val (x, y) = view.pixelOf(point.latitude, point.longitude)
                val centre = Offset(x, y)
                val radius = if (numbered && point.number != null) 11.dp.toPx() else 9.dp.toPx()
                drawCircle(color = Color.White, radius = radius + 2.dp.toPx(), center = centre)
                drawCircle(color = if (point.flag) theme.secondaryText else theme.accent, radius = radius, center = centre)
                when {
                    // Le drapeau de l'arrivée, comme `Marker(systemImage: "flag.fill")`.
                    point.flag -> drawArrivalFlag(centre, radius, theme.secondaryText)
                    numbered && point.number != null -> {
                        val measured = measurer.measure(
                            text = point.number.toString(),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(centre.x - measured.size.width / 2f, centre.y - measured.size.height / 2f),
                        )
                    }

                    else -> drawCircle(color = Color.White, radius = radius * 0.38f, center = centre)
                }
            }
            // L'attribution: obligatoire, jamais omise, même sur une carte à moitié chargée.
            val label = measurer.measure(
                text = OsmTiles.attribution,
                style = TextStyle(fontSize = 9.sp, color = theme.tertiaryText),
            )
            if (tiles.isNotEmpty()) {
                val pad = 3.dp.toPx()
                drawRect(
                    color = theme.surfaceColor.copy(alpha = 0.82f),
                    topLeft = Offset(size.width - label.size.width - 2 * pad, size.height - label.size.height - 2 * pad),
                    size = Size(label.size.width + 2 * pad, label.size.height + 2 * pad),
                )
            }
            drawText(
                textLayoutResult = label,
                topLeft = Offset(size.width - label.size.width - 3.dp.toPx(), size.height - label.size.height - 3.dp.toPx()),
            )
        }
    }
}

/** Un drapeau d'arrivée, dessiné dans une épingle (`flag.fill`, blanc sur la pastille). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrivalFlag(
    centre: Offset,
    radius: Float,
    color: Color,
) {
    val top = centre.y - radius * 0.62f
    val bottom = centre.y + radius * 0.62f
    drawLine(
        color = Color.White,
        start = Offset(centre.x - radius * 0.28f, top),
        end = Offset(centre.x - radius * 0.28f, bottom),
        strokeWidth = 1.7.dp.toPx(),
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(centre.x - radius * 0.22f, top),
        size = Size(radius * 0.92f, radius * 0.66f),
    )
}
