package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les mesures d'encre d'une carte se font une fois, pas une fois par carte.**
 *
 * Le propriétaire : « avant c'était fluide, il y a un truc mal optimisé c'est tout ». Le truc, c'était
 * le profil de la bande d'encre ([BlocConversation.inkZoneRowAlphas]) : **un rang par pixel**, et
 * chaque rang interrogeait [BlocConversation.inkZoneAlpha] — qui balaie les pas d'opacité, les deux
 * voiles et les quatre fonds possibles de la plaque, pour trois encres. Une carte payait donc des
 * dizaines de milliers de rapports de contraste **pour un chiffre qui ne dépend que de l'apparence**,
 * et chaque carte qui entrait dans la liste les repayait : c'est ce qui hachait le défilement.
 *
 * Le verrou ne mesure pas le temps (une machine de test n'est pas un téléphone) : il vérifie que le
 * **même objet** sort du cache pour la même apparence — donc que la mesure n'a lieu qu'une fois — et
 * que les valeurs restent dans les bornes de la règle.
 */
class BlocInkMemoTest {

    private val theme = ResolvedTheme(OnyxTheme.entries.first(), OnyxTheme.entries.first().palette, dark = true, OnyxFonts.System)
    private val other = ResolvedTheme(OnyxTheme.entries.first(), OnyxTheme.entries.first().palette, dark = false, OnyxFonts.System)

    @Test
    fun `le profil de la bande est partage entre les cartes`() {
        val first = BlocConversation.inkZoneRowAlphas(theme, bandPx = 44f, density = 2.75f)
        val second = BlocConversation.inkZoneRowAlphas(theme, bandPx = 44f, density = 2.75f)
        assertSame("le profil doit être mesuré une seule fois", first, second)
        assertTrue("et il a bien un rang par pixel de la bande", first.size in 40..48)
        // Bornes de la règle : la bande va de zéro au bord à l'opacité pleine à `InkZoneFade`.
        assertTrue("le bord est transparent", first.first() < 0.01f)
        assertTrue("le plateau est l'opacité mesurée", first.last() <= BlocConversation.inkZoneAlpha(theme))
    }

    @Test
    fun `les encres et les fonds sont partages eux aussi`() {
        assertSame("les fonds ne sont pas remesurés", BlocConversation.grounds(theme), BlocConversation.grounds(theme))
        val ink = BlocConversation.ink(theme, theme.textColor)
        assertEquals("et l'encre non plus (même valeur, donc même mesure)", ink, BlocConversation.ink(theme, theme.textColor))
        val alpha = BlocConversation.inkZoneAlpha(theme)
        assertEquals("la mesure est stable", alpha, BlocConversation.inkZoneAlpha(theme), 0f)
        assertTrue("et bornée", alpha in 0f..1f)
    }

    @Test
    fun `la mesure partagee coute ce qu elle coute, une fois`() {
        // Mesure de la dépense évitée (elle s'affiche dans le rapport du lot).
        val cold = (1..20).map { i ->
            val fresh = ResolvedTheme(OnyxTheme.entries.first(), OnyxTheme.entries.first().palette, dark = i % 2 == 0, OnyxFonts.System, fontScale = 1f + i / 1000f)
            val started = System.nanoTime()
            BlocConversation.inkZoneRowAlphas(fresh, 44f, 2.75f)
            System.nanoTime() - started
        }.average() / 1_000_000.0
        val warm = kotlin.system.measureNanoTime {
            repeat(20) { BlocConversation.inkZoneRowAlphas(theme, 44f, 2.75f) }
        } / 20.0 / 1_000_000.0
        println("BANC · profil d'une carte : mesure froide = %.3f ms, cache = %.4f ms".format(cold, warm))
        assertTrue("le cache est au moins dix fois moins cher qu'une mesure", warm * 10 < cold)
    }

    @Test
    fun `chaque apparence a sa propre mesure`() {
        assertTrue(
            "la bande sombre et la bande claire ne partagent pas le même profil",
            BlocConversation.inkZoneRowAlphas(theme, 44f, 2.75f) !==
                BlocConversation.inkZoneRowAlphas(other, 44f, 2.75f),
        )
    }
}
