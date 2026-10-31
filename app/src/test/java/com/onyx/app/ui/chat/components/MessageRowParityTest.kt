package com.onyx.app.ui.chat.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRowParityTest {
    @Test
    fun `les metriques utilisent caption2 et des chiffres tabulaires`() {
        val theme = ResolvedTheme.fallback()
        val style = replyMetricsTextStyle(theme)

        assertEquals(11.sp, style.fontSize)
        assertEquals("tnum", style.fontFeatureSettings)
        assertEquals(theme.caption2.fontFamily, style.fontFamily)
        assertEquals(theme.tertiaryText, style.color)
    }

    @Test
    fun `la rangee d actions a une seule taille de glyphe, une cible de 44 dp, un espacement egal`() {
        // « icônes, tailles, placement » : les quatre cases partagent la même taille de glyphe et la
        // même cible tactile, et l'espacement est unique — donc régulier.
        assertEquals(20.dp, ReplyActionGlyphSize)
        assertEquals(44.dp, ReplyActionTouchTarget)
        assertEquals(2.dp, ReplyActionSpacing)
        // Le retrait aligne le premier glyphe sur le bord du texte : (44 − 20) / 2 = 12.
        assertEquals(-12f, ReplyActionRowLeading.value, 0.001f)
        assertEquals(
            -(ReplyActionTouchTarget.value - ReplyActionGlyphSize.value) / 2f,
            ReplyActionRowLeading.value,
            0.001f,
        )
    }

    @Test
    fun `la rangee porte les actions de la reference, dans son ordre`() {
        val slots = replyActionSlots(
            copied = false, isReading = false,
            canBranch = true, canRestore = true, canRegenerate = true, canRead = true,
        )
        // La **bifurcation** ouvre la rangée : c'est la première à gauche, avant régénérer, lire à
        // voix haute et copier — comme la référence. « Restaurer », qui n'existe que chez nous,
        // garde sa case et son rang derrière elle.
        assertEquals(
            listOf(
                ReplyAction.Branch, ReplyAction.Restore, ReplyAction.Regenerate,
                ReplyAction.Read, ReplyAction.Copy,
            ),
            slots.map { it.action },
        )
        assertEquals("Branch from here", slots[0].label)
        assertEquals("Restore to here", slots[1].label)
        assertEquals("Regenerate", slots[2].label)
        assertEquals("Read aloud", slots[3].label)
        assertEquals("Copy reply", slots[4].label)
        // Le glyphe de la bifurcation est celui de la référence (Lucide `git-branch`).
        assertEquals(com.onyx.app.ui.design.Symbols.Branch, slots[0].symbol)
    }

    @Test
    fun `l etat de la lecture et de la copie change le glyphe sans deplacer la case`() {
        val idle = replyActionSlots(
            copied = false, isReading = false,
            canBranch = true, canRestore = true, canRegenerate = true, canRead = true,
        )
        val active = replyActionSlots(
            copied = true, isReading = true,
            canBranch = true, canRestore = true, canRegenerate = true, canRead = true,
        )
        // Mêmes actions, dans le même ordre : les cinq positions ne bougent pas.
        assertEquals(idle.map { it.action }, active.map { it.action })
        assertEquals(idle.size, active.size)
        // Le dessin change dans la case (enceinte → carré ; feuilles → coche) …
        assertNotEquals(idle[3].symbol, active[3].symbol)
        assertNotEquals(idle[4].symbol, active[4].symbol)
        assertEquals("Stop reading", active[3].label)
        assertEquals("Copied", active[4].label)
        // … et la lecture reste active, dans sa case.
        assertTrue(active[3].enabled)
    }

    @Test
    fun `une action indisponible garde sa case grisee au lieu de disparaitre`() {
        val slots = replyActionSlots(
            copied = false, isReading = false,
            canBranch = false, canRestore = false, canRegenerate = false, canRead = false,
        )
        assertEquals(5, slots.size)
        assertFalse(slots[0].enabled)
        assertFalse(slots[1].enabled)
        assertFalse(slots[2].enabled)
        assertFalse(slots[3].enabled)
        // La copie ne dépend d'aucun rappel : elle reste toujours active.
        assertTrue(slots[4].enabled)
    }

    @Test
    fun `le raisonnement est ferme par defaut, s'annonce, et se fige sous reduce motion`() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()

        // Fermé par défaut (même une réponse courte) : il ne s'ouvre pas tout seul.
        assertTrue(source.contains("val open = chosen ?: false"))
        // Reduce Motion fige l'ouverture du raisonnement.
        assertTrue(source.contains("EnterTransition.None"))
        assertTrue(source.contains("ExitTransition.None"))

        // La ligne porte l'icône, puis l'annonce en petites capitales, puis la durée, puis le
        // chevron — dans cet ordre.
        val header = source.substringAfter("private fun ThinkingFold(")
            .substringBefore("private fun ReasoningDetail(")
        val icon = header.indexOf("Symbols.Brain")
        val label = header.indexOf("OnyxLocalization[\"Model reasoning\"]")
        val duration = header.indexOf("reasoningSeconds(message)")
        val chevron = header.indexOf("Symbols.ChevronRight")
        assertTrue("icône absente", icon >= 0)
        assertTrue("annonce absente", label >= 0)
        assertTrue("durée absente", duration >= 0)
        assertTrue("chevron absent", chevron >= 0)
        assertTrue("icône avant l'annonce", icon < label)
        assertTrue("annonce avant la durée", label < duration)
        assertTrue("durée avant le chevron", duration < chevron)
    }

    @Test
    fun `la duree de reflexion se garde, et vaut au moins une seconde`() {
        assertEquals(6L, reasoningSeconds(reply(started = 0L, ended = 6_000L))!!)
        // Sous la seconde, « 0 s » se lirait comme rien : une réflexion a quand même pris un moment.
        assertEquals(1L, reasoningSeconds(reply(started = 1_000L, ended = 1_200L))!!)
        // Une réponse enregistrée avant qu'on garde les temps n'affiche pas de durée.
        assertNull(reasoningSeconds(reply()))
    }

    @Test
    fun `le raisonnement est lisible et les icones de la rangee tiennent leur seuil`() {
        fun channel(value: Float): Double =
            if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        fun luminance(c: androidx.compose.ui.graphics.Color): Double =
            0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        fun ratio(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color): Double {
            val a = luminance(fg)
            val b = luminance(bg)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }
        fun over(fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) =
            androidx.compose.ui.graphics.Color(
                red = fg.red * fg.alpha + bg.red * (1 - fg.alpha),
                green = fg.green * fg.alpha + bg.green * (1 - fg.alpha),
                blue = fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
            )

        val light = ResolvedTheme.fallback()
        val themes = listOf(
            light to androidx.compose.ui.graphics.Color.White,
            ResolvedTheme.resolve(light.theme, dark = true, fonts = light.fonts) to androidx.compose.ui.graphics.Color.Black,
        )
        themes.forEachIndexed { index, (theme, page) ->
            // Un texte : 4,5 au minimum (le raisonnement et son libellé).
            val text = ratio(over(reasoningInk(theme), page), page)
            assertTrue("raisonnement ${if (index == 0) "clair" else "sombre"} : $text", text >= 4.5)
            // Une icône : 3 au minimum (les quatre actions au repos).
            val icon = ratio(over(theme.secondaryText, page), page)
            assertTrue("icônes ${if (index == 0) "clair" else "sombre"} : $icon", icon >= 3.0)
        }
    }

    @Test
    fun `le retrait negatif utilise offset car padding le refuse`() {
        val source = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
        assertTrue(source.contains("Modifier.offset(x = ReplyActionRowLeading)"))
        assertFalse(source.contains("Modifier.padding(start = ReplyActionRowLeading)"))
    }

    @Test
    fun `seul le prefixe du prompt est en gras`() {
        val prompt = promptLineText("❯ ", "pwd")
        val bold = prompt.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }

        assertEquals("❯ pwd", prompt.text)
        assertEquals(1, bold.size)
        assertEquals(0, bold.single().start)
        assertEquals(2, bold.single().end)
    }

    private fun reply(started: Long? = null, ended: Long? = null): ChatMessage = ChatMessage(
        id = "a1",
        role = Role.ASSISTANT,
        text = "ok",
        createdAt = 0L,
        reasoning = "…",
        reasoningStartedAt = started,
        reasoningEndedAt = ended,
    )
}
