package com.onyx.app.ui.bots

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Supprimer le bot » demande confirmation sans faire défiler la page.**
 *
 * Relevé sur l'appareil : un appui sur « Supprimer le bot » renvoyait la vue tout en bas et la
 * question s'affichait sous la ligne de flottaison — « je ne vois pas les options, je suis obligé de
 * le faire manuellement ». La confirmation était un **bloc ajouté à la fin du `Column` défilant** :
 * elle poussait le contenu, tombait hors de l'écran, et le geste de l'ouvrir déplaçait la vue au
 * lieu de montrer la question.
 *
 * Elle est maintenant **la boîte de la maison** ([com.onyx.app.ui.design.OnyxConfirm]) : un panneau
 * centré, au-dessus de l'écran, entièrement visible quelle que soit la longueur de la page — et la
 * page ne bouge plus. Ce verrou tient les deux moitiés : la boîte est là, le bloc en ligne n'y est
 * plus.
 */
class BotDeleteConfirmTest {

    private val editor = File("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt").readText()

    @Test
    fun `la suppression s ouvre dans la boite de confirmation, pas au bas de la page`() {
        assertTrue(
            "la boîte de la maison est utilisée",
            editor.contains("import com.onyx.app.ui.design.OnyxConfirm"),
        )
        assertTrue(
            "la question nomme le bot",
            editor.contains("""title = "Delete ${'$'}{bot?.label(mainName) ?: "this bot"}?","""),
        )
        assertTrue(
            "les deux issues sont là",
            editor.contains("destructiveLabel = OnyxLocalization[\"Delete\"],") &&
                editor.contains("cancelLabel = OnyxLocalization[\"Cancel\"],") &&
                editor.contains("onDestructive = {") &&
                editor.contains("onCancel = { confirmingDelete = false },"),
        )
    }

    @Test
    fun `plus aucune confirmation en ligne au bas du formulaire`() {
        // Les deux rangées de l'ancien bloc : « Delete …? » puis « Cancel », ajoutées à la suite du
        // `Column`. Elles ne doivent pas revenir — c'est exactement le défaut signalé.
        assertFalse(
            "l'ancienne rangée « Annuler » du bloc en ligne est partie",
            editor.contains("""BasicText("Cancel", style = socleBodyStyle(theme).copy(color = theme.secondaryText))"""),
        )
        assertFalse(
            "et la question ne se dessine plus comme une rangée du formulaire",
            editor.contains("""IdentityRow(onClick = { confirmingDelete = false })"""),
        )
    }

    @Test
    fun `les deux libelles de la boite sont en francais`() {
        assertEquals("Supprimer", OnyxLocalization.translate("Delete", Locale.FRANCE))
        assertEquals("Annuler", OnyxLocalization.translate("Cancel", Locale.FRANCE))
    }
}
