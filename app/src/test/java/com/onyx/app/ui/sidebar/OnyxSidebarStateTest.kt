package com.onyx.app.ui.sidebar

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.SidebarContentMaxWidth
import com.onyx.app.ui.SidebarMinWidth
import com.onyx.app.ui.TwoPaneMinWidth
import com.onyx.app.ui.sidebarContentWidthFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le mode du volet suit la **largeur**, et chaque largeur garde **son** état.
 *
 * C'est l'exigence « je veux ça adapté à tous les formats d'écrans » : un téléphone étroit ouvre le
 * volet en surimpression, un pliable déplié ou une tablette le pose à côté du fil, et passer de
 * l'un à l'autre ne transporte pas l'ouverture — ouvrir la tablette ne laisse pas une
 * surimpression ouverte sur le téléphone, et la replier ne referme pas la surimpression.
 */
class OnyxSidebarStateTest {

    @Test
    fun `le mode suit la largeur, du telephone etroit a la tablette`() {
        // Étroit : un seul panneau, le volet recouvre le fil.
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(320.dp))
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(360.dp))
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(600.dp))
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(700.dp))
        // Large : le fil garde ses 360 dp et le volet est posé à côté.
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(800.dp))
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(900.dp))
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(1200.dp))
    }

    @Test
    fun `la surimpression demarre fermee, le volet pose demarre ouvert`() {
        val state = OnyxSidebarState()
        // Le mode par défaut est la surimpression : elle recouvre le fil, elle n'a rien à y faire.
        assertEquals(SidebarMode.COMPACT, state.mode)
        assertFalse(state.isOpen)

        state.updateMode(SidebarMode.DOCKED)
        assertTrue("posé, il ne recouvre rien : il est ouvert", state.isOpen)
    }

    @Test
    fun `un seul geste ouvre puis referme`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        assertFalse(state.isOpen)
        state.toggle()
        assertTrue(state.isOpen)
        state.toggle()
        assertFalse("le même appui referme : jamais deux gestes", state.isOpen)
    }

    @Test
    fun `chaque largeur garde son etat`() {
        val state = OnyxSidebarState()

        // Téléphone : on ouvre la surimpression.
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        assertTrue(state.isOpen)

        // Pliage/dépliage : la tablette a sa **propre** mémoire, ouverte par défaut.
        state.updateMode(SidebarMode.DOCKED)
        assertTrue(state.isOpen)
        state.close()
        assertFalse(state.isOpen)

        // Retour au téléphone : la surimpression a gardé son ouverture.
        state.updateMode(SidebarMode.COMPACT)
        assertTrue("la surimpression du téléphone n'a pas bougé", state.isOpen)

        // Et l'inverse : refermer le téléphone ne rouvre pas la tablette.
        state.close()
        state.updateMode(SidebarMode.DOCKED)
        assertFalse("le volet posé était resté replié", state.isOpen)
    }

    @Test
    fun `le volet pose garde ses bornes, jamais plus de 40 pour cent de la fenetre`() {
        // La colonne idéale de la référence : 360 dp. Au ras du seuil des deux panneaux, le volet
        // tombe à son plancher (320 dp), et il ne dépasse jamais 40 % de la fenêtre.
        fun near(d: androidx.compose.ui.unit.Dp, value: Float) = assertTrue(
            "attendu ≈ $value, lu ${d.value}",
            kotlin.math.abs(d.value - value) < 0.01f,
        )
        near(sidebarDockedWidthFor(800.dp), 320f)
        near(sidebarDockedWidthFor(820.dp), 328f)
        near(sidebarDockedWidthFor(900.dp), 360f)
        near(sidebarDockedWidthFor(1024.dp), 360f)
        near(sidebarDockedWidthFor(2400.dp), 360f)
        assertTrue(sidebarDockedWidthFor(820.dp) <= 820.dp * 0.40f)
    }

    @Test
    fun `la surimpression prend toute la fenetre, et aucun second panneau n apparait`() {
        // Relevé sur la vidéo du propriétaire : à 86 % de la fenêtre, il restait une **bande du
        // fil** au bord droit, dont le pied (le compositeur) se lisait comme une seconde copie du
        // volet. Le volet en surimpression prend donc la fenêtre entière.
        assertEquals(360.dp, sidebarOverlayWidthFor(360.dp))
        assertEquals(397.dp, sidebarOverlayWidthFor(397.dp))
        assertEquals(600.dp, sidebarOverlayWidthFor(600.dp))
        assertEquals(200.dp, sidebarOverlayWidthFor(200.dp))
        // Il reste donc **zéro** place à côté du volet, quel que soit le palier.
        listOf(320.dp, 360.dp, 397.dp, 411.dp, 420.dp, 600.dp, 799.dp)
            .forEach { width ->
                assertEquals(
                    "$width : il ne doit rien rester à côté du volet",
                    0.dp,
                    width - sidebarOverlayWidthFor(width),
                )
            }
    }

    /**
     * Le seuil des deux panneaux, **prouvé sur les deux largeurs de l'appareil du propriétaire** :
     * 411 dp (Honor Magic V3 replié) et 862 dp (déplié). C'est ce seuil qui décide que la largeur
     * **élargie** d'un écran (400…560 dp) ne s'applique jamais sur un téléphone.
     */
    @Test
    fun `le seuil deux panneaux est prouve a 411 et 862 dp`() {
        // 411 dp — replié : un seul panneau, le volet recouvre la fenêtre entière.
        assertEquals(SidebarMode.COMPACT, sidebarModeFor(411.dp))
        assertEquals(411.dp, sidebarWidthForMode(411.dp, SidebarMode.COMPACT))
        assertEquals(
            "la largeur élargie d'un écran ne s'applique pas en surimpression",
            411.dp,
            sidebarWidthForMode(411.dp, SidebarMode.COMPACT, content = true),
        )
        assertTrue("411 dp reste sous le seuil", 411.dp < TwoPaneMinWidth)

        // 862 dp — déplié : deux panneaux, et c'est **là** que la largeur d'écran sert.
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(862.dp))
        assertTrue("862 dp passe le seuil", 862.dp >= TwoPaneMinWidth)
        assertEquals(
            "déplié, le volet portant un écran prend la largeur d'un écran",
            sidebarContentWidthFor(862.dp),
            sidebarWidthForMode(862.dp, SidebarMode.DOCKED, content = true),
        )
        assertTrue(
            "déplié, le fil garde de quoi se lire",
            862.dp - sidebarWidthForMode(862.dp, SidebarMode.DOCKED, content = true) >= 0.dp,
        )
    }

    /**
     * Le **second point de mesure** du lot 3 : à 862 dp (pliable déplié), le volet n'est plus une
     * surimpression pleine largeur mais une colonne **posée** — bornée à 320…400 dp et à 40 % de la
     * fenêtre. C'est aussi la preuve que la **bande de 340 dp** d'autrefois n'existe plus.
     */
    @Test
    fun `le pliable deplie a 862 dp mesure un volet pose borne, pas une surimpression`() {
        assertEquals(SidebarMode.DOCKED, sidebarModeFor(862.dp))
        val volet = sidebarDockedWidthFor(862.dp)
        assertTrue("$volet : volet sous le plancher", volet >= 320.dp)
        assertTrue("$volet : volet au-dessus du plafond", volet <= 400.dp)
        assertTrue("le volet ne dépasse pas 40 %", volet.value <= 862.dp.value * 0.40f + 0.001f)
        // Le fil garde ses 360 dp : c'est ce qui autorise le deux-panneaux.
        assertTrue("le fil garde 360 dp au moins", 862.dp - volet >= 360.dp)
        // À 411 dp, la même fonction change de nature : surimpression pleine largeur, bande nulle.
        assertEquals(411f, sidebarOverlayWidthFor(411.dp).value, 0.01f)
        assertEquals(0f, (411.dp - sidebarOverlayWidthFor(411.dp)).value, 0.01f)
        assertTrue("l'ancienne bande de 340 dp n'existe plus", sidebarOverlayWidthFor(411.dp) > 340.dp)
    }

    /**
     * Le point resté douteux : un glissement franc fermait le volet **entier** en sautant l'écran
     * qu'il portait, là où le bouton système, lui, dépilait d'abord. Les trois sorties — bouton,
     * geste du bord et appui sur le voile — passent maintenant par `sidebarBackStep` :
     * « écran → liste → fermeture », un niveau à la fois.
     */
    @Test
    fun `le geste de bord depile le volet avant de le fermer, comme le bouton systeme`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        state.pushDestination("SKILLS")

        sidebarBackStep(state)
        assertEquals("l'écran est dépilé, pas le volet fermé", null, state.destination)
        assertTrue("le volet reste ouvert sur sa liste", state.isOpen)

        sidebarBackStep(state)
        assertFalse("un second geste referme le volet", state.isOpen)

        // Sur la liste déjà ouverte, un seul geste le referme d'emblée.
        state.open()
        sidebarBackStep(state)
        assertFalse(state.isOpen)
    }

    @Test
    fun `la largeur du volet depend du mode`() {
        assertEquals(sidebarOverlayWidthFor(400.dp), sidebarWidthForMode(400.dp, SidebarMode.COMPACT))
        assertEquals(sidebarDockedWidthFor(900.dp), sidebarWidthForMode(900.dp, SidebarMode.DOCKED))
    }

    // -----------------------------------------------------------------------------------------
    // La navigation **interne** au volet : une tuile ouvre son écran dedans, le retour dépile.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `une tuile ouvre son ecran dans le volet, et le volet reste ouvert`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        assertEquals("le volet montre sa liste au départ", null, state.destination)
        assertFalse(state.isNavigating)

        // C'est la demande du propriétaire : « quand je clique sur Aperçus, on reste dans le volet ».
        state.pushDestination("INSIGHTS")
        assertEquals("INSIGHTS", state.destination)
        assertTrue("le volet reste ouvert", state.isOpen)
        assertTrue(state.isNavigating)
    }

    @Test
    fun `le retour depile un niveau a la fois, puis ferme le volet`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        state.pushDestination("SETTINGS")
        state.pushDestination("SKILLS")

        // Écran → écran précédent : un seul niveau, le volet reste ouvert.
        assertTrue(state.popDestination())
        assertEquals("SETTINGS", state.destination)
        assertTrue("le volet est encore ouvert", state.isOpen)

        // Écran → liste : le volet reste ouvert, il montre sa racine.
        assertTrue(state.popDestination())
        assertEquals(null, state.destination)
        assertFalse(state.isNavigating)
        assertTrue("le volet est encore ouvert sur sa liste", state.isOpen)

        // Liste → fermeture : c'est la marche **suivante**, et le retour n'a plus rien à dépiler.
        assertFalse("il n'y a plus rien à dépiler", state.popDestination())
        state.close()
    }

    @Test
    fun `la recherche est un mode a part, distinct de l ouverture du volet`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        assertFalse("le volet s'ouvre sur la liste, pas sur la recherche", state.searching)

        // Le premier appui sur le champ entre en recherche — l'ouverture, elle, ne bouge pas.
        state.enterSearch()
        assertTrue(state.searching)
        assertTrue("entrer en recherche ne referme pas le volet", state.isOpen)

        // **Sortir de la recherche ne referme pas le volet** : c'est le geste suivant qui le fait.
        // C'est l'ordre que la référence suit, et ce que le lot verrouille.
        state.exitSearch()
        assertFalse(state.searching)
        assertTrue("la sortie de la recherche laisse le volet ouvert", state.isOpen)

        // L'autre sens, pour que les deux états ne se confondent jamais : refermer le volet pendant
        // la recherche ne quitte pas la recherche (la requête survit au repli).
        state.enterSearch()
        state.close()
        assertTrue("refermer le volet ne quitte pas la recherche", state.searching)
        assertFalse(state.isOpen)
    }

    @Test
    fun `le volet repart de sa racine quand on le quitte pour une conversation`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.DOCKED)
        state.pushDestination("KANBAN")
        state.clearDestinations()
        assertEquals(null, state.destination)
        assertTrue("le volet posé ne s'est pas refermé", state.isOpen)
    }

    @Test
    fun `deux appuis sur la meme tuile n empilent pas deux fois le meme ecran`() {
        val state = OnyxSidebarState()
        state.pushDestination("SKILLS")
        state.pushDestination("SKILLS")
        // Un seul niveau : sinon la flèche demanderait deux appuis pour revenir à la liste, et le
        // geste « un niveau à la fois » se lirait comme un retour mort.
        assertTrue(state.popDestination())
        assertEquals(null, state.destination)
    }

    @Test
    fun `la pile du volet se depile dans l ordre inverse de l empilement`() {
        val state = OnyxSidebarState()
        listOf("SETTINGS", "SKILLS", "SKILL_EDITOR").forEach(state::pushDestination)
        assertEquals("SKILL_EDITOR", state.destination)
        assertTrue(state.popDestination()); assertEquals("SKILLS", state.destination)
        assertTrue(state.popDestination()); assertEquals("SETTINGS", state.destination)
        assertTrue(state.popDestination()); assertEquals(null, state.destination)
        assertFalse(state.popDestination())
    }

    @Test
    fun `la navigation du volet survit a un repliage et a une recréation`() {
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        state.pushDestination("SETTINGS")
        state.pushDestination("INSIGHTS")

        // Repliage : la largeur change, le mode change, **la navigation ne se perd pas** — et elle
        // ne rejoue pas non plus (la pile est celle qu'on a laissée, pas celle du départ).
        state.updateMode(SidebarMode.DOCKED)
        assertEquals("INSIGHTS", state.destination)
        state.updateMode(SidebarMode.COMPACT)
        assertEquals("INSIGHTS", state.destination)

        // Recréation d'activité : ce qui a été mémorisé se **restaure** à l'identique.
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(OnyxSidebarState.Saver) { scope.save(state) }
        val restored = OnyxSidebarState.Saver.restore(saved!!)!!
        assertEquals(state.destination, restored.destination)
        assertEquals(state.mode, restored.mode)
        assertEquals(state.isOpen, restored.isOpen)
        assertTrue(restored.popDestination())
        assertEquals("SETTINGS", restored.destination)
    }

    @Test
    fun `le volet portant un ecran prend la largeur d un ecran, sans ecraser le fil`() {
        // La colonne de la liste (320…400 dp) écraserait un écran : porté, le volet s'élargit.
        assertEquals(sidebarContentWidthFor(900.dp), sidebarWidthForMode(900.dp, SidebarMode.DOCKED, content = true))
        // Jamais moins de 400 dp — c'est la largeur où ces écrans se lisent —
        assertTrue(sidebarContentWidthFor(800.dp) >= 400.dp)
        // jamais plus de 560,
        assertTrue(sidebarContentWidthFor(2400.dp) <= 560.dp)
        // et jamais au point de retirer le fil quand la fenêtre peut le garder.
        assertTrue("le fil garde ses 360 dp", 900.dp - sidebarContentWidthFor(900.dp) >= 360.dp)
        assertTrue("le volet porte un écran plus large que la liste", sidebarContentWidthFor(900.dp) > sidebarDockedWidthFor(900.dp))
        // Et sur un téléphone, la surimpression garde **sa** largeur : le volet ne bouge pas.
        assertEquals(
            sidebarOverlayWidthFor(420.dp),
            sidebarWidthForMode(420.dp, SidebarMode.COMPACT, content = true),
        )
    }

    @Test
    fun `la largeur du volet portant un ecran reste dans la fenetre, sur tous les paliers`() {
        // Du téléphone à la tablette large : jamais plus large que la fenêtre, toujours au moins la
        // largeur d'une colonne, et jamais au-dessus du plafond d'un écran.
        listOf(360.dp, 420.dp, 600.dp, 720.dp, 800.dp, 838.dp, 900.dp, 933.dp, 1024.dp, 1280.dp, 2400.dp)
            .forEach { width ->
                val content = sidebarContentWidthFor(width)
                assertTrue("$width : volet plus large que la fenêtre", content <= width)
                assertTrue("$width : volet sous la colonne de la liste", content >= SidebarMinWidth)
                assertTrue("$width : volet au-dessus du plafond d'écran", content <= SidebarContentMaxWidth)
            }
    }

    @Test
    fun `le mode recherche survit au changement de largeur, comme la requete`() {
        // Un pliable qu'on déplie pendant qu'on cherche : la recherche reste ouverte — c'est le même
        // volet, dessiné autrement.
        val state = OnyxSidebarState()
        state.updateMode(SidebarMode.COMPACT)
        state.open()
        state.enterSearch()
        state.updateMode(SidebarMode.DOCKED)
        assertTrue(state.searching)
        assertTrue(state.isOpen)
        state.updateMode(SidebarMode.COMPACT)
        assertTrue(state.searching)
    }
}
