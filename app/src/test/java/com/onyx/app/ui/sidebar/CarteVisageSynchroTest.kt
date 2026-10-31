package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.bots.conversationProfile
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.ProfileInForce
import com.onyx.app.ui.design.botFace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La synchronisation du visage d'une carte de conversation.**
 *
 * La demande du propriétaire : « le visage du bot toujours en rapport avec le nom et **synchro si je
 * change son visage** ». La carte ne peut donc pas porter une copie figée au moment où la
 * conversation a été créée : elle doit montrer le visage que l'identité du bot porte **maintenant**.
 *
 * Le chemin est celui du volet : la liste demande le visage au magasin vivant (`faceCodeOf`), ligne
 * par ligne, à chaque construction de la liste — cette fonction est celle que `OnyxApp` appelle en
 * lisant l'état des bots. Ce test mesure donc la **seule** chose qui compte : une même conversation,
 * deux visages enregistrés différents, deux visages de carte différents.
 */
class CarteVisageSynchroTest {

    private fun conversation(id: String, sessionId: String) = Conversation(
        id = id,
        title = "Réunion de chantier",
        createdAt = 0L,
        updatedAt = 0L,
        serverSessionId = sessionId,
    )

    private val conversation = conversation("c1", "session-1")
    private val now = 1_000.0

    /** La liste telle que le volet la construit, avec l'identité **vivante** passée en argument. */
    private fun rows(faceOf: (String) -> String?): List<SidebarRow> = sidebarRows(
        conversations = listOf(conversation),
        liveKinds = emptyMap(),
        seenMarks = emptyMap(),
        now = now,
        agentName = "default",
        identityOf = { "scout" },
        faceCodeOf = faceOf,
    )

    /** Deux visages **valides** (la grammaire de `BotFace.parse`) et visiblement différents. */
    private val visageA = "bubble-happy-smile-antenna-1"
    private val visageB = "egg-wide-grin-blush-4"

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    @Test
    fun `changer le visage d un bot change le visage de sa carte`() {
        val avant = rows({ visageA }).single()
        val apres = rows({ visageB }).single()
        assertEquals("l'identité de la ligne est le nom de profil du bot", "scout", avant.identity)
        assertEquals("le visage enregistré est celui que la liste a lu", visageA, avant.identityFace)
        assertEquals("le visage enregistré est celui que la liste a lu", visageB, apres.identityFace)
        val visageAvant = botFace(avant.identity, avant.identityFace)
        val visageApres = botFace(apres.identity, apres.identityFace)
        assertNotEquals(
            "changer le visage du bot ne change pas le visage de sa carte : la carte en montre une copie figée",
            visageAvant,
            visageApres,
        )
        println(
            "synchro : scout + $visageA -> ${visageAvant.colour} ${visageAvant.shape}-${visageAvant.eyes} · " +
                "scout + $visageB -> ${visageApres.colour} ${visageApres.shape}-${visageApres.eyes}",
        )
    }

    @Test
    fun `une copie figee a la creation ne montrerait pas le visage vivant — le temoin nomme la valeur`() {
        // **Le témoin négatif** : le visage tel qu'une copie faite à la création le garderait, et le
        // visage vivant après le changement. Les deux diffèrent — c'est exactement ce qu'une carte
        // qui figerait son visage montrerait, et que le test ci-dessus refuse.
        val figeAuDebut: BotFace = botFace("scout", visageA)
        val vivant = rows({ visageB }).single()
        val vivantFace = botFace(vivant.identity, vivant.identityFace)
        assertNotEquals(
            "le témoin négatif ne diffère pas du vivant : la dent de synchro ne mesure rien",
            figeAuDebut,
            vivantFace,
        )
        // Et sans visage enregistré, c'est le NOM qui décide : deux profils, deux visages.
        val sansVisage = rows({ null }).single()
        assertTrue(
            "un profil sans visage enregistré doit tirer le sien de son nom",
            botFace(sansVisage.identity, sansVisage.identityFace) == botFace("scout", null),
        )
        assertNotEquals(
            "deux profils différents montrent le même visage : la carte n'est pas liée au nom",
            botFace("scout", null),
            botFace("quill", null),
        )
    }

    /**
     * **La preuve par identité RÉELLE du roster — pas un bot de laboratoire.**
     *
     * Deux bots du roster, avec leur visage **choisi**, un **seul** serveur : c'est exactement la
     * situation de la capture du propriétaire (trois cartes, un seul visage). Chaque conversation est
     * rattachée à **son** bot (son chat serveur), et l'identité de sa carte est le **profil de ce bot**
     * (`conversationProfile`) — la clé unique de la maison, la même que l'en-tête et les onglets.
     *
     * Le témoin nommé est l'**ancienne règle** : l'identité résolue **par serveur** donne le **même**
     * profil aux deux conversations — donc le **même** visage de repli. C'est le défaut « mauvaise
     * synchro ».
     */
    @Test
    fun `deux bots reels de visages differents rendent deux visages differents sur leurs cartes`() {
        val scout = BotProfile(name = "scout", displayName = "scout", faceCode = visageA, chatId = "s-scout")
        val quill = BotProfile(name = "quill", displayName = "quill", faceCode = visageB, chatId = "s-quill")
        val bots = listOf(scout, quill)
        val server = ServerConfig(id = "srv", name = "Onyx", hermesProfile = "default")
        val conversations = listOf(
            conversation("c-scout", "s-scout"),
            conversation("c-quill", "s-quill"),
        )
        val faces = bots.associate { it.name to it.faceCode }

        // **La liste telle que le volet la construit** : l'identité résolue **par conversation**.
        val rows = sidebarRows(
            conversations = conversations,
            liveKinds = emptyMap(),
            seenMarks = emptyMap(),
            now = now,
            agentName = "default",
            identityOf = { c -> conversationProfile(c, bots, server, "default") },
            faceCodeOf = { name -> faces[name] },
        )
        assertEquals("une ligne par conversation", 2, rows.size)
        assertEquals("l'identité de la carte vient du bot de la conversation", listOf("scout", "quill"), rows.map { it.identity })
        assertEquals("le visage enregistré est celui du bot", listOf(visageA, visageB), rows.map { it.identityFace })

        val visages = rows.map { botFace(it.identity, it.identityFace) }
        assertEquals(
            "deux bots de visages différents montrent le même visage sur leurs cartes : la synchro est cassée",
            2,
            visages.distinct().size,
        )

        // **Le témoin négatif, nommé** : l'ANCIENNE règle — l'identité par serveur — donne UN seul
        // profil (le `default` du serveur), donc UN seul visage pour les deux cartes.
        val ancienProfils = conversations.map { ProfileInForce.inForce(null, server.effectiveProfile) }
        assertEquals(
            "l'ancienne règle (identité par serveur) devrait donner un seul profil aux deux " +
                "conversations — sinon le témoin ne mesure rien",
            1,
            ancienProfils.distinct().size,
        )
        val anciensVisages = ancienProfils.map { botFace(it, faces[it]) }
        assertEquals(
            "l'ancienne règle par serveur donne bien le MÊME visage aux deux cartes : c'est le défaut relevé",
            1,
            anciensVisages.distinct().size,
        )
        assertNotEquals(
            "la nouvelle règle doit différer de l'ancienne : elle a changé quelque chose",
            anciensVisages.first(),
            visages.first(),
        )

        // **Et la source**, pour que la règle ne puisse pas redevenir « par serveur » sans que le test le dise.
        val model = read("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt")
        assertTrue(
            "l'identité d'une ligne n'est plus résolue par conversation — ChatSidebarModel.kt",
            model.contains("identityOf(conversation)"),
        )
        assertFalse(
            "l'identité d'une ligne est encore résolue par serveur (`conversation.serverId`) — ChatSidebarModel.kt",
            model.contains("identityOf(conversation.serverId)"),
        )
        val app = read("app/src/main/java/com/onyx/app/ui/OnyxApp.kt")
        assertTrue(
            "OnyxApp ne résout plus l'identité par `conversationProfile` (conversation → bot → serveur → default)",
            app.contains("conversationProfile("),
        )
        assertFalse(
            "OnyxApp résout encore l'identité par serveur (`identityByServer`)",
            app.contains("identityByServer"),
        )
        println(
            "synchro par le roster : " + rows.joinToString(" · ") { "${it.identity}+${it.identityFace}" } +
                " → ${visages.joinToString(" / ") { "${it.colour} ${it.shape}-${it.eyes}" }} · " +
                "ancienne règle (par serveur) → un seul visage ${anciensVisages.first().colour}",
        )
    }
}
