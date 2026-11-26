package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Les chemins que la sonde interroge sont-ils ceux que le serveur **sert** ?
 *
 * C'est le défaut que ce fichier empêche de revenir : une capacité pointée sur un chemin
 * d'attente (`api/vault`) répond « absent » sur un serveur où le coffre est pourtant installé —
 * l'écran dirait le contraire de la vérité, ce que la brique B1 devait précisément supprimer.
 *
 * Chaque chemin est donc écrit en dur ici, tel qu'il a été **vérifié sur une installation réelle**
 * (`/api/plugins/onyx-vault/health` répond 401 sans session, 200 avec), et non déduit d'un nom.
 */
class ServerCapabilityRouteTest {

    @Test
    fun `le coffre interroge la route de sante du plugin installe`() {
        assertEquals("api/plugins/onyx-vault/health", ServerCapability.VAULT.route)
    }

    @Test
    fun `la demonstration vise le second plugin, pas le coffre`() {
        assertEquals("api/plugins/onyx-record/health", ServerCapability.RECORD.route)
        assertFalse(
            "un journal relisible n'entre pas dans le coffre",
            ServerCapability.RECORD.route.contains("onyx-vault"),
        )
    }

    @Test
    fun `aucune capacite ne pointe plus sur un chemin d'attente`() {
        val waited = listOf("api/vault", "api/record")
        ServerCapability.entries.forEach { capability ->
            assertFalse(
                "${capability.name} pointe encore sur « api/… » au lieu du chemin réel",
                capability.route in waited,
            )
        }
    }
}
