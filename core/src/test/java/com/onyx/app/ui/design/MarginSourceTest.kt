package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le verrou de l'**unification des marges** : deux lots ont posé la même règle de barre chacun de
 * son côté (`SafeBarSpacing` en dp, `SystemMargins` en dp **et** en pixels). Le calcul ne vit plus
 * que dans [SystemMargins] ; [SafeBarSpacing] n'est plus qu'un nom qui l'appelle.
 *
 * Ce test le prouve de l'extérieur : pour tous les encarts réellement rencontrés, l'alias rend
 * **exactement** la valeur de la source unique. Si quelqu'un réécrit un `+ 8` dans l'alias, la
 * marge se remet à diverger et ce test casse.
 */
class MarginSourceTest {

    /** Les encarts réels : gestes, trois boutons, pliable déplié, et l'absence de bande. */
    private val insetsDp = listOf(0, 24, 32, 48, 51, 135)

    @Test
    fun `l espace visuel est la marge nette de la source unique`() {
        assertEquals(SystemMargins.ClearanceDp, SafeBarSpacing.GapDp)
    }

    @Test
    fun `la marge basse de l alias est celle de SystemMargins`() {
        for (inset in insetsDp) {
            assertEquals(
                "bas, encart de $inset dp",
                SystemMargins.barBottomDp(inset),
                SafeBarSpacing.bottomMarginDp(inset),
            )
        }
        // Et avec un espace visuel explicite, l'alias transmet bien son paramètre.
        assertEquals(SystemMargins.barBottomDp(48, gapDp = 12), SafeBarSpacing.bottomMarginDp(48, gapDp = 12))
    }

    @Test
    fun `la marge haute de l alias est celle de SystemMargins`() {
        for (inset in insetsDp) {
            assertEquals(
                "haut, encart de $inset dp",
                SystemMargins.barTopDp(inset),
                SafeBarSpacing.topMarginDp(inset),
            )
        }
    }

    @Test
    fun `le verdict de l alias est celui de SystemMargins`() {
        for (inset in insetsDp) {
            for (edge in listOf(0, 8, 24, 32, 48, 56, 135)) {
                assertEquals(
                    "bord de $edge dp sous un encart de $inset dp",
                    SystemMargins.clearsBand(edge, inset),
                    SafeBarSpacing.clearsSystemBottom(edge, inset),
                )
                assertEquals(
                    SystemMargins.clearsBand(edge, inset),
                    SafeBarSpacing.clearsSystemTop(edge, inset),
                )
            }
        }
    }

    /**
     * Les deux unités disent la même chose : à une densité de 1, la règle en dp et la règle en
     * pixels donnent le même nombre — c'est ce qui autorise un seul calcul pour les lecteurs plein
     * écran (pixels) et pour les barres (dp). Le haut garde sa nuance, elle aussi unique : un
     * contenu qui n'a pas le rembourrage d'une barre — la croix d'un lecteur — ajoute la marge
     * nette ([SystemMargins.topPx]), une barre non ([SystemMargins.barTopDp]).
     */
    @Test
    fun `les deux unites concordent a une densite de 1`() {
        val clearance = SystemMargins.ClearanceDp
        for (inset in insetsDp) {
            assertEquals(SystemMargins.barBottomDp(inset), SystemMargins.bottomPx(inset, density = 1f))
            assertEquals(
                SystemMargins.barTopDp(inset) + clearance,
                SystemMargins.topPx(inset, density = 1f),
            )
        }
    }
}
