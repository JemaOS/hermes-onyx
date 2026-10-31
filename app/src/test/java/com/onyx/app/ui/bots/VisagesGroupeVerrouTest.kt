package com.onyx.app.ui.bots

import java.io.File
import com.onyx.app.ui.design.TabFaceCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du défilement des visages sur le repère du bas d'un groupe.**
 *
 * La demande du propriétaire, devant la capture d'une conversation de **groupe** (le salon, sa
 * barre du bas : Chat / Flux / Fichiers puis le repère-visage) :
 *
 * > « dans un groupe bot, cette partie encadrée en rouge, il vaut mieux faire passer le visage de
 * > un bot par un bot avec un temps pour passer à l'autre : 8 secondes »
 *
 * Le repère doit donc présenter les visages des bots du groupe **un par un**, **[8 secondes]**
 * chacun, en boucle ; la teinte suit le visage affiché ; « Réduire les animations » le **fige** ; et
 * une conversation à **un seul bot** ne défile **pas**.
 *
 * Quatre clauses, quatre bites. Les trois premières sont des **ancres de source** : elles tombent
 * dès que le code cesse de dire ce qu'il doit dire. La quatrième interroge la **règle pure** du
 * défilement ([TabFaceCycle]), donc le chiffre vérifié et le chiffre employé sont le même code.
 */
class VisagesGroupeVerrouTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val room = read("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt")
    private val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")

    // — (a) le repère d'un groupe rend successivement les visages des bots du groupe —

    @Test
    fun `le repere d une salle fait defiler les visages des membres`() {
        // Le salon construit la file des visages à partir de ses membres, demande l'index courant,
        // et la teinte du repère suit `botFaceColour` du visage montré.
        assertTrue(
            "GroupRoomScreen doit peupler `faceCycle` (les visages des bots du groupe)",
            room.contains("faceCycle ="),
        )
        assertTrue(
            "GroupRoomScreen doit tenir l'index courant par `rememberTabFaceCycleIndex`",
            room.contains("rememberTabFaceCycleIndex"),
        )
        assertTrue(
            "la file des visages vient des membres de la salle (`room.members`)",
            room.contains("room.members"),
        )
        assertTrue(
            "la teinte du repère suit le visage montré (`botFaceColour`)",
            room.contains("botFaceColour"),
        )
    }

    // — (b) l'intervalle déclaré est de huit secondes —

    @Test
    fun `l intervalle declare vaut huit secondes`() {
        // Le chiffre est **lu dans la source** de la barre : s'il n'y est pas, la valeur mesurée
        // est dite « absente » au lieu de passer pour huit secondes.
        val m = Regex("""PeriodMs\s*:\s*Long\s*=\s*([0-9_]+)L""").find(bar)
        val measured = m?.groupValues?.get(1)?.replace("_", "")
            ?: run {
                assertTrue(
                    "ChatTabBar doit déclarer `const val PeriodMs: Long = 8_000L` (valeur mesurée : absente)",
                    false,
                )
                return@run ""
            }
        assertEquals("l'intervalle déclaré doit valoir 8 secondes", "8000", measured)
    }

    // — (c) une conversation à un seul bot ne défile pas —

    @Test
    fun `un seul bot ne defile pas`() {
        // La barre refuse le défilement sous deux visages : un repère à un seul visage reste figé.
        assertTrue(
            "ChatTabBar doit court-circuiter le défilement sous deux visages (`faces.size <= 1`)",
            bar.contains("faces.size <= 1"),
        )
        // Un fil à un bot (ChatScreen) ne demande **aucun** défilement : le défaut serait d'en voir
        // tourner un là où il n'y a qu'un bot.
        assertFalse(
            "ChatScreen (1-à-1) ne doit pas demander de défilement (`faceCycle`)",
            chat.contains("faceCycle"),
        )
    }

    // — (d) « Réduire les animations » fige le visage —

    @Test
    fun `reduire les animations fige le visage`() {
        assertTrue(
            "ChatTabBar doit lire le réglage du système (`LocalReduceMotion`)",
            bar.contains("LocalReduceMotion"),
        )
        // Le figement, nommé : sous le réglage (ou sous un seul visage) la boucle ne démarre pas
        // et l'index reste le premier. L'ancre porte sur **ce** court-circuit, pas sur la lecture
        // du réglage (que la barre fait déjà pour la capsule).
        assertTrue(
            "le défilement doit se figer par un court-circuit `reduceMotion) return 0`",
            bar.contains("reduceMotion) return 0"),
        )
    }

    // — Les valeurs, pas seulement les ancres —

    @Test
    fun `le chiffre du defilement est huit mille millisecondes`() {
        assertEquals("huit secondes, en millisecondes", 8_000L, TabFaceCycle.PeriodMs)
    }

    @Test
    fun `les visages passent un par un, toutes les huit secondes`() {
        // Trois visages : la suite est 0, 1, 2, puis 0 — chaque visage tient exactement 8 s.
        assertEquals(0, TabFaceCycle.indexAt(0L, 3))
        assertEquals(0, TabFaceCycle.indexAt(7_999L, 3))
        assertEquals(1, TabFaceCycle.indexAt(8_000L, 3))
        assertEquals(1, TabFaceCycle.indexAt(15_999L, 3))
        assertEquals(2, TabFaceCycle.indexAt(16_000L, 3))
        assertEquals(2, TabFaceCycle.indexAt(23_999L, 3))
        assertEquals(0, TabFaceCycle.indexAt(24_000L, 3))
        // Le temps négatif (une horloge qui recule) retombe sur le premier visage.
        assertEquals(0, TabFaceCycle.indexAt(-5L, 3))
    }

    @Test
    fun `un seul visage, ou aucun, ne defile jamais`() {
        assertEquals(0, TabFaceCycle.indexAt(0L, 1))
        assertEquals(0, TabFaceCycle.indexAt(60_000L, 1))
        assertEquals(0, TabFaceCycle.indexAt(60_000L, 0))
        assertEquals(0, TabFaceCycle.shownIndex(60_000L, 1, reduceMotion = false))
    }

    @Test
    fun `reduire les animations fige sur le premier visage, quel que soit le temps`() {
        // Le figement est dans la règle, pas seulement au bord de l'écran : quelle que soit
        // l'horloge, le visage montré est le premier — un visage choisi, stable.
        assertEquals(0, TabFaceCycle.shownIndex(0L, 3, reduceMotion = true))
        assertEquals(0, TabFaceCycle.shownIndex(16_000L, 3, reduceMotion = true))
        assertEquals(0, TabFaceCycle.shownIndex(80_000L, 3, reduceMotion = true))
        // Sans le réglage, le même temps fait bel et bien défiler.
        assertEquals(2, TabFaceCycle.shownIndex(16_000L, 3, reduceMotion = false))
    }
}
