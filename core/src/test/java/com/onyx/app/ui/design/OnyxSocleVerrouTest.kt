package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du socle — et des routes, toutes.**
 *
 * Ce que ce test ferme : le socle vaut au moment où il est écrit, mais une valeur juste
 * **aujourd'hui** redevient fausse **demain**, dans le fichier voisin, sans que personne ne le voie —
 * parce qu'un nombre en dur ne se relit pas, ne se cherche pas, ne se compte pas. Quatre verrous :
 *
 *  1. **les jetons** — les six corps, les deux espacements, la famille de rayons, la famille
 *     d'ombres et l'épaisseur de trait sont **exactement** les valeurs lues dans le générateur ;
 *  2. **le source, écran par écran** — l'ancien verrou ne couvrait que **quatre** écrans : une taille
 *     de 19 sp dans *Kanban*, *Code*, le *Flux* ou n'importe quelle feuille passait sans un mot.
 *     Il couvre maintenant **toutes les routes** ([ROUTES], 158 fichiers) : tout corps hors des six,
 *     toute famille étrangère, tout corps sous 11 sp **fait échouer** le test **en nommant la route,
 *     le fichier et la ligne** ;
 *  3. **le plancher** — 11 sp est un plancher, pas une préférence : une valeur sous 11 sp échoue
 *     **même si elle figure dans les exceptions**. Une exception se justifie ; un corps illisible se
 *     corrige ;
 *  4. **la morsure** — le verrou est éprouvé sur une source **fautive fabriquée à partir d'un écran
 *     réel** ([MORSURES]) : sans ce point, un test vert ne prouve pas qu'il mord, seulement qu'il ne
 *     regarde rien.
 */
class OnyxSocleVerrouTest {

    // ============================================================================================
    // 1 · Les jetons
    // ============================================================================================

    @Test
    fun `l echelle typographique a exactement six corps`() {
        assertEquals(
            listOf(30.sp, 26.sp, 16.sp, 15.sp, 12.sp, 11.sp),
            OnyxSocle.TypeScale,
        )
        assertEquals(OnyxSocle.TypeScale.sortedByDescending { it.value }, OnyxSocle.TypeScale)
        // L'interlettrage des grands corps : négatif, et rien d'autre.
        assertTrue(OnyxSocle.TrackingDisplay.value < 0f)
        assertEquals(-0.032f, OnyxSocle.TrackingDisplay.value, 0.0005f)
        assertEquals(-0.030f, OnyxSocle.TrackingHeading.value, 0.0005f)
    }

    @Test
    fun `snap ramene toute taille calculee sur l echelle et jamais sous onze`() {
        // Une taille calculée (un libellé qui se réduit, un rapport de couverture) ne sort pas de
        // l'échelle : elle tombe sur le corps immédiatement inférieur…
        assertEquals(15.sp, OnyxSocle.snap(15.sp))
        assertEquals(15.sp, OnyxSocle.snap(15.9.sp))
        assertEquals(12.sp, OnyxSocle.snap(14.sp))
        assertEquals(12.sp, OnyxSocle.snap(12.5.sp))
        assertEquals(11.sp, OnyxSocle.snap(11.sp))
        assertEquals(30.sp, OnyxSocle.snap(31.sp))
        // …et sous le plancher, elle **remonte** à 11 — le cas vu sur l'onglet à 4,2 dp.
        assertEquals(11.sp, OnyxSocle.snap(9.5.sp))
        assertEquals(11.sp, OnyxSocle.snap(4.2.sp))
        assertEquals(11.sp, OnyxSocle.snap(0.sp))
        listOf(0.5f, 4.2f, 9f, 10.5f, 13.5f, 14.5f, 19f, 34f, 44f).forEach {
            val snapped = OnyxSocle.snap(it.sp)
            assertTrue("snap($it) = " + snapped.value + " : hors échelle", OnyxSocle.isOnScale(snapped))
            assertTrue("snap($it) = " + snapped.value + " : sous le plancher", snapped.value >= 11f)
        }
    }

    @Test
    fun `il n y a que deux espacements, huit et seize`() {
        assertEquals(listOf(8.dp, 16.dp), OnyxSocle.SpacingScale)
        assertEquals(8, OnyxSocle.SpacingStep)
        OnyxSocle.spacingTokens.forEach { (name, value) ->
            assertTrue(
                "le jeton " + name + " vaut " + value.value + " dp : hors échelle (8 ou 16 seulement)",
                OnyxSocle.isOnScale(value),
            )
        }
        // 4, 12 et 24 ne sont plus dans l'échelle d'usage — c'est tout l'objet du socle.
        listOf(4.dp, 12.dp, 24.dp).forEach {
            assertFalse(it.value.toString() + " dp ne doit plus être sur l'échelle d'usage", OnyxSocle.isOnScale(it))
        }
    }

    @Test
    fun `il n y a qu une famille de rayons`() {
        assertEquals(listOf(14.dp, 20.dp, 28.dp, 50.dp), OnyxSocle.Radii)
        OnyxSocle.radiusTokens.forEach { (name, value) ->
            assertTrue("le rayon " + name + " (" + value.value + ") n'est pas de la famille", OnyxSocle.isRadiusOnScale(value))
        }
        assertEquals(14.dp, OnyxSocle.interior(20.dp, 6.dp))
        assertEquals(0.dp, OnyxSocle.interior(14.dp, 20.dp))
    }

    @Test
    fun `les ombres sont trois profondeurs enchainees, jamais deux ombres dans un seul filtre`() {
        assertEquals(3, OnyxSocle.Shadows.size)
        assertEquals(listOf("Near", "Mid", "Far"), OnyxSocle.Shadows.map { it.name })
        assertEquals(3, OnyxSocle.ShadowMid.layers.size)
        assertEquals(3, OnyxSocle.ShadowFar.layers.size)
        OnyxSocle.Shadows.forEach { depth ->
            depth.layers.forEachIndexed { i, layer ->
                assertTrue(depth.name + "[" + i + "] : flou nul", layer.blur.value > 0f)
                assertTrue(depth.name + "[" + i + "] : opacité nulle", layer.alpha > 0f)
            }
            assertEquals(depth.layers.map { it.dy.value }.sorted(), depth.layers.map { it.dy.value })
        }
        // **La morsure de l'invalidité** : une couche sans flou fait lever la construction.
        assertThrows(IllegalArgumentException::class.java) {
            OnyxSocle.ShadowDepth("Fantôme", listOf(OnyxSocle.ShadowLayer(dy = 0.dp, blur = 0.dp, spread = 0.dp, tier = 1, alpha = 1f)))
        }
    }

    @Test
    fun `l epaisseur de trait rendue vaut 1,55 quel que soit le cote de l icone`() {
        assertEquals(1.55f, OnyxSocle.Stroke.value, 0.0005f)
        listOf(12.dp, 13.dp, 14.dp, 17.dp, 18.dp, 19.dp, 20.dp, 22.dp, 24.dp, 28.dp, 32.dp).forEach { size ->
            val rendered = OnyxSocle.renderedStroke(size, OnyxSocle.iconStrokeWidth(size))
            assertEquals("le trait rendu à " + size.value + " dp", 1.55f, rendered, 0.0005f)
        }
        assertEquals(1.55f * 24f / 18f, OnyxSocle.iconStrokeWidth(18.dp), 0.0005f)
    }

    @Test
    fun `chaque exception declaree porte une raison, et chaque retrait aussi`() {
        assertTrue("le socle doit déclarer ses exceptions", OnyxSocle.exceptions.isNotEmpty())
        (OnyxSocle.exceptions + OnyxSocle.retiredExceptions).forEach {
            assertTrue("une exception sans lieu", it.where.isNotBlank())
            assertTrue("une exception sans valeur", it.value.isNotBlank())
            assertTrue("une exception sans raison : " + it.where, it.why.length > 40)
        }
        assertTrue(OnyxSocle.isDeclaredException("Échelle typographique — titre de couverture générative", "17.sp"))
        // **Retirées, ces deux-là ne sont plus tolérées** : 10 sp (sous le plancher) et 13 sp (corps
        // entre deux marches). Le registre les garde ; le verrou ne les excuse plus.
        assertFalse(OnyxSocle.isDeclaredException("Échelle typographique — plancher monospace (âge, durée)", "10.sp (JetBrains Mono)"))
        assertFalse(OnyxSocle.isDeclaredException("Échelle typographique — identifiant monospace", "13.sp (JetBrains Mono)"))
        assertEquals(2, OnyxSocle.retiredExceptions.size)
    }

    @Test
    fun `la table de provenance cite le generateur`() {
        assertTrue(OnyxSocle.provenance.size >= 12)
        OnyxSocle.provenance.forEach {
            assertTrue("la provenance de " + it.token + " ne cite pas de fichier", it.source.contains(".") && it.source.contains(":"))
        }
    }

    @Test
    fun `la couverture est deterministe pour le meme titre`() {
        repeat(5) {
            assertEquals(
                ConversationCover.plan("Réunion de chantier", "conv-1"),
                ConversationCover.plan("Réunion de chantier", "conv-1"),
            )
        }
        assertEquals(3, ConversationCover.plan("Réunion de chantier", "conv-1").paletteIndex)
        assertEquals(3, ConversationCover.plan("Réunion de chantier", "conv-1").variant)
        assertEquals(5, ConversationCover.plan("Le grand départ", "conv-2").paletteIndex)
        assertEquals(1, ConversationCover.plan("Le grand départ", "conv-2").variant)
        assertEquals(2, ConversationCover.plan("Notes du soir", "conv-3").paletteIndex)
        assertFalse(
            ConversationCover.plan("Réunion de chantier", "conv-1").paletteIndex ==
                ConversationCover.plan("Le grand départ", "conv-2").paletteIndex,
        )
        val plans = (0 until 12).map { ConversationCover.plan("Titre " + it, "id-" + it) }
        assertTrue("trop peu de palettes distinctes", plans.map { it.paletteIndex }.toSet().size >= 4)
        assertTrue("trop peu de variations distinctes", plans.map { it.variant }.toSet().size >= 3)
    }

    // ============================================================================================
    // 2 · Le source — toutes les routes
    // ============================================================================================

    @Test
    fun `chaque fichier d interface appartient a une route`() {
        val declared = ROUTES.values.flatten().toSet()
        val missing = declared.filter { read(it).isEmpty() }
        assertTrue("des fichiers déclarés sont introuvables : " + missing, missing.isEmpty())
        // Aucun écran ne doit exister hors de la table : un fichier ajouté demain sous `ui/` sans
        // être déclaré n'est pas audité — c'est exactement le trou qu'on ferme ici.
        val declaredKeys = declared.map { key(it) }.toSet()
        val orphans = interfaceFiles().filter { key(it) !in declaredKeys && key(it) !in NOT_SCREENS }
        assertTrue(
            "des écrans d'interface ne sont dans aucune route (donc audités par personne) :\n" + orphans.joinToString("\n"),
            orphans.isEmpty(),
        )
    }

    @Test
    fun `aucune route ne porte un corps hors echelle`() {
        val found = mutableListOf<String>()
        ROUTES.forEach { (route, files) ->
            files.forEach { path ->
                val src = read(path)
                if (src.isEmpty()) return@forEach
                SocleAudit.auditTypography(src).forEach { v ->
                    if (v.category == SocleAudit.Category.FONT_SIZE) {
                        found += route + " · " + path + ":" + v.line + " — corps " + v.raw + " sp hors échelle"
                    }
                }
            }
        }
        assertTrue(
            "des corps hors échelle traînent dans les routes (" + found.size + ") :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `aucun corps ne descend sous onze sp, meme declare`() {
        val found = mutableListOf<String>()
        ROUTES.forEach { (route, files) ->
            files.forEach { path ->
                val src = read(path)
                if (src.isEmpty()) return@forEach
                SocleAudit.auditTypography(src).forEach { v ->
                    if (v.category == SocleAudit.Category.FLOOR) {
                        found += route + " · " + path + ":" + v.line + " — corps " + v.raw + " sp < 11 sp"
                    }
                }
            }
        }
        assertTrue(
            "des corps sous le plancher de 11 sp (" + found.size + ") :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `aucune taille n est divisee par l echelle de police`() {
        // Le défaut relevé : `(12 / fontScale)` sur les « A » du réglage de taille de texte. À
        // 1,3× cela vaut 9,2 sp, à 2× cela vaut 6 sp — une taille appliquée **à l'envers**, plus
        // petite à mesure que la police du système grandit.
        val found = mutableListOf<String>()
        ROUTES.forEach { (route, files) ->
            files.forEach { path ->
                val src = read(path)
                if (src.isEmpty()) return@forEach
                SocleAudit.auditTypography(src).forEach { v ->
                    if (v.category == SocleAudit.Category.DIVIDED_BY_SCALE) {
                        found += route + " · " + path + ":" + v.line
                    }
                }
            }
        }
        assertTrue(
            "des tailles divisées par l'échelle de police (" + found.size + ") :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `une seule famille d interface et une seule monospace`() {
        val found = mutableListOf<String>()
        ROUTES.forEach { (route, files) ->
            files.forEach { path ->
                val src = read(path)
                if (src.isEmpty()) return@forEach
                SocleAudit.auditFamilies(src).forEach { v ->
                    found += route + " · " + path + ":" + v.line + " — famille " + v.raw
                }
            }
        }
        assertTrue(
            "des familles étrangères (" + found.size + ") :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
        // Le serif est **nommé** (deux thèmes de lecture) et ne peut pas gagner d'occurrence.
        val theme = read("core/src/main/java/com/onyx/app/ui/theme/OnyxTheme.kt")
        assertTrue("la table de thèmes est introuvable", theme.isNotEmpty())
        val serif = SocleAudit.countSerifDesigns(theme)
        assertEquals(
            "le serif n'appartient qu'aux thèmes PAPER et SLATE (déclaré dans OnyxSocle.exceptions)",
            3,
            serif,
        )
    }

    @Test
    fun `aucun ecran du socle n ecrit une valeur d espacement hors socle`() {
        val found = mutableListOf<String>()
        SOCLE_SCREENS.forEach { path ->
            val src = read(path)
            assertTrue("le fichier « " + path + " » est introuvable pour l'audit", src.isNotEmpty())
            SocleAudit.auditSpacing(src).forEach { v ->
                val allowed = BASELINE[path]?.get(v.category)?.contains(v.raw) ?: false
                if (!allowed) found += path + ": " + v.category + " = " + v.raw + " (ligne " + v.line + ")"
            }
        }
        assertTrue(
            "des valeurs d'espacement hors socle traînent dans les écrans du socle :\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test
    fun `le socle lui meme ne porte aucune valeur hors socle`() {
        SOCLE_FILES.forEach { path ->
            val src = read(path)
            assertEquals("le fichier « " + path + " » est introuvable", true, src.isNotEmpty())
            val v = SocleAudit.auditSpacing(src).filter { it.category != SocleAudit.Category.STROKE_WIDTH }
            val typo = SocleAudit.auditTypography(src).filter { it.category != SocleAudit.Category.FLOOR }
            assertTrue("le socle porte une valeur hors socle dans " + path + " : " + v, v.isEmpty())
            assertTrue("le socle porte une typographie hors socle dans " + path + " : " + typo, typo.isEmpty())
        }
    }

    // ============================================================================================
    // 3 · La morsure — sur une route qui n'était pas couverte
    // ============================================================================================

    @Test
    fun `le verrou mord sur une valeur hors echelle`() {
        val fautive = """
            Text(text = "x", fontSize = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {}
            Box(Modifier.clip(RoundedCornerShape(17.dp)))
        """.trimIndent()
        val v = SocleAudit.auditTypography(fautive)
        assertTrue("le verrou n'a pas mordu sur 19 sp", v.any { it.category == SocleAudit.Category.FONT_SIZE && it.raw == "19" })
        val s = SocleAudit.auditSpacing(fautive)
        assertTrue("le verrou n'a pas mordu sur 7 dp", s.any { it.category == SocleAudit.Category.SPACED_BY && it.raw == "7" })
        assertTrue("le verrou n'a pas mordu sur 17 dp de rayon", s.any { it.category == SocleAudit.Category.RADIUS && it.raw == "17" })
        val propre = """
            Text(text = "x", fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {}
            Box(Modifier.clip(RoundedCornerShape(20.dp)))
        """.trimIndent()
        assertTrue("le verrou crie sur une source conforme : " + SocleAudit.auditTypography(propre), SocleAudit.auditTypography(propre).isEmpty())
        assertTrue("le verrou crie sur une source conforme : " + SocleAudit.auditSpacing(propre), SocleAudit.auditSpacing(propre).isEmpty())
    }

    @Test
    fun `le verrou mord sur les routes qui n etaient pas couvertes avant`() {
        // **La morsure de l'extension.** L'ancien verrou ne lisait que quatre écrans du socle.
        // Ici on prend le source **réel** d'écrans qui n'étaient pas couverts, on y injecte une
        // valeur fautive, et le verrou doit la nommer — fichier et ligne compris.
        MORSURES.forEach { (path, injected, expectedLine) ->
            val src = read(path)
            assertTrue("le fichier « " + path + " » est introuvable pour la morsure", src.isNotEmpty())
            // Source propre : le verrou ne doit rien dire.
            assertEquals(
                "le verrou crie déjà sur « " + path + " » : " + SocleAudit.auditTypography(src),
                0,
                SocleAudit.auditTypography(src).size,
            )
            // Source fautive : la même, avec une taille de 19 sp posée sur la première ligne de texte.
            val lines = src.lines().toMutableList()
            lines.add(expectedLine - 1, "        Text(text = \"morsure\", fontSize = " + injected + ".sp)")
            val mordu = SocleAudit.auditTypography(lines.joinToString("\n"))
            assertTrue(
                "le verrou n'a pas mordu sur " + injected + " sp dans " + path,
                mordu.any { it.category == SocleAudit.Category.FONT_SIZE && it.raw == injected },
            )
            val hit = mordu.first { it.category == SocleAudit.Category.FONT_SIZE && it.raw == injected }
            assertEquals("la ligne nommée n'est pas la bonne dans " + path, expectedLine, hit.line)
        }
    }

    @Test
    fun `le plancher mord meme quand une exception declaree le couvrirait`() {
        // Une valeur sous 11 sp est un **défaut**, pas une exception : même la liste ne l'excuse pas.
        val trop_petit = """Text(text = "x", fontSize = 10.sp)"""
        val v = SocleAudit.auditTypography(trop_petit)
        assertTrue("le plancher n'a pas mordu sur 10 sp", v.any { it.category == SocleAudit.Category.FLOOR })
        val divise = """Text(text = "A", fontSize = (12 / fontScale).coerceAtLeast(6f).sp)"""
        assertTrue(
            "le verrou n'a pas vu la division par l'échelle de police",
            SocleAudit.auditTypography(divise).any { it.category == SocleAudit.Category.DIVIDED_BY_SCALE },
        )
    }

    // ============================================================================================

    private fun read(path: String): String =
        listOf(File(path), File("../" + path), File("../../" + path))
            .firstOrNull { it.isFile }?.readText() ?: ""

    companion object {
        /** Le corps nominal d'une puce et d'un onglet — pour la table de routes du rapport. */
        const val FLOOR_SP = 11f

        /**
         * **La table des routes.** Une entrée par route (écran), et pour chacune ses fichiers : son
         * écran, ses feuilles, ses dialogues, ses rangées, ses pastilles, ses états vides et ses
         * messages d'erreur. C'est cette table que le verrou parcourt, et c'est elle que le rapport
         * imprime.
         */
        val ROUTES: Map<String, List<String>> = mapOf(
            "Appels" to listOf(
                "app/src/main/java/com/onyx/app/ui/calls/IncomingCallScreen.kt",
            ),
            "Blocs riches et correctifs" to listOf(
                "core/src/main/java/com/onyx/app/ui/rich/AgentPageBlock.kt",
                "core/src/main/java/com/onyx/app/ui/rich/CodeBlock.kt",
                "core/src/main/java/com/onyx/app/ui/rich/PatchScreen.kt",
                "core/src/main/java/com/onyx/app/ui/rich/RichBlocks.kt",
                "core/src/main/java/com/onyx/app/ui/rich/RichWebView.kt",
                "core/src/main/java/com/onyx/app/ui/rich/StreamingTail.kt",
            ),
            "Bots, salons et fiches de bot" to listOf(
                "app/src/main/java/com/onyx/app/ui/bots/APIBotEditorScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/BotFacePickerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/BotVoicePickerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/BotsArrange.kt",
                "app/src/main/java/com/onyx/app/ui/bots/BotsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/GroupComposer.kt",
                "app/src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/HiddenBotsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt",
            ),
            "Capacités, compétences et MCP" to listOf(
                "app/src/main/java/com/onyx/app/ui/capabilities/CapabilitiesScreen.kt",
                "app/src/main/java/com/onyx/app/ui/capabilities/CapabilityParts.kt",
                "app/src/main/java/com/onyx/app/ui/capabilities/McpPages.kt",
                "app/src/main/java/com/onyx/app/ui/capabilities/SkillEditorScreen.kt",
                "app/src/main/java/com/onyx/app/ui/capabilities/SkillPages.kt",
            ),
            "Capture rapide" to listOf(
                "app/src/main/java/com/onyx/app/ui/capture/QuickCaptureScreen.kt",
            ),
            "Cartes de réponse" to listOf(
                "core/src/main/java/com/onyx/app/ui/cards/ReplyCardFormat.kt",
            ),
            "Code" to listOf(
                "app/src/main/java/com/onyx/app/ui/code/CodeScreen.kt",
                "app/src/main/java/com/onyx/app/ui/code/CodeSessionActions.kt",
            ),
            "Communs — cartes, états, toasts" to listOf(
                "app/src/main/java/com/onyx/app/ui/common/AcknowledgementsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/common/AdaptiveSegmented.kt",
                "app/src/main/java/com/onyx/app/ui/common/ConnectionProblemCard.kt",
                "app/src/main/java/com/onyx/app/ui/common/LazyCard.kt",
                "app/src/main/java/com/onyx/app/ui/common/PageStates.kt",
                "app/src/main/java/com/onyx/app/ui/common/TipJarScreen.kt",
                "app/src/main/java/com/onyx/app/ui/common/Toast.kt",
                "app/src/main/java/com/onyx/app/ui/common/TypedTitle.kt",
                "app/src/main/java/com/onyx/app/ui/common/WhatsNewScreen.kt",
            ),
            "Contexte et mémoire" to listOf(
                "app/src/main/java/com/onyx/app/ui/context/ContextFileEditorScreen.kt",
                "app/src/main/java/com/onyx/app/ui/context/ContextFileEditorViewModel.kt",
                "app/src/main/java/com/onyx/app/ui/context/ContextScreen.kt",
            ),
            "Conversations et projets" to listOf(
                "app/src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt",
                "app/src/main/java/com/onyx/app/ui/conversations/ConversationShared.kt",
                "app/src/main/java/com/onyx/app/ui/conversations/ProjectSessionsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/conversations/SessionUsageScreen.kt",
            ),
            "Coquille de navigation (OnyxApp)" to listOf(
                "app/src/main/java/com/onyx/app/ui/OnyxApp.kt",
            ),
            "Discussion — écran, feuilles et rangées" to listOf(
                "app/src/main/java/com/onyx/app/ui/chat/AudioPlayerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/CameraScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ChatControlsSection.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ChatPanels.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ComposerEditorScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/ConversationFilesController.kt",
                "app/src/main/java/com/onyx/app/ui/chat/DeliveredFiles.kt",
                "app/src/main/java/com/onyx/app/ui/chat/DocumentScreens.kt",
                "app/src/main/java/com/onyx/app/ui/chat/FilesTab.kt",
                "app/src/main/java/com/onyx/app/ui/chat/OutboxQueue.kt",
                "app/src/main/java/com/onyx/app/ui/chat/PdfViewerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/PhotoViewerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/QueuedMessageRow.kt",
                "app/src/main/java/com/onyx/app/ui/chat/SelectableTextScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/SlashCommands.kt",
                "app/src/main/java/com/onyx/app/ui/chat/SubagentListScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/SubagentTranscriptScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/TranscriptExport.kt",
                "app/src/main/java/com/onyx/app/ui/chat/VideoPlayerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ApprovalCard.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/BranchFromHereAlert.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ContextCard.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/FollowUpChips.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/InterruptCards.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/LinkPreviewCard.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/MarkdownText.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/MentionSuggestions.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/OsmMapImage.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ReplyCardViews.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/StartScreen.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/SubagentRows.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ThreadOrganisation.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/TodoChecklist.kt",
                "app/src/main/java/com/onyx/app/ui/chat/components/ToolCallDetail.kt",
            ),
            "Fichiers" to listOf(
                "app/src/main/java/com/onyx/app/ui/files/FilesScreen.kt",
                "app/src/main/java/com/onyx/app/ui/files/FilesViewModel.kt",
            ),
            "Flux (Insights)" to listOf(
                "app/src/main/java/com/onyx/app/ui/insights/InsightsScreen.kt",
            ),
            "Installation (setup)" to listOf(
                "app/src/main/java/com/onyx/app/ui/setup/SetupAnatomy.kt",
                "app/src/main/java/com/onyx/app/ui/setup/SetupCodeScanner.kt",
                "app/src/main/java/com/onyx/app/ui/setup/SetupCodeSheet.kt",
                "app/src/main/java/com/onyx/app/ui/setup/SetupScreen.kt",
                "app/src/main/java/com/onyx/app/ui/setup/SetupSuggestions.kt",
            ),
            "Journal d'agent (Ledger)" to listOf(
                "app/src/main/java/com/onyx/app/ui/ledger/AgentLedgerScreen.kt",
            ),
            "Kanban" to listOf(
                "app/src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt",
                "app/src/main/java/com/onyx/app/ui/kanban/KanbanSwipe.kt",
            ),
            "Modèles" to listOf(
                "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt",
                "app/src/main/java/com/onyx/app/ui/models/ModelsScreen.kt",
            ),
            "Ordinateur" to listOf(
                "app/src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/computer/ComputerSession.kt",
                "app/src/main/java/com/onyx/app/ui/computer/ComputerStreamPanel.kt",
            ),
            "Outils" to listOf(
                "app/src/main/java/com/onyx/app/ui/tools/ToolsScreen.kt",
            ),
            "Réglages" to listOf(
                "app/src/main/java/com/onyx/app/ui/settings/AndroidAutoSettings.kt",
                "app/src/main/java/com/onyx/app/ui/settings/AppIconSettings.kt",
                "app/src/main/java/com/onyx/app/ui/settings/AppearanceRows.kt",
                "app/src/main/java/com/onyx/app/ui/settings/CallsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/ConnectionDetailsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/GatewayCard.kt",
                "app/src/main/java/com/onyx/app/ui/settings/GatewayScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/LockSettingsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/LogsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/LogsViewModel.kt",
                "app/src/main/java/com/onyx/app/ui/settings/PhoneToolsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/PushSettingsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/VoiceSettingsScreen.kt",
            ),
            "Réglages — Voix" to listOf(
                "app/src/main/java/com/onyx/app/ui/settings/voice/HermesVoiceRows.kt",
                "app/src/main/java/com/onyx/app/ui/settings/voice/KokoroVoicesView.kt",
                "app/src/main/java/com/onyx/app/ui/settings/voice/LanguageScreens.kt",
                "app/src/main/java/com/onyx/app/ui/settings/voice/SpokenPrefix.kt",
                "app/src/main/java/com/onyx/app/ui/settings/voice/SpokenPrefixesScreen.kt",
                "app/src/main/java/com/onyx/app/ui/settings/voice/StopPhrasesScreen.kt",
            ),
            "Serveurs et profils" to listOf(
                "app/src/main/java/com/onyx/app/ui/servers/ProfilePickerScreen.kt",
                "app/src/main/java/com/onyx/app/ui/servers/ServersScreen.kt",
            ),
            "Socle, anatomie et composants partagés" to listOf(
                "core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt",
                "core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt",
                "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt",
                "core/src/main/java/com/onyx/app/ui/design/ChipRowFit.kt",
                "core/src/main/java/com/onyx/app/ui/design/ContentAnatomy.kt",
                "core/src/main/java/com/onyx/app/ui/design/CounterBadge.kt",
                "core/src/main/java/com/onyx/app/ui/design/FileThumbnailView.kt",
                "core/src/main/java/com/onyx/app/ui/design/Galerie.kt",
                "core/src/main/java/com/onyx/app/ui/design/IdentityMark.kt",
                "core/src/main/java/com/onyx/app/ui/design/HouseSheet.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxAlert.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxAnatomy.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxControls.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxConfirm.kt",
                "core/src/main/java/com/onyx/app/ui/design/OnyxTextPolicy.kt",
                "core/src/main/java/com/onyx/app/ui/design/OpeningCover.kt",
                "core/src/main/java/com/onyx/app/ui/design/SegmentedFit.kt",
                "core/src/main/java/com/onyx/app/ui/design/SelectableText.kt",
                "core/src/main/java/com/onyx/app/ui/design/SocleKit.kt",
            ),
            "Terminal" to listOf(
                "app/src/main/java/com/onyx/app/ui/terminal/TerminalScreen.kt",
            ),
            "Texte localisé (résolveur)" to listOf(
                "core/src/main/java/com/onyx/app/ui/localization/OnyxBasicText.kt",
                "core/src/main/java/com/onyx/app/ui/localization/OnyxTextResolver.kt",
            ),
            "Thème — l'échelle elle-même" to listOf(
                "core/src/main/java/com/onyx/app/ui/theme/ResolvedTheme.kt",
            ),
            "Tâches planifiées (Cron)" to listOf(
                "app/src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt",
                "app/src/main/java/com/onyx/app/ui/cron/CronScreen.kt",
            ),
            "Verrouillage" to listOf(
                "app/src/main/java/com/onyx/app/ui/lock/LockScreen.kt",
            ),
            "Voix" to listOf(
                "app/src/main/java/com/onyx/app/ui/voice/RisingCaption.kt",
                "app/src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt",
            ),
            "Volet et barre latérale" to listOf(
                "app/src/main/java/com/onyx/app/ui/sidebar/AccountSheet.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/ArchivedScreen.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/SidebarScaffold.kt",
                "app/src/main/java/com/onyx/app/ui/sidebar/TileLabelFit.kt",
            ),
            "Widget d'écran d'accueil" to listOf(
                "app/src/main/java/com/onyx/app/widget/OnyxWidget.kt",
            ),
        )

        /**
         * **Les morsures.** Trois écrans que l'ancien verrou **ne lisait pas** : la table des tâches,
         * l'écran Code et le Flux. On y injecte 19 sp — un corps qui n'existe nulle part — et le
         * verrou doit le nommer, avec le fichier et la ligne.
         */
        val MORSURES: List<Triple<String, String, Int>> = listOf(
            Triple("app/src/main/java/com/onyx/app/ui/kanban/KanbanScreen.kt", "19", 40),
            Triple("app/src/main/java/com/onyx/app/ui/code/CodeScreen.kt", "19", 30),
            Triple("app/src/main/java/com/onyx/app/ui/insights/InsightsScreen.kt", "19", 25),
        )

        /**
         * **Le balayage de l'arbre.** Tout fichier d'interface qui rend du texte doit figurer dans
         * [ROUTES] : un fichier ajouté demain sans route déclarée échoue au test de couverture.
         */
        val UI_TREE: List<String> = interfaceFiles()

        /** Les écrans de la vague 1 et 3, ceux dont le socle gouverne aussi les espacements. */
        val SOCLE_SCREENS = listOf(
            "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
            "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt",
            "app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt",
            "core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt",
            "app/src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt",
            "app/src/main/java/com/onyx/app/ui/settings/GatewayCard.kt",
            "app/src/main/java/com/onyx/app/ui/capabilities/CapabilitiesScreen.kt",
            "app/src/main/java/com/onyx/app/ui/models/ModelSheet.kt",
        )

        /** Les fichiers du socle lui-même : ils ne peuvent rien porter hors socle. */
        val SOCLE_FILES = listOf(
            "core/src/main/java/com/onyx/app/ui/design/OnyxSocle.kt",
            "core/src/main/java/com/onyx/app/ui/design/SocleShadow.kt",
            "core/src/main/java/com/onyx/app/ui/design/ConversationCover.kt",
        )

        /**
         * **Les exceptions de source, déclarées et gelées.** Vide : plus aucune valeur d'espacement
         * n'est tolérée hors échelle dans les écrans du socle.
         */
        val BASELINE: Map<String, Map<SocleAudit.Category, Set<String>>> = emptyMap()
    }
}


/**
 * **Des fichiers qui ne sont pas des écrans, et dont le source porte pourtant la forme cherchée.**
 *
 * Nommés un par un — c'est le prix d'un lecteur qui relève des **formes d'écriture** et non l'arbre
 * syntaxique ([interfaceFiles]).
 *
 * `ui/setup/QrCodeDecoder.kt` : le résultat d'un décodage s'y nomme `ScanOutcome.Text(...)` — une
 * **valeur** décodée, pas un texte peint. Rien n'y dessine, et il n'a donc pas de route à déclarer.
 */
private val NOT_SCREENS: Set<String> = setOf("ui/setup/QrCodeDecoder.kt")

/** Les racines d'interface et le préfixe de clé qui va avec (`com/onyx/app/`). */
private val UI_ROOTS: List<Pair<String, String>> = listOf(
    "app/src/main/java/com/onyx/app/ui" to "ui/",
    "core/src/main/java/com/onyx/app/ui" to "ui/",
    "app/src/main/java/com/onyx/app/widget" to "widget/",
)

/**
 * Tous les fichiers d'interface **qui portent du texte**, sous la clé `ui/…` ou `widget/…`
 * (relatif à `com/onyx/app/`).
 *
 * Le filtre est le même que celui de la table : un fichier qui écrit `Text(`, une taille ou une
 * famille est un fichier que le verrou doit lire. Un modèle de vue, une palette ou un dessin
 * vectoriel n'a rien à y faire — et c'est **le filtre qui décide**, pas une liste tenue à la main.
 */
private fun interfaceFiles(): List<String> {
    val out = mutableListOf<String>()
    UI_ROOTS.forEach { (root, prefix) ->
        val dir = listOf(File(root), File("../" + root), File("../../" + root))
            .firstOrNull { it.isDirectory } ?: return@forEach
        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach {
                val src = it.readText()
                val carriesText = src.contains("Text(") || src.contains("fontSize") || src.contains("fontFamily")
                if (carriesText) out += prefix + it.relativeTo(dir).path.replace(File.separatorChar, '/')
            }
    }
    return out
}

/** La clé d'un chemin déclaré : ce qui suit `com/onyx/app/`. */
private fun key(path: String): String = path.substringAfter("com/onyx/app/", path)

/**
 * **Le lecteur du source.** Il relève, dans un texte Kotlin, les formes d'écriture qui portent les
 * valeurs du socle : une taille de texte, un écart, un rayon, une épaisseur de trait, une famille.
 * Il ne prétend pas lire l'arbre syntaxique — il lit les **formes d'écriture** qui portent ces
 * valeurs, et c'est suffisant pour fermer la dérive : ces formes sont la seule manière d'écrire un
 * nombre en dur pour ces jetons.
 *
 * **Deux audits, et non un seul.** La typographie se vérifie sur **toutes** les routes ; les
 * espacements et les rayons, encore en chantier, se vérifient sur les écrans du socle. Les mélanger
 * ferait un test qui crie sur quarante fichiers pour une raison qui n'est pas la sienne.
 */
object SocleAudit {

    enum class Category { FONT_SIZE, FLOOR, DIVIDED_BY_SCALE, FAMILY, SPACED_BY, PADDING, RADIUS, STROKE_WIDTH }

    data class Violation(val category: Category, val raw: String, val line: Int)

    private val FONT_SIZE = Regex("""fontSize\s*=\s*([0-9.]+)\.sp""")
    private val SP_LITERAL = Regex("""([0-9]+(?:\.[0-9]+)?)\.sp\b""")
    private val DIVIDED = Regex("""fontSize\s*=[^\n]*/\s*[A-Za-z0-9_.]*fontScale""")
    private val FAMILY = Regex("""fontFamily\s*=\s*([A-Za-z0-9_.]+)""")
    private val SPACED_BY = Regex("""spacedBy\(\s*([0-9.]+)\.dp\s*\)""")
    private val PADDING = Regex("""\.padding\(\s*([0-9.]+)\.dp\s*\)""")
    private val RADIUS = Regex("""RoundedCornerShape\(\s*([0-9.]+)\.dp\s*\)""")
    private val STROKE = Regex("""strokeWidth\s*=\s*([0-9.]+)""")
    private val QUOTED = Regex("\"[^\"\n]*\"")

    /**
     * Les formes d'écriture qui portent un `.sp` **sans être un corps** : un interlettrage, le pas
     * d'un auto-ajustement, une hauteur de ligne. Les confondre avec un corps ferait crier le verrou
     * sur du texte juste.
     */
    private val NOT_A_BODY = listOf("letterSpacing", "stepSize", "lineHeight", "lineSpacing")

    /** Les familles admises : l'interface (sans-serif) et les données (monospace). */
    private val ALLOWED_FAMILIES = setOf("FontFamily.Monospace", "FontFamily.SansSerif", "FontFamily.Default")

    /** Les valeurs de correction optique tolérées pour un écart (elles ne séparent pas deux blocs). */
    private val OPTICAL = OnyxSocle.Optical.map { it.value }.toSet()

    /** Le numéro de ligne d'un index de caractère. */
    private fun lineOf(source: String, index: Int): Int {
        var n = 1
        val end = minOf(index, source.length)
        for (i in 0 until end) if (source[i] == '\n') n++
        return n
    }

    /** Le source avec le **contenu des chaînes** remplacé par des espaces — les lignes ne bougent pas. */
    private fun maskStrings(source: String): String =
        QUOTED.replace(source) { " ".repeat(it.value.length) }

    /** Vrai si cette valeur est un corps **déclaré** dans [OnyxSocle.exceptions]. */
    private fun isDeclared(raw: String): Boolean =
        OnyxSocle.exceptions.any { it.value.startsWith(raw + ".sp") }

    /**
     * **L'audit typographique** — celui qui court sur toutes les routes.
     *
     * Trois refus : un corps hors des six (sauf exception déclarée), un corps **sous 11 sp** (sans
     * exception possible), une taille **divisée par l'échelle de police** (une taille qui rétrécit
     * quand la police du système grandit).
     */
    fun auditTypography(source: String): List<Violation> {
        val out = mutableListOf<Violation>()
        val masked = maskStrings(source)
        SP_LITERAL.findAll(masked).forEach { m ->
            val before = masked.substring(maxOf(0, m.range.first - 64), m.range.first)
            if (NOT_A_BODY.any { before.contains(it) }) return@forEach
            val raw = m.groupValues[1]
            val value = raw.toFloat()
            val line = lineOf(source, m.range.first)
            if (value < OnyxSocle.Micro.value - 0.01f) {
                out += Violation(Category.FLOOR, raw, line)
            } else if (!OnyxSocle.isOnScale(value.sp) && !isDeclared(raw)) {
                out += Violation(Category.FONT_SIZE, raw, line)
            }
        }
        DIVIDED.findAll(masked).forEach { m ->
            out += Violation(Category.DIVIDED_BY_SCALE, "fontSize / fontScale", lineOf(source, m.range.first))
        }
        return out
    }

    /**
     * **L'audit des familles.** Une seule famille d'interface, une seule pour les identifiants et
     * les nombres. Est refusé : toute `FontFamily.` explicite qui n'est ni `Monospace`, ni
     * `SansSerif`, ni `Default` — le serif et le cursif n'ont pas leur place dans une interface
     * dont l'échelle est déjà tenue.
     */
    fun auditFamilies(source: String): List<Violation> {
        val out = mutableListOf<Violation>()
        val masked = maskStrings(source)
        FAMILY.findAll(masked).forEach { m ->
            val raw = m.groupValues[1]
            if (!raw.contains("FontFamily")) return@forEach
            val family = "FontFamily." + raw.substringAfterLast("FontFamily.")
            if (family !in ALLOWED_FAMILIES) {
                out += Violation(Category.FAMILY, family, lineOf(source, m.range.first))
            }
        }
        return out
    }

    /** Le nombre de `FontDesign.SERIF` d'un fichier — la famille nommée des thèmes de lecture. */
    fun countSerifDesigns(source: String): Int =
        Regex("""FontDesign\.SERIF""").findAll(maskStrings(source)).count()

    /** **L'audit des espacements et des rayons** — les écrans du socle seulement, pour l'instant. */
    fun auditSpacing(source: String): List<Violation> {
        val out = mutableListOf<Violation>()
        val masked = maskStrings(source)
        SPACED_BY.findAll(masked).forEach { m ->
            val v = m.groupValues[1].toFloat()
            if (!OnyxSocle.isOnScale(v.dp) && v !in OPTICAL) {
                out += Violation(Category.SPACED_BY, m.groupValues[1], lineOf(source, m.range.first))
            }
        }
        PADDING.findAll(masked).forEach { m ->
            val v = m.groupValues[1].toFloat()
            if (!OnyxSocle.isOnScale(v.dp) && v !in OPTICAL) {
                out += Violation(Category.PADDING, m.groupValues[1], lineOf(source, m.range.first))
            }
        }
        RADIUS.findAll(masked).forEach { m ->
            val v = m.groupValues[1].toFloat()
            if (!OnyxSocle.isRadiusOnScale(v.dp)) {
                out += Violation(Category.RADIUS, m.groupValues[1], lineOf(source, m.range.first))
            }
        }
        STROKE.findAll(masked).forEach { m ->
            out += Violation(Category.STROKE_WIDTH, m.groupValues[1], lineOf(source, m.range.first))
        }
        return out
    }

    /** L'audit complet d'un fichier du socle : typographie + espacements. */
    fun audit(source: String): List<Violation> = auditTypography(source) + auditSpacing(source)
}
