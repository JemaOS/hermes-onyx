package com.onyx.app

import com.onyx.app.data.model.serve.GroupApproval
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupLiveSession
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.data.model.serve.GroupRoomStatus
import com.onyx.app.data.model.serve.GroupSessionSlice
import com.onyx.app.data.model.serve.GroupState
import com.onyx.app.data.net.GroupRoomApi
import com.onyx.app.ui.bots.GroupRoomFeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le fil d'une salle **lit en parallèle**.
 *
 * Le défaut : la boucle de suivi posait ses lectures **une après une** — le journal, puis
 * l'état, puis les sessions vivantes, puis les événements de chaque bot. Chacune est un
 * aller-retour sur le canal, et le tunnel Cloudflare en ajoute environ 60 ms : avec quatre
 * bots, un tour de la boucle attendait treize voyages, soit plus longtemps que l'intervalle
 * entre deux tours. La réponse du quatrième bot arrivait après les trois premiers.
 *
 * Ce test ne vérifie pas la forme du code : il **mesure**. Le faux note le début et la fin de
 * chaque appel, et le test regarde si les intervalles se **chevauchent**. Un tick sérial
 * donnerait des intervalles disjoints ; un tick parallèle les superpose.
 */
class GroupRoomFeedParalleleTest {

    /** Un appel mesuré : quand il a commencé, quand il a fini. */
    private class Mesure(val nom: String, val debut: Long, val fin: Long)

    /** Combien de temps chaque appel « réseau » dure, en millisecondes. */
    private val delai = 120L

    private val mesures = mutableListOf<Mesure>()

    /**
     * Mesure un appel. La mesure est **suspend** : le `delay` doit laisser la coroutine
     * rendrait la main, sinon les lectures ne pourraient pas se chevaucher — et le test
     * mesurerait un faux sérial.
     */
    private suspend fun mesurer(nom: String) {
        val debut = System.nanoTime()
        delay(delai)
        val fin = System.nanoTime()
        mesures += Mesure(nom, debut, fin)
    }

    /** Deux intervalles se chevauchent-ils ? C'est la marque du parallèle. */
    private fun seChevauchent(a: Mesure, b: Mesure): Boolean =
        a.debut < b.fin && b.debut < a.fin

    private open class Api : GroupRoomApi {
        override suspend fun log(room: String, since: Int): List<GroupEvent> = emptyList()
        override suspend fun roomState(room: String): GroupRoomStatus =
            GroupRoomStatus(room = null, state = GroupState())

        override suspend fun send(room: String, text: String, eventID: String) = Unit
        override suspend fun stop(room: String) = Unit
        override suspend fun rename(room: String, name: String) = Unit
        override suspend fun disband(room: String) = Unit
        override suspend fun retry(room: String, task: String) = Unit
        override suspend fun approve(room: String, approval: GroupApproval, choice: String) = Unit

        override suspend fun desktopRooms(): List<GroupRoom> = emptyList()

        override suspend fun liveSessions(room: String): List<GroupLiveSession> = emptyList()

        override suspend fun memberSession(room: String, profile: String): String? = "key-" + profile

        override suspend fun sessionEvents(session: String, since: Int): GroupSessionSlice =
            GroupSessionSlice(events = emptyList(), latest = since)
    }

    private val room = GroupRoom(
        id = "onyx-1",
        name = "Weekend hike",
        members = listOf(
            GroupMember(id = "a", profile = "coder", handle = "coder", displayName = "Coder"),
            GroupMember(id = "b", profile = "kilo", handle = "kilo", displayName = "Kilo"),
            GroupMember(id = "c", profile = "planner", handle = "planner", displayName = "Planner"),
        ),
    )

    @Test
    fun `le journal et l etat de la salle sont lus en meme temps`() = runBlocking {
        val api = object : Api() {
            override suspend fun log(room: String, since: Int): List<GroupEvent> {
                mesurer("groups.log")
                return emptyList()
            }

            override suspend fun roomState(room: String): GroupRoomStatus {
                mesurer("groups.state")
                return GroupRoomStatus(room = null, state = GroupState())
            }
        }
        val feed = GroupRoomFeed(room, api)

        feed.look()

        val log = mesures.first { it.nom == "groups.log" }
        val state = mesures.first { it.nom == "groups.state" }

        assertTrue(
            "le journal et l'etat doivent se chevaucher : " +
                "poses l'un apres l'autre, la reponse attend deux voyages",
            seChevauchent(log, state),
        )
    }

    @Test
    fun `les evenements de plusieurs bots sont lus en meme temps`() = runBlocking {
        // Trois sessions vivantes : une par bot de la salle.
        val api = object : Api() {
            override suspend fun liveSessions(room: String): List<GroupLiveSession> {
                mesurer("session.active_list")
                return listOf("s1", "s2", "s3").map { GroupLiveSession(id = it, key = "key-$it", status = "working") }
            }

            override suspend fun sessionEvents(session: String, since: Int): GroupSessionSlice {
                mesurer("session.events." + session)
                return GroupSessionSlice(events = emptyList(), latest = since)
            }

            override suspend fun roomState(room: String): GroupRoomStatus {
                mesurer("groups.state")
                // La salle travaille : c'est ce qui fait lire les brouillons des bots.
                return GroupRoomStatus(room = null, state = GroupState(working = true))
            }
        }
        val feed = GroupRoomFeed(room, api)

        // La salle doit être **au travail** : c'est le travail qui fait lire les brouillons.
        feed.look()
        assertTrue("la salle doit etre au travail pour lire les brouillons", feed.state.value.busy)

        feed.lookAtDrafts()

        val parSession = mesures.filter { it.nom.startsWith("session.events.") }
        assertTrue("les trois sessions doivent avoir ete lues : " + parSession.size, parSession.size == 3)
        assertTrue(
            "les lectures des bots doivent se chevaucher : " +
                "l une apres l autre, la reponse du dernier attend les precedents",
            seChevauchent(parSession[0], parSession[1]) && seChevauchent(parSession[1], parSession[2]),
        )
    }
}
