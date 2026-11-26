package com.onyx.app.ui.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.onyx.app.ui.design.GlassSurface
import com.onyx.app.ui.design.OnyxBase
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.PlateGeometry
import com.onyx.app.ui.design.composerBottomInsets
import com.onyx.app.ui.design.liquidGlass
import com.onyx.app.ui.design.socleShadow
import com.onyx.app.ui.theme.LocalOnyxTheme

/** One surface and one system inset owner for chat and room composers. */
@Composable
internal fun CompactComposerDock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val shape = RoundedCornerShape(PlateGeometry.Radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .composerBottomInsets()
            .padding(
                start = PlateGeometry.SideMargin,
                end = PlateGeometry.SideMargin,
                bottom = OnyxSpace.Half,
            )
            .socleShadow(OnyxBase.ShadowFar, PlateGeometry.Radius, theme.dark)
            .clip(shape)
            .border(PlateGeometry.Outline, OnyxSurfaces.hairline(theme), shape)
            .liquidGlass(GlassSurface.TAB_BAR, shape, masking = true),
        content = content,
    )
}
