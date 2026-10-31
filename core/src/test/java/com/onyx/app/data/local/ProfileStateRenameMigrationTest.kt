package com.onyx.app.data.local

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou : **un profil renommé conserve son état local**, et un profil neuf reste neuf.
 *
 * L'état local est indexé par **nom de profil** — visage (`onyx_bot_faces`), visibilité
 * (`force_hidden_<nom>`), activité (`onyx_bot_activity`) et rattachement des conversations par le
 * titre (`conversations.json`). Après un renommage serveur (`ancien-nom-qa` → `qa-tester`), ces entrées
 * sont sous l'ancien nom : la migration les reporte sur le nom courant, **une seule fois**.
 *
 * Retirer la migration fait rougir ce test : l'état reste sous `ancien-nom-qa`, et `qa-tester` est vu
 * comme un bot neuf.
 */
class ProfileStateRenameMigrationTest {

    /** Un magasin clé-valeur en mémoire : `SharedPreferences` sans Android. */
    private class MemoryStore : RenameStore {
        private val values = mutableMapOf<String, Any>()

        override fun contains(key: String) = values.containsKey(key)
        override fun string(key: String) = values[key] as? String
        override fun boolean(key: String, fallback: Boolean) = values[key] as? Boolean ?: fallback
        override fun putString(key: String, value: String) { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        @Suppress("UNCHECKED_CAST")
        override fun stringSet(key: String) = values[key] as? Set<String>
        override fun putStringSet(key: String, value: Set<String>) { values[key] = value }
    }

    private val faces = MemoryStore()
    private val activity = MemoryStore()
    private val ledgerStore = MemoryStore()

    private val server = "server-1"
    private val migration = ProfileStateMigration(faces, activity, ProfileRenameLedger(ledgerStore))

    /** Le roster d'après le renommage : `qa-tester` a porté `ancien-nom-qa`. */
    private val renamed = listOf(BotProfile(name = "qa-tester", displayName = "QA-tester", previousNames = listOf("ancien-nom-qa")))

    private fun oldState() {
        // Visage marqué + masqué, un titre et un modèle : tout sous l'ancien nom.
        faces.putString(FaceKeys.face("ancien-nom-qa"), "1:egg:happy:grin:blush:7:3:1")
        faces.putBoolean(FaceKeys.hideOverride("ancien-nom-qa"), true)
        faces.putString(FaceKeys.title("ancien-nom-qa"), "Recruteur")
        faces.putString(FaceKeys.model("ancien-nom-qa"), "sonnet")
        faces.putBoolean(FaceKeys.shown("ancien-nom-qa"), false)
        // Activité : la marque de lecture et la place dans l'ordre.
        activity.putString(ActivityKeys.seen(server, ActivityKeys.botItem("ancien-nom-qa")), "1620000000.0")
        activity.putString(
            ActivityKeys.order(server),
            listOf(ActivityKeys.botItem("ancien-nom-qa"), "room:x").joinToString(ActivityKeys.SEPARATOR),
        )
    }

    private fun conversations() = listOf(
        Conversation(id = "c1", createdAt = 1, updatedAt = 2, title = "ancien-nom-qa"),
        Conversation(id = "c2", createdAt = 3, updatedAt = 4, title = "ancien-nom-qa"),
    )

    @Test
    fun `un profil renomme conserve son etat local`() {
        oldState()
        val before = conversations()

        val outcome = migration.migrate(renamed, server, before)

        // Le visage et la visibilité ont suivi le nom courant.
        assertEquals("1:egg:happy:grin:blush:7:3:1", faces.string(FaceKeys.face("qa-tester")))
        assertEquals(true, faces.boolean(FaceKeys.hideOverride("qa-tester"), false))
        assertEquals("Recruteur", faces.string(FaceKeys.title("qa-tester")))
        assertEquals("sonnet", faces.string(FaceKeys.model("qa-tester")))
        assertEquals(false, faces.boolean(FaceKeys.shown("qa-tester"), true))
        assertFalse("l'ancien visage ne reste pas", faces.contains(FaceKeys.face("ancien-nom-qa")))
        assertFalse("l'ancien masque ne reste pas", faces.contains(FaceKeys.hideOverride("ancien-nom-qa")))

        // L'activité et l'ordre ont suivi.
        assertEquals("1620000000.0", activity.string(ActivityKeys.seen(server, ActivityKeys.botItem("qa-tester"))))
        assertNull(activity.string(ActivityKeys.seen(server, ActivityKeys.botItem("ancien-nom-qa"))))
        assertEquals(
            listOf(ActivityKeys.botItem("qa-tester"), "room:x").joinToString(ActivityKeys.SEPARATOR),
            activity.string(ActivityKeys.order(server)),
        )

        // Les deux conversations sont retitrées : elles restent rattachées.
        assertEquals(listOf("qa-tester", "qa-tester"), outcome.conversations.map { it.title })

        // Un seul renommage reporté, et tracé.
        assertEquals(listOf("ancien-nom-qa=>qa-tester"), outcome.applied.map { it.fact })
        assertEquals(setOf("ancien-nom-qa=>qa-tester"), ProfileRenameLedger(ledgerStore).applied())
    }

    @Test
    fun `un profil inconnu reste neuf`() {
        oldState()
        val roster = renamed + BotProfile(name = "newbie", displayName = "Newbie")

        val outcome = migration.migrate(roster, server, conversations())

        // Le bot neuf n'hérite de rien : ni visage, ni masque, ni activité, ni conversation.
        assertNull(faces.string(FaceKeys.face("newbie")))
        assertNull(faces.string(FaceKeys.title("newbie")))
        assertFalse(faces.contains(FaceKeys.hideOverride("newbie")))
        assertNull(activity.string(ActivityKeys.seen(server, ActivityKeys.botItem("newbie"))))
        assertTrue(outcome.conversations.none { it.title == "newbie" })
    }

    @Test
    fun `la migration ne se rejoue pas et n'ecrase pas l'etat courant`() {
        oldState()
        migration.migrate(renamed, server, conversations())

        // Entre-temps, la personne a changé le visage de `qa-tester` et marqué sa ligne lue.
        faces.putString(FaceKeys.face("qa-tester"), "5:star:cool")
        activity.putString(ActivityKeys.seen(server, ActivityKeys.botItem("qa-tester")), "1700000000.0")

        // Une relance de l'app rejoue la passe : rien ne doit bouger.
        val second = migration.migrate(renamed, server, conversations())

        assertTrue("plus rien à reporter", second.applied.isEmpty())
        assertEquals("5:star:cool", faces.string(FaceKeys.face("qa-tester")))
        assertEquals("1700000000.0", activity.string(ActivityKeys.seen(server, ActivityKeys.botItem("qa-tester"))))
        // Le journal n'a pas grossi.
        assertEquals(setOf("ancien-nom-qa=>qa-tester"), ProfileRenameLedger(ledgerStore).applied())
    }

    @Test
    fun `un bot neuf qui reprend l'ancien nom n'herite pas`() {
        oldState()
        migration.migrate(renamed, server, conversations())

        // Un bot **réellement neuf** apparaît plus tard sous l'ancien nom `ancien-nom-qa`.
        faces.putString(FaceKeys.face("ancien-nom-qa"), "9:moon:calm")
        faces.putBoolean(FaceKeys.hideOverride("ancien-nom-qa"), true)

        val outcome = migration.migrate(renamed, server, conversations())

        // Il garde SON état : la migration est journalisée et ne le lui prend pas.
        assertTrue(outcome.applied.isEmpty())
        assertEquals("9:moon:calm", faces.string(FaceKeys.face("ancien-nom-qa")))
        // Et `qa-tester` garde le sien, intact.
        assertEquals("1:egg:happy:grin:blush:7:3:1", faces.string(FaceKeys.face("qa-tester")))
    }

    @Test
    fun `l'etat d'un profil vivant homonyme n'est jamais vole`() {
        // `scout` existe encore en tant que profil : `qa-tester` dit pourtant l'avoir porté.
        val roster = listOf(
            BotProfile(name = "qa-tester", previousNames = listOf("scout")),
            BotProfile(name = "scout"),
        )
        faces.putString(FaceKeys.face("scout"), "3:leaf:calm")

        val outcome = migration.migrate(roster, server, emptyList(), aliases = emptyMap())

        assertTrue(outcome.applied.isEmpty())
        assertEquals("3:leaf:calm", faces.string(FaceKeys.face("scout")))
        assertNull(faces.string(FaceKeys.face("qa-tester")))
    }

    @Test
    fun `la table d'alias locale reporte le renommage sans previous_names`() {
        oldState()
        // Le serveur ne dit pas `previous_names` : une table d'alias **explicite** prend le
        // relais. La table de production est vide (la source d'un renommage est le serveur) ;
        // c'est donc le mécanisme de repli que ce test tient, avec un ancien nom neutre.
        val silent = listOf(BotProfile(name = "qa-tester", displayName = "QA-tester"))

        migration.migrate(silent, server, emptyList(), aliases = mapOf("ancien-nom-qa" to "qa-tester"))

        assertEquals("1:egg:happy:grin:blush:7:3:1", faces.string(FaceKeys.face("qa-tester")))
        assertEquals(true, faces.boolean(FaceKeys.hideOverride("qa-tester"), false))
    }

    @Test
    fun `un titre qui n'est que la premiere question n'est pas retitre`() {
        val conv = listOf(
            Conversation(
                id = "c1",
                createdAt = 1,
                updatedAt = 2,
                title = "ancien-nom-qa",
                messages = listOf(com.onyx.app.data.model.ChatMessage(id = "m1", role = com.onyx.app.data.model.Role.USER, text = "ancien-nom-qa", createdAt = 1)),
            ),
        )
        // Le titre égale la première question : `customName` le refuse, rien n'est retitré.
        val outcome = migration.migrate(renamed, server, conv)
        assertEquals("ancien-nom-qa", outcome.conversations.single().title)
    }
}
