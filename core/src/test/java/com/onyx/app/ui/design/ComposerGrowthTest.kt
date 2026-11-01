package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la croissance du champ — et ses dents.**
 *
 * Le défaut signalé par le propriétaire : « le champ ne s'adapte pas » — le texte débordait sous les
 * commandes et la première ligne était coupée. Ce fichier éprouve, sans appareil, les quatre points de
 * la règle ([ComposerGrowth]) : la croissance **ligne par ligne**, le **maximum**, le **défilement
 * interne** au-delà, et la **descente des commandes** dès la deuxième ligne. Chaque chiffre est lu sur
 * la même table que l'écran : le chiffre annoncé et le chiffre vérifié sont le même.
 *
 * La **morsure** est éprouvée par les contrôles négatifs : sur l'ancienne règle (hauteur figée,
 * commandes jamais descendues), le test de croissance et celui de la descente **échouent**.
 */
class ComposerGrowthTest {

    /** La hauteur d'une ligne du corps rendue sur l'appareil de référence (15 sp × 1,22 ≈ 18,3 dp). */
    private val lineDp = 18.3f

    // — 1 · au repos : trois lignes réservées, et plus rien ne bouge sur les trois premières —

    @Test
    fun `au repos le champ reserve trois lignes`() {
        assertEquals("la rangée minimale de la planche reste 64 dp", 64.dp, PlaqueGeometry.FieldRowHeight)
        assertEquals("la cible de la planche : 140 dp", 140.dp, PlaqueGeometry.Height)
        // **En grand de base** : le champ montre trois lignes dès qu'il est vide.
        val repos = ComposerGrowth.RestLines * lineDp + ComposerGrowth.VerticalPaddingDp
        assertEquals("trois lignes + le rembourrage", repos, ComposerGrowth.fieldRowHeightDp(1, lineDp), 0.001f)
        assertTrue("et il est plus haut que l'ancien repos de 64 dp", repos > 64f)
        // Le défaut du propriétaire : à 19 caractères (deuxième ligne) la plaque changeait de
        // hauteur et déplaçait le clavier. Les trois premières lignes ne changent plus rien.
        assertEquals(
            "une ligne, deux lignes, trois lignes : la même hauteur",
            ComposerGrowth.fieldRowHeightDp(1, lineDp),
            ComposerGrowth.fieldRowHeightDp(3, lineDp),
            0.001f,
        )
    }

    // — 2 · croissance ligne par ligne, sans jamais de coupure —

    @Test
    fun `la hauteur grandit avec le nombre de lignes, de facon monotone`() {
        val hauteurs = (1..ComposerGrowth.MaxLines).map { ComposerGrowth.fieldRowHeightDp(it, lineDp) }
        // Monotone (jamais plus petite quand le texte gagne une ligne)…
        hauteurs.zipWithNext().forEach { (a, b) ->
            assertTrue("la hauteur recule : $a → $b", b >= a)
        }
        // …plate jusqu'à la réserve, puis elle **grandit vraiment** au-delà (quatrième ligne ici).
        assertEquals("les trois premières lignes ne bougent pas", hauteurs[0], hauteurs[2], 0.001f)
        assertTrue("à quatre lignes le champ doit avoir grandi", hauteurs[3] > hauteurs[2])
        // Chaque ligne au-delà du repos se **voit** : la hauteur d'une ligne s'ajoute à la précédente.
        assertEquals(
            "deux lignes de plus = deux hauteurs de ligne de plus",
            hauteurs[4] - hauteurs[2],
            2f * lineDp,
            0.001f,
        )
        // Aucune ligne n'est jamais coupée : la hauteur du champ **contient** toutes les lignes rendues.
        (1..ComposerGrowth.MaxLines).forEach { n ->
            assertTrue(
                "les $n lignes doivent tenir dans la hauteur du champ",
                ComposerGrowth.fieldRowHeightDp(n, lineDp) >= n * lineDp,
            )
        }
    }

    // — 3 · au maximum, le champ cesse de grandir et défile en lui-même —

    @Test
    fun `au maximum la hauteur s arrete et le champ defile en lui-meme`() {
        val maxHauteur = ComposerGrowth.fieldRowHeightDp(ComposerGrowth.MaxLines, lineDp)
        assertEquals("dix lignes ne font pas plus haut que huit", maxHauteur, ComposerGrowth.fieldRowHeightDp(10, lineDp), 0.001f)
        assertEquals(maxHauteur, ComposerGrowth.fieldRowHeightDp(100, lineDp), 0.001f)
        assertTrue("huit lignes : pas encore de défilement interne", !ComposerGrowth.scrollsInternally(ComposerGrowth.MaxLines))
        assertTrue("neuf lignes : le champ défile en lui-même", ComposerGrowth.scrollsInternally(ComposerGrowth.MaxLines + 1))
        assertEquals("on ne dessine jamais plus de huit lignes", ComposerGrowth.MaxLines, ComposerGrowth.shownLines(20))
        assertEquals("et jamais moins d'une", 1, ComposerGrowth.shownLines(0))
    }

    // — 4 · à 1,3× de police, la même règle (le plafond est un nombre de LIGNES, pas une hauteur) —

    @Test
    fun `a 1,3x la croissance suit la meme regle`() {
        val lineDp13 = lineDp * 1.3f
        assertEquals(
            "le plafond reste huit lignes, quelle que soit la police",
            ComposerGrowth.MaxLines,
            ComposerGrowth.shownLines(50),
        )
        // Le champ grandit d'autant plus que la ligne est haute, mais s'arrête toujours à huit lignes.
        assertTrue(
            "à 1,3× le champ est plus haut à huit lignes qu'à 1,0×",
            ComposerGrowth.fieldRowHeightDp(ComposerGrowth.MaxLines, lineDp13) >
                ComposerGrowth.fieldRowHeightDp(ComposerGrowth.MaxLines, lineDp),
        )
        assertFalse("et il ne défile pas en lui-même à huit lignes, même à 1,3×", ComposerGrowth.scrollsInternally(ComposerGrowth.MaxLines))
    }

    // — 5 · les commandes descendent à la troisième ligne (deux lignes tolérées sur la rangée) —

    @Test
    fun `les commandes descendent a la troisieme ligne`() {
        // Deux lignes rendues tiennent dans la hauteur de repos : les commandes restent à droite, la
        // plaque ne bouge pas pendant qu'on écrit le premier mot (le défaut « la barre s'agrandit au
        // dixième caractère » venait de la descente dès la deuxième ligne).
        assertEquals("la tolérance est de deux lignes", 2, ComposerGrowth.StackAfterLines)
        assertFalse("une ligne : les commandes restent à droite, sur la même rangée", ComposerGrowth.stacksCommands(1))
        assertFalse("deux lignes : elles y restent encore, la plaque ne bouge pas", ComposerGrowth.stacksCommands(2))
        assertTrue("trois lignes : les commandes descendent sur leur propre rangée", ComposerGrowth.stacksCommands(3))
        assertTrue(ComposerGrowth.stacksCommands(ComposerGrowth.MaxLines))
        assertTrue(ComposerGrowth.stacksCommands(ComposerGrowth.MaxLines + 1))
    }

    // — 6 · la largeur de référence est celle de l'état d'une ligne (elle interdit l'aller-retour) —

    @Test
    fun `la largeur de reference est celle d'une ligne`() {
        // Les quatre commandes de la planche et leurs quatre écarts : 44 + 44 + 44 + 48 + 4 × 10 = 220.
        assertEquals("la place fixe de la rangée d'une ligne", 220f, ComposerGrowth.RowFixedWidthDp, 0.001f)
        // La plaque mesurée (379,43 dp) moins son rembourrage (12 de chaque côté) : 355,43 de contenu ;
        // le champ garde 135,43 — la valeur que la planche a donnée au pixel.
        assertEquals(135.43f, ComposerGrowth.singleLineFieldWidthDp(355.43f), 0.05f)
        // Une largeur nulle ou négative ne rend jamais un champ de largeur nulle (il ne disparaît pas).
        assertTrue(ComposerGrowth.singleLineFieldWidthDp(-100f) >= 1f)
    }

    // — 7 · la plaque étendue laisse le fil : aucun message n'est caché en bas —

    @Test
    fun `la plaque etendue reste sous la hauteur d ecran et laisse le fil`() {
        // L'appareil de référence : 1080 × 2400 px à 420 dpi → ≈ 914 dp de haut.
        val screenDp = 914f
        val plaqueEtendue = ComposerGrowth.plaqueHeightDp(ComposerGrowth.fieldRowHeightDp(ComposerGrowth.MaxLines, lineDp))
        assertEquals("huit lignes + la rangée d'onglets", 166.4f + 76f, plaqueEtendue, 0.2f)
        assertTrue(
            "la plaque étendue tient largement sous la moitié de l'écran",
            plaqueEtendue < 0.5f * screenDp,
        )
        // Le dégagement au-dessus de la pastille reste positif : elle ne se pose jamais sur le texte.
        assertTrue(
            "la pastille « aller au dernier message » garde sa place au-dessus de la plaque étendue",
            ComposerGrowth.pillClearanceDp(plaqueEtendue, screenDp) > plaqueEtendue,
        )
    }
}
