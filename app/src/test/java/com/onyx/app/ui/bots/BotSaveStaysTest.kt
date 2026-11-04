package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Enregistrer » enregistre, et ne change pas de page.**
 *
 * Relevé sur l'appareil : l'action `Enregistrer`, en haut à droite de l'identité d'un bot, renvoyait
 * sur la **liste des bots** — lue par le propriétaire comme « la page de création de bot ». Le
 * câblage disait pourtant `onSave` : c'est le parent qui ajoutait `navTo(Route.BOTS.name)` après
 * l'écriture du profil, sans que rien ne le demande.
 *
 * Ce verrou tient les trois morceaux : le parent n'écrit plus de navigation dans `onSave` (alors
 * qu'il en garde une à la **création**, qui, elle, doit sortir), l'écran confirme l'enregistrement
 * par un mot bref, et ce mot est traduit.
 */
class BotSaveStaysTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val editor = File("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt").readText()

    /** Le corps du `onSave` de l'éditeur, du paramètre jusqu'au `onCreate` suivant. */
    private val saveBlock: String =
        app.substringAfter("onSave = { title, about, model, shown ->")
            .substringBefore("onCreate = { title, about, soul, shown, faceCode ->")

    /** Les appels de navigation réels — pas les commentaires qui les citent. */
    private fun navigationsIn(block: String): List<String> =
        block.lines().map { it.trim() }.filter { it.startsWith("navTo(") }

    @Test
    fun `enregistrer ne navigue plus vers la liste des bots`() {
        assertTrue("le bloc onSave est trouvé", saveBlock.isNotBlank())
        val calls = navigationsIn(saveBlock)
        assertTrue("onSave ne doit plus sortir de la page, il appelait : $calls", calls.isEmpty())
        // La création, elle, continue de sortir : c'est son rôle.
        val createBlock = app.substringAfter("onCreate = { title, about, soul, shown, faceCode ->")
            .substringBefore("onDelete = {")
        assertTrue(
            "la création ramène toujours à la liste",
            navigationsIn(createBlock).any { it.contains("Route.BOTS.name") },
        )
    }

    @Test
    fun `l ecran confirme l enregistrement par un mot bref`() {
        assertTrue(
            "l'action d'enregistrement pose le retour",
            editor.contains("onSave(title, about, bot?.model, shown)") &&
                editor.contains("savedFlash = true"),
        )
        assertTrue(
            "le mot s'affiche près de l'action",
            editor.contains("text = \"Saved\","),
        )
        assertTrue(
            "et il s'efface tout seul",
            editor.contains("kotlinx.coroutines.delay(1_600)"),
        )
    }

    @Test
    fun `le mot est traduit`() {
        assertEquals("Enregistré", OnyxLocalization.translate("Saved", Locale.FRANCE))
        assertEquals("Enregistrer", OnyxLocalization.translate("Save", Locale.FRANCE))
    }
}
