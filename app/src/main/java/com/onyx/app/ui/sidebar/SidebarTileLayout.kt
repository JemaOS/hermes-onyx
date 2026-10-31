package com.onyx.app.ui.sidebar

/**
 * **La grille de raccourcis du volet, personnalisable** — ses bornes, son ordre, sa bibliothèque.
 *
 * Le volet dessinait jusqu'ici **six** tuiles figées (les six destinations de la référence), tandis
 * que l'app en déclare **dix** ([SidebarDestinations.tiles]) : les autres sont **déjà** servies par le
 * routeur du volet (`OnyxApp` → `paneScreens`), elles n'étaient simplement plus dessinées. Ce fichier
 * ne crée **aucune route** : il choisit **lesquelles** sont sur la grille, **dans quel ordre**, et
 * laisse **tout le reste** disponible dans une bibliothèque.
 *
 * ## Les règles, tenues ici et mesurées par les tests
 *
 * - **De [MIN_TILES] à [MAX_TILES] tuiles.** Un retrait qui passerait sous le plancher est
 *   **refusé** avec sa raison ([removalRefusal]) ; un ajout au-delà du plafond aussi
 *   ([additionRefusal]). **Jamais de troncature silencieuse** : la grille ne rogne pas une liste,
 *   elle refuse et elle le dit.
 * - **Rien ne disparaît** : une tuile retirée reste dans [library] et se remet en un appui.
 * - **La valeur par défaut, ce sont les six actuelles** ([defaultIds]) : une clé absente — ou
 *   inconnue d'une version antérieure — rend la grille d'origine, jamais une grille vide.
 * - **Un réglage déjà stocké n'est pas invalidé** : [parse] garde les identifiants qu'il connaît,
 *   dans l'ordre où ils ont été enregistrés, ignore ce qu'il ne connaît pas, et ne complète la
 *   grille que si elle est **descendue sous le plancher** ([normalize]).
 *
 * Tout est **pur** (aucun Compose, aucune horloge, aucun accès disque) : la règle se mesure sans
 * appareil, et l'écran ne fait que la dessiner.
 */
internal object SidebarTileLayout {

    /** **Le plancher de la grille** : sous quatre tuiles, elle ne se lit plus comme une grille. */
    const val MIN_TILES: Int = 4

    /** **Le plafond de la grille** : au-delà de douze, elle ne tient plus sur une hauteur d'écran. */
    const val MAX_TILES: Int = 12

    /** Les bornes de la maison. Un test les **neutralise** pour prouver que le verrou mord. */
    val bounds: TileGridBounds = TileGridBounds.House

    /** Le séparateur de la valeur enregistrée : aucun identifiant de route n'en porte. */
    const val SEPARATOR: String = ","

    /**
     * **La grille d'origine : les six tuiles de la référence, dans son ordre.** C'est la valeur par
     * défaut du réglage, et ce que « Réinitialiser » repose. Elle est **dérivée** de
     * [SidebarDestinations.referenceTiles] : si la référence change, le défaut suit — il n'est pas
     * recopié ici à la main.
     */
    val defaultIds: List<String> = SidebarDestinations.referenceTiles.map { it.id }

    /** **Le catalogue complet** : toutes les destinations que le volet sait ouvrir, dans l'ordre déclaré. */
    val catalog: List<SidebarTile> get() = SidebarDestinations.tiles

    /** Les identifiants connus, pour ne jamais accepter une tuile qui n'aurait pas d'écran. */
    private val knownIds: Set<String> get() = catalog.map { it.id }.toSet()

    /** La tuile d'un identifiant, ou `null` s'il n'en existe aucune (aucune destination morte). */
    fun tile(id: String): SidebarTile? = catalog.firstOrNull { it.id == id }

    /** Les tuiles d'une liste d'identifiants, dans l'ordre donné ; ce qui est inconnu est ignoré. */
    fun resolve(ids: List<String>): List<SidebarTile> = ids.mapNotNull { tile(it) }

    /**
     * **La bibliothèque** : les destinations disponibles **mais pas sur la grille**, sous la même
     * forme de tuiles. C'est ce qui rend un retrait **réversible** : rien ne quitte l'app.
     */
    fun library(shown: List<String>): List<SidebarTile> {
        val visible = shown.toSet()
        return catalog.filter { it.id !in visible }
    }

    /** La grille entre-t-elle dans ses bornes ? */
    fun inBounds(ids: List<String>): Boolean = ids.size in MIN_TILES..MAX_TILES

    /**
     * **Le refus d'un retrait**, ou `null` quand il passe : la raison du refus, telle qu'elle sera
     * écrite à l'écran (une clé de traduction, remplie de [MIN_TILES]).
     */
    fun removalRefusal(ids: List<String>): String? = bounds.removalRefusal(ids.size)

    /**
     * **Le refus d'un ajout**, ou `null` quand il passe. Un ajout est refusé **au plafond**, et pour
     * un identifiant que le catalogue ne porte pas — jamais pour une autre raison : aucune tuile
     * n'est silencieusement écartée.
     */
    fun additionRefusal(ids: List<String>, id: String): String? {
        bounds.additionRefusal(ids.size)?.let { return it }
        return when {
            tile(id) == null -> TILE_UNKNOWN_REFUSAL
            id in ids -> TILE_ALREADY_REFUSAL
            else -> null
        }
    }

    /** Le retrait, s'il passe ; sinon la liste **inchangée** (le refus est lu par l'appelant). */
    fun remove(ids: List<String>, id: String): List<String> =
        if (removalRefusal(ids) != null || id !in ids) ids else ids.filterNot { it == id }

    /** L'ajout, s'il passe ; sinon la liste **inchangée**. */
    fun add(ids: List<String>, id: String): List<String> =
        if (additionRefusal(ids, id) != null) ids else ids + id

    /**
     * **Le réordonnancement** : la tuile de [from] prend la place de [to], les autres se resserrent
     * autour d'elle, **sans rien perdre ni déplacer au-delà**. Un déplacement qui ne change rien (même
     * place, index hors liste) rend la liste telle quelle.
     */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices) return ids
        val target = to.coerceIn(0, ids.lastIndex)
        if (target == from) return ids
        val out = ids.toMutableList()
        out.add(target, out.removeAt(from))
        return out
    }

    /**
     * **Le réglage enregistré, relu.** Vide, inconnu ou illisible : la grille d'origine. Les
     * identifiants connus sont gardés **dans l'ordre enregistré** ; ceux d'une version antérieure
     * (retirés du catalogue) sont ignorés sans invalider le reste. Aucun remplacement silencieux.
     */
    fun parse(stored: String?): List<String> {
        val kept = stored.orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in knownIds }
            .distinct()
        if (kept.isEmpty()) return defaultIds
        return normalize(kept)
    }

    /** La valeur à écrire : les identifiants, dans l'ordre, séparés par [SEPARATOR]. */
    fun encode(ids: List<String>): String = normalize(ids).joinToString(SEPARATOR)

    /**
     * La grille **ramenée dans ses bornes** : au plus [MAX_TILES] tuiles, au moins [MIN_TILES]. Sous
     * le plancher, elle est complétée par les tuiles d'origine qu'elle ne porte pas encore — jamais
     * laissée plus courte que ce que l'écran sait dessiner.
     */
    fun normalize(ids: List<String>): List<String> {
        val kept = ids.filter { it in knownIds }.distinct().take(MAX_TILES)
        if (kept.size >= MIN_TILES) return kept
        return (kept + defaultIds.filterNot { it in kept }).take(MIN_TILES)
    }

    /** Le refus d'un identifiant que le catalogue ne porte pas. */
    internal const val TILE_UNKNOWN_REFUSAL: String = "This screen has no shortcut."

    /** Le refus d'un identifiant déjà sur la grille. */
    internal const val TILE_ALREADY_REFUSAL: String = "This shortcut is already on the grid."
}

/**
 * **Les bornes d'une grille**, nommées à part pour être **mesurées** — et pour qu'un test puisse les
 * **neutraliser** ([Neutralized]) et montrer que c'est bien elles qui font rougir le contrôle.
 */
internal data class TileGridBounds(val min: Int, val max: Int) {

    /** Le refus d'un retrait, quand la grille est à [min] tuiles ou moins. */
    fun removalRefusal(size: Int): String? =
        if (size <= min) AT_LEAST_REFUSAL else null

    /** Le refus d'un ajout, quand la grille est à [max] tuiles ou plus. */
    fun additionRefusal(size: Int): String? =
        if (size >= max) AT_MOST_REFUSAL else null

    companion object {

        /** Les bornes de la maison : celles du brief — quatre au moins, douze au plus. */
        val House: TileGridBounds = TileGridBounds(SidebarTileLayout.MIN_TILES, SidebarTileLayout.MAX_TILES)

        /**
         * **Des bornes neutralisées** : aucune limite. Ce n'est pas une variante de l'app — c'est la
         * **contre-épreuve** des tests de bornes : avec elle, les mêmes assertions passent, ce qui
         * prouve que ce sont [House] qui les font échouer, et non la forme du code.
         */
        val Neutralized: TileGridBounds = TileGridBounds(1, 99)

        /** « Une grille garde au moins %d tuiles. » — rempli de [min] au rendu. */
        const val AT_LEAST_REFUSAL: String = "A grid keeps at least %d tiles."

        /** « Une grille contient %d tuiles au maximum. » — rempli de [max] au rendu. */
        const val AT_MOST_REFUSAL: String = "A grid holds %d tiles at most."
    }
}
