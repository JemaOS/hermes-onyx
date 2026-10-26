package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Réglages → Outils: les titres et les descriptions que le serveur envoie doivent sortir en
 * français, sans toucher aux identifiants d'outils.
 *
 * Les chaînes anglaises de ce test sont celles de la passerelle (`api/tools/toolsets`), copiées
 * telles quelles: c'est ce que l'écran reçoit, donc c'est ce que le dictionnaire doit couvrir.
 */
class ToolsPageLocalizationTest {

    private companion object {
        /** Les libellés de la page, avec le français attendu. */
        val titles = linkedMapOf(
            // Un sigle et deux noms propres restent tels quels.
            "A2A" to "A2A",
            "Kanban" to "Kanban",
            "Yuanbao" to "Yuanbao",
            "Web Search & Scraping" to "Recherche et lecture web",
            "Browser Automation" to "Automatisation du navigateur",
            "Terminal & Processes" to "Terminal et processus",
            "File Operations" to "Opérations sur les fichiers",
            "Code Execution" to "Exécution de code",
            "Vision / Image Analysis" to "Analyse d’images",
            "Video Analysis" to "Analyse de vidéos",
            "Image Generation" to "Création d’images",
            "Video Generation" to "Création de vidéos",
            "X (Twitter) Search" to "Recherche sur X (Twitter)",
            "Text-to-Speech" to "Synthèse vocale",
            "Speech-to-Text" to "Transcription vocale",
            "Skills" to "Compétences",
            "Task Planning" to "Planification des tâches",
            "Memory" to "Mémoire",
            "Context Engine" to "Moteur de contexte",
            "Session Search" to "Recherche dans les conversations",
            "Connections" to "Connexions",
            "Clarifying Questions" to "Questions de clarification",
            "Task Delegation" to "Délégation de tâches",
            "Cron Jobs" to "Tâches planifiées",
            "Discord (read/participate)" to "Discord (lire et participer)",
            "Discord Server Admin" to "Administration d’un serveur Discord",
            "Computer Use (macOS/Windows/Linux)" to "Utilisation de l’ordinateur (macOS/Windows/Linux)",
        )

        /** Les descriptions, telles que le serveur les envoie. */
        val descriptions = linkedMapOf(
            // Des identifiants d'API seuls: rien à traduire.
            "web_search, web_extract" to "web_search, web_extract",
            "execute_code" to "execute_code",
            "vision_analyze" to "vision_analyze",
            "image_generate" to "image_generate",
            "text_to_speech" to "text_to_speech",
            "todo_list" to "todo_list",
            "clarify" to "clarify",
            "delegate_task" to "delegate_task",
            // Les mots, eux, sont traduits.
            "navigate, click, type, scroll" to "naviguer, cliquer, taper, défiler",
            "terminal, process" to "terminal, processus",
            "read, write, patch, search" to "lire, écrire, corriger, chercher",
            "list, view, manage" to "lister, voir, gérer",
            "video_analyze (requires video-capable model)" to
                "video_analyze (nécessite un modèle capable de lire la vidéo)",
            "video_generate (text/image/reference)" to "video_generate (texte/image/référence)",
            "x_search (requires xAI OAuth or XAI_API_KEY)" to
                "x_search (nécessite xAI OAuth ou XAI_API_KEY)",
            "voice transcription (gateway voice messages + voice mode)" to
                "transcription vocale (messages vocaux de la passerelle + mode vocal)",
            "opt-in task board tools for this platform" to
                "outils de tableau de tâches, à activer pour cette plateforme",
            "persistent memory across sessions" to "mémoire persistante d’une session à l’autre",
            "runtime tools from the active context engine" to
                "outils d’exécution du moteur de contexte actif",
            "search past conversations" to "chercher dans les conversations passées",
            "remote connector tools and account authorization" to
                "outils de connexion à distance et autorisation de compte",
            "create/list/update/pause/resume/run, with optional attached skills" to
                "créer, lister, modifier, mettre en pause, reprendre, exécuter — avec des compétences jointes en option",
            "fetch messages, search members, create thread" to
                "lire les messages, chercher des membres, créer un fil",
            "list channels/roles, pin, assign roles" to
                "lister les salons et les rôles, épingler, attribuer des rôles",
            "group info, member queries, DM" to "infos du groupe, recherche de membres, messages privés",
            "background desktop control via cua-driver" to
                "contrôle du bureau en arrière-plan via cua-driver",
        )

        val footers = linkedMapOf(
            "Couldn't reach the gateway" to "Passerelle injoignable",
        )

        /**
         * Les mots qui ne peuvent être que de l'anglais.
         *
         * On ignore les jetons d'identifiant (ceux qui portent un `_`), comme `web_search`: ce
         * sont des noms d'API, pas de l'anglais à traduire. Le test reste volontairement étroit:
         * un mot français identique à l'anglais (« terminal », « agent », « messages ») n'y est
         * pas, seul ce qui n'a aucune chance d'être du français y figure.
         */
        val englishWords = setOf(
            "the", "and", "with", "for", "from", "of", "this", "that", "requires", "tools", "tool",
            "platform", "settings", "switches", "gateway", "browser", "default", "optional",
            "attached", "scraping", "automation", "operations", "generation", "planning",
            "delegation", "connector", "authorization", "channels", "desktop", "survive",
        )

        /** Les mots anglais restés dans un texte français, hors identifiants. */
        fun englishLeftovers(text: String): List<String> =
            Regex("[A-Za-z]{2,}").findAll(text)
                .map { it.value }
                .filter { !it.contains('_') }
                .filter { it.lowercase(Locale.ROOT) in englishWords }
                .toList()
    }

    @Test
    fun `les titres de la page Outils sont en francais`() {
        titles.forEach { (english, french) ->
            assertEquals("Titre « $english »", french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals("Repli anglais de « $english »", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `les descriptions de la page Outils sont en francais`() {
        descriptions.forEach { (english, french) ->
            assertEquals("Description « $english »", french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals("Repli anglais de « $english »", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
        footers.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
        }
    }

    @Test
    fun `aucune description de la page ne garde de mot anglais en francais`() {
        (titles.values + descriptions.values + footers.values).forEach { french ->
            val leftovers = englishLeftovers(french)
            assertTrue("Mots anglais restés dans « $french » : $leftovers", leftovers.isEmpty())
        }
    }

    @Test
    fun `un identifiant d outil traverse la page sans etre traduit`() {
        val identifiers = listOf("a2a_call", "a2a_discover", "browser_cdp", "execute_code", "clarify", "computer_use")
        identifiers.forEach { id ->
            assertEquals(id, OnyxLocalization.translate(id, Locale.FRANCE))
            assertEquals("$id · $id", OnyxLocalization.translateParagraphs("$id · $id", Locale.FRANCE))
        }
    }

    @Test
    fun `une description en plusieurs paragraphes est traduite paragraphe par paragraphe`() {
        // La fiche A2A telle que la passerelle l'envoie: des paragraphes, un retour à la ligne final.
        val english = "A2A (Agent-to-Agent) protocol v1.0 support for Hermes Agent — both directions of the open " +
            "Linux Foundation standard for inter-agent communication.\n" +
            "Pure stdlib transport (http.server + urllib) — no a2a-sdk dependency required.\n"
        val french = OnyxLocalization.translateParagraphs(english, Locale.FRANCE)

        assertEquals("le nombre de paragraphes ne change pas", 3, french.split('\n').size)
        assertTrue(french.startsWith("Prise en charge du protocole A2A (Agent-to-Agent) v1.0"))
        assertTrue(french.contains("Transport en bibliothèque standard uniquement"))
        assertTrue("aucun mot anglais resté : $french", englishLeftovers(french).isEmpty())
    }

    @Test
    fun `les cles deja traduites gardent leur traduction`() {
        // Le piège déjà rencontré: une entrée en double écrasait « Horaires » par « Programmes ».
        assertEquals("Horaires", OnyxLocalization.translate("Schedules", Locale.FRANCE))
        assertEquals("Nouvel horaire", OnyxLocalization.translate("New Schedule", Locale.FRANCE))
        assertEquals("Recherche web", OnyxLocalization.translate("Web search", Locale.FRANCE))
        assertEquals("Moteur de contexte", OnyxLocalization.translate("Context engine", Locale.FRANCE))
        assertEquals("Réglages", OnyxLocalization.translate("Settings", Locale.FRANCE))
        assertEquals("Activés · 28", OnyxLocalization.translate("Enabled · 28", Locale.FRANCE))
        assertEquals("Outils", OnyxLocalization.translate("Tools", Locale.FRANCE))
    }

    @Test
    fun `le dictionnaire de la page Outils n ecrase aucune entree existante`() {
        val collisions = onyxToolsPageFrench.keys.filter { it in onyxToolsFrench }
        assertTrue("Clés déjà présentes dans onyxToolsFrench: $collisions", collisions.isEmpty())
        onyxToolsPageFrench.forEach { (key, value) ->
            assertFalse("Traduction vide pour « $key »", value.isBlank())
            assertTrue("La traduction de « $key » ne doit pas rester l'anglais", value != key)
        }
    }
}
