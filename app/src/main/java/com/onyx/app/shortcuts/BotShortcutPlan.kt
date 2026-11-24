package com.onyx.app.shortcuts

import com.onyx.app.data.model.serve.BotProfile

/**
 * A bot shortcut, as it is published: enough to name the row, paint its tile, and know which
 * conversation to open.
 *
 * [bot] is the bot's **profile name** — the key every route names; [label] is what is read (its
 * title, otherwise its name); [chatId] is its `canonical_session`, the one that links the
 * conversation already kept. All three travel in the shortcut's intent: the app opens the
 * conversation of the **same** bot, without asking the server anything again.
 */
data class BotShortcut(
    val id: String,
    val bot: String,
    val label: String,
    val chatId: String?,
    val isDefault: Boolean,
    /**
     * The bot's **resolved face**, as the section carries it (`BotProfile.faceCode`): it is what
     * gives the tile its colour, the same one as the face shown everywhere else.
     *
 * It comes from the roster, **not** from a computation on the name: the section distributes the
 * bots' faces (`BotFace.faces`) so that two bots do not have the same creature, so that the
 * colour deduced from the name alone would not be the one the app shows. A bot without a
 * resolved face keeps `null`: the tile then falls back to the
 * face of its name.
     */
    val faceCode: String? = null,
)

/**
 * La **règle de sélection** de la section des bots du menu d'appui long : quels bots, dans quel
 * ordre, et combien.
 *
 * L'appui long sur l'icône ouvre un menu où trois raccourcis d'action sont déclarés par le
 * manifeste (`ask`, `talk`, `note` — ils ne bougent pas) et où une tuile par bot vient ensuite.
 * La plateforme borne cette liste : un raccourci publié à l'exécution coûte une place
 * (`getMaxShortcutCountPerActivity`, la place des raccourcis épinglés et des trois actions
 * déduite), et le lanceur n'en montre qu'une partie. D'où une règle explicite, appliquée ici et
 * nulle part ailleurs :
 *
 * 1. **Les bots que la personne a mis en tête** — l'ordre enregistré de la section (« Reorder
 *    Bots ») : c'est son choix, il passe avant tout le reste.
 * 2. **Puis les plus récemment actifs** (`lastActive`, le plus récent d'abord) : « les plus
 *    utilisés d'abord ».
 * 3. **Puis l'ordre du serveur**, tel quel.
 *
 * Le nombre est borné par [MAX_BOTS] et par la place que la plateforme laisse ([schedule]).
 */
object BotShortcutPlan {

    /**
     * The number of published bot shortcuts.
     *
     * Four, like the menu section of the reference (three actions, then the bots), and because
     * beyond that a launcher's menu becomes a directory: the list keeps room for what one pins
     * there.
     */
    const val MAX_BOTS = 4

    /**
     * The rank of the first bot shortcut: **after** the three actions.
     *
     * The three action shortcuts are declared in `res/xml/shortcuts.xml` and take ranks 0, 1 and
     * 2; the bots are therefore requested from 3 on, in the order of the rule above.
     *
     * **Measured, and worth knowing**: the platform **rebases** the ranks of published shortcuts
     * onto `0…n-1` in the order they are given (`dumpsys shortcut` shows it: requested at 3, 4, 5,
     * they read back as 0, 1, 2). It is therefore the **order** of the published list that
     * decides, and not the value written here; the gap between the actions' ranks and the bots'
     * cannot be obtained. What the platform shows next is described in the batch report.
     */
    const val FIRST_RANK = 3

    /** The identifiers of the three action shortcuts, declared by the manifest. */
    val ACTION_IDS = listOf("ask", "talk", "note")

    /**
     * The stable identifier of a bot shortcut, derived from the **profile name**.
     *
     * A profile's name can carry spaces, dots, accents; a shortcut's identifier, on the other
     * hand, serves as a key for the launcher (and as a pin identifier on a home screen). It is
     * therefore reduced to letters, digits, hyphens and underscores, and followed by a
     * four-hex-digit fingerprint of the **exact** name: two profiles that the cleaning would make
     * identical (`a b` and `a-b`) thus keep distinct identifiers.
     */
    fun id(bot: String): String = "bot-" + slug(bot) + "-" + fingerprint(bot)

    /** The order of the section, selection rule included (see the object's documentation). */
    fun arrange(bots: List<BotProfile>, order: List<String>): List<BotProfile> {
        if (bots.size <= 1) return bots
        val position = HashMap<String, Int>()
        order.forEachIndexed { index, line -> position.putIfAbsent(line, index) }
        // A bot followed through a rename by its old name, like `BotListItem.arranged`:
        // the saved order still speaks of the old profile.
        fun handPlace(bot: BotProfile): Int? {
            position["bot:" + bot.name]?.let { return it }
            bot.previousNames.forEach { old -> position["bot:$old"]?.let { return it } }
            return null
        }
        val place = bots.associate { bot -> bot.name to handPlace(bot) }
        if (place.values.any { it != null }) {
            val rosterIndex = bots.withIndex().associate { (index, bot) -> bot.name to index }
            return bots.sortedWith { a, b ->
                val ha = place[a.name] ?: Int.MAX_VALUE
                val hb = place[b.name] ?: Int.MAX_VALUE
                val byHand = ha - hb
                when {
                    byHand != 0 -> byHand
                    // The most recent first, and an activity unknown to the server last.
                    (a.lastActive ?: Long.MIN_VALUE) != (b.lastActive ?: Long.MIN_VALUE) ->
                        if ((b.lastActive ?: Long.MIN_VALUE) > (a.lastActive ?: Long.MIN_VALUE)) 1 else -1
                    else -> rosterIndex.getValue(a.name) - rosterIndex.getValue(b.name)
                }
            }
        }
        // Without a manual order, the section follows the most recent; without activity known to
        // the server (`lastActive` null), the roster order does not move.
        return bots.sortedByDescending { it.lastActive ?: Long.MIN_VALUE }
    }

    /** The shortcuts to publish: the rule above, bounded by [maxBots]. */
    fun schedule(
        bots: List<BotProfile>,
        order: List<String>,
        mainName: String,
        maxBots: Int = MAX_BOTS,
    ): List<BotShortcut> {
        if (maxBots <= 0) return emptyList()
        return arrange(bots, order).take(maxBots).map { bot ->
            BotShortcut(
                id = id(bot.name),
                bot = bot.name,
                label = bot.label(mainName),
                chatId = bot.chatId?.takeIf { it.isNotBlank() },
                isDefault = bot.isDefault,
                faceCode = bot.faceCode,
            )
        }
    }

    /** The long label of a bot shortcut. */
    fun longLabel(shortcut: BotShortcut): String = "Chat with " + shortcut.label

    private fun slug(raw: String): String {
        val cleaned = buildString {
            raw.trim().forEach { char ->
                append(if (char.isLetterOrDigit()) char.lowercaseChar() else '-')
            }
        }
        val trimmed = cleaned.trim('-').ifEmpty { "bot" }
        return if (trimmed.length <= 48) trimmed else trimmed.take(48)
    }

    private fun fingerprint(seed: String): String {
        // FNV-1a 32 bits: the same computation as a profile's face, so that a shortcut's identifier
        // is derived from its name by a rule, never from an order counter.
        var hash = 2_166_136_261u
        for (byte in seed.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xFF).toUInt()) * 16_777_619u
        }
        return (hash and 0xFFFFu).toString(16).padStart(4, '0')
    }
}
