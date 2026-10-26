package com.onyx.app.ui.chat

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/** La vidéo à lire: son adresse, et son nom, pour la barre du haut. */
data class VideoSource(val uri: Uri, val name: String)

/**
 * Lecteur d'une vidéo jointe.
 *
 * Fond noir, la vidéo au milieu, et une barre de transport de la maison — pas de `MediaController`
 * du système (il dessine du Material au milieu d'une app entièrement habillée à la main): le
 * rond lecture/pause, la piste qu'on fait glisser, et le temps écoulé sur la durée.
 *
 * La première image est celle de la vignette du fil: rien ne se lit avant l'appui sur lecture.
 */
@Composable
fun VideoPlayerScreen(
    source: VideoSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val duration = remember(source.uri) { mutableIntStateOf(0) }
    val position = remember(source.uri) { mutableIntStateOf(0) }
    var playing by remember(source.uri) { mutableStateOf(false) }
    var problem by remember(source.uri) { mutableStateOf<String?>(null) }
    var dragging by remember(source.uri) { mutableStateOf(false) }
    var dragFraction by remember(source.uri) { mutableFloatStateOf(0f) }
    var track by remember { mutableStateOf(IntSize.Zero) }
    // Le lecteur vit ici, tenu par l'écran: il se libère en partant, comme le `PdfRenderer`.
    var view by remember(source.uri) { mutableStateOf<VideoView?>(null) }

    // Ce que la vignette a déjà lu est la durée: on l'annonce sans attendre la préparation,
    // puis le lecteur affine. Sert aussi de repli quand la préparation ne dit rien.
    LaunchedEffect(source.uri) {
        val known = VideoPoster.durationMillis(context, source.uri)
        if (known > 0L && duration.intValue <= 0) duration.intValue = known.toInt()
    }

    // Le temps avance tout seul pendant la lecture: le seul état relu par l'écran.
    LaunchedEffect(playing, view) {
        while (playing) {
            view?.let { position.intValue = it.currentPosition }
            delay(200)
        }
    }

    DisposableEffect(source.uri) {
        onDispose { view?.stopPlayback() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(android.graphics.Color.BLACK.let { androidx.compose.ui.graphics.Color(it) }),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OnyxIcon(
                    symbol = Symbols.XmarkCircle,
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    size = 26.dp,
                    contentDescription = "Close",
                    modifier = Modifier.onyxClickable(onClick = onClose),
                )
                BasicText(
                    text = source.name,
                    localize = false,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = theme.caption.copy(color = androidx.compose.ui.graphics.Color.White),
                    modifier = Modifier
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val problemText = problem
                if (problemText != null) {
                    BasicText(
                        text = problemText,
                        localize = false,
                        style = theme.footnote.copy(color = theme.secondaryText),
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { androidContext -> VideoView(androidContext) },
                        update = { video ->
                            if (view !== video) {
                                view = video
                                // Aucune barre du système: la nôtre est dessous.
                                video.setMediaController(null)
                                video.setOnPreparedListener { player ->
                                    player.isLooping = false
                                    val known = video.duration
                                    if (known > 0) duration.intValue = known
                                    video.seekTo(1)
                                }
                                video.setOnCompletionListener {
                                    playing = false
                                    position.intValue = duration.intValue
                                }
                                video.setOnErrorListener { _, _, _ ->
                                    playing = false
                                    problem = OnyxLocalization["This video can't be played."]
                                    true
                                }
                                runCatching { video.setVideoURI(source.uri) }.onFailure {
                                    problem = OnyxLocalization["This video can't be played."]
                                }
                            }
                        },
                    )
                    // La surface de la vidéo reste touchable: un appui démarre, un appui pendant
                    // la lecture met en pause. Le grand rond n'est peint que quand rien ne joue —
                    // c'est la vignette qui invite à appuyer, pas un contrôle qui reste collé.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onyxClickable {
                                val video = view ?: return@onyxClickable
                                if (playing) {
                                    video.pause()
                                    playing = false
                                } else {
                                    video.start()
                                    playing = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!playing) {
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(76.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                OnyxIcon(
                                    symbol = Symbols.Play,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    size = 26.dp,
                                    contentDescription = "Play",
                                )
                            }
                        }
                    }
                }
            }

            TransportBar(
                position = position.intValue,
                duration = duration.intValue,
                playing = playing,
                dragging = dragging,
                dragFraction = dragFraction,
                onTogglePlay = {
                    val video = view
                    if (video != null) {
                        if (playing) {
                            video.pause()
                            playing = false
                        } else {
                            video.start()
                            playing = true
                        }
                    }
                },
                onTrackResize = { track = it },
                onScrub = { fraction ->
                    dragging = true
                    dragFraction = fraction
                },
                onScrubEnd = {
                    val video = view
                    if (video != null && duration.intValue > 0) {
                        val target = (dragFraction.coerceIn(0f, 1f) * duration.intValue).toInt()
                        video.seekTo(target)
                        position.intValue = target
                    }
                    dragging = false
                },
                onStep = { delta ->
                    val video = view
                    if (video != null) {
                        val target = (video.currentPosition + delta).coerceIn(0, maxOf(duration.intValue, 0))
                        video.seekTo(target)
                        position.intValue = target
                    }
                },
            )
        }
    }
}

/** La barre du bas: temps écoulé, piste, durée, et les deux pas de quinze secondes. */
@Composable
private fun TransportBar(
    position: Int,
    duration: Int,
    playing: Boolean,
    dragging: Boolean,
    dragFraction: Float,
    onTogglePlay: () -> Unit,
    onTrackResize: (IntSize) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onStep: (Int) -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val fraction = if (dragging) {
        dragFraction
    } else if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val elapsed = VideoMeta.durationLabel(position.toLong())
    val total = VideoMeta.durationLabel(duration.toLong())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Le rond lecture/pause de la barre: le seul contrôle qui reste pendant la lecture.
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(30.dp)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f), CircleShape)
                .onyxClickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = if (playing) Symbols.Pause else Symbols.Play,
                tint = androidx.compose.ui.graphics.Color.White,
                size = 14.dp,
                contentDescription = if (playing) "Pause" else "Play",
            )
        }
        BasicText(
            text = if (elapsed.isEmpty()) "0:00" else elapsed,
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText),
        )
        // La piste: fine, dessinée ici, et on y pose le doigt pour se déplacer.
        var width by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onSizeChanged {
                    width = it.width
                    onTrackResize(it)
                }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (width > 0) {
                            onScrub(offset.x / width.toFloat())
                            onScrubEnd()
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onScrubEnd() },
                        onDragCancel = { onScrubEnd() },
                    ) { change, _ ->
                        if (width > 0) onScrub((change.position.x / width.toFloat()).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                        RoundedCornerShape(50),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(theme.accent, RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp)
                        .background(androidx.compose.ui.graphics.Color.White, CircleShape),
                )
            }
        }
        BasicText(
            text = if (total.isEmpty()) "0:00" else total,
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText),
        )
        OnyxIcon(
            symbol = Symbols.ArrowCounterclockwise,
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            size = 20.dp,
            contentDescription = "Back 15 seconds",
            modifier = Modifier
                .semantics { contentDescription = OnyxLocalization["Back 15 seconds"] }
                .onyxClickable { onStep(-15_000) },
        )
        OnyxIcon(
            symbol = Symbols.ChevronRight,
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            size = 20.dp,
            contentDescription = "Forward 15 seconds",
            modifier = Modifier.onyxClickable { onStep(15_000) },
        )
    }
}
