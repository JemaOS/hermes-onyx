package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mots des deux écrans de ce lot — « Capacités » et « Aperçus » — doivent sortir en français.
 *
 * Le propriétaire a demandé le lexique en français : un écran neuf qui afficherait ses titres et ses
 * phrases en anglais serait à moitié livré. Les **identifiants** du serveur (le nom d'une
 * compétence, `web_search`, l'adresse d'un serveur MCP) n'entrent pas dans le dictionnaire : l'écran
 * les rend verbatim, et ce test vérifie aussi qu'ils n'y ont pas été ajoutés.
 */
class CapabilitiesInsightsLocalizationTest {

    private val french = Locale.FRANCE

    @Test
    fun `les titres et les phrases des deux ecrans sont en francais`() {
        val expected = linkedMapOf(
            // Capacités.
            "Capabilities" to "Capacités",
            "What the agent can use in new chats" to "Ce que l’agent peut utiliser dans les nouvelles conversations",
            "Browse the catalogue" to "Parcourir le catalogue",
            "Changes apply from the next chat." to "Les changements s’appliquent à partir de la prochaine conversation.",
            "Toolsets the agent gets in new chats." to
                "Les jeux d’outils que l’agent reçoit dans les nouvelles conversations.",
            "No toolsets on this Hermes." to "Aucun jeu d’outils sur cet Hermes.",
            "No MCP servers on this Hermes." to "Aucun serveur MCP sur cet Hermes.",
            "Nothing matches this search." to "Aucun résultat pour cette recherche.",
            "The catalogue" to "Le catalogue",
            "The catalogue isn’t available on this Hermes." to "Le catalogue n’est pas disponible sur cet Hermes.",
            "Installed" to "Installée",
            "Install" to "Installer",
            "Test" to "Tester",
            "Testing…" to "Test en cours…",
            "Connected" to "Connecté",
            "Not connected" to "Non connecté",
            "Set by the plugin" to "Défini par le greffon",
            "Turned on" to "Allumée",
            "Turned off" to "Éteinte",
            "Couldn’t load" to "Impossible de charger",
            "Try again" to "Réessayer",
            // Aperçus.
            "Insights" to "Aperçus",
            "7 days" to "7 jours",
            "30 days" to "30 jours",
            "90 days" to "90 jours",
            "Estimated cost" to "Coût estimé",
            "billed" to "facturé",
            "Sessions" to "Sessions",
            "Tokens in and out" to "Jetons entrée/sortie",
            "Prompt from cache" to "Part venue du cache",
            "Tokens by day" to "Jetons par jour",
            "Peak" to "Pic",
            "Tap a day for its numbers." to "Touchez un jour pour ses chiffres.",
            "Models" to "Modèles",
            "Nothing used yet" to "Rien d’utilisé pour l’instant",
            "Couldn’t load the insights" to "Impossible de charger les aperçus",
            "The analytics route isn’t available on this Hermes." to
                "La route d’analyse n’est pas disponible sur cet Hermes.",
            "This profile only, not every profile." to "Ce profil seulement, pas tous les profils.",
            "Loading the insights…" to "Chargement des aperçus…",
        )

        for ((english, attendu) in expected) {
            assertEquals(attendu, OnyxLocalization.translate(english, french))
        }
    }

    @Test
    fun `les gabarits a nombre se composent en francais`() {
        assertEquals(
            "Rechercher les 24 compétences",
            OnyxLocalization.format("Search %d skills", 24, locale = french),
        )
        assertEquals("3.1M jetons", OnyxLocalization.format("%s tokens", "3.1M", locale = french))
        assertEquals("12 sessions", OnyxLocalization.format("%d sessions", 12, locale = french))
        assertEquals("Connecté · 4 outils", OnyxLocalization.format("Connected · %d tools", 4, locale = french))
    }

    @Test
    fun `une cle inconnue reste telle quelle`() {
        // Ce qui n'est pas un libellé d'Onyx ne se traduit pas : une réponse du serveur, un nom de
        // compétence, une adresse.
        assertEquals("web_search", OnyxLocalization.translate("web_search", french))
        assertEquals("hermes-agent", OnyxLocalization.translate("hermes-agent", french))
        assertEquals("http://10.0.2.2:8890", OnyxLocalization.translate("http://10.0.2.2:8890", french))
    }

    @Test
    fun `les deux dictionnaires ne se contredisent pas sur les libelles partages`() {
        // « Skills » et « MCP » viennent d'ailleurs dans l'app : les deux écrans doivent lire la
        // **même** traduction, sinon le sélecteur et la liste ne diraient pas la même chose.
        assertEquals("Compétences", OnyxLocalization.translate("Skills", french))
        assertTrue(onyxCapabilitiesFrench.isNotEmpty())
        assertTrue(onyxInsightsFrench.isNotEmpty())
        assertTrue(
            "aucune valeur vide",
            (onyxCapabilitiesFrench.values + onyxInsightsFrench.values).none { it.isBlank() },
        )
    }

    @Test
    fun `les identifiants techniques ne sont pas dans le dictionnaire`() {
        // Un identifiant traduit serait un identifiant cassé : l'agent ne trouverait plus son outil.
        for (key in onyxCapabilitiesFrench.keys + onyxInsightsFrench.keys) {
            assertFalse("un identifiant en clé de dictionnaire : $key", key.contains("_") && key == key.lowercase())
        }
    }
}
