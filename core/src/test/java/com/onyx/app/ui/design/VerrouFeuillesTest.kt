package com.onyx.app.ui.design

import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE verrou des feuilles — aucune surface claire en dur, aucun filet pleine largeur.**
 *
 * Le propriétaire a montré **trois fois** une feuille hors thème : le fond de l'écran sombre et la
 * feuille blanche, des filets de séparation **pleine largeur** qui débordaient du panneau, des
 * **tuiles blanches sur feuille blanche**, et un « Annuler » en **mot bleu centré** au milieu d'une
 * bande grise. Le défaut n'était pas *cette* feuille : c'était **le gabarit** et les feuilles qui ne
 * le suivaient pas.
 *
 * Une intention ne tient pas un an : ce test **mord**. Il relève, dans le source des feuilles, les
 * trois formes d'écriture qui ramènent le défaut —
 *
 *  1. **le filet pleine largeur** : un `OnyxRowDivider` sans retrait, qui court de bord à bord ;
 *  2. **la surface claire en dur** : un blanc ou une teinte claire peints comme fond de panneau
 *     (au lieu du barreau du thème) — la feuille blanche en mode sombre ;
 *  3. **le barreau non flottant** : `theme.surfaceColor` (le barreau d'une **carte**) au lieu de
 *     `OnyxSurfaces.surfaceHigh` (celui des surfaces qui **flottent** : feuille, menu, alerte) — la
 *     feuille qui se devine au lieu de se détacher ;
 *
 * et il **mesure**, dans les deux apparences et sur tous les thèmes, que la tuile d'une rangée et la
 * pastille d'une action se détachent bien du fond réel de la feuille ([OnyxSurfaces.StepMin]).
 *
 * Enfin il **prouve qu'il mord** : sur une source fabriquée (les trois formes d'écriture doivent
 * produire trois constats **nommés**), et sur la **teinte d'avant** — le gris de la famille sur la
 * feuille blanche, qui mesurait moins que la marche d'une surface, donc une tuile invisible.
 */
class VerrouFeuillesTest {

    /**
     * **Aucune feuille ne peint une surface claire en dur ni un filet pleine largeur.**
     *
     * Le relevé nomme **le fichier et la ligne** de chaque constat : le défaut doit être réparable en
     * un coup d'œil, sinon le verrou ne sert qu'à dire « non ».
     */
    @Test
    fun `aucune feuille ne peint une surface claire en dur ni un filet pleine largeur`() {
        val findings = mutableListOf<SheetAudit.Finding>()

        Sheets.forEach { rel ->
            val file = File(repoRoot, rel)
            assertTrue("feuille introuvable (liste gelée périmée) : $rel", file.isFile)
            findings += SheetAudit.findings(rel, file.readText())
        }

        assertTrue(
            "des feuilles sont sorties du système (fichier:ligne) :\n" +
                findings.joinToString("\n") { "  ${it.file}:${it.line}  [${it.kind}]  ${it.text.trim()}" },
            findings.isEmpty(),
        )
    }

    /**
     * **La fermeture d'une feuille est une action de la maison, pas un mot.**
     *
     * C'est le « Annuler » bleu centré : la fermeture doit porter **une forme** (une capsule pleine,
     * du barreau d'action de la feuille et sa cible tactile) — jamais une encre d'accent seule.
     */
    @Test
    fun `la fermeture d une feuille porte une forme, pas une encre seule`() {
        val house = File(repoRoot, "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt").readText()

        assertTrue(
            "la fermeture est une pastille de la maison",
            house.contains("fun HouseSheetCloseRow(label: String, onDismiss: () -> Unit)") &&
                house.contains(".clip(RoundedCornerShape(OnyxSocle.RadiusCapsule))") &&
                house.contains("val ground = houseSheetActionFill(theme)"),
        )
        assertTrue(
            "elle est en retrait de la marge d'écran, jamais pleine largeur",
            house.contains(".padding(horizontal = OnyxSpace.ScreenMargin, vertical = OnyxSpace.Half)"),
        )
        assertTrue(
            "sa cible est celle de la maison",
            house.contains(".heightIn(min = OnyxSpace.TouchTarget)"),
        )
    }

    /**
     * **La tuile et l'action se voient sur la feuille — dans les deux apparences, sur tous les
     * thèmes.**
     *
     * C'est l'arithmétique du défaut « tuiles blanches sur feuille blanche » : chaque teinte est
     * comparée au fond **réel** du panneau ([houseSheetFill]) et doit tenir la marche d'une surface
     * qui se **détache** ([OnyxSurfaces.FloatStepMin]).
     */
    @Test
    fun `les tuiles et les actions se detachent du panneau dans les deux apparences`() {
        var examined = 0
        OnyxTheme.values().forEach { theme ->
            listOf(false, true).forEach { dark ->
                val resolved = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                val fill = houseSheetFill(resolved)
                val tile = houseSheetTileTint(resolved)
                val action = houseSheetActionFill(resolved)

                assertTrue(
                    "$theme (dark=$dark) : la tuile ne se détache pas du panneau " +
                        "(rapport ${OnyxContrast.ratio(tile, fill)}, minimum ${OnyxSurfaces.FloatStepMin})",
                    OnyxContrast.ratio(tile, fill) >= OnyxSurfaces.FloatStepMin,
                )
                assertTrue(
                    "$theme (dark=$dark) : la pastille d'action ne se détache pas du panneau " +
                        "(rapport ${OnyxContrast.ratio(action, fill)}, minimum ${OnyxSurfaces.FloatStepMin})",
                    OnyxContrast.ratio(action, fill) >= OnyxSurfaces.FloatStepMin,
                )
                examined += 1
            }
        }
        assertTrue("le relevé doit couvrir les deux apparences de chaque thème", examined >= 14)
    }

    /**
     * **La preuve que le verrou mord — la teinte d'avant était invisible.**
     *
     * Le gris de la famille ([neutralThumbnailTint], `#F1F1F5` en clair) sur la feuille blanche
     * mesurait **1,127:1** — **moins** que la marche d'une surface qui se détache : c'est le défaut,
     * chiffré. La teinte corrigée ([houseSheetTileTint]) la dépasse — le même calcul qui a motivé la
     * correction, relu par le test.
     */
    @Test
    fun `la teinte d avant etait invisible sur la feuille claire, c est le defaut mesure`() {
        val light = ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = false, fonts = OnyxFonts.System)
        val sheet = houseSheetFill(light)
        val before = OnyxContrast.ratio(neutralThumbnailTint(light), sheet)
        val after = OnyxContrast.ratio(houseSheetTileTint(light), sheet)

        assertTrue(
            "la teinte d'avant devait être sous la marche d'une surface qui se détache (mesurée $before)",
            before < OnyxSurfaces.FloatStepMin,
        )
        assertTrue(
            "la teinte corrigée devait la dépasser (mesurée $after)",
            after >= OnyxSurfaces.FloatStepMin,
        )
    }

    /**
     * **La preuve que le verrou mord — sur une source fabriquée.**
     *
     * Un fichier neuf qui réintroduit les trois formes d'écriture doit produire trois constats,
     * **nommés**. Sans ce test, un verrou qui ne détecte rien passerait pour un verrou qui n'a rien à
     * dire.
     */
    @Test
    fun `le verrou mord sur une feuille hors theme`() {
        val neuve = """
            package x
            fun F() {
                Column {
                    OnyxRowDivider(inset = 0.dp)
                    Box(Modifier.background(Color.White))
                    Box(Modifier.background(theme.surfaceColor))
                    Panel(Modifier.frostedSheet(FrostSurface.SHEET, shape, tint = theme.surfaceColor))
                    Panel(Modifier.background(ReferencePalette.LightSurface))
                }
            }
        """.trimIndent()

        val findings = SheetAudit.findings("x/Neuve.kt", neuve)
        val kinds = findings.map { it.kind }.toSet()

        assertTrue(
            "le filet pleine largeur n'est pas relevé",
            SheetAudit.Kind.FILET_PLEINE_LARGEUR in kinds,
        )
        assertTrue(
            "la surface claire en dur n'est pas relevée",
            SheetAudit.Kind.SURFACE_CLAIRE_EN_DUR in kinds,
        )
        assertTrue(
            "le barreau non flottant n'est pas relevé",
            SheetAudit.Kind.SURFACE_NON_FLOTTANTE in kinds,
        )
        assertTrue(
            "chaque constat doit nommer le fichier et la ligne",
            findings.all { it.file == "x/Neuve.kt" && it.line > 0 },
        )
    }

    // ============================================================================================
    //  Le relevé, gelé
    // ============================================================================================

    private val repoRoot: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        requireNotNull(dir) { "racine du dépôt (settings.gradle.kts) introuvable depuis ${File("").absolutePath}" }
        dir
    }

    companion object {

        /**
         * **Les feuilles gelées** — la liste des surfaces qui sont une **feuille du bas** (ou une
         * alerte / un menu, qui partagent le même gabarit de panneau). Le verrou les relit toutes ;
         * une feuille neuve s'ajoute ici **sciemment**.
         */
        val Sheets: List<String> = listOf(
            "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt",
            "core/src/main/java/com/onyx/app/ui/design/OnyxActionSheet.kt",
            "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
            "core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt",
            "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
            "core/src/main/java/com/onyx/app/ui/design/OnyxConfirm.kt",
            "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt",
            "app/src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt",
            "app/src/main/java/com/onyx/app/ui/chat/components/ToolCallDetail.kt",
            "app/src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt",
            "app/src/main/java/com/onyx/app/ui/conversations/ProjectManage.kt",
            "app/src/main/java/com/onyx/app/ui/sidebar/AccountSheet.kt",
            "app/src/main/java/com/onyx/app/ui/setup/SetupCodeSheet.kt",
            "app/src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt",
            "app/src/main/java/com/onyx/app/ui/code/CodeSessionActions.kt",
        )
    }
}

/**
 * **Le lecteur du source des feuilles.**
 *
 * Il ne prétend pas lire l'arbre syntaxique : il relève les **formes d'écriture** qui ramènent le
 * défaut, ce qui suffit à fermer la dérive — ces formes sont la seule manière d'écrire ces
 * surfaces. Le relevé du rapport sort de ce même objet, donc le chiffre annoncé et le chiffre
 * vérifié ne peuvent pas diverger.
 */
object SheetAudit {

    enum class Kind {
        /** Un filet de séparation **sans retrait** : il court de bord à bord du panneau. */
        FILET_PLEINE_LARGEUR,

        /** Une **surface claire en dur** (un blanc, une teinte claire) peinte comme fond de panneau. */
        SURFACE_CLAIRE_EN_DUR,

        /** Le barreau d'une **carte** (`surfaceColor`) là où il faut celui des surfaces qui flottent. */
        SURFACE_NON_FLOTTANTE,
    }

    data class Finding(val kind: Kind, val file: String, val line: Int, val text: String)

    /**
     * Les formes **interdites dans une feuille**.
     *
     * - le filet sans retrait : `OnyxRowDivider(inset = 0.dp` et un `endInset = 0.dp` ;
     * - la surface claire en dur : `.background(Color.White`, `tint = Color.White` et la palette
     *   **claire** employée directement (`ReferencePalette.Light…`) — c'est la feuille blanche en
     *   mode sombre ;
     * - le barreau non flottant : `tint = theme.surfaceColor` et `.background(theme.surfaceColor)`
     *   (une feuille, un menu ou une alerte prennent `OnyxSurfaces.surfaceHigh`).
     */
    private val Interdits: List<Pair<Kind, String>> = listOf(
        Kind.FILET_PLEINE_LARGEUR to "OnyxRowDivider(inset = 0.dp",
        Kind.FILET_PLEINE_LARGEUR to "inset = 0.dp, endInset = 0.dp",
        Kind.SURFACE_CLAIRE_EN_DUR to ".background(Color.White",
        Kind.SURFACE_CLAIRE_EN_DUR to "tint = Color.White",
        Kind.SURFACE_CLAIRE_EN_DUR to "ReferencePalette.Light",
        Kind.SURFACE_NON_FLOTTANTE to "tint = theme.surfaceColor",
        Kind.SURFACE_NON_FLOTTANTE to ".background(theme.surfaceColor)",
    )

    fun findings(file: String, source: String): List<Finding> =
        Interdits.flatMap { (kind, token) ->
            source.lineSequence().withIndex()
                .filter { it.value.contains(token) }
                .map { (index, line) -> Finding(kind, file, index + 1, line) }
                .toList()
        }
}
