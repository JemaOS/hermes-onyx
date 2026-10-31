package com.onyx.app.localization

import com.onyx.app.ui.localization.resolveOnyxText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnyxLocalizationTest {
    @Test
    fun `un telephone francais obtient les libelles principaux en francais`() {
        assertEquals("Réglages", OnyxLocalization.translate("Settings", Locale.FRANCE))
        assertEquals("Terminé", OnyxLocalization.translate("Done", Locale.CANADA_FRENCH))
        assertEquals("Configurer Onyx", OnyxLocalization.translate("Set up Onyx", Locale.forLanguageTag("fr-BE")))
    }

    @Test
    fun `les libelles de la poignee de la barre d onglets ont quitte le dictionnaire`() {
        // La poignée à chevron a été retirée de l'écran sur l'ordre du propriétaire : ses deux
        // libellés n'ont plus de contrôle à nommer. Ils quittent donc le dictionnaire, plutôt que
        // d'y rester comme des traductions qu'aucun bouton ne porte — une clé morte est un chemin
        // qu'on rebrancherait par distraction.
        //
        // Ce qui se vérifie ici est justement **l'absence de traduction** : les clés se rendent
        // verbatim, exactement comme un contenu inconnu.
        assertEquals("Hide the tab bar", OnyxLocalization.translate("Hide the tab bar", Locale.FRANCE))
        assertEquals("Show the tab bar", OnyxLocalization.translate("Show the tab bar", Locale.FRANCE))
    }

    @Test
    fun `le chemin verbatim preserve un nom ou un message identique a une cle`() {
        assertEquals("Réglages", resolveOnyxText("Settings", localize = true, locale = Locale.FRANCE))
        assertEquals("Settings", resolveOnyxText("Settings", localize = false, locale = Locale.FRANCE))
        assertEquals("Settings", resolveOnyxText("Settings", localize = false, locale = Locale.ENGLISH))
    }

    @Test
    fun `la langue par defaut est lue a chaque appel`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("Annuler", OnyxLocalization["Cancel"])
            Locale.setDefault(Locale.US)
            assertEquals("Cancel", OnyxLocalization["Cancel"])
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `anglais puis cle sont les replis et le contenu inconnu reste verbatim`() {
        assertEquals("API", OnyxLocalization.translate("API", Locale.FRANCE))
        assertEquals("Settings", OnyxLocalization.translate("Settings", Locale.ENGLISH))
        val serverMessage = "Server message that is not a localization key"
        assertEquals(serverMessage, OnyxLocalization.translate(serverMessage, Locale.FRANCE))
        assertEquals(serverMessage, OnyxLocalization.translate(serverMessage, Locale.JAPAN))
    }

    @Test
    fun `les libelles dynamiques conservent les noms et les nombres`() {
        assertEquals("3 éléments sélectionnés", OnyxLocalization.translate("3 selected", Locale.FRANCE))
        assertEquals("2 sur 4 choisis", OnyxLocalization.translate("2 of 4 chosen", Locale.FRANCE))
        assertEquals(
            "Horaires de Ada Lovelace",
            OnyxLocalization.translate("Ada Lovelace's Schedules", Locale.FRANCE),
        )
        assertEquals("3 selected", OnyxLocalization.translate("3 selected", Locale.ENGLISH))
    }

    @Test
    fun `les compteurs et etats dynamiques sont localises sans toucher aux noms`() {
        val cases = linkedMapOf(
            "1 earlier message" to "1 message précédent",
            "12 earlier messages" to "12 messages précédents",
            "1 message waiting" to "1 message en attente",
            "4 messages waiting" to "4 messages en attente",
            "Thought for 6 s" to "Réflexion pendant 6 s",
            "2 of 4 answering" to "2 sur 4 en train de répondre",
            "Turn 3" to "Tour 3",
            "Ask Ada Lovelace" to "Demander à Ada Lovelace",
            "Ada Lovelace is speaking" to "Ada Lovelace parle",
            "Ada Lovelace is writing" to "Ada Lovelace écrit",
            "Same as Ada Lovelace" to "Identique à Ada Lovelace",
            "The tools Ada Lovelace has." to "Les outils de Ada Lovelace.",
            "Muted until tomorrow, 14:00" to "Silencieux jusqu’à demain, 14:00",
            "Deleting Settings…" to "Suppression de Settings…",
            "Uploading Settings…" to "Téléversement de Settings…",
            "Running · gpt-5 · task 2 of 4" to "En cours · gpt-5 · tâche 2 sur 4",
            "Photo 2 of 4" to "Photo 2 sur 4",
            "The recogniser stopped (code 5)." to "Le moteur de reconnaissance s’est arrêté (code 5).",
            "2 of 4 chosen · Turn 3" to "2 sur 4 choisis · Tour 3",
            "Working · 2 tools" to "En cours : 2 outils",
            "3 conversations · 1 pinned · 2 archived" to "3 conversations · 1 épinglée · 2 archivées",
            "Ada Lovelace · Replying…" to "Ada Lovelace · Réponse en cours…",
            "Ada Lovelace — Personality" to "Ada Lovelace — Personnalité",
            "Listening for 12 s" to "Écoute depuis 12 s",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `les libellés français existants ont un repli anglais sans changer le français`() {
        assertEquals("round", OnyxLocalization.translate("rond", Locale.ENGLISH))
        assertEquals("rond", OnyxLocalization.translate("rond", Locale.FRANCE))
        assertEquals("line", OnyxLocalization.translate("en ligne", Locale.ENGLISH))
        assertEquals("en ligne", OnyxLocalization.translate("en ligne", Locale.FRANCE))
        assertEquals("squircle", OnyxLocalization.translate("carré arrondi", Locale.ENGLISH))
        assertEquals("carré arrondi", OnyxLocalization.translate("carré arrondi", Locale.FRANCE))
    }

    @Test
    fun `le formatage traduit le gabarit sans traduire ses arguments`() {
        assertEquals(
            "Impossible de lire les horaires de Ada Lovelace : Settings",
            OnyxLocalization.format(
                "Couldn't read %1\$s's schedules: %2\$s",
                "Ada Lovelace",
                "Settings",
                locale = Locale.FRANCE,
            ),
        )
        assertEquals(
            "Couldn't read Ada Lovelace's schedules: Settings",
            OnyxLocalization.format(
                "Couldn't read %1\$s's schedules: %2\$s",
                "Ada Lovelace",
                "Settings",
                locale = Locale.ENGLISH,
            ),
        )
    }

    @Test
    fun `aucune cle critique des ecrans principaux ne reste en anglais`() {
        val identicalInFrench = setOf("Bots", "Conversations")
        val missing = criticalFrench.keys.filter { key ->
            key !in identicalInFrench && OnyxLocalization.translate(key, Locale.FRANCE) == key
        }
        assertTrue("Clés critiques encore en anglais: $missing", missing.isEmpty())

        criticalFrench.forEach { (key, expected) ->
            assertEquals("Traduction française de « $key »", expected, OnyxLocalization.translate(key, Locale.FRANCE))
            assertEquals("Repli anglais de « $key »", key, OnyxLocalization.translate(key, Locale.ENGLISH))
        }
    }

    @Test
    fun `le titre de creation d une competence est localise`() {
        assertEquals("Nouvelle compétence", OnyxLocalization.translate("New skill", Locale.FRANCE))
        assertEquals("New skill", OnyxLocalization.translate("New skill", Locale.ENGLISH))
    }

    @Test
    fun `les libelles de la montre suivent aussi la langue`() {
        val cases = linkedMapOf(
            "Tap to talk" to "Touchez pour parler",
            "Read again" to "Réécouter",
            "Your server" to "Votre serveur",
            "READY" to "PRÊT",
            "LISTENING" to "ÉCOUTE",
            "THINKING" to "RÉFLEXION",
            "SPEAKING" to "LECTURE",
            "PROBLEM" to "PROBLÈME",
            "Ask Onyx" to "Demander à Onyx",
            "Tap and talk" to "Touchez et parlez",
            "The server sent nothing back." to "Le serveur n’a rien renvoyé.",
            "Something went wrong." to "Une erreur est survenue.",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `un salon ne se dit plus « discussion de groupe »`() {
        // L'objet a changé de nom — « salon », comme la référence l'appelle — et le dictionnaire
        // central le dit. Les **identifiants** du code (`GroupRoom`, `createGroup`, `Route.GROUP_CHAT`)
        // restent, eux : les renommer casserait la persistance des salles gardées ici.
        assertEquals("Salon", OnyxLocalization.translate("Group chat", Locale.FRANCE))
        assertEquals("Nouveau salon", OnyxLocalization.translate("New room", Locale.FRANCE))
        assertEquals("Salons", OnyxLocalization.translate("Rooms", Locale.FRANCE))
        assertEquals("Aucun salon pour le moment.", OnyxLocalization.translate("No rooms yet.", Locale.FRANCE))

        listOf(
            "Group chat", "New Group Chat", "New room", "Rooms", "Room name",
            "Talk to the group", "Pick two to six bots for the group.",
        ).forEach { key ->
            val french = OnyxLocalization.translate(key, Locale.FRANCE)
            assertFalse(
                "« $key » se dit encore en « discussion de groupe » ($french)",
                french.lowercase().contains("discussion de groupe"),
            )
        }
    }

    private companion object {
        /** Un échantillon transversal volontairement indépendant du dictionnaire de production. */
        val criticalFrench = linkedMapOf(
            // Setup et connexion.
            "Set up Onyx" to "Configurer Onyx",
            "Welcome to Onyx" to "Bienvenue dans Onyx",
            "Scan a setup code" to "Scanner un code de configuration",
            "Test connection" to "Tester la connexion",
            // Chat et actions.
            "Conversations" to "Conversations",
            "New conversation" to "Nouvelle conversation",
            "Find in conversation" to "Rechercher dans la conversation",
            "Jump to latest" to "Aller au dernier message",
            "Retry" to "Réessayer",
            "Dismiss" to "Ignorer",
            "Copy reply" to "Copier la réponse",
            "Read aloud" to "Lire à voix haute",
            // Conversations, bots et salons.
            "Bots" to "Bots",
            "Group chat" to "Salon",
            "No bots yet" to "Aucun bot pour le moment",
            "Ask them together." to "Posez-leur une question ensemble.",
            "New Group Chat" to "Nouveau salon",
            "Room name" to "Nom du salon",
            "Bots · pick 2 to 6" to "BOTS · EN CHOISIR DE 2 À 6",
            // Voix.
            "Voice" to "Voix",
            "Listening" to "À l’écoute",
            "Talk to the group" to "Parler au salon",
            "Stop listening" to "Arrêter l’écoute",
            "Stop reading" to "Arrêter la lecture",
            "Voice settings" to "Réglages vocaux",
            // Réglages et états.
            "Settings" to "Réglages",
            "Appearance" to "Apparence",
            "Return key sends" to "La touche Retour envoie",
            "Suggest follow-up questions" to "Suggérer des questions de suivi",
            "Loading from Onyx…" to "Chargement depuis Onyx…",
            "Try Again" to "Réessayer",
            // Horaires.
            "Schedules" to "Horaires",
            "New Schedule" to "Nouvel horaire",
            "Pick a Time Instead" to "Choisir plutôt une heure",
            "Last run" to "Dernière exécution",
            "No runs yet" to "Aucune exécution pour le moment",
            // Actions communes.
            "Cancel" to "Annuler",
            "Delete" to "Supprimer",
            "Save" to "Enregistrer",
            "Done" to "Terminé",
        )
    }
}
