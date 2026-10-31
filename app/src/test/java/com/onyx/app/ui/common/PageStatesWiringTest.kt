package com.onyx.app.ui.common

import com.onyx.app.ui.bots.BotGridCell
import com.onyx.app.ui.bots.botGridCells
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du lot « états manquants »** — il lit les sources des écrans, comme les autres passes
 * du projet : ce qui compte est que chaque état soit **fourni au site d'appel** et relié à une
 * action, et que rien d'inerte ne subsiste.
 *
 * Trois règles, une par défaut de l'audit des 66 routes (`AUDIT-ROUTES.md`) :
 *
 * 1. **T5 — plus d'attente muette.** Chaque écran de données qui lit le serveur montre
 *    `OnyxLoadingRows`, et non une ligne de texte seule (ni rien du tout).
 * 2. **T6 — « serveur injoignable » traité partout.** Chaque échec passe par `OnyxTroublePanel`,
 *    qui offre toujours un « Réessayer » — et non par une rangée muette.
 * 3. **T9 — les listes longues sont paresseuses.** Les écrans dont la liste peut atteindre
 *    plusieurs centaines de rangées (Fichiers, Planifiés, Archives, Sessions de projet, Code) sont
 *    passés en `LazyColumn`, et **ne composent plus** toutes leurs rangées d'un coup.
 */
class PageStatesWiringTest {

    private val root = File("src/main/java/com/onyx/app/ui")
    private fun screen(path: String): String = File(root, path).readText()

    // MARK: - 1. L'attente : des rangées grises là où elle manquait

    @Test
    fun `les ecrans de donnees montrent des rangees d'attente, pas une ligne muette`() {
        val withSkeleton = mapOf(
            "files/FilesScreen.kt" to "les fichiers",
            "models/ModelsScreen.kt" to "les modèles",
            "code/CodeScreen.kt" to "les sessions de code",
            "bots/BotsScreen.kt" to "les bots",
            "bots/HiddenBotsScreen.kt" to "les bots masqués",
            "cron/CronScreen.kt" to "les tâches planifiées",
            "capabilities/CapabilitiesScreen.kt" to "les capacités",
            "conversations/ProjectSessionsScreen.kt" to "les sessions de projet",
            "kanban/KanbanScreen.kt" to "le tableau", // lit le serveur : sa ligne d'attente reste
        )
        for ((path, what) in withSkeleton) {
            val source = screen(path)
            val ok = source.contains("OnyxLoadingRows(") || source.contains("OnyxProgressView(")
            assertTrue("$path ($what) doit porter une attente visible", ok)
        }
    }

    @Test
    fun `l'attente est un composant unique, pas une ligne par ecran`() {
        val states = File(root, "common/PageStates.kt").readText()
        assertTrue("le composant unique d'attente", states.contains("fun OnyxLoadingRows("))
        assertTrue("il n'est pas un tourniquet plein écran", states.contains("height(11.dp)"))
        assertFalse("et il ne clignote pas (rien à figer sous « Réduire les animations »)", states.contains("rememberInfiniteTransition"))
    }

    // MARK: - 2. L'erreur : un message et une action, jamais un écran muet

    @Test
    fun `chaque echec de lecture passe par le panneau commun et son Reessayer`() {
        val wired = mapOf(
            "files/FilesScreen.kt" to "viewModel::reload",
            "models/ModelsScreen.kt" to "onRetry = { attempt++ }",
            "bots/BotsScreen.kt" to "onRetry = viewModel::refresh",
            "cron/CronScreen.kt" to "onRetry = viewModel::refresh",
            "conversations/ProjectSessionsScreen.kt" to "onRetry = viewModel::refresh",
        )
        for ((path, retry) in wired) {
            val source = screen(path)
            assertTrue("$path doit passer par le panneau commun", source.contains("OnyxTroublePanel("))
            assertTrue("$path : le « Réessayer » doit agir", source.contains(retry))
        }
        // Le panneau lui-même : la carte du fil quand le serveur n'a pas répondu, sinon la rangée
        // d'avertissement — **avec** « Try Again » dans les deux cas.
        val states = File(root, "common/PageStates.kt").readText()
        assertTrue(states.contains("if (looksLikeServerTrouble(problem)) {"))
        assertTrue(states.contains("ConnectionProblemCard("))
        assertTrue(states.contains("OnyxRow(onClick = onRetry) {"))
        assertTrue(states.contains("BasicText(\"Try Again\""))
    }

    @Test
    fun `la carte du fil accepte un ecran sans chemin vers les Reglages`() {
        val card = File(root, "common/ConnectionProblemCard.kt").readText()
        assertTrue(
            "l'entrée des Réglages est facultative depuis un écran qui n'y mène pas",
            card.contains("onOpenSettings: (() -> Unit)? = null,"),
        )
        assertTrue("un refus inattendu garde « Try Again » en accent", card.contains("if (showSettings) theme.accent else theme.userText"))
    }

    @Test
    fun `le bouton des Reglages n'est pas offert quand il n'existe pas`() {
        val card = File(root, "common/ConnectionProblemCard.kt").readText()
        assertTrue(card.contains("val showSettings = needsSettings(kind) && onOpenSettings != null"))
        assertTrue(card.contains("if (showSettings) {"))
    }

    // MARK: - 3. L'erreur *n'est pas* une absence de route

    @Test
    fun `une route absente se dit autrement qu'une panne`() {
        val project = screen("conversations/ProjectSessionsScreen.kt")
        assertTrue("l'état dit ce qui manque côté serveur", project.contains("Not available on this gateway yet"))
        assertTrue("et il nomme la route", project.contains("projects.project_sessions"))
        assertTrue("il ne promet pas un écran vide", project.contains("rather than an empty project"))
        assertTrue("le modèle porte le drapeau", project.contains("val routeMissing: Boolean = false"))
    }

    // MARK: - 3 bis. Un échec ne se lit ni comme une attente, ni comme un état vide

    @Test
    fun `un echec ne se lit ni comme une attente ni comme un etat vide`() {
        // Modèles : une sonde qui **lève** (adresse fermée) doit poser un problème, sinon l'écran
        // ne disait rien du tout — relevé sur le banc, adresse 10.0.2.2:8691 fermée.
        val models = screen("models/ModelsScreen.kt")
        assertTrue(
            "une sonde qui échoue par exception pose quand même un problème",
            models.contains("probe == null -> (outcome.exceptionOrNull() as? TransportError)?.userMessage()"),
        )
        // Code : l'échec prend la place de l'état vide, il ne s'y ajoute pas.
        val code = screen("code/CodeScreen.kt")
        assertTrue(
            "l'échec remplace l'état vide",
            code.contains("sessions.isEmpty() && state.problem != null -> OnyxTroublePanel("),
        )
        // Capacités : trois familles, trois attentes qui **s'arrêtent** quand la lecture a échoué.
        val capabilities = screen("capabilities/CapabilitiesScreen.kt")
        assertEquals(
            "les trois familles arrêtent leur attente sur un échec",
            3,
            Regex(Regex.escape("if (problem == null) OnyxLoadingRows")).findAll(capabilities).count(),
        )
    }

    // MARK: - 4. La paresse des listes longues (audit T9)
    @Test
    fun `les listes qui peuvent atteindre 500 rangees sont paresseuses`() {
        val lazyScreens = listOf(
            "files/FilesScreen.kt",
            "cron/CronScreen.kt",
            "sidebar/ArchivedScreen.kt",
            "conversations/ProjectSessionsScreen.kt",
            "code/CodeScreen.kt",
        )
        for (path in lazyScreens) {
            val source = screen(path)
            val lazy = source.contains("OnyxLazyFormPage(") || source.contains("LazyColumn(")
            assertTrue("$path doit défiler paresseusement", lazy)
        }
    }

    @Test
    fun `la carte paresseuse reprend la forme de la carte ordinaire`() {
        val card = File(root, "common/LazyCard.kt").readText()
        // Les mêmes jetons que `OnyxFormSection` : la carte paresseuse ne peut pas dériver.
        assertTrue(card.contains("theme.formSurfaceColor"))
        assertTrue(card.contains("OnyxSpace.CardCorner"))
        assertTrue(card.contains("OnyxSpace.ScreenMargin"))
        assertTrue(card.contains("OnyxInkOn(theme.secondaryText, theme.formPageColor, OnyxContrast.TEXT_MIN)"))
        // Et elle ne dessine aucune couleur en dur (règle de la maison).
        assertFalse(card.contains("Color(0x"))
    }

    @Test
    fun `les rangees hors ecran ne sont plus composees`() {
        // Avant : `Column(verticalScroll)` + `forEach` — les 500 rangées existaient toutes. Après :
        // la liste paresseuse ne compose que la fenêtre. Ce qui le prouve dans la source, c'est
        // qu'un `verticalScroll` ne porte plus la liste des fichiers / tâches / sessions.
        val files = screen("files/FilesScreen.kt")
        assertFalse(
            "la liste des fichiers ne vit plus dans un Column défilant",
            files.contains("verticalScroll(rememberScrollState())"),
        )
        assertTrue("elle est découpée en rangées paresseuses", files.contains("itemsIndexed(entries"))
        assertTrue("Fichiers : le compte des rangées composées suit la fenêtre", files.contains("OnyxCardRow(first = index == 0, last = index == entries.lastIndex)"))

        val cron = screen("cron/CronScreen.kt")
        assertTrue(cron.contains("itemsIndexed(state.jobs"))
        val project = screen("conversations/ProjectSessionsScreen.kt")
        assertTrue(project.contains("itemsIndexed(lane.sessions"))
        val archived = screen("sidebar/ArchivedScreen.kt")
        assertTrue(archived.contains("itemsIndexed(archived"))
        val code = screen("code/CodeScreen.kt")
        assertTrue(code.contains("items(sessions, key = { session -> session.id })"))
    }

    // MARK: - 5. Les trois routes « mortes à l'usage » (audit §4)

    @Test
    fun `l'entree des bots masques ne depend plus du hasard du roster`() {
        val bots = screen("bots/BotsScreen.kt")
        assertTrue(
            "la tuile est posée dès qu'un Dashboard est configuré et que l'écran peut l'ouvrir",
            bots.contains("if (hiddenEntry && (hidden > 0 || hasDashboard)) cells += BotGridCell.Hidden"),
        )
        assertTrue("l'appelant dit s'il fournit l'entrée", bots.contains("hiddenEntry = onOpenHiddenBots != null,"))
        assertEquals(
            "l'entrée est là sans bot caché, avec un Dashboard",
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot, BotGridCell.Hidden),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 0, hiddenEntry = true),
        )
        assertEquals(
            "sans entrée fournie, la grille ne dessine rien qui n'ouvre",
            listOf(BotGridCell.Face, BotGridCell.Face, BotGridCell.NewBot),
            botGridCells(2, expanded = false, hasDashboard = true, hidden = 3, hiddenEntry = false),
        )
    }

    @Test
    fun `l'ecran des bots masques explique au lieu de constater`() {
        val hidden = screen("bots/HiddenBotsScreen.kt")
        assertTrue(hidden.contains("No hidden bots"))
        assertTrue(
            "la phrase dit d'où viennent les bots cachés",
            hidden.contains("Hidden bots are the gateway roster's own, shared with "),
        )
        assertTrue("et l'attente y est aussi", hidden.contains("OnyxLoadingRows(label = \"Loading from Onyx…\")"))
    }

    @Test
    fun `la fiche d'usage dit d'ou viennent les chiffres`() {
        val usage = screen("conversations/SessionUsageScreen.kt")
        // L'ancien drapeau booléen est parti : il confondait « la route n'a pas répondu » et « la
        // route a répondu sans cette ligne **sous ce profil** », et accusait le téléphone des deux.
        assertFalse("le drapeau menteur est de retour", usage.contains("fromGateway"))
        assertTrue("les issues de lecture sont nommées", usage.contains("internal sealed interface UsageSource"))
        assertTrue("la route qui a répondu sans la ligne est un cas à part", usage.contains("data class NoRow(val profile: String) : UsageSource"))
        // La phrase **nomme le profil** interrogé : la panne n'est plus imputée au serveur.
        assertTrue(
            "la notice ne nomme pas le profil",
            usage.contains("OnyxLocalization.format(KEY_REGISTRY_NO_ROW, profileName(origin.profile)"),
        )
        assertTrue(usage.contains("internal const val KEY_REGISTRY_NO_ROW"))
        assertTrue(
            "la phrase nomme ce que seul le serveur sait",
            usage.contains("Reasoning tokens and cost come from the gateway alone and stay blank."),
        )
        // Et un compteur absent ne se lit pas « 0 » : la fiche le dit avec un tiret.
        assertTrue("le total peut manquer", usage.contains("summary.totalTokensOrNull"))
    }
}
