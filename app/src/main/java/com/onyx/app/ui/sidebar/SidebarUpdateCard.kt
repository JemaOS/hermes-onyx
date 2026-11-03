package com.onyx.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.AppUpdate
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.FlatHairlineDp
import com.onyx.app.ui.design.Lucide
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.flatWellColor
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **La mise à jour de l'app**, dans le volet : une carte qui dit qu'une version plus récente est
 * publiée, et qui permet de la **télécharger par-dessus** celle-ci.
 *
 * « Par-dessus », et non « à la place » : les trois paquets sont signés du **même** certificat,
 * donc l'installation par-dessus garde la session, les réglages et l'historique. C'est ce que la
 * deuxième ligne promet — et c'est vrai, ce n'est pas une formule.
 *
 * La carte est **discretable** : la croix l'écarte, et elle ne revient pas pour cette version
 * (le rappel est mémorisé). Une alerte qu'on ne peut pas écarter n'est plus une alerte, c'est un
 * reproche.
 */
@Composable
internal fun SidebarUpdateCard(
    update: AppUpdate,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val pane = sidebarPaneColor(theme)
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = OnyxSpace.BarInset, end = OnyxSpace.Snug, top = OnyxSpace.Half),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(flatWellColor(theme))
                .border(FlatHairlineDp, theme.separator, shape)
                .padding(OnyxSpace.Snug),
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                BasicText(
                    // La version est **variable** : le gabarit est traduit, le numéro est rendu
                    // tel quel (un numéro ne se traduit pas).
                    text = OnyxLocalization.format("Version %1\$s is out", update.versionName),
                    style = theme.body.copy(
                        color = sidebarInk(theme.textColor, pane, OnyxContrast.TEXT_MIN),
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(OnyxSpace.Half))
                OnyxIcon(
                    symbol = Lucide.X,
                    tint = mutedInk(pane),
                    size = 14.dp,
                    modifier = Modifier
                        .onyxTappable(onClick = onDismiss)
                        .padding(OnyxSpace.Hair),
                )
            }
            BasicText(
                text = OnyxLocalization["Install it over this one; your sign-in and settings stay."],
                style = theme.footnote.copy(color = mutedInk(pane)),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OnyxContrast.filled(theme.accent))
                    .onyxTappable(onClick = onDownload)
                    .padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Half),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                ) {
                    OnyxIcon(
                        symbol = Symbols.ArrowDown,
                        tint = OnyxContrast.inkOn(OnyxContrast.filled(theme.accent)),
                        size = 16.dp,
                    )
                    BasicText(
                        text = OnyxLocalization["Download"],
                        style = theme.footnote.copy(
                            color = OnyxContrast.inkOn(OnyxContrast.filled(theme.accent)),
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
