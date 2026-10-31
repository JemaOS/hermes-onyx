package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.common.MuteRow
import com.onyx.app.ui.common.MuteSpan
import com.onyx.app.ui.common.muteSubmenuRows
import com.onyx.app.ui.design.FrostLevel
import com.onyx.app.ui.design.GlassPanelCorner
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.FrostSurface
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.frostVeilAlpha
import java.io.File
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le menu d'une rangée de bot au design de la référence : un panneau **ancré à la rangée** touchée
 * (jamais au centre de l'écran), l'icône à gauche, le chevron des sous-menus à droite, un filet
 * **entre les groupes seulement**, et le silence en **sous-menu** (« Mute › ») au lieu de quatre
 * lignes en clair — sans rien perdre : les quatre durées y sont toutes, plus « Unmute » si le bot
 * est déjà tu.
 */
class BotMenuAnchoredTest {
    private val ui = File("src/main/java/com/onyx/app/ui")
    private val menus = File(ui, "bots/BotMenus.kt").readText()
    private val botsScreen = File(ui, "bots/BotsScreen.kt").readText()
    private val menu = File("../core/src/main/java/com/onyx/app/ui/design/OnyxMenu.kt").readText()

    // MARK: - L'ancrage : le panneau s'ouvre près de la rangée

    @Test
    fun `le panneau s ouvre pres de la rangee touchee, pas au centre de l ecran`() {
        assertTrue("le menu prend une ancre", menu.contains("anchor: Rect? = null"))
        assertTrue("l'ancre est la rangée touchée, dans la fenêtre", menu.contains("boundsInWindow()"))
        // Ancrée, la feuille couvre tout l'écran (au-dessus des barres système) : ses coordonnées
        // sont celles de la fenêtre, et le voile ne laisse pas de bande claire.
        assertTrue(
            "une fenêtre plein écran",
            menu.contains("DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)"),
        )
        // L'origine du voile est mesurée, pas devinée : la rangée et le panneau vivent dans le
        // même repère.
        assertTrue("le voile se mesure", menu.contains("placed = it.boundsInWindow().topLeft"))
        assertTrue("la rangée est ramenée dans son repère", menu.contains("it.bottom - offset.y"))
        // Au-dessus ou en dessous selon la place, et jamais hors de l'écran.
        assertTrue("la place en dessous se calcule", menu.contains("val below = r.bottom.roundToInt() + gapPx"))
        assertTrue("et celle au-dessus", menu.contains("val above = r.top.roundToInt() - gapPx - heightPx"))
        assertTrue("rien ne sort de l'écran", menu.contains("coerceIn(marginPx"))
        assertTrue("le panneau attend d'être placé", menu.contains(".alpha(if (placed == null || replacingPanel) 0f else 1f)"))

        // L'écran des bots donne la tuile de ce qu'on a touché, et la rangée du volet donne la
        // sienne — l'ancre est toujours la ligne touchée, jamais le centre de l'écran.
        assertTrue("la tuile des bots", botsScreen.contains("onTileBounds(bot.name, it.boundsInWindow())"))
        assertTrue("et la salle de cet écran", botsScreen.contains("tileBounds[\"room:\" + group.id] = it.boundsInWindow()"))
        assertTrue(botsScreen.contains("anchor = tileBounds[\"bot:\" + bot.name]"))
        val volet = File(ui, "sidebar/ChatSidebar.kt").readText()
        assertTrue("la rangée du volet se mesure aussi", volet.contains(".onGloballyPositioned { bounds = it.boundsInWindow() }"))
        // La rangée du volet n'ancre plus son menu : l'appui long ouvre la **feuille du bas**, le
        // même composant que les autres listes du volet — pas un second panneau à ancrer.
        assertTrue("et son menu est la feuille du bas", volet.contains("ConversationActionSheet("))
        assertTrue(menus.contains("anchor = anchor"))
    }

    @Test
    fun `la mesure du panneau est celle de la reference`() {
        val width = Regex("private val MenuWidth = (\\d+)\\.dp").find(menu)!!.groupValues[1].toInt()
        assertTrue("le panneau reste compact, entre 250 et 290 dp : $width", width in 250..290)
        // Le coin n'est plus un nombre recopié dans le menu : c'est celui de la famille des panneaux
        // de verre, une valeur **partagée** avec la feuille du bas et l'alerte. La vérifier ici par
        // la constante — et non par une expression régulière sur le source — est ce qui empêche les
        // trois panneaux de diverger.
        assertTrue(
            "le coin du menu vient de la table du verre",
            menu.contains("private val MenuCorner = GlassPanelCorner"),
        )
        assertEquals("le coin de la famille des panneaux", OnyxSpace.PanelCorner, GlassPanelCorner)
        // Idem pour la hauteur d'une ligne : elle prend la marche des rangées de l'échelle
        // d'espacement, au lieu des 44 dp qui n'existaient que dans ce menu.
        assertTrue(
            "la hauteur d'une ligne vient de l'échelle",
            menu.contains("private val MenuRowHeight = OnyxSpace.RowHeight"),
        )
        assertTrue("une ligne reste une cible confortable", OnyxSpace.RowHeight.value >= 48f)
        assertEquals(OnyxSpace.RowHeight, 56.dp)
        assertEquals("l'icône d'une ligne", 17, Regex("private val MenuIconSize = (\\d+)\\.dp").find(menu)!!.groupValues[1].toInt())
        // Le fond est celui de la surface, translucide, sous un voile léger.
        // Le panneau garde la couleur de surface a 94 % en clair : la valeur est celle de la
        // reference, et elle se verifie par la fonction de voile, pas par le texte.
        assertTrue(menu.contains("tint = OnyxSurfaces.surfaceHigh(theme),"))
        // Le panneau se détache de la page d'une **marche**, et non de son opacité : le barreau des
        // surfaces flottantes mesure 1,314:1 sur la page sombre (voir `OnyxSurfaces`).
        assertTrue(
            "le panneau prend le barreau des surfaces flottantes",
            menu.contains("tint = OnyxSurfaces.surfaceHigh(theme),"),
        )
        assertEquals(0.94f, frostVeilAlpha(FrostSurface.MENU, FrostLevel.FULL, dark = false), 0.001f)
        assertTrue(menu.contains("Color.Black.copy(alpha = 0.18f)"))
    }

    @Test
    fun `le voile se ferme au clic exterieur et a Echap, et le panneau avale`() {
        // Le geste corrigé récemment ne doit pas revenir en arrière : le voile porte la fermeture.
        assertTrue(menu.contains(".onyxTappable(onClick = onDismiss)"))
        // Le panneau n'est pas une zone de fermeture : un appui entre deux lignes ne fait rien.
        assertTrue(menu.contains(".onyxTappable(onClick = {})"))
        assertTrue("Échap ferme", menu.contains("Key.Escape"))
        assertTrue("et le retour aussi", menu.contains("Dialog(onDismissRequest = onDismiss"))
    }

    // MARK: - Les lignes : icône, libellé, chevron, filets entre les groupes

    @Test
    fun `les filets sont entre les groupes seulement`() {
        assertTrue(
            "un filet sur la ligne qui ouvre un groupe",
            menu.contains("if (index > 0 && item.groupBreak) OnyxRowDivider(inset = 12.dp)"),
        )
        assertFalse("plus de filet entre deux lignes du même groupe", menu.contains("if (index > 0) OnyxRowDivider"))
        assertTrue("l'entrée porte son groupe", menu.contains("val groupBreak: Boolean = false"))
        assertTrue("le chevron des sous-menus", menu.contains("Symbols.ChevronRight"))
        // Un seul filet dans le menu d'un bot : devant la mise en ordre, comme le `Divider()` du
        // modèle.
        assertTrue(menus.contains("groupBreak = action == BotMenuAction.Reorder"))
    }

    @Test
    fun `l ordre et les libelles sont ceux de la reference`() {
        // La langue de l'appareil est posée : le libellé composé (« Nouvelle conversation avec … »)
        // se lit alors sans dépendre de la locale de la machine de test.
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        try {
        val bot = BotProfile(name = "kilo", chatId = "bot-chat")
        val labels = botMenuActions(
            bot,
            isOpen = false,
            unread = false,
            hasOrder = false,
            hasRecent = true,
        ).map { botMenuLabel(it, BotMenuFrame(label = "Kilo")) }
        assertEquals(
            // Les **neuf entrées de la référence**, dans son ordre (`BotsRoster.kt:444-454`), puis
            // celles de la maison.
            listOf(
                "Pin to top",
                "Hide",
                "Open recent session",
                "New chat with Kilo",
                "Routines",
                "Start fresh",
                "Edit",
                "Duplicate",
                "Delete",
                "Mark as Unread",
                "Voice…",
                "Mute",
                "Change Face…",
                "Reorder Bots",
            ),
            labels,
        )
        // En français, comme le reste de l'app.
        assertEquals("Voix…", OnyxLocalization.translate("Voice…", Locale.FRENCH))
        assertEquals("Mettre en silence", OnyxLocalization.translate("Mute", Locale.FRENCH))
        assertEquals("Routines", OnyxLocalization.translate("Routines", Locale.FRENCH))
        assertEquals("Modifier", OnyxLocalization.translate("Edit", Locale.FRENCH))
        assertEquals("Changer de visage…", OnyxLocalization.translate("Change Face…", Locale.FRENCH))
        assertEquals("Masquer", OnyxLocalization.translate("Hide", Locale.FRENCH))
        assertEquals("Réordonner les bots", OnyxLocalization.translate("Reorder Bots", Locale.FRENCH))
        // Les quatre mots qui dépendent de l'état, et le nom du bot dans le quatrième.
        assertEquals("Épingler en haut", OnyxLocalization.translate("Pin to top", Locale.FRENCH))
        assertEquals("Afficher", OnyxLocalization.translate("Unhide", Locale.FRENCH))
        assertEquals("Ouvrir la dernière session", OnyxLocalization.translate("Open recent session", Locale.FRENCH))
        assertEquals("Dupliquer", OnyxLocalization.translate("Duplicate", Locale.FRENCH))
        assertEquals(
            "une conversation neuve avec le bot",
            "Nouvelle conversation avec Kilo",
            // Le `locale` est **nommé** : `format` le prend après le `vararg`, et un `Locale`
            // passé en positionnel tomberait dans les arguments du gabarit.
            OnyxLocalization.format("New chat with %1\$s", "Kilo", locale = Locale.FRENCH),
        )
        } finally {
            OnyxLanguage.apply(OnyxLanguage.SYSTEM)
        }
    }

    // MARK: - Le silence en sous-menu

    @Test
    fun `le silence est une ligne a sous-menu, plus quatre lignes en clair`() {
        val items = menuItems(
            actions = listOf(BotMenuAction.Voice, BotMenuAction.Mute, BotMenuAction.Reorder),
            muteLines = {
                MuteSpan.entries.map { span -> MenuItem(span.title) { } }
            },
            onDismiss = {},
            onVoice = {},
            onReorder = {},
        )
        assertEquals(listOf("Voice…", "Mute", "Reorder Bots"), items.map { it.label })
        assertTrue("« Voice… » ouvre directement", items[0].submenu == null)
        assertEquals("« Mute › » porte ses durées", 4, items[1].submenu?.size)
        assertTrue("et le filet reste sur la mise en ordre", items[2].groupBreak)
    }

    @Test
    fun `le sous-menu du silence garde les quatre durees, et Unmute quand il est tu`() {
        val spans = listOf(MuteSpan.HOUR, MuteSpan.EIGHT_HOURS, MuteSpan.TOMORROW, MuteSpan.FOREVER)

        // Pas tu : les quatre durées, et elles seules.
        val open = muteSubmenuRows(until = null)
        assertEquals(4, open.size)
        assertEquals(spans, open.map { (it as MuteRow.Span).span })
        assertFalse("rien n'est séparé quand il n'y a qu'elles", open.any { (it as MuteRow.Span).groupBreak })

        // Déjà tu : « Unmute », la ligne qui dit jusqu'à quand, puis les quatre durées encore.
        val muted = muteSubmenuRows(until = System.currentTimeMillis() + 60 * 60_000L)
        assertEquals(6, muted.size)
        assertEquals(MuteRow.Unmute, muted[0])
        assertTrue("l'échéance suit « Unmute »", muted[1] is MuteRow.Until)
        assertEquals("aucune durée n'a disparu", spans, muted.drop(2).map { (it as MuteRow.Span).span })
        assertTrue("les durées forment leur propre groupe", (muted[2] as MuteRow.Span).groupBreak)
        assertFalse((muted[3] as MuteRow.Span).groupBreak)

        // Les libellés sont ceux du modèle, dits en français.
        assertEquals(
            listOf("Pendant 1 heure", "Pendant 8 heures", "Jusqu’à demain", "Jusqu’à réactivation"),
            spans.map { OnyxLocalization.translate(it.title, Locale.FRENCH) },
        )
    }

    @Test
    fun `un telephone n a pas la place de deux panneaux, le sous-menu prend la place du panneau`() {
        assertTrue(
            "la place se décide avant de dessiner",
            menu.contains("val replacingPanel = submenu != null && !beside && !leftOf"),
        )
        assertTrue("à droite du panneau quand il y a la place", menu.contains("beside -> right"))
        assertTrue("à gauche sinon", menu.contains("leftOf -> x - gapPx - widthPx"))
        assertTrue("et à sa place", menu.contains("else -> x"))
    }

    @Test
    fun `chaque duree garde son action`() {
        assertTrue("le sous-menu vient de `MuteMenu`", menus.contains("muteSubmenuItems(mutes, item)"))
        assertTrue("la ligne « Mute » l'ouvre", menus.contains("submenu = muteLines()"))
        assertTrue("et la durée choisie ferme le menu en agissant", menus.contains("onDismiss()\n            entry.onSelect()"))
    }
}
