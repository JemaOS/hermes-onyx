package com.onyx.app.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Le **golden** de la conversion : un journal d'exemple, et le brouillon attendu, comparé tel quel.
 *
 * Le fichier attendu (`src/test/resources/skill/invoice.md`) n'est pas une paraphrase de la sortie :
 * c'est le brouillon lui-même, recopié à la main depuis ce qu'un enregistrement réel produit. Le
 * comparer octet pour octet tient trois promesses d'un coup :
 *
 * - la sortie est **stable** — la même entrée donne ce fichier, aujourd'hui comme demain ;
 * - les règles de fusion se voient dans le résultat (deux attentes de 120 et 180 ms font un seul
 *   pas de 300 ms ; deux défilements de 540 et 220 px font un seul pas de 760 px) ;
 * - le secret du journal ne laisse aucune trace, puisque le fichier attendu n'en contient aucune.
 *
 * Quand une règle change, ce test tombe, et le diff montre **exactement** la ligne qui a changé :
 * c'est le but. Un golden se relit avant de le mettre à jour, jamais après.
 */
class SkillDraftGoldenTest {

    private val journal = ActionJournal(
        title = "Créer une facture depuis un client",
        windowWidth = 1080,
        windowHeight = 2400,
        actions = listOf(
            TracedAction.Navigate("https://factures.example.fr/nouvelle"),
            TracedAction.Tap(540f, 240f, "Nouvelle facture"),
            TracedAction.Wait(120),
            TracedAction.Wait(180),
            TracedAction.Type("1 240,00 €"),
            TracedAction.Tap(300f, 900f),
            // Le mot de passe de la démonstration : il ne doit apparaître nulle part.
            TracedAction.Type("mot-de-passe-de-demo", isSecret = true),
            TracedAction.Scroll(ScrollDirection.DOWN, 540),
            TracedAction.Scroll(ScrollDirection.DOWN, 220),
            TracedAction.Wait(2500),
            TracedAction.Key(NamedKey.BACK),
        ),
    )

    @Test
    fun `le journal d'exemple produit exactement le brouillon attendu`() {
        val attendu = readGolden("/skill/invoice.md")

        assertEquals(attendu, skillDraftMarkdown(journal))
    }

    @Test
    fun `le brouillon attendu ne contient pas le texte secret du journal`() {
        val golden = readGolden("/skill/invoice.md")
        val secret = journal.actions.filterIsInstance<TracedAction.Type>().first { it.isSecret }.text

        assertFalse(golden.contains(secret))
    }

    private fun readGolden(path: String): String =
        javaClass.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("golden absent du classpath de test : $path")
}
