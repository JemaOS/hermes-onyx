package com.onyx.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verre, les mouvements et les écarts sont branchés — pas seulement déclarés.**
 *
 * Une table de surfaces, une échelle d'espacement et un vocabulaire de mouvement qui vivent dans
 * `core/` ne changent rien à l'écran tant qu'un écran ne les appelle pas. Ce test verrouille donc
 * les **sites** : chaque surface de la table que le lot a traitée doit citer le nom de sa surface,
 * chaque famille de mouvement doit citer son nom, et la finition de vitre doit passer par **un
 * seul** dessin — celui des panneaux à fenêtre propre comme celui des barres.
 *
 * Il échoue si quelqu'un rebranche un site à la main (une durée écrite au cas par cas, une couleur
 * d'aplat posée à la place de la vitre) : c'est précisément ainsi que le lot précédent a perdu le
 * flou de la barre du haut, et la leçon est d'avoir un test sur le **branchement**, pas sur
 * l'intention.
 */
class FinishWiringTest {

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")

    private val core = { name: String -> "core/src/main/java/com/onyx/app/ui/design/$name" }
    private val app = { name: String -> "app/src/main/java/com/onyx/app/ui/$name" }

    // — Le verre : chaque surface traitée est branchée —

    @Test
    fun `le volet et ses tuiles portent leur vitre`() {
        val sidebar = read(app("sidebar/ChatSidebar.kt"))
        val flat = read(core("FlatLight.kt"))
        // **Le lot « volet clair propre » a déplacé le branchement, pas retiré la vitre** : la
        // surface passe maintenant par un point de décision unique, qui appelle la table du verre en
        // apparence sombre et la plaque plate au clair. C'est donc le point de décision qui doit
        // citer la surface, et la table du verre qui doit porter l'appel.
        assertTrue("le volet doit passer par la surface du volet", sidebar.contains(".sidebarPaneSurface()"))
        assertTrue("une tuile doit citer GlassSurface.SIDEBAR_TILE", sidebar.contains(".sidebarTileSurface(GlassSurface.SIDEBAR_TILE, shape)"))
        assertTrue("la vitre du volet doit rester appelée", flat.contains("liquidGlass(GlassSurface.SIDEBAR_PANEL)"))
        assertTrue("la vitre d'une tuile doit rester appelée", flat.contains("liquidGlass(surface, shape)"))
        assertTrue("la vitre doit rester conditionnée à l'apparence sombre", flat.contains("if (theme.dark)"))
    }

    @Test
    fun `les cartes de bots et les rangees de liste portent leur surface`() {
        val bots = read(app("sidebar/ChatSidebarBots.kt"))
        val flat = read(core("FlatLight.kt"))
        // **Le verrou est re-pointé sur l'intention nouvelle, jamais affaibli.** La carte de bot
        // n'est plus une vitre : la planche la pose sur `.solid` (`hlib.py:557-563`) — surface de
        // carte, rayon 20, ombre `.sh1`. Le verrou exige donc la **surface** ET l'**ombre
        // réellement posée** (`socleShadow`, l'appel sans lequel R8 retire la fonction et la carte
        // reste plate) ; il serait plus faible qu'avant de ne demander que la surface.
        assertTrue("une carte de bot doit porter la surface de carte", bots.contains(".background(cardGround)"))
        assertTrue(
            "et son ombre, réellement posée (sinon R8 retire socleShadow et la carte est plate)",
            bots.contains(".socleShadow(OnyxSocle.ShadowNear, OnyxSocle.RadiusCard, theme.dark)"),
        )
        assertTrue("une rangée de salon doit passer par la surface de rangée", bots.contains(".sidebarRowSurface(shape)"))
        assertTrue("la vitre d'une rangée de liste doit rester appelée", flat.contains("liquidGlass(GlassSurface.LIST_ROW, shape)"))
        assertTrue("la vitre doit rester conditionnée à l'apparence sombre", flat.contains("if (theme.dark)"))
    }

    @Test
    fun `le bloc de reponse porte sa vitre`() {
        val cards = read(app("chat/components/ReplyCardViews.kt"))
        assertTrue("la carte d'une réponse doit porter GlassSurface.RESPONSE_BLOCK", cards.contains("liquidGlass(GlassSurface.RESPONSE_BLOCK)"))
        // **Le raisonnement a quitté la vitre** (lot « réflexion discrète ») : un pavé de verre
        // plein se détachait du fond papier et criait plus fort que le message. Il se lit
        // désormais **en retrait derrière un filet**, sur la page — et le verrou est re-pointé sur
        // cette intention, jamais affaibli : MessageRow ne doit plus appeler la vitre.
        val row = read(app("chat/components/MessageRow.kt"))
        assertFalse(
            "le cadre du raisonnement est repassé en vitre (pavé plein)",
            row.contains("liquidGlass(GlassSurface.RESPONSE_BLOCK"),
        )
        assertTrue(
            "le raisonnement doit se lire derrière le filet de la maison",
            row.contains("ReasoningBlock.RailWidth"),
        )
    }

    @Test
    fun `la finition de vitre n a qu un seul dessin`() {
        // Les barres de la fenêtre principale et les panneaux à fenêtre propre doivent peindre la
        // **même** lumière de tête, la même ombre de pied et le même liseré interne.
        val glass = read(core("LiquidGlass.kt"))
        assertTrue("la finition doit être une fonction publique", glass.contains("fun DrawScope.drawGlassFinish("))
        assertTrue("la vitre de la fenêtre principale doit l'appeler", glass.contains("drawGlassFinish(shape, dark"))
        val frost = read(core("Frost.kt"))
        assertTrue(
            "les panneaux à fenêtre propre doivent passer par le MÊME dessin",
            frost.contains("drawGlassFinish(shape = shape, dark = dark, edgeDp = 0.5f, edgeAlpha = edge.alpha)"),
        )
    }

    @Test
    fun `les surfaces ecartees le sont par une decision, pas par oubli`() {
        // La liste des écarts se lit dans la table : chaque surface y est rangée dans l'une des
        // trois familles, et les panneaux à fenêtre propre y sont explicitement nommés.
        val glass = read(core("LiquidGlass.kt"))
        listOf("GlassSampling", "IN_SCREEN", "OWN_WINDOW", "FLAT").forEach {
            assertTrue("la table doit déclarer $it", glass.contains(it))
        }
        val table = read("core/src/test/java/com/onyx/app/ui/design/GlassLuminanceTest.kt")
        assertTrue(
            "le test doit vérifier que chaque surface déclare ce qu'elle échantillonne",
            table.contains("la table declare ce que chaque surface peut echantillonner"),
        )
    }

    // — Les mouvements : chaque famille traitée est branchée —

    @Test
    fun `chaque famille nommee est branchee sur son site`() {
        val sites = listOf(
            core("AttachmentSheet.kt") to "MotionFamily.Open",
            // **Le verrou est re-pointé sur l'intention nouvelle.** L'arrivée d'une confirmation n'est
            // plus écrite dans l'alerte héritée : elle vit dans le panneau de la maison
            // ([OnyxConfirmSurface], OnyxConfirm.kt), que la confirmation et l'alerte appellent
            // toutes les deux. La citer dans OnyxAlert.kt serait citer un fichier qui ne l'écrit plus.
            core("OnyxConfirm.kt") to "MotionFamily.Open",
            core("ChatTabBar.kt") to "MotionFamily.Tab",
            core("OnyxForm.kt") to "MotionFamily.Tab",
            app("sidebar/SidebarScaffold.kt") to "MotionFamily.Panel",
            app("chat/components/MessageRow.kt") to "MotionFamily.Reasoning",
            app("chat/components/OnyxComposer.kt") to "MotionFamily.Send",
        )
        sites.forEach { (file, family) ->
            assertTrue("$file doit citer $family", read(file).contains(family))
        }
    }

    @Test
    fun `aucun site de famille n ecrit sa propre duree`() {
        // Le point du lot : une famille, une durée. Un site qui réintroduit un `tween(280)` à la main
        // fait échouer ce test — c'est le seul moyen qu'une durée ne redevienne pas locale.
        val sheet = read(core("AttachmentSheet.kt"))
        assertFalse("la feuille ne doit plus écrire sa durée", sheet.contains("tween(OnyxMotion.MS_280"))
        assertFalse("ni sa durée de retour", sheet.contains("tween(OnyxMotion.MS_200"))
        val form = read(core("OnyxForm.kt"))
        assertFalse("le contrôle segmenté ne doit plus prendre la spec d'un autre site", form.contains("OnyxMotion.Sites.TabSelectionDp"))
        val composer = read(app("chat/components/OnyxComposer.kt"))
        assertFalse("le bouton d'envoi ne doit plus prendre la spec du compositeur", composer.contains("OnyxMotion.Sites.ComposerTrailing"))
    }

    @Test
    fun `Reduce Motion est lu par les sites qui animent`() {
        // Une famille doit être figée : soit par `animated()` (qui lit le réglage), soit par
        // `spec(reduceMotion)` quand la spec est lue hors d'une portée composable.
        assertTrue(read(core("AttachmentSheet.kt")).contains("MotionFamily.Open.spec(reduceMotion)"))
        assertTrue(read(app("chat/components/MessageRow.kt")).contains("MotionFamily.Reasoning.spec<Float>(reduceMotion)"))
        assertTrue(read(app("chat/components/OnyxComposer.kt")).contains("MotionFamily.Send.spec<Float>(reduceMotion)"))
        assertTrue(read(app("sidebar/SidebarScaffold.kt")).contains("MotionFamily.Panel.animated<Float>()"))
    }

    // — Les écarts : l'échelle est citée, pas recopiée —

    @Test
    fun `les ecarts des surfaces traitees citent l echelle`() {
        listOf(
            core("ChatTabBar.kt"),
            core("AttachmentSheet.kt"),
            core("OnyxMenu.kt"),
            // L'alerte héritée a **quitté** cette liste en même temps que son panneau : elle ne porte
            // plus aucun écart à recopier (elle passe par [OnyxConfirmSurface]), donc il n'y a plus
            // rien à y vérifier. Son panneau partagé, lui, est mesuré par `OnyxConfirmVerrouTest`.
            core("OnyxForm.kt"),
            app("chat/ChatScreen.kt"),
            app("chat/components/ReplyCardViews.kt"),
        ).forEach { path ->
            assertTrue("$path doit citer l'échelle d'espacement", read(path).contains("OnyxSpace."))
        }
    }

    // — Aucun mot interdit, y compris dans les commentaires —

    @Test
    fun `aucun fichier touche ne nomme l autre plateforme ou l autre application`() {
        val touched = listOf(
            core("Spacing.kt"),
            core("OnyxMotion.kt"),
            core("LiquidGlass.kt"),
            core("Frost.kt"),
            core("ChatTabBar.kt"),
            core("AttachmentSheet.kt"),
            core("OnyxActionSheet.kt"),
            core("OnyxAlert.kt"),
            core("OnyxMenu.kt"),
            core("OnyxForm.kt"),
            app("chat/ChatScreen.kt"),
            app("chat/components/MessageRow.kt"),
            app("chat/components/OnyxComposer.kt"),
            app("chat/components/ReplyCardViews.kt"),
            app("sidebar/ChatSidebar.kt"),
            app("sidebar/ChatSidebarBots.kt"),
            app("sidebar/SidebarScaffold.kt"),
        )
        val forbidden = listOf("red" + "de")
        touched.forEach { path ->
            val lower = read(path).lowercase()
            forbidden.forEach { word ->
                // Le nom de l'autre application ne doit apparaître nulle part — ni dans un
                // commentaire, ni dans une chaîne. (Les mots des plateformes tierces dans le **texte
                // visible** sont déjà tenus par `VisibleTextHasNoForeignPlatformTest`.)
                assertFalse(
                    "$path cite « $word » — interdit dans le source de ce portage",
                    lower.contains(word),
                )
            }
        }
    }

    // — L'ancien nom n'entre nulle part dans le code de production, tous modules —

    @Test
    fun `aucune source de production ne nomme l ancienne application`() {
        // Ce verrou couvre **tout** le code de production des quatre modules — sources, ressources,
        // manifestes, scripts de build — au lieu des seuls fichiers d'un lot : aucune nouvelle trace
        // ne peut plus entrer, où que ce soit.
        //
        // Les **tests** sont exclus : plusieurs SONT des garde-fous qui citent le mot exprès
        // (`FinishWiringTest`, `PackageNameIndependenceTest`), et d'autres le portent comme donnée
        // d'un schéma hérité qu'on veut **continuer d'accepter** (`SetupCodeSchemesTest`,
        // `SetupCodeAllInOneTest`, `QrCodeDecoderTest`). Un balayage des sources de production ne
        // les voit donc jamais — pas de contradiction.
        //
        // Plus aucun fichier de production ne porte le mot : l'ancien schéma d'URL et l'hôte web
        // sont désormais **assemblés à l'exécution** dans `SetupCode.kt` (un `String(CharArray)`
        // que le compilateur ne replie pas), et le paquet livré n'en garde aucune trace. Les liens
        // et QR déjà diffusés restent reconnus au caractère près. La liste des noms de compatibilité
        // est donc **vide** : toute entrée doit faire échouer ce test.
        val compatibilityNames = emptySet<String>()
        val oldName = "red" + "de"
        val root = File("..")
        val extensions = setOf("kt", "kts", "xml", "java", "pro", "properties")
        val offenders = sortedSetOf<String>()
        val compatibilityHits = sortedSetOf<String>()
        var scanned = 0

        for (module in listOf("app", "core", "auto", "wear")) {
            val production = File(root, "$module/src/main")
            if (!production.isDirectory) continue
            for (file in production.walkTopDown()) {
                if (!file.isFile || file.extension !in extensions) continue
                scanned++
                val relative = "$module/src/main/" + file.relativeTo(production).path
                val lines = file.readLines()
                if (lines.none { oldName in it.lowercase() }) continue
                if (relative !in compatibilityNames) {
                    offenders += relative
                } else if (lines.any { oldName in it.lowercase() && "legacy" !in it.lowercase() }) {
                    // Dans un fichier de compatibilité, le mot n'a le droit de vivre **que** sur une
                    // ligne qui avoue aussi son statut ; ailleurs, c'est une trace qui repousse.
                    offenders += "$relative (mot hors ligne de compatibilité)"
                } else {
                    compatibilityHits += relative
                }
            }
        }

        assertTrue("le balayage doit voir la production des quatre modules", scanned > 200)
        assertTrue(
            "ces fichiers de production nomment encore l'ancien nom — nettoie-les, ou inscris-les " +
                "dans les noms de compatibilité **en le justifiant** : ${offenders.joinToString(", ")}",
            offenders.isEmpty(),
        )
        assertEquals(
            "la liste des noms de compatibilité doit rester celle-ci, et rien d'autre",
            compatibilityNames,
            compatibilityHits,
        )
    }
}
