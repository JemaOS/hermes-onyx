package com.onyx.app.ui.cards

import com.onyx.app.data.model.ReplyCard
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mots et les chiffres des cartes d'une réponse: heures et jours écrits selon la langue du
 * téléphone, températures, durées et distances, et où ouvrir l'app de cartes.
 */
class ReplyCardFormatTest {

    @Test
    fun `une heure s ecrit comme le telephone l ecrit`() {
        // L'heure seule, dans la langue du téléphone: l'heure d'Europe se lit « 14 » (17 sur
        // Android, où ICU donne « 14 h »), celle des États-Unis « 2 PM ».
        assertTrue(ReplyCardFormat.hourLabel("14:00", Locale.FRANCE).startsWith("14"))
        assertTrue(ReplyCardFormat.hourLabel("14:00", Locale.US).startsWith("2"))
        assertTrue(ReplyCardFormat.hourLabel("14:00", Locale.US).endsWith("PM"))
        // Une heure ISO, comme le plugin en écrit une.
        assertTrue(ReplyCardFormat.hourLabel("2026-10-23T10:00", Locale.FRANCE).startsWith("10"))
        // Un texte qui n'est pas une heure reste tel quel.
        assertEquals("tonight", ReplyCardFormat.hourLabel("tonight", Locale.FRANCE))
        assertEquals("25:00", ReplyCardFormat.hourLabel("25:00", Locale.FRANCE))
    }

    @Test
    fun `aujourd hui s ecrit Aujourd hui, un autre jour son nom`() {
        val today = LocalDate.of(2026, 10, 23)
        assertEquals("Aujourd’hui", ReplyCardFormat.dayLabel("2026-10-23", today = today, locale = Locale.FRANCE))
        val tomorrow = ReplyCardFormat.dayLabel("2026-10-24", today = today, locale = Locale.FRANCE)
        assertTrue("le jour abrégé : $tomorrow", tomorrow.startsWith("sam"))
        assertEquals("Sat", ReplyCardFormat.dayLabel("2026-10-24", today = today, locale = Locale.US))
        // Le jour long, écrit dans la langue du téléphone (« vendredi 23 octobre »).
        assertEquals("vendredi 23 octobre", ReplyCardFormat.longDate(today, Locale.FRANCE))
        // Une date illisible reste telle quelle.
        assertEquals("soon", ReplyCardFormat.dayLabel("soon", today = today, locale = Locale.FRANCE))
        assertEquals(LocalDate.of(2026, 1, 2), ReplyCardFormat.date("2026-01-02"))
        assertNull(ReplyCardFormat.date("2026-13-45"))
    }

    @Test
    fun `les degres sont des nombres entiers`() {
        assertEquals("19°", ReplyCardFormat.degrees(19.0))
        assertEquals("76°", ReplyCardFormat.degrees(75.5))
        assertEquals("0°", ReplyCardFormat.degrees(0.0))
        assertEquals("-12°", ReplyCardFormat.degrees(-11.5))
    }

    @Test
    fun `un temps se nomme  le nomme`() {
        assertEquals("Clear skies", ReplyCardFormat.conditionName(ReplyCard.Condition.CLEAR))
        assertEquals("Partly cloudy", ReplyCardFormat.conditionName(ReplyCard.Condition.PARTLY_CLOUDY))
        assertEquals("Thunderstorms", ReplyCardFormat.conditionName(ReplyCard.Condition.THUNDERSTORM))
        assertEquals("", ReplyCardFormat.conditionName(ReplyCard.Condition.UNKNOWN))
    }

    @Test
    fun `un chemin se nomme et se mesure`() {
        assertEquals("Walk", ReplyCardFormat.modeName(ReplyCard.Route.Mode.WALKING))
        assertEquals("Drive", ReplyCardFormat.modeName(ReplyCard.Route.Mode.DRIVING))
        assertEquals("Cycle", ReplyCardFormat.modeName(ReplyCard.Route.Mode.CYCLING))
        assertEquals("Transit", ReplyCardFormat.modeName(ReplyCard.Route.Mode.TRANSIT))
    }

    @Test
    fun `une duree et une distance s abregent`() {
        assertTrue(ReplyCardFormat.duration(720).contains("12"))
        assertTrue(ReplyCardFormat.duration(4_800).contains("1"))
        // À la route, comme `Measurement(.road)`: des mètres, puis des kilomètres.
        assertEquals("600 m", ReplyCardFormat.distance(600.0, Locale.US))
        assertEquals("1,2 km", ReplyCardFormat.distance(1_234.0, Locale.FRANCE))
        assertEquals("1.2 km", ReplyCardFormat.distance(1_234.0, Locale.US))
    }

    @Test
    fun `deux points se mesurent a vol d oiseau`() {
        // Le bureau et Rossio Roasters, à Lisbonne: un peu plus d'un kilomètre.
        val meters = ReplyCardFormat.straightLineMeters(38.7076, -9.1365, 38.7176, -9.1404)
        assertTrue("attendu ~1,2 km, obtenu $meters", meters in 1_000.0..1_400.0)
        assertEquals(0.0, ReplyCardFormat.straightLineMeters(38.7, -9.1, 38.7, -9.1), 0.001)
    }

    @Test
    fun `l appui sur la carte ouvre le lieu, sinon le site d OpenStreetMap`() {
        val one = ReplyCard.Place(name = "Rossio Roasters", latitude = 38.7176, longitude = -9.1404)
        val uri = ReplyCardFormat.mapsUri(listOf(one))!!
        assertTrue(uri.startsWith("geo:38.7176,-9.1404"))
        assertTrue(uri.contains("Rossio+Roasters"))

        val two = listOf(one, ReplyCard.Place(name = "Tram 28 Café", latitude = 38.7103, longitude = -9.1378))
        val many = ReplyCardFormat.mapsUri(two)!!
        assertTrue("plusieurs lieux : le site d'OpenStreetMap", many.startsWith("https://www.openstreetmap.org/"))

        assertNull("sans coordonnées, rien à ouvrir", ReplyCardFormat.mapsUri(listOf(ReplyCard.Place(name = "Nowhere"))))

        val route = ReplyCard.Route(to = one, mode = ReplyCard.Route.Mode.WALKING)
        assertTrue(ReplyCardFormat.routeUri(route)!!.contains("-9.1404"))
    }
}
