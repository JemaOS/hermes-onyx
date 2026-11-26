package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les révisions d'écriture d'un bot : deux clés, deux comptes.
 *
 * Le serveur suit `ui_meta` **par clé** : `hermes-bots` pour les réglages de bot (visibilité,
 * épinglage, titre), `onyx` pour ce qu'Onyx y écrit (le visage). Chaque clé a sa propre compteur,
 * et une écriture doit dire la révision **de sa clé**.
 *
 * Le défaut observé : l'app écrivait le visage avec une révision `0` en dur, alors que le serveur
 * en attendait `1`. La réponse était `ui_meta_conflicts: { onyx: { expected: 0, actual: 1 } }` —
 * **l'écriture refusée**. Et l'appel étant enveloppé dans un `runCatching`, l'échec disparaissait :
 * le geste semblait passer, le serveur gardait l'ancien visage, et la bouche choisie n'arrivait
 * jamais dans la scène vocale.
 */
class BotFaceRevisionTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun row(revisions: String, onyxFace: String? = "1:bubble:round:smile:none:3:0:0"): JsonObject {
        val meta = buildJsonObject {
            put("hermes-bots", buildJsonObject { put("hidden", false) })
            put("onyx", buildJsonObject { if (onyxFace != null) put("face", onyxFace) })
        }
        return buildJsonObject {
            put("name", "default")
            put("description", "")
            put("is_default", true)
            put("ui_meta", meta)
            put("ui_meta_revisions", json.parseToJsonElement(revisions).jsonObject)
        }
    }

    @Test
    fun `la revision du visage est lue sous la cle onyx`() {
        val profile = botProfileFromRow(row("""{"hermes-bots": 2, "onyx": 1}"""))

        assertNotNull(profile)
        assertEquals("la revision des reglages de bot", 2, profile!!.setUpRevision)
        assertEquals("la revision du visage est celle de la cle onyx", 1, profile.faceRevision)
    }

    @Test
    fun `les deux revisions sont comptees a part`() {
        val profile = botProfileFromRow(row("""{"hermes-bots": 7, "onyx": 3}"""))!!

        assertEquals(7, profile.setUpRevision)
        assertEquals(3, profile.faceRevision)
    }

    @Test
    fun `une revision absente vaut zero et n invente rien`() {
        val profile = botProfileFromRow(row("""{"hermes-bots": 2}"""))!!

        assertEquals(2, profile.setUpRevision)
        assertEquals(0, profile.faceRevision)
    }

    @Test
    fun `le visage du serveur est lu dans le meme profil`() {
        val profile = botProfileFromRow(row("""{"hermes-bots": 1, "onyx": 1}"""))!!

        assertEquals("1:bubble:round:smile:none:3:0:0", profile.faceCode)
    }

    @Test
    fun `le code de visage avec bouche est compris`() {
        // La bouche est la 4e partie du code serveur : `1:forme:yeux:bouche:marque:couleur:…`.
        val face = com.onyx.app.ui.design.BotFace.parse("1:bubble:round:oh:none:3:0:0")

        assertNotNull("un code a bouche doit etre lu", face)
        assertEquals(
            com.onyx.app.ui.design.FaceMouth.OH,
            face!!.mouth,
        )
        assertEquals("1:bubble:round:oh:none:3:0:0", face.code())
    }

    @Test
    fun `une bouche sans bouche se distingue bien`() {
        val sans = com.onyx.app.ui.design.BotFace.parse("1:bubble:round:none:none:3:0:0")
        val avec = com.onyx.app.ui.design.BotFace.parse("1:bubble:round:oh:none:3:0:0")

        assertNotNull(sans)
        assertNotNull(avec)
        assertTrue("les deux visages doivent differer", sans != avec)
    }
}
