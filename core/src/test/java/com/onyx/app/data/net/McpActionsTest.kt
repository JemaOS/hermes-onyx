package com.onyx.app.data.net

import com.onyx.app.data.model.serve.McpServerDraft
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le formulaire d'ajout d'un serveur MCP : ce qui se refuse **avant** l'envoi, et ce que le serveur
 * répond ensuite — dans les deux cas en français.
 *
 * Les corps d'erreur de ce test sont ceux de la route réelle : `{"detail":"Provide exactly one of
 * URL (HTTP/SSE) or command (stdio)"}` en 400, `{"detail":"Server 'x' already exists"}` en 409,
 * `{"detail":[{"loc":["body","name"],"msg":"Field required"}]}` en 422, et `{"detail":"Server 'x'
 * not found"}` en 404. C'est ce qui permet de dire *pourquoi* plutôt qu'un code.
 */
class McpActionsTest {

    @After
    fun restoreLanguage() {
        OnyxLanguage.apply(OnyxLanguage.SYSTEM)
    }

    private fun french() = OnyxLanguage.apply(OnyxLanguage.FRENCH)

    @Test
    fun `un nom manquant est refuse avant l'envoi`() {
        french()
        assertEquals(
            "Donnez un nom au serveur.",
            OnyxLocalization[mcpDraftProblem(McpServerDraft(name = "  ", command = "npx")).orEmpty()],
        )
    }

    @Test
    fun `il faut un transport et un seul`() {
        french()
        // Ni adresse ni commande.
        assertEquals(
            "Saisissez la commande qui lance le serveur.",
            OnyxLocalization[mcpDraftProblem(McpServerDraft(name = "x")).orEmpty()],
        )
        // Par adresse, mais sans adresse.
        assertEquals(
            "Saisissez l’adresse du serveur.",
            OnyxLocalization[mcpDraftProblem(McpServerDraft(name = "x", transport = "http")).orEmpty()],
        )
    }

    @Test
    fun `une variable d'environnement sans valeur est refusee`() {
        french()
        assertEquals(
            "Chaque variable d’environnement a besoin d’un nom et d’une valeur, sous la forme CLE=valeur.",
            OnyxLocalization[
                mcpDraftProblem(McpServerDraft(name = "x", command = "npx", env = "TOKEN")).orEmpty(),
            ],
        )
        // Avec son « = », la même ligne passe.
        assertNull(mcpDraftProblem(McpServerDraft(name = "x", command = "npx", env = "TOKEN=v")))
    }

    @Test
    fun `l'authentification par en-tete exige un jeton`() {
        french()
        assertEquals(
            "Un jeton est nécessaire pour l’authentification par en-tête.",
            OnyxLocalization[
                mcpDraftProblem(
                    McpServerDraft(name = "x", transport = "http", url = "https://x/mcp", auth = "header"),
                ).orEmpty(),
            ],
        )
        assertNull(
            mcpDraftProblem(
                McpServerDraft(
                    name = "x",
                    transport = "http",
                    url = "https://x/mcp",
                    auth = "header",
                    bearerToken = "t",
                ),
            ),
        )
    }

    @Test
    fun `un brouillon complet ne trouve aucun empechement`() {
        assertNull(
            mcpDraftProblem(
                McpServerDraft(name = "x", command = "npx", args = "-y @scope/s", env = "K=v"),
            ),
        )
    }

    @Test
    fun `l'environnement se decoupe en couples nom valeur`() {
        val draft = McpServerDraft(name = "x", command = "npx", env = " A=1 \nB=2=3\n\n")
        assertEquals(listOf("A" to "1", "B" to "2=3"), draft.environment)
        assertEquals(emptyList<String>(), draft.malformedEnvironment)
        assertEquals(listOf("-y", "@scope/s"), McpServerDraft(name = "x", command = "npx", args = "-y  @scope/s").argumentList)
    }

    @Test
    fun `les refus du serveur se disent en francais, chacun par sa cause`() {
        french()
        assertEquals(
            "Un seul transport : une adresse, ou une commande — pas les deux.",
            mcpFailureMessage(
                TransportError.Http(400, """{"detail":"Provide exactly one of URL (HTTP/SSE) or command (stdio)"}"""),
            ),
        )
        assertEquals(
            "Un serveur nommé probe existe déjà.",
            mcpFailureMessage(TransportError.Http(409, """{"detail":"Server 'probe' already exists"}"""), "probe"),
        )
        assertEquals(
            "Le serveur inexistant est introuvable.",
            mcpFailureMessage(TransportError.Http(404, """{"detail":"Server 'inexistant' not found"}"""), "inexistant"),
        )
        assertEquals(
            "probe vient du greffon kanban ; il ne se règle pas ici.",
            mcpFailureMessage(
                TransportError.Http(409, """{"detail":"Server 'probe' is provided by plugin 'kanban' and cannot be modified"}"""),
                "probe",
            ),
        )
        assertEquals(
            "Le serveur attend tous les champs obligatoires. Vérifiez le formulaire et renvoyez.",
            mcpFailureMessage(
                TransportError.Http(422, """{"detail":[{"loc":["body","name"],"msg":"Field required"}]}"""),
            ),
        )
        assertEquals(
            "Les arguments vont avec une commande, pas avec une adresse.",
            mcpFailureMessage(TransportError.Http(400, """{"detail":"Arguments are only supported for stdio MCP servers"}""")),
        )
        assertEquals(
            "probe a été refusé : suspicious command/args configuration",
            mcpFailureMessage(
                TransportError.Http(400, """{"detail":"Server 'probe' rejected: suspicious command/args configuration"}"""),
                "probe",
            ),
        )
    }

    @Test
    fun `un corps inconnu n'est pas remplace par un code nu`() {
        french()
        val message = mcpFailureMessage(TransportError.Http(400, """{"detail":"something else entirely"}"""))
        assertTrue("le corps est rendu, pas perdu : $message", message.contains("something else entirely"))
        assertTrue("et la phrase reste française", message.startsWith("Le serveur a refusé"))
    }

    @Test
    fun `un 401 garde le message du transport`() {
        french()
        val message = mcpFailureMessage(TransportError.Http(401, """{"detail":"Unauthorized"}"""))
        assertTrue(message.contains("401"))
    }

    @Test
    fun `en anglais les memes refus restent les phrases du dictionnaire`() {
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        assertEquals(
            "Give one transport only: an address, or a command — not both.",
            mcpFailureMessage(TransportError.Http(400, """{"detail":"Provide exactly one of URL (HTTP/SSE) or command (stdio)"}"""), null),
        )
        assertEquals(Locale.ENGLISH, OnyxLanguage.preferred)
    }
}
