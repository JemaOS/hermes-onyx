package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la garde de mesure.**
 *
 * La géométrie est éprouvée sans appareil par `FloatAnchorTest` (core) : c'est lui qui compare la
 * position mesurée à la position attendue, chemin de reprise par chemin de reprise. Ce test-ci tient
 * le **câblage** : l'élément qui flotte au-dessus de la plaque — la pastille « aller au dernier
 * message » — passe par l'ancre mesurée, et ne peut pas être dessiné avant que la plaque ait livré
 * sa hauteur. **Arbitrage de la réconciliation** : la poignée à chevron a été retirée (demande du
 * propriétaire), il n'y a donc plus qu'**un** élément flottant — ce verrou le **nomme** au lieu
 * d'exiger une poignée disparue.
 *
 * Il mord : retirer `floatAnchor != null` de la garde le fait tomber, et c'est exactement le défaut —
 * une position issue d'une ancre non mesurée (zéro à la première passe de chaque reprise) se
 * retrouvait peinte à l'écran.
 */
class FloatAnchorWiringTest {

    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val stacking =
        File("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt").readText()

    @Test
    fun `l ecran ne se dessine pas avant d avoir mesure la plaque`() {
        // L'ancre vient d'une fonction qui rend `null` tant que la plaque n'a pas livré ses hauteurs :
        // on ne dessine pas de position, on demande la mesure.
        assertTrue(
            screen.contains("val floatAnchor = threadFloatAnchor("),
        )
        assertTrue(
            "les deux hauteurs mesurées doivent être passées, pas des estimations",
            screen.contains("composerPx = composerHeightPx,") &&
                screen.contains("bottomRowPx = tabBarHeightPx,"),
        )
        assertTrue(
            "et la marge mesurée reste la réserve du fil plus le dégagement nommé",
            screen.contains("measured = jumpClearance,"),
        )
    }

    @Test
    fun `le seul element flottant qui reste est derriere la garde`() {
        // **Arbitrage de la réconciliation.** Le lot du chevron a retiré la **poignée** de la barre
        // (demande du propriétaire) : il ne reste qu'**un** élément flottant — la pastille « aller au
        // dernier message ». Sa garde de mesure tient : elle ne paraît pas avant que la plaque ait
        // livré ses hauteurs. Ce verrou-ci **nomme** l'arbitrage au lieu d'exiger une poignée qui
        // n'existe plus.
        assertFalse(
            "la poignée à chevron ne doit plus exister (retrait demandé par le propriétaire)",
            screen.contains("ChatTabBarHandle("),
        )
        val pastille = screen.substringAfter("visible = offersJump").substringBefore("enter = fadeIn")
        assertTrue(
            "la pastille doit porter la garde de mesure",
            pastille.contains("floatAnchor != null,"),
        )
    }

    @Test
    fun `aucun element flottant n est place avant l ancre mesuree`() {
        // L'ANCRE EST DÉCLARÉE AVANT LA PASTILLE : l'ordre de la source est la preuve que la position
        // dessinée ne peut pas être construite sans passer par elle.
        val ancre = screen.indexOf("val floatAnchor")
        assertTrue("l'ancre doit exister", ancre > 0)
        val pill = screen.indexOf("JumpToLatestPill(live = state.isStreaming")
        assertTrue("la pastille doit venir après l'ancre", pill > ancre)

        // Et elle ne reçoit **pas** sa position avant que l'ancre existe : la marge du bas de
        // l'élément flottant n'est écrite qu'après la déclaration de l'ancre.
        val avant = screen.substringBefore("val floatAnchor")
        assertFalse(
            "aucun élément flottant ne doit être posé avant l'ancre mesurée",
            avant.contains("bottom = jumpClearance"),
        )
    }

    @Test
    fun `la regle est tenue dans le noyau, une seule fois pour tous les elements flottants`() {
        assertTrue(
            "la mesure de la plaque est une règle du noyau",
            stacking.contains("fun plaqueMeasured(composerDp: Int, bottomRowDp: Int): Boolean"),
        )
        assertTrue(
            "l'ancre rend `null` tant que la plaque n'est pas mesurée",
            stacking.contains("fun floatAnchorDp(composerDp: Int, bottomRowDp: Int, measuredDp: Int): Int?"),
        )
        assertTrue(
            "et le haut de l'élément se lit depuis le haut de la fenêtre",
            stacking.contains("fun floatTopDp("),
        )
        assertTrue(
            "le verdict d'écrasement de l'en-tête est une règle, pas une intention",
            stacking.contains("fun overlapsHeader(floatTopDp: Int?, headerBottomDp: Int): Boolean"),
        )
        assertTrue(
            "la garde se lit dans `logcat` sur l'appareil, chemin de reprise par chemin de reprise",
            stacking.contains("const val FloatAnchorLogTag: String = \"OnyxFloatAnchor\""),
        )
    }
}
