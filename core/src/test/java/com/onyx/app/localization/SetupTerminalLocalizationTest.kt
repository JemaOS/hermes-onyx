package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupTerminalLocalizationTest {
    @Test
    fun `le scanner et la confirmation parlent francais`() {
        val cases = linkedMapOf(
            "Point the camera at a Onyx setup code." to "Pointez la caméra vers un code de configuration Onyx.",
            "That isn't a Onyx setup code." to "Ce n’est pas un code de configuration Onyx.",
            "No camera access" to "Accès à la caméra refusé",
            "Open Settings" to "Ouvrir les réglages",
            "Setup code" to "Code de configuration",
            "Testing the connection…" to "Test de la connexion…",
            "Add server" to "Ajouter le serveur",
            "Remove this server" to "Supprimer ce serveur",
            "Test again" to "Tester à nouveau",
            "Edit connection…" to "Modifier la connexion…",
            "Onyx will use this" to "Onyx utilisera cette connexion",
            "password included" to "mot de passe inclus",
            "no password" to "aucun mot de passe",
            "no key" to "aucune clé",
            "key included" to "clé incluse",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `le terminal et ses erreurs parlent francais`() {
        val cases = linkedMapOf(
            "Trust this server?" to "Faire confiance à ce serveur ?",
            "Trust" to "Faire confiance",
            "Reconnect" to "Reconnecter",
            "Copy This Phone's Key" to "Copier la clé de ce téléphone",
            "Edit Account" to "Modifier le compte",
            "Connect" to "Connecter",
            "Open Terminal" to "Ouvrir le terminal",
            "This phone's key" to "Clé de ce téléphone",
            "Server's key" to "Clé du serveur",
            "Copied" to "Copiée",
            "Make a new key?" to "Créer une nouvelle clé ?",
            "Make a New Key for This Phone" to "Créer une nouvelle clé pour ce téléphone",
            "Not connected: the server's key wasn't trusted." to "Non connecté : la clé du serveur n’a pas été approuvée.",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
        assertEquals(
            "Connexion à hermes.example…",
            OnyxLocalization.translate("Connecting to hermes.example…", Locale.FRANCE),
        )
        assertEquals(
            "Pour vous connecter, renseignez le nom d’hôte du serveur.",
            OnyxLocalization.translate("To connect, fill in the server's host name.", Locale.FRANCE),
        )
    }
}
