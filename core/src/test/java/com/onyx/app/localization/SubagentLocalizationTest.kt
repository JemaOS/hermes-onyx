package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les libellés des sous-agents en français : l'écran des tâches, ses états et ses lignes de détail.
 *
 * Les phrases composées (détail d'une tâche) passent par les motifs du dictionnaire, comme ailleurs
 * dans l'app : le compte d'outils et la durée restent des chiffres, le reste se traduit.
 */
class SubagentLocalizationTest {

    private val fr = Locale.FRANCE
    private fun t(key: String) = OnyxLocalization.translate(key, fr)

    @Test
    fun `l ecran des taches et ses etats sont en francais`() {
        assertEquals("Sous-agents", t("Subagents"))
        assertEquals("Tâches", t("Tasks"))
        assertEquals("Sous-agent", t("Subagent"))
        assertEquals("Tâche déléguée", t("Delegated task"))
        assertEquals("En cours", t("Running"))
        assertEquals("Lancé", t("Dispatched"))
        assertEquals("Terminé", t("Finished"))
        assertEquals("Échec", t("Failed"))
        assertEquals("Arrêter", t("Stop"))
    }

    @Test
    fun `les lignes de detail des taches se traduisent sans toucher aux chiffres`() {
        assertEquals("7 outils · read_file", t("7 tools · read_file"))
        assertEquals("1 outil · web_search", t("1 tool · web_search"))
        assertEquals("4 outils · 23 s · Three tiers.", t("4 tools · 23 s · Three tiers."))
        // Sans outil : la durée et le résumé.
        assertEquals("12 s · Three tiers.", t("12 s · Three tiers."))
        // Le temps écoulé en tête (liste des tâches) : la durée reste, le reste se traduit.
        assertEquals("42 s · 7 outils · read_file", t("42 s · 7 tools · read_file"))
        assertEquals(
            "12 s · Tourne en arrière-plan ; les résultats arrivent dans un tour ultérieur",
            t("12 s · Running in the background; results arrive in a later turn"),
        )
        // La ligne d'état de la transcription traduit le détail **à part** : sinon le compte
        // restait en anglais dans la phrase complète.
        assertEquals("7 outils · 3.3 min · all done", t("7 tools · 3.3 min · all done"))
        // Le compte d'outils seul, et la tâche en série.
        assertEquals("3 outils", t("3 tools"))
        assertEquals("Getting there · tâche 2 sur 3", t("Getting there · task 2 of 3"))
    }

    @Test
    fun `ce qu on dit quand le transport ne donne pas la transcription`() {
        assertEquals(
            "Ce serveur ne transporte pas la transcription",
            t("The transcript isn't carried by this server"),
        )
        assertEquals(
            "Ce transport ne donne pas la transcription du sous-agent",
            t("This transport doesn't give the subagent's transcript"),
        )
        assertEquals(
            "Tourne en arrière-plan ; les résultats arrivent dans un tour ultérieur",
            t("Running in the background; results arrive in a later turn"),
        )
    }

    @Test
    fun `les consignes de pilotage restent traduites`() {
        assertEquals("Envoyé au sous-agent", t("Sent to the subagent"))
        assertEquals("Guider le sous-agent…", t("Steer the subagent…"))
        assertEquals("Le sous-agent a déjà terminé", t("Subagent already finished"))
    }
}
