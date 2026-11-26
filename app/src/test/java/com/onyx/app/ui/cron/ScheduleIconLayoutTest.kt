package com.onyx.app.ui.cron

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleIconLayoutTest {
    private val cron = File("src/main/java/com/onyx/app/ui/cron/CronScreen.kt").readText()
    private val bot = File("src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt").readText()

    @Test
    fun `run now pause et resume utilisent les variantes outline de la référence`() {
        assertTrue(cron.contains("MenuItem(\"Run now\", Symbols.BoltOutline)"))
        assertTrue(cron.contains("if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline"))
        assertTrue(bot.contains("if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline"))
        // LOT anatomie-des-contenus : « Run now » et « Pause » sont devenus les **pilules** de la
        // carte de résumé (noir rempli / gris), et le crayon et la corbeille vivent dans la **ligne
        // d'actions** de l'en-tête. Aucune fonction n'a disparu.
        assertTrue(cron.contains("symbol = Lucide.Zap,"))
        assertTrue(cron.contains("if (job.paused) Lucide.Play else Lucide.Pause"))
        assertTrue(cron.contains("symbol = Lucide.Pencil,"))
        assertTrue(cron.contains("symbol = Lucide.Trash2,"))
        listOf(
            "ic_sym_bolt.xml",
            "ic_sym_play.xml",
            "ic_sym_pause.xml",
            "ic_sym_pause_circle.xml",
            "ic_sym_exclamationmark_circle.xml",
            "ic_sym_clock_badge_questionmark.xml",
        ).forEach { assertTrue("$it manquant", File("../core/src/main/res/drawable/$it").isFile) }
    }

    @Test
    fun `la routine dun bot distingue pause missed et indisponible`() {
        assertTrue(bot.contains("job.paused -> Symbols.PauseCircle"))
        assertTrue(bot.contains("missed -> Symbols.ExclamationCircleOutline"))
        assertTrue(bot.contains("symbol = Symbols.ClockBadgeQuestionmark"))
    }

    @Test
    fun `le point cron mesure neuf points et reste aligne en haut`() {
        // LOT anatomie-des-contenus : le rond d'état de couleur est remplacé par **la vignette
        // d'icône de l'anatomie** (`ContentRow(symbol = …)`) — l'état est dit par la colonne de
        // droite, en monospace, comme dans la source (`ScheduledPage.kt:325`).
        assertTrue(cron.contains("CronStatusDotSize = 9.dp"))
        assertTrue(cron.contains("symbol = if (job.lastError != null"))
        assertTrue(cron.contains("statusLabel = statusLabel"))
    }

    @Test
    fun `sans Dashboard cron montre la cloche question et les etats vides de la référence`() {
        // `ContentUnavailableView("No gateway configured", systemImage: "clock.badge.questionmark")`
        assertTrue(cron.contains("title = \"No gateway configured\""))
        assertTrue(cron.contains("symbol = Symbols.ClockBadgeQuestionmark"))
        // `ContentUnavailableView("No jobs yet", systemImage: "clock", description: …)`
        assertTrue(cron.contains("title = \"No jobs yet\""))
        assertTrue(cron.contains("symbol = Symbols.Clock,"))
        assertTrue(cron.contains("Schedule Onyx to run a prompt every hour"))
        assertTrue(cron.contains("OnyxEmptyState("))
        // `ContentUnavailableView("Couldn't load jobs", systemImage: "wifi.exclamationmark")` : le
        // signe de la panne vit désormais dans le **panneau commun des états**, qui remplace la
        // rangée locale — même symbole, et « Réessayer » dans les deux cas (lot « états manquants »).
        assertTrue(cron.contains("OnyxTroublePanel(problem = problem, onRetry = viewModel::refresh)"))
        val trouble = File("src/main/java/com/onyx/app/ui/common/ConnectionProblemCard.kt").readText()
        assertTrue(trouble.contains("Symbols.WifiExclamation"))
    }

    @Test
    fun `cron se declare sans gateway quand il ne peut pas administrer`() {
        val vm = File("src/main/java/com/onyx/app/ui/cron/CronViewModel.kt").readText()
        assertTrue(vm.contains("val needsDashboard: Boolean = false"))
        assertTrue(vm.contains("if (profile == null && !config.canAdminister)"))
        assertTrue(vm.contains("needsDashboard = true"))
    }
}
