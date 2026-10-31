package com.onyx.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import com.onyx.app.car.AutoArtwork
import com.onyx.app.car.AutoStart
import com.onyx.app.data.model.ChatTurn
import com.onyx.app.localization.OnyxLocalization

/** Les cartes Android Auto qui ont une action possible : poursuivre Ask, ou repartir d'un chat vide. */
class AutoHomeScreen(
    carContext: CarContext,
    private val history: MutableList<ChatTurn>,
) : Screen(carContext) {
    private val settings = CarSettings(carContext)
    private val looks = AutoSettingsBridge(carContext)

    override fun onGetTemplate(): Template {
        val look = looks.looks().cards
        val items = buildList {
            add(AutoStart.ASK)
            if (history.isNotEmpty()) add(AutoStart.NEW_CHAT)
        }
        val header = Header.Builder()
            .setTitle(settings.server().displayName)
            .setStartHeaderAction(Action.APP_ICON)
            .build()

        // GridTemplate appartient à l'API 2 ; un hôte d'API 1 reçoit les mêmes cartes en lignes.
        return if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_2) {
            val list = ItemList.Builder()
            items.forEach { start -> list.addItem(gridItem(start, look)) }
            GridTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build()
        } else {
            val list = ItemList.Builder()
            items.forEach { start -> list.addItem(row(start, look)) }
            ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build()
        }
    }

    private fun gridItem(start: AutoStart, look: com.onyx.app.car.AutoCardLook): GridItem =
        GridItem.Builder()
            .setTitle(title(start))
            .setText(subtitle(start))
            .setImage(icon(start, look), GridItem.IMAGE_TYPE_LARGE)
            .setOnClickListener { open(start) }
            .build()

    private fun row(start: AutoStart, look: com.onyx.app.car.AutoCardLook): Row =
        Row.Builder()
            .setTitle(title(start))
            .addText(subtitle(start))
            .setImage(icon(start, look), Row.IMAGE_TYPE_LARGE)
            .setOnClickListener { open(start) }
            .build()

    private fun icon(start: AutoStart, look: com.onyx.app.car.AutoCardLook): CarIcon =
        CarIcon.Builder(IconCompat.createWithBitmap(AutoArtwork.startCard(start, look))).build()

    private fun open(start: AutoStart) {
        if (start == AutoStart.NEW_CHAT) history.clear()
        carContext.getCarService(ScreenManager::class.java).push(
            AskScreen(carContext, initialQuery = null, history = history, showBack = true),
        )
    }

    private fun title(start: AutoStart): String = when (start) {
        AutoStart.ASK -> OnyxLocalization["Ask"]
        AutoStart.NEW_CHAT -> OnyxLocalization["New Chat"]
    }

    private fun subtitle(start: AutoStart): String = when (start) {
        AutoStart.ASK -> OnyxLocalization["One question"]
        AutoStart.NEW_CHAT -> OnyxLocalization["Start fresh"]
    }
}
