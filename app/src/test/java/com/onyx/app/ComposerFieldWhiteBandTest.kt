package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou de source du **champ d'écriture** et de l'**empilement du bas**.
 *
 * Le défaut mesuré, sur les deux compositeurs de l'app (une conversation de bot **et** une salle de
 * groupe) : clavier ouvert, une **bande claire** apparaissait derrière le texte du champ, et la
 * barre à cinq onglets était recouverte à moitié. Rien dans le code ne l'empêchait — ce fichier est
 * ce qui l'empêche désormais, écran par écran.
 *
 * Le calcul des couleurs et des marges, lui, est éprouvé sans appareil par `ComposerFieldTest`
 * (core). Ici on vérifie que **les écrans s'en servent vraiment**.
 */
class ComposerFieldWhiteBandTest {

    private fun read(path: String) = File(path).readText()

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val groupComposer = read("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt")
    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")

    /** Le corps du champ d'une conversation : de la définition du champ à la fin de son `Column`. */
    private fun conversationField(): String =
        composer.substringAfter("// Le fond du champ **ne peint aucun aplat**").substringBefore("val trailingStates")

    // — La bande claire derrière le texte —

    @Test
    fun `le champ d une conversation ne peint aucun aplat`() {
        val field = conversationField()
        assertTrue("le champ doit passer par la règle du lot", field.contains("composerFieldSurface("))
        assertFalse(
            "le champ peint encore une surface opaque derrière le texte",
            field.contains("background(theme.surfaceColor"),
        )
        assertFalse("le champ peint encore une vitre opaque", field.contains("glassSurface("))
    }

    @Test
    fun `le champ d une salle de groupe suit la meme regle`() {
        // **Le champ de la salle EST celui de la conversation** : la salle passe par
        // `OnyxComposer`, donc la règle du champ — aucun aplat, sélection posée localement, encart
        // du système une seule fois — n'est écrite qu'à **un** endroit, et la salle la suit.
        assertTrue("la salle doit passer par le champ de la maison", groupComposer.contains("OnyxComposer("))
        assertTrue("le champ de la maison passe par la règle du lot", composer.contains("composerFieldSurface("))
        assertFalse(
            "la salle a encore un champ à elle (un second dessin du champ)",
            groupComposer.contains("private fun GroupComposerField"),
        )
    }

    @Test
    fun `les deux champs reposent les couleurs de selection`() {
        assertTrue("la conversation doit reposer ses couleurs de champ", composer.contains("ProvideFieldTextColours(theme)"))
        assertTrue("la salle passe par le champ de la maison, qui les repose", groupComposer.contains("OnyxComposer("))
        assertFalse(
            "la salle ne repose plus ses couleurs à elle (le champ partagé le fait une fois)",
            groupComposer.contains("ProvideFieldTextColours("),
        )
    }

    /**
     * **Le test qui casse si la sélection redevient blanche.** La sélection d'un champ prend la
     * teinte de l'accent ; le seul endroit qui la pose est le thème, et il ne doit jamais y avoir de
     * blanc.
     */
    @Test
    fun `la selection du theme n est jamais blanche`() {
        val theme = read("../core/src/main/java/com/onyx/app/ui/theme/Theme.kt")
        assertTrue("la sélection doit suivre l'accent", theme.contains("backgroundColor = resolved.accent.copy"))
        assertFalse(
            "la sélection ne doit jamais être un blanc",
            theme.contains("backgroundColor = Color.White") || theme.contains("backgroundColor = androidx.compose.ui.graphics.Color.White"),
        )
        val field = read("../core/src/main/java/com/onyx/app/ui/design/ComposerField.kt")
        assertTrue("la règle du champ doit exister", field.contains("fun fieldSelectionColours("))
        assertTrue("elle doit dériver l'opacité de la lisibilité", field.contains("selectionAlphaFor("))
    }

    // — L'empilement : les deux marges ne s'additionnent pas —

    @Test
    fun `aucun compositeur ne cumule la marge du clavier et celle de la navigation`() {
        listOf(composer to "conversation", groupComposer to "salle").forEach { (source, name) ->
            // La faute d'origine : `imePadding()` immédiatement suivi de `navigationBarsPadding()`.
            assertFalse(
                "$name : les deux marges se suivent encore",
                source.contains(".imePadding()\n            .navigationBarsPadding()") ||
                    source.contains(".imePadding()\n    .navigationBarsPadding()"),
            )
        }
        // La règle vit **une fois**, dans le champ de la maison ; la salle y délègue, et c'est la
        // plaque de l'écran qui prend la marge pour toute la pile (voir le test suivant).
        assertTrue("la conversation prend la marge par la règle du lot", composer.contains("composerBottomInsets()"))
        assertTrue("la salle délègue au champ de la maison", groupComposer.contains("OnyxComposer("))
        assertTrue("la salle laisse la plaque prendre la marge une seule fois", groupComposer.contains("applyBottomInsets"))
    }

    @Test
    fun `la pile du bas prend la marge une seule fois`() {
        assertTrue("la pile doit prendre la marge système", chat.contains("composerBottomInsets()"))
        assertTrue("le champ ne doit pas la reprendre", chat.contains("applyBottomInsets = false"))
        // **La salle suit la même règle** : sa plaque prend l'encart une fois, son champ le laisse.
        val room = read("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt")
        assertTrue("la pile de la salle doit prendre la marge système", room.contains("composerBottomInsets()"))
        assertTrue("le champ de la salle ne doit pas la reprendre", room.contains("applyBottomInsets = false"))
    }

    @Test
    fun `la barre d onglets ne peut plus etre masquee, il n existe plus d etat pour cela`() {
        // **C'est le verrou du lot « retrait de la poignée ».** Il garantissait la présence de la
        // poignée à chevron ; il garantit maintenant son **absence**, et l'absence de tout l'état
        // qu'elle commandait. Il rougit dès qu'un des cinq points revient — chaque assertion a été
        // mise à l'épreuve en remettant la poignée (voir le rapport du lot).
        assertFalse("le bouton à chevron est encore dessiné dans l'écran", chat.contains("ChatTabBarHandle("))
        assertFalse("l'état « barre masquée » existe encore", chat.contains("tabBarHidden"))
        assertFalse("la visibilité de la barre est encore calculée", chat.contains("tabBarVisible"))
        assertFalse("la préférence de masquage est encore lue", chat.contains("panels.tabsHidden("))
        assertFalse("la préférence de masquage est encore écrite", chat.contains("panels.setTabsHidden("))
        // Et la règle pure a quitté le module : plus de fonction de masquage à rebrancher.
        val stacking = read("../core/src/main/java/com/onyx/app/ui/design/ComposerStacking.kt")
        assertFalse("la règle de masquage est encore dans ComposerStacking", stacking.contains("fun tabBarVisible("))
        assertFalse("l'opacité de masquage est encore dans ComposerStacking", stacking.contains("fun tabBarAlpha("))
        assertFalse("l'aller-retour est encore dans ComposerStacking", stacking.contains("fun toggled("))
        assertFalse("le libellé de la poignée est encore dans ComposerStacking", stacking.contains("fun handleLabelKey("))
        // La barre, elle, est **peinte sans condition** : plus d'enveloppe dont la visibilité
        // dépendrait d'un état.
        assertTrue("la barre doit être posée telle quelle dans la plaque", chat.contains("                ChatTabBar("))
        assertFalse("la barre est encore derrière une visibilité conditionnelle", chat.contains("label = \"tabBarVisibility\""))
        // **Et plus rien ne retient ni n'efface la pile.** La retenue pendant la frappe appartenait à
        // l'effacement au défilement : retirée avec lui. Le focus n'est plus suivi, et la règle pure
        // de retenue a quitté le module — c'est l'absence qui garantit la plaque.
        assertFalse(
            "le focus du compositeur n'a plus à retenir une pile qui ne s'efface plus",
            chat.contains("composerFocused"),
        )
        assertFalse(
            "la règle pure de retenue a quitté le module",
            stacking.contains("ComposerStacking.typingHolds(") || stacking.contains("fun typingHolds"),
        )
    }

    @Test
    fun `la poignee a chevron a quitte la barre et le module de design`() {
        // Le **dessin**, le **geste** et la **géométrie** de la poignée ont disparu ensemble : il ne
        // reste ni le compositeur qui la peignait, ni le rond lui-même, ni les 44 dp de cible que la
        // barre lui réservait.
        val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")
        assertFalse("le rond à chevron est encore dessiné", bar.contains("fun ChatTabBarHandle("))
        assertFalse("la table de la poignée existe encore", bar.contains("object TabBarHandle"))
        assertFalse("le libellé de la poignée est encore déclaré", bar.contains("fun labelKey(hidden: Boolean)"))
        assertFalse("la barre accepte encore une poignée", bar.contains("handle: (@Composable () -> Unit)?"))
        assertFalse("la barre réserve encore la place d'une poignée", bar.contains("handleDp = if (handle != null)"))
        // Et les deux libellés qu'elle portait ont quitté le dictionnaire central.
        val tabs = read("../core/src/main/java/com/onyx/app/localization/OnyxFrenchTabs.kt")
        assertFalse("« Hide the tab bar » est encore une clé traduite", tabs.contains("\"Hide the tab bar\" to "))
        assertFalse("« Show the tab bar » est encore une clé traduite", tabs.contains("\"Show the tab bar\" to "))
    }

    /**
     * **Le test qui casse si un aplat revient dans le conteneur du compositeur.**
     *
     * La pile du bas ne pose **aucun** aplat : la plaque est une **vitre**, et c'est elle qui peint
     * — sa teinte mesurée ([glassTintAlpha], au plancher de lisibilité), son arête claire et son
     * filet. Jusqu'au lot « plaque finale », elle peignait en plus le barreau de surface
     * ([OnyxSurfaces.surfaceHigh]) **sous** sa vitre : le verre n'avait plus rien à montrer, et la
     * plaque se lisait « un aplat gris qui ne se détache pas du bord » — le premier des sept écarts.
     * Le barreau est donc **interdit** ici, et le verrou garde les deux règles qui comptaient : pas
     * de `theme.surfaceColor` (le bandeau blanc du défaut), pas deux fonds empilés.
     */
    @Test
    fun `aucun aplat dans le conteneur du compositeur`() {
        val stack = chat.substringAfter("// **La pile du bas**").substringBefore("ToastHost(")
        assertTrue("la pile du bas doit exister", stack.isNotBlank())
        assertFalse("un aplat de surface est posé dans la pile du bas", stack.contains("background(theme.surfaceColor"))
        // **Aucun fond peint dans la pile du bas** : la plaque est translucide, c'est sa vitre qui
        // peint (teinte mesurée, arête, filet). Un `background` de plus, et la plaque redevient
        // l'aplat qu'elle était.
        assertEquals(
            "la pile du bas ne pose aucun fond : la plaque est une vitre",
            0,
            Regex("""\.background\(""").findAll(stack).count(),
        )
        assertTrue(
            "la plaque peint sa vitre, masquante (masking = true)",
            stack.contains(
                "liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlaqueGeometry.Radius), masking = true)",
            ),
        )
        assertFalse(
            "la plaque ne peint plus le barreau de surface sous sa vitre",
            stack.contains("OnyxSurfaces.surfaceHigh(theme)"),
        )
        assertFalse("une élévation est posée dans la pile du bas", stack.contains("tonalElevation"))
        assertFalse("une élévation est posée dans la pile du bas", stack.contains(".shadow("))
        // Et la vitre ne peut plus être tirée vers le blanc en clair : c'est la règle du verre.
        val frost = read("../core/src/main/java/com/onyx/app/ui/design/Frost.kt")
        assertTrue(frost.contains("fun veilIsNeverLighterThanPage("))
        assertFalse(
            "le voile clair ne doit plus être tiré vers le blanc",
            frost.contains("else lerp(pageColour, Color.White"),
        )
        assertFalse(
            "le voile clair ne doit plus être tiré vers le blanc",
            frost.contains("lerp(pageColour, Color.White, 0.05f)"),
        )
    }

    // — Le code en ligne —

    @Test
    fun `le code en ligne tire ses couleurs de la regle du lot`() {
        val markdown = read("src/main/java/com/onyx/app/ui/chat/components/MarkdownText.kt")
        assertTrue(markdown.contains("inlineCodeSurface(theme)"))
        assertTrue(markdown.contains("inlineCodeInk(theme)"))
        assertFalse("le fond du code ne doit pas être une surface choisie à la main", markdown.contains("codeBackground = theme.surfaceColor.toArgb()"))
    }
}
