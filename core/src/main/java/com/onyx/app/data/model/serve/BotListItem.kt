package com.onyx.app.data.model.serve

/**
 * Une ligne de la section « Bots »: le chat d'un bot, ou une discussion de groupe.
 * La personne peut les mettre dans l'ordre qu'elle veut (« Reorder Bots »).
 *
 * Comme `BotListItem` , avec les mêmes règles:
 * sans ordre enregistré, les bots puis les salles, comme ils viennent; avec un ordre, ceux qui y
 * sont viennent dans cet ordre — un bot suivi à travers un renommage par son ancien nom — et ceux
 * qui sont arrivés depuis vont à la fin.
 */
sealed interface BotListItem {
    val id: String

    data class Bot(val profile: BotProfile) : BotListItem {
        override val id: String get() = "bot:" + profile.name
    }

    data class Room(val room: GroupRoom) : BotListItem {
        override val id: String get() = "room:" + room.id
    }

    companion object {
        /** Les lignes de la section, dans l'ordre enregistré (`order`), sinon dans l'ordre reçu. */
        fun arranged(bots: List<BotProfile>, rooms: List<GroupRoom>, order: List<String>): List<BotListItem> {
            val items = bots.map { Bot(it) } + rooms.map { Room(it) }
            if (order.isEmpty()) return items
            val byId = LinkedHashMap<String, BotListItem>()
            for (item in items) byId.putIfAbsent(item.id, item)
            for (bot in bots) {
                for (old in bot.previousNames) byId.putIfAbsent("bot:$old", Bot(bot))
            }
            val taken = mutableSetOf<String>()
            val arranged = mutableListOf<BotListItem>()
            for (id in order) {
                val item = byId[id] ?: continue
                if (taken.add(item.id)) arranged += item
            }
            return arranged + items.filterNot { it.id in taken }
        }

        /**
         * L'ordre à garder après qu'une ligne a été glissée: l'id de chaque ligne telle qu'elle
         * est montrée maintenant, puis celles qu'on gardait et qui ne sont pas montrées (un bot
         * caché, une salle pas encore lue).
         */
        fun orderAfter(shown: List<BotListItem>, from: Int, to: Int, kept: List<String>): List<String> {
            if (from !in shown.indices) return kept
            val ids = shown.map { it.id }.toMutableList()
            val moving = ids.removeAt(from)
            ids.add(to.coerceIn(0, ids.size), moving)
            val showing = ids.toSet()
            return ids + kept.filterNot { it in showing }
        }
    }
}
