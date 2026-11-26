package com.onyx.app.shortcuts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le Coffre de secrets a ses deux entrées, et une seule preuve.**
 *
 * Demandé depuis le téléphone : « le coffre doit pas être dans cette section, il doit être dans
 * Réglages à part et dans la liste des raccourcis ». Ce fichier tient les trois parties :
 *
 * 1. il **n'est plus** dessiné dans « Capacités » ;
 * 2. il a sa **propre** ligne dans les Réglages, dans sa propre section, conditionnée par la sonde ;
 * 3. il est une **tuile** du menu d'appui long sur l'icône, et cette tuile suit la même preuve —
 *    retirée par le lanceur quand le serveur ne sert pas le greffon.
 */
class VaultEntryTest {

    private val capabilities = File("src/main/java/com/onyx/app/ui/capabilities/CapabilitiesScreen.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val shortcuts = File("src/main/res/xml/shortcuts.xml").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()
    private val french = File("src/main/res/values-fr/strings.xml").readText()
    private val activity = File("src/main/java/com/onyx/app/MainActivity.kt").readText()
    private val inbox = File("src/main/java/com/onyx/app/intake/ShareInbox.kt").readText()

    // MARK: - 1. Plus dans « Capacités »

    @Test
    fun `Capacites ne porte plus le Coffre`() {
        assertFalse("plus de ligne « Secrets › »", capabilities.contains("Secrets ›"))
        assertFalse("plus de paramètre pour l'ouvrir", capabilities.contains("onOpenVault"))
        assertFalse("plus de sonde du coffre dans cet écran", capabilities.contains("ServerCapability.VAULT"))
    }

    // MARK: - 2. Réglages, à part

    @Test
    fun `les Reglages ont une section Secrets et sa ligne`() {
        assertTrue("la section a son en-tête", settings.contains("header = \"Secrets\","))
        assertTrue("la ligne à sa page", settings.contains("label = \"Secrets vault\","))
        assertTrue("et elle part de la page des Réglages", app.contains("onOpenVault = { navTo(Route.VAULT.name) },"))
        assertTrue("le volet la porte aussi", app.contains("onOpenVault = { panePush(Route.VAULT.name) },"))
    }

    @Test
    fun `la ligne et la tuile suivent la meme preuve`() {
        assertTrue("les Réglages sondent la capacité", settings.contains("ServerProbe(it)"))
        assertTrue("et retiennent la réponse", settings.contains("ServerCapability.VAULT"))
        assertTrue("la ligne dépend de cette réponse", settings.contains("onOpenVault != null && vaultReady"))
        assertTrue("la même preuve pilote la tuile", settings.contains("VaultShortcut.sync(context, ready)"))
    }

    // MARK: - 3. Le raccourci de l'icône

    @Test
    fun `le menu de l icone porte la tuile du Coffre`() {
        assertTrue(shortcuts.contains("android:shortcutId=\"vault\""))
        assertTrue(shortcuts.contains("android:action=\"com.onyx.app.action.VAULT\""))
        assertTrue(shortcuts.contains("android:icon=\"@drawable/ic_shortcut_vault\""))
        assertTrue(shortcuts.contains("@string/shortcut_vault_short"))
    }

    @Test
    fun `la tuile est dans la famille des autres`() {
        val tile = File("../core/src/main/res/drawable/ic_shortcut_vault.xml").readText()
        assertTrue(
            "carré à coins arrondis",
            tile.contains("M24,0 H84 A24,24 0 0 1 108,24 V84 A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z"),
        )
        assertTrue("le glyphe est blanc", tile.contains("#FFFFFFFF"))
        assertTrue("elle dit son disque de sûreté", tile.contains("disque de sûreté"))
    }

    @Test
    fun `la tuile se nomme en francais sur un telephone francais`() {
        assertTrue(strings.contains("<string name=\"shortcut_vault_short\">Secret vault</string>"))
        assertTrue(french.contains("<string name=\"shortcut_vault_short\">Coffre de secrets</string>"))
        assertTrue(french.contains("<string name=\"shortcut_vault_long\">Ouvrir le coffre de secrets</string>"))
    }

    @Test
    fun `la tuile ouvre le Coffre une seule fois`() {
        assertTrue("l'activité reconnaît l'action", activity.contains("ACTION_VAULT -> ShareInbox.requestVault()"))
        assertTrue("la demande est déposée", inbox.contains("fun requestVault()"))
        assertTrue("et se vide après lecture", inbox.contains("_vaultRequested.value = false"))
        assertTrue("l'UI l'ouvre", app.contains("vaultRequested -> {"))
        assertTrue("sur la page du Coffre", app.contains("navTo(Route.VAULT.name)"))

        // Le lanceur retire la tuile quand la capacité n'est pas prouvée : une entrée absente
        // plutôt qu'un tap mort.
        val shortcut = File("src/main/java/com/onyx/app/shortcuts/VaultShortcut.kt").readText()
        assertTrue("elle est retirée sans preuve", shortcut.contains("manager.disableShortcuts"))
        assertTrue("et remise avec", shortcut.contains("manager.enableShortcuts"))
    }
}
