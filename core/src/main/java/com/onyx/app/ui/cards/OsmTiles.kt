package com.onyx.app.ui.cards

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * La géométrie d'une carte statique bâtie sur des tuiles OpenStreetMap.
 *
 * référence pose la carte d'une réponse dans MapKit (`Map(initialPosition: .automatic, interactionModes: [])`).
 * Android n'a pas d'équivalent gratuit : la carte est donc **composée ici** — le cadre est calculé
 * sur la boîte englobante des points, les tuiles choisies à un zoom raisonnable, et les épingles
 * dessinées par-dessus. Aucune clé d'API, aucun SDK payant.
 *
 * Tout ce fichier est du calcul pur (pas d'Android, pas de réseau) : c'est ce qui décide du zoom,
 * des tuiles à demander et de la place d'un point dans l'image.
 */
object OsmTiles {

    /** La taille d'une tuile OSM, en pixels (elle ne change pas). */
    const val tileSize: Int = 256

    /** Le zoom le plus proche d'une rue, et le plus large (le monde entier). */
    const val maxZoom: Int = 17
    const val minZoom: Int = 1

    /**
     * L'attribution obligatoire des tuiles OpenStreetMap (« © OpenStreetMap »), sous la carte.
     * La politique de tuiles l'exige, et l'Open Data Commons ODbL avec elle.
     */
    const val attribution: String = "© OpenStreetMap"

    /** Le gabarit d'une tuile (la politique d'usage demande un agent identifiable : voir le client). */
    fun url(zoom: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    /** La latitude au-delà de laquelle la projection de Mercator n'a plus de sens. */
    const val maxLatitude: Double = 85.05112878

    /**
     * Le cadre d'une carte : les points d'une carte de réponse, réunis. Une vue ne se calcule que
     * sur une boîte non vide.
     */
    data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double) {
        val isValid: Boolean
            get() = south.isFinite() && west.isFinite() && north.isFinite() && east.isFinite() && south <= north

        val centerLatitude: Double get() = (south + north) / 2.0
        val centerLongitude: Double get() = (west + east) / 2.0

        companion object {
            /** La boîte d'un ensemble de points ; `null` s'il n'y en a aucun. */
            fun of(points: List<Pair<Double, Double>>): Bounds? {
                if (points.isEmpty()) return null
                val latitudes = points.map { it.first }.sorted()
                val longitudes = points.map { it.second }.sorted()
                return Bounds(
                    south = latitudes.first().coerceIn(-maxLatitude, maxLatitude),
                    west = longitudes.first().coerceIn(-180.0, 180.0),
                    north = latitudes.last().coerceIn(-maxLatitude, maxLatitude),
                    east = longitudes.last().coerceIn(-180.0, 180.0),
                )
            }
        }
    }

    /** La carte à dessiner : son zoom, les tuiles à demander et où tombe chaque point. */
    data class View(
        val zoom: Int,
        val tiles: List<Tile>,
        /** Le pixel-monde (à ce zoom) du coin haut-gauche de l'image. */
        val originX: Double,
        val originY: Double,
        val widthPx: Double,
        val heightPx: Double,
    ) {
        /** La position d'un point dans l'image, en pixels ; le point peut tomber hors du cadre. */
        fun pixelOf(latitude: Double, longitude: Double): Pair<Float, Float> {
            val world = 256.0 * (1 shl zoom)
            val x = (longitude + 180.0) / 360.0 * world
            val y = tileY(latitude, zoom) * tileSize
            return Pair((x - originX).toFloat(), (y - originY).toFloat())
        }

        /** L'écart entre deux points dans l'image, en pixels (pour savoir si le cadre est trop serré). */
        fun pixelDistance(a: Pair<Double, Double>, b: Pair<Double, Double>): Float {
            val (ax, ay) = pixelOf(a.first, a.second)
            val (bx, by) = pixelOf(b.first, b.second)
            return kotlin.math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        }
    }

    /** Une tuile à demander : `x`/`y` sont ceux du schéma OSM, `left`/`top` sa place dans l'image. */
    data class Tile(val zoom: Int, val x: Int, val y: Int, val left: Float, val top: Float) {
        val key: String get() = "$zoom/$x/$y"
        val url: String get() = OsmTiles.url(zoom, x, y)
    }

    /** La largeur du monde, en pixels, à ce zoom. */
    fun worldPixels(zoom: Int): Double = tileSize.toDouble() * (1 shl zoom)

    /** La coordonnée « tuile » (non entière) de cette longitude à ce zoom. */
    fun tileX(longitude: Double, zoom: Int): Double = (longitude + 180.0) / 360.0 * (1 shl zoom)

    /** La coordonnée « tuile » (non entière) de cette latitude à ce zoom, en Mercator. */
    fun tileY(latitude: Double, zoom: Int): Double {
        val clamped = latitude.coerceIn(-maxLatitude, maxLatitude)
        val radians = clamped * PI / 180.0
        val y = (1.0 - ln(tan(radians) + 1.0 / kotlin.math.cos(radians)) / PI) / 2.0
        return y * (1 shl zoom)
    }

    /** Le plus grand zoom auquel la boîte tient dans l'image, marge comprise. */
    fun zoomFor(bounds: Bounds, widthPx: Int, heightPx: Int, paddingPx: Int = 28): Int {
        val usableWidth = (widthPx - 2 * paddingPx).coerceAtLeast(32)
        val usableHeight = (heightPx - 2 * paddingPx).coerceAtLeast(32)
        var zoom = maxZoom
        while (zoom > minZoom) {
            val world = worldPixels(zoom)
            val spanX = abs(tileX(bounds.east, zoom) - tileX(bounds.west, zoom)) * tileSize
            val spanY = abs(tileY(bounds.south, zoom) - tileY(bounds.north, zoom)) * tileSize
            if (spanX <= usableWidth && spanY <= usableHeight) return zoom
            zoom--
        }
        return minZoom
    }

    /**
     * La carte d'une boîte, dans une image de [widthPx] sur [heightPx] : le zoom choisi, les tuiles
     * à demander (bornées : 24 au plus) et le coin de l'image.
     */
    fun view(bounds: Bounds, widthPx: Int, heightPx: Int, paddingPx: Int = 28, maxTiles: Int = 24): View? {
        if (!bounds.isValid || widthPx <= 0 || heightPx <= 0) return null
        val zoom = zoomFor(bounds, widthPx, heightPx, paddingPx)
        val centerX = tileX(bounds.centerLongitude, zoom) * tileSize
        val centerY = tileY(bounds.centerLatitude, zoom) * tileSize
        val originX = centerX - widthPx / 2.0
        val originY = centerY - heightPx / 2.0
        val firstTileX = floor(originX / tileSize).toInt()
        val firstTileY = floor(originY / tileSize).toInt()
        val lastTileX = floor((originX + widthPx) / tileSize).toInt()
        val lastTileY = floor((originY + heightPx) / tileSize).toInt()
        val count = (1 shl zoom)
        val tiles = buildList {
            for (x in firstTileX..lastTileX) {
                for (y in firstTileY..lastTileY) {
                    if (y < 0 || y >= count) continue
                    // Au-delà des bords du monde, la tuile est la même que celle de l'autre bord.
                    val wrapped = ((x % count) + count) % count
                    add(Tile(zoom, wrapped, y, ((x * tileSize) - originX).toFloat(), ((y * tileSize) - originY).toFloat()))
                    if (size >= maxTiles) return@buildList
                }
            }
        }
        return View(zoom = zoom, tiles = tiles, originX = originX, originY = originY, widthPx = widthPx.toDouble(), heightPx = heightPx.toDouble())
    }
}
