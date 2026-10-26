package com.onyx.app.data.model.serve

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'ordre de la section « Bots »: les lignes telles qu'elles s'affichent, l'ordre qu'un glissement
 * enregistre, et ce qu'un ordre devenu faux (un bot renommé, un nouveau venu, une salle terminée)
 * devient.
 *
 * Le modèle est `BotListItem`.
 */
class BotListItemTest {

    private fun bot(name: String, previous: List<String> = emptyList()) =
        BotProfile(name = name, previousNames = previous)

    private fun room(id: String) = GroupRoom(id = id, name = id)

    @Test
    fun `sans ordre, les bots puis les salles`() {
        val items = BotListItem.arranged(
            bots = listOf(bot("kilo"), bot("default")),
            rooms = listOf(room("onyx-1")),
            order = emptyList(),
        )

        assertEquals(
            listOf("bot:kilo", "bot:default", "room:onyx-1"),
            items.map { it.id },
        )
    }

    @Test
    fun `un ordre enregistre met les lignes dans cet ordre`() {
        val items = BotListItem.arranged(
            bots = listOf(bot("kilo"), bot("scout")),
            rooms = listOf(room("onyx-1")),
            order = listOf("room:onyx-1", "bot:scout", "bot:kilo"),
        )

        assertEquals(listOf("room:onyx-1", "bot:scout", "bot:kilo"), items.map { it.id })
    }

    @Test
    fun `un bot renomme est suivi par son ancien nom`() {
        val renamed = bot("scout", previous = listOf("explorer"))

        val items = BotListItem.arranged(
            bots = listOf(bot("kilo"), renamed),
            rooms = emptyList(),
            order = listOf("bot:explorer", "bot:kilo"),
        )

        // L'ancien nom désigne toujours le même bot, et il ne reparaît pas une seconde fois.
        assertEquals(listOf("bot:scout", "bot:kilo"), items.map { it.id })
    }

    @Test
    fun `un nouveau venu et une salle terminee se placent a la fin`() {
        val items = BotListItem.arranged(
            bots = listOf(bot("kilo"), bot("newbie")),
            rooms = emptyList(),
            order = listOf("bot:kilo", "room:gone"),
        )

        assertEquals(listOf("bot:kilo", "bot:newbie"), items.map { it.id })
    }

    @Test
    fun `un glissement enregistre l ordre montre, les lignes cachees restant a la fin`() {
        val shown = BotListItem.arranged(
            bots = listOf(bot("kilo"), bot("scout")),
            rooms = listOf(room("onyx-1")),
            order = emptyList(),
        )

        // La dernière ligne montée en tête: les lignes gardées mais non montrées suivent.
        val order = BotListItem.orderAfter(
            shown = shown,
            from = 2,
            to = 0,
            kept = listOf("bot:hidden-one"),
        )

        assertEquals(listOf("room:onyx-1", "bot:kilo", "bot:scout", "bot:hidden-one"), order)
    }

    @Test
    fun `un glissement hors de la section ne change rien`() {
        val shown = BotListItem.arranged(bots = listOf(bot("kilo")), rooms = emptyList(), order = emptyList())

        assertEquals(listOf("bot:kept"), BotListItem.orderAfter(shown, from = 5, to = 0, kept = listOf("bot:kept")))
    }
}
