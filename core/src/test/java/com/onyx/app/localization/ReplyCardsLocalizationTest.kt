package com.onyx.app.localization

import com.onyx.app.ui.cards.ReplyCardFormat
import com.onyx.app.data.model.ReplyCard
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mots des cartes d'une réponse en français : chaque libellé que les cartes affichent a sa
 * traduction, et les phrases qu'elles composent (une humidité, un écart d'heure) passent par les
 * gabarits et les motifs de [OnyxLocalization].
 */
class ReplyCardsLocalizationTest {
    private val fr = Locale.FRANCE
    private val us = Locale.US

    @Test
    fun `les actions d une carte sont traduites`() {
        assertEquals("Ouvrir dans Cartes", OnyxLocalization.translate("Open in Maps", fr))
        assertEquals("Itinéraire", OnyxLocalization.translate("Directions", fr))
        assertEquals("Carte indisponible", OnyxLocalization.translate("Map unavailable", fr))
        assertEquals("Voiture", OnyxLocalization.translate("Drive", fr))
        assertEquals("À pied", OnyxLocalization.translate("Walk", fr))
        assertEquals("Transport", OnyxLocalization.translate("Transit", fr))
        assertEquals("Vélo", OnyxLocalization.translate("Cycle", fr))
    }

    @Test
    fun `les noms de temps sont traduits`() {
        assertEquals("Ciel dégagé", OnyxLocalization.translate(ReplyCardFormat.conditionName(ReplyCard.Condition.CLEAR), fr))
        assertEquals("Partiellement nuageux", OnyxLocalization.translate("Partly cloudy", fr))
        assertEquals("Nuageux", OnyxLocalization.translate("Cloudy", fr))
        assertEquals("Brouillard", OnyxLocalization.translate("Fog", fr))
        assertEquals("Bruine", OnyxLocalization.translate("Drizzle", fr))
        assertEquals("Pluie", OnyxLocalization.translate("Rain", fr))
        assertEquals("Forte pluie", OnyxLocalization.translate("Heavy rain", fr))
        assertEquals("Orages", OnyxLocalization.translate("Thunderstorms", fr))
        assertEquals("Neige", OnyxLocalization.translate("Snow", fr))
        assertEquals("Grésil", OnyxLocalization.translate("Sleet", fr))
        assertEquals("Grêle", OnyxLocalization.translate("Hail", fr))
        assertEquals("Venteux", OnyxLocalization.translate("Windy", fr))
    }

    @Test
    fun `les gabarits gardent leurs arguments`() {
        assertEquals("Humidité 62 %", OnyxLocalization.format("Humidity %1\$d%%", 62, locale = fr))
        assertEquals("Pluie 10 %", OnyxLocalization.format("Rain %1\$d%%", 10, locale = fr))
        assertEquals("Vent 14 km/h NW", OnyxLocalization.format("Wind %1\$s", "14 km/h NW", locale = fr))
        assertEquals("Ressenti 18°", OnyxLocalization.format("Feels like %1\$s", "18°", locale = fr))
        assertEquals("Données météo par Open-Meteo.com", OnyxLocalization.translate("Weather data by Open-Meteo.com", fr))
        assertEquals("Recherche de la météo à Lisbonne…", OnyxLocalization.format("Looking up the weather in %1\$s…", "Lisbonne", locale = fr))
    }

    @Test
    fun `la carte nomme ses lieux et son chemin`() {
        assertEquals("De la gare à Rossio Roasters", OnyxLocalization.format("%1\$s to %2\$s", "la gare", "Rossio Roasters", locale = fr))
        assertEquals("D'ici à Rossio Roasters", OnyxLocalization.format("From here to %1\$s", "Rossio Roasters", locale = fr))
        assertEquals("≈ 1,2 km à vol d'oiseau", OnyxLocalization.format("≈ %1\$s straight line", "1,2 km", locale = fr))
        assertEquals("Appeler Rossio Roasters", OnyxLocalization.format("Call %1\$s", "Rossio Roasters", locale = fr))
        assertEquals("Ouvrir Alfama Bean dans Cartes", OnyxLocalization.format("Open %1\$s in Maps", "Alfama Bean", locale = fr))
        assertEquals("Site de Rossio Roasters", OnyxLocalization.format("%1\$s's website", "Rossio Roasters", locale = fr))
    }

    @Test
    fun `l heure d une carte se dit en francais`() {
        assertEquals("Votre heure", OnyxLocalization.translate("Your time", fr))
        assertEquals("Même heure", OnyxLocalization.translate("Same time", fr))
        // Une phrase que la carte compose elle-même : le motif la traduit, le nombre reste.
        assertEquals("8 h d’avance", OnyxLocalization.translate(ReplyCard.Clock.difference(28_800), fr))
        assertEquals("4 h de retard", OnyxLocalization.translate(ReplyCard.Clock.difference(-14_400), fr))
        assertEquals("12 min", OnyxLocalization.format("%1\$d min", 12, locale = fr))
        assertEquals("1 h 20 min", OnyxLocalization.format("%1\$d hr %2\$d min", 1, 20, locale = fr))
        assertEquals("Recherche de l'heure à Tokyo…", OnyxLocalization.format("Looking up the time in %1\$s…", "Tokyo", locale = fr))
    }

    @Test
    fun `en anglais les cartes gardent les mots d référence`() {
        assertEquals("Open in Maps", OnyxLocalization.translate("Open in Maps", us))
        assertEquals("Directions", OnyxLocalization.translate("Directions", us))
        assertEquals("8 hr ahead", OnyxLocalization.translate(ReplyCard.Clock.difference(28_800), us))
        assertEquals("Clear skies", OnyxLocalization.translate(ReplyCardFormat.conditionName(ReplyCard.Condition.CLEAR), us))
        assertTrue(OnyxLocalization.translate("Partly cloudy", us).startsWith("Partly"))
    }
}
