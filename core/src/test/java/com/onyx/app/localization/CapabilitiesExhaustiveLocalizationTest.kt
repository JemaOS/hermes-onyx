package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mots du **complément** de l'écran « Capacités » : le formulaire d'un serveur MCP, sa fiche,
 * ses refus, les comptes des trois familles et l'état vide de MCP.
 *
 * Tous doivent sortir en français ; les **identifiants** du serveur (le nom d'un serveur, une
 * adresse, un nom d'outil) n'entrent pas dans le dictionnaire — l'écran les rend verbatim.
 */
class CapabilitiesExhaustiveLocalizationTest {

    private val french = Locale.FRANCE

    @Test
    fun `le formulaire d'ajout d'un serveur MCP parle francais`() {
        val expected = linkedMapOf(
            "Add an MCP source" to "Ajouter une source MCP",
            "Add the server" to "Ajouter le serveur",
            "Adding…" to "Ajout…",
            "Command" to "Commande",
            "Arguments" to "Arguments",
            "Environment variables" to "Variables d’environnement",
            "One per line, like KEY=value." to "Une par ligne, sous la forme CLE=valeur.",
            "Authentication" to "Authentification",
            "Bearer token" to "Jeton Bearer",
            "A local command, or a server over the network." to
                "Une commande locale, ou un serveur par le réseau.",
            "Give the server a name." to "Donnez un nom au serveur.",
            "Give the server's address." to "Saisissez l’adresse du serveur.",
            "Give the command that starts the server." to "Saisissez la commande qui lance le serveur.",
            "Each environment variable needs a name and a value, like KEY=value." to
                "Chaque variable d’environnement a besoin d’un nom et d’une valeur, sous la forme CLE=valeur.",
            "A token is required for header authentication." to
                "Un jeton est nécessaire pour l’authentification par en-tête.",
            "Give one transport only: an address, or a command — not both." to
                "Un seul transport : une adresse, ou une commande — pas les deux.",
            "Arguments belong to a command, not to an address." to
                "Les arguments vont avec une commande, pas avec une adresse.",
            "Environment variables belong to a command, not to an address." to
                "Les variables d’environnement vont avec une commande, pas avec une adresse.",
            "A token goes with header authentication only." to
                "Un jeton ne s’utilise qu’avec l’authentification par en-tête.",
            "A command-launched server has no HTTP authentication." to
                "Un serveur lancé par une commande n’a pas d’authentification HTTP.",
            "The server needs every required field. Check the form and send again." to
                "Le serveur attend tous les champs obligatoires. Vérifiez le formulaire et renvoyez.",
        )
        for ((english, attendu) in expected) {
            assertEquals(attendu, OnyxLocalization.translate(english, french))
        }
    }

    @Test
    fun `la fiche d'un serveur et son etat vide parlent francais`() {
        val expected = linkedMapOf(
            "Over the network" to "Par le réseau",
            "Local command" to "Commande locale",
            "Transport unknown" to "Transport inconnu",
            "Sign-in (OAuth)" to "Connexion (OAuth)",
            "Header token" to "Jeton d’en-tête",
            "Tools this server exposes" to "Outils exposés par ce serveur",
            "Test the server to list its tools." to "Testez le serveur pour lister ses outils.",
            "Test again" to "Tester à nouveau",
            "Back to the servers" to "Retour aux serveurs",
            "Delete this server?" to "Supprimer ce serveur ?",
            "Nothing here yet. Use “Add an MCP source” above." to
                "Rien ici pour l’instant : utilisez « Ajouter une source MCP » ci-dessus.",
            "No tools were listed." to "Aucun outil n’a été listé.",
        )
        for ((english, attendu) in expected) {
            assertEquals(attendu, OnyxLocalization.translate(english, french))
        }
    }

    @Test
    fun `les trois familles, leurs outils et leurs competences parlent francais`() {
        val expected = linkedMapOf(
            "Expanded" to "Déplié",
            "Collapsed" to "Plié",
            "Reading…" to "Lecture…",
            "Category" to "Famille",
            "Show tools" to "Afficher les outils",
            "Hide tools" to "Masquer les outils",
            "Tools in this toolset" to "Outils de ce jeu d’outils",
            "No tools listed for this toolset." to "Aucun outil listé pour ce jeu d’outils.",
            "Available" to "Disponible",
            "Built-in" to "Intégrée",
            "Trusted" to "Vérifiée",
            "Community" to "Communauté",
            "Couldn't install it" to "Impossible de l’installer",
            "Couldn't remove it" to "Impossible de le retirer",
            "Couldn't add the server" to "Impossible d’ajouter le serveur",
            "Couldn't remove the server" to "Impossible de retirer le serveur",
            "This Hermes does not serve the skills route." to "Ce Hermes ne sert pas la route des compétences.",
            "This Hermes does not serve the toolsets route." to
                "Ce Hermes ne sert pas la route des jeux d’outils.",
            "This Hermes does not serve the MCP servers route." to
                "Ce Hermes ne sert pas la route des serveurs MCP.",
            "Prompts" to "Invites",
            "Resources" to "Ressources",
            "Skills the agent can read when a request matches." to
                "Les compétences que l’agent peut lire quand une demande y correspond.",
        )
        for ((english, attendu) in expected) {
            assertEquals(attendu, OnyxLocalization.translate(english, french))
        }
    }

    @Test
    fun `les gabarits a nombre des trois familles se composent en francais`() {
        assertEquals("24 compétences", OnyxLocalization.format("%d skills", 24, locale = french))
        assertEquals("1 compétence", OnyxLocalization.format("%d skill", 1, locale = french))
        assertEquals("3 jeux d’outils", OnyxLocalization.format("%d toolsets", 3, locale = french))
        assertEquals("1 jeu d’outils", OnyxLocalization.format("%d toolset", 1, locale = french))
        assertEquals("2 serveurs MCP", OnyxLocalization.format("%d MCP servers", 2, locale = french))
        assertEquals("1 serveur MCP", OnyxLocalization.format("%d MCP server", 1, locale = french))
        assertEquals("3 sur 24", OnyxLocalization.format("%d of %d", 3, 24, locale = french))
        assertEquals("12 compétences allumées", OnyxLocalization.format("%d skills on", 12, locale = french))
        assertEquals("1 compétence allumée", OnyxLocalization.format("%d skill on", 1, locale = french))
        assertEquals("4 jeux d’outils allumés", OnyxLocalization.format("%d toolsets on", 4, locale = french))
        assertEquals("1 jeu d’outils allumé", OnyxLocalization.format("%d toolset on", 1, locale = french))
        assertEquals("1 serveurs actifs", OnyxLocalization.format("%d MCP servers on", 1, locale = french))
        assertEquals("1 serveur actif", OnyxLocalization.format("%d MCP server on", 1, locale = french))
        assertEquals("7 outils", OnyxLocalization.format("%d tools", 7, locale = french))
        assertEquals("1 outil", OnyxLocalization.format("%d tool", 1, locale = french))
        assertEquals("Connecté · 1 outil", OnyxLocalization.format("Connected · %d tool", 1, locale = french))
        assertEquals("Rechercher les 5 jeux d’outils", OnyxLocalization.format("Search %d toolsets", 5, locale = french))
        assertEquals(
            "Rechercher les 2 serveurs MCP",
            OnyxLocalization.format("Search %d MCP servers", 2, locale = french),
        )
        assertEquals("312 ms", OnyxLocalization.format("Took %1\$s ms", 312, locale = french))
        assertEquals("Ouvrir files", OnyxLocalization.format("Open %1\$s", "files", locale = french))
        assertEquals(
            "Un serveur nommé probe existe déjà.",
            OnyxLocalization.format("A server named %1\$s already exists.", "probe", locale = french),
        )
        assertEquals(
            "probe vient du greffon kanban ; il ne se règle pas ici.",
            OnyxLocalization.format(
                "%1\$s is supplied by the plugin %2\$s; it can't be changed here.",
                "probe",
                "kanban",
                locale = french,
            ),
        )
        assertEquals(
            "Cela retire probe du fichier de configuration d’Hermes. C’est irréversible.",
            OnyxLocalization.format(
                "This removes %1\$s from the Hermes config file. It can't be undone.",
                "probe",
                locale = french,
            ),
        )
    }

    @Test
    fun `en anglais les memes cles restent inchangees`() {
        val keys = listOf(
            "Add an MCP source",
            "Give the server a name.",
            "Over the network",
            "Delete this server?",
            "Expanded",
            "Available",
            "Nothing here yet. Use “Add an MCP source” above.",
        )
        keys.forEach { key ->
            assertEquals("Repli anglais de « $key »", key, OnyxLocalization.translate(key, Locale.ENGLISH))
        }
    }

    @Test
    fun `les identifiants techniques ne sont pas dans le dictionnaire`() {
        // Un identifiant traduit serait un identifiant cassé : l'agent ne trouverait plus son outil,
        // ni le serveur son adresse.
        for (key in onyxCapabilitiesFrench.keys) {
            assertFalse("un identifiant en clé de dictionnaire : $key", key.contains("_") && key == key.lowercase())
        }
        assertTrue("le dictionnaire de l'écran est vide", onyxCapabilitiesFrench.size > 40)
        assertTrue("une valeur vide", onyxCapabilitiesFrench.values.none { it.isBlank() })
    }
}
