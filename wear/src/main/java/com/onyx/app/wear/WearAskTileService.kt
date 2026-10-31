package com.onyx.app.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.onyx.app.localization.OnyxLocalization

/**
 * La tuile « demander » : un mot au poignet, et Onyx s'ouvre en écoute.
 *
 * Équivalent Android de la tuile/complication de la référence : la marque, une phrase, et
 * un tap qui lance l'écran vocal.
 */
class WearAskTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(openTheApp())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )

    /** L'unique élément de la tuile : toute la surface est cliquable. */
    private fun openTheApp(): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("ask")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(WearActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setWidth(DimensionBuilders.expand())
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(OnyxLocalization["Ask Onyx"])
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setColor(ColorBuilders.argb(0xFFFFD700.toInt()))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .build(),
                            )
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(6f))
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(OnyxLocalization["Tap and talk"])
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setColor(ColorBuilders.argb(0xFFB6B6C2.toInt()))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
