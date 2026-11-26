package com.onyx.app.ui.common

import com.onyx.app.ui.design.OnyxSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Un contrôle segmenté aux tailles ordinaires, une liste aux tailles d'accessibilité.
 *
 * À partir d'un facteur de police d'environ 1,8, trois ou quatre segments ne tiennent plus sur une
 * ligne sans être tronqués — référence passe alors à un menu, ce port à une liste verticale.
 */
@Composable
fun AdaptiveSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    /** La place que l'appelant lui laisse — la marge d'écran du socle, comme ses voisins. */
    modifier: Modifier = Modifier,
    /**
     * **La moitié active en aplat d'accent** ([OnyxSegmented] `filled`), son mot posé dessus : la
     * règle de la pastille d'onglet. Faux = la pilule de surface habituelle.
     */
    filled: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale

    if (fontScale < 1.8f) {
        OnyxSegmented(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier,
            filled = filled,
        )
        return
    }

    val theme = LocalOnyxTheme.current
    val accent = OnyxContrast.filled(theme.accent)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.fill, RoundedCornerShape(OnyxSpace.ControlCorner))
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            active && filled -> accent
                            active -> theme.surfaceColor
                            else -> androidx.compose.ui.graphics.Color.Transparent
                        },
                        RoundedCornerShape(OnyxSpace.ControlCorner),
                    )
                    .onyxTappable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    text = label,
                    style = theme.body.copy(
                        color = when {
                            active && filled -> OnyxContrast.inkOn(accent)
                            active -> theme.textColor
                            else -> theme.secondaryText
                        },
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    OnyxIcon(
                        Symbols.Checkmark,
                        tint = if (filled) OnyxContrast.inkOn(accent) else theme.accent,
                        size = 14.dp,
                    )
                }
            }
        }
    }
}
