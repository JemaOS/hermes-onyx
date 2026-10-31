package com.onyx.app.data.net

/**
 * Ce que l'app demande au pont pour son aperçu : la **taille** de l'image et la **qualité**.
 *
 * L'aperçu était un 1280×720 servi à tout le monde, quel que soit l'écran en face. Or le volet de
 * l'aperçu, sur un téléphone plié, fait ~1080 pixels de large : les 200 pixels de trop étaient
 * encodés, transmis, décodés — et jetés. À l'inverse, un volet plus large (plié ouvert, en
 * paysage) montrait une image montée en éprouvette.
 *
 * L'app annonce donc la largeur **réelle** de son volet, et le pont s'y range : jamais moins de
 * [MIN_WIDTH] (illisible), jamais plus de [MAX_WIDTH] (inutile), toujours **pair** (les encodeurs
 * n'aiment pas les tailles impaires), et jamais au-dessus de l'écran du PC lui-même.
 *
 * La qualité suit la même idée : moins de pixels à l'écran cachent moins les défauts, donc on
 * peut quantifier plus fort sans que ça se voie — et chaque cran de quantificateur se paie en
 * kilo-octets par seconde.
 */
object ComputerStream {

    /** En dessous, le texte du PC ne se lit plus. */
    const val MIN_WIDTH = 480

    /** Au-dessus, on encode des pixels qu'aucun téléphone ne peut montrer. */
    const val MAX_WIDTH = 2560

    /** Le quantificateur JPEG d'un aperçu large (le pont garde son propre plafond). */
    const val QUALITY_WIDE = 7

    /** …et celui d'un petit volet, où les défauts ne se voient pas. */
    const val QUALITY_SMALL = 12

    /**
     * La largeur d'image à demander pour un volet de [paneWidthPx] pixels réels.
     *
     * @param screenWidth la largeur de l'écran du PC : demander plus n'a aucun sens.
     */
    fun viewWidthFor(paneWidthPx: Int, screenWidth: Int = MAX_WIDTH): Int {
        if (paneWidthPx <= 0) return MIN_WIDTH
        val ceiling = minOf(MAX_WIDTH, if (screenWidth > 0) screenWidth else MAX_WIDTH)
        val floor = minOf(MIN_WIDTH, ceiling)
        val wanted = paneWidthPx.coerceIn(floor, ceiling)
        // Pair : les encodeurs vidéo n'aiment pas les tailles impaires.
        return (wanted / 2) * 2
    }

    /**
     * Le quantificateur JPEG pour cette largeur d'image.
     *
     * À l'écran, l'aperçu ne fait jamais plus de ~1100 points de large : au-delà de ~1440 pixels
     * d'image, la finesse supplémentaire ne se voit pas et le poids, si.
     */
    fun qualityFor(viewWidthPx: Int): Int = when {
        viewWidthPx >= 1440 -> QUALITY_WIDE
        viewWidthPx >= 960 -> 9
        viewWidthPx >= 640 -> 11
        else -> QUALITY_SMALL
    }

    /** Le taux d'images d'un geste : plus haut que le repos, jamais plus bas. */
    fun fpsFor(base: Int, turbo: Int, moving: Boolean): Int =
        if (moving) maxOf(base, turbo) else base

    /**
     * Combien de temps la cadence haute survit au dernier geste (ms).
     *
     * Un geste est bref, mais son **résultat** arrive après : la fenêtre couvre le mouvement et ce
     * qu'il vient de changer à l'écran. Plus courte, on verrait le bureau se figer juste après
     * avoir lâché ; plus longue, on paierait du réseau pour rien.
     */
    const val TURBO_HOLD_MS = 2_500L

    /** En dessous, le texte du PC cesse d'être lisible : l'échelle n'y descend **jamais**. */
    const val READABLE_WIDTH = 900
}

/**
 * Les mesures du flux, en clair — lues **au même endroit** que celui qui décide de la qualité.
 *
 * Pas de seconde mesure parallèle : ce que l'écran montre sous « Détails du flux » est exactement
 * ce qui fait descendre ou remonter l'échelle. Une mesure à part finirait par contredire la
 * décision qu'elle prétend expliquer.
 *
 * - [rttMs] : l'aller-retour d'un `ping` applicatif (le pong du pont) ;
 * - [jitterMs] : l'écart-type des derniers aller-retours — la **gigue**, ce qui rend un lien
 *   pénible même quand sa latence moyenne est bonne ;
 * - [fps] : les images **reçues** par seconde ;
 * - [dropped] : les images jetées parce qu'elles sont arrivées en retard (l'aperçu n'en garde
 *   qu'une) ;
 * - [missed] : les trames **manquées** — ce que la cadence annoncée promettait et qui n'est jamais
 *   arrivé ;
 * - [width] / [quality] : ce qui est réellement demandé au pont ;
 * - [kbps] : le débit estimé du flux d'images.
 */
data class ComputerStreamStats(
    val rttMs: Int = 0,
    val jitterMs: Int = 0,
    val fps: Float = 0f,
    val dropped: Int = 0,
    val missed: Int = 0,
    val width: Int = 0,
    val quality: Int = 0,
    val kbps: Int = 0,
) {
    /** Vrai quand il y a de quoi juger : un lien qui n'a rien mesuré ne se juge pas. */
    val measured: Boolean get() = width > 0 || fps > 0f
}

/**
 * Un cran de l'échelle : la largeur **plafond** demandée, le quantificateur, et la cadence.
 *
 * La largeur est un plafond : l'app demande `min(plafond, volet)`, donc un petit volet ne paie
 * jamais des pixels qu'il ne montre pas.
 */
data class ComputerQualityStep(val width: Int, val quality: Int, val fps: Int)

/**
 * L'échelle de qualité : du plus lourd au plus léger, avec un **plancher de lisibilité**.
 *
 * Le dernier cran ne descend pas sous [ComputerStream.READABLE_WIDTH] : une image fluide mais
 * illisible ne sert à personne — on préfère une image moins fréquente qu'une image qu'on ne peut
 * pas lire. Le pont, lui, garde ses propres bornes (largeur, cadence) : l'échelle propose, le pont
 * dispose.
 */
object ComputerQuality {

    /** Du plus lourd (index 0) au plus léger (dernier). */
    val LADDER: List<ComputerQualityStep> = listOf(
        ComputerQualityStep(width = 2560, quality = 7, fps = 6),
        ComputerQualityStep(width = 1600, quality = 8, fps = 6),
        ComputerQualityStep(width = 1280, quality = 9, fps = 5),
        ComputerQualityStep(width = 1024, quality = 10, fps = 4),
        ComputerQualityStep(width = ComputerStream.READABLE_WIDTH, quality = 12, fps = 3),
    )

    /** Le cran le plus lourd — celui d'une liaison qui se porte bien. */
    const val HEAVIEST = 0

    /** Le cran le plus léger — celui de la « liaison économe ». */
    val LIGHTEST = LADDER.lastIndex

    /**
     * La largeur réellement demandée pour ce cran et ce volet : jamais plus que le volet, jamais
     * moins que ce qui reste lisible, toujours pair.
     */
    fun widthFor(step: ComputerQualityStep, paneWidthPx: Int): Int {
        val ceiling = if (paneWidthPx > 0) minOf(step.width, paneWidthPx) else step.width
        val floor = minOf(ComputerStream.READABLE_WIDTH, if (paneWidthPx > 0) paneWidthPx else step.width)
        val wanted = ceiling.coerceAtLeast(floor)
        return (wanted / 2) * 2
    }
}

/**
 * Le gouverneur de qualité : **descente rapide, remontée lente**, avec hystérésis.
 *
 * Un lien qui se dégrade le dit tout de suite (une seule mesure mauvaise suffit pour descendre) ;
 * un lien qui va bien doit le prouver plusieurs fois de suite avant qu'on remonte — sinon on
 * oscillerait entre deux crans, et l'oscillation coûte plus cher que le cran le plus bas.
 *
 * Il est **pur** : on lui donne des mesures, il rend une décision. C'est ce qui permet de l'éprouver
 * sans réseau, et c'est aussi ce qui garantit qu'il n'y a qu'un seul décideur.
 */
class ComputerQualityGovernor(
    private val highRttMs: Int = 400,
    private val lowRttMs: Int = 180,
    private val highMissRatio: Float = 0.12f,
    private val steadySamples: Int = 8,
) {
    /** Le cran courant. */
    var step: Int = ComputerQuality.HEAVIEST
        private set

    /** Vrai quand l'utilisateur a forcé la liaison économe. */
    var economical: Boolean = false
        private set

    private var steady = 0

    /** Change de cran à la main (l'utilisateur a cliqué), et remet le compteur à zéro. */
    fun select(index: Int) {
        step = index.coerceIn(0, ComputerQuality.LIGHTEST)
        steady = 0
    }

    /** Force le cran le plus léger ; rend le cran à demander. */
    fun forceEconomy(): Int {
        economical = true
        select(ComputerQuality.LIGHTEST)
        return step
    }

    /** Rend la main à l'échelle ; rend le cran à demander. */
    fun automatic(): Int {
        economical = false
        select(ComputerQuality.HEAVIEST)
        return step
    }

    /**
     * Une mesure de plus. Rend le **nouveau** cran quand la décision change, `null` sinon.
     *
     * `missRatio` est le rapport des trames manquées sur celles attendues, `dropped` le nombre
     * d'images jetées depuis la dernière mesure.
     */
    fun observe(stats: ComputerStreamStats, missRatio: Float, dropped: Int): Int? {
        if (economical) return null
        val bad = stats.rttMs > highRttMs || missRatio > highMissRatio || dropped >= 2
        val good = stats.rttMs in 1 until lowRttMs && missRatio <= 0f && dropped == 0
        if (bad) {
            steady = 0
            if (step < ComputerQuality.LIGHTEST) {
                step += 1
                return step
            }
            return null
        }
        if (good) {
            steady += 1
            if (steady >= steadySamples && step > ComputerQuality.HEAVIEST) {
                step -= 1
                steady = 0
                return step
            }
        } else {
            steady = 0
        }
        return null
    }
}
