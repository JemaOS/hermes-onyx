package com.onyx.app.ui.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le geste d'une rangée se classe par son AXE dominant**, et une seule fois — puis ne **révèle**
 * sa pilule qu'au-delà d'une distance minimale.
 *
 * C'est le correctif du défaut signalé : « quand je défile vers le bas ou haut j'ai la barre archiver
 * ou pin qui apparaît, c'est trop sensible ; normalement c'est que le slide vers droite ou gauche
 * horizontal ». La classification ne lisait que `x`, si bien qu'un défilement vertical au doigt qui
 * dérive latéralement déclenchait l'action.
 *
 * Ce fichier mesure **les deux règles séparément**, sans appareil :
 *
 *  1. l'**axe** — un geste vertical (haut **et** bas), un geste horizontal (gauche **et** droite), et
 *     un geste oblique à 30° ; l'axe est tranché **au seuil système**, sans quoi le glissement du
 *     volet démarrerait avant la rangée et prendrait le doigt ;
 *  2. la **révélation** — la rangée ne bouge pas d'un pixel avant la distance minimale, puis suit.
 *
 * Le seuil système utilisé ici est celui d'un téléphone (`touchSlop`, ~8 dp) ; il est passé en
 * paramètre pour que le test dise **la** règle et pas une densité.
 */
class SidebarSwipeAxisTest {

    private val slop = 8f
    private val minTravel = 24f

    private fun axis(dx: Float, dy: Float) = sidebarSwipeAxis(dx, dy, slop)

    // — La table des seuils : ce que le rapport annonce est ce que le code applique —

    @Test
    fun `la dominance d axe exige un facteur un et demi`() {
        assertEquals(1.5f, SidebarSwipeAxisDominance, 0.0001f)
    }

    @Test
    fun `la distance minimale tient entre le seuil systeme et le geste franc`() {
        // Plus longue que le seuil système (la révélation ne part pas au premier pixel), plus courte
        // que le seuil du geste franc (sinon l'action serait inatteignable).
        assertTrue("au-dessus du seuil système", SidebarSwipeAxisMin.value > 8f)
        assertTrue("sous le geste franc", SidebarSwipeAxisMin.value < SidebarSwipeThreshold.value)
    }

    // — Rien n'est révélé tant que l'axe n'est pas tranché —

    @Test
    fun `au repos l axe n est pas tranche`() {
        assertEquals(SidebarSwipeAxis.UNDECIDED, axis(0f, 0f))
        assertEquals("un frémissement des deux côtés", SidebarSwipeAxis.UNDECIDED, axis(3f, -4f))
    }

    @Test
    fun `l axe est tranche des le seuil systeme, pas plus tard`() {
        // C'est l'arbitrage : la rangée consomme le doigt sur l'événement même où le glissement du
        // volet le revendiquerait. Un classement plus tardif laisserait le volet partir en premier.
        assertEquals(SidebarSwipeAxis.HORIZONTAL, axis(9f, 0f))
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(0f, 9f))
        assertEquals(SidebarSwipeAxis.UNDECIDED, axis(7f, 7f))
    }

    // — Le défilement VERTICAL, dans les deux sens, n'engage jamais une action —

    @Test
    fun `un glissement vertical vers le haut est un defilement`() {
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(0f, -400f))
    }

    @Test
    fun `un glissement vertical vers le bas est un defilement`() {
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(0f, 400f))
    }

    @Test
    fun `le defilement signale par le proprietaire reste au defilement`() {
        // Le geste réellement reproduit à l'appareil : surtout vertical, avec la dérive latérale
        // d'un doigt qui « part de travers » — il épinglait avant le correctif.
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(dx = 220f, dy = -500f))
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(dx = -220f, dy = 500f))
    }

    // — Le glissement HORIZONTAL, dans les deux sens, engage l'action —

    @Test
    fun `un glissement horizontal vers la droite engage la rangee`() {
        assertEquals(SidebarSwipeAxis.HORIZONTAL, axis(200f, 0f))
    }

    @Test
    fun `un glissement horizontal vers la gauche engage la rangee`() {
        assertEquals(SidebarSwipeAxis.HORIZONTAL, axis(-200f, 0f))
    }

    @Test
    fun `un geste horizontal avec une petite derive verticale reste horizontal`() {
        assertEquals(SidebarSwipeAxis.HORIZONTAL, axis(dx = 200f, dy = -60f))
        assertEquals(SidebarSwipeAxis.HORIZONTAL, axis(dx = -200f, dy = 60f))
    }

    // — Le geste OBLIQUE : un verdict explicite, pas un entre-deux —

    @Test
    fun `un geste oblique a trente degres de la verticale est un defilement`() {
        // 30° d'inclinaison par rapport à la verticale : |dx| / |dy| = tan(30°) ≈ 0,577.
        val dy = 100f
        val dx = dy * 0.577f
        assertEquals(
            "un geste oblique reste au défilement : c'est le doigt qui part de travers",
            SidebarSwipeAxis.VERTICAL,
            axis(dx, dy),
        )
    }

    @Test
    fun `un geste oblique a trente degres de l horizontale engage la rangee`() {
        // Vue depuis l'autre axe : 30° sous l'horizontale donne |dx| / |dy| = cot(30°) ≈ 1,73.
        val dx = 100f
        val dy = dx / 1.732f
        assertEquals(
            "franchement horizontal, donc revendiqué",
            SidebarSwipeAxis.HORIZONTAL,
            axis(dx, dy),
        )
    }

    @Test
    fun `un geste a quarante cinq degres est un defilement`() {
        // L'égalité parfaite n'est pas une dominance : le doute profite au défilement.
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(120f, 120f))
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(-120f, 120f))
    }

    @Test
    fun `la dominance se mesure sur la course cumulee, pas sur le dernier evenement`() {
        // Même rapport d'axes à deux échelles : le verdict ne dépend pas de la taille du dernier pas,
        // seulement de la course cumulée — un geste interrompu a déjà parcouru sa course.
        assertEquals("même rapport, à petite échelle", SidebarSwipeAxis.HORIZONTAL, axis(30f, 18f))
        assertEquals("même rapport, à grande échelle", SidebarSwipeAxis.HORIZONTAL, axis(150f, 90f))
        // Un rapport qui bascule, en revanche, change le verdict : c'est bien l'axe qui décide.
        assertEquals(SidebarSwipeAxis.VERTICAL, axis(18f, 30f))
    }

    // — La révélation : rien avant la distance minimale, puis la course au-delà —

    @Test
    fun `la rangee ne revele rien avant la distance minimale`() {
        assertEquals(0f, sidebarSwipeRevealPx(0f, minTravel), 0f)
        assertEquals("sous la distance minimale, la rangée est immobile", 0f, sidebarSwipeRevealPx(20f, minTravel), 0f)
        assertEquals("exactement à la distance minimale, rien n'est encore découvert", 0f, sidebarSwipeRevealPx(24f, minTravel), 0f)
    }

    @Test
    fun `au-dela de la distance minimale la rangee suit le doigt depuis zero`() {
        // La pose part de zéro là où la distance minimale est atteinte : la rangée ne saute pas.
        assertEquals(6f, sidebarSwipeRevealPx(30f, minTravel), 0.0001f)
        assertEquals(26f, sidebarSwipeRevealPx(50f, minTravel), 0.0001f)
        // Et le sens est porté par le signe du geste.
        assertEquals(-26f, sidebarSwipeRevealPx(-50f, minTravel), 0.0001f)
    }
}
