package com.onyx.app.ui.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie de la carte statique composée sur des tuiles OpenStreetMap (l'adaptation Android de
 * la carte MapKit de la référence) : projection de Mercator, choix du zoom, tuiles à demander, place des
 * points dans l'image.
 */
class OsmTilesTest {

    /** Lisbonne : le bureau (Praça do Comércio) et Rossio Roasters, comme la démo de la référence. */
    private val office = 38.7076 to -9.1365
    private val rossio = 38.7176 to -9.1404

    @Test
    fun `la projection place les repere du monde`() {
        val z = 1
        assertEquals(1.0, OsmTiles.tileX(0.0, z), 1e-9)
        assertEquals(1.0, OsmTiles.tileY(0.0, z), 1e-9)
        assertEquals(0.0, OsmTiles.tileX(-180.0, z), 1e-9)
        assertEquals(2.0, OsmTiles.tileX(180.0, z), 1e-9)
        assertEquals(0.0, OsmTiles.tileY(OsmTiles.maxLatitude, z), 1e-6)
        assertEquals(2.0, OsmTiles.tileY(-OsmTiles.maxLatitude, z), 1e-6)
        // Une latitude hors du monde connu est ramenée au bord plutôt que de partir à l'infini.
        assertEquals(0.0, OsmTiles.tileY(89.0, z), 1e-6)
    }

    @Test
    fun `le cadre se calcule sur la boite des points`() {
        assertNull(OsmTiles.Bounds.of(emptyList()))
        val bounds = OsmTiles.Bounds.of(listOf(office, rossio))!!
        assertEquals(38.7076, bounds.south, 1e-9)
        assertEquals(38.7176, bounds.north, 1e-9)
        assertEquals(-9.1404, bounds.west, 1e-9)
        assertEquals(-9.1365, bounds.east, 1e-9)
    }

    @Test
    fun `le zoom se resserre sur deux rues et s elargit sur le monde`() {
        val streets = OsmTiles.Bounds.of(listOf(office, rossio))!!
        val wide = OsmTiles.Bounds(south = -40.0, west = -30.0, north = 40.0, east = 30.0)
        assertTrue("deux rues doivent zoomer près du sol", OsmTiles.zoomFor(streets, 640, 380) >= 14)
        assertTrue("le monde doit rester large", OsmTiles.zoomFor(wide, 640, 380) <= 4)
        assertTrue(
            "le zoom reste dans les bornes",
            OsmTiles.zoomFor(streets, 640, 380) in OsmTiles.minZoom..OsmTiles.maxZoom,
        )
    }

    @Test
    fun `la vue pose ses tuiles et place ses points dedans`() {
        val bounds = OsmTiles.Bounds.of(listOf(office, rossio))!!
        val view = OsmTiles.view(bounds, 640, 380)!!
        assertTrue(view.tiles.isNotEmpty())
        assertTrue("24 tuiles au plus", view.tiles.size <= 24)
        // Le centre de la boîte tombe au centre de l'image.
        val (cx, cy) = view.pixelOf(bounds.centerLatitude, bounds.centerLongitude)
        assertEquals(view.widthPx / 2, cx.toDouble(), 12.0)
        assertEquals(view.heightPx / 2, cy.toDouble(), 12.0)
        // Les deux points sont dans le cadre, et à une distance mesurable (le zoom les a suivis).
        listOf(office, rossio).forEach { (latitude, longitude) ->
            val (x, y) = view.pixelOf(latitude, longitude)
            assertTrue("point hors du cadre : $x,$y", x in 0f..view.widthPx.toFloat())
            assertTrue("point hors du cadre : $x,$y", y in 0f..view.heightPx.toFloat())
        }
        assertTrue("les deux cafés sont séparés à l'écran", view.pixelDistance(office, rossio) > 60f)
        // Le nord est plus haut que le sud, l'est plus à droite que l'ouest (le bureau est à
        // l'est de Rossio : -9,1365 contre -9,1404).
        assertTrue(view.pixelOf(rossio.first, rossio.second).second < view.pixelOf(office.first, office.second).second)
        assertTrue(view.pixelOf(rossio.first, rossio.second).first < view.pixelOf(office.first, office.second).first)
    }

    @Test
    fun `une boite vide ou invalide ne donne pas de carte`() {
        assertNull(OsmTiles.view(OsmTiles.Bounds(0.0, 0.0, -1.0, 1.0), 640, 380))
        assertNull(OsmTiles.view(OsmTiles.Bounds.of(listOf(office))!!, 0, 380))
    }

    @Test
    fun `l attribution et l url sont ceux d OpenStreetMap`() {
        assertEquals("© OpenStreetMap", OsmTiles.attribution)
        assertEquals("https://tile.openstreetmap.org/13/3888/3079.png", OsmTiles.url(13, 3888, 3079))
        // Une tuile garde sa place dans l'image.
        val bounds = OsmTiles.Bounds.of(listOf(office, rossio))!!
        val view = OsmTiles.view(bounds, 640, 380)!!
        view.tiles.forEach { tile ->
            assertEquals(tile.key, "${tile.zoom}/${tile.x}/${tile.y}")
            assertTrue(tile.url.contains("/${tile.zoom}/${tile.x}/${tile.y}.png"))
        }
    }
}
