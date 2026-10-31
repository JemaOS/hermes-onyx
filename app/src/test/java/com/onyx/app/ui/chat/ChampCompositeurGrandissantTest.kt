package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du champ grandissant — et sa morsure.**
 *
 * Le défaut de la capture du propriétaire : le champ ne s'adaptait pas, le texte débordait sous les
 * commandes et la **première ligne** était coupée. Les chiffres (la hauteur au repos, le plafond de
 * huit lignes, la largeur de référence) sont éprouvés sans appareil par `ComposerGrowthTest`, dans
 * `core`. Ici, on vérifie que **l'écran s'en sert vraiment** — une table juste que personne ne lit ne
 * change aucun pixel — et on **mord** : les deux prédicats ci-dessous rendent **faux** sur la source
 * d'avant (hauteur figée, commandes jamais descendues), et le test échouerait donc sur l'arbre d'avant.
 */
class ChampCompositeurGrandissantTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")

    // — Les deux prédicats : la règle, en une fonction qu'on peut opposer à l'arbre d'avant —

    /**
     * **Le champ grandit-il avec son contenu ?** Vrai si la rangée du champ est au **minimum** de
     * 64 dp (l'état au repos) et **plus figée** à cette valeur. L'ancienne source — une hauteur figée
     * — rend faux.
     */
    private fun champGrandit(source: String): Boolean =
        source.contains("Modifier.heightIn(min = PlaqueGeometry.FieldRowHeight)") &&
            !source.contains("Modifier.height(PlaqueGeometry.FieldRowHeight)")

    /**
     * **Les commandes descendent-elles sur leur propre rangée dès la deuxième ligne ?** Vrai si le
     * contenant bascule sur `multiline`, si la rangée des commandes porte le « + » quand elle est
     * seule, et si un ressort pousse les boutons à droite. L'ancienne source (tout sur une ligne,
     * jamais replié) rend faux — et c'est là que le texte passait **sous** les commandes.
     */
    private fun commandesDescendent(source: String): Boolean =
        source.contains("val commandsOwnRow = !plate || multiline") &&
            source.contains("if (commandsOwnRow) leading()") &&
            source.contains("if (plate && !multiline) {") &&
            source.contains("} else if (multiline) {")

    /** **La mesure du brouillon se fait-elle à une largeur FIXE ?** C'est ce qui interdit l'aller-retour. */
    private fun mesureALargeurFixe(source: String): Boolean =
        source.contains("ComposerGrowth.singleLineFieldWidthDp(composerWidthDp - 24f)") &&
            source.contains("val multiline = ComposerGrowth.stacksCommands(draftLineCount)")

    // — 1 · le champ grandit —

    @Test
    fun `la rangee du champ grandit avec le contenu, elle n est plus figee a 64 dp`() {
        assertTrue(
            "la rangée du champ doit être au minimum de 64 dp, jamais figée à 64 dp",
            champGrandit(composer),
        )
        assertTrue(
            "les 64 dp de la planche restent l'état au repos",
            composer.contains("Modifier.heightIn(min = PlaqueGeometry.FieldRowHeight)"),
        )
        assertTrue(
            "le plafond du champ est la règle de croissance",
            composer.contains("maxLines: Int = ComposerGrowth.MaxLines,"),
        )
    }

    // — 2 · les commandes descendent, le texte ne passe jamais dessous —

    @Test
    fun `les commandes descendent sur leur rangee des que le texte passe a plusieurs lignes`() {
        assertTrue(
            "la disposition doit basculer sur plusieurs lignes (commandes dessous)",
            commandesDescendent(composer),
        )
        assertTrue(
            "le « + » suit les commandes quand elles ont leur propre rangée",
            composer.contains("if (commandsOwnRow) leading()"),
        )
        assertTrue(
            "et la plaque à une ligne garde son « + » en tête, avant le champ",
            composer.contains("} else if (multiline) {"),
        )
    }

    // — 3 · la décision est prise à une largeur fixe (pas d'aller-retour) —

    @Test
    fun `la decision une ligne ou plusieurs est prise a une largeur fixe`() {
        assertTrue(
            "la mesure doit se faire à la largeur de l'état d'une ligne, jamais à la largeur courante",
            mesureALargeurFixe(composer),
        )
        assertTrue(
            "et les lignes réelles sont comptées par mesure, pas estimées au nombre de caractères",
            composer.contains("draftMeasurer.measure(") && composer.contains(".lineCount"),
        )
    }

    // — 4 · LA MORSURE : les prédicats refusent l'arbre d'avant —

    /**
     * La **forme exacte** de la source d'avant (avant ce lot) : la rangée du champ à hauteur figée,
     * le « + » de la rangée conditionné à la carte seule, et un contenant qui ne connaît que `plate`.
     * Aucun prédicat ne doit l'accepter — c'est la morsure, éprouvée par un contrôle négatif comme
     * `ComposerFieldLayersTest` le fait pour la couche unique du champ.
     */
    private val ancien = """
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (plate) {
                        Modifier.height(PlaqueGeometry.FieldRowHeight)
                    } else {
                        Modifier.padding(horizontal = ComposerCardSideMargin)
                    },
                )
        ) {
            Row(modifier = if (plate) { Modifier } else { Modifier.fillMaxWidth() }) {
                if (!plate) leading()
            }
        }
        ComposerBodyContainer(plate = plate, leading = leading) { body(it) }
    """.trimIndent()

    @Test
    fun `la morsure — la source d avant est refusee par les deux predicats`() {
        assertFalse(
            "le champ figé à 64 dp ne doit PAS être accepté : c'est la coupure de la première ligne",
            champGrandit(ancien),
        )
        assertFalse(
            "les commandes jamais descendues ne doivent PAS être acceptées : le texte passe dessous",
            commandesDescendent(ancien),
        )
        assertFalse(
            "et la mesure à largeur variable ne doit pas passer",
            mesureALargeurFixe(ancien),
        )
        // Contre-épreuve : la source d'avant **porte bien** la hauteur figée — la morsure porte.
        assertTrue(ancien.contains("Modifier.height(PlaqueGeometry.FieldRowHeight)"))
        assertTrue(ancien.contains("if (!plate) leading()"))
    }
}
