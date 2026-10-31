package com.onyx.app.data.net

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

/**
 * Les écritures d'un bot par le RPC du Dashboard (`ServerBots` de la référence) : le visage dans
 * `ui_meta.onyx`, la visibilité dans `ui_meta["hermes-bots"]`, et la révision attendue qui
 * accompagne chaque écriture — Hermes refuse un `ui_meta` changé entre-temps.
 */
class ServeBotsRpcWriteTest {

    /** Le dernier appel reçu, et de quoi faire répondre le faux canal. */
    private class Fake(
        private val answer: (String) -> JsonElement = { json("""{"applied":{"ui_meta":true}}""") },
    ) : RpcChannel {
        var method: String? = null
        var params: JsonObject? = null

        override suspend fun call(method: String, params: JsonObject, timeoutMillis: Long): JsonElement {
            this.method = method
            this.params = params
            return answer(method)
        }
    }

    private fun meta(params: JsonObject): JsonObject =
        (params["ui_meta"] as JsonObject)["hermes-bots"]!!.jsonObject

    @Test
    fun `remontrer un bot ecrit hidden false sur la revision lue`() = runBlocking {
        val fake = Fake()

        ServeBotsRpc(fake).setHidden("scout", hidden = false, revision = 7)

        assertEquals("profiles.configure", fake.method)
        val params = fake.params!!
        assertEquals("scout", params["name"]!!.toString().trim('"'))
        assertEquals("false", meta(params)["hidden"]!!.toString())
        assertEquals("7", params["ui_meta_expected_revisions"]!!.jsonObject["hermes-bots"]!!.toString())
    }

    @Test
    fun `cacher un bot ecrit hidden true`() = runBlocking {
        val fake = Fake()

        ServeBotsRpc(fake).setHidden("scout", hidden = true, revision = 0)

        assertEquals("true", meta(fake.params!!)["hidden"]!!.toString())
    }

    @Test
    fun `un ui_meta change ailleurs est refuse et dit en clair`() = runBlocking {
        val fake = Fake {
            json("""{"applied":{"ui_meta":true,"ui_meta_conflicts":{"hermes-bots":{"expected":0,"actual":9}}}}""")
        }

        val problem = runCatching { ServeBotsRpc(fake).setHidden("scout", hidden = false, revision = 0) }
            .exceptionOrNull()

        assertTrue("un conflit est une erreur lisible, pas un silence", problem is TransportError)
        assertEquals(
            "le message est celui de Onyx, dans la langue de l'appareil",
            OnyxLocalization["This bot was just changed elsewhere. Review it and choose again."],
            problem!!.message,
        )
    }

    @Test
    fun `sans application par Hermes, l ecriture est signalee`() = runBlocking {
        val fake = Fake { json("""{"applied":{"ui_meta":false}}""") }

        val problem = runCatching { ServeBotsRpc(fake).setFace("scout", "1:round:round:none:none:4:0:0", revision = 0) }
            .exceptionOrNull()

        assertTrue(problem is TransportError)
        assertEquals(
            OnyxLocalization["Hermes did not keep the bot setting."],
            problem!!.message,
        )
    }

    @Test
    fun `le visage part dans ui_meta onyx, ou disparait`() = runBlocking {
        val fake = Fake()

        ServeBotsRpc(fake).setFace("scout", "1:round:round:none:none:4:0:0", revision = 2)
        val face = (fake.params!!["ui_meta"] as JsonObject)["onyx"]!!.jsonObject["face"]!!.toString()
        assertEquals("1:round:round:none:none:4:0:0", face.trim('"'))

        // Sans visage choisi, `ui_meta.onyx` vaut `null` : le nom du bot décide, comme sur la référence.
        ServeBotsRpc(fake).setFace("scout", null, revision = 3)
        val cleared = (fake.params!!["ui_meta"] as JsonObject)["onyx"]
        assertEquals("null", cleared.toString())
    }

    @Test
    fun `le roster porte la revision des reglages de bot`() = runBlocking {
        val fake = Fake {
            json(
                """{"profiles":[{"name":"scout","ui_meta":{"hermes-bots":{"hidden":true}},
                   "ui_meta_revisions":{"hermes-bots":4,"onyx":1}}]}""",
            )
        }

        val bot: BotProfile = ServeBotsRpc(fake).profiles().single()

        assertTrue(bot.isHidden)
        assertEquals("la révision accompagne une écriture", 4, bot.setUpRevision)
    }

    /**
     * « Épingler en haut » : le même `ui_meta` que la visibilité, la même révision attendue — c'est
     * le réglage de la référence (`changeMeta(bot, "pinned", …)`), et Hermes Desktop le voit.
     */
    @Test
    fun `epingler un bot ecrit pinned dans le meme bloc que la visibilite`() = runBlocking {
        val fake = Fake()

        ServeBotsRpc(fake).setPinned("scout", pinned = true, revision = 3)

        assertEquals("profiles.configure", fake.method)
        val params = fake.params!!
        assertEquals("scout", params["name"]!!.toString().trim('"'))
        assertEquals("true", meta(params)["pinned"]!!.toString())
        assertEquals("3", params["ui_meta_expected_revisions"]!!.jsonObject["hermes-bots"]!!.toString())
    }

    @Test
    fun `detacher un bot ecrit pinned false`() = runBlocking {
        val fake = Fake()

        ServeBotsRpc(fake).setPinned("scout", pinned = false, revision = 0)

        assertEquals("false", meta(fake.params!!)["pinned"]!!.toString())
    }

    @Test
    fun `le roster lit l epingle, et un conflit d ui_meta est dit en clair`() = runBlocking {
        val roster = Fake {
            json("""{"profiles":[{"name":"scout","ui_meta":{"hermes-bots":{"pinned":true}}}]}""")
        }
        val bot = ServeBotsRpc(roster).profiles().single()
        assertTrue("le roster dit l'épingle", bot.pinned)

        val conflicted = Fake {
            json("""{"applied":{"ui_meta":true,"ui_meta_conflicts":{"hermes-bots":{"expected":3,"actual":9}}}}""")
        }
        val problem = runCatching { ServeBotsRpc(conflicted).setPinned("scout", pinned = true, revision = 3) }
            .exceptionOrNull()
        assertTrue("un conflit est une erreur lisible, pas un silence", problem is TransportError)
    }
}
