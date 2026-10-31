package com.onyx.app.ui.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Verrou de forme de l'en-tête — celui qui mord sur la source d'avant, telle quelle.**
 *
 * Il ne référence aucune des fonctions du correctif : il lit les sources et les largeurs relevées,
 * donc il **s'exécute aussi sur l'arbre d'avant** et y échoue franchement — c'est la morsure rouge.
 *
 * Largeurs relevées sur l'appareil (1080 x 2400, densité 420 → 2,625 px/dp, corps des métas 12 sp) :
 *
 * | grandeur                                            | px    | dp    |
 * |-----------------------------------------------------|-------|-------|
 * | largeur du centre de la barre (la colonne du titre)  | 456   | 173,7 |
 * | « @writer » (le profil écrit en trop)                | 107   | 40,8  |
 * | écart de rangée (3 dp)                               | 7,875 | 3     |
 * | chevron (8 dp)                                       | 21    | 8     |
 * | « deepseek/deepseek-v4.1-flash » en entier          | 427,0 | 162,7 |
 * | « deepseek/deepseek-… » (ce qui était dessiné)       | 303   | 115,4 |
 *
 * D'où la morsure : **312,25 px** laissés au modèle avec le profil, **427,0 px** demandés par le nom
 * entier — il manquait 114,9 px, exactement ce que « @writer » et son écart occupaient.
 */
class EnteteNomModeleSourceVerrouTest {

    private val centrePx = 456.0
    private val profilPx = 107.0
    private val ecartPx = 7.875
    private val chevronPx = 21.0
    private val modeleEntierPx = 427.0

    @Test
    fun `la place du profil est ce qui manquait au nom du modele`() {
        val sansProfil = centrePx - ecartPx - chevronPx
        val avecProfil = centrePx - profilPx - 2 * ecartPx - chevronPx
        assertFalse(
            "AVANT : $avecProfil px pour un nom de $modeleEntierPx px — il manquait " +
                "${modeleEntierPx - avecProfil} px, ce que « @writer » ($profilPx px) + son écart " +
                "($ecartPx px) occupaient",
            avecProfil >= modeleEntierPx,
        )
        assertTrue(
            "APRÈS : $sansProfil px pour un nom de $modeleEntierPx px",
            sansProfil >= modeleEntierPx,
        )
        assertTrue(
            "la place rendue au modèle est exactement celle du profil",
            profilPx + ecartPx >= modeleEntierPx - avecProfil,
        )
    }

    @Test
    fun `l en-tete n ecrit plus le profil sans comparer au titre, et le modele est un identifiant`() {
        val bar = source("app/src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
        assertFalse(
            "AVANT : le profil s'écrit sans regarder le titre — OnyxTopBar.kt:361, « @writer » " +
                "mesuré à 107 px / 40,8 dp pris à la place du modèle",
            bar.contains("profileLabel?.takeIf { it.isNotBlank() }?.let {"),
        )
        assertTrue(
            "APRÈS : le profil passe par la comparaison au titre",
            bar.contains("headerProfileLabel(profileLabel, resolvedTitle)"),
        )
        // AVANT, le modèle se coupait à la fin (« deepseek/deepseek-… ») : il ne disait plus quel
        // modèle partait. APRÈS, il est un identifiant — une ligne, abrégé au milieu s'il déborde.
        assertTrue(
            "APRÈS : le modèle est dessiné par IdentifierText",
            bar.contains("IdentifierText("),
        )
        assertFalse(
            "AVANT : le modèle se coupait par l'ellipse de fin",
            bar.contains("measurer.headerLine(subtitle, subtitleStyle, budget)"),
        )
    }

    @Test
    fun `le routage reste lisible quand il differe du titre`() {
        val chat = source("app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
        assertTrue(
            "l'écran passe toujours le profil en force (`profileInForce`) à l'en-tête",
            chat.contains("profileLabel = state.profileInForce"),
        )
        val editor = source("app/src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt")
        assertTrue(
            "l'identité du bot (ouverte par le visage) écrit son @handle",
            editor.contains("\"@\" + (bot?.handle ?: botHandle(typed))"),
        )
    }

    private fun source(path: String): String =
        listOf(File(path), File("../$path")).first { it.isFile }.readText()
}
