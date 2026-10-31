package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * **La rune du lanceur — la seule source de vérité du signe de la maison.**
 *
 * Le signe que l'application porte partout où elle dit qui elle est : le cercle et la **rune
 * Othalan ouverte**, telle qu'elle a été mesurée sur la couche de premier plan de l'icône adaptative
 * (`ic_launcher_adaptive_foreground.xml`, du lot de l'icône), au recouvrement de silhouette près.
 *
 * **Pourquoi ce fichier existe.** Le glyphe que l'application dessinait pour elle-même
 * (`core/src/main/res/drawable/ic_launcher_foreground.xml`) était un trace **fermé** : sa jambe
 * gauche et sa jambe droite se rejoignaient par une barre en bas, ce que l'icône du lanceur n'a pas.
 * Deux runes voisines dans la même application, c'est deux signes à maintenir et deux identités à
 * l'écran — le défaut que ce fichier supprime. **Le trace est écrit une fois, ici** ; le dessin de
 * l'application et la ressource de notification s'y conforment, et le verrou
 * (`app/src/test/.../RuneSourceUniqueTest.kt`) fait échouer toute divergence **en nommant le
 * fichier et la ligne**.
 *
 * **Le trace vient de l'icône, il n'est pas réinventé.** Les sommets sont ceux relevés sur la couche
 * de premier plan de l'icône adaptative (cercle de rayon 31,5 dans un canevas de 108, rune de
 * hauteur 0,40 du diamètre, trait plein de 3,88 à bouts ronds) : `M47.15,64.73 …`. Le trace est
 * **ouvert** — la rune de l'icône n'a pas de barre en bas, et c'est ce que le glyphe de l'app
 * refermait.
 *
 * Ce fichier ne rend **aucun texte** : il ne fait donc pas partie des routes d'interface du verrou
 * du socle, et son trait (3,88 dans le canevas de 108) n'est pas une valeur d'écartement d'écran —
 * c'est la **géométrie du signe**, mise à l'échelle au dessin.
 */
object OnyxRune {

    /**
     * Le trace de référence, **mot pour mot de la couche de premier plan de l'icône adaptative**.
     * Cinq sommets, aucun `Z` : le trace est ouvert.
     */
    const val PATH_DATA: String = "M47.15,64.73 L61.08,50.80 L54,43.27 L46.92,50.80 L60.85,64.73"

    /** Le trait plein de la référence, **dans le canevas de 108**. */
    const val STROKE_WIDTH: Float = 3.88f

    /** Le canevas de la référence : la couche de premier plan fait 108 × 108. */
    const val VIEWPORT: Float = 108f

    /**
     * Le centre du signe dans le canevas de référence (54, 54) : la rune est centrée dans le cercle.
     */
    const val CENTER: Float = 54f

    /**
     * La hauteur de la rune rapportée au diamètre du disque, **mesurée sur l'icône** : 0,40.
     *
     * C'est l'anatomie de l'emblème — le signe ne remplit jamais son disque, il s'y pose.
     */
    const val HEIGHT_FRACTION: Float = 0.40f

    /**
     * **Le signe de la maison ne se teinte pas.** La marque de l'application — la plaque sombre, le
     * cercle et la rune — vit dans [OnyxBrand], avec ses couleurs prises sur l'icône de lanceur :
     * une marque est l'identité de l'app, elle ne se dérive pas du thème. Ce fichier ne porte donc
     * plus de densité de teinte : la constante de disque teinté a disparu avec `OnyxRuneMark`.
     */

    /**
     * Les tailles du signe, celles que la planche de lisibilité éprouve et que l'application
     * utilise. Une taille de rune hors de cette liste est une improvisation.
     */
    val Sizes: List<Dp> = listOf(20.dp, 24.dp, 28.dp, 44.dp, 56.dp)

    /** Le plus petit signe que la maison dessine. */
    val Smallest: Dp = Sizes.first()

    /** La taille d'une ombre d'onglet, d'une pastille secondaire. */
    val Small: Dp = 24.dp

    /** La taille d'un signe de tête. */
    val Medium: Dp = 28.dp

    /** La vignette de rangée, la carte de serveur, la tête du volet : la cible du pouce. */
    val Mark: Dp = OnyxSocle.ThumbnailSize

    /** Le signe d'une planche, d'un écran vide. */
    val Large: Dp = 56.dp

    // =============================================================================================
    // 1 · La géométrie — pure, sans Compose, pour être mesurée par un test et par un script
    // =============================================================================================

    /** Un sommet du trace, dans le canevas de 108. */
    data class Vertex(val x: Float, val y: Float)

    /**
     * Le trace analysé : les sommets, dans l'ordre, et **le fait qu'il soit ouvert**.
     *
     * L'analyseur lit la **même chaîne** que la ressource publie ([PATH_DATA]) : un trace écrit
     * autrement dans la ressource ne peut pas passer pour la référence.
     */
    data class Trace(val vertices: List<Vertex>, val closed: Boolean, val strokeWidth: Float)

    init {
        require(PATH_DATA.endsWith("Z").not()) {
            "la rune de la maison est OUVERTE : un trace finissant par « Z » referme la rune " +
                "de l'icône et fait deux signes dans la même application"
        }
    }

    /**
     * Analyse un trace de type « M x,y L x,y … [Z] » — les seules formes d'écriture que la
     * ressource emploie. Pure : aucun type Android, elle s'exécute dans un test JVM.
     */
    fun parse(pathData: String, strokeWidth: Float = STROKE_WIDTH): Trace {
        var closed = false
        var command = ""
        val vertices = mutableListOf<Vertex>()
        // Un jeton est **soit** une lettre de commande, **soit** un nombre — la forme d'écriture
        // de la ressource colle la commande au premier nombre (« M47.15,64.73 »), il faut donc les
        // séparer avant de lire.
        val tokens = Regex("[A-Za-z]|[-+]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)")
            .findAll(pathData)
            .map { it.value }
            .toList()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.length == 1 && token[0].isLetter()) {
                if (token.equals("Z", ignoreCase = true)) closed = true else command = token.uppercase()
                index += 1
                continue
            }
            val x = token.toFloatOrNull()
            val y = if (index + 1 < tokens.size) tokens[index + 1].toFloatOrNull() else null
            if (x != null && y != null && (command == "M" || command == "L")) {
                vertices += Vertex(x, y)
                index += 2
            } else {
                index += 1
            }
        }
        return Trace(vertices, closed, strokeWidth)
    }

    /** Le trace de la maison. */
    val trace: Trace get() = parse(PATH_DATA, STROKE_WIDTH)

    /** La largeur du trace, en unités du canevas de 108. */
    val width: Float
        get() = trace.vertices.maxOf { it.x } - trace.vertices.minOf { it.x }

    /** La hauteur du trace, en unités du canevas de 108. */
    val height: Float
        get() = trace.vertices.maxOf { it.y } - trace.vertices.minOf { it.y }

    /** Le pas d'échelle qui amène le trace **et son trait** à une hauteur extérieure donnée. */
    fun scaleFor(heightUnits: Float): Float = heightUnits / (height + STROKE_WIDTH)

    /** La hauteur extérieure d'un trace quelconque (sommets **et** trait). */
    fun outerHeight(t: Trace): Float =
        (t.vertices.maxOf { it.y } - t.vertices.minOf { it.y }) + t.strokeWidth

    /**
     * **Un trace quelconque, placé** : centré sur ([centerX], [centerY]) et mis à l'échelle pour que
     * sa hauteur extérieure vaille [heightUnits]. Renvoie les sommets et la demi-épaisseur du trait.
     *
     * C'est le seul chemin de placement : le signe de la maison, la rune d'une ressource étrangère
     * et celle d'un ancien glyphe passent tous par ici, donc ils se comparent dans le même repère.
     */
    fun placedTrace(t: Trace, centerX: Float, centerY: Float, heightUnits: Float): Pair<List<Vertex>, Float> {
        val centreX = (t.vertices.maxOf { it.x } + t.vertices.minOf { it.x }) / 2f
        val centreY = (t.vertices.maxOf { it.y } + t.vertices.minOf { it.y }) / 2f
        val k = heightUnits / outerHeight(t)
        val points = t.vertices.map { Vertex((it.x - centreX) * k + centerX, (it.y - centreY) * k + centerY) }
        return points to (t.strokeWidth * k / 2f)
    }

    /**
     * Le trace **placé** : centré sur ([centerX], [centerY]) et mis à l'échelle pour que sa hauteur
     * vaille [heightUnits]. Renvoie les sommets et la demi-épaisseur du trait, dans le même repère.
     */
    fun placed(centerX: Float, centerY: Float, heightUnits: Float): Pair<List<Vertex>, Float> =
        placedTrace(trace, centerX, centerY, heightUnits)

    /**
     * Le trace **normalisé pour la mesure** : sommet le plus haut à `-50`, le plus bas à `+50`,
     * centré horizontalement — deux traces se comparent donc *à forme égale*, quelle que soit leur
     * échelle d'écriture.
     */
    fun normalized(t: Trace): Pair<List<Vertex>, Float> {
        val minX = t.vertices.minOf { it.x }
        val maxX = t.vertices.maxOf { it.x }
        val minY = t.vertices.minOf { it.y }
        val maxY = t.vertices.maxOf { it.y }
        val centreX = (minX + maxX) / 2f
        val centreY = (minY + maxY) / 2f
        val k = 100f / (maxY - minY)
        val points = t.vertices.map { Vertex((it.x - centreX) * k, (it.y - centreY) * k) }
        return points to (t.strokeWidth * k / 2f)
    }

    /** La distance d'un point au segment [a]–[b]. */
    private fun distanceToSegment(px: Float, py: Float, a: Vertex, b: Vertex): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-9f) return hypot(px - a.x, py - a.y)
        val t = (((px - a.x) * dx + (py - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }

    /**
     * **La silhouette** d'un trace au trait épais : vrai si le point tombe sous le trait — c'est le
     * cas quand sa distance à la ligne brisée est sous la demi-épaisseur (les bouts ronds et les
     * jointures rondes sont donc comptés, comme Compose les dessine).
     */
    fun covers(points: List<Vertex>, halfWidth: Float, closed: Boolean, x: Float, y: Float): Boolean {
        val segments = if (closed) points.size else points.size - 1
        for (i in 0 until segments) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            if (distanceToSegment(x, y, a, b) <= halfWidth) return true
        }
        return false
    }

    /**
     * **Le recouvrement des deux silhouettes (IoU)** : l'aire commune divisée par l'aire réunie,
     * échantillonnée sur une grille carrée de [samples] pas de côté. 1,0 = les deux signes sont
     * exactement le même dessin ; 0 = rien en commun.
     */
    fun iou(
        a: Pair<List<Vertex>, Float>,
        aClosed: Boolean,
        b: Pair<List<Vertex>, Float>,
        bClosed: Boolean,
        samples: Int = 400,
    ): Float {
        val extent = maxOf(
            a.first.maxOf { abs(it.x) } + a.second,
            a.first.maxOf { abs(it.y) } + a.second,
            b.first.maxOf { abs(it.x) } + b.second,
            b.first.maxOf { abs(it.y) } + b.second,
        ) * 1.05f
        var intersection = 0
        var union = 0
        for (i in 0 until samples) {
            val x = -extent + 2f * extent * (i + 0.5f) / samples
            for (j in 0 until samples) {
                val y = -extent + 2f * extent * (j + 0.5f) / samples
                val inA = covers(a.first, a.second, aClosed, x, y)
                val inB = covers(b.first, b.second, bClosed, x, y)
                if (inA && inB) intersection++
                if (inA || inB) union++
            }
        }
        return if (union == 0) 1f else intersection.toFloat() / union.toFloat()
    }

    /** Le recouvrement du trace **de la maison** contre celui d'une ressource donnée — 1,0 attendu. */
    fun iouAgainstReference(pathData: String, strokeWidth: Float, samples: Int = 400): Float {
        val other = parse(pathData, strokeWidth)
        return iou(
            normalized(trace), trace.closed,
            normalized(other), other.closed,
            samples,
        )
    }

    /**
     * **Le rapport d'encre** : la part de l'aire du carré de côté [side] que la rune peint, son
     * carré étant posé dans un disque de diamètre [side] (le signe occupe [HEIGHT_FRACTION] du
     * disque, centré). C'est la mesure qui distingue un signe d'une tache, et celle qui se vérifie
     * à toutes les tailles.
     */
    fun inkRatio(side: Float, samples: Int = 300): Float {
        val (points, halfWidth) = placed(side / 2f, side / 2f, side * HEIGHT_FRACTION)
        var painted = 0
        for (i in 0 until samples) {
            val x = side * (i + 0.5f) / samples
            for (j in 0 until samples) {
                val y = side * (j + 0.5f) / samples
                if (covers(points, halfWidth, trace.closed, x, y)) painted++
            }
        }
        return painted.toFloat() / (samples * samples)
    }

    /**
     * **Le rendu à un seul bit** : la silhouette réduite à [side] pixels de côté, chaque pixel noir
     * ou blanc selon que son centre tombe sous le trait. C'est le test qui décide de la lisibilité
     * — un signe qui survit à 20 dp à un seul bit se lit sur une notification, un onglet, un
     * tiroir.
     */
    fun oneBit(side: Int): List<Boolean> = oneBit(trace, side)

    /** Le rendu à un seul bit d'un **trace quelconque** — c'est ce qui éprouve la morsure. */
    fun oneBit(t: Trace, side: Int): List<Boolean> {
        val (points, halfWidth) = placedTrace(t, side / 2f, side / 2f, side * HEIGHT_FRACTION)
        return List(side * side) { index ->
            val x = (index % side) + 0.5f
            val y = (index / side) + 0.5f
            covers(points, halfWidth, t.closed, x, y)
        }
    }

    // =============================================================================================
    // 2 · Le dessin
    // =============================================================================================

    /** Le chemin Compose du trace, posé dans un carré de côté [side] (le disque). */
    fun path(side: Float): Path {
        val (points, _) = placed(side / 2f, side / 2f, side * HEIGHT_FRACTION)
        return Path().apply {
            points.forEachIndexed { index, vertex ->
                if (index == 0) moveTo(vertex.x, vertex.y) else lineTo(vertex.x, vertex.y)
            }
            if (trace.closed) close()
        }
    }

    /** L'épaisseur du trait, ramenée au carré de côté [side]. */
    fun strokeFor(side: Float): Float = STROKE_WIDTH * scaleFor(side * HEIGHT_FRACTION)

    /**
     * La rune seule, à l'encre [ink], dans la surface courante : le signe occupe
     * [HEIGHT_FRACTION] du côté le plus court.
     */
    fun DrawScope.drawRune(ink: Color) {
        val side = min(size.width, size.height)
        drawPath(
            path = path(side),
            color = ink,
            style = Stroke(
                width = strokeFor(side),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }

}

// La marque de l'application — la plaque sombre, le cercle et la rune — n'est **pas** ici : elle vit
// dans `OnyxBrand.kt`, avec ses couleurs prises sur l'icône de lanceur et **aucune** teinte de thème.
// `OnyxRuneMark(size, disc, tint)` a disparu : c'était une icône d'accent, pas la marque.
