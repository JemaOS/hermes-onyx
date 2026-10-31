package com.onyx.app.ui.design

import androidx.compose.runtime.Composable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **La feuille d'actions — au gabarit unique de la maison** ([HouseSheet]).
 *
 * C'était la feuille qui « vivait dans un thème différent » (le propriétaire, sur le choix de la
 * langue) : titre **centré** en petit, options en **lignes de texte centrées** sans tuile ni filet,
 * sous-titre coupé, panneau **translucide** où le texte de la page derrière devenait lisible, et
 * « Annuler » dans un **second panneau détaché sous la feuille**.
 *
 * Elle ne redessine plus rien : elle **remplit** le gabarit —
 *  - la poignée, le grand titre **à gauche** et le sous-titre, dans la feuille ;
 *  - une rangée par entrée : **vignette grise arrondie**, libellé, **contrôle à droite** (la coche de
 *    l'entrée retenue), filet **en retrait** entre deux rangées ;
 *  - la fermeture **dans** la feuille, en dernière rangée.
 *
 * L'API ne change pas : une entrée reste un [MenuItem] (`symbol` pour la vignette, `checked` pour le
 * contrôle de droite, `destructive` pour l'encre d'alerte).
 */
@Composable
fun OnyxActionSheet(
    title: String? = null,
    message: String? = null,
    items: List<MenuItem>,
    cancelLabel: String = "Cancel",
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    HouseSheet(
        title = title,
        subtitle = message,
        onDismiss = onDismiss,
        closeLabel = cancelLabel,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HouseSheetDivider()
            HouseSheetRow(
                label = item.label,
                symbol = item.symbol,
                destructive = item.destructive,
                onClick = item.onSelect,
                trailing = if (item.checked == true) {
                    {
                        OnyxIcon(
                            symbol = Symbols.Checkmark,
                            tint = OnyxInkOn(theme.accentInk, OnyxSurfaces.surfaceHigh(theme), TextInkTarget),
                            size = HouseSheetControlGlyph,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
