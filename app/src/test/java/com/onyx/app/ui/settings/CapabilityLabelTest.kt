package com.onyx.app.ui.settings

import com.onyx.app.data.net.ServerCapability
import com.onyx.app.data.net.Support
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que la section « Server capabilities » écrit, et ce qu'elle refuse d'écrire.
 *
 * Deux règles tenues ici, et ce sont les seules qui comptent :
 *
 * 1. **Une réponse inconnue n'est jamais un oui.** [Support.UNKNOWN] se comporte comme un non pour
 *    décider (`Support.isUsable`), mais l'écran ne prétend pas que la capacité existe : il écrit
 *    « Not confirmed ». Une ligne « Available » sur une sonde muette serait exactement le mensonge
 *    que la brique B1 devait supprimer.
 * 2. **Aucun libellé technique.** Le nom d'une capacité est son rôle (« Secrets vault »), jamais le
 *    chemin de sa route : personne ne lit « api/vault » et ne comprend ce qu'il gagne.
 */
class CapabilityLabelTest {

    @Test
    fun `une sonde sans reponse ne dit jamais oui`() {
        assertEquals("Checking…", supportLabel(null))
        assertEquals("Not on this server", supportLabel(Support.NO))
        assertEquals("Not confirmed", supportLabel(Support.UNKNOWN))
        // Le seul cas qui a le droit d'écrire « Available ».
        assertEquals("Available", supportLabel(Support.YES))
    }

    @Test
    fun `une reponse inconnue ne se lit pas comme une presence`() {
        val unknown = supportLabel(Support.UNKNOWN)
        assertNotEquals(supportLabel(Support.YES), unknown)
        assertFalse("UNKNOWN ne doit pas s'écrire « Available »", unknown.contains("Available"))
    }

    @Test
    fun `chaque capacite porte son role, jamais le nom de sa route`() {
        val labels = ServerCapability.entries.map { capabilityLabel(it) }
        labels.forEach { label ->
            assertTrue("un libellé vide ne dit rien", label.isNotBlank())
            assertFalse("un libellé ne cite pas une route : $label", label.contains("api/"))
            assertFalse("un libellé n'est pas un identifiant : $label", label.contains("_"))
        }
        assertEquals("trois libellés distincts", labels.size, labels.distinct().size)
        assertEquals("Secrets vault", capabilityLabel(ServerCapability.VAULT))
        assertEquals("Teach a task", capabilityLabel(ServerCapability.RECORD))
        assertEquals("Room handoff", capabilityLabel(ServerCapability.HANDOFF))
    }

    @Test
    fun `les libelles de la section sont traduits`() {
        val strings = listOf("Server capabilities", "Secrets vault", "Teach a task", "Room handoff",
            "Checking…", "Available", "Not on this server", "Not confirmed")
        strings.forEach { key ->
            val french = OnyxLocalization.translate(key, Locale.FRANCE)
            assertNotEquals("« $key » n'a pas de français", key, french)
        }
        assertEquals("Capacités du serveur", OnyxLocalization.translate("Server capabilities", Locale.FRANCE))
        assertEquals("Non confirmé", OnyxLocalization.translate("Not confirmed", Locale.FRANCE))
    }
}
