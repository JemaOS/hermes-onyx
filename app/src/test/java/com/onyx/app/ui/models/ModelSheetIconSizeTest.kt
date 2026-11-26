package com.onyx.app.ui.models

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La taille des icônes de la feuille des modèles.**
 *
 * Le défaut mesuré sur la capture du propriétaire : la croix de fermeture sortait à **31,7 dp**
 * (pour 15 dp annoncés) et l'étoile à **44,7 dp** (pour 17 dp) — l'étoile débordait sur le prix et
 * le nom du modèle, et la feuille se lisait « tout gros ».
 *
 * La cause n'était pas une taille mal choisie : c'est que **`OnyxIcon` applique son `size` APRÈS le
 * `modifier` de l'appelant** (`modifier = modifier.size(size)`). L'appelant qui posait la cible
 * tactile sur le même nœud — `.size(ModelControlTarget)` — voyait donc son `size` **écrasé**, et le
 * glyphe héritait de la taille de la zone (48 dp).
 *
 * La correction est la **forme juste**, celle qu'emploient déjà le champ de recherche du volet et
 * la barre du haut : un `Box` qui porte la cible tactile, et l'icône à l'intérieur avec sa propre
 * taille.
 */
class ModelSheetIconSizeTest {

    private val feuille = File("src/main/java/com/onyx/app/ui/models/ModelSheet.kt").readText()

    @Test
    fun `la cible tactile fait 48 dp, comme partout dans la maison`() {
        assertEquals(48f, ModelControlTarget.value, 0.01f)
    }

    @Test
    fun `aucune icone ne porte la cible tactile sur le meme noeud qu elle`() {
        // La règle : une icône NE DOIT PAS avoir de `.size(...)` dans son `modifier`. La cible
        // tactile se pose sur un `Box` séparé, sinon elle écrase la taille du glyphe.
        val appels = Regex("OnyxIcon\\s*\\(").findAll(feuille)
        val coupables = mutableListOf<String>()
        for (m in appels) {
            // trouver la fermeture de l'appel
            var i = m.range.last + 1
            var profondeur = 1
            while (i < feuille.length && profondeur > 0) {
                when (feuille[i]) {
                    '(' -> profondeur++
                    ')' -> profondeur--
                }
                i++
            }
            val bloc = feuille.substring(m.range.last + 1, i)
            if (!bloc.contains("modifier")) continue
            if (Regex("\\.size\\s*\\(").containsMatchIn(bloc)) {
                coupables += feuille.substring(m.range.last, i).replace(Regex("\\s+"), " ").take(110)
            }
        }
        assertTrue(
            "Icônes dont la cible tactile écrase la taille du glyphe : $coupables",
            coupables.isEmpty(),
        )
    }

    @Test
    fun `les trois icones de la feuille gardent leur taille`() {
        // La croix de fermeture, l'étoile et la croix du champ : trois glyphes, trois tailles.
        assertTrue("la croix de fermeture est à 15 dp", feuille.contains("size = 15.dp"))
        assertTrue("l'étoile est à 17 dp", feuille.contains("size = 17.dp"))
        assertTrue("la croix du champ est à 16 dp", feuille.contains("size = 16.dp"))
    }

    @Test
    fun `chaque icone a une cible tactile de 48 dp autour d elle`() {
        // Trois `Box` de 48 dp : la croix de fermeture, la croix du champ, l'étoile.
        val boites = Regex("Box\\s*\\(").findAll(feuille).map { m ->
            var i = m.range.last + 1
            var profondeur = 1
            while (i < feuille.length && profondeur > 0) {
                when (feuille[i]) {
                    '(' -> profondeur++
                    ')' -> profondeur--
                }
                i++
            }
            feuille.substring(m.range.last + 1, i)
        }
        val avecCible = boites.count { Regex("\\.size\\s*\\(\\s*ModelControlTarget\\s*\\)").containsMatchIn(it) }
        assertEquals(
            "Chaque icône de la feuille doit avoir sa cible tactile de 48 dp (croix, croix du champ, étoile)",
            3,
            avecCible,
        )
    }

    @Test
    fun `la feuille ne pose plus la cible sur une icone`() {
        // La forme fautive, celle qui a produit la capture : l'icône et la cible sur le même nœud.
        assertFalse(
            "l'icône ne doit plus porter la cible tactile dans son propre modifier",
            Regex("OnyxIcon\\s*\\([\\s\\S]{0,400}?\\.size\\s*\\(\\s*ModelControlTarget\\s*\\)")
                .containsMatchIn(feuille),
        )
    }
}
