package com.onyx.app.data.model.serve

/**
 * Un renommage de profil : l'ancien nom, et celui qu'il porte maintenant.
 *
 * Le serveur ne donne **aucun identifiant immuable** par profil (vérifié : `profiles.list` /
 * `GET api/profiles` n'exposent que `name`, `path`, `display_name`, `model`, `is_default`…). Le
 * seul fil d'Ariane d'un renommage est donc `previous_names`, la liste que l'outil de renommage
 * ajoute au `profile.yaml` (`hermes profile rename`) — d'où ce type.
 */
data class ProfileRename(val from: String, val to: String) {
    /** La trace écrite dans le journal local : « ancien=>courant ». */
    val fact: String get() = "$from=>$to"
}

/**
 * Les renommages à reporter sur le nom courant de l'état local.
 *
 * Deux sources, dans cet ordre :
 *
 * 1. **`previous_names`** du serveur — le fil d'Ariane d'un renommage, quand il est fourni ;
 * 2. une **table d'alias locale** ([LocalAliases]) — le repli quand le serveur d'un Dashboard
 *    plus ancien ne dit pas `previous_names`.
 *
 * Deux garde-fous, pour ne jamais voler l'état d'un bot qui n'a rien à voir :
 * - si l'**ancien** nom est celui d'un profil **vivant** du roster, on ne reporte rien : deux
 *   profils ne peuvent pas porter le même nom à la fois, et se tromper vaut moins que ne rien
 *   faire ;
 * - si le **nom courant** n'est pas connu du roster (serveur injoignable), on ne reporte rien
 *   non plus : la migration se rejoue au prochain chargement, quand le roster est lisible.
 */
object ProfileRenames {

    /**
     * La table d'alias locale : les renommages qu'on connaît **sans** le serveur.
     *
     * Elle est **vide** : la source d'un renommage est le `previous_names` du profil, que le
     * serveur porte pour un profil réellement renommé. La table reste le **repli** — le point par
     * lequel un appelant peut déclarer un ancien nom qu'un Dashboard plus ancien tairait — mais
     * aucun ancien nom n'y est écrit en dur : c'est une donnée du serveur, pas une constante du
     * code.
     */
    val LocalAliases: Map<String, String> = emptyMap()

    /** Les renommages à reporter, déduits du roster et de la table d'alias. */
    fun plan(roster: List<BotProfile>, aliases: Map<String, String> = LocalAliases): List<ProfileRename> {
        val current = roster.map { it.name }.toSet()
        val planned = LinkedHashMap<String, ProfileRename>()

        fun consider(from: String, to: String) {
            if (from.isBlank() || to.isBlank() || from == to) return
            // Un profil vivant porte ce nom : ne pas lui voler son état.
            if (from in current) return
            // Le nom courant n'est pas connu : rien à reporter pour l'instant.
            if (to !in current) return
            planned.putIfAbsent(from, ProfileRename(from, to))
        }

        for (bot in roster) {
            for (old in bot.previousNames) consider(old, bot.name)
        }
        for ((from, to) in aliases) consider(from, to)
        return planned.values.toList()
    }
}
