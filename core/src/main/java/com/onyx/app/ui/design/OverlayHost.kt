package com.onyx.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * **L'hôte des calques de la fenêtre principale.**
 *
 * ## Pourquoi il existe
 *
 * Une fenêtre **`Dialog`** est une **autre fenêtre** : elle ne voit ni la source de flou Haze
 * (posée par l'écran qui fait défiler son contenu, dans la fenêtre principale), ni la page de la
 * lentille, ni le calque de fond ([LocalBackdrop]). Un panneau qui y vit ne peut donc **rien**
 * échantillonner : il ne peut que se peindre d'un voile, et c'est exactement ce qui le faisait lire
 * comme du **lavis** — un rectangle gris translucide posé sur l'écran, le contenu dessous restant
 * lisible.
 *
 * Cet hôte donne aux panneaux flottants **la fenêtre principale** : ils y lisent la source de flou,
 * courbent ce qu'ils recouvrent, et portent la finition de verre comme le reste de l'app.
 *
 * ## Ce qu'il ne change pas
 *
 * Le calque est dessiné **au-dessus de la route**, jamais dedans : une vitre qui se lirait
 * elle-même planterait l'app (la surface serait dessinée dans la couche qu'elle échantillonne).
 */
@Stable
class OverlayHost {
    /** Le calque courant : `null` quand rien n'est ouvert. */
    var layer by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    /** Le jeton du calque courant : il dit **quel** calque est en place. */
    private var current: Any? = null

    /**
     * Pose un calque, en remplaçant celui qui serait là.
     *
     * @return le jeton du calque posé : il faut le rendre à [dismiss] pour que celui-ci ne retire
     * que **ce** calque. Deux panneaux peuvent se demander tour à tour la place (un sous-menu s'ouvre
     * sur un menu) ; sans jeton, la fermeture du premier fermerait le second.
     */
    fun show(layer: @Composable () -> Unit): Any {
        val token = Any()
        current = token
        this.layer = layer
        return token
    }

    /**
     * Retire le calque courant — appelé par le calque lui-même quand il se ferme.
     *
     * @param token le jeton rendu par [show] : si le calque en place n'est pas celui-là, on ne
     * touche à rien — un panneau qui se ferme ne doit pas fermer celui qui l'a remplacé.
     */
    fun dismiss(token: Any) {
        if (current === token) {
            current = null
            layer = null
        }
    }
}

/**
 * **L'hôte courant** : `null` quand aucun hôte n'est posé (le menu reste alors dans sa fenêtre, sans
 * verre — il ne se ferme plus tout seul, mais il s'ouvre).
 */
val LocalOverlayHost = compositionLocalOf<OverlayHost?> { null }
