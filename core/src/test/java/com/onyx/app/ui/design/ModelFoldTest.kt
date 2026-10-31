package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **repli** de la feuille : ce qui se voit d'emblée, et ce que la dernière ligne
 * « Tous les modèles · N › » déplie.
 *
 * Le calcul est tenu ici, sans appareil : le compteur montré par la feuille et le compteur éprouvé
 * sont le **même chiffre**. Aucun prix, aucune couleur — seulement la liste.
 */
class ModelFoldTest {

    private fun choice(id: String) = ModelChoice(id = id, label = id, profile = "default")

    private val catalogue = listOf("kilo/a", "kilo/b", "kilo/c", "kilo/d").map(::choice)

    @Test
    fun `les epingles et les recents restent devant, le reste attend la ligne`() {
        val sections = ModelSelection.sections(
            catalogue,
            favourites = setOf("kilo/a"),
            recents = listOf("kilo/b"),
        )
        assertEquals("un épinglé", 1, sections.favourites.size)
        assertEquals("un récent", 1, sections.recents.size)
        assertEquals("deux rangées attendent le dépliage", 2, ModelSelection.restCount(sections))
        assertFalse("il y a de quoi garder devant : la liste s'ouvre repliée", ModelSelection.opensExpanded(sections))
    }

    @Test
    fun `sans epingle ni recent la liste s ouvre deja depliee`() {
        val sections = ModelSelection.sections(catalogue, favourites = emptySet(), recents = emptyList())
        assertTrue("rien à garder : replier montrerait une feuille vide", ModelSelection.opensExpanded(sections))
        assertEquals("rien n'est caché", 4, ModelSelection.restCount(sections))
    }

    @Test
    fun `un modele epingle et recent ne se compte pas deux fois`() {
        val sections = ModelSelection.sections(
            catalogue,
            favourites = setOf("kilo/a"),
            recents = listOf("kilo/a"),
        )
        assertEquals("l'épinglé l'emporte", 1, sections.favourites.size)
        assertEquals("il ne se répète pas dans les récents", 0, sections.recents.size)
        assertEquals("le compteur du dépliage reste honnête", 3, ModelSelection.restCount(sections))
    }

    @Test
    fun `une recherche vide deplie tout, et la ligne disparait`() {
        // Une recherche ne laisse rien derrière : ses résultats sont tous dans « le reste », donc la
        // ligne « Tous les modèles » n'aurait plus rien à déplier.
        val sections = ModelSelection.sections(
            ModelSelection.filter(catalogue, "kilo/c"),
            favourites = setOf("kilo/a"),
            recents = listOf("kilo/b"),
        )
        assertEquals("la recherche a écarté les deux gardés", 1, ModelSelection.restCount(sections))
        assertTrue(sections.favourites.isEmpty())
        assertTrue(sections.recents.isEmpty())
    }
}
