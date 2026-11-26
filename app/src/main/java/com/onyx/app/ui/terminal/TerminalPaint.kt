package com.onyx.app.ui.terminal

/**
 * **Un style par plage, pas un span par caractère.**
 *
 * `paintLine` construisait un `SpanStyle` **par caractère** : 120 spans par ligne, 40 lignes, à
 * chaque publication — soit 4 800 objets par trame, 60 trames par seconde, presque 300 000 objets
 * par seconde pendant une compilation. Aucun appareil ne suit ça, et le ramasse-miettes non plus :
 * c'est le figement qu'on voit sur un terminal bavard, celui qui n'a rien à voir avec le réseau.
 *
 * Une ligne de terminal est presque toujours d'**une seule couleur** (`ls`, `make`, un log) : grouper
 * les cellules de style identique donne donc un ou deux spans au lieu de cent vingt.
 */
data class StyleRun(val start: Int, val length: Int, val style: TerminalStyle)

/**
 * Les plages de style d'une ligne : chaque run couvre des cellules **consécutives de même style**.
 *
 * Pure (aucun type Compose), donc vérifiable sans appareil — c'est la partie qui porte la garantie.
 */
fun styleRuns(styles: List<TerminalStyle>): List<StyleRun> {
    if (styles.isEmpty()) return emptyList()
    val runs = ArrayList<StyleRun>()
    var index = 0
    while (index < styles.size) {
        val style = styles[index]
        var end = index + 1
        while (end < styles.size && styles[end] == style) end++
        runs.add(StyleRun(start = index, length = end - index, style = style))
        index = end
    }
    return runs
}

/**
 * **Le rendu d'une ligne est gardé tant que la ligne ne change pas.**
 *
 * Une sortie de terminal touche une ligne à la fois (une ligne de log s'ajoute, une barre de
 * progression se réécrit) : repeindre les quarante lignes pour une est du travail jeté. Le cache
 * retient donc le rendu **par rangée**, avec sa clé (le texte et une empreinte de style — une
 * commande colorée peut réécrire le même texte dans une autre couleur).
 *
 * Générique sur le type de rendu : la classe reste pure, la ligne peinte (`AnnotatedString`) vient
 * de l'appelant.
 */
class TerminalLineCache<T : Any> {

    private val keys = HashMap<Int, Key>()
    private val values = HashMap<Int, T>()

    /** Le nombre de rendus réellement effectués — la mesure que les tests lisent. */
    var paints: Int = 0
        private set

    private data class Key(val text: String, val fingerprint: Int)

    fun painted(row: Int, text: String, fingerprint: Int, paint: () -> T): T {
        val key = Key(text, fingerprint)
        val cachedKey = keys[row]
        val cached = values[row]
        if (cachedKey == key && cached != null) return cached
        val fresh = paint()
        keys[row] = key
        values[row] = fresh
        paints++
        return fresh
    }

    /** Une nouvelle session : plus rien ne correspond à ce qui est à l'écran. */
    fun clear() {
        keys.clear()
        values.clear()
        paints = 0
    }
}