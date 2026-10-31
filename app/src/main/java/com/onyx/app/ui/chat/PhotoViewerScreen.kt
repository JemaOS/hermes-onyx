package com.onyx.app.ui.chat

import com.onyx.app.ui.design.OnyxSocle
import com.onyx.app.ui.design.OnyxSpace

import android.net.Uri
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.onyx.app.localization.OnyxLocalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.navigationBarClearance
import com.onyx.app.ui.design.statusBarClearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Une image du visualiseur : où la lire, et ce qu'on écrit dessous. */
data class ViewerPhoto(val id: String, val uri: Uri, val caption: String = "", val name: String = "Photo")

/** Le zoom maximal d'une image, en multiple de la taille ajustée à l'écran. */
private const val MaxZoom = 5f

/**
 * Amortit le dépassement des bords : plus on tire au-delà de la limite, plus ça résiste, au lieu
 * de s'arrêter net. C'est le rebond élastique d'`UIScrollView`.
 */
private fun elasticEdge(value: Float, limit: Float): Float = when {
    value > limit -> limit + (value - limit) * 0.35f
    value < -limit -> -limit + (value + limit) * 0.35f
    else -> value
}

/**
 * Une image en plein écran (`PhotoViewer`).
 *
 * Fond noir, glissement latéral d'une image à l'autre, pincement ou double tap pour zoomer, et un
 * tap qui ne laisse que l'image. On ferme par la croix.
 */
@Composable
fun PhotoViewerScreen(
    photos: List<ViewerPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) {
        onClose()
        return
    }

    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex)) { photos.size }
    var bare by remember { mutableStateOf(false) }
    // Tirer l'image vers le bas la referme, comme la visionneuse de la référence.
    var dragY by remember { mutableFloatStateOf(0f) }
    val dragAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragY > 0f) (1f - dragY / 600f).coerceIn(0.3f, 1f) else 1f,
        label = "photoDrag",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(photos.size) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY > 120f) onClose() else dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { _, amount ->
                    dragY = (dragY + amount).coerceAtLeast(0f)
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragY; alpha = dragAlpha },
        ) { page ->
            ZoomablePhoto(
                photo = photos[page],
                // La page courante change : chaque image encore en mémoire repart à 1×.
                pageKey = pager.currentPage,
                onToggleBare = { bare = !bare },
            )
        }

        if (!bare) {
            // Le compte, puis la sortie.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // La croix descend **sous** la barre d'état : la marge vient de l'utilitaire
                    // commun des marges du système, pas d'un calcul local qui l'ignorait. Les
                    // 12 dp du bas portent le bouton (sa zone touchable est plus grande que
                    // l'icône) à plus de 8 dp sous la bande.
                    .statusBarClearance()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (photos.size > 1) {
                    BasicText(
                        text = "${pager.currentPage + 1} of ${photos.size}",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            // Énoncé pour lecteur d'écran, pas seulement la pastille visuelle.
                            .semantics {
                                contentDescription = com.onyx.app.localization.OnyxLocalization[
                                    "Photo ${pager.currentPage + 1} of ${photos.size}",
                                ]
                            },
                    )
                }
                Box(modifier = Modifier.weight(1f))
                OnyxIcon(
                    symbol = Symbols.XmarkCircle,
                    tint = Color.White.copy(alpha = 0.9f),
                    size = 28.dp,
                    modifier = Modifier.onyxClickable(onClick = onClose),
                    contentDescription = "Close",
                )
            }

            val caption = photos[pager.currentPage].caption
            if (caption.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // La légende du fichier se pose **au-dessus** de la zone de navigation.
                        .navigationBarClearance()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = caption,
                        localize = false,
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = OnyxSocle.Meta,
                        ),
                        maxLines = 2,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Une page : l'image, et les gestes du zoom. */
@Composable
private fun ZoomablePhoto(photo: ViewerPhoto, pageKey: Int, onToggleBare: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Le lecteur décode **borné** : le côté le plus long à la taille de l'écran, jamais l'image
    // entière. Une photo de 8000×6000 pèse 192 Mo décodée — le moteur de rendu refuse un tel
    // `Bitmap` et lève sur le fil principal, ce qui tuait le processus.
    // On ne dit « illisible » qu'une fois la tentative finie : une image lente ne doit pas
    // s'afficher en erreur pendant son décodage.
    var attempted by remember(photo.uri) { mutableStateOf(false) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.uri) {
        value = com.onyx.app.ui.chat.BoundedImages
            .decodeUriOffMain(
                context,
                photo.uri,
                com.onyx.app.ui.chat.BoundedImages.VIEWER_PX,
                com.onyx.app.ui.chat.BoundedImages.VIEWER_BYTES,
            )
            ?.asImageBitmap()
        attempted = true
    }

    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var settleTarget by remember(photo.id) { mutableStateOf<Offset?>(null) }
    // Le déplacement vit dans un état : le doigt l'emmène, et au relâchement il revient
    // doucement dans ses limites (rebond).
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var room by remember { mutableStateOf(IntSize.Zero) }

    // On a changé de page : l'image revient à 1×, sans décalage. Comme `setZoomScale(1)` de la référence.
    LaunchedEffect(pageKey) {
        scale = 1f
        offset = Offset.Zero
    }

    // Ce dont on peut déplacer l'image zoomée avant de toucher le bord.
    fun panLimit(atScale: Float): Offset {
        val w = room.width.toFloat()
        val h = room.height.toFloat()
        return Offset(maxOf(0f, (w * atScale - w) / 2f), maxOf(0f, (h * atScale - h) / 2f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { room = it }
            .pointerInput(photo.id) {
                // Détecteur écrit à la main : `detectTransformGestures` ne dit pas quand le geste
                // se termine, or c'est là qu'il faut ramener l'image dans ses bords.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var panning = false
                    var zooming = false
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (zoomChange != 1f) {
                            zooming = true
                            scale = (scale * zoomChange).coerceIn(1f, MaxZoom)
                        }
                        if (!panning && (zooming || panChange.getDistance() > viewConfiguration.touchSlop)) {
                            panning = true
                        }
                        if (panning && scale > 1f) {
                            val limit = panLimit(scale)
                            offset = Offset(
                                elasticEdge(offset.x + panChange.x, limit.x),
                                elasticEdge(offset.y + panChange.y, limit.y),
                            )
                        }
                        event.changes.forEach { change ->
                            if (zooming || (panning && scale > 1f && change.positionChanged())) {
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Le geste est fini : la pose dans les limites se fait hors du geste, car un
                    // geste de pointeur ne peut pas suspendre sur une animation.
                    if (scale <= 1f) {
                        scale = 1f
                        settleTarget = Offset.Zero
                    } else {
                        val limit = panLimit(scale)
                        settleTarget = Offset(
                            offset.x.coerceIn(-limit.x, limit.x),
                            offset.y.coerceIn(-limit.y, limit.y),
                        )
                    }
                }
            }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > 1f) {
                            // Déjà zoomé : retour au 1×, centré.
                            scale = 1f
                            settleTarget = Offset.Zero
                        } else {
                            // On zoome là où l'on a touché, pas au centre : on vise 2,5×, et on
                            // recadre pour que la zone touchée vienne au milieu.
                            val target = 2.5f
                            val center = Offset(room.width / 2f, room.height / 2f)
                            val limit = panLimit(target)
                            val wanted = (center - position) * target
                            val clamped = Offset(
                                wanted.x.coerceIn(-limit.x, limit.x),
                                wanted.y.coerceIn(-limit.y, limit.y),
                            )
                            scale = target
                            settleTarget = clamped
                        }
                    },
                    onTap = { onToggleBare() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
        // Un fichier illisible se **dit** : un écran noir sans un mot fait croire à une image
        // noire, et le propriétaire n'a aucun geste à faire. La carte nomme le fichier, la
        // raison, et rappelle le geste (le renvoyer depuis l'appareil).
        if (attempted && bitmap == null) {
            PhotoUnreadableCard(name = photo.name)
        }
    }
}

/** La carte d'une image qui n'a pas pu être lue — jamais un écran vide. */
@Composable
private fun PhotoUnreadableCard(name: String) {
    Column(
        modifier = Modifier
            .padding(28.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(OnyxSpace.TileCorner))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnyxIcon(
            symbol = Symbols.ExclamationTriangle,
            tint = Color.White.copy(alpha = 0.55f),
            size = 24.dp,
        )
        BasicText(
            text = OnyxLocalization["This image can't be read."],
            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
        )
        BasicText(
            text = name,
            localize = false,
            style = androidx.compose.ui.text.TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            text = OnyxLocalization["Its file is empty, cut short, or no longer on this device. Send it again from the device."],
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
