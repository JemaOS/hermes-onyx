package com.onyx.app.localization

import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.ui.cards.ReplyCardFormat
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le réglage « Langue » : ses trois choix, sa traduction, et — le point qui compte — le fait qu'un
 * choix explicite **ne touche jamais** la langue d'Android (`Locale.getDefault()`), tout en étant
 * suivi par le rendu d'un bout à l'autre (libellés, compteurs, dates).
 */
class LanguageSettingLocalizationTest {

    @After
    fun remettreLeChoix() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    /** Les autres catalogues français, pour refuser une clé qui en écraserait une. */
    private val autresCatalogues =
        onyxSharedFrench + onyxAdditionalFrench + onyxSettingsFrench + onyxCommonFrench +
            onyxRemainingFrench + onyxSetupTerminalFrench + onyxCardsFrench + onyxLockFrench +
            onyxToolsFrench + onyxToolsPageFrench

    @Test
    fun `aucune cle du reglage de langue n ecrase une traduction existante`() {
        val collisions = onyxLanguageFrench.keys.filter { it in autresCatalogues }
        assertTrue("Clés déjà présentes ailleurs dans le dictionnaire : $collisions", collisions.isEmpty())
        onyxLanguageFrench.forEach { (key, value) ->
            assertFalse("Traduction vide pour « $key »", value.isBlank())
            assertTrue("La traduction de « $key » reste l'anglais", value != key)
        }
    }

    @Test
    fun `la ligne et la feuille de langue sont traduites`() {
        assertEquals("Langue", OnyxLocalization.translate("Language", Locale.FRANCE))
        assertEquals("Language", OnyxLocalization.translate("Language", Locale.ENGLISH))
        val message = "Onyx follows the phone's language unless you pick one. Screens, sheets, " +
            "menus and notifications follow it at once."
        assertEquals(
            "Onyx suit la langue du téléphone, sauf si vous en choisissez une. Les écrans, les " +
                "feuilles, les menus et les notifications la suivent aussitôt.",
            OnyxLocalization.translate(message, Locale.FRANCE),
        )
        assertEquals(message, OnyxLocalization.translate(message, Locale.ENGLISH))
    }

    @Test
    fun `les trois choix sont proposes et Systeme se traduit, les langues non`() {
        assertEquals(listOf("system", "fr", "en"), OnyxLanguage.choices)

        // « System » passe par le dictionnaire.
        assertEquals("Système", OnyxLocalization.translate(OnyxLanguage.label("system"), Locale.FRANCE))
        assertEquals("System", OnyxLocalization.translate(OnyxLanguage.label("system"), Locale.ENGLISH))

        // Les deux langues s'écrivent dans leur propre langue, quelle que soit celle de l'interface :
        // on retrouve toujours la sienne dans la liste.
        assertEquals("Français", OnyxLanguage.label("fr"))
        assertEquals("Français", OnyxLocalization.translate(OnyxLanguage.label("fr"), Locale.ENGLISH))
        assertEquals("Français", OnyxLocalization.translate(OnyxLanguage.label("fr"), Locale.FRANCE))
        assertEquals("English", OnyxLanguage.label("en"))
        assertEquals("English", OnyxLocalization.translate(OnyxLanguage.label("en"), Locale.FRANCE))
        assertEquals("English", OnyxLocalization.translate(OnyxLanguage.label("en"), Locale.ENGLISH))

        // Un choix absent ou inconnu retombe sur « Système », jamais sur une langue tirée au hasard.
        assertEquals("system", OnyxLanguage.normalize(null))
        assertEquals("system", OnyxLanguage.normalize(""))
        assertEquals("system", OnyxLanguage.normalize("de"))
        assertEquals("fr", OnyxLanguage.normalize("fr"))
        assertEquals("en", OnyxLanguage.normalize("en"))
    }

    @Test
    fun `un choix explicite ne touche pas la langue d Android`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            // Android est en italien : l'app en anglais doit rester en anglais **sans** que
            // `Locale.getDefault()` bouge — le micro, la lecture et les autres apps gardent la leur.
            Locale.setDefault(Locale.ITALY)
            OnyxLanguage.apply("en")
            assertEquals(Locale.ENGLISH, OnyxLanguage.preferred)
            assertEquals("Cancel", OnyxLocalization["Cancel"])
            assertEquals(Locale.ITALY, Locale.getDefault())

            OnyxLanguage.apply("fr")
            assertEquals(Locale.FRENCH, OnyxLanguage.preferred)
            assertEquals("Annuler", OnyxLocalization["Cancel"])
            assertEquals(Locale.ITALY, Locale.getDefault())
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
            Locale.setDefault(androidAUjourdHui)
        }
    }

    @Test
    fun `Systeme garde le comportement d avant, relu a chaque appel`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            OnyxLanguage.apply("system")
            Locale.setDefault(Locale.FRANCE)
            assertEquals("Annuler", OnyxLocalization["Cancel"])
            Locale.setDefault(Locale.US)
            assertEquals("Cancel", OnyxLocalization["Cancel"])
            // …et la langue préférée est exactement celle de l'appareil.
            assertEquals(Locale.getDefault(), OnyxLanguage.preferred)
            Locale.setDefault(Locale.JAPAN)
            assertEquals("Cancel", OnyxLocalization["Cancel"])
        } finally {
            Locale.setDefault(androidAUjourdHui)
        }
    }

    @Test
    fun `un ecran hors reglages suit la langue choisie`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            // L'appareil est en anglais ; le choix dit français : tout l'écran suit le choix.
            Locale.setDefault(Locale.US)
            OnyxLanguage.apply("fr")
            assertEquals("Horaires", OnyxLocalization["Schedules"])
            assertEquals("Réglages", OnyxLocalization["Settings"])
            assertEquals("Annuler", OnyxLocalization["Cancel"])
            assertEquals("3 éléments sélectionnés", OnyxLocalization["3 selected"])
            assertEquals("Réflexion pendant 6 s", OnyxLocalization["Thought for 6 s"])
            assertEquals("Langue", OnyxLocalization["Language"])

            OnyxLanguage.apply("en")
            assertEquals("Schedules", OnyxLocalization["Schedules"])
            assertEquals("Settings", OnyxLocalization["Settings"])
            assertEquals("Cancel", OnyxLocalization["Cancel"])

            // Le repli implicite de la valeur par défaut, sans argument de locale.
            OnyxLanguage.apply("fr")
            assertEquals("Annuler", OnyxLocalization["Cancel"])
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
            Locale.setDefault(androidAUjourdHui)
        }
    }

    @Test
    fun `les dates suivent la langue choisie`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val jour = LocalDate.of(2026, 10, 23)

            OnyxLanguage.apply("fr")
            assertEquals("vendredi 23 octobre", ReplyCardFormat.longDate(jour))
            assertEquals("ven.", ReplyCardFormat.dayLabel("2026-10-23", LocalDate.of(2026, 10, 1)))

            OnyxLanguage.apply("en")
            assertEquals("Friday, October 23", ReplyCardFormat.longDate(jour))
            assertEquals("Fri", ReplyCardFormat.dayLabel("2026-10-23", LocalDate.of(2026, 10, 1)))

            // « Système » : la date reprend celle d'Android, sans choisir à sa place.
            OnyxLanguage.apply("system")
            assertEquals("Friday, October 23", ReplyCardFormat.longDate(jour))
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
            Locale.setDefault(androidAUjourdHui)
        }
    }

    @Test
    fun `les nombres et les durees suivent la langue choisie`() {
        val androidAUjourdHui = Locale.getDefault()
        try {
            // L'appareil est en français : un choix anglais doit donner le point décimal anglais
            // (« 1.9 s », pas « 1,9 s ») — c'était le défaut visible dans le fil.
            Locale.setDefault(Locale.FRANCE)
            OnyxLanguage.apply("en")
            assertEquals("1.9 s", TurnMetrics.format(1.94))
            OnyxLanguage.apply("fr")
            assertEquals("1,9 s", TurnMetrics.format(1.94))

            // Et « Système » redonne exactement le séparateur de l'appareil.
            OnyxLanguage.apply("system")
            assertEquals("1,9 s", TurnMetrics.format(1.94))
            Locale.setDefault(Locale.US)
            OnyxLanguage.apply("system")
            assertEquals("1.9 s", TurnMetrics.format(1.94))
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
            Locale.setDefault(androidAUjourdHui)
        }
    }
}
