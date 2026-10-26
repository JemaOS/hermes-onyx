package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization

/**
 * Un symbole, comme `Image(systemName:)`: le vector drawable teinté à la couleur demandée.
 *
 * Les drawables de [Symbols] sont dessinés en blanc, donc la teinte les recolore entièrement.
 */
@Composable
fun OnyxIcon(
    @DrawableRes symbol: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(symbol),
        contentDescription = contentDescription?.let { OnyxLocalization[it] },
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
