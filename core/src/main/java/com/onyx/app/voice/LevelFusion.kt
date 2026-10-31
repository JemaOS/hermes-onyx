package com.onyx.app.voice

/**
 * **Le niveau des points, pris sur un signal que l'app maîtrise.**
 *
 * Le défaut rapporté, capture à l'appui : écran « À L'ÉCOUTE », « Allez-y. », et **sept points
 * parfaitement plats et identiques**. La cause est précise : le seul niveau affiché venait du
 * **reconnaisseur** (`onRmsChanged`). Quand ce moteur livre des mesures plates — ou aucune — les
 * points ne bougent pas, alors que rien ne dit à la personne que le son n'arrive pas.
 *
 * La correction tient en deux règles, toutes deux **pures** (donc éprouvables sur JVM) :
 *
 *  1. **[displayedLevel]** — on affiche le plus fort des deux signaux : celui du moteur et celui du
 *     micro que l'app ouvre elle-même ([MicLevel]). Un moteur muet n'efface donc plus un vrai son ;
 *  2. **[LevelFusion]** — on **décide** quand le niveau du moteur n'est pas exploitable : aucune
 *     trame, ou des trames qui ne bougent pas. C'est à ce moment-là qu'on prend le signal que l'app
 *     contrôle, et jamais avant : ouvrir un second micro pendant que le reconnaisseur travaille
 *     risque de lui voler le son.
 *
 * Et parce qu'un moteur qui n'entend rien ne doit **jamais** laisser les points plats en affichant
 * « À L'ÉCOUTE », la panne d'écoute ([listenTrouble]) est jugée sur les **trames fusionnées** : ce
 * que le moteur a reçu **plus** ce que le micro de l'app a reçu. Rien n'écoute en silence.
 */

/** Le niveau à afficher : le plus fort des deux signaux, jamais l'écrasement de l'un par l'autre. */
fun displayedLevel(engineLevel: Float, meterLevel: Float): Float =
    maxOf(engineLevel.coerceIn(0f, 1f), meterLevel.coerceIn(0f, 1f))

/**
 * Le moteur de reconnaissance livre-t-il un niveau **exploitable** ?
 *
 * Après [framesToJudge] mesures, deux cas le disent muet :
 *  - **aucune trame** (`frames == 0`) : le moteur tourne sans rien recevoir ;
 *  - **des trames plates** : l'écart entre le plus bas et le plus haut vu reste sous [flatBand].
 *
 * Tant que la réponse est « pas encore jugé », le niveau du moteur est pris tel quel et le micro de
 * l'app n'est pas ouvert.
 */
class LevelFusion(
    /** Combien de mesures avant de juger — une seconde environ de reconnaissance. */
    private val framesToJudge: Int = 16,
    /** En dessous de cet écart, la suite de niveaux est jugée plate. */
    private val flatBand: Float = 0.02f,
) {
    private var seen = 0
    private var lowest = Float.MAX_VALUE
    private var highest = -Float.MAX_VALUE
    private var needed = false

    /** Une mesure du moteur : son niveau, et le nombre total de trames qu'il a livrées. */
    fun observe(engineLevel: Float, engineFrames: Int) {
        if (needed) return
        seen += 1
        lowest = minOf(lowest, engineLevel)
        highest = maxOf(highest, engineLevel)
        if (seen < framesToJudge) return
        needed = engineFrames <= 0 || (highest - lowest) <= flatBand
    }

    /** Vrai quand il faut prendre le niveau sur le signal que l'app contrôle. */
    val wantsMeter: Boolean get() = needed

    /** Vrai une fois la question tranchée (muet ou non) — évite de juger indéfiniment. */
    val decided: Boolean get() = needed || seen >= framesToJudge

    /** Une nouvelle écoute : la question se repose. */
    fun reset() {
        seen = 0
        lowest = Float.MAX_VALUE
        highest = -Float.MAX_VALUE
        needed = false
    }
}
