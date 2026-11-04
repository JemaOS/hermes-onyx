package com.onyx.app.ui.ledger

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une étiquette de rangée ne se coupe pas : c'est la valeur qui s'élide.**
 *
 * Défaut relevé, capture à l'appui : dans « Relevé de l'agent », la rangée *Modèle* affichait
 * « M o d è l e » — **une lettre par ligne**. La cause n'était pas la police mais la répartition de
 * la largeur : l'étiquette portait `weight(1f)` (elle absorbait ce que la valeur laissait) et la
 * valeur se mesurait **sans borne**, donc un identifiant de modèle long occupait toute la rangée et
 * ne laissait rien à l'étiquette, comprimée à la largeur d'un caractère.
 *
 * L'ordre inverse est le bon : l'étiquette prend ce qu'elle demande, la valeur prend **le reste** —
 * bornée par un poids — et s'élide. Ce verrou interdit le retour de la forme qui a produit le
 * défaut, parce qu'elle se réécrit naturellement quand on ajoute une rangée.
 */
class LedgerRowAlignTest {

    private val fichier = File("src/main/java/com/onyx/app/ui/ledger/AgentLedgerScreen.kt")
    private val source = fichier.readText()

    /** Le source **sans ses commentaires** : une règle porte sur le code, pas sur son explication. */
    private val code = source.lineSequence()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }
        .joinToString("\n")

    private val rangee = code.substringAfter("private fun LedgerRowView(")
        .substringBefore("private fun CompareBots(")

    @Test
    fun `l etiquette du releve ne porte pas de poids`() {
        val etiquette = rangee.substringAfter("text = row.label,").substringBefore("style =")
        assertFalse(
            "une étiquette pondérée se fait écraser par une valeur longue (le défaut « M o d è l e »)",
            etiquette.contains("weight("),
        )
    }

    @Test
    fun `la valeur prend la place restante, bornee`() {
        assertTrue(
            "la valeur occupe la place qui reste et s'aligne à droite",
            rangee.contains("Modifier.weight(1f),") && rangee.contains("contentAlignment = Alignment.CenterEnd"),
        )
    }

    @Test
    fun `une valeur longue s elide sur une ligne`() {
        assertTrue("une seule ligne", rangee.contains("maxLines = 1"))
    }

    @Test
    fun `aucune rangee du releve ne pondere son etiquette`() {
        // Les autres rangées de l'écran suivaient la même forme : elles ne doivent pas la réintroduire.
        assertFalse(
            "plus d'étiquette pondérée dans la rangée du relevé",
            rangee.contains("text = row.label,\n                style = theme.body.copy(color = ledgerLabelInk(theme, ground)),\n                modifier = Modifier.weight(1f),"),
        )
    }
}
