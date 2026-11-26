package com.onyx.app.ui.design

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Un glissement vers la droite n'importe où sur l'écran (`SwipeToOpen`) : c'est le geste du
 * `SidePanel` de la référence, qui prend la surface entière — la liste des conversations arrive alors par la
 * gauche.
 *
 * Le bord de l'écran, lui, appartient au retour système d'Android (une app ne peut s'en soustraire
 * qu'en partie : le plafond est de 200 dp par bord, et le système rogne le reste). Le geste du bord
 * gauche ne passe donc pas par ici : c'est le retour, traité dans la mise en page, qui révèle la
 * liste au lieu de fermer l'app — le même effet, par le chemin que la plateforme laisse ouvert.
 *
 * Seul un geste franchement latéral compte, pour que le fil continue de défiler ; un glissement
 * vers la gauche ne dit rien. `onEnded` reçoit la distance et la vitesse horizontale réelle du
 * geste (pixels par seconde, vers la droite positif), pour décider sur l'élan et pas seulement sur
 * la distance parcourue.
 */
fun Modifier.swipeRight(
    enabled: Boolean = true,
    onChanged: (Float) -> Unit,
    onEnded: (distance: Float, velocity: Float) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var total = 0f
        var started = false
        // Garde les derniers points pour en tirer la vitesse du geste.
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = {
                total = 0f
                started = false
                tracker.resetTracking()
            },
            onHorizontalDrag = { change, dragAmount ->
                // La vitesse se mesure sur les mouvements récents, pas sur tout le geste.
                tracker.addPosition(change.uptimeMillis, change.position)
                total += dragAmount
                // Un geste qui part vers la gauche n'ouvre rien.
                if (!started && total > 8f) started = true
                if (started) onChanged(maxOf(0f, total))
                change.consume()
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                if (started) onEnded(total, velocity) else onEnded(0f, 0f)
            },
            onDragCancel = { onEnded(0f, 0f) },
        )
    },
)
