package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois vues d'une conversation — **le verrou anti-faux-chiffre**, au niveau des panneaux.
 *
 * Il lit la source du fichier qui porte les trois panneaux et vérifie que la **structure de la
 * référence** est bien là (rangée repliée / dépliée, diff à deux encres, sortie en direct, section
 * TERMINÉS, quatre tuiles, fenêtre de contexte, fichiers lus, compte) **et** que les phrases qui
 * avouent une donnée absente y sont — parce que c'est elles qui remplacent le chiffre inventé.
 *
 * Ce test **mord** : retirer la phrase honnête, ou remplacer un appel à `ServeChatActions.money(…)`
 * par un nombre en dur, le fait rougir.
 */
class ThreeViewsPanelsLockTest {

    private val sheet = File("src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt").readText()

    @Test
    fun `les trois panneaux existent et remplacent la feuille`() {
        assertTrue(sheet.contains("private fun UsagePanel("))
        assertTrue(sheet.contains("private fun ProcessPanel("))
        assertTrue(sheet.contains("private fun RestorePanel("))
    }

    @Test
    fun `consommation et cout — titre, montant, appels, quatre tuiles`() {
        assertTrue("le titre « Cette conversation · modèle »", sheet.contains("OnyxLocalization[\"This conversation\"]"))
        assertTrue("le grand montant vient de ServeChatActions.money", sheet.contains("ServeChatActions.money(usage.costUsd)"))
        assertTrue("le nombre d'appels au modèle", sheet.contains("model calls: "))
        assertTrue("le sous-titre « estimé d'après les tarifs »", sheet.contains("Costs are the server's own estimate from provider pricing."))
        listOf("\"Input\"", "\"Output\"", "\"Reasoning\"", "\"From cache\"").forEach {
            assertTrue("la tuile $it", sheet.contains(it))
        }
    }

    @Test
    fun `fenetre de contexte — compteur, barre segmentee, legende`() {
        assertTrue(sheet.contains("private fun ContextBar("))
        assertTrue(sheet.contains("private fun ContextLegendRow("))
        assertTrue("le compteur / limite · pourcentage", sheet.contains("ServeChatActions.percent(usage.contextPercent)"))
        assertTrue("la barre n'est peinte que sur de vraies tranches", sheet.contains("if (usage.contextSlices.isNotEmpty())"))
        assertTrue(
            "l'aveu quand le serveur ne détaille pas",
            sheet.contains("This server does not announce the breakdown by category."),
        )
    }

    @Test
    fun `fichiers lus et compte — la taille reelle, ou l aveu qu il n y en a pas`() {
        assertTrue(sheet.contains("SectionCaption(OnyxLocalization[\"Files read\"].uppercase())"))
        assertTrue(sheet.contains("ServeChatActions.fileSize(file.chars)"))
        assertTrue(
            "l'aveu pour les fichiers non annoncés",
            sheet.contains("This server does not announce which files fill the context."),
        )
        assertTrue(sheet.contains("SectionCaption(OnyxLocalization[\"Account\"].uppercase())"))
        assertTrue(
            "l'aveu quand le compte n'est pas publié — jamais une barre à 0 %",
            sheet.contains("This server publishes no account usage; no weekly limit or renewal date is announced."),
        )
        assertTrue("la limite hebdomadaire utilisée", sheet.contains("\"Weekly limit used\""))
    }

    @Test
    fun `points de restauration — tuile d horloge, identifiant monospace, Restaurer, diff a deux encres`() {
        assertTrue(sheet.contains("private fun RestoreRow("))
        assertTrue("la tuile d'horloge", sheet.contains("Symbols.Clock"))
        assertTrue("l'identifiant court en monospace", sheet.contains("point.shortHash"))
        assertTrue("la pastille Restaurer", sheet.contains("private fun RestorePill("))
        assertTrue("le diff", sheet.contains("private fun RestoreDiffBody("))
        assertTrue("la ligne de compte", sheet.contains("value.countLine()"))
        assertTrue("l'encre des ajouts", sheet.contains("DiffLineKind.ADD ->"))
        assertTrue("l'encre des suppressions", sheet.contains("DiffLineKind.REMOVE ->"))
        assertTrue("le replié / déplié", sheet.contains("expandedId == point.hash"))
    }

    @Test
    fun `processus — point vert et compte, sortie en direct, section TERMINES`() {
        assertTrue(sheet.contains("private fun ProcessRow("))
        assertTrue(sheet.contains("private fun FinishedProcessRow("))
        assertTrue("le compte en cours", sheet.contains("running.size"))
        assertTrue("la sortie en direct", sheet.contains("private fun ProcessOutput("))
        assertTrue("la section TERMINÉS", sheet.contains("SectionCaption(OnyxLocalization[\"Done\"].uppercase())"))
        assertTrue("la pastille d'arrêt", sheet.contains("private fun StopPill("))
        assertTrue("la ligne durée · pid · chemin", sheet.contains("process.metaLine()"))
        assertTrue("le déplié", sheet.contains("expandedId == process.id"))
    }

    @Test
    fun `aucun chiffre n est peint en dur a la place d une donnee absente`() {
        // Les compteurs passent par les helpers du serveur, qui rendent « — » pour `null`.
        assertTrue(sheet.contains("ServeChatActions.compact(usage.inputTokens)"))
        assertTrue(sheet.contains("ServeChatActions.compact(usage.outputTokens)"))
        // Un zéro de repli sur les jetons serait exactement l'invention interdite.
        assertFalse("pas de zéro de repli sur les jetons", sheet.contains("inputTokens ?: 0"))
        assertFalse("pas de zéro de repli sur la sortie", sheet.contains("outputTokens ?: 0"))
        // La sortie d'un processus absente s'avoue, elle ne s'invente pas.
        assertTrue(sheet.contains("This server gave no output for this process."))
        assertTrue(sheet.contains("This server gave no diff for this restore point."))
    }
}
