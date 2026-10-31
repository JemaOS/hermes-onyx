package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la confirmation — le retrait, l'écart, et le ton destructeur.**
 *
 * Ce que le propriétaire a pointé, mot pour mot : « la partie restaurer message n'est pas cohérente
 * avec le reste du thème et les boutons collent trop au bord, ça devrait être espacé ». Deux défauts,
 * et tous les deux **mesurables** :
 *
 *  1. **le retrait** — les actions d'une alerte héritée étaient posées **bord à bord**, à 0 dp du
 *     bord du panneau, sans écart entre elles. La règle de la maison est **16 dp** de retrait
 *     ([OnyxReadyGeometry.Inset]) et **16 dp** d'écart entre deux actions
 *     ([OnyxReadyGeometry.ActionGap]) ;
 *  2. **le ton destructeur** — l'action qui ne se rattrape pas portait l'encre brute du système, et le
 *     panneau de la maison un **aplat** rouge : deux teintes pour un seul rôle. La maison n'en a
 *     qu'une, l'encre de danger ([OnyxConfirmTone]) — et son libellé tient **≥ 4,5:1** dans les deux
 *     apparences.
 *
 * Le verrou **mord** : sur une source fabriquée (un panneau qui poserait 12 dp, ou pas de retrait du
 * tout) il nomme le fichier fautif — sans ce second test, un verrou qui ne détecte rien passerait
 * pour un verrou qui n'a rien à dire.
 */
class OnyxConfirmVerrouTest {

    // ============================================================================================
    // 1 · La géométrie — le retrait et l'écart, tels que le dessin les pose
    // ============================================================================================

    @Test
    fun `le retrait des actions au bord vaut 16 et l ecart entre elles 16`() {
        assertTrue(
            "le retrait d'une action au bord du panneau doit valoir 16 dp, il vaut " +
                "${OnyxReadyGeometry.Inset.value}",
            OnyxReadyGeometry.Inset.value == 16f,
        )
        assertTrue(
            "l'écart entre deux actions doit valoir 16 dp, il vaut ${OnyxReadyGeometry.ActionGap.value}",
            OnyxReadyGeometry.ActionGap.value == 16f,
        )
        assertTrue(
            "la cible d'une action doit valoir au moins 48 dp, elle vaut ${OnyxReadyGeometry.Target.value}",
            OnyxReadyGeometry.Target.value >= 48f,
        )
    }

    @Test
    fun `le verrou mord sur un bouton qui touche le bord`() {
        // Un retrait sous 16 dp — ce que le propriétaire a vu — est refusé, et la valeur fautive est
        // nommée : c'est elle qui apparaîtra dans le message d'échec.
        listOf(0.dp, 8.dp, 12.dp, 15.dp).forEach { tooSmall ->
            assertFalse(
                "un retrait de ${tooSmall.value} dp ne tient pas la règle de la maison (16 dp)",
                OnyxReadyGeometry.insetIsHouse(tooSmall),
            )
        }
        assertTrue("16 dp est le retrait de la maison", OnyxReadyGeometry.insetIsHouse(16.dp))
        assertTrue("au-delà de 16 dp, la règle tient aussi", OnyxReadyGeometry.insetIsHouse(24.dp))

        // Et l'écart : deux actions collées l'une à l'autre sont refusées.
        assertFalse("8 dp d'écart, ce n'est pas l'écart de la maison", OnyxReadyGeometry.gapIsHouse(8.dp))
        assertTrue("16 dp d'écart est l'écart de la maison", OnyxReadyGeometry.gapIsHouse(16.dp))
    }

    @Test
    fun `le panneau de la maison pose le retrait et les sources fabriquees sont relevees`() {
        val panels = listOf(
            "core/src/main/java/com/onyx/app/ui/design/OnyxConfirm.kt",
            "core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt",
        )
        panels.forEach { path ->
            val source = read(path)
            val findings = ConfirmInsetAudit.findings(path, source)
            assertTrue(
                "des confirmations ne posent pas le retrait de la maison (fichier:ligne) :\n" +
                    findings.joinToString("\n") { "  $it" },
                findings.isEmpty(),
            )
        }

        // **La morsure** : une source fabriquée qui pose 12 dp doit produire un constat **nommé**.
        val faute = """
            .padding(OnyxSocle.Tight)
        """.trimIndent()
        val bite = ConfirmInsetAudit.findings("x/Panneau.kt", faute)
        assertTrue("un panneau à 12 dp n'est pas relevé", bite.isNotEmpty())
        assertTrue("le constat doit nommer le fichier", bite.all { it.startsWith("x/Panneau.kt:") })
    }

    // ============================================================================================
    // 2 · Le ton destructeur — une seule teinte, mesurée dans les deux apparences
    // ============================================================================================

    @Test
    fun `le libelle destructeur tient 4,5 sur 1 dans les deux apparences`() {
        listOf(true, false).forEach { dark ->
            val theme = theme(dark)
            val appearance = if (dark) "sombre" else "claire"
            val ratio = OnyxConfirmTone.destructiveRatio(theme)
            assertTrue(
                "en apparence $appearance, le libellé destructeur mesure $ratio sur son fond : " +
                    "sous le plancher de 4,5:1",
                ratio >= OnyxContrast.TEXT_MIN,
            )
        }
    }

    /**
     * **La mesure gelée** — les deux chiffres que le rapport annonce, lisibles dans le test.
     *
     * Et le chiffre qui motive la correction : l'encre **brute** du signal d'erreur (celle que
     * l'alerte héritée posait telle quelle) mesure **3,74:1** en sombre sur la pastille — **sous** le
     * plancher de 4,5:1 du brief. C'est le « rouge générique, pas mesuré » que le propriétaire a vu.
     */
    @Test
    fun `la mesure gelee du ton destructeur`() {
        val dark = theme(dark = true)
        val light = theme(dark = false)

        assertEquals(
            "l'encre de danger brute sur la pastille (apparence sombre)",
            3.739f,
            OnyxContrast.ratio(dark.error, OnyxConfirmTone.destructiveGround(dark)),
            0.01f,
        )
        assertEquals(
            "l'encre de danger mesurée sur la pastille (apparence sombre)",
            4.5f,
            OnyxConfirmTone.destructiveRatio(dark),
            0.01f,
        )
        assertEquals(
            "l'encre de danger mesurée sur la pastille (apparence claire)",
            4.5f,
            OnyxConfirmTone.destructiveRatio(light),
            0.01f,
        )
        // La cible se détache du panneau dans les deux apparences : 1,20:1 en sombre, 1,21:1 en clair.
        assertEquals(
            "la marche de la pastille sur le panneau (sombre)",
            1.20f,
            OnyxContrast.ratio(OnyxConfirmTone.destructiveGround(dark), OnyxSurfaces.surfaceHigh(dark)),
            0.02f,
        )
        assertEquals(
            "la marche de la pastille sur le panneau (clair)",
            1.21f,
            OnyxContrast.ratio(OnyxConfirmTone.destructiveGround(light), OnyxSurfaces.surfaceHigh(light)),
            0.02f,
        )
    }

    @Test
    fun `le libelle de la sortie tient 4,5 sur 1 dans les deux apparences`() {
        listOf(true, false).forEach { dark ->
            val theme = theme(dark)
            val appearance = if (dark) "sombre" else "claire"
            val ratio = OnyxConfirmTone.cancelRatio(theme)
            assertTrue(
                "en apparence $appearance, le libellé de sortie mesure $ratio sur son fond : " +
                    "sous le plancher de 4,5:1",
                ratio >= OnyxContrast.TEXT_MIN,
            )
        }
    }

    @Test
    fun `la pastille destructive se detache du panneau dans les deux apparences`() {
        listOf(true, false).forEach { dark ->
            val theme = theme(dark)
            val appearance = if (dark) "sombre" else "claire"
            val panel = OnyxSurfaces.surfaceHigh(theme)
            val step = OnyxContrast.ratio(OnyxConfirmTone.destructiveGround(theme), panel)
            assertTrue(
                "en apparence $appearance, la pastille destructive mesure $step sur le panneau : " +
                    "la cible ne se lit pas — elle ne se détache pas assez",
                step >= 1.10f,
            )
        }
    }

    @Test
    fun `le ton destructeur est une encre, jamais un aplat rouge`() {
        // **La règle du propriétaire**, citée dans la maison : « le danger soit porté par l'encre du
        // libellé, pas par un aplat ». Le fond de la pastille destructrice est donc un **neutre** de
        // l'échelle des surfaces — jamais la couleur d'erreur elle-même.
        listOf(true, false).forEach { dark ->
            val theme = theme(dark)
            assertFalse(
                "la pastille destructive est un aplat de la couleur d'erreur : le danger doit être " +
                    "porté par l'encre, pas par un aplat",
                OnyxConfirmTone.destructiveGround(theme) == theme.error,
            )
        }
    }

    // ============================================================================================
    // 3 · L'alerte héritée n'est plus une boîte du système
    // ============================================================================================

    @Test
    fun `l alerte heritee ne dessine plus de lignes internes ni de panneau a elle`() {
        val source = read("core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt")
        assertFalse(
            "OnyxAlert.kt dessine encore les lignes de séparation internes du système (OnyxRowDivider)",
            source.contains("OnyxRowDivider"),
        )
        assertFalse(
            "OnyxAlert.kt ne doit plus dessiner son propre panneau de verre (frostedSheet)",
            source.contains("frostedSheet"),
        )
        assertTrue(
            "OnyxAlert.kt doit passer par la surface de la maison (OnyxConfirmSurface)",
            ConfirmInsetAudit.rendersThroughHousePanel(source),
        )
    }

    @Test
    fun `la confirmation de la branche passe par le composant de la maison`() {
        val source = read("app/src/main/java/com/onyx/app/ui/chat/components/BranchFromHereAlert.kt")
        assertTrue(
            "la confirmation de la branche doit passer par OnyxConfirm",
            source.contains("OnyxConfirm("),
        )
        assertFalse(
            "la confirmation de la branche ne doit plus dessiner son propre panneau (frostedSheet)",
            source.contains("frostedSheet"),
        )
        assertFalse(
            "ni écrire son rayon à elle",
            source.contains("GlassPanelCorner"),
        )
    }

    // ============================================================================================

    private fun theme(dark: Boolean): ResolvedTheme =
        ResolvedTheme.resolve(OnyxTheme.STANDARD, dark, OnyxFonts.System)

    private val repoRoot: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        requireNotNull(dir) { "racine du dépôt introuvable depuis ${File("").absolutePath}" }
        dir
    }

    private fun read(path: String): String {
        val file = listOf(File(repoRoot, path), File(path)).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }
}

/**
 * **Le lecteur du retrait** — il lit le source du panneau de la maison et vérifie qu'il pose bien le
 * retrait de la maison ([OnyxReadyGeometry.Inset], 16 dp) autour de ses actions.
 *
 * Il ne prétend pas lire l'arbre syntaxique : il cherche la forme d'écriture qui **pose** le retrait
 * (`padding(OnyxReadyGeometry.Inset)` dans [OnyxConfirmSurface]) et refuse un panneau qui poserait un
 * autre pas — c'est-à-dire un bouton qui « colle au bord ». Le constat **nomme le fichier et la
 * ligne**, comme les autres verrous de la maison.
 */
object ConfirmInsetAudit {

    /** Le retrait de la maison, tel qu'il s'écrit dans le panneau. */
    private val HouseInset = "padding(OnyxReadyGeometry.Inset)"

    /** L'appel au panneau de la maison : `OnyxConfirmSurface {` / `OnyxConfirmSurface(`. */
    private val HousePanel = Regex("""OnyxConfirm(Surface)?\s*[({]""")

    /** Tout retrait d'un panneau : la forme à partir de laquelle on juge où le panneau est posé. */
    private val AnyPanelPadding = Regex("""padding\(\s*Onyx(?:Socle|Space)\.[A-Za-z]+""")

    /** Vrai si [source] passe par le panneau de la maison — la seule surface de confirmation. */
    fun rendersThroughHousePanel(source: String): Boolean = HousePanel.containsMatchIn(source)

    fun findings(file: String, source: String): List<String> {
        // Deux manières de tenir la règle : **poser** le retrait de la maison (c'est le panneau), ou
        // **passer par** le panneau de la maison ([OnyxConfirmSurface]). Un fichier de confirmation
        // qui ne fait ni l'un ni l'autre dessine son propre panneau — et ses boutons collent au bord.
        if (source.contains(HouseInset)) return emptyList()
        if (rendersThroughHousePanel(source)) return emptyList()
        // Pas de retrait de la maison : on nomme la ligne du retrait trouvé, ou la première ligne du
        // fichier en repli (un panneau sans retrait du tout).
        val line = source.lineSequence().withIndex()
            .firstOrNull { (_, text) -> AnyPanelPadding.containsMatchIn(text) }
            ?.index?.plus(1)
            ?: 1
        return listOf(
            "$file:$line  [RETRAIT]  le panneau ne pose pas le retrait de la maison " +
                "(OnyxReadyGeometry.Inset = ${OnyxReadyGeometry.Inset.value} dp) : un bouton y colle au bord",
        )
    }
}
