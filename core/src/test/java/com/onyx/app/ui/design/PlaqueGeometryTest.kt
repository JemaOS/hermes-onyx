package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La géométrie de la plaque du bas : 140 dp, et rien d'autre.**
 *
 * Le défaut signalé est un chiffre : **166 dp** mesurés là où la planche en demande **140**. Un
 * chiffre ne se corrige pas par une intention, il se corrige par une somme qui ne peut pas dériver.
 * Ce test relit donc la **même** table que l'écran ([PlaqueGeometry]) et vérifie :
 *
 *  - la hauteur est la somme des deux rangées, et elle vaut 140 ;
 *  - le filet de séparation **ne prend pas de place** — sans quoi la plaque ferait 141, ce qui est
 *    exactement le genre d'écart d'un point qui ne se voit pas dans un calcul et se voit à l'écran ;
 *  - les quatre valeurs que la planche déclare (rayon 28, marge 16, filet 1, arête 1,5) sont celles
 *    du socle et de l'échelle de la maison — pas des nombres écrits à côté ;
 *  - la voie des commandes flottantes ne loge plus que la pastille de défilement : elle rend au fil
 *    les 12 dp que la cible et l'écart de la poignée à chevron réservaient.
 */
class PlaqueGeometryTest {

    @Test
    fun `la plaque vaut 140 dp, et c'est la somme de ses deux rangees`() {
        assertEquals(64.dp, PlaqueGeometry.FieldRowHeight)
        assertEquals(76.dp, PlaqueGeometry.TabRowHeight)
        assertEquals(
            "la plaque est la somme de ses deux rangées",
            PlaqueGeometry.FieldRowHeight + PlaqueGeometry.TabRowHeight,
            PlaqueGeometry.Height,
        )
        assertEquals("la cible de la planche : 379 × 140", 140.dp, PlaqueGeometry.Height)
    }

    @Test
    fun `le filet interne ne prend pas de place dans la somme`() {
        // C'est la correction d'un écart d'un point : `.ask{height:64px;border-bottom:1px}` ferait
        // 65 + 76 = 141 si le filet était une rangée. Il est **peint** sur la frontière, donc la
        // somme reste 140.
        assertEquals(1.dp, PlaqueGeometry.Separator)
        assertEquals(OnyxSpace.Stroke, PlaqueGeometry.Separator)
        assertEquals(
            "le filet est compris dans les deux rangées, pas ajouté",
            140.dp,
            PlaqueGeometry.FieldRowHeight + PlaqueGeometry.TabRowHeight,
        )
    }

    @Test
    fun `les valeurs de la planche sont celles de la maison`() {
        assertEquals("rayon de `.plaque`", OnyxSocle.RadiusPanel, PlaqueGeometry.Radius)
        assertEquals("la marge d'écran", OnyxSocle.ScreenMargin, PlaqueGeometry.SideMargin)
        assertEquals("le filet tout autour", OnyxSpace.Stroke, PlaqueGeometry.Outline)
        assertEquals("l'arête claire du haut", 1.5.dp, PlaqueGeometry.TopEdge)
        assertEquals("grain de la planche", 120, PlaqueGeometry.GrainTilePx)
        assertEquals(0.075f, PlaqueGeometry.Grain)
    }

    @Test
    fun `la voie flottante ne loge plus que la pastille de defilement`() {
        // La poignée à chevron qui partageait cette bande a été **retirée de l'écran** sur l'ordre du
        // propriétaire. La voie n'a donc plus à porter qu'une commande : la pastille « aller au
        // dernier message ». Elle vaut sa hauteur plus son dégagement — et **ce chiffre est la
        // garantie** que la pastille ne se pose pas sur le texte : le fil s'arrête exactement au haut
        // de la pastille (c'est ce que l'écran lui réserve, voir `floatLane`).
        assertEquals(
            "la voie = le dégagement de la plaque + la hauteur de la pastille",
            PlaqueGeometry.FloatClearance + PlaqueGeometry.PillHeight,
            PlaqueGeometry.FloatLane,
        )
        assertEquals(
            "la pastille est la cible du pouce de la maison",
            OnyxSpace.ThumbnailSize,
            PlaqueGeometry.PillHeight,
        )
        assertTrue(
            "la voie loge la commande entière : sous sa hauteur, elle déborderait sur le texte",
            PlaqueGeometry.FloatLane >= PlaqueGeometry.PillHeight,
        )
        // **Et elle a bel et bien rétréci.** La voie d'avant valait le dégagement, la cible de la
        // poignée *et* l'écart qu'elle gardait au-dessus d'elle : 12 + 44 + 12 = 68 dp, dont l'écran
        // reprenait la totalité. La voie ne vaut plus que 56, et la différence est rendue au fil.
        val ancienneVoie = PlaqueGeometry.FloatClearance + OnyxSpace.ThumbnailSize + OnyxSpace.Snug
        assertEquals("l'ancienne voie, poignée comprise, valait 68 dp", 68.dp, ancienneVoie)
        assertEquals(
            "la voie retirée rend 12 dp au fil",
            12.dp,
            ancienneVoie - PlaqueGeometry.FloatLane,
        )
        assertEquals("et son dégagement vient de l'échelle", OnyxSpace.Snug, PlaqueGeometry.FloatClearance)
    }

    @Test
    fun `la cellule d'un repere est la cible du pouce, donc les libelles tombent au meme niveau`() {
        // Le repère-visage de la planche fait 44 px (`.capface`) et les repères à icône 19 px : sans
        // cellule commune, les libellés ne tombent pas au même niveau (relevé d'audit sur la
        // planche elle-même : « repères de barre à des centres différents »). La cellule est la
        // cible tactile de la maison — la même valeur que la pastille de défilement.
        assertEquals(OnyxSpace.ThumbnailSize, PlaqueGeometry.TabCell)
        assertTrue(PlaqueGeometry.TabCell >= 44.dp)
    }

    @Test
    fun `quatre reperes sont dessines, et le cinquieme n existe plus`() {
        // « Idées » et « Objectifs » ont été **supprimés** (demande du propriétaire) : le cinquième
        // repère (« Objectifs »), qui tombait à 4,2 dp de large au bord de la plaque, n'est plus une
        // page cachée du pageur — c'est un écran qui n'existe plus, comme « Idées ». La barre
        // dessine **quatre repères** : trois onglets de contenu, puis le visage du bot.
        assertEquals("le dessin a quatre repères", 4, ChatTab.drawn.size)
        assertEquals("trois pages dans le pageur", 3, chatTabCount)
        assertTrue("le repère-visage n'est pas une page", ChatTab.BOT !in ChatTab.paged)
        assertEquals("le libellé du repère-visage", "Onyx", ChatTab.FACE_LABEL)
    }

    // — Les trois retouches : la pastille, la rangée du champ, le libellé —

    @Test
    fun `la rangee du champ prend les valeurs de la planche, pour porter quatre commandes`() {
        // La planche donne la rangée du champ au pixel : `.ask{gap:10px; padding:0 12px}`,
        // `.askf{padding:0 4px}` et `.csend{width:48px}`. Ces trois nombres sont ceux de la planche,
        // et ce sont eux qui laissent le libellé du champ tenir **entier** à 1,3× de police alors que
        // la plaque porte quatre commandes (`+`, le micro, le mode vocal, l'envoi).
        assertEquals("la marche de la rangée (`.ask{gap:10px}`)", 10.dp, PlaqueGeometry.FieldGap)
        assertEquals("le rembourrage du champ (`.askf{padding:0 4px}`)", 4.dp, PlaqueGeometry.FieldInset)
        assertEquals("le côté de l'envoi (`.csend{width:48px}`)", 48.dp, PlaqueGeometry.SendSlot)
        assertTrue(
            "l'envoi est le seul à dépasser la cible de la maison — c'est l'action principale",
            PlaqueGeometry.SendSlot > PlaqueGeometry.TabCell,
        )
        assertTrue(
            "et la marche reste au-dessus de ce que la cible tactile exige",
            // À 44 dp de cible visuelle, le système déborde de 2 dp par côté : 4 dp suffisent.
            PlaqueGeometry.FieldGap.value >= 4f,
        )
    }

    @Test
    fun `la pastille de l onglet actif prend les valeurs de la planche`() {
        // `hcss.py:134` / `:146` : `--pill` vaut un blanc à 74 % en clair et 17 % en sombre ;
        // `--pillline` (le filet `inset 0 0 0 1px`) vaut 92 % et 24 %. Ce sont les valeurs de la
        // planche, et l'onglet actif n'en invente aucune.
        assertTrue("le fond en clair", PlaqueGeometry.ActivePillLightAlpha == 0.74f)
        assertTrue("le fond en sombre", PlaqueGeometry.ActivePillDarkAlpha == 0.17f)
        assertTrue("le filet en clair", PlaqueGeometry.ActivePillLineLightAlpha == 0.92f)
        assertTrue("le filet en sombre", PlaqueGeometry.ActivePillLineDarkAlpha == 0.24f)
        assertTrue(
            "en sombre la pastille est plus discrète : la page l'est aussi",
            PlaqueGeometry.ActivePillDarkAlpha < PlaqueGeometry.ActivePillLightAlpha,
        )
        assertTrue(
            "le filet est plus marqué que le fond — c'est lui qui détache l'ovale",
            PlaqueGeometry.ActivePillLineLightAlpha > PlaqueGeometry.ActivePillLightAlpha,
        )
    }

    /**
     * **L'arithmétique de la rangée, celle qui décide si le libellé tient.**
     *
     * C'est le calcul que le lot a dû faire pour remettre le bouton du mode vocal **sans rien
     * supprimer** : la plaque mesurée (379,43 dp), le compositeur qui y prend 12 dp de chaque côté,
     * les quatre commandes de la planche (44, 44, 44, 48) et leurs quatre écarts (10 dp). Ce qui
     * reste est le champ — et ce qu'il offre au libellé décide de la clé ([PlaqueInvite]).
     *
     * Le test **exige les deux moitiés** de la décision : la place suffit pour la version courte,
     * et elle ne suffit **pas** pour la phrase entière. Si l'une des deux devenait fausse, le choix
     * de la clé serait faux — et la plaque porterait soit un libellé coupé, soit une ellipse ôtée
     * sans raison.
     */
    @Test
    fun `la rangee du champ tient ses quatre commandes, ses ecarts et son libelle`() {
        val plaqueDp = 379.43f // la largeur mesurée de la plaque sur l'appareil
        val inner = plaqueDp - 2 * 12f // le rembourrage latéral du compositeur
        val commands = 44f + 44f + 44f + PlaqueGeometry.SendSlot.value
        val gaps = 4 * PlaqueGeometry.FieldGap.value
        val field = inner - commands - gaps
        val offered = field - 2 * PlaqueGeometry.FieldInset.value

        assertEquals("les quatre commandes de la planche", 180f, commands)
        assertEquals("quatre écarts de la planche", 40f, gaps)
        assertEquals("le champ", 135.43f, field, 0.05f)
        assertEquals("ce qu'il offre au libellé", 127.43f, offered, 0.05f)
        assertTrue(
            "la place doit porter la version courte (« Écrire à Onyx », ≈ 117 dp)",
            offered >= 117f,
        )
        assertTrue(
            "et elle ne porte pas la phrase entière (« Écrire à Onyx… », ≈ 130 dp) : " +
                "c'est ce qui justifie la clé courte",
            offered < 130f,
        )
    }
}
