package com.onyx.app.ui.cron

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleIconLayoutTest {
    private val cron = File("src/main/java/com/onyx/app/ui/cron/CronScreen.kt").readText()
    private val bot = File("src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt").readText()

    @Test
    fun `run now pause et resume utilisent les variantes outline `() {
        assertTrue(cron.contains("MenuItem(\"Run now\", Symbols.BoltOutline)"))
        assertTrue(cron.contains("if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline"))
        assertTrue(bot.contains("if (job.paused) Symbols.PlayOutline else Symbols.PauseOutline"))
        assertTrue(cron.contains("ActionRow(\"Run now\", Symbols.BoltOutline"))
        assertTrue(cron.contains("ActionRow(\"Edit\", Symbols.Pencil"))
        assertTrue(cron.contains("ActionRow(\"Delete\", Symbols.Trash"))
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
        assertTrue(cron.contains("CronStatusDotSize = 9.dp"))
        assertTrue(cron.contains(".size(CronStatusDotSize)"))
        assertTrue(cron.contains("verticalAlignment = Alignment.Top"))
    }

    @Test
    fun `sans Dashboard cron montre la cloche question et les etats vides `() {
        // `ContentUnavailableView("No gateway configured", systemImage: "clock.badge.questionmark")`
        assertTrue(cron.contains("title = \"No gateway configured\""))
        assertTrue(cron.contains("symbol = Symbols.ClockBadgeQuestionmark"))
        // `ContentUnavailableView("No jobs yet", systemImage: "clock", description: …)`
        assertTrue(cron.contains("title = \"No jobs yet\""))
        assertTrue(cron.contains("symbol = Symbols.Clock,"))
        assertTrue(cron.contains("Schedule Onyx to run a prompt every hour"))
        assertTrue(cron.contains("OnyxEmptyState("))
        // `ContentUnavailableView("Couldn't load jobs", systemImage: "wifi.exclamationmark")`
        assertTrue(cron.contains("OnyxIcon(Symbols.WifiExclamation, tint = theme.warning, size = 16.dp)"))
    }

    @Test
    fun `cron se declare sans gateway quand il ne peut pas administrer`() {
        val vm = File("src/main/java/com/onyx/app/ui/cron/CronViewModel.kt").readText()
        assertTrue(vm.contains("val needsDashboard: Boolean = false"))
        assertTrue(vm.contains("if (profile == null && !config.canAdminister)"))
        assertTrue(vm.contains("needsDashboard = true"))
    }
}
