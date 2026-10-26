package com.onyx.app.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.onyx.app.ui.design.BotAvatar
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.BotPose
import com.onyx.app.ui.design.LocalReduceMotion
import com.onyx.app.ui.design.botFace
import com.onyx.app.voice.VoicePhase
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Une image sur trente suffit à un visage qui cligne et qui parle (`TimelineView` ). */
private const val FACE_FRAME_NANOS = 33_333_333L

/**
 * L'instant d'un visage à la seconde `seconds`: où il regarde, jusqu'où ses yeux sont fermés, son
 * ouverture de bouche, son inclinaison. Comme `OnyxVoiceFace.pose`.
 *
 * - **Clignement**: un toutes les 4,6 s, un double toutes les 13,8 s — le visage ne reste jamais
 * les yeux fixes, même pendant qu'il parle.
 * - **Regard**: à vous pendant l'écoute, ailleurs et haut pendant la réflexion, doucement devant
 * pendant la lecture, et au repos un regard tenu puis un vif passage au suivant.
 * - **Bouche**: elle s'ouvre avec le niveau de la voix pendant la lecture (`level × 1,8`), et
 * reste au visage le reste du temps.
 * - **Inclinaison**: la tête penche avec le regard (`tilt + regard.x × 2,5`).
 * - **Sourcils et paupières**: les micro-expressions de la phase — hauts et ouverts à l'écoute,
 * rapprochés et mi-clos en réflexion, animés pendant la parole, une petite houle au repos.
 * - **Respiration**: un lent va-et-vient du corps (environ quatre secondes), même au repos.
 */
fun voicePose(seconds: Float, phase: VoicePhase, level: Float, tilt: Float = 0f): BotPose {
    // Un clignement dure 0,16 s, en sinus: fermé au milieu, ouvert aux deux bouts.
    fun blink(t: Float, every: Float, offset: Float): Float {
        val at = ((t + offset) % every + every) % every
        return if (at < 0.16f) sin(at / 0.16f * Math.PI).toFloat() else 0f
    }

    val shut = maxOf(blink(seconds, every = 4.6f, offset = 0f), blink(seconds, every = 13.8f, offset = 0.35f))
    val look = when (phase) {
        VoicePhase.LISTENING ->
            // Vers vous, un rien sous le milieu, la plus petite dérive.
            Offset(0.08f * sin(seconds * 1.3f), 0.3f + 0.06f * sin(seconds * 0.9f))

        VoicePhase.THINKING ->
            // En haut et de côté, tournant comme s'il pesait la question.
            Offset(0.55f + 0.3f * cos(seconds * 2.2f), -0.8f + 0.15f * sin(seconds * 2.2f))

        VoicePhase.SPEAKING ->
            Offset(0.18f * sin(seconds * 0.8f), 0.2f + 0.08f * sin(seconds * 1.3f))

        VoicePhase.IDLE -> {
            // Au repos: un regard tenu, puis un passage vif au suivant.
            val spots = listOf(
                Offset(0.7f, 0.25f), Offset(-0.6f, 0.4f), Offset(0.1f, -0.55f),
                Offset(-0.85f, -0.2f), Offset(0.45f, 0.6f), Offset(0f, 0.15f),
            )
            val slot = 2.4f
            val index = floor(seconds / slot).toInt()
            val within = seconds % slot / slot
            val from = spots[Math.floorMod(index + spots.size - 1, spots.size)]
            val to = spots[Math.floorMod(index, spots.size)]
            val move = (within / 0.12f).coerceAtMost(1f)
            val eased = move * move * (3f - 2f * move)
            Offset(from.x + (to.x - from.x) * eased, from.y + (to.y - from.y) * eased)
        }
    }
    val mouth = if (phase == VoicePhase.SPEAKING) (level * 1.8f).coerceIn(0f, 1f) else 0f
    // Les **micro-expressions** de la phase: sourcils et paupières suivent ce que le visage fait —
    // sourcils hauts à l'écoute, rapprochés en réflexion, vivants pendant la parole, une petite
    // houle au repos. Lentes et de faible amplitude: de la vie, jamais du bruit.
    val brow = when (phase) {
        VoicePhase.LISTENING -> 0.35f + 0.08f * sin(seconds * 1.1f)
        VoicePhase.THINKING -> -0.45f + 0.10f * sin(seconds * 1.7f)
        VoicePhase.SPEAKING -> 0.05f + 0.10f * sin(seconds * 3.1f)
        VoicePhase.IDLE -> 0.05f * sin(seconds * 0.6f)
    }
    val squint = when (phase) {
        VoicePhase.LISTENING -> 0f
        VoicePhase.THINKING -> 0.28f
        VoicePhase.SPEAKING -> 0.05f + 0.05f * sin(seconds * 2.2f)
        VoicePhase.IDLE -> 0.06f + 0.05f * sin(seconds * 0.7f)
    }
    // La respiration: un cycle d'environ quatre secondes, lente et presque imperceptible. C'est la
    // seule chose qui bouge encore quand le visage est au repos — il vit, sans rien déplacer.
    val breath = 0.5f * sin(seconds * 1.5f)
    return BotPose(
        look = look,
        blink = shut,
        mouth = mouth,
        tilt = tilt + look.x * 2.5f,
        brow = brow.coerceIn(-1f, 1f),
        squint = squint.coerceIn(-1f, 1f),
        breath = breath,
    )
}

/**
 * Le grand visage de la scène vocale: celui du bot qui parle, avec ses yeux qui clignent, son
 * regard qui suit et **sa bouche qui s'ouvre avec sa voix**. Comme `OnyxVoiceFace`
 *: le même visage, vivant, dans l'orbe.
 *
 * Sous **Reduce Motion**, le visage est figé: aucun instant ne passe (`paused: reduceMotion` du
 * `TimelineView`), donc pas de clignement, pas de dérive du regard, et la bouche reste celle du
 * visage plutôt que de battre avec la voix.
 *
 * `level` est lu dans la composition: le niveau de la voix ne fait donc re-dessiner que ce visage,
 * pas l'écran entier (dix fois par seconde au plus).
 */
@Composable
fun VoiceFace(
    name: String,
    faceCode: String?,
    phase: VoicePhase,
    level: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val face = remember(name, faceCode) { botFace(name, faceCode) }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            seconds = 0f
            return@LaunchedEffect
        }
        // Une image sur trente: le temps ne s'écrit que quand un trentième a passé, donc aucun
        // re-dessin inutile sur un écran à 90 ou 120 Hz.
        val started = withFrameNanos { it }
        var shown = -1L
        while (true) {
            withFrameNanos { now ->
                val frame = (now - started) / FACE_FRAME_NANOS
                if (frame != shown) {
                    shown = frame
                    seconds = frame / 30f
                }
            }
        }
    }
    val pose = if (reduceMotion) {
        BotPose()
    } else {
        voicePose(seconds = seconds, phase = phase, level = level(), tilt = face.tilt.toFloat())
    }
    BotAvatar(
        name = name,
        size = size,
        faceCode = faceCode,
        modifier = modifier,
        mood = BotMood.IDLE,
        showsInitial = false,
        blinks = false,
        pose = pose,
    )
}
