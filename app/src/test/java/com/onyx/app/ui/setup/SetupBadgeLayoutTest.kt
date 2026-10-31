package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La rangée d'une adresse porte un badge bleu « Onyx utilisera cette connexion ». Sa mise en page
 * a une **seule** règle, et c'est elle que ces tests verrouillent : le badge tient sur **une** ligne,
 * il est **séparé** du titre par une marge fixe, il ne peut pas se replier sous lui-même, et quand
 * la place manque il descend **entier** sur la ligne suivante au lieu de se chevaucher avec le
 * titre — c'était le défaut : à police 1,3×, sur un écran de 411 dp, « Onyx utilisera cette
 * connexion » (~225 dp) se coupait en deux et venait se poser sur « Serveur de voix ».
 */
class SetupBadgeLayoutTest {

    private val sheet = File("src/main/java/com/onyx/app/ui/setup/SetupCodeSheet.kt").readText()

    private val addressRow: String = sheet
        .substringAfter("private fun SetupAddressRow(")
        .substringBefore("\n@Composable\nprivate fun SetupActionRow")

    private val badgeGap: String = sheet
        .substringAfter("private val UsedBadgeGap")
        .substringBefore("private val UsedBadgeStackGap")

    @Test
    fun `la rangee d adresse se range avec FlowRow, pas avec une rangee qui se chevauche`() {
        // `FlowRow` est **la** règle : la ligne se remplit, et ce qui ne tient pas descend d'un bloc.
        assertTrue(addressRow, addressRow.contains("FlowRow("))
        assertTrue(addressRow, addressRow.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertTrue(addressRow, addressRow.contains("verticalArrangement = Arrangement.spacedBy(UsedBadgeStackGap)"))
        // Le titre ne déborde plus jamais sous le badge : il s'abrège le premier.
        assertTrue(addressRow, addressRow.contains("maxLines = 1"))
        assertTrue(addressRow, addressRow.contains("overflow = TextOverflow.Ellipsis"))
        // L'**adresse** est un identifiant : elle ne s'abrège plus à la fin. Le verrou du lot
        // précédent demandait une ellipse à une ligne ; elle perdait justement le port et le chemin,
        // c'est-à-dire ce qui distingue deux serveurs. Elle passe désormais par `IdentifierText`,
        // qui garde le **début** et la **fin** — le verrou est resserré, pas relâché.
        assertTrue(addressRow, addressRow.contains("IdentifierText("))
    }

    @Test
    fun `le badge tient sur une seule ligne et garde une marge nette avec le titre`() {
        val badge = addressRow.substringAfter("if (used)").substringBefore("}\n        }")
        assertTrue(badge, badge.contains("maxLines = 1"))
        assertTrue(badge, badge.contains("overflow = TextOverflow.Ellipsis"))
        // La marge est portée par le titre (padding de fin), pas par le badge : quand le badge
        // descend à la ligne suivante, il reste **aligné** sur le titre.
        assertTrue(addressRow, addressRow.contains("Modifier.padding(end = UsedBadgeGap)"))
        assertFalse(badge, badge.contains("Modifier.padding(start ="))
        assertTrue(badgeGap, badgeGap.contains("= 12.dp"))
    }

    @Test
    fun `l ancienne rangee a espaceur qui s ecrasait a disparu`() {
        // `Box(Modifier.weight(1f))` tombait à zéro dès que titre et badge ne tenaient pas : plus
        // aucune marge, et le badge se repliait sur le titre.
        assertFalse(addressRow, addressRow.contains("Box(Modifier.weight(1f))"))
        assertFalse(addressRow, addressRow.contains("Row(verticalAlignment = Alignment.CenterVertically)"))
    }
}
