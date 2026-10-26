package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatProjectsTest {
    @Test
    fun `le nom de dossier est nettoye comme `() {
        assertEquals("Mon-projet- suite", projectFolderName("  Mon/projet: suite  "))
        assertEquals("visible", projectFolderName("...visible"))
        assertEquals("ligne 1 ligne 2", projectFolderName("ligne 1\nligne 2"))
        assertNull(projectFolderName("... \n "))
        assertEquals(80, projectFolderName("x".repeat(120))?.length)
    }

    @Test
    fun `seuls les vrais dossiers sont proposes dans l ordre alphabetique`() {
        val projects = listOf(
            ServerProject(id = "home", label = "No project", path = "/home", isHome = true),
            ServerProject(id = "z", label = "zeta", path = "/z"),
            ServerProject(id = "empty", label = "Empty", path = null),
            ServerProject(id = "a", label = "Alpha", path = "/a"),
        )

        assertEquals(listOf("Alpha", "zeta"), projectFolders(projects).map { it.label })
        assertEquals(listOf("zeta"), projectMoveTargets(projects, "Alpha").map { it.label })
    }

    @Test
    fun `les chemins du serveur gardent une seule barre oblique`() {
        assertEquals("/home/Projects/Onyx", serverPathJoin("/home/", "Projects", "Onyx"))
        assertEquals("~/Projects", serverPathJoin("~", "Projects"))
    }
}
