package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de source du lot « plaque finale » — les sept écarts, un par un.**
 *
 * Les chiffres (140 dp, 64 + 76, le rayon, la marge, le filet, la voie flottante) sont éprouvés sans
 * appareil par `PlaqueGeometryTest`, dans `core`. Ici on vérifie que **les écrans s'en servent
 * vraiment** : c'est la seule chose qu'un test de source peut dire, et elle est nécessaire — une
 * table juste que personne ne lit ne change aucun pixel.
 *
 * Ce qui est verrouillé, écran par écran :
 *
 *  1. la plaque est **translucide** (aucun aplat de surface peint sous la vitre) et porte le filet
 *     **interne** entre le champ et les onglets ;
 *  2. la rangée du champ vaut 64 dp et celle des onglets 76 ;
 *  3. l'envoi est **plein à l'accent** (plus de fond de contrôle au repos) ;
 *  4. l'onglet actif ne peint **plus de pastille de fond** ;
 *  5. quatre repères dessinés, le quatrième portant le **visage** et le libellé « Onyx » ;
 *  6. la poignée à chevron a été **retirée** de l'écran ; la bande réservée ne loge plus que la
 *     pastille de défilement, ramenée à sa stricte hauteur ;
 *  7. la puce du modèle n'est pas dans le champ de la plaque.
 */
class PlaqueFinaleWiringTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val screen = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val bar = read("../core/src/main/java/com/onyx/app/ui/design/ChatTabBar.kt")

    // — 1 · une seule surface translucide, avec son filet interne —

    @Test
    fun `la plaque n est plus un aplat de surface`() {
        assertTrue(
            "la plaque peint toujours sa vitre, et elle la peint MASQUANTE (masking = true)",
            screen.contains(
                "liquidGlass(GlassSurface.TAB_BAR, RoundedCornerShape(PlaqueGeometry.Radius), masking = true)",
            ),
        )
        assertFalse(
            "un aplat opaque sous la vitre efface ce qu'elle a à montrer : c'est le « verre perdu »",
            screen.contains("background(\n                        OnyxSurfaces.surfaceHigh(theme),"),
        )
        assertFalse(
            "et la surface haute ne doit plus être peinte par la plaque, sous aucune forme",
            screen.contains("OnyxSurfaces.surfaceHigh(theme),\n                        RoundedCornerShape(PlaqueGeometry.Radius)"),
        )
    }

    @Test
    fun `le filet interne separe le champ des onglets`() {
        assertTrue(
            "le filet est peint sur la frontière haute de la rangée d'onglets",
            screen.contains(".drawBehind {") && screen.contains("PlaqueGeometry.Separator.toPx()"),
        )
        assertTrue(
            "la couleur du filet est celle de la maison, mesurée une fois",
            screen.contains("val separatorColour = OnyxSurfaces.hairline(theme)"),
        )
        assertTrue(
            "le filet ne prend pas de place : la rangée garde ses 76 dp",
            screen.contains(".height(PlaqueGeometry.TabRowHeight)"),
        )
    }

    // — 2 · les deux étages tiennent dans 140 dp —

    @Test
    fun `la rangee du champ vaut 64 dp au repos, celle des onglets 76, et elle grandit`() {
        // **Retouche** (demande du propriétaire : le champ doit grandir comme la référence). La
        // rangée du champ vaut **au moins** 64 dp — l'état au repos, la première moitié des 140 dp —
        // et elle **grandit** avec le contenu (voir `ComposerGrowth`). Au repos la plaque vaut
        // toujours 140 dp ; l'ancienne hauteur **figée** est justement ce qui coupait la première ligne.
        assertTrue(
            "la rangée du champ garde ses 64 dp comme minimum (l'état au repos)",
            composer.contains("Modifier.heightIn(min = PlaqueGeometry.FieldRowHeight)"),
        )
        assertFalse(
            "et elle n'est plus figée : le texte au-delà d'une ligne n'est plus coupé",
            composer.contains("Modifier.height(PlaqueGeometry.FieldRowHeight)"),
        )
        assertTrue(
            "le champ ne se coupe plus sur un plafond de cinq lignes : il prend la règle de croissance",
            composer.contains("maxLines: Int = ComposerGrowth.MaxLines,"),
        )
    }

    // — 3 · l'envoi est l'action principale, pleine à l'accent —

    @Test
    fun `l envoi est plein a l accent, meme au repos — et mesure sur la plaque`() {
        val trailing = composer.substringAfter("\"send\" -> ComposerCircleButton(")
            .substringBefore("\n\n        else ->")
        assertTrue(
            "le fond du bouton est l'accent du thème **écarté du sol de la plaque** " +
                "(`theme.accent` posé tel quel mesurait 1,14:1 sur la page du thème « Code » en clair)",
            composer.contains("val disc = ComposerCommands.disc(theme, theme.accent)") &&
                trailing.contains("tint = disc,"),
        )
        assertTrue(
            "et la flèche est mesurée sur le disque **réellement peint** — blanc ou presque-noir " +
                "selon ce que la mesure donne, jamais une couleur écrite à la main",
            trailing.contains("contentTint = ComposerCommands.inkOnDisc(theme, disc),"),
        )
        assertFalse(
            "plus de fond de contrôle : c'est ce qui faisait lire « il a l'air désactivé »",
            trailing.contains("theme.fill"),
        )
        assertFalse("et plus de filet de repos sous le disque", trailing.contains("border = if (enabled)"))
        assertTrue(
            "et l'envoi **inerte** (champ vide) ne subit aucune opacité : à 35 %, il mesurait 1,49:1 " +
                "sur la plaque en apparence sombre et sa flèche disparaissait avec lui",
            trailing.contains("disabledAlpha = ComposerCommands.INERT_ALPHA,"),
        )
    }

    // — 4 · l'onglet actif porte la pastille de la planche —

    @Test
    fun `l onglet actif porte la pastille de la planche`() {
        // **Décision changée, et assumée.** Le lot précédent avait retiré cette pastille sur la foi
        // d'un brief qui la croyait absente de la planche ; la planche la déclare pourtant
        // (`.navi.on{background:var(--pill)}`, `fil-clair.html:110`), et la règle du propriétaire est
        // que **la planche gagne**. Elle est donc revenue, avec ses valeurs (`--pill`, `--pillline`),
        // sans le glissement `offset` qui poussait le quatrième repère hors de la plaque.
        assertTrue(
            "le fond de l'onglet actif revient, avec la valeur de la planche",
            bar.contains("background(pill, SurfaceRole.ActiveCapsule.shape)"),
        )
        assertTrue(
            "et son filet aussi (`inset 0 0 0 1px --pillline`)",
            bar.contains("PlaqueGeometry.Outline,") && bar.contains("pillLine,"),
        )
        assertTrue(
            "les deux teintes viennent de la table, pas d'un blanc écrit à la main",
            bar.contains("PlaqueGeometry.activePill(theme)") &&
                bar.contains("PlaqueGeometry.activePillLine(theme)"),
        )
        assertFalse(
            "la pastille n'est plus animée par un `offset` : c'est lui qui cassait la quatrième colonne",
            bar.contains(".offset(x = slide)"),
        )
        assertTrue(
            "l'encre de l'onglet est toujours mesurée sur le fond composé — pastille comprise",
            // LOT « encre de la plaque » : le fond interrogé n'est plus un fond **imaginaire** (le
            // pire des deux aplats sous la vitre), c'est celui de la **plaque telle qu'elle est
            // peinte** — le voile du verre sur la page de l'apparence, et sous la pastille de
            // l'onglet actif. Le propriétaire a photographié le résultat de l'ancien choix : des
            // libellés à 1,40:1 sur la plaque sombre. La règle, elle, n'a pas changé : *l'encre est
            // mesurée sur le fond composé, pastille comprise*.
            bar.contains("val ink = plaqueInk(theme, selected, isFace)") &&
                bar.contains("fun plaqueInk(") &&
                bar.contains("return readableInk(preferred, plaqueGround(theme, selected))") &&
                bar.contains("if (selected) PlaqueGeometry.activePill(theme) else null") &&
                bar.contains("OnyxSurfaces.page(theme)"),
        )
        assertFalse(
            "et le fond n'est plus choisi parmi deux aplats hypothétiques (blanc pur / noir pur)",
            bar.contains("contrastRatio(Color.White, backgroundDark)"),
        )
    }

    // — 5 · le quatrième repère est le visage, le cinquième n'est plus dessiné —

    @Test
    fun `le quatrieme repere est le visage, avec son libelle`() {
        assertTrue("la barre sait dessiner un repère-visage", bar.contains("faceIndex: Int? = null"))
        /**
         * **Décision changée, et assumée.** Ce verrou disait « le visage prend un cercle à
         * l'accent ». Le propriétaire l'a renversé : la couleur d'identité du bot **désigne le
         * bot, elle ne colore pas la commande** — et la planche n'a, sous son `.capface`, **aucun
         * disque** : le visage porte sa couleur, l'anneau clair le détache du verre. Le repère ne
         * se teinte donc plus à l'accent. Le détail est verrouillé par `OngletsPlaqueWiringTest`.
         */
        assertTrue(
            "le visage garde l'anneau clair de la planche",
            bar.contains("PlaqueGeometry.FaceRing"),
        )
        assertFalse(
            "et son disque ne prend plus la couleur d'identité du bot (elle désigne, elle ne teinte pas)",
            bar.substringAfter("if (faceContent != null && faceIndex == index)")
                .substringBefore("faceContent()")
                .contains("background(accent)"),
        )
        assertTrue(
            "et son libellé est celui du repère, pas celui de la page",
            bar.contains("faceLabel ?: ChatTab.FACE_LABEL"),
        )
        assertTrue(
            "l'écran le branche sur le quatrième repère, avec le visage de la conversation",
            screen.contains("faceIndex = ChatTab.drawn.lastIndex") &&
                screen.contains("faceLabel = ChatTab.FACE_LABEL") &&
                screen.contains("BotLook("),
        )
    }

    @Test
    fun `le cinquieme repere n existe plus`() {
        // Le cinquième repère (« Objectifs ») est **supprimé** avec « Idées » : ce n'est plus une
        // page cachée du pageur, c'est un écran qui n'existe plus. La barre ne peint que les repères
        // dessinés, et le pageur ne compte que les pages de contenu.
        assertTrue(
            "l'écran ne peint que les repères dessinés",
            screen.contains("tabs = ChatTab.drawn,"),
        )
        assertTrue("et la fonction de dessin les nomme", bar.contains("val drawn: List<ChatTab>"))
        assertTrue(
            "le pageur ne compte que les pages de contenu (le repère-visage n'en est pas une)",
            screen.contains("pageCount = { chatTabCount }"),
        )
    }

    // — 6 · rien ne flotte sur du texte —

    @Test
    fun `la poignee a chevron n existe plus, et la voie ne loge plus qu une pastille`() {
        assertFalse(
            "le rond à chevron est encore posé au-dessus de la plaque",
            screen.contains("ChatTabBarHandle("),
        )
        assertFalse(
            "la place qu'il occupait à la marge est encore réservée",
            screen.contains(".padding(start = PlaqueGeometry.SideMargin, bottom = jumpClearance)"),
        )
        assertFalse(
            "son état d'ouverture / de fermeture existe encore",
            screen.contains("hidden = tabBarHidden,"),
        )
        assertFalse("la barre est encore masquable", screen.contains("tabBarVisible("))
    }

    @Test
    fun `le fil reserve la voie des commandes flottantes`() {
        assertTrue(
            "la voie ne vaut plus que le dégagement plus la pastille — la poignée n'y est plus",
            screen.contains("val floatLane = PlaqueGeometry.FloatLane"),
        )
        assertFalse(
            "l'ancienne somme (qui réservait la cible de la poignée) est encore là",
            screen.contains("PlaqueGeometry.FloatClearance + PlaqueGeometry.FloatLane"),
        )
        assertTrue(
            "le fil ne la traverse pas : elle est dans son rembourrage bas",
            screen.contains("bottom = glassBottomInset + floatLane + OnyxSpace.BlockGap,"),
        )
        assertTrue(
            "les autres onglets la réservent aussi",
            screen.contains(".padding(top = glassTopInset, bottom = glassBottomInset + floatLane),"),
        )
        assertTrue(
            "et la liste des commandes se pose au-dessus d'elle, pas dedans",
            screen.contains("Spacer(Modifier.height(glassBottomInset + floatLane))"),
        )
    }

    // — 7 · plus de libellé de modèle dans le champ —

    @Test
    fun `la puce du modele n est pas dessinee sur la plaque`() {
        assertTrue(
            "la puce est conditionnée au mode carte",
            composer.contains("if (!plate) {") && composer.contains("ComposerModelChip("),
        )
        assertTrue(
            "elle reste entière pour la carte, et la porte du modèle reste ouverte dans l'en-tête",
            screen.contains("onOpenModelPicker = onOpenModelSheet") &&
                screen.contains("onPickModel = onPickModel"),
        )
    }
}
