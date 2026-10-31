package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le câblage des sorties de l'écran de configuration.
 *
 * Le propriétaire signale « Ignorer ou Terminé ne marchent pas » : les deux boutons doivent agir,
 * jamais rester inertes. Ce test lit le câblage réel (les deux arbres), comme le font les autres
 * tests de parité de ce dépôt.
 */
class SetupExitWiringTest {
    private val setup = File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt").readText()
    private val anatomy = File("src/main/java/com/onyx/app/ui/setup/SetupAnatomy.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    @Test
    fun `l en-tete passe bien par les deux sorties`() {
        assertTrue(setup.contains("onBack = onSkip"))
        assertTrue(setup.contains("onAction = { finish() }"))
        // Le bouton « Done » ne va plus droit au bout : il passe par la validation.
        assertFalse(setup.contains("onAction = onDone,"))
        assertTrue(setup.contains("val refusal = doneRefusal(config, testing, probe)"))
        assertTrue(setup.contains("if (refusal == null) onDone()"))
    }

    @Test
    fun `la raison d un refus s affiche sous la barre`() {
        assertTrue(setup.contains("var reason by remember { mutableStateOf<String?>(null) }"))
        assertTrue(setup.contains("reason?.let { message ->"))
        assertTrue(setup.contains("background(theme.error.copy(alpha = 0.12f))"))
    }

    @Test
    fun `le test de connexion ne reste pas inerte pendant qu il tourne`() {
        // La pilule « Tester » vit **dans** le champ (SetupAnatomy.kt) : estompée et sans appui
        // quand elle ne peut rien faire, au lieu d'avaler l'appui.
        assertTrue(anatomy.contains(".alpha(if (testing) 0.35f else 1f)"))
        assertTrue(anatomy.contains("onyxTappable(enabled = !testing, onClick = onTest)"))
        assertFalse(setup.contains("if (testing) return@OnyxRow"))
        assertFalse(anatomy.contains("if (testing) return@OnyxRow"))
    }

    @Test
    fun `le premier lancement passe par Ignorer et ne se reaffiche plus`() {
        // Le drapeau qui manquait : la porte de sortie de l'écran.
        assertTrue(app.contains("val setupDone by settingsViewModel.setupDone.collectAsStateWithLifecycle()"))
        assertTrue(app.contains("val firstRunSetup = (!state.server.isUsable && !setupDone) || forceFirstRunSetup"))
        assertTrue(
            "les maquettes (démo, salle, chaîne vocale rejouée) passent outre l'écran de configuration",
            app.contains("if (firstRunSetup && !voiceDemo && !groupVoiceDemoOn && voiceScenario == null) {"),
        )
        // « Ignorer » a son propre chemin, distinct de « Terminé ».
        assertTrue(app.contains("onSkip = {"))
        assertTrue(app.contains("if (skipSavesDraft(setupDraft)) {"))
        assertTrue(app.contains("serverAfterSkip(state.server, serverList)?.let { server ->"))
        assertTrue(app.contains("settingsViewModel.setActive(server.id)"))
        // Les deux sorties marquent la configuration comme traversée.
        assertTrue(app.split("settingsViewModel.setSetupDone(true)").size >= 3)
    }

    @Test
    fun `un appareil sans adresse reste utilisable`() {
        // Le fil dit ce qui manque au lieu de laisser un écran vide, et ouvre les Réglages.
        val chat = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("if (!state.server.isUsable) {"))
        assertTrue(chat.contains("com.onyx.app.ui.common.NoServerCard(onOpenSettings = onOpenSettingsFromError)"))
        val card = File("src/main/java/com/onyx/app/ui/common/ConnectionProblemCard.kt").readText()
        assertTrue(card.contains("fun NoServerCard("))
        assertTrue(card.contains("text = \"No server set up yet\""))
        assertTrue(card.contains("text = \"Open Settings\""))
    }
}
