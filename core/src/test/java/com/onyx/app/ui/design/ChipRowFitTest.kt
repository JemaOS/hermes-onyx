package com.onyx.app.ui.design

import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle qui empêche une puce d'être **coupée** — éprouvée avec les mesures de la **vraie police**
 * de l'app.
 *
 * Le défaut du propriétaire : sur 360 dp à police 1,3×, la quatrième puce de filtre (« Prêt »)
 * dépassait le bord droit et il n'en restait qu'un fragment ; dans le compositeur, la puce de
 * branche se coupait au milieu d'un mot. Ce test rejoue les quatre combinaisons **360/411 dp ×
 * 1,0/1,3×** et exige que la rangée entière **tienne**.
 *
 * Les largeurs viennent des **avances de la police livrée dans l'app**
 * (`core/src/main/assets/fonts/Inter-SemiBold.ttf`, la graisse de la puce active). Elles sont
 * données en **cadratins** (fraction de la taille) : la largeur d'un libellé vaut donc `somme des
 * avances × taille × échelle`. La somme des avances ignore le crénage — elle est **légèrement plus
 * large** que la chaîne réelle (jusqu'à ~3,6 % sur ces libellés), ce qui va dans le bon sens : ce
 * qui tient ici tient à l'écran.
 */
class ChipRowFitTest {

    /** La police de l'app, dont les avances de ce test sont tirées. */
    private val fontFile = File("src/main/assets/fonts/Inter-SemiBold.ttf")

    /** Les avances, en cadratins, des caractères employés par les libellés de l'écran Code. */
    private val advances: Map<Char, Float> = mapOf(
        ' ' to 0.2520f, '-' to 0.4653f, '/' to 0.3789f, '…' to 0.9561f,
        '0' to 0.6597f, '1' to 0.4229f, '2' to 0.6230f, '3' to 0.6362f, '4' to 0.6660f,
        '5' to 0.6123f, '6' to 0.6397f, '7' to 0.5762f, '8' to 0.6401f, '9' to 0.6397f,
        'À' to 0.7275f, 'B' to 0.6592f, 'E' to 0.6055f, 'N' to 0.7593f, 'P' to 0.6450f,
        'R' to 0.6523f, 'T' to 0.6602f, 'a' to 0.5742f, 'b' to 0.6152f, 'c' to 0.5825f,
        'd' to 0.6240f, 'e' to 0.5913f, 'ê' to 0.5913f, 'h' to 0.6123f, 'i' to 0.2617f,
        'j' to 0.2617f, 'k' to 0.5693f, 'm' to 0.9004f, 'n' to 0.6118f, 'o' to 0.6089f,
        'p' to 0.6240f, 'q' to 0.6240f, 'r' to 0.3970f, 's' to 0.5493f, 't' to 0.3530f,
        'u' to 0.6123f, 'v' to 0.5869f, 'w' to 0.8394f, 'y' to 0.5884f,
    )

    /** Ce qu'une puce de filtre garde autour de son libellé : ses deux marges intérieures, en dp. */
    private val chipInsetDp = 24f

    /** L'écart entre deux puces, en dp. */
    private val gapDp = 8f

    /** Ce que la rangée de filtres perd de chaque côté de l'écran : ses marges, en dp. */
    private val rowMarginDp = 32f

    /** Ce que la ligne du compositeur perd : ses marges, le bouton d'envoi et l'écart qui les sépare. */
    private val composerMarginDp = 32f
    private val composerSendDp = 46f

    /** Ce qu'une puce du compositeur garde autour de son libellé : marges, icône et chevron. */
    private val composerChipInsetDp = 52f

    /** La même puce sans ses ornements : ses deux marges intérieures seules. */
    private val composerPlainInsetDp = 20f

    /** La largeur d'un libellé, en dp, à une taille et une échelle de police données. */
    private fun widthDp(text: String, sizeSp: Float, fontScale: Float = 1f): Float {
        text.forEach { char ->
            assertTrue("avance inconnue pour '$char' (${fontFile.name})", advances.containsKey(char))
        }
        return text.fold(0f) { total, char -> total + advances.getValue(char) } * sizeSp * fontScale
    }

    /** Les quatre libellés de la rangée, compteurs compris — ceux de la capture du propriétaire. */
    private fun labels(counts: List<Int>, withCounts: Boolean): List<String> = listOf(
        "Tout", "En cours", "À toi", "Prêt",
    ).mapIndexed { index, name -> if (withCounts) "$name ${counts[index]}" else name }

    /** Ce que la rangée demande, en dp, pour ces libellés à cette taille. */
    private fun needed(
        counts: List<Int>,
        withCounts: Boolean,
        sizeSp: Float,
        fontScale: Float,
    ): Float = ChipRowFit.rowWidth(
        labelWidthsDp = labels(counts, withCounts).map { widthDp(it, sizeSp, fontScale) },
        chipInsetDp = chipInsetDp,
        gapDp = gapDp,
    )

    /** Le plan que l'écran retiendrait, mesuré comme lui. */
    private fun planFor(screenDp: Float, fontScale: Float, counts: List<Int>): ChipRowPlan {
        val available = screenDp - rowMarginDp
        return ChipRowFit.plan(availableDp = available, chipInsetDp = chipInsetDp, gapDp = gapDp) { size, withCounts ->
            labels(counts, withCounts).map { widthDp(it, size, fontScale) }
        }
    }

    @Test
    fun `la table d avances vient de la police livree dans l app`() {
        // La table est relevée sur ce fichier : s'il disparaît, la mesure n'a plus de source.
        assertTrue("la police de l'app doit être livrée : ${fontFile.absolutePath}", fontFile.isFile)
    }

    // MARK: - La rangée de filtres : les quatre puces tiennent

    @Test
    fun `les quatre puces tiennent a 360 et 411 dp, a 1,0 et 1,3 fois`() {
        val counts = listOf(3, 1, 0, 2)
        for (screenDp in listOf(360f, 411f)) {
            for (fontScale in listOf(1.0f, 1.3f)) {
                val available = screenDp - rowMarginDp
                val plan = planFor(screenDp, fontScale, counts)
                assertTrue("la rangée doit tenir : $screenDp dp × $fontScale", plan.fits)
                assertTrue(
                    "la taille ne descend pas sous le plancher : ${plan.fontSizeSp}",
                    plan.fontSizeSp >= ChipRowFit.FLOOR_SP,
                )
                val used = needed(counts, plan.showCounts, plan.fontSizeSp, fontScale)
                assertTrue(
                    "$screenDp dp × $fontScale : la rangée demande $used dp pour $available dp",
                    used <= available,
                )
                // Les compteurs restent : ils ne tombent que si la place manque vraiment.
                assertTrue("$screenDp dp × $fontScale : les compteurs doivent rester", plan.showCounts)
            }
        }
    }

    @Test
    fun `a 1,0 fois la taille nominale reste entiere`() {
        val counts = listOf(3, 1, 0, 2)
        // 411 dp à 1,0× : la place suffit pour les quatre libellés à leur taille nominale.
        assertEquals(ChipRowFit.BASE_SP, planFor(411f, 1.0f, counts).fontSizeSp, 0.001f)
    }

    @Test
    fun `a 1,3 fois sur 360 dp la taille descend, mais la rangee tient`() {
        val counts = listOf(3, 1, 0, 2)
        val plan = planFor(360f, 1.3f, counts)
        assertTrue("la taille descend", plan.fontSizeSp < ChipRowFit.BASE_SP)
        assertTrue("mais pas sous le plancher", plan.fontSizeSp >= ChipRowFit.FLOOR_SP)
        assertTrue("et rien ne dépasse", needed(counts, plan.showCounts, plan.fontSizeSp, 1.3f) <= 360f - rowMarginDp)
        // Ce qui débordait avant : à la taille nominale, la rangée ne tenait pas — c'est le défaut.
        assertTrue(
            "à la taille nominale la rangée débordait bien",
            needed(counts, true, ChipRowFit.BASE_SP, 1.3f) > 360f - rowMarginDp,
        )
    }

    @Test
    fun `un compteur a deux chiffres ne coupe pas les puces`() {
        val counts = listOf(120, 12, 0, 7)
        for (screenDp in listOf(360f, 411f)) {
            for (fontScale in listOf(1.0f, 1.3f)) {
                val plan = planFor(screenDp, fontScale, counts)
                assertTrue("la rangée tient : $screenDp dp × $fontScale", plan.fits)
                assertTrue(
                    "aucun morceau de puce au bord : $screenDp dp × $fontScale",
                    needed(counts, plan.showCounts, plan.fontSizeSp, fontScale) <= screenDp - rowMarginDp,
                )
            }
        }
    }

    @Test
    fun `les compteurs tombent avant qu une puce ne se coupe`() {
        // Un écran plus étroit que ceux visés : les quatre noms restent, les compteurs s'effacent.
        val counts = listOf(120, 12, 0, 7)
        val plan = planFor(320f, 1.3f, counts)
        assertTrue("la rangée tient encore", plan.fits)
        assertFalse("sans les compteurs", plan.showCounts)
        assertTrue(
            "et les quatre noms restent entiers",
            needed(counts, false, plan.fontSizeSp, 1.3f) <= 320f - rowMarginDp,
        )
    }

    // MARK: - Un libellé s'abrège aux frontières de segment, jamais au milieu d'un mot

    @Test
    fun `un libelle s abrege aux frontieres de segment, jamais au milieu d un mot`() {
        val text = "code/session-neuve"
        // Tout tient : rien ne change.
        assertEquals(text, ChipRowFit.shortenAtBoundary(text, '/') { true })
        // Il faut de la place : un segment entier tombe, la fin reste.
        assertEquals("…/session-neuve", ChipRowFit.shortenAtBoundary(text, '/') { it.length <= 15 })
        // Plus rien ne tient : le dernier segment, entier lui aussi.
        assertEquals("session-neuve", ChipRowFit.shortenAtBoundary(text, '/') { it.length <= 13 })
        // Un libellé sans séparateur n'est jamais coupé.
        assertEquals("main", ChipRowFit.shortenAtBoundary("main", '/') { false })
    }

    @Test
    fun `la regle pure retient la plus grande taille qui tient`() {
        // Un libellé de 8 fois la taille, dans 120 dp : 15 → 120 + 20 (trop), 12 → 96 + 20 (tient).
        // Les marches sont les **corps du socle** : 15 → 12 → 11, jamais 14,5 ni 12,5.
        val narrowed = ChipRowFit.fontSize(120f, 20f, 8f) { size -> listOf(size * 8f) }
        assertEquals(12f, narrowed, 0.001f)
        assertTrue("la taille descend", narrowed < ChipRowFit.BASE_SP)
        assertTrue(ChipRowFit.rowWidth(listOf(narrowed * 8f), 20f, 8f) <= 120f)
        // Aucune marche hors échelle : la taille rendue est **toujours** un corps du socle.
        listOf(80f, 100f, 120f, 150f, 200f, 400f).forEach { available ->
            val size = ChipRowFit.fontSize(available, 20f, 8f) { s -> listOf(s * 7.3f) }
            assertTrue(
                "taille " + size + " sp hors échelle pour " + available + " dp",
                OnyxSocle.isOnScale(size.sp),
            )
            assertTrue("taille " + size + " sp sous le plancher", size >= ChipRowFit.FLOOR_SP)
        }
        // Un libellé court garde la taille nominale.
        assertEquals(ChipRowFit.BASE_SP, ChipRowFit.fontSize(120f, 20f, 8f) { listOf(30f) }, 0.001f)
        // Rien ne peut tenir : le plancher.
        assertEquals(ChipRowFit.FLOOR_SP, ChipRowFit.fontSize(20f, 20f, 8f) { listOf(10_000f) }, 0.001f)
        assertEquals(11f, ChipRowFit.FLOOR_SP, 0.001f)
    }

    // MARK: - Les puces du compositeur : aucune n'est coupée

    /**
     * Le cas du propriétaire : dépôt « john », aucune copie de travail choisie (la puce dit
     * « Nouveau worktree »), et une branche longue (`code/c-est-quoi-ici`).
     */
    private val composerFull = listOf("john", "Nouveau worktree", "code/c-est-quoi-ici")

    /** La ligne du compositeur retenue, mesurée comme à l'écran. */
    private fun composerFor(screenDp: Float, fontScale: Float): ChipLabelPlan {
        val available = screenDp - composerMarginDp - composerSendDp
        val base = 12f
        return ChipRowFit.labelPlan(
            labels = composerFull,
            availableDp = available,
            insetsDp = listOf(composerChipInsetDp, composerPlainInsetDp),
            gapDp = gapDp,
            baseSp = base,
            // Le plancher est une taille **rendue** : à 1,3×, 11 sp font 14,3 dp de haut.
            floorSp = (ChipRowFit.FLOOR_SP / fontScale).coerceAtMost(base),
        ) { text, size -> widthDp(text, size, fontScale) }
    }

    @Test
    fun `les puces du compositeur tiennent, et ne perdent jamais un morceau de mot`() {
        for (screenDp in listOf(360f, 411f)) {
            for (fontScale in listOf(1.0f, 1.3f)) {
                val available = screenDp - composerMarginDp - composerSendDp
                val plan = composerFor(screenDp, fontScale)
                assertTrue("la ligne tient : $screenDp dp × $fontScale", plan.fits)
                val used = ChipRowFit.rowWidth(
                    plan.labels.map { widthDp(it, plan.fontSizeSp, fontScale) },
                    if (plan.plain) composerPlainInsetDp else composerChipInsetDp,
                    gapDp,
                )
                assertTrue(
                    "$screenDp dp × $fontScale : la ligne demande $used dp pour $available dp",
                    used <= available,
                )
                // Chaque libellé retenu est soit l'original, soit un **segment entier** de
                // l'original (au pire préfixé d'une ellipse) : jamais une coupe au milieu d'un mot.
                plan.labels.forEachIndexed { index, label ->
                    val original = composerFull[index]
                    if (label == original) return@forEachIndexed
                    val segments = label.removePrefix("…/").split('/')
                    assertTrue(
                        "« $label » doit être fait de segments entiers de « $original »",
                        original.split('/').toSet().containsAll(segments),
                    )
                    assertFalse("aucune ellipse au milieu d'un mot : $label", label.drop(2).contains('…'))
                }
            }
        }
    }

    @Test
    fun `a 411 dp et 1,0 fois la branche longue est gardee entiere`() {
        val plan = composerFor(411f, 1.0f)
        // Mesuré : la branche entière tient à sa taille nominale dès que les puces se passent de
        // leurs ornements — aucun caractère n'est perdu, et rien ne dépasse.
        assertTrue("la ligne tient", plan.fits)
        assertTrue("sans les ornements", plan.plain)
        assertEquals(composerFull, plan.labels)
        assertEquals(12f, plan.fontSizeSp, 0.001f)
    }

    @Test
    fun `au plus etroit la branche s abrege a une frontiere, jamais avant`() {
        // 360 dp à 1,3× : la place manque pour trois libellés entiers. La branche tombe à son
        // dernier segment — un mot entier — et la ligne tient.
        val plan = composerFor(360f, 1.3f)
        assertTrue("la ligne tient", plan.fits)
        assertEquals("john", plan.labels[0])
        assertEquals("Nouveau worktree", plan.labels[1])
        assertEquals("c-est-quoi-ici", plan.labels[2])
        assertTrue("et sans une ellipse au milieu d'un mot", !plan.labels[2].contains('…'))
    }
}
