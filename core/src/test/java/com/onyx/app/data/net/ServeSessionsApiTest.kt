package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le **registre lu en entier**.
 *
 * C'est ce qui rend vérifiable la règle du groupe par défaut (`withHomeGroup` : « Home = toutes les
 * conversations du registre − celles qu'un projet réclame »). Le Dashboard plafonne `limit` à 100
 * par appel : sans pagination, la première page serait prise pour la base, et les conversations
 * au-delà de la centième resteraient sans groupe — le défaut même que le lot corrige.
 */
class ServeSessionsApiTest {

    @Test
    fun `le registre est lu page apres page, jusqu a une page courte`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val api = sessionsApi(offsets) { offset ->
            listOf(
                listOf("s1", "s2"),
                listOf("s3", "s4"),
                listOf("s5"),
            ).getOrElse(offset / 2) { emptyList() }
        }

        val all = api.allSummaries(pageSize = 2, maxPages = 10)

        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), all.map { it.id })
        // Une page courte arrête la lecture : on ne demande pas une page de plus pour rien.
        assertEquals(listOf(0, 2, 4), offsets)
    }

    @Test
    fun `une conversation vue deux fois ne compte qu une fois`() = runBlocking {
        // Une écriture pendant la lecture peut décaler les pages : le dédoublonnage est ce qui
        // empêche une conversation d'être comptée deux fois dans un groupe.
        val api = sessionsApi(mutableListOf()) { offset ->
            if (offset == 0) listOf("s1", "s2") else listOf("s2", "s3")
        }

        val all = api.allSummaries(pageSize = 2, maxPages = 3)

        assertEquals(listOf("s1", "s2", "s3"), all.map { it.id })
    }

    @Test
    fun `une base plus courte que la page ne fait qu une demande`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val api = sessionsApi(offsets) { listOf("seule") }

        assertEquals(listOf("seule"), api.allSummaries(pageSize = 100).map { it.id })
        assertEquals(listOf(0), offsets)
    }

    /** Le plafond de pages **borne** la lecture d'une base énorme : la sonde n'emballe pas le serveur. */
    @Test
    fun `le plafond de pages borne la lecture`() = runBlocking {
        val offsets = mutableListOf<Int>()
        // Des pages toujours pleines : sans plafond, la lecture ne s'arrêterait jamais.
        val api = sessionsApi(offsets) { offset -> listOf("s$offset", "s${offset + 1}") }

        val all = api.allSummaries(pageSize = 2, maxPages = 3)

        assertEquals(listOf(0, 2, 4), offsets)
        assertEquals(6, all.size)
    }

    /** Les pages sont demandées **en ordre de création** : un tri qui bouge ferait sauter des conversations. */
    @Test
    fun `les pages sont demandees en ordre de creation`() = runBlocking {
        val orders = mutableListOf<String>()
        // Une page courte : une seule demande, donc une seule trace à lire.
        val api = sessionsApi(mutableListOf(), orders) { listOf("s1") }

        api.allSummaries(pageSize = 2, maxPages = 2)

        assertEquals(listOf("created"), orders)
    }
    /** Une page **pleine** suivie d'une page vide : la lecture s'arrête sur la vide, sans rien perdre. */
    @Test
    fun `une page pleine suivie d une vide s arrete sur la vide`() = runBlocking {
        val offsets = mutableListOf<Int>()
        val api = sessionsApi(offsets) { offset -> if (offset == 0) listOf("s1", "s2") else emptyList() }

        val all = api.allSummaries(pageSize = 2, maxPages = 10)

        assertEquals(listOf("s1", "s2"), all.map { it.id })
        assertEquals(listOf(0, 2), offsets)
    }
}

/**
 * Un registre simulé : chaque appel `api/sessions` répond la page que [page] donne pour son
 * `offset`, et [asked] garde la trace des offsets demandés. La clé d'installation dispense de la
 * sonde d'authentification, il n'y a donc pas de bruit dans la trace.
 */
private fun sessionsApi(
    asked: MutableList<Int>,
    orders: MutableList<String> = mutableListOf(),
    page: (Int) -> List<String>,
): ServeSessionsApi {
    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val offset = request.url.queryParameter("offset")
        if (!request.url.encodedPath.endsWith("/api/sessions") || offset == null) {
            request.respond("""{"ok":true}""")
        } else {
            asked += offset.toInt()
            request.url.queryParameter("order")?.let { orders += it }
            val rows = page(offset.toInt()).joinToString(",") { id -> """{"id":"$id"}""" }
            request.respond("""{"sessions":[$rows],"total":0,"limit":100,"offset":$offset}""")
        }
    }.build()
    val config = ServerConfig(
        id = "test",
        serveBaseUrl = "https://hermes.test",
        // « Une clé » : aucun mot de passe, donc aucune sonde de session avant la lecture.
        apiKey = "installation-key",
    )
    return ServeSessionsApi(ServeClient(config, http))
}

private fun Request.respond(body: String): Response = Response.Builder()
    .request(this)
    .protocol(Protocol.HTTP_1_1)
    .code(200)
    .message("test")
    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
    .build()
