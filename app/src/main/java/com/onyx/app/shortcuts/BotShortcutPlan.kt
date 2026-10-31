package com.onyx.app.shortcuts

import com.onyx.app.data.model.serve.BotProfile

/**
 * Un raccourci de bot, tel qu'il se publie : de quoi nommer la ligne, peindre sa tuile, et savoir
 * quelle conversation ouvrir.
 *
 * [bot] est le **nom de profil** du bot — la clé que toutes les routes nomment ; [label] est ce
 * qu'on lit (son titre, sinon son nom) ; [chatId] est son `canonical_session`, celui qui relie la
 * conversation déjà gardée. Les trois voyagent dans l'intention du raccourci : l'app ouvre la
 * conversation du **même** bot, sans rien redemander au serveur.
 */
data class BotShortcut(
    val id: String,
    val bot: String,
    val label: String,
    val chatId: String?,
    val isDefault: Boolean,
    /**
     * Le **visage résolu** du bot, tel que la section le porte (`BotProfile.faceCode`) : c'est lui
     * qui donne à la tuile sa couleur, la même que celle du visage montré partout ailleurs.
     *
     * Il vient du roster, **pas** d'un calcul sur le nom : la section répartit les visages des bots
     * (`BotFace.faces`) pour que deux bots n'aient pas la même créature, si bien que la couleur
     * déduite du seul nom ne serait pas celle que l'app montre. Un bot sans visage résolu garde
     * `null` : la tuile retombe alors sur le visage de son nom.
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
     * Le nombre de raccourcis de bots publiés.
     *
     * Quatre, comme la section du menu de la référence (trois actions, puis les bots), et parce
     * qu'au-delà le menu d'un lanceur devient un annuaire : la liste garde de la place pour ce
     * qu'on y épingle.
     */
    const val MAX_BOTS = 4

    /**
     * Le rang du premier raccourci de bot : **après** les trois actions.
     *
     * Les trois raccourcis d'action sont déclarés dans `res/xml/shortcuts.xml` et prennent les rangs
     * 0, 1 et 2 ; les bots sont donc demandés à partir de 3, dans l'ordre de la règle ci-dessus.
     *
     * **Mesuré, et à savoir** : la plateforme **rebase** les rangs des raccourcis publiés sur
     * `0…n-1` dans l'ordre où ils sont donnés (`dumpsys shortcut` le montre : demandés à 3, 4, 5,
     * ils se relisent 0, 1, 2). C'est donc **l'ordre** de la liste publiée qui décide, et non la
     * valeur écrite ici ; l'écart entre les rangs des actions et ceux des bots ne peut pas être
     * obtenu. Ce que la plateforme montre ensuite est décrit dans le rapport du lot.
     */
    const val FIRST_RANK = 3

    /** Les identifiants des trois raccourcis d'action, déclarés par le manifeste. */
    val ACTION_IDS = listOf("ask", "talk", "note")

    /**
     * L'identifiant stable d'un raccourci de bot, dérivé du **nom de profil**.
     *
     * Le nom d'un profil peut porter des espaces, des points, des accents ; l'identifiant d'un
     * raccourci, lui, sert de clé au lanceur (et d'identifiant d'épingle sur un écran d'accueil).
     * Il est donc réduit aux lettres, chiffres, tirets et tirets bas, et suivi d'une empreinte de
     * quatre chiffres hexadécimaux du nom **exact** : deux profils que le nettoyage rendrait
     * identiques (`a b` et `a-b`) gardent ainsi des identifiants distincts.
     */
    fun id(bot: String): String = "bot-" + slug(bot) + "-" + fingerprint(bot)

    /** L'ordre de la section, règle de sélection comprise (voir la documentation de l'objet). */
    fun arrange(bots: List<BotProfile>, order: List<String>): List<BotProfile> {
        if (bots.size <= 1) return bots
        val position = HashMap<String, Int>()
        order.forEachIndexed { index, line -> position.putIfAbsent(line, index) }
        // Un bot suivi à travers un renommage par son ancien nom, comme `BotListItem.arranged` :
        // l'ordre enregistré parle encore de l'ancien profil.
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
                    // Le plus récent d'abord, et une activité inconnue du serveur en dernier.
                    (a.lastActive ?: Long.MIN_VALUE) != (b.lastActive ?: Long.MIN_VALUE) ->
                        if ((b.lastActive ?: Long.MIN_VALUE) > (a.lastActive ?: Long.MIN_VALUE)) 1 else -1
                    else -> rosterIndex.getValue(a.name) - rosterIndex.getValue(b.name)
                }
            }
        }
        // Sans ordre à la main, la section suit le plus récent ; sans activité connue du serveur
        // (`lastActive` nul), l'ordre du roster ne bouge pas.
        return bots.sortedByDescending { it.lastActive ?: Long.MIN_VALUE }
    }

    /** Les raccourcis à publier : la règle ci-dessus, bornée par [maxBots]. */
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

    /** Le libellé long d'un raccourci de bot. */
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
        // FNV-1a 32 bits : le même calcul que le visage d'un profil, pour que l'identifiant d'un
        // raccourci soit dérivé de son nom par une règle, jamais d'un compteur d'ordre.
        var hash = 2_166_136_261u
        for (byte in seed.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toInt() and 0xFF).toUInt()) * 16_777_619u
        }
        return (hash and 0xFFFFu).toString(16).padStart(4, '0')
    }
}
