package com.onyx.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/** Un bouton d'alerte : son libellé, et s'il est destructeur. */
data class AlertAction(
    val label: String,
    val destructive: Boolean = false,
    val onSelect: () -> Unit,
)

/**
 * **L'alerte héritée — une confirmation, mais une seule surface : celle de la maison.**
 *
 * Ce que le propriétaire a vu : « la partie restaurer message n'est pas cohérente avec le reste du
 * thème, et les boutons collent trop au bord ». Sa capture le montrait : un panneau **gris clair**
 * au milieu d'une appli sombre, un titre **centré** en deux lignes, une **ligne de séparation**
 * interne au-dessus de chaque action, et surtout des actions **nues, bord à bord** — le libellé
 * touchait le bord gauche et le bord droit, sans marge ni écart. C'était la boîte du système :
 * Compose n'a pas d'alerte qui ressemble à celle de la référence, et ce panneau-ci était une
 * **seconde** surface, dessinée à la main à côté de la confirmation de la maison.
 *
 * Depuis, il n'y a **qu'un** panneau : [OnyxConfirmSurface] — le verre de la table, le rayon 28 de
 * la famille des panneaux, l'arête claire de la lumière de tête, le grand titre du socle **aligné à
 * gauche**, le corps du socle, et des actions **pleines largeur** posées à **16 dp du bord** et
 * séparées de **16 dp**. Cette fonction n'est plus qu'un adaptateur : elle garde son API (une liste
 * d'actions libre, la sortie facultative) et rend les formes de la maison par [houseActions].
 *
 * Ce que ce fichier **ne fait plus** : aucune ligne de séparation interne héritée du système (le
 * filet de rangée n'y est plus appelé), aucun texte nu centré comme seule action, aucun panneau
 * dessiné ici. Une correction de rayon, de vitre ou de marge dans [OnyxConfirmSurface] vaut désormais
 * pour **toutes** les alertes de l'app.
 *
 * L'ordre des actions est celui de la maison : celle qui ne se rattrape pas **d'abord**, la sortie
 * ensuite — quelle que soit la place qu'elles occupaient dans la liste de l'appelant.
 */
@Composable
fun OnyxAlert(
    title: String,
    message: String? = null,
    actions: List<AlertAction>,
    /**
     * La sortie par le dehors (le toucher à côté, le retour) ; `null` : il n'y en a pas, comme une
     * `UIAlertController` d'la référence, à qui seule une de ses actions répond. Un `{}` laisserait un
     * geste qui ne fait rien.
     */
    onDismiss: (() -> Unit)? = null,
    /** Les erreurs et textes venant du serveur passent avec `false`, sans traduction. */
    localizeMessage: Boolean = true,
) {
    val theme = LocalOnyxTheme.current
    val dismissable = onDismiss != null

    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
        ),
    ) {
        DialogWindowBlur(FrostSurface.SHEET)
        ConfirmScrim {
            // **Le panneau de la maison** — le même que celui de [OnyxConfirm], et il arrive par la
            // même famille de mouvement ([MotionFamily.Open], dans [OnyxConfirmSurface]).
            OnyxConfirmSurface {
                OnyxConfirmHeading(title)
                OnyxConfirmBody(message, localizeMessage)
                OnyxConfirmActions {
                    val ordered = houseActions(actions)
                    val exit = ordered.indexOfLast { !it.destructive }
                    ordered.forEachIndexed { index, action ->
                        val (ground, ink) = actionGrounds(theme, action.destructive, index == exit)
                        OnyxConfirmAction(
                            label = action.label,
                            ground = ground,
                            ink = ink,
                            onClick = action.onSelect,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Les actions d'une alerte **dans l'ordre de la maison** : l'action qui ne se rattrape pas d'abord,
 * la sortie ensuite. La liste de l'appelant n'est pas modifiée — c'est une nouvelle liste.
 */
internal fun houseActions(actions: List<AlertAction>): List<AlertAction> =
    actions.filter { it.destructive } + actions.filterNot { it.destructive }

/**
 * **Le ton d'une action d'alerte — un seul par rôle.**
 *
 *  - une action **destructrice** : l'encre de danger de la maison sur le gris de capsule
 *    ([OnyxConfirmTone]) — jamais un aplat rouge ;
 *  - la **sortie** (la dernière action non destructrice) : la même pastille, en encre de texte ;
 *  - une **action principale** (« Se fier », « Laisser », « Déplacer ») : la pilule d'accent de la
 *    maison, avec l'encre qui s'y lit le mieux.
 */
internal fun actionGrounds(theme: ResolvedTheme, destructive: Boolean, exit: Boolean): Pair<Color, Color> =
    when {
        destructive -> OnyxConfirmTone.destructiveGround(theme) to OnyxConfirmTone.destructiveInk(theme)
        exit -> OnyxConfirmTone.cancelGround(theme) to OnyxConfirmTone.cancelInk(theme)
        else -> {
            val fill = OnyxContrast.filled(theme.accent)
            fill to OnyxContrast.inkOn(fill)
        }
    }
