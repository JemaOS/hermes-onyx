package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les positions tiennent après une réouverture.**
 *
 * Le défaut relevé par le propriétaire : *« ça crée des bugs, la barre pour afficher les messages
 * récents se retrouve en haut, c'est incohérent »*, capture à l'appui. La cause est une **ancre non
 * mesurée** : la marge des éléments flottants était calculée depuis `composerHeightPx` et
 * `tabBarHeightPx` — deux états remplis par `onSizeChanged`, donc **à zéro** jusqu'au premier passage
 * de mise en page. À chaque reprise de l'app c'est la **première** image qui part de ces zéros, si
 * bien que les deux éléments flottants — la pastille « aller au dernier message » et la poignée de la
 * barre — étaient dessinés à une position que rien ne mesurait, puis sautaient.
 *
 * Ce test tient la règle **sans appareil**, sur les chemins de reprise que la consigne demande de
 * couvrir : l'ancre rend `null` tant que la plaque n'a pas livré ses hauteurs (`null` = on ne dessine
 * pas), et, dès qu'elle est mesurée, le haut de l'élément flottant tombe **dans la zone du fil** —
 * jamais dans l'en-tête, jamais dans la barre d'état, jamais dans la plaque.
 *
 * Les hauteurs des cas sont celles du relevé de `ChatThreadReserveTest` (densité 2,625) : même
 * appareil, mêmes chiffres, une seule vérité par cas.
 */
class FloatAnchorTest {

    /** Cadre de référence : 1080 × 2400 px à 420 dpi → 411 × 914 dp, barre d'état 24 dp. */
    private val windowDp = 914
    private val statusBarDp = 24

    /** Le bas de l'en-tête : la barre d'état (24 dp) plus la vitre du haut (64 dp). */
    private val headerBottomDp = statusBarDp + 64

    /** La hauteur de la pastille « aller au dernier message » : la cible du pouce (44 dp). */
    private val pillDp = 44

    /**
     * Un cas de reprise. [label] est le chemin ; [composerDp] et [bottomRowDp] sont les hauteurs
     * **mesurées** de la plaque une fois la mise en page passée ; [imeDp] et [navigationDp] sont les
     * encarts du système au moment de la mesure.
     */
    private data class Reprise(
        val label: String,
        val composerDp: Int,
        val bottomRowDp: Int,
        val imeDp: Int,
        val navigationDp: Int,
    )

    private val chemins = listOf(
        // La pile normale : carte du compositeur, barre à cinq onglets, navigation par gestes.
        Reprise("démarrage à froid", 132, 86, 0, 24),
        // La restauration relit le fil et l'onglet : la pile est la même.
        Reprise("mort de processus puis restauration", 132, 86, 0, 24),
        // Rotation : la fenêtre est plus large, la pile garde ses hauteurs de portrait (mesurées).
        Reprise("recréation (rotation)", 132, 86, 0, 24),
        // Thème clair ↔ sombre, langue : la pile ne change pas de hauteur.
        Reprise("recréation (thème, langue)", 132, 86, 0, 24),
        // Police système 1,3× : la carte et la rangée grandissent **ensemble** — c'est le cas mesuré.
        Reprise("recréation (police 1,3×)", 172, 104, 0, 24),
        // Retour depuis une feuille : la pile est inchangée, la surface revient telle quelle.
        Reprise("retour depuis une feuille", 132, 86, 0, 24),
        // Clavier ouvert : l'encart d'IME est le plus grand des deux, compté **une** fois.
        Reprise("clavier ouvert", 132, 86, 288, 24),
        // Clavier fermé : retour à la navigation seule.
        Reprise("clavier fermé", 132, 86, 0, 24),
        // Insets changés : barre de navigation à trois boutons (48 dp).
        Reprise("insets changés (trois boutons)", 132, 86, 0, 48),
    )

    @Test
    fun `table des chemins de reprise — position mesuree contre position attendue`() {
        val report = StringBuilder(
            "\nTABLE DES CHEMINS DE REPRISE — la pastille « aller au dernier message »\n" +
                "chemin                                 1re passe          après mesure\n" +
                "                                       ancre  haut        ancre  haut attendu  haut mesuré  écart  verdict\n",
        )
        chemins.forEach { cas ->
            // --- la première passe de la reprise : la plaque n'a pas encore livré ses hauteurs ---
            val ancreAvant = ComposerStacking.floatAnchorDp(
                composerDp = 0,
                bottomRowDp = 0,
                measuredDp = ComposerStacking.PillClearanceDp,
            )
            val hautAvant = ComposerStacking.floatTopDp(
                composerDp = 0,
                bottomRowDp = 0,
                measuredDp = ComposerStacking.PillClearanceDp,
                windowDp = windowDp,
                floatHeightDp = pillDp,
            )
            assertNull(
                "${cas.label} : rien ne doit être dessiné avant la mesure de la plaque",
                ancreAvant,
            )
            assertNull(
                "${cas.label} : pas de haut non plus — l'élément n'existe pas encore",
                hautAvant,
            )
            assertFalse(
                "${cas.label} : un élément non dessiné ne peut pas écraser l'en-tête",
                ComposerStacking.overlapsHeader(hautAvant, headerBottomDp),
            )

            // --- après la mesure : l'ancre vaut la réserve du fil plus le dégagement ---
            val reserve = ComposerStacking.threadBottomReserveDp(
                composerDp = cas.composerDp,
                bottomRowDp = cas.bottomRowDp,
                imeDp = cas.imeDp,
                navigationBarsDp = cas.navigationDp,
            )
            val attendu = reserve + ComposerStacking.PillClearanceDp
            val ancre = ComposerStacking.floatAnchorDp(
                composerDp = cas.composerDp,
                bottomRowDp = cas.bottomRowDp,
                measuredDp = attendu,
            )
            assertEquals("${cas.label} : l'ancre doit être la réserve mesurée + le dégagement", attendu, ancre)

            val haut = ComposerStacking.floatTopDp(
                composerDp = cas.composerDp,
                bottomRowDp = cas.bottomRowDp,
                measuredDp = attendu,
                windowDp = windowDp,
                floatHeightDp = pillDp,
            )
            assertEquals(
                "${cas.label} : le haut se lit depuis le haut de la fenêtre",
                windowDp - attendu - pillDp,
                haut,
            )
            assertFalse(
                "${cas.label} : la pastille ne doit jamais entrer dans la bande de l'en-tête",
                ComposerStacking.overlapsHeader(haut, headerBottomDp),
            )
            val hautPlaque = windowDp - reserve
            assertTrue(
                "${cas.label} : la pastille doit vivre dans la zone du fil, au-dessus de la plaque " +
                    "(haut $haut, haut de plaque $hautPlaque)",
                haut!! + pillDp <= hautPlaque,
            )
            val ecart = haut - (windowDp - attendu - pillDp)
            report.append(
                "%-38s %-6s %-10s %-6d %-13d %-12d %-6d %s\n".format(
                    cas.label,
                    "—",
                    "non dessiné",
                    attendu,
                    windowDp - attendu - pillDp,
                    haut,
                    ecart,
                    "ok",
                ),
            )
        }
        print(report)
    }

    @Test
    fun `une ancre non mesuree ne dessine rien et l ancienne position tombait dans la plaque`() {
        // **La morsure.** C'est la valeur de départ du défaut : `composerHeightPx` et
        // `tabBarHeightPx` valent zéro à la première passe de **chaque** reprise. Avec la garde,
        // l'ancre est nulle et rien n'est dessiné.
        assertNull(ComposerStacking.floatAnchorDp(composerDp = 0, bottomRowDp = 0, measuredDp = 44))
        assertNull(ComposerStacking.floatAnchorDp(composerDp = 132, bottomRowDp = 0, measuredDp = 44))
        assertNull(ComposerStacking.floatAnchorDp(composerDp = 0, bottomRowDp = 86, measuredDp = 44))
        assertFalse(ComposerStacking.plaqueMeasured(composerDp = 0, bottomRowDp = 0))
        assertFalse(ComposerStacking.plaqueMeasured(composerDp = 132, bottomRowDp = 0))

        // Et ce que la **même** formule donnait sans la garde : la réserve fabriquée depuis zéro
        // (l'encart du système seul, plus l'écart de la maison — 8 dp), plus le dégagement nommé
        // (12 dp). Quarante-quatre points sous le bon endroit.
        val ancienne = ComposerStacking.threadBottomReserveDp(
            composerDp = 0,
            bottomRowDp = 0,
            imeDp = 0,
            navigationBarsDp = statusBarDp,
        ) + ComposerStacking.PillClearanceDp
        assertEquals(
            "l'ancienne marge tombait à 44 dp du bas (24 d'encart + 8 d'écart + 12 de dégagement)",
            44,
            ancienne,
        )
        val hautAncien = windowDp - ancienne - pillDp          // 826 dp
        val hautPlaque = windowDp - ComposerStacking.threadBottomReserveDp(132, 86, 0, 24)   // 664 dp
        assertTrue(
            "l'ancienne position ($hautAncien) tombait DANS la plaque (dont le haut est à $hautPlaque)",
            hautAncien + pillDp > hautPlaque,
        )
        // Le bon chiffre, lui, est bien au-dessus de la plaque : c'est celui que la garde dessine.
        val bon = ComposerStacking.floatAnchorDp(composerDp = 132, bottomRowDp = 86, measuredDp = 266)
        assertEquals(266, bon)
    }

    @Test
    fun `la garde couvre le compositeur ET la rangee, pas seulement l une des deux`() {
        // Une hauteur mesurée ne suffit pas : les deux le sont, ou rien ne se dessine. C'est le sens
        // de « on ne dessine pas tant qu'on n'a pas mesuré » — une moitié de mesure n'est pas une
        // mesure, et son zéro donne la même position fausse.
        listOf(0 to 0, 0 to 86, 132 to 0, -1 to 86, 132 to -1).forEach { (composer, row) ->
            assertNull(
                "compositeur $composer, rangée $row : aucune ancre ne doit sortir",
                ComposerStacking.floatAnchorDp(composerDp = composer, bottomRowDp = row, measuredDp = 266),
            )
        }
        assertEquals(
            "les deux mesurées : l'ancre sort",
            266,
            ComposerStacking.floatAnchorDp(composerDp = 132, bottomRowDp = 86, measuredDp = 266),
        )
    }
}
