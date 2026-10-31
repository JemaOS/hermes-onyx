package com.onyx.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **Le verre répond au doigt — mais il ne s'éclaire plus.**
 *
 * Pourquoi ce fichier a changé : le lot précédent avait fait réagir la vitre au doigt avec **trois**
 * effets, dont deux étaient de la **lumière** — une tache spéculaire blanche au point de contact
 * (0,16) et un contour illuminé (0,55). Le propriétaire a tranché : « la lumière au toucher,
 * supprime ça de partout, c'est moche ». Les deux pièces lumineuses — la tache **et** le contour —
 * ont donc été **retirées partout** : aucune vitre ne s'allume plus sous le doigt, nulle part.
 *
 * **Ce qui remplace la lumière : un appui, sans un photon de plus.** Il ne reste que des signes
 * **non lumineux**, et il n'en reste que parce qu'en retirer **tous** aurait laissé la surface
 * muette au doigt :
 *
 *  1. **[GlassPressScale] — l'enfoncement.** La vitre petite (tuile, rangée, capsule, segmenté,
 *     pilule) s'enfonce de 1,5 % à l'appui. C'est l'échelle d'appui déjà employée par les gestes de
 *     l'app (voir `Interactions.kt`) — pas un effet nouveau.
 *  2. **[GlassPressShade] — l'assombrissement.** Les surfaces qui ne *peuvent pas* s'enfoncer sans
 *     faire bouger leur encre (barres, panneaux, compositeur : voir `GlassSurface.scalesOnPress`)
 *     prennent, elles, une ombre très légère **dans la vitre, sous l'encre**. C'est l'exact pendant
 *     — en sombre — du « gain de luminance » d'avant, qui lui blanchissait la vitre.
 *
 * Ni l'un ni l'autre ne peint de blanc : **jamais de lumière, jamais de ripple générique.**
 *
 * **Le réglage.** [LocalGlassHalo] coupe **tout** l'appui — l'enfoncement **et** l'assombrissement —
 * d'un seul interrupteur. C'est le contrat avec l'écran des Réglages (« Halo ») : halo éteint =
 * surface **parfaitement inerte** au doigt. Un interrupteur qui ne couperait qu'une des deux pièces
 * serait pire que pas d'interrupteur.
 *
 * **Reduce Motion.** L'appui se **fige** : aucune échelle, aucun assombrissement, aucune transition.
 * Le verre reste — c'est un matériau, pas un mouvement — mais il ne réagit plus au doigt.
 *
 * **Le budget.** L'appui tourne **pendant** le geste, c'est-à-dire au pire moment. Il est donc
 * branché sur la mesure de fluidité qui existe déjà ([GlassFluidity]) : dès que la charge dépasse le
 * seuil, [GlassFluidity.pressAllowed] passe à faux et l'appui **disparaît** au doigt au lieu de faire
 * tomber la cadence.
 *
 * **Le gel pendant le défilement : il ne reste que la règle qui ne peint rien.** Le défilement
 * **n'est pas un appui** : pendant le geste, [glassPressStrength] rend zéro (voir
 * [LocalGlassScrollFreeze]) — une vitre ne s'enfonce pas sous un doigt qui la fait défiler. **Ce
 * signal ne touche plus à la peinture du verre** : le plafonnement du flou et le gel de la lumière
 * de tête qu'il portait ont été **retirés** (`1.0.0+742`) — ils n'existaient que pour empêcher une
 * **lumière de contact** de suivre le doigt, et cette lumière a été supprimée partout (voir
 * [LocalGlassHalo]). L'appui, lui, ne peint ni lumière ni voile : il enfonce ou assombrit.
 *
 * **La réfraction, depuis Haze 2.** `dev.chrisbanes.haze` **2.0.1** sait **déplacer** le contenu
 * échantillonné : son module `haze-glass` expose un profil de réfraction d'arête
 * (`RefractionProfile.Edge`), une dispersion chromatique, un liseré d'arête et un reflet spéculaire
 * — donc [GlassRefractionAvailable] est vrai, et c'est une mesure, pas une promesse.
 */

/**
 * L'enfoncement de la vitre à l'appui : 1,5 %. Assez pour se sentir, pas assez pour sauter.
 *
 * C'est le retour au toucher des **petites** surfaces (`GlassSurface.scalesOnPress`) — un mouvement,
 * pas une lumière.
 */
const val GlassPressScale: Float = 0.985f

/**
 * L'assombrissement de la vitre à l'appui, en alpha de noir : 6 %. C'est le retour au toucher des
 * surfaces qui **ne s'enfoncent pas** (barres, panneaux, compositeur) — une ombre, jamais un éclat.
 *
 * Il remplace le **gain de luminance** du lot précédent (0,06), qui lui *blanchissait* la vitre au
 * doigt. L'ombre va dans l'autre sens : la vitre s'assombrit, le doigt se lit sans qu'aucune lumière
 * n'apparaisse.
 */
const val GlassPressShade: Float = 0.06f

/**
 * Où en est l'appui d'une vitre : le doigt est-il posé.
 *
 * L'état vit **par surface** : deux vitres ne partagent pas leur appui. Le **point de contact** a
 * disparu avec la lumière — il n'existait que pour poser la tache spéculaire au bon endroit, et rien
 * de ce qui reste ne se place dans la surface (l'enfoncement et l'assombrissement portent la surface
 * entière).
 */
@Stable
class GlassPressState {
    /** Vrai tant que le doigt est posé. */
    var pressed: Boolean by mutableStateOf(false)
        internal set

    internal fun press() {
        pressed = true
    }

    internal fun release() {
        pressed = false
    }
}

/** L'appui d'une vitre. `rememberGlassPressState()` en rend un par surface. */
@Composable
fun rememberGlassPressState(): GlassPressState = remember { GlassPressState() }

/**
 * Le réglage « Halo ». `false` : la surface est **parfaitement inerte** au doigt — pas d'enfoncement,
 * pas d'assombrissement — et la vitre garde sa profondeur. Valeur par défaut `true` : hors de
 * l'arbre qui pose le réglage (aperçus, tests), le verre répond comme dans l'app.
 *
 * Le nom est gardé : c'est **la clé persistée** et le libellé de l'écran des Réglages (« Halo »). Ce
 * qu'il coupe a changé — la lumière au doigt a été retirée, il ne reste que l'appui.
 */
val LocalGlassHalo = compositionLocalOf { true }

/**
 * **L'appui se suspend pendant un défilement.** Vrai tant qu'une liste est en mouvement.
 *
 * Une vitre qui réagit **au doigt qui la fait défiler** n'est pas un appui : le doigt passe sur les
 * rangées et chacune réagit sous lui, ce qui se lit comme un effet qui suit le doigt. Le défilement
 * **n'est pas un appui** — c'est un déplacement — donc aucune vitre ne réagit tant qu'une liste
 * bouge : la réponse au doigt s'éteint le temps du geste et revient au repos.
 *
 * **Ce signal ne COMMANDE plus rien de ce qui est peint.** Le gel de défilement portait deux
 * autres pièces — le **plafonnement du flou** (la vitre prenait le voile de repli pendant le geste)
 * et le **gel de la lumière de tête** —, et toutes deux ont été **retirées** (`1.0.0+742`) : elles
 * n'existaient que pour qu'une **lumière de contact** ne suive pas le doigt, et cette lumière a été
 * supprimée partout. Ce qui reste est la seule règle qui a un objet, et elle **ne peint rien** : un
 * doigt qui défile n'est pas un doigt qui appuie. Valeur par défaut `false` : hors de l'arbre qui la
 * pose (aperçus, tests), la vitre répond au doigt comme dans l'app.
 */
val LocalGlassScrollFreeze = compositionLocalOf { false }

/**
 * La force de l'appui d'une vitre, pour une trame : un seul chiffre, de 0 (au repos) à 1 (plein
 * doigt).
 *
 * [strength] vaut zéro quand l'appui est éteint (réglage), figé (Reduce Motion), **coupé par le
 * budget** ([fluidityAllows]), ou **suspendu pendant un défilement** ([scrolling]). C'est la seule
 * fonction qui décide — quatre conditions, un seul chiffre — donc aucune des quatre ne peut être
 * oubliée à un endroit et appliquée à un autre.
 *
 * Elle ne rend **qu'une intensité d'appui** : elle ne peint rien, et surtout **aucune lumière**. Les
 * deux seuls consommateurs sont l'enfoncement ([glassPressScale]) et l'assombrissement
 * ([GlassPressShade]).
 */
fun glassPressStrength(
    enabled: Boolean,
    reduceMotion: Boolean,
    fluidityAllows: Boolean,
    pressed: Float,
    /**
     * Vrai pendant qu'une liste défile : l'appui **s'éteint** le temps du geste, il ne suit pas le
     * doigt. Le défaut `false` garde le verre vivant hors de l'arbre qui pose
     * [LocalGlassScrollFreeze].
     */
    scrolling: Boolean = false,
): Float =
    if (enabled && !reduceMotion && fluidityAllows && !scrolling) pressed.coerceIn(0f, 1f) else 0f

/**
 * Vrai si la réfraction du contenu est disponible dans cette version de Haze.
 *
 * **Vrai depuis le passage à `haze` 2.0.1**, dont le module `haze-glass` déplace réellement le
 * contenu échantillonné ([RefractionProfile.Edge]). C'est une **mesure** : le drapeau suit la
 * capacité de la bibliothèque, et `GlassTier` décide à l'exécution si l'appareil peut s'en servir
 * (processeur d'ombrage à l'exécution, API 33+). Sous ce seuil, la vitre garde le flou progressif
 * ([GlassEdgeLens]) — disponible partout — et le contenu n'est pas déplacé.
 *
 * Ce drapeau est lu par le rapport **et** par les tests.
 */
const val GlassRefractionAvailable: Boolean = true

/**
 * L'**effet de loupe d'arête** : un flou plus large au bord qu'au centre, qui fait lire le bord
 * comme une arête de verre épaisse.
 *
 * C'est le **repli** de la réfraction, et il reste branché : sur les appareils sans processeur
 * d'ombrage à l'exécution, c'est lui — et non un décalage de contenu — qui donne son épaisseur à
 * l'arête. Sur ceux qui en ont un, la réfraction s'y ajoute ; les deux ne se remplacent pas.
 */
const val GlassEdgeLens: Boolean = true

/** La force du flou progressif au bord, en fraction du rayon : le bord floute plus que le centre. */
const val GlassEdgeLensStrength: Float = 1.35f

/**
 * La hauteur de la **parallaxe** du reflet, en dp : quand une vitre se déplace, sa lumière se
 * déplace un peu moins vite qu'elle. 6 dp est le décalage maximal du dégradé de tête, soit une
 * fraction de la hauteur d'une barre — perceptible en mouvement, invisible à l'arrêt.
 */
val GlassParallaxDp: Dp = 6.dp

/**
 * La parallaxe effective, en dp, pour un déplacement vertical de [travelPx] pixels : proportionnelle
 * au déplacement, bornée à [GlassParallaxDp], nulle quand la surface ne bouge pas.
 *
 * Elle est **quantifiée** (l'appelant ne lui donne qu'une position arrondie) : un reflet qui
 * redemanderait une trame pour un huitième de pixel coûterait plus cher que l'effet qu'il rend.
 */
fun glassParallaxDp(travelPx: Float, density: Float): Float {
    if (density <= 0f) return 0f
    val travelDp = travelPx / density
    val max = GlassParallaxDp.value
    return (-travelDp * 0.12f).coerceIn(-max, max)
}

/**
 * Le suivi de l'appui **sans rien voler** : les événements sont lus au passage `Initial`, et ne sont
 * jamais consommés. Une vitre n'a donc pas à être cliquable pour réagir — et une vitre qui l'est
 * continue de recevoir son appui normalement.
 *
 * C'est un suivi de **doigt posé**, pas de point : la position n'est plus lue (la lumière qui s'y
 * plaçait a été retirée), seul l'appui compte.
 */
fun Modifier.glassPress(state: GlassPressState): Modifier = composed {
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.press()
            // Aucune consommation : l'appui continue vers le bouton ou la rangée qui est dessous.
            // Le passage `Initial` sert exactement à ça — voir sans prendre.
            var active = true
            while (active) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    active = false
                    state.release()
                }
            }
        }
    }
}

/** La vitre **enfoncée** : l'échelle de l'appui, portée par la surface entière. */
@Composable
fun glassPressScale(state: GlassPressState, enabled: Boolean, reduceMotion: Boolean): Float {
    val target = if (state.pressed && enabled && !reduceMotion) GlassPressScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = MotionFamily.Press.animated(),
        label = "glassPress",
    )
    return scale
}
