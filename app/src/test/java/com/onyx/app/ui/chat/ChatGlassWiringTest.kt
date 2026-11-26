package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La vitre est appliquée — et elle a quelque chose à flouter.**
 *
 * Haze ne dessine son effet que sur un contenu **déjà dessiné** : la source doit être peinte avant la
 * vitre. La fusion avait posé la barre du haut **avant** le pageur dans le même `Box` — donc
 * au-dessous de lui. Deux conséquences, d'un seul défaut, et les deux ont été vues :
 *
 * - la vitre n'avait rien à échantillonner et retombait sur son voile de repli, quasi opaque (le
 *   « verre perdu » se lit à 0,07:1 près de la page : il n'y avait plus de vitre, il y avait un
 *   aplat) ;
 * - la barre, sous le pageur, ne recevait plus aucun appui — aucun bouton ne répondait.
 *
 * Ce test verrouille l'**ordre du dessin** (la barre après la source), et la présence de la vitre sur
 * les trois surfaces qui doivent en porter une. Il échoue si l'une des deux est retirée, et il échoue
 * aussi si quelqu'un remet la barre avant le fil — l'erreur exacte qui a coûté les deux défauts.
 */
class ChatGlassWiringTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val chatScreen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val topBar = read("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt")
    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")

    @Test
    fun `le fil est la source de la vitre`() {
        assertTrue(
            "ChatScreen doit poser la source Haze sur le parent qui défile",
            chatScreen.contains("Modifier.onyxHazeSource(it)"),
        )
        // Et c'est bien la source **comptée** : depuis Haze 2, la vitre ne peut plus demander à
        // l'état s'il y a quelque chose à échantillonner (sa liste de zones est `internal`). Le
        // modificateur nu la laisserait peindre son voile sur du contenu parfaitement lisible.
        assertEquals(
            "aucune source n'est posée sans être comptée",
            0,
            Regex("Modifier\\.hazeSource\\(").findAll(chatScreen).count(),
        )
    }

    @Test
    fun `la barre est dessinee apres la source`() {
        val source = chatScreen.indexOf("Modifier.onyxHazeSource(it)")
        val bar = chatScreen.indexOf("            OnyxTopBar(")
        assertTrue("la source doit exister", source > 0)
        assertTrue("la barre doit exister", bar > 0)
        assertTrue(
            "la barre du haut doit être dessinée APRÈS la source Haze (barre=$bar, source=$source) : " +
                "au-dessous, elle ne peut ni flouter ni recevoir d'appui",
            bar > source,
        )
    }

    @Test
    fun `la barre porte la vitre du fil`() {
        // La barre peint la vitre du fil **avec la forme de son rôle** ([SurfaceRole.Bar], une
        // capsule) : la forme ne vient donc plus d'un appel qui pourrait repasser un rectangle, elle
        // vient de la table des rôles — c'est plus fort, pas plus faible.
        assertTrue(
            "OnyxTopBar doit peindre GlassSurface.THREAD_TOP_BAR avec la forme de son rôle",
            topBar.contains("liquidGlass(GlassSurface.THREAD_TOP_BAR, SurfaceRole.Bar.shape)"),
        )
        // Et plus l'ancien voile opaque, qui ne floutait rien.
        assertFalse(
            "la barre ne doit plus peindre `frostedBar`",
            topBar.contains(".frostedBar("),
        )
    }

    @Test
    fun `le compositeur porte sa vitre`() {
        assertTrue(
            "le compositeur doit peindre GlassSurface.COMPOSER",
            composer.contains("liquidGlass(GlassSurface.COMPOSER"),
        )
    }

    @Test
    fun `la teinte de la vitre n est pas opaque`() {
        // La teinte ne doit pas être assez opaque pour effacer ce qu'elle couvre : c'est par là que
        // le « verre perdu » revient, même quand la source est bien posée.
        val selected = read("core/src/main/java/com/onyx/app/ui/design/ModelSelection.kt")
        assertTrue("le fichier de sélection est lu", selected.isNotEmpty())
        val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            // Le chemin de peinture passe par l'accesseur **sans niveau** (`glassPaintTintAlpha`) :
            // c'est ce qui garantit que la mesure de fluidité ne rentre plus dans l'opacité peinte.
            "la vitre doit passer par le plancher de lisibilité, pas par une constante opaque",
            glass.contains("glassPaintTintAlpha(surface, dark, inks)"),
        )
        assertTrue(
            "le repli garde son voile plus opaque, et il est nommé",
            glass.contains("fun glassScrimAlpha("),
        )
    }
}
