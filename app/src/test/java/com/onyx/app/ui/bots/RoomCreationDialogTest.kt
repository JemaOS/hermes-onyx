package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La boîte de création d'un salon : ce qu'elle **est** (l'anatomie de la référence), ce qu'elle
 * **applique** (la contrainte de 2 à 6, le nom non vide, la raison lisible) et ce qu'elle **dit**
 * (le paragraphe ne promet que ce que le code tient).
 *
 * Les deux dernières sont les plus importantes : une action inerte qui ne dit pas pourquoi est un
 * bouton muet, et une phrase qui promet « ça continue application fermée » à un salon que la
 * passerelle ne tient pas est un mensonge.
 */
class RoomCreationDialogTest {

    private val dialogSource = File("src/main/java/com/onyx/app/ui/bots/RoomCreationDialog.kt").readText()
    private val screenSource = File("src/main/java/com/onyx/app/ui/bots/BotsScreen.kt").readText()
    private val flowSource = File("src/main/java/com/onyx/app/ui/bots/GroupCreationFlow.kt").readText()
    private val viewModelSource = File("src/main/java/com/onyx/app/ui/bots/GroupChatViewModel.kt").readText()
    private val groupScreenSource = File("src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt").readText()

    /** Les libellés de la boîte se lisent en français (le propriétaire voit du français). */
    private fun <T> inFrench(block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(Locale.FRANCE)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    // MARK: - L'anatomie de la référence

    @Test
    fun `la boite porte le titre, le nom, la section, la liste et l'action`() {
        // Le titre en gras de la référence, son libellé de champ et son invite.
        assertTrue("le titre", dialogSource.contains("text = \"New room\""))
        assertTrue("le libellé du champ", dialogSource.contains("text = \"Room name\""))
        assertTrue("l'invite du champ", dialogSource.contains("placeholder = \"Release room\""))
        // L'intitulé de section **avec la contrainte**, comme la référence le compose.
        assertTrue(
            "la section porte la contrainte",
            dialogSource.contains("text = \"Bots · pick \$ROOM_MIN_BOTS to \$ROOM_MAX_BOTS\""),
        )
        // La liste : le visage du bot, et son cercle de sélection.
        assertTrue("chaque bot a son visage", dialogSource.contains("BotLook(name = bot.name"))
        assertTrue("et son cercle", dialogSource.contains("PickMark(picked = picked"))
        // L'action de validation, en pilule.
        assertTrue("une action de validation", dialogSource.contains("text = \"Create\""))
        assertTrue("et son annulation", dialogSource.contains("text = \"Cancel\""))
        assertTrue("la pilule est dessinée par la maison", dialogSource.contains("private fun RoomActionPill("))
    }

    @Test
    fun `le champ est focalise a l'ouverture, et son arete prend l'accent`() {
        assertTrue("demande de focus", dialogSource.contains("fieldFocus.requestFocus()"))
        assertTrue("arête du champ à l'accent", dialogSource.contains("composerFieldSurface(theme, RoundedCornerShape(OnyxSpace.FieldCorner), focused = true)"))
    }

    @Test
    fun `la contrainte affichee est celle du serveur`() {
        assertEquals(2, ROOM_MIN_BOTS)
        assertEquals(6, ROOM_MAX_BOTS)
        assertEquals("celle du modèle", GroupRoom.MIN_MEMBERS, ROOM_MIN_BOTS)
        assertEquals("celle du modèle", GroupRoom.MAX_MEMBERS, ROOM_MAX_BOTS)
    }

    // MARK: - Ce qu'elle applique

    @Test
    fun `un nom vide ne fait pas de salon, et la raison le dit`() = inFrench {
        val reason = roomBlockReason(name = "   ", chosen = 3, busy = false)

        assertNotNull("un nom vide bloque", reason)
        assertTrue("la raison dit le nom : $reason", reason!!.contains("nom"))
        // Une fois le nom donné, le même compte passe.
        assertNull(roomBlockReason(name = "Salon des versions", chosen = 3, busy = false))
    }

    @Test
    fun `moins de deux bots bloquent, deux passent`() = inFrench {
        assertNotNull("un bot ne suffit pas", roomBlockReason("Salon", 0, false))
        assertNotNull("un bot ne suffit pas", roomBlockReason("Salon", 1, false))
        assertNull("deux suffisent", roomBlockReason("Salon", 2, false))
    }

    @Test
    fun `six bots passent, sept bloquent, et la raison dit combien en enlever`() = inFrench {
        assertNull("six, c'est le plafond, et il passe", roomBlockReason("Salon", 6, false))

        val reason = roomBlockReason("Salon", 7, false)
        assertNotNull("sept bloquent", reason)
        assertTrue("la raison dit le maximum : $reason", reason!!.contains("au maximum"))
        assertTrue("et combien en enlever : $reason", reason.contains("1"))
    }

    @Test
    fun `la raison de trop peu dit combien il en manque, et le plancher`() = inFrench {
        val one = roomBlockReason("Salon", 1, false)!!
        assertTrue("il en manque un : $one", one.contains("Encore 1 bot"))
        assertTrue("le plancher est dit : $one", one.contains("au moins 2"))

        val none = roomBlockReason("Salon", 0, false)!!
        assertTrue("il en manque deux : $none", none.contains("2"))
    }

    @Test
    fun `une ouverture en cours bloque aussi, et se dit`() = inFrench {
        val reason = roomBlockReason("Salon", 3, busy = true)

        assertNotNull("pendant l'ouverture, rien ne repart", reason)
        assertEquals(OnyxLocalization["Making the room…"], reason)
    }

    @Test
    fun `l'action reste inerte tant que la raison est là, et la dit`() {
        // L'action ne valide que sans raison ; sinon, elle **repose** la raison à l'écran.
        assertTrue(
            "l'action est liée à la raison",
            dialogSource.contains("enabled = reason == null"),
        )
        assertTrue(
            "appuyer sur une action inerte dit pourquoi",
            dialogSource.contains("if (reason == null) onCreate(name.trim(), chosen.toSet()) else blocked = reason"),
        )
        assertTrue(
            "la raison est montrée à côté de l'action",
            dialogSource.contains("(blocked ?: reason)?.let { why ->"),
        )
        // Jamais un bouton grisé qui ne dit rien : la raison est aussi sa description lue.
        assertTrue("la raison est la description de l'action", dialogSource.contains("description = reason,"))
    }

    @Test
    fun `le compte des bots choisis se dit en francais`() = inFrench {
        assertEquals("1 choisi", roomPickedLine(1))
        assertEquals("3 choisis", roomPickedLine(3))
    }

    @Test
    fun `le nom envoyé est celui tapé, sans ses blancs`() {
        assertEquals("Salon des versions", roomNameToSend("  Salon des versions  "))
    }

    // MARK: - Ce qu'elle dit : aucune promesse non tenue

    @Test
    fun `l hebergement suit le chemin d'ouverture`() {
        // Le Dashboard **et** une passerelle qui tient les salons : la passerelle héberge.
        assertEquals(RoomHosting.GATEWAY, roomHosting(hasDashboard = true, gatewayRunsRooms = true))
        // Sans Dashboard, la salle est gardée ici (le chemin de `GroupRoomStore`).
        assertEquals(RoomHosting.PHONE, roomHosting(hasDashboard = false, gatewayRunsRooms = false))
        // Avec un Dashboard dont la passerelle ne tient pas de salon : l'ouverture sera refusée.
        assertEquals(RoomHosting.UNAVAILABLE, roomHosting(hasDashboard = true, gatewayRunsRooms = false))
    }

    @Test
    fun `la phrase de l'application fermee n'apparait que si la passerelle tient le salon`() = inFrench {
        val doorway = roomExplanation(RoomHosting.GATEWAY)
        val phone = roomExplanation(RoomHosting.PHONE)
        val refused = roomExplanation(RoomHosting.UNAVAILABLE)

        // Tenue : la promesse de la référence, et elle est vraie (le salon vit sur la passerelle).
        assertTrue("le salon tourne sur la passerelle", doorway.contains("passerelle"))
        assertTrue("il continue application fermée", doorway.contains("application fermée"))
        assertTrue("et la conversation est là au retour", doorway.contains("quand vous revenez"))

        // Pas tenue : la phrase ne doit **pas** y être, et ce qui se passe vraiment doit y être.
        assertFalse("pas de promesse sur ce téléphone", phone.contains("application fermée"))
        assertFalse("ni de promesse quand c'est refusé", refused.contains("application fermée"))
        assertTrue("le salon gardé ici se dit : $phone", phone.contains("gardé sur ce téléphone"))
        assertTrue("avec ce qui est vrai : $phone", phone.contains("pendant qu’Onyx est ouvert"))
        assertTrue("et un refus annoncé : $refused", refused.contains("refusée"))
    }

    @Test
    fun `la promesse est derriere la garde, dans le code aussi`() {
        // La phrase n'existe qu'une fois, et seulement dans la branche GATEWAY.
        val promise = "keeps going"
        assertEquals("une seule occurrence", 1, Regex(promise).findAll(dialogSource).count())

        val gatewayBranch = dialogSource
            .substringAfter("RoomHosting.GATEWAY ->")
            .substringBefore("RoomHosting.PHONE ->")
        assertTrue("la promesse est dans la branche passerelle", gatewayBranch.contains(promise))

        val otherBranches = dialogSource.substringAfter("RoomHosting.PHONE ->")
        assertFalse("et nulle part après", otherBranches.contains(promise))
        // Et l'écran branche la boîte sur ce que la passerelle dit tenir — pas sur une supposition.
        assertTrue(
            "l'écran lit l'hébergement",
            screenSource.contains("hasDashboard = hasDashboard,") &&
                screenSource.contains("gatewayRunsRooms = state.groupsWritable,"),
        )
    }

    @Test
    fun `aucun texte visible de la boite ne promet une continuité sans appareil`() {
        // Les autres phrases de la boîte (raisons, section) ne parlent que du compte et du nom.
        inFrench {
            listOf(
                roomBlockReason("", 3, false)!!,
                roomBlockReason("Salon", 1, false)!!,
                roomBlockReason("Salon", 8, false)!!,
                roomPickedLine(4),
            ).forEach { text ->
                assertFalse("« $text » ne promet rien de tel", text.contains("application fermée"))
                assertFalse("« $text » ne parle pas de passerelle", text.contains("passerelle"))
            }
        }
    }

    // MARK: - Les fonctions d'Onyx restent

    @Test
    fun `le tour de parole reste un bot a la fois`() {
        // Les réponses sont demandées dans l'ordre du roster, une après l'autre : la boucle pose la
        // réponse **avant** de la faire écrire, et attend la fin de la précédente.
        val loop = viewModelSource.indexOf("for (bot in asked) {")
        val writing = viewModelSource.indexOf("isStreaming = true", loop)
        val awaited = viewModelSource.indexOf("await", loop)
        assertTrue("une boucle sur les bots servis", loop > 0)
        assertTrue("la réponse est posée dans la boucle", writing > loop)
        assertTrue("et rien ne part en parallèle : pas de await dans la boucle", awaited == -1)
        // Le fil ne demande jamais tous les bots d'un coup : un seul appel de tour à la fois.
        assertEquals(
            "un seul envoi par tour",
            1,
            Regex("ChatCompletionsClient\\(botConfig\\)\\.stream\\(").findAll(viewModelSource).count(),
        )
    }

    @Test
    fun `la salle garde ses visages, son compositeur et sa voix`() {
        assertTrue("les visages", groupScreenSource.contains("BotLook("))
        assertTrue("le compositeur de la salle", groupScreenSource.contains("GroupComposer("))
        assertTrue("la voix de groupe", groupScreenSource.contains("onOpenVoice"))
        assertTrue("la lecture du fil", groupScreenSource.contains("GroupMessageRow("))
    }

    @Test
    fun `l'ecran ouvre ce qui vient d'etre cree, et n'oublie pas l'ancien chemin`() {
        assertTrue("la boîte est montée par l'écran", screenSource.contains("RoomCreationDialog("))
        assertTrue("la création passe par le modèle", screenSource.contains("viewModel.createGroup(name = name, members = members, mainName = mainName)"))
        assertTrue("le salon fait est ouvert", screenSource.contains("onOpenGroup?.invoke(made.room) ?: onGroupChat()"))
        assertTrue("le refus est montré", screenSource.contains("is GroupCreation.Failed -> roomProblem = made.problem"))
    }

    @Test
    fun `la contrainte est aussi tenue avant l'appel, sans rien tronquer`() {
        assertTrue("trop peu refusé", flowSource.contains("if (chosen.size < GroupRoom.MIN_MEMBERS)"))
        assertTrue("trop refusé aussi", flowSource.contains("if (chosen.size > GroupRoom.MAX_MEMBERS)"))
        assertFalse("plus de coupe silencieuse", flowSource.contains(".take(GroupRoom.MAX_MEMBERS)"))
    }

    @Test
    fun `les mots de la boite sont ceux de la reference, en francais`() {
        assertEquals("Nouveau salon", OnyxLocalization.translate("New room", Locale.FRANCE))
        assertEquals("Nom du salon", OnyxLocalization.translate("Room name", Locale.FRANCE))
        assertEquals("Salon des versions", OnyxLocalization.translate("Release room", Locale.FRANCE))
        assertEquals("BOTS · EN CHOISIR DE 2 À 6", OnyxLocalization.translate("Bots · pick 2 to 6", Locale.FRANCE))
        assertEquals("Créer", OnyxLocalization.translate("Create", Locale.FRANCE))
        assertEquals("Annuler", OnyxLocalization.translate("Cancel", Locale.FRANCE))
    }
}
