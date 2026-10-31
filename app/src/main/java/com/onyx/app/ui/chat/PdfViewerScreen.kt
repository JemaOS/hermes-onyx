package com.onyx.app.ui.chat

import com.onyx.app.ui.design.onyxPage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.navigationBarClearance
import com.onyx.app.ui.design.statusBarClearance
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Le fichier à lire : son adresse (cache du téléphone, ou pièce jointe), et son nom. */
data class PdfSource(val uri: Uri, val name: String)

/** Le zoom maximal d'une page, en multiple de la largeur de l'écran. */
private const val MaxPdfZoom = 4f

/** La largeur maximale d'une page rendue, en pixels : au-delà, le gain ne se voit plus. */
private const val MaxPagePixels = 2048

/**
 * Un PDF ouvert : ses pages, rendues à la demande.
 *
 * `PdfRenderer` ne rend qu'une page à la fois et n'est pas réentrant : un verrou sérialise les
 * rendus, et chaque page est refermée aussitôt dessinée. Le document se ferme avec l'écran.
 */
private class PdfDocument(private val renderer: PdfRenderer) {

    private val lock = Mutex()

    val pageCount: Int = runCatching { renderer.pageCount }.getOrDefault(0)

    /** La page [index], dessinée à la largeur demandée (bornée), ou `null` si elle ne se rend pas. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val page = renderer.openPage(index)
                try {
                    val ratio = if (page.width > 0) page.height.toFloat() / page.width else 1f
                    val width = widthPx.coerceIn(64, MaxPagePixels)
                    val height = (width * ratio).toInt().coerceIn(1, MaxPagePixels * 2)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Le papier reste blanc : c'est la page, pas le thème, qui donne sa couleur.
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { renderer.close() }
    }
}

/** Ouvre un PDF depuis son adresse : le cache de l'app, ou une pièce jointe reçue. */
private fun openDocument(context: Context, uri: Uri): PdfDocument? = runCatching {
    val descriptor: ParcelFileDescriptor? = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> ParcelFileDescriptor.open(
            File(uri.path.orEmpty()),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

        else -> context.contentResolver.openFileDescriptor(uri, "r")
    }
    descriptor?.let { PdfDocument(PdfRenderer(it)) }
}.getOrNull()

/**
 * Un PDF montré dans l'app : la première page tout de suite, puis les suivantes au glissement ou
 * par les flèches, avec double tap et pincement pour zoomer.
 *
 * Le rendu est entièrement hors ligne (`PdfRenderer` du système) : aucune police, aucune
 * bibliothèque à télécharger, et rien ne sort du téléphone. Un fichier illisible est dit en mots —
 * jamais une page blanche qui ferait croire à un succès.
 */
@Composable
fun PdfViewerScreen(
    source: PdfSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current

    var document by remember(source.uri) { mutableStateOf<PdfDocument?>(null) }
    var pages by remember(source.uri) { mutableIntStateOf(0) }
    var problem by remember(source.uri) { mutableStateOf<String?>(null) }
    var zoom by remember(source.uri) { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source.uri) {
        val opened = withContext(Dispatchers.IO) { openDocument(context, source.uri) }
        if (opened == null || opened.pageCount == 0) {
            opened?.close()
            problem = OnyxLocalization["This PDF can't be opened."]
        } else {
            document = opened
            pages = opened.pageCount
        }
    }

    // Le document ne survit pas à l'écran : les descripteurs du système se referment avec lui.
    DisposableEffect(source.uri) {
        onDispose { document?.close() }
    }

    val pager = rememberPagerState(pageCount = { PdfPaging.pageCount(pages) })
    val shown = pager.currentPage
    LaunchedEffect(shown) { zoom = 1f }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // La bande haute : le nom et la croix se posent sous la barre d'état.
                .statusBarClearance()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = source.name,
                localize = false,
                style = theme.subheadline.copy(color = theme.textColor),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            OnyxIcon(
                symbol = Symbols.XmarkCircle,
                tint = theme.secondaryText,
                size = 26.dp,
                contentDescription = "Close",
                modifier = Modifier.onyxClickable(onClick = onClose),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // La page se rend à la largeur de l'écran, doublée : nette jusqu'à 2×, sans coûter
            // une image par cran de zoom.
            val renderWidth = with(density) { (maxWidth * 2).toPx().toInt() }
            val problemText = problem
            when {
                problemText != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = problemText,
                        localize = false,
                        style = theme.footnote.copy(color = theme.secondaryText),
                        modifier = Modifier.padding(24.dp),
                    )
                }

                pages == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LiveMark(color = theme.secondaryText)
                }

                else -> HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PdfPage(
                        document = document,
                        page = page,
                        renderWidth = renderWidth,
                        zoom = if (page == shown) zoom else 1f,
                        onZoom = { zoom = it },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // La bande basse : les flèches et le compte de pages remontent au-dessus de la
                // zone de navigation, au lieu de tomber dessus.
                .navigationBarClearance()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Les flèches doublent le glissement : utile quand le zoom occupe toute la largeur.
            OnyxIcon(
                symbol = Symbols.ChevronRight,
                tint = if (PdfPaging.canStep(shown, pages, -1)) theme.accent else theme.quaternaryText,
                size = 18.dp,
                contentDescription = "Previous page",
                modifier = Modifier
                    .graphicsLayer { rotationZ = 180f }
                    .onyxClickable(enabled = PdfPaging.canStep(shown, pages, -1)) {
                        scope.launch { pager.animateScrollToPage(PdfPaging.clamp(shown - 1, pages)) }
                    },
            )
            BasicText(
                text = OnyxLocalization[PdfPaging.label(shown, pages)],
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier
                    .background(theme.fill, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics {
                        contentDescription = OnyxLocalization[PdfPaging.label(shown, pages)]
                    },
            )
            OnyxIcon(
                symbol = Symbols.ChevronRight,
                tint = if (PdfPaging.canStep(shown, pages, 1)) theme.accent else theme.quaternaryText,
                size = 18.dp,
                contentDescription = "Next page",
                modifier = Modifier.onyxClickable(enabled = PdfPaging.canStep(shown, pages, 1)) {
                    scope.launch { pager.animateScrollToPage(PdfPaging.clamp(shown + 1, pages)) }
                },
            )
            Box(modifier = Modifier.weight(1f))
            if (zoom > 1f) {
                BasicText(
                    text = "Fit",
                    style = theme.caption.copy(color = theme.accent),
                    modifier = Modifier
                        .onyxClickable { zoom = 1f }
                        .padding(6.dp),
                )
            }
        }
    }
}

/**
 * Une page : son image rendue, les gestes du zoom, et le **déplacement** quand la page est agrandie.
 *
 * À un doigt la page se déplace (tant qu'elle est zoomée) ; le carrousel garde la main quand elle
 * ne l'est pas, pour que tourner les pages reste possible. Le déplacement revient à zéro dès qu'on
 * repasse par « Fit », ou qu'on change de page : on ne garde jamais une page décalée sans le dire.
 */
@Composable
private fun PdfPage(
    document: PdfDocument?,
    page: Int,
    renderWidth: Int,
    zoom: Float,
    onZoom: (Float) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val bitmap by produceState<Bitmap?>(initialValue = null, page, renderWidth, document) {
        value = document?.render(page, renderWidth)
    }

    // La fenêtre du lecteur, en pixels : elle seule dit jusqu'où la page peut se déplacer.
    var viewport by remember(page) { mutableStateOf(IntSize.Zero) }
    // Le déplacement courant ; au repos, la page est centrée.
    var pan by remember(page) { mutableStateOf(Offset.Zero) }

    // Une page qui se rétrécit (retour à « Fit ») ou qui change ramène le déplacement dans ses
    // bornes — sinon la page resterait poussée hors de l'écran par un zoom qu'on vient d'annuler.
    LaunchedEffect(zoom, viewport) {
        pan = Offset(
            PdfPan.clamp(pan.x, viewport.width, zoom),
            PdfPan.clamp(pan.y, viewport.height, zoom),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(page, zoom) {
                // Le glissement ne prend la main que sur une page agrandie : autrement, il doit
                // rester au carrousel, sinon on ne pourrait plus tourner les pages.
                if (zoom > 1f) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pan = Offset(
                            PdfPan.clamp(pan.x + drag.x, viewport.width, zoom),
                            PdfPan.clamp(pan.y + drag.y, viewport.height, zoom),
                        )
                    }
                }
            }
            .pointerInput(page) {
                // Le pincement est le seul geste pris ici : à un doigt, le carrousel garde la main
                // pour tourner les pages.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        val change = event.calculateZoom()
                        if (change != 1f) {
                            onZoom((zoom * change).coerceIn(1f, MaxPdfZoom))
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                }
            }
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = { onZoom(if (zoom > 1f) 1f else 2f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        if (shown == null) {
            LiveMark(color = theme.secondaryText)
        } else {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        // Le déplacement lu ici, dans le calque : la page glisse sans recomposer le fil.
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
        }
    }
}
