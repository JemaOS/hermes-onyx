package com.onyx.app.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La troisième surface du lot « Teach a task » : le skill devient une routine.**
 *
 * Ce qui se vérifie ici est la seule chose qui pouvait se tromper sans bruit : ce qui est posé dans
 * le brouillon de routine. L'écran, lui, ne fait que l'afficher.
 */
class SkillRoutineTest {

    @Test
    fun `la routine prend le nom du skill`() {
        val seed = SkillRoutine.from("facture")!!

        assertEquals("facture", seed.title)
    }

    @Test
    fun `un chemin de skill ne garde que son dernier segment`() {
        // Un skill peut vivre dans un dossier (`facturation/facture`) : une routine qui s'appelle
        // « facturation/facture » se lit comme un chemin dans la liste des horaires.
        assertEquals("facture", SkillRoutine.from("facturation/facture")!!.title)
        assertEquals("facture", SkillRoutine.from("  /facture/  ")!!.title)
    }

    @Test
    fun `l invite nomme la competence sans recopier son contenu`() {
        val prompt = SkillRoutine.promptFor("facture")

        assertTrue("la compétence est nommée", prompt.contains("« facture »"))
        assertTrue("elle est bien demandée", prompt.contains("Applique la compétence"))
        // Le Markdown n'est pas recopié : le skill vit sur le serveur, sous son nom.
        assertTrue("aucun titre de skill n'est figé", !prompt.contains("##"))
    }

    @Test
    fun `un nom vide ne propose rien`() {
        assertNull(SkillRoutine.from(""))
        assertNull(SkillRoutine.from("   "))
        assertNull(SkillRoutine.from("/"))
    }
}
