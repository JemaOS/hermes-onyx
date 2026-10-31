package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'organisation reprise de la référence : les gabarits du fil (puce repliée, pied de réponse,
 * cartes de sous-agents, carte de groupe, étapes) et de l'écran Compétences (badges, séparateurs
 * en retrait).
 *
 * Les gabarits sont testés comme des **fonctions pures** ; le dessin qui ne l'est pas (un
 * composable) est verrouillé par une lecture de source, comme les autres verrous de parité.
 */
class ThreadOrganisationTest {

    private val previousLocale = Locale.getDefault()

    @org.junit.Before
    fun frenchInterface() {
        // L'interface est en français (le dictionnaire central) : les gabarits sont donc relus
        // dans sa langue, pas dans celle du serveur d'intégration.
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun agent(
        id: String,
        status: SubagentStatus = SubagentStatus.RUNNING,
        startedAt: Long = 0L,
        toolCount: Int = 0,
        durationSeconds: Double? = null,
        model: String? = null,
    ) = SubagentActivity(
        id = id,
        goal = "task $id",
        status = status,
        startedAt = startedAt,
        toolCount = toolCount,
        durationSeconds = durationSeconds,
        model = model,
    )

    private fun tool(
        id: String,
        name: String = "terminal",
        startedAt: Long = 0L,
        endedAt: Long? = null,
    ) = ToolActivity(
        id = id,
        name = name,
        status = if (endedAt == null) ToolStatus.RUNNING else ToolStatus.COMPLETED,
        startedAt = startedAt,
        endedAt = endedAt,
    )

    // -------------------------------------------------------------------------------------
    // La puce repliée des outils : « Ran 3 tools · 1 s »
    // -------------------------------------------------------------------------------------

    @Test
    fun `la puce repliee dit le compte, au singulier comme au pluriel`() {
        assertEquals("Lancé 3 outils", ranToolsLabel(3))
        assertEquals("Lancé 1 outil", ranToolsLabel(1))
    }

    @Test
    fun `la duree de la puce s ecrit en secondes, puis en minutes`() {
        // Sous la seconde : « < 1 s », comme la référence.
        assertEquals("< 1 s", ranToolsDuration(0.2))
        assertEquals("1 s", ranToolsDuration(1.4))
        assertEquals("42 s", ranToolsDuration(42.0))
        assertEquals("1 min 4 s", ranToolsDuration(64.0))
        // Sans horodatage, rien : on n'invente pas une durée.
        assertNull(ranToolsDuration(null))
    }

    @Test
    fun `la puce complete joint le compte et la duree, et se tait sans duree`() {
        assertEquals("Lancé 3 outils · 1 s", ranToolsChip(3, 1.4))
        assertEquals("Lancé 3 outils", ranToolsChip(3, null))
        // Un seul outil se dit au singulier.
        assertEquals("Lancé 1 outil · 2 min 3 s", ranToolsChip(1, 123.0))
    }

    @Test
    fun `les secondes d une reponse se calculent du premier depart au dernier retour`() {
        val tools = listOf(
            tool("a", startedAt = 1_000L, endedAt = 3_000L),
            tool("b", startedAt = 500L, endedAt = 5_500L),
        )
        assertEquals(5.0, toolsElapsedSeconds(tools)!!, 0.001)
        // Un outil encore en cours n'a pas de durée : on ne compte pas un retour qui n'est pas venu.
        assertNull(toolsElapsedSeconds(listOf(tool("a", startedAt = 1_000L))))
        assertNull(toolsElapsedSeconds(listOf(tool("a", name = "terminal"))))
    }

    @Test
    fun `les icones de genre sont dedoublonnees et bornees`() {
        val names = listOf("terminal", "execute_code", "read_file", "terminal")
        val symbols = toolKindSymbols(names)
        // Deux genres distincts (terminal, lecture) — et jamais plus de deux icônes.
        assertEquals(2, symbols.size)
        assertEquals(symbols, symbols.distinct())
        assertTrue("un seul terminal", toolKindSymbols(listOf("terminal", "execute_code")).size == 1)
        assertEquals(RanIconsMax, toolKindSymbols(listOf("terminal", "read_file", "web_search", "delegate_task")).size)
    }

    // -------------------------------------------------------------------------------------
    // Le pied de réponse : les jetons
    // -------------------------------------------------------------------------------------

    @Test
    fun `les jetons du pied s ecrivent entree puis sortie, compacts`() {
        assertEquals("18.4k \u2193 · 612 \u2191", replyTokenPair(TokenUsage(input = 18_400, output = 612)))
        assertEquals("0 \u2193 · 5 \u2191", replyTokenPair(TokenUsage(input = 0, output = 5)))
        assertEquals("1.2M \u2193 · 0 \u2191", replyTokenPair(TokenUsage(input = 1_200_000, output = 0)))
    }

    @Test
    fun `un tour sans jetons ne dit rien plutot qu un chiffre invente`() {
        assertNull(replyTokenPair(null))
        assertNull(replyTokenPair(TokenUsage()))
    }

    @Test
    fun `un compte de jetons reste lisible a toutes les echelles`() {
        assertEquals("612", compactTokenCount(612))
        assertEquals("18.4k", compactTokenCount(18_400))
        assertEquals("1.0k", compactTokenCount(1_000))
        assertEquals("1.2M", compactTokenCount(1_200_000))
    }

    // -------------------------------------------------------------------------------------
    // Les cartes de sous-agents : pied et groupe
    // -------------------------------------------------------------------------------------

    @Test
    fun `le pied d une carte dit le modele, la duree et le compte d outils`() {
        val finished = agent(
            "a",
            status = SubagentStatus.COMPLETED,
            toolCount = 5,
            durationSeconds = 12.0,
            model = "sonnet-5",
        )
        assertEquals("sonnet-5 · 12 s · 5 outils", subagentMetaLine(finished, now = 99_000L))

        // Sans modèle ni outils, seule la durée reste — et aucun séparateur en trop.
        val bare = agent("b", status = SubagentStatus.COMPLETED, durationSeconds = 3.0)
        assertEquals("3 s", subagentMetaLine(bare, now = 99_000L))

        // Un outil, au singulier.
        val one = agent("c", status = SubagentStatus.COMPLETED, toolCount = 1, durationSeconds = 1.0)
        assertEquals("1 s · 1 outil", subagentMetaLine(one, now = 99_000L))
    }

    @Test
    fun `une tache vivante dit un temps qui avance, une tache lancee a l aveugle le dit`() {
        val live = agent("a", status = SubagentStatus.RUNNING, startedAt = 0L, toolCount = 2)
        assertEquals("0:03 · 2 outils", subagentMetaLine(live, now = 3_000L))
        val blind = agent("b", status = SubagentStatus.DISPATCHED)
        assertEquals("En arrière-plan", subagentMetaLine(blind, now = 3_000L))
    }

    @Test
    fun `la carte de groupe dit combien travaillent sur combien, et depuis quand`() {
        assertEquals(
            "Travail avec sous-agents · 1 sur 3 · 0:03",
            subagentGroupLabel(live = 1, total = 3, seconds = 3L),
        )
        // Sans total connu, on ne dit pas « 0 sur 0 ».
        assertEquals("Travail avec sous-agents · 0:12", subagentGroupLabel(live = 0, total = 0, seconds = 12L))
        // Sans durée, le compte seul.
        assertEquals("Travail avec sous-agents · 1 sur 3", subagentGroupLabel(live = 1, total = 3, seconds = null))
    }

    @Test
    fun `une duree en horloge s ecrit comme une horloge`() {
        assertEquals("0:03", elapsedClock(3))
        assertEquals("0:42", elapsedClock(42))
        assertEquals("12:05", elapsedClock(725))
        assertEquals("1:02:03", elapsedClock(3_723))
        assertEquals("0:00", elapsedClock(-5))
    }

    @Test
    fun `les points de groupe se comptent au dela de quatre sous-agents`() {
        // Le dessin s'arrête à quatre : au-delà, le dernier point porte « +N ».
        assertEquals(4, TeamDotsMax)
    }

    // -------------------------------------------------------------------------------------
    // Les étapes et l'état du fil
    // -------------------------------------------------------------------------------------

    @Test
    fun `l etape d un plan se dit en clair`() {
        assertEquals("Étape 3 sur 5", planStepLabel(3, 5))
    }

    @Test
    fun `la pastille d etat ne se pose que pendant un tour`() {
        // Au repos : aucune pastille, l'en-tête garde sa hauteur.
        assertNull(threadStatus(working = false, subagents = 0))
        // En cours : « Travail », avec le compte de sous-agents quand il y en a.
        assertEquals("En cours", threadStatus(working = true, subagents = 0)!!.text)
        assertEquals("En cours · 1 sous-agent", threadStatus(working = true, subagents = 1)!!.text)
        assertEquals("En cours · 3 sous-agents", threadStatus(working = true, subagents = 3)!!.text)
    }

    @Test
    fun `une etape de plan prend le pas sur le compte de sous-agents`() {
        assertEquals(
            "En cours · Étape 3 sur 5",
            threadStatusLabel(working = true, subagents = 2, step = 3, total = 5),
        )
        // Un plan vide ne dit pas « Étape 1 sur 0 ».
        assertEquals("En cours", threadStatusLabel(working = true, subagents = 0, step = 1, total = 0))
    }

    // -------------------------------------------------------------------------------------
    // L'écran Compétences : les badges de provenance
    // -------------------------------------------------------------------------------------

    @Test
    fun `le badge de provenance se traduit, et se tait quand le serveur ne dit rien`() {
        assertEquals("hub", provenanceBadge("hub"))
        assertEquals("appris", provenanceBadge("agent"))
        assertEquals("appris", provenanceBadge("learned"))
        assertEquals("externe", provenanceBadge("external"))
        // La casse et les espaces du serveur ne changent pas le badge.
        assertEquals("hub", provenanceBadge(" HUB "))
        assertNull(provenanceBadge(null))
        assertNull(provenanceBadge(""))
        assertNull(provenanceBadge("inconnu"))
    }

    @Test
    fun `le dictionnaire porte bien les libelles repris de la reference`() {
        // Traduits **avant** d'être joints : une phrase composée ne se traduit pas toute seule.
        assertEquals("Lancé 3 outils", OnyxLocalization.format("Ran %d tools", 3))
        assertEquals("Travail avec sous-agents", OnyxLocalization["Working with subagents"])
        assertEquals("Étape 3 sur 5", OnyxLocalization.format("Étape %1\$d sur %2\$d", 3, 5))
        assertEquals("appris", OnyxLocalization["learned"])
    }

    // -------------------------------------------------------------------------------------
    // Le dessin : verrous de source
    // -------------------------------------------------------------------------------------

    private fun source(path: String) = File("src/main/java/$path").readText()

    @Test
    fun `la carte d activite au dessus du compositeur a ete retiree`() {
        val chat = source("com/onyx/app/ui/chat/ChatScreen.kt")
        val thread = source("com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
        // Ordre du propriétaire : elle dupliquait la réflexion du fil et l'arrêt du compositeur.
        assertFalse("la carte d'activité ne doit plus être posée", chat.contains("LiveActivityCard("))
        assertFalse("l'état qui l'alimentait ne doit plus l'appeler", chat.contains("liveActivityTitle("))
        assertFalse("son composant ne doit plus exister", thread.contains("fun LiveActivityCard("))
        // Le **seul** arrêt du tour est celui du compositeur : un carré, dans la rangée du champ.
        val composer = source("com/onyx/app/ui/chat/components/OnyxComposer.kt")
        assertEquals(
            "le compositeur porte exactement un arrêt du tour",
            1,
            Regex("Symbols\\.Stop").findAll(composer).count(),
        )
        // L'arrêt d'un sous-agent est branché sur le vrai `subagent.interrupt` ; et sous le drapeau
        // de doublure de QA (`onyx.subagentDemo`), il se résout en local comme l'écran des tâches.
        val app = source("com/onyx/app/ui/OnyxApp.kt")
        assertTrue(app.contains("scope.launch { chatViewModel.stopSubagent(agent.id) }"))
        assertTrue(app.contains("if (subagentDemoRun) {"))
        assertTrue(app.contains("Stopped from Onyx before it finished."))
    }

    @Test
    fun `un bouton d arret de sous-agent n est dessine que pour un enfant vivant`() {
        val rows = source("com/onyx/app/ui/chat/components/SubagentRows.kt")
        assertTrue(rows.contains("if (agent.isLive) onStop?.let { stop -> SubagentStopButton(onClick = { stop(agent) }) }"))
        // Le groupe n'arrête que les enfants vivants.
        assertTrue(rows.contains("subagents.filter { it.isLive }.forEach(stop)"))
        // Un seul enfant n'a pas de carte de groupe : pas de chevron qui n'ouvrirait rien.
        assertTrue(rows.contains("val grouped = subagents.size > 1"))
    }

    @Test
    fun `les separateurs de l ecran Competences sont en retrait`() {
        val screen = source("com/onyx/app/ui/capabilities/CapabilitiesScreen.kt")
        assertTrue("séparateur en retrait", screen.contains("ContentRowDivider()"))
        assertTrue("badge de provenance posé", screen.contains("badge = provenanceBadge(skill.provenance)"))
        // Aucun séparateur de bord à bord ne subsiste dans la liste des compétences.
        val skills = screen.substringAfter("private fun SkillsBody").substringBefore("private fun SkillListRow")
        // Le filet vient du gabarit : il part **après la vignette** et s'arrête avant le bord droit.
        assertTrue(skills.contains("ContentRowDivider()"))
    }

    @Test
    fun `la provenance est lue sur la reponse du serveur, pas supposee`() {
        val api = File("../core/src/main/java/com/onyx/app/data/net/ServeSkillsApi.kt").readText()
        assertTrue(api.contains("val provenance: String? = null"))
        assertTrue(api.contains("provenance = obj.strOrNull(\"provenance\")"))
    }

    @Test
    fun `la rangee d un outil fini dit le verbe au passe, pas le nom de l outil`() {
        assertEquals("Lancé", toolPastVerb("terminal"))
        assertEquals("Lancé", toolPastVerb("execute_code"))
        assertEquals("Lu", toolPastVerb("read_file"))
        assertEquals("Recherché", toolPastVerb("web_search"))
        assertEquals("Modifié", toolPastVerb("patch"))
        assertEquals("Délégué", toolPastVerb("delegate_task"))
        // Un outil inconnu garde son nom, précédé du verbe de repli : jamais un verbe inventé.
        assertEquals("Utilisé custom tool", toolPastVerb("custom_tool"))
    }

    @Test
    fun `la rangee d outils porte la commande en monospace et la duree a droite`() {
        // LOT fil-vivant : la rangée d'outil est devenue une **carte de contexte** ; le contrat ne
        // change pas — la commande est en **monospace**, et la durée se pose après elle, à droite.
        // Le verrou suit les deux morceaux là où ils vivent maintenant, au lieu de les relâcher.
        val row = source("com/onyx/app/ui/chat/components/MessageRow.kt")
        val steps = row.substringAfter("private fun StepsFold").substringBefore("private fun StepDetail")
        assertTrue(
            "commande absente de la carte d'outil",
            steps.contains("detail = tool.preview?.takeIf { it.isNotBlank() },"),
        )
        assertTrue(
            "durée absente",
            steps.contains("trailing = toolElapsedSeconds(tool)?.let { ranToolsDuration(it) },"),
        )
        val card = source("com/onyx/app/ui/chat/components/ContextCard.kt")
        val mono = card.indexOf("fontFamily = FontFamily.Monospace")
        val detail = card.indexOf("detail?.takeIf { it.isNotBlank() }?.let { line ->")
        val trailing = card.indexOf("trailing?.takeIf { it.isNotBlank() }?.let { tail ->")
        assertTrue("la commande de la carte n'est pas en monospace", mono >= 0)
        assertTrue("la commande de la carte n'est pas dessinée", detail >= 0)
        assertTrue("la durée de la carte n'est pas dessinée", trailing >= 0)
        assertTrue("la durée vient après la commande", detail < trailing && mono < trailing)
    }

    @Test
    fun `le libelle de la carte de groupe ne se coupe pas a une ellipse`() {
        // « Travail avec sous-agents · 3 sur 4 · 1:35 » est plus long que le libellé anglais :
        // sur une seule ligne, la carte perdait le compte ou la durée (relevé en capture).
        val file = source("com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
        val group = file.substringAfter("fun SubagentGroupCard").substringBefore("fun TeamDots")
        // LOT fil-vivant : le groupe passe son libellé à la carte de contexte, qui le dessine.
        assertTrue("le libellé du groupe n'est plus passé à la carte", group.contains("title = label,"))
        val card = source("com/onyx/app/ui/chat/components/ContextCard.kt")
        val title = card.substringAfter("text = title,").substringBefore("}")
        assertTrue("le libellé du groupe est sur une seule ligne", title.contains("maxLines = 2,"))
    }

    @Test
    fun `les deux icones de genre precedent le libelle de la puce`() {
        val label = source("com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
        val chip = label.substringAfter("fun RanToolsChipLabel").substringBefore("// L'en-tête")
        val icons = chip.indexOf("toolKindSymbols(tools.map { it.name })")
        val text = chip.indexOf("ranToolsChip(tools.size")
        assertTrue("icônes absentes", icons >= 0)
        assertTrue("libellé absent", text >= 0)
        assertTrue("les icônes précèdent le texte", icons < text)
        assertNotEquals(0, icons)
    }
}
