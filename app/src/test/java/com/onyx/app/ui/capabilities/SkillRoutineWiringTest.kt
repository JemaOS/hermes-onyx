package com.onyx.app.ui.capabilities

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La troisième surface du lot « Teach a task » : le bouton, l'écran, et le fil qui les joint.**
 *
 * La règle pure (skill → brouillon de routine) est testée par comportement dans
 * `SkillRoutineTest` (`:core`). Ici ne reste que le **câblage**, qui ne peut se casser qu'en
 * silence : un bouton que personne n'appelle, une graine que personne ne consomme, un écran qui
 * n'est jamais ouvert. C'est exactement de cette façon que la surface était restée à moitié faite.
 */
class SkillRoutineWiringTest {

    private val root = File("src/main/java/com/onyx/app")
    private val editor = File(root, "ui/capabilities/SkillEditorScreen.kt").readText()
    private val cron = File(root, "ui/cron/CronScreen.kt").readText()
    private val app = File(root, "ui/OnyxApp.kt").readText()

    @Test
    fun `l editeur de skill propose d en faire une routine`() {
        assertTrue(
            "l'éditeur doit accepter le geste",
            editor.contains("onMakeRoutine: ((String) -> Unit)? = null"),
        )
        assertTrue(
            "et le proposer à l'écran",
            editor.contains("Make it a routine"),
        )
        assertTrue(
            "en enregistrant la compétence AVANT d'ouvrir les routines",
            editor.contains("saveThen { onMakeRoutine(name.trim()) }"),
        )
    }

    @Test
    fun `l ecran des horaires ouvre le brouillon propose`() {
        assertTrue("la graine arrive par un paramètre", cron.contains("seed: com.onyx.app.data.skill.SkillRoutine.Seed? = null"))
        assertTrue("elle ouvre une routine neuve", cron.contains("viewModel.startNew()"))
        assertTrue(
            "et la remplit du titre et de l'invite",
            cron.contains("draft.copy(title = opening.title, prompt = opening.prompt)"),
        )
        assertTrue("une seule fois", cron.contains("onSeedUsed()"))
    }

    @Test
    fun `les deux chemins de l app sont cables`() {
        // Deux sites d'appel : la route plein écran et le panneau du volet. En oublier un, c'est
        // un bouton mort sur la moitié des appareils.
        val runners = Regex("onMakeRoutine = \\{ name ->").findAll(app).count()
        assertTrue("les deux sites passent le geste ($runners trouvés)", runners >= 2)
        assertTrue(
            "la graine est posée et consommée",
            app.contains("routineSeed = com.onyx.app.data.skill.SkillRoutine.from(name)") &&
                app.contains("seed = routineSeed") &&
                app.contains("onSeedUsed = { routineSeed = null }"),
        )
    }
}