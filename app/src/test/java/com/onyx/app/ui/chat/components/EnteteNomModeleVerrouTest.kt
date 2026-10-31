package com.onyx.app.ui.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Verrou de l'en-tête : pas de nom en double, et le modèle entier.**
 *
 * Le propriétaire a vu trois lignes dans la barre du haut : « writer » (le titre), puis
 * « @writer » collé au modèle, et le modèle **cassé sur deux lignes** — « deepseek/ » puis
 * « deepsee… ». Deux causes, tenues ici :
 *
 * 1. le profil en force s'écrivait **toujours**, même quand il répétait le nom déjà à l'écran
 *    (`OnyxTopBar` le dessinait avant le modèle, `ChatScreen` passait `state.profileInForce`) ;
 * 2. sa place manquait au modèle, et le modèle pouvait se **replier** sur deux lignes
 *    (`maxLines = 2`), ce qui coupait un nom au milieu.
 *
 * Ce fichier échoue si :
 *
 * - **(a)** le profil s'affiche alors qu'il répète le nom déjà à l'écran ;
 * - **(b)** le nom du modèle est tronqué alors que la rangée avait encore de la place ;
 * - **(c)** l'information de routage (le profil en force) disparaît de l'écran dans le cas où
 *   elle **diffère** du nom affiché.
 *
 * Le rendu réel est relevé par le harnais d'appareil (`qa/20261031-entete-nom/entete-balayage.py`,
 * en-tête `AVANT` / `APRÈS`) : les largeurs ci-dessous sont ses mesures, pas des estimations.
 */
class EnteteNomModeleVerrouTest {

    // -- (a) Le profil ne répète pas ce qui est déjà écrit ------------------------------------

    @Test
    fun `le profil qui repete le titre ne s ecrit pas`() {
        assertNull("« writer » sous le titre « writer » : doublon", headerProfileLabel("writer", "writer"))
        assertNull("la casse ne fait pas une information", headerProfileLabel("writer", "Writer"))
        assertNull("les blancs autour ne font pas une information", headerProfileLabel(" writer ", "writer"))
        assertNull("un profil vide ne s'écrit pas", headerProfileLabel("", "writer"))
        assertNull("un profil nul ne s'écrit pas", headerProfileLabel(null, "writer"))
    }

    @Test
    fun `un titre d attente reste distinct d un profil`() {
        // « Nouvelle conversation » n'est pas le nom d'un profil : le profil en force reste écrit,
        // parce qu'il dit autre chose que le titre.
        assertEquals("default", headerProfileLabel("default", "Nouvelle conversation"))
    }

    // -- (c) L'information de routage reste écrite quand elle diffère -------------------------

    @Test
    fun `un profil qui differe du titre reste ecrit`() {
        // Un bot titré « Rédacteur » dont le profil est `writer` : les deux diffèrent, la ligne
        // reste — c'est là qu'elle renseigne (le routage du tour porte `writer`).
        assertEquals("writer", headerProfileLabel("writer", "Rédacteur"))
        assertEquals("coder", headerProfileLabel("coder", "Onyx"))
    }

    @Test
    fun `le profil en force reste le libelle passe par l ecran`() {
        val chat = source("app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
        assertTrue(
            "l'écran passe toujours le profil en force à l'en-tête",
            chat.contains("profileLabel = state.profileInForce"),
        )
        // Et il reste lisible **ailleurs** quand l'en-tête l'efface : l'identité du bot écrit son
        // handle. C'est la réponse au « où est passée l'information de routage ».
        val editor = source("app/src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt")
        assertTrue(
            "l'identité du bot écrit son @handle",
            editor.contains("\"@\" + (bot?.handle ?: botHandle(typed))"),
        )
    }

    // -- (b) Le nom du modèle reçoit la place, et tient sur une ligne -------------------------

    /**
     * Les largeurs du relevé d'appareil, en pixels (écran 1080 x 2400, densité 420 → 2,625 px/dp) :
     *
     * - la rangée du second ligne est plafonnée à [RowPx] (240 dp) ;
     * - « @writer » au corps des métas (12 sp) prend [ProfilePx] ;
     * - « deepseek/deepseek-v4.1-flash » en entier en demande [ModelPx] ;
     * - le chevron, l'anneau de contexte et les écarts prennent [FixedPx].
     */
    private val RowPx = 630
    private val ProfilePx = 110
    private val ModelPx = 434
    private val FixedPx = 116

    @Test
    fun `la place du profil est exactement ce qui manque au modele`() {
        val sansProfil = headerModelBudgetPx(RowPx, 0, FixedPx)
        val avecProfil = headerModelBudgetPx(RowPx, ProfilePx, FixedPx)
        assertFalse(
            "AVANT : le nom entier ($ModelPx px) ne tenait pas dans $avecProfil px",
            avecProfil >= ModelPx,
        )
        assertTrue(
            "APRÈS : le nom entier ($ModelPx px) tient dans $sansProfil px",
            sansProfil >= ModelPx,
        )
        assertEquals(
            "la place rendue au modèle est exactement celle du profil",
            ProfilePx,
            sansProfil - avecProfil,
        )
    }

    @Test
    fun `le modele est un identifiant, sur une ligne, qui garde son debut et sa fin`() {
        val bar = source("app/src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
        // Le modèle ne passe plus par la coupe de libellé (`headerLine` + ellipse en fin) : il est
        // un **identifiant** — `maxLines = 1`, dessiné verbatim, abrégé au **milieu** s'il déborde.
        // « deepseek/deepseek-… » (une coupe à la fin) ne disait plus quel modèle partait.
        val site = Regex("IdentifierText\\([\\s\\S]{0,600}?maxLines = 1,")
            .find(bar)?.value ?: error("le modèle n'est plus dessiné par IdentifierText, une ligne")
        assertTrue("le modèle est bien celui de la barre", site.contains("text = subtitle,"))
        assertFalse(
            "plus de coupe de libellé sur le modèle (ellipse en fin)",
            bar.contains("measurer.headerLine(subtitle, subtitleStyle, budget)"),
        )
        // Et le profil n'est plus dessiné sans condition : il passe par la règle.
        assertTrue(bar.contains("headerProfileLabel(profileLabel, resolvedTitle)"))
        assertFalse(
            "plus de profil écrit sans la règle",
            bar.contains("profileLabel?.takeIf { it.isNotBlank() }?.let {"),
        )
    }

    private fun source(path: String): String =
        listOf(File(path), File("../$path")).first { it.isFile }.readText()
}
