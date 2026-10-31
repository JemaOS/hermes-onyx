package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.Role
import com.onyx.app.ui.conversations.ConversationActionTint
import com.onyx.app.ui.design.MotionFamily
import com.onyx.app.ui.conversations.ConversationSheetRow
import com.onyx.app.ui.conversations.conversationActivitySubtitle
import com.onyx.app.ui.conversations.conversationSheetRows
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **câblage** des gestes d'une conversation dans le **volet** : la feuille du bas, ses lignes, le
 * glissement vers la gauche et ses pavés, le bandeau d'annulation, et les gestes de l'appareil qui
 * vont avec (presse-papiers, silence).
 *
 * Ce que ce fichier tient, c'est que les gestes ne tiennent pas **par accident** : la liste reçoit de
 * quoi glisser, la feuille réemploie les lignes partagées, chaque action arme son retour en arrière,
 * et le geste de bord n'est jamais consommé.
 */
class SidebarConversationGesturesTest {

    private val sidebar = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
    private val host = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt").readText()
    private val model = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt").readText()
    private val sheet = File("src/main/java/com/onyx/app/ui/conversations/ConversationActionSheet.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val repo = File("src/main/java/com/onyx/app/data/repo/ConversationRepository.kt").readText()

    private fun conversation(
        pinned: Boolean = false,
        archived: Boolean = false,
        hasMessages: Boolean = true,
        outbox: Int = 0,
    ) = Conversation(
        id = "conv-1",
        title = "Where is the key?",
        createdAt = 0L,
        updatedAt = 3_600_000L,
        pinned = pinned,
        archived = archived,
        messages = if (hasMessages) listOf(ChatMessage(id = "m1", role = Role.USER, text = "hi", createdAt = 1L)) else emptyList(),
        outbox = (1..outbox).map { OutboxItem(id = "o$it", text = "queued", queuedAt = 0L) },
    )

    /** Les lignes minimales : les cinq actions et rien d'autre. */
    private fun baseRows(
        conversation: Conversation = conversation(),
        canMarkUnread: Boolean = false,
        muted: Boolean = false,
    ) = conversationSheetRows(
        conversation = conversation,
        canMarkUnread = canMarkUnread,
        muted = muted,
        onRename = {},
        onCreateThread = {},
        onTogglePin = {},
        onToggleArchive = {},
        onDelete = {},
    )

    // -- La feuille : le modèle ---------------------------------------------------------------

    @Test
    fun `la feuille range les cinq actions dans l ordre de la reference`() {
        assertEquals(
            listOf("Rename", "Create thread", "Pin", "Archive", "Delete"),
            baseRows().map { it.label },
        )
    }

    @Test
    fun `supprimer reste la derniere ligne malgre les complements`() {
        val rows = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = true,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onToggleUnread = {},
            onUsage = {},
            onCopyTitle = {},
            onOpenElsewhere = {},
            muteRows = listOf(
                ConversationSheetRow("Unmute", 0, ConversationActionTint.MUTE),
                ConversationSheetRow("For 1 Hour", 0, ConversationActionTint.MUTE),
            ),
        )
        assertEquals("Delete", rows.last().label)
        assertTrue("et elle est destructrice", rows.last().destructive)
    }

    @Test
    fun `la famille de teinte suit le libelle`() {
        val rows = baseRows().associateBy { it.label }
        assertEquals(ConversationActionTint.CONVERSATION, rows.getValue("Rename").tint)
        assertEquals(ConversationActionTint.THREAD, rows.getValue("Create thread").tint)
        assertEquals(ConversationActionTint.PIN, rows.getValue("Pin").tint)
        assertEquals(ConversationActionTint.ARCHIVE, rows.getValue("Archive").tint)
        assertEquals(ConversationActionTint.DANGER, rows.getValue("Delete").tint)
    }

    @Test
    fun `une ligne de silence prend la teinte du silence`() {
        val rows = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = false,
            muted = true,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            muteRows = listOf(ConversationSheetRow("Unmute", 0, ConversationActionTint.MUTE)),
        )
        val mute = rows.first { it.label == "Unmute" }
        assertEquals(ConversationActionTint.MUTE, mute.tint)
        assertFalse("le silence n'est jamais destructeur", mute.destructive)
    }

    @Test
    fun `la ligne de non lu dit lu ou non lu selon la marque`() {
        val unread = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = true,
            muted = false,
            unread = true,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onToggleUnread = {},
        ).first { it.label.startsWith("Mark as") }
        assertEquals("Mark as Read", unread.label)

        val read = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = true,
            muted = false,
            unread = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onToggleUnread = {},
        ).first { it.label.startsWith("Mark as") }
        assertEquals("Mark as Unread", read.label)
    }

    @Test
    fun `la ligne de non lu disparait quand on ne peut pas marquer`() {
        val rows = baseRows(canMarkUnread = false).map { it.label }
        assertFalse("aucune ligne de non-lu", rows.any { it.startsWith("Mark as") })
    }

    @Test
    fun `creer un fil s eteint sur une conversation vide`() {
        val row = baseRows(conversation = conversation(hasMessages = false)).first { it.label == "Create thread" }
        assertFalse("rien à recopier", row.enabled)
    }

    @Test
    fun `creer un fil s allume des qu il y a un message`() {
        val row = baseRows(conversation = conversation(hasMessages = true)).first { it.label == "Create thread" }
        assertTrue(row.enabled)
    }

    @Test
    fun `creer un fil s allume avec un envoi en attente`() {
        // Un envoi encore dans la file est du fil : il y a bien quelque chose à recopier.
        val row = baseRows(conversation = conversation(hasMessages = false, outbox = 1))
            .first { it.label == "Create thread" }
        assertTrue("la file de départ compte comme du fil", row.enabled)
    }

    @Test
    fun `copier le titre n apparait que s il est fourni`() {
        assertFalse(baseRows().any { it.label == "Copy title" })
        val withCopy = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onCopyTitle = {},
        )
        assertTrue(withCopy.any { it.label == "Copy title" })
    }

    @Test
    fun `ouvrir ailleurs n apparait que s il est fourni`() {
        assertFalse(baseRows().any { it.label == "Open in New Window" })
        val withOpen = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onOpenElsewhere = {},
        )
        assertTrue(withOpen.any { it.label == "Open in New Window" })
    }

    @Test
    fun `usage n apparait que s il est fourni`() {
        assertFalse(baseRows().any { it.label == "Usage & cost" })
        val withUsage = conversationSheetRows(
            conversation = conversation(),
            canMarkUnread = false,
            muted = false,
            onRename = {},
            onCreateThread = {},
            onTogglePin = {},
            onToggleArchive = {},
            onDelete = {},
            onUsage = {},
        )
        assertTrue(withUsage.any { it.label == "Usage & cost" })
    }

    @Test
    fun `chaque ligne de la feuille porte son symbole`() {
        baseRows().forEach { assertNotEquals("une ligne muette : ${it.label}", 0, it.symbol) }
    }

    @Test
    fun `le sous-titre dit l age, jamais un nombre de millisecondes`() {
        val subtitle = conversationActivitySubtitle(updatedAt = 0L, now = 7L * 3600L * 1000L)
        assertTrue(subtitle.contains("7"))
        assertFalse(subtitle.contains("25920000"))
    }

    // -- Le glissement : sa géométrie ---------------------------------------------------------

    /** Les jetons du glissement : **un seul** fichier les porte, donc la mesure est sans ambiguïté. */
    private val swipeStyle = File("src/main/java/com/onyx/app/ui/sidebar/SidebarSwipe.kt").readText()

    private fun dpOf(name: String): Int =
        Regex("internal val $name: Dp = (\\d+)\\.dp").find(swipeStyle)!!.groupValues[1].toInt()

    private fun swipeRow(): String = sidebar
        .substringAfter("private fun SwipeableConversationRow(")
        .substringBefore("private fun SidebarSwipeAction(")

    @Test
    fun `le seuil du geste franc est un vrai geste, plus court que la pilule`() {
        val threshold = dpOf("SidebarSwipeThreshold")
        assertTrue("un geste franc ne descend pas sous la cible tactile", threshold >= MinTarget.value.toInt())
        assertTrue("le seuil tombe avant toute la pilule", threshold < dpOf("SidebarSwipeFallbackReveal"))
    }

    @Test
    fun `la course du glissement reste bornee`() {
        assertTrue("la marge au-delà de la pilule reste bornée", dpOf("SidebarSwipeSlack") in 1..120)
        assertTrue(
            "la course plancher couvre le seuil et une pilule",
            dpOf("SidebarSwipeFallbackReveal") > dpOf("SidebarSwipeThreshold"),
        )
    }

    @Test
    fun `la pilule et la zone morte partagent la meme marge`() {
        // Une seule valeur : l'air de la pilule avec le bord **est** la marge de la zone morte, si
        // bien que les deux ne peuvent pas diverger.
        assertEquals(12, dpOf("SidebarSwipeMargin"))
        assertTrue("la zone morte se sert de la marge", swipeStyle.contains("SidebarSwipeMargin.toPx()"))
        assertTrue("la liste la lit une fois", sidebar.contains("val edgeDeadZone = rememberSidebarSwipeDeadZone()"))
    }

    @Test
    fun `les coins de la rangee-carte sont ceux de la maison`() {
        assertEquals("le rayon de la rangée-carte", 16, dpOf("SidebarSwipeCorner"))
        assertTrue(
            "l'ombre de la carte est légère",
            dpOf("SidebarSwipeCardElevation") in 1..8,
        )
    }

    @Test
    fun `la zone morte du bord est la bande du systeme plus la marge`() {
        // L'arithmétique, pure et testée sans appareil : la bande du système **plus** la marge.
        assertEquals(152f, sidebarSwipeDeadZonePx(systemGesturesLeftPx = 140f, marginPx = 12f), 0.001f)
        assertEquals("une bande nulle ne retire rien", 12f, sidebarSwipeDeadZonePx(0f, 12f), 0.001f)
        assertEquals("un système négatif ne retire rien", 12f, sidebarSwipeDeadZonePx(-40f, 12f), 0.001f)
        assertEquals("une marge négative ne retire rien", 0f, sidebarSwipeDeadZonePx(0f, -1f), 0.001f)
    }

    @Test
    fun `la rangee ne revendique rien dans la zone morte`() {
        val swipe = swipeRow()
        assertTrue("le geste lit le doigt avant le clic", swipe.contains("PointerEventPass.Initial"))
        assertTrue(
            "vers la droite, la zone morte reste au geste de bord",
            swipe.contains("if (total > 0f && down.position.x <= deadZone.value) return@awaitEachGesture"),
        )
        assertFalse("le détecteur qui avalait tout l'horizontal est banni", swipe.contains("detectHorizontalDragGestures"))
    }

    @Test
    fun `la rangee ne glisse pas en multi-selection`() {
        // En sélection, le geste coche : aucune rangée ne porte de pilule.
        assertTrue(sidebar.contains("val actions = if (selecting) null else rowActions?.invoke(row)"))
    }

    @Test
    fun `la rangee revient en place et rien ne reste ouvert`() {
        // La pose n'est **pas mémorisée** : une rangée à zéro est une rangée au repos, donc aucune
        // carte ni pilule fantôme — la carte se lit sur la pose elle-même.
        assertTrue(
            "la carte et la pilule se lisent sur la pose",
            sidebar.contains("val lifted = slide.value > 0.5f || slide.value < -0.5f"),
        )
        assertTrue("la rangée revient en place", sidebar.contains("slide.animateTo(0f, settle)"))
        assertFalse("aucune rangée ne reste ouverte", sidebar.contains("var openId"))
    }

    @Test
    fun `le geste franc applique l action, et rien sous le seuil`() {
        val swipe = swipeRow()
        assertTrue("le geste franc décide sur ce qui a été parcouru", swipe.contains("val pin = total >= threshold.value"))
        assertTrue("l'autre direction aussi", swipe.contains("val archive = total <= -threshold.value"))
        assertTrue("l'action part au geste, sans boîte de dialogue", swipe.contains("pin -> onPin()"))
        assertTrue("et l'autre part de même", swipe.contains("archive -> onArchive()"))
        assertTrue("le retour en place est lancé avant l'action", swipe.contains("slide.animateTo(0f, settle)"))
        assertFalse("aucune confirmation modale dans le geste", swipe.contains("OnyxAlert"))
    }

    @Test
    fun `le retour en place suit la famille de mouvement de la maison`() {
        // Un geste de la main : famille « micro » (≤ 250 ms), et sous « Réduire les animations »,
        // aucune image intermédiaire — c'est `animated()` qui lit le réglage système.
        assertTrue("la famille est nommée", sidebar.contains("MotionFamily.Tab.animated<Float>()"))
        assertTrue("elle est micro", MotionFamily.Tab.isMicro)
        assertFalse("aucune durée écrite à la main", swipeRow().contains("tween("))
    }

    // -- Le câblage : les rangées, la feuille, l'annulation ------------------------------------

    @Test
    fun `le volet porte les deux gestes sur ses rangees`() {
        assertTrue(sidebar.contains("SwipeableConversationRow("))
        assertTrue(sidebar.contains("onLongPress = onLongPress?.let { press -> { bounds?.let(press) } }"))
    }

    @Test
    fun `la liste recoit les deux directions, et pas la suppression`() {
        assertTrue(sidebar.contains("rowActions = rowActions,"))
        assertTrue(sidebar.contains("onSwipePin = { actions -> applyPin(actions, !actions.conversation.pinned) }"))
        assertTrue(sidebar.contains("onSwipeArchive = { actions -> applyArchive(actions, !actions.conversation.archived) }"))
        assertFalse("la suppression n'est **pas** dans le glissement", sidebar.contains("onSwipeDelete"))
        // Elle reste **atteignable** : la feuille de l'appui long la porte, avec sa confirmation —
        // un geste large vers elle serait un piège.
        assertTrue("l'appui long la porte", sidebar.contains("confirmDelete?.let"))
        assertTrue("et la feuille l'appelle", sidebar.contains("onDelete = {"))
    }

    @Test
    fun `la pilule est claire, avec son icone et son libelle`() {
        val pill = sidebar.substringAfter("private fun SidebarSwipeAction(")
            .substringBefore("La **case** d'une rangée en multi-sélection")
        assertTrue("l'encre vient de la pilule", pill.contains("sidebarSwipePillInk(theme)"))
        assertTrue("la surface est celle de la pilule", pill.contains(".sidebarSwipePill()"))
        assertTrue("la cible est celle de la maison", pill.contains("defaultMinSize(minHeight = MinTarget)"))
        assertTrue(
            "le libellé ne touche pas le bord du galet",
            pill.contains(".padding(horizontal = OnyxSpace.Base, vertical = OnyxSpace.Snug)"),
        )
        // Et la **cible** de la maison couvre la pilule, pas seulement son texte : l'étiquette est
        // posée **avant** le rembourrage interne.
        assertTrue(
            "l'étiquette couvre la pilule entière",
            pill.indexOf(".semanticsLabel(label)") < pill.indexOf(".padding(horizontal = OnyxSpace.Base"),
        )
        assertTrue("l'icône est là", pill.contains("OnyxIcon(symbol = symbol"))
        assertTrue("le libellé se traduit", pill.contains("localize = true"))
        assertFalse("aucune couleur d'accent", pill.contains("theme.accent"))
        assertFalse("aucun rouge", pill.contains("theme.error"))
        assertFalse("plus d'aplat plein", pill.contains("Color.White"))
    }

    @Test
    fun `l appui long ouvre la feuille du bas`() {
        assertTrue(sidebar.contains("ConversationActionSheet("))
        assertTrue(sidebar.contains("onMenuRow = if (rowActions != null) { row, _ -> sheetRow = row } else null"))
    }

    @Test
    fun `le volet n invente pas une seconde feuille`() {
        assertFalse("le volet ne définit aucune feuille", sidebar.contains("fun ConversationActionSheet("))
        assertFalse("ni ne redessine un panneau ancré", sidebar.contains("OnyxMenu("))
        assertTrue("il emploie celle des autres listes", sheet.contains("fun ConversationActionSheet("))
    }

    @Test
    fun `le volet construit la feuille avec les lignes partagees`() {
        assertTrue(sidebar.contains("conversationSheetRows("))
        assertTrue(sidebar.contains("subtitle = conversationActivitySubtitle(actions.conversation.updatedAt)"))
    }

    @Test
    fun `le volet arme le retour des trois actions`() {
        assertTrue(sidebar.contains("fun applyPin("))
        assertTrue(sidebar.contains("fun applyArchive("))
        assertTrue(sidebar.contains("fun applyDelete("))
        assertTrue(sidebar.contains("actions.onTogglePin(!pinned)"))
        assertTrue(sidebar.contains("actions.onToggleArchive(!archived)"))
        assertTrue(sidebar.contains("actions.onRestore()"))
    }

    @Test
    fun `le bandeau se pose au bas du volet`() {
        assertTrue(sidebar.contains("ConversationUndoBar("))
        assertTrue(sidebar.contains("modifier = Modifier.align(Alignment.BottomCenter)"))
    }

    @Test
    fun `supprimer demande une confirmation explicite`() {
        assertTrue(sidebar.contains("confirmDelete?.let"))
        assertTrue("la confirmation est la surface de la maison, pas la boîte du système", sidebar.contains("OnyxConfirm("))
        assertFalse("la boîte du système a disparu du geste", sidebar.contains("OnyxAlert("))
        assertTrue(sidebar.contains("It leaves this phone and your server. This can't be undone."))
    }

    @Test
    fun `renommer ouvre l invite maison`() {
        assertTrue(sidebar.contains("OnyxPrompt("))
        assertTrue(sidebar.contains("target.onRename(renameText.trim())"))
    }

    @Test
    fun `le modele de la rangee porte tous les gestes`() {
        val actions = sidebar.substringAfter("internal data class SidebarRowActions(")
            .substringBefore("\n)")
        listOf(
            "val conversation:",
            "val unread:",
            "val muted:",
            "val onMarkRead:",
            "val onMarkUnread:",
            "val onTogglePin:",
            "val onToggleArchive:",
            "val onRename:",
            "val onCreateThread:",
            "val onOpenUsage:",
            "val onCopyTitle:",
            "val onOpenElsewhere:",
            "val onDelete:",
            "val onRestore:",
            "val muteRows:",
        ).forEach { field ->
            assertTrue("le geste $field manque", actions.contains(field))
        }
    }

    @Test
    fun `le branchement remplit chaque geste de la rangee`() {
        listOf(
            "conversation = conversation,",
            "unread = sidebarHasUnreadReply",
            "muted = mutes.isMuted(conversation.id),",
            "onMarkRead =",
            "onMarkUnread =",
            "onTogglePin =",
            "onToggleArchive =",
            "onRename =",
            "onCreateThread =",
            "onOpenUsage =",
            "onCopyTitle =",
            "onOpenElsewhere =",
            "onDelete =",
            "onRestore =",
            "muteRows = muteMenuItems",
        ).forEach { field ->
            assertTrue("le geste « $field » n'est pas branché", host.contains(field))
        }
    }

    @Test
    fun `le presse-papiers est celui de l appareil`() {
        assertTrue(host.contains("ClipEntry(ClipData.newPlainText(\"Onyx\", conversation.title))"))
        assertTrue(host.contains("val clipboard = LocalClipboard.current"))
    }

    @Test
    fun `le silence vient du menu maison`() {
        assertTrue(host.contains("muteMenuItems(mutes, conversation.id)"))
        assertTrue(host.contains("val mutes = remember { Mutes(context) }"))
    }

    @Test
    fun `l app branche le renommage, l archivage et la bifurcation`() {
        assertTrue(app.contains("onRenameConversation = { conversation, title ->"))
        assertTrue(app.contains("onSetConversationArchived = { conversation, archived ->"))
        assertTrue(app.contains("onCreateThreadConversation = { conversation ->"))
        assertTrue(app.contains("conversationRepo.fork(conversation.id)"))
    }

    @Test
    fun `le retour d une suppression retablit la conversation et defait la marque`() {
        // Le rétablissement passe par `restore` : c'est le seul chemin qui défait la marque de
        // suppression, sans quoi le dépôt refuserait de réécrire la conversation (elle ne
        // reviendrait jamais).
        assertTrue(app.contains("onRestoreConversation = { conversation -> scope.launch { conversationRepo.restore(conversation) } }"))
    }

    @Test
    fun `le depot offre la bifurcation locale`() {
        assertTrue(repo.contains("suspend fun fork(id: String"))
        assertTrue(repo.contains("internal fun forkedConversation("))
        assertTrue(repo.contains("internal fun forkTitle("))
        assertFalse("aucune route de fork serveur", repo.contains("forkSession"))
    }

    @Test
    fun `les archives ne sont pas retirees de la liste`() {
        // Aucune règle du modèle ne filtre sur l'état d'archive : une conversation archivée reste une
        // rangée, donc reste atteignable.
        assertFalse(model.contains("archived"))
    }

    // -- Le geste de rangée : l'axe verrouillé, jamais le défilement ---------------------------

    @Test
    fun `l axe du geste est tranche une fois, avant toute revelation`() {
        val swipe = swipeRow()
        assertTrue(
            "l'axe naît indécis",
            swipe.contains("var axis = SidebarSwipeAxis.UNDECIDED"),
        )
        assertTrue(
            "il est tranché par la règle pure, sur la course des DEUX axes",
            swipe.contains("axis = sidebarSwipeAxis(total, across, slop)"),
        )
        assertTrue(
            "les deux courses sont cumulées",
            swipe.contains("across += delta.y"),
        )
    }

    @Test
    fun `un geste vertical ne peut plus engager une action dans ce geste`() {
        val swipe = swipeRow()
        assertTrue(
            "classé vertical, le geste quitte la rangée sans rien consommer",
            swipe.contains("if (axis == SidebarSwipeAxis.VERTICAL) return@awaitEachGesture"),
        )
        assertTrue(
            "et il n'y a d'action que si l'axe est horizontal",
            swipe.contains("if (axis != SidebarSwipeAxis.HORIZONTAL) return@awaitEachGesture"),
        )
        // Le verrou est posé **avant** la revendication : une fois l'axe tranché, aucun retour en arrière.
        assertTrue(
            "le verrou précède la revendication",
            swipe.indexOf("SidebarSwipeAxis.VERTICAL) return@awaitEachGesture") <
                swipe.indexOf("dragging = true"),
        )
    }

    @Test
    fun `la liste ne recoit le geste qu'apres la distance minimale`() {
        val swipe = swipeRow()
        assertTrue(
            "la distance minimale est lue en pixels, par l'état mis à jour",
            swipe.contains("val axisMin = rememberUpdatedState(") &&
                sidebar.contains("SidebarSwipeAxisMin.toPx()"),
        )
        // Et elle retient la **révélation**, pas la classification : la rangée et le geste du volet
        // s'arbitrent au seuil système, sans quoi le volet démarrerait le premier.
        assertTrue(
            "la pose passe par la règle pure de révélation",
            swipe.contains("sidebarSwipeRevealPx(total, axisMin.value)"),
        )
        assertTrue(
            "et la classification ne prend PAS la distance minimale",
            !swipe.contains("slop, axisMin.value)"),
        )
    }

    @Test
    fun `la zone morte du bord garde le geste d'ouverture, dans les deux sens`() {
        // Le geste vers la droite depuis le bord reste au volet ; le geste horizontal franc, lui,
        // revendique — et l'ordre des deux vérifications est celui du code livré.
        val swipe = swipeRow()
        val deadZoneAt = swipe.indexOf("if (total > 0f && down.position.x <= deadZone.value) return@awaitEachGesture")
        val claimsAt = swipe.indexOf("dragging = true")
        assertTrue("la zone morte est vérifiée", deadZoneAt > 0)
        assertTrue("avant la revendication", deadZoneAt < claimsAt)
        // Et le verrou vertical tombe **avant** la zone morte : un défilement vertical près du bord
        // reste un défilement, il n'ouvre pas le volet et n'épingle pas.
        assertTrue(
            "le verrou d'axe précède la zone morte",
            swipe.indexOf("SidebarSwipeAxis.VERTICAL) return@awaitEachGesture") < deadZoneAt,
        )
    }

    // -- La lumière pendant le défilement : aucune couche qui bouge ne s'éclaire -----------------

    @Test
    fun `la liste fige la lumiere tant qu'elle defile`() {
        assertTrue(
            "l'état de défilement de la liste est posé en local de composition",
            sidebar.contains("CompositionLocalProvider(LocalGlassScrollFreeze provides state.isScrollInProgress)"),
        )
        // Le local est lu **du même état** que celui qui porte la position de défilement : la lumière
        // ne peut pas se croire au repos pendant que la liste bouge.
        assertTrue("c'est bien l'état passé à la liste", sidebar.contains("state = state,"))
    }
}
