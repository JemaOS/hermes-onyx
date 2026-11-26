package com.onyx.app.data.model.serve

import com.onyx.app.data.net.DashboardSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regroupement et la recherche des compétences de l'écran « Capacités ».
 *
 * Deux défauts que ces règles évitent : une famille affichée deux fois parce que le serveur écrit
 * « Research » ici et « research » là, et une compétence qu'on ne retrouve pas en la cherchant par
 * sa famille (« web » doit ramener ce qui est rangé dans « Web »).
 */
class SkillGroupingTest {

    private val skills = listOf(
        DashboardSkill(name = "hermes-agent", description = "Configure et exploite Hermes", category = "Hermes"),
        DashboardSkill(name = "web-search", description = "Cherche sur le web", category = "web"),
        DashboardSkill(name = "arxiv", description = "Papiers scientifiques", category = "Research"),
        DashboardSkill(name = "curator", description = "Range les souvenirs", category = "  Research  "),
        DashboardSkill(name = "sans-famille", description = "Aucune famille déclarée"),
        DashboardSkill(name = "sans-famille-2", description = "Aucune famille non plus", category = "   "),
    )

    @Test
    fun `les familles sont repliees en minuscules et la famille sans nom passe en dernier`() {
        val groups = skillGroups(skills, "")

        assertEquals(listOf("hermes", "research", "web", "other"), groups.keys.toList())
        assertEquals(listOf("arxiv", "curator"), groups.getValue("research").map { it.name })
        assertEquals(listOf("sans-famille", "sans-famille-2"), groups.getValue("other").map { it.name })
    }

    @Test
    fun `la recherche porte sur le nom, la description et la famille, sans la casse`() {
        assertEquals(listOf("web-search"), skillGroups(skills, "WEB").getValue("web").map { it.name })
        assertEquals(
            listOf("hermes-agent"),
            skillGroups(skills, "exploite").getValue("hermes").map { it.name },
        )
        assertEquals(listOf("arxiv", "curator"), skillGroups(skills, "research").getValue("research").map { it.name })
    }

    @Test
    fun `une recherche sans resultat ne rend aucun groupe`() {
        assertTrue(skillGroups(skills, "zzz").isEmpty())
        assertEquals(0, skillMatchCount(skills, "zzz"))
    }

    @Test
    fun `le compte suit la recherche, et vaut le tout quand elle est vide`() {
        // Le libellé « Rechercher les N compétences » dit le nombre réellement filtré.
        assertEquals(6, skillMatchCount(skills, ""))
        assertEquals(2, skillMatchCount(skills, "research"))
        assertEquals(1, skillMatchCount(skills, "web"))
        // La famille « other » porte le nom qu'on lui a donné, jamais celui de la compétence.
        assertEquals(OTHER_GROUP, skillGroups(skills, "").keys.last())
    }
}
