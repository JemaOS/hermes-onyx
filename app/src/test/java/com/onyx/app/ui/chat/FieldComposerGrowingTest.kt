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
class FieldComposerGrowingTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")

    // — Les deux prédicats : la règle, en une fonction qu'on peut opposer à l'arbre d'avant —

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


}
