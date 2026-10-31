package com.onyx.app.ui.chat

import com.onyx.app.ui.design.onyxPage

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.delay

/** Le son à lire : son adresse (le cache du téléphone, ou la pièce jointe) et son nom. */
data class SoundSource(val uri: Uri, val name: String)

/**
 * Le lecteur d'un son joint.
 *
 * Le même contrat que le lecteur de vidéo : rien du système à l'écran (`MediaPlayer` n'en dessine
 * aucun contrôle), le nom en tête, puis la **position** — la piste qu'on fait glisser, le temps
 * écoulé sur la durée — et le rond **lecture/pause**. Le son se lit sur le fond de la page, sans
 * l'écran noir de la vidéo : il n'y a pas d'image à mettre en valeur, seulement un titre.
 *
 * Le lecteur est tenu par l'écran et libéré en partant ([DisposableEffect]) : aucun son ne continue
 * après la fermeture.
 */
@Composable
fun AudioPlayerScreen(
    source: SoundSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var player by remember(source.uri) { mutableStateOf<MediaPlayer?>(null) }
    val duration = remember(source.uri) { mutableIntStateOf(0) }
    val position = remember(source.uri) { mutableIntStateOf(0) }
    var playing by remember(source.uri) { mutableStateOf(false) }
    var problem by remember(source.uri) { mutableStateOf<String?>(null) }
    var dragging by remember(source.uri) { mutableStateOf(false) }
    var dragFraction by remember(source.uri) { mutableStateOf(0f) }
    var track by remember { mutableStateOf(IntSize.Zero) }

    // Le lecteur se prépare en arrivant : la durée s'affiche sans qu'on ait à appuyer.
    DisposableEffect(source.uri) {
        var created: MediaPlayer? = null
        runCatching {
            created = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, source.uri)
                setOnPreparedListener { ready ->
                    duration.intValue = ready.duration.coerceAtLeast(0)
                    player = ready
                }
                setOnCompletionListener {
                    playing = false
                    position.intValue = duration.intValue
                }
                setOnErrorListener { _, _, _ ->
                    playing = false
                    problem = OnyxLocalization["This sound can't be played."]
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            problem = OnyxLocalization["This sound can't be played."]
        }
        return@DisposableEffect onDispose {
            playing = false
            created?.let { done ->
                runCatching { done.stop() }
                runCatching { done.release() }
            }
            player = null
        }
    }

    // Le temps avance tout seul pendant la lecture.
    LaunchedEffect(playing, player) {
        while (playing) {
            player?.let { position.intValue = runCatching { it.currentPosition }.getOrDefault(position.intValue) }
            delay(200)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OnyxIcon(
                symbol = Symbols.XmarkCircle,
                tint = theme.secondaryText,
                size = 26.dp,
                contentDescription = "Close",
                modifier = Modifier.onyxClickable(onClick = onClose),
            )
            BasicText(
                text = source.name,
                localize = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = theme.caption.copy(color = theme.textColor),
                modifier = Modifier
                    .background(theme.fill, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val problemText = problem
            if (problemText != null) {
                BasicText(
                    text = problemText,
                    localize = false,
                    style = theme.footnote.copy(color = theme.secondaryText),
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Le disque du son : la pastille de la maison, avec l'onde au centre.
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .background(theme.accent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        OnyxIcon(
                            symbol = Symbols.SpeakerWave,
                            tint = theme.accent,
                            size = 54.dp,
                            contentDescription = source.name,
                        )
                    }
                    BasicText(
                        text = source.name,
                        localize = false,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = theme.headline.copy(color = theme.textColor),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }

        AudioTransport(
            position = position.intValue,
            duration = duration.intValue,
            playing = playing,
            dragging = dragging,
            dragFraction = dragFraction,
            onTrackResize = { track = it },
            onTogglePlay = {
                val sound = player ?: return@AudioTransport
                runCatching {
                    if (playing) {
                        sound.pause()
                        playing = false
                    } else {
                        sound.start()
                        playing = true
                    }
                }.onFailure {
                    problem = OnyxLocalization["This sound can't be played."]
                }
            },
            onScrub = { fraction ->
                dragging = true
                dragFraction = fraction
            },
            onScrubEnd = {
                val sound = player
                if (sound != null && duration.intValue > 0) {
                    val target = (dragFraction.coerceIn(0f, 1f) * duration.intValue).toInt()
                    runCatching { sound.seekTo(target) }
                    position.intValue = target
                }
                dragging = false
            },
        )
    }
}

/** La barre du bas du son : le rond lecture/pause, le temps écoulé, la piste, et la durée. */
@Composable
private fun AudioTransport(
    position: Int,
    duration: Int,
    playing: Boolean,
    dragging: Boolean,
    dragFraction: Float,
    onTrackResize: (IntSize) -> Unit,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val fraction = if (dragging) {
        dragFraction
    } else if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val elapsed = VideoMeta.durationLabel(position.toLong()).ifEmpty { "0:00" }
    val total = VideoMeta.durationLabel(duration.toLong())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(theme.accent, CircleShape)
                .onyxClickable(onClick = onTogglePlay)
                .semantics { contentDescription = if (playing) "Pause" else "Play" },
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(
                symbol = if (playing) Symbols.Pause else Symbols.Play,
                tint = theme.pageColor,
                size = 20.dp,
                contentDescription = null,
            )
        }
        BasicText(text = elapsed, localize = false, style = theme.caption.copy(color = theme.secondaryText))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onSizeChanged { onTrackResize(it) }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        if (width > 0f) {
                            onScrub((offset.x / width).coerceIn(0f, 1f))
                            onScrubEnd()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(theme.fill, RoundedCornerShape(50)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(theme.accent, RoundedCornerShape(50)),
            )
        }
        BasicText(
            text = total.ifEmpty { elapsed },
            localize = false,
            style = theme.caption.copy(color = theme.secondaryText, textAlign = TextAlign.End),
        )
    }
}
