package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les verrous du lot « usage par bot »** — deux écrans, une seule racine.
 *
 * Ce que la source doit porter, et ce qu'elle ne doit plus porter :
 *
 * a. les routes de session (`session.usage`, `process.list`, `rollback.list`) partent avec
 *    l'identifiant d'**exécution** — la résolution registre → exécution vit à **un** endroit
 *    (`ServeChatActions.liveSessionId`) et on n'en écrit pas une seconde ;
 * b. une lecture du registre part **avec le profil** qui porte la session : celui du bot de la
 *    conversation, sinon celui du serveur ;
 * c. la fiche d'usage ne fait pas passer un compteur du téléphone pour celui de la passerelle : le
 *    drapeau menteur (`fromGateway`) est remplacé par des issues **nommées** ;
 * d. un compteur de jetons absent se lit « — », jamais un zéro fabriqué.
 */
class UsageParBotVerrouTest {

    private fun read(path: String) = File(path).readText()

    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val usageScreen = read("src/main/java/com/onyx/app/ui/conversations/SessionUsageScreen.kt")
    private val ledgerScreen = read("src/main/java/com/onyx/app/ui/ledger/AgentLedgerScreen.kt")
    private val chatActions = read("../core/src/main/java/com/onyx/app/data/net/ServeChatActions.kt")
    private val sessionsApi = read("../core/src/main/java/com/onyx/app/data/net/ServeSessionsApi.kt")

    // — (a) l'identifiant d'exécution, à une seule source de vérité ------------------------------

    @Test
    fun `a - les routes de session partent avec l identifiant d execution, jamais le registre`() {
        // La consommation résout l'identifiant d'exécution (via `session.resume`) **avant** d'appeler
        // `session.usage` : c'est ce que le gateway attend, et le registre (`api-…`) est refusé en
        // `4001 session not found` (`tui_gateway/session_registry.py::_sess_nowait`).
        assertTrue(
            "la résolution registre → exécution a disparu de la consommation",
            chatActions.contains("val params = sessionParams(liveSessionId(sessionId))"),
        )
        assertTrue(chatActions.contains("\"session.usage\""))
        // Les autres routes de session passent par la même résolution (une seule source de vérité).
        assertTrue(chatActions.contains("processParams(liveSessionId(sessionId))") || chatActions.contains("\"process.list\", sessionParams(liveSessionId(sessionId))"))
        assertTrue(chatActions.contains("\"rollback.list\", sessionParams(liveSessionId(sessionId))"))
        assertTrue(
            "la résolution vit dans `liveSessionId`, pas ailleurs",
            chatActions.contains("suspend fun liveSessionId(stored: String): String"),
        )
    }

    // — (b) le registre se lit par profil --------------------------------------------------------

    @Test
    fun `b - la fiche d usage demande le registre sous le profil du bot`() {
        assertTrue(
            "la fiche d'usage ne passe pas le profil : elle lira la base du profil par défaut",
            usageScreen.contains("ServeSessionsApi(ServeClient(reachable)).summary(stored, profile)"),
        )
        assertTrue("la fiche d'usage n'a plus de paramètre `profile`", usageScreen.contains("profile: String? = null,"))
        // OnyxApp lui donne le profil **du bot** de la conversation (décision unique).
        assertTrue(app.contains("profile = usageProfile,"))
        assertTrue(app.contains("usageProfile = com.onyx.app.ui.bots.conversationProfile("))
    }

    @Test
    fun `b - le releve de l agent demande le registre sous les profils des bots`() {
        assertTrue(
            "le relevé ne lit pas le registre par profil : la ligne d'un bot est introuvable",
            ledgerScreen.contains("ServeSessionsApi(client).summariesIn(profiles)"),
        )
        assertTrue(ledgerScreen.contains("ledgerProfiles(roster, server)"))
        assertTrue(ledgerScreen.contains("conversationProfile(conversation, roster, server, mainName)"))
        // **Une seule liste de profils** sert le registre **et** la table de prix : deux listes
        // divergeraient, et l'attribution par bot ne serait plus la même sur les deux routes.
        assertTrue(ledgerScreen.contains("readRegistry(client, profiles)"))
        assertTrue(ledgerScreen.contains("readPrices(server, profiles)"))
    }

    @Test
    fun `b - une lecture du registre sans profil n existe plus dans l app`() {
        assertFalse(
            "l'app relit le registre sans profil (la base `default` n'a pas la ligne d'un bot)",
            usageScreen.contains("ServeSessionsApi(ServeClient(reachable)).summary(stored)"),
        )
        assertFalse(
            "le relevé relit le registre sans profil",
            ledgerScreen.contains("ServeSessionsApi(client).allSummaries()"),
        )
        // Et la route elle-même sait porter le profil : `profile` est un paramètre de `api/sessions`.
        assertTrue(sessionsApi.contains("named(profile)?.let { put(\"profile\", it) }"))
    }

    // — (c) le drapeau menteur a disparu ---------------------------------------------------------

    @Test
    fun `c - la fiche ne fait plus passer un compteur du telephone pour celui de la passerelle`() {
        // L'ancien drapeau booléen : `false` accusait le téléphone aussi bien pour une route muette
        // que pour une route qui avait répondu sans cette ligne.
        assertFalse(
            "le drapeau menteur `fromGateway` est de retour",
            usageScreen.contains("fromGateway"),
        )
        // Il est remplacé par des issues nommées.
        assertTrue(usageScreen.contains("internal sealed interface UsageSource"))
        assertTrue(usageScreen.contains("data class NoRow(val profile: String) : UsageSource"))
        assertTrue(usageScreen.contains("data object Registry : UsageSource"))
        // La notice qui a **répondu sans la ligne** nomme le profil au lieu d'accuser le serveur.
        assertTrue(usageScreen.contains("OnyxLocalization.format(KEY_REGISTRY_NO_ROW, profileName(origin.profile)"))
        assertTrue(usageScreen.contains("holds no row for this session under the profile"))
    }

    @Test
    fun `c - la phrase de la route muette est encore traduite`() {
        // La phrase d'origine reste la clé du dictionnaire français : on ne casse pas la traduction.
        assertTrue(usageScreen.contains("internal const val KEY_ROUTE_SILENT"))
        val french = read("../core/src/main/java/com/onyx/app/localization/OnyxFrenchCommon.kt")
        assertTrue(french.contains("The gateway's usage route didn't answer for this session"))
        assertTrue(french.contains("Le registre de la passerelle a répondu, mais il ne porte aucune ligne"))
    }

    // — (d) aucun zéro fabriqué ------------------------------------------------------------------

    @Test
    fun `d - un compteur absent se lit en tiret, et le total ne se replie pas en zero`() {
        assertTrue(
            "les compteurs fabriquent encore un zéro",
            sessionsApi.contains("fun count(value: Int?): String = value?.toString() ?: \"—\""),
        )
        assertTrue(
            "le total de la fiche se replie encore sur un zéro",
            usageScreen.contains("UsageRow(\"Total\", SessionUsage.count(summary.totalTokensOrNull))"),
        )
        assertTrue(sessionsApi.contains("val totalTokensOrNull: Int?"))
    }
}
