package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Working » et « now » dans la liste des conversations.**
 *
 * Le défaut relevé sur la capture : sous le titre « coder », la seconde ligne disait « Working » et
 * l'heure « now » — en anglais, au milieu d'une liste française. La cause n'était pas le
 * dictionnaire (« Working » → « En cours » et « now » → « à l'instant » y étaient déjà) mais le
 * rendu : `BlocConversation` dessinait l'aperçu et l'heure `localize = false`, à juste titre pour
 * l'aperçu d'un message — du texte d'utilisateur —, et donc aussi pour l'état d'un agent.
 *
 * La rangée porte maintenant son **activité**, ce qui permet de distinguer les deux, et l'état d'un
 * agent au travail **bat** (« En cours. », « En cours.. », « En cours... »).
 */
class SidebarWorkingLabelTest {

    private val model = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt").readText()
    private val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()

    private fun conversation() = Conversation(
        id = "conv-1",
        title = "coder",
        createdAt = 0L,
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun `la rangee porte l activite signalee par le serveur`() {
        val working = sidebarRowOf(
            conversation = conversation(),
            kind = ActivityKind.WORKING,
            seenMark = null,
            now = 1_700_000_030.0,
            agentName = "Onyx",
        )
        assertEquals(ActivityKind.WORKING, working.activity)
        assertEquals("Working", working.subtitle)

        val idle = sidebarRowOf(
            conversation = conversation(),
            kind = null,
            seenMark = null,
            now = 1_700_000_030.0,
            agentName = "Onyx",
        )
        assertNull("au repos, aucune activité : l'aperçu reste du texte d'utilisateur", idle.activity)
    }

    @Test
    fun `l etat et l heure sont des chaines de l app, donc elles se traduisent`() {
        assertEquals("En cours", OnyxLocalization.translate("Working", Locale.FRANCE))
        assertEquals("Vous attend", OnyxLocalization.translate("Waiting for you", Locale.FRANCE))
        assertEquals("à l'instant", OnyxLocalization.translate("now", Locale.FRANCE))
        // Et les âges qui n'ont pas d'entrée restent tels quels : « 2h » se lit dans les deux langues.
        assertEquals("2h", OnyxLocalization.translate("2h", Locale.FRANCE))
    }

    @Test
    fun `l ecran branche bien la traduction et le battement`() {
        assertTrue(
            "l'aperçu d'un état se traduit",
            sidebar.contains("localizePreview = row.activity != null,"),
        )
        assertTrue(
            "et l'agent au travail fait battre ses points",
            sidebar.contains("workingPreview = row.activity == com.onyx.app.data.net.ActivityKind.WORKING,"),
        )
        assertTrue(
            "l'heure se traduit aussi",
            sidebar.contains("localizeMeta = true,"),
        )
        // Le modèle nomme l'état par une clé, pas par une phrase composée : la traduction la trouve.
        assertTrue(
            "le sous-titre d'un agent au travail reste une clé",
            model.contains("ActivityKind.WORKING -> \"Working\""),
        )
    }
}
