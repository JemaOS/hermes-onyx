package com.onyx.app.ui.design

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les vérifications que la prose de l'audit ne peut pas faire : chaque ligne de [MotionTable] est
 * ancrée dans le relevé de référence **et** dans le code Kotlin, les courbes sont bien les cubic-bezier
 * de la référence, et les ressorts portent l'amortissement et la raideur annoncés.
 *
 * Le relevé `MOTION-INVENTORY.tsv` est généré par `tools/motion-inventory.py` depuis
 * `/home/john/r132/onyx/Echo/Views` et **versionné** : le test n'a pas besoin de la source de référence
 * pour vérifier que la table ne raconte pas d'histoires.
 */
class MotionTableTest {

    private val repoRoot = File("..")
    private val inventory = File(repoRoot, "MOTION-INVENTORY.tsv")

    @Test
    fun `le releve de reference est versionne et lisible`() {
        assertTrue("MOTION-INVENTORY.tsv manque à la racine du dépôt", inventory.isFile)
        assertTrue("le relevé est vide", inventory.readLines().size > 100)
    }

    @Test
    fun `chaque ligne de la table existe au numero annonce dans le releve de reference`() {
        val rows = inventoryRows()
        val missing = mutableListOf<String>()
        MotionTable.entries.forEach { entry ->
            val row = rows[entry.référence]
            val fragment = normalize(entry.sourceSpec)
            when {
                row == null -> missing += "${entry.référence} absent du relevé"
                !row.contains(fragment) -> missing += "${entry.référence} : « $fragment » n'est pas dans « $row »"
            }
        }
        assertEquals("sites de reference introuvables :\n" + missing.joinToString("\n"), emptyList<String>(), missing)
    }

    @Test
    fun `chaque equivalent Kotlin annonce est present dans le code`() {
        val missing = mutableListOf<String>()
        MotionTable.entries.forEach { entry ->
            if (entry.android.isEmpty()) return@forEach
            val needle = entry.android
            if (needle.startsWith("OnyxMotion.Sites.")) {
                val token = needle.removePrefix("OnyxMotion.Sites.")
                val declared = File(repoRoot, OnyxMotionFile).readText().contains("val $token: ")
                val used = source(entry.androidFile).contains(needle)
                if (!declared && !used) missing += "$needle : ni déclaré dans OnyxMotion.kt, ni utilisé dans ${entry.androidFile}"
                return@forEach
            }
            if (!source(entry.androidFile).contains(needle)) {
                missing += "$needle absent de ${entry.androidFile}"
            }
        }
        assertEquals("équivalents Kotlin introuvables :\n" + missing.joinToString("\n"), emptyList<String>(), missing)
    }

    @Test
    fun `les conversions de ressorts portent l amortissement et la raideur annonces`() {
        val wrong = mutableListOf<String>()
        MotionTable.entries.filter { it.dampingRatio != null }.forEach { entry ->
            val duration = requireNotNull(entry.springDuration) { "${entry.référence} : durée manquante" }
            val bounce = requireNotNull(entry.springBounce) { "${entry.référence} : rebond manquant" }
            val spec: SpringSpec<Float> = if (entry.snappy) {
                // `.snappy(duration:, extraBounce:)` : 0,15 de rebond de la référence + extraBounce.
                OnyxMotion.springSnappy(duration, bounce - 0.15)
            } else {
                OnyxMotion.springReference(duration, bounce)
            }
            if (spec.dampingRatio != entry.dampingRatio || spec.stiffness != entry.stiffness) {
                wrong += "${entry.référence} : attendu ${entry.dampingRatio}/${entry.stiffness}, " +
                    "obtenu ${spec.dampingRatio}/${spec.stiffness}"
            }
        }
        assertTrue("aucune ligne de la table ne teste un ressort", MotionTable.entries.any { it.dampingRatio != null })
        assertEquals("ressorts faux :\n" + wrong.joinToString("\n"), emptyList<String>(), wrong)
    }

    @Test
    fun `les courbes d la référence sont les cubic-bezier documentes`() {
        // Les points de contrôle officiels, recalculés ici indépendamment de Compose.
        assertEasing(OnyxMotion.EaseOut, 0f, 0f, 0.58f, 1f)
        assertEasing(OnyxMotion.EaseIn, 0.42f, 0f, 1f, 1f)
        assertEasing(OnyxMotion.EaseInOut, 0.42f, 0f, 0.58f, 1f)
        assertEasing(OnyxMotion.Linear, 0f, 0f, 1f, 1f)
        assertEasing(OnyxMotion.Standard, 0.4f, 0f, 0.2f, 1f)
    }

    @Test
    fun `Reduce Motion suit les trois echelles systeme`() {
        // Aucune échelle à zéro : rien n'est réduit.
        assertTrue(!ReduceMotion.isReduced(1f, 1f, 1f))
        assertTrue(!ReduceMotion.isReduced(0.5f, 0.5f, 0.5f))
        // « Supprimer les animations » met les trois à zéro (Android 12).
        assertTrue(ReduceMotion.isReduced(0f, 0f, 0f))
        // Une seule suffit : un utilisateur qui coupe les animations ne veut pas des boucles.
        assertTrue(ReduceMotion.isReduced(0f, 1f, 1f))
        assertTrue(ReduceMotion.isReduced(1f, 0f, 1f))
        assertTrue(ReduceMotion.isReduced(1f, 1f, 0f))
        // Les trois clés lues sont bien celles du système.
        assertEquals(
            listOf(
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,
            ),
            ReduceMotion.scopes,
        )
    }

    @Test
    fun `la table couvre les ecrans principaux et compte ses statuts`() {
        val views = MotionTable.sourceViews()
        listOf(
            "Views/VoiceView",
            "Views/TranscriptView",
            "Views/MessageRow",
            "Views/MessageArrival",
            "Views/StartScreen",
            "Views/ComposerView",
            "Views/TypedTitle",
            "Views/FollowUpChips",
            "Views/StreamingTail",
            "Views/SwipeToOpen",
            "Views/OpeningCover",
        ).forEach { view ->
            assertTrue("$view n'est couvert par aucune ligne", views.contains(view))
        }
        val counts = MotionTable.countsByStatus()
        assertEquals(
            "les quatre statuts doivent être représentés",
            MotionTable.MotionStatus.entries.toSet(),
            counts.keys,
        )
        assertTrue("la table doit porter au moins 50 sites", MotionTable.entries.size >= 50)
    }

    // — Outils —

    private fun source(file: String): String {
        val handle = File(repoRoot, file)
        assertTrue("source introuvable : $file", handle.isFile)
        return handle.readText()
    }

    /** Les lignes du relevé, indexées par `Views/<fichier>:<ligne>`. */
    private fun inventoryRows(): Map<String, String> = buildMap {
        inventory.readLines().drop(1).forEach { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@forEach
            put("${parts[0]}:${parts[1]}", normalize(parts[3]))
        }
    }

    /** Le relevé et le Kotlin n'ont pas la même mise en forme : on compare sur des espaces simples. */
    private fun normalize(text: String): String = text.trim().split(Regex("\\s+")).joinToString(" ")

    /**
     * Compare une courbe à un cubic-bezier **recalculé à la main** (bisection sur l'abscisse), pas
     * à une autre instance de la classe visée : sinon le test ne prouverait que sa propre
     * cohérence. Les points de contrôle sont ceux que la doc de la référence publie pour `easeIn`,
     * `easeOut`, `easeInOut` et `timingCurve(0.4, 0, 0.2, 1)`.
     */
    private fun assertEasing(easing: Easing, x1: Float, y1: Float, x2: Float, y2: Float) {
        var t = 0f
        while (t <= 1.0001f) {
            val expected = bezier(x1, y1, x2, y2, t)
            val actual = easing.transform(t)
            assertTrue(
                "à $t : attendu $expected, obtenu $actual (points $x1/$y1/$x2/$y2)",
                Math.abs(expected - actual) < 2e-3f,
            )
            t += 0.05f
        }
        assertEquals(0f, easing.transform(0f), 1e-4f)
        assertEquals(1f, easing.transform(1f), 1e-4f)
    }

    /** La fonction de minutage d'un cubic-bezier, résolue par dichotomie sur l'abscisse. */
    private fun bezier(x1: Float, y1: Float, x2: Float, y2: Float, progress: Float): Float {
        fun axis(p1: Float, p2: Float, s: Float): Float {
            val u = 1f - s
            return 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s
        }
        var low = 0f
        var high = 1f
        repeat(60) {
            val middle = (low + high) / 2f
            if (axis(x1, x2, middle) < progress) low = middle else high = middle
        }
        return axis(y1, y2, (low + high) / 2f)
    }

    private companion object {
        const val OnyxMotionFile = "core/src/main/java/com/onyx/app/ui/design/OnyxMotion.kt"
    }
}
