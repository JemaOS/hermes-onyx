package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Une réinstallation restaure les conversations du serveur — même si le premier essai rate.**
 *
 * Relevé du propriétaire : « j'ai supprimé Onyx et réinstallé, j'ai perdu toutes mes conversations…
 * ça aurait dû tout restaurer ». Sur une installation neuve, le registre du téléphone est vide : tout
 * vient de l'import des sessions du serveur ([importServerSessions]). L'import était pourtant marqué
 * comme **fait** dès le premier passage, que la lecture ait réussi ou non — et au démarrage, elle
 * échoue souvent : tunnel qui démarre, réseau pas prêt sur les premières frames. La marque valait
 * alors pour toujours, et l'app restait vide sans jamais réessayer.
 *
 * Ce verrou tient les trois pièces de la correction, parce qu'aucune ne se voit à l'écran :
 *
 * 1. l'import **rend un booléen** (la lecture a-t-elle abouti ?) ;
 * 2. la marque « déjà importé pour ce serveur » n'est posée **que** sur un succès ;
 * 3. un échec **réessaie**, à intervalle croissant et plafonné.
 */
class ServerImportRetryTest {

    private val source = File("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt").readText()

    /** Le source **sans ses commentaires** : une règle porte sur le code, pas sur son explication. */
    private val code = source.lineSequence()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("*/")
        }
        .joinToString("\n")

    private val boucle = code.substringAfter("servers.activeServer.collect {")
        .substringBefore("// Le menu qui s'ouvre")

    @Test
    fun `l import dit s il a abouti`() {
        assertTrue(
            "la fonction doit rendre un booléen",
            code.contains("private suspend fun importServerSessions(server: ServerConfig): Boolean"),
        )
        assertTrue(
            "un échec de lecture rend faux",
            code.contains("return false"),
        )
        assertTrue(
            "la lecture qui échoue est bien celle des sessions",
            code.contains("runCatching { api.summariesByProfile(profiles) }"),
        )
        // **Et la raison se dit.** Un import qui échoue en silence, c'est ce qui a coûté trois
        // versions : le volet reste vide et rien n'explique pourquoi.
        assertTrue(
            "l'échec pose une note lisible",
            code.contains("_importNote.value = OnyxLocalization.format("),
        )
        assertTrue(
            "avec le message que le volet affiche",
            code.contains("The server's conversations did not load"),
        )
    }

    @Test
    fun `l import interroge toujours au moins le profil par defaut`() {
        // Le vrai « toujours vide » : `effectiveProfile` rend `null` pour « default », donc
        // `listOfNotNull(server.effectiveProfile)` était **vide** quand le roster gardé l'était
        // aussi — c'est-à-dire après une réinstallation. Aucune lecture n'était lancée, et
        // l'import se disait réussi.
        assertTrue(
            "le profil du serveur est toujours dans la liste (chaîne vide = profil par défaut)",
            code.contains("val profiles = (listOf(server.hermesProfile) + cached?.bots.orEmpty().map { it.name })"),
        )
        assertFalse(
            "plus de liste qui peut être vide",
            code.contains("listOfNotNull(server.effectiveProfile)"),
        )
        // Et la route retire « default » et le vide : la lecture porte bien sur le profil du serveur.
        val api = File("../core/src/main/java/com/onyx/app/data/net/ServeSessionsApi.kt").readText()
        assertTrue(
            "« default » et le vide veulent dire « le profil du serveur »",
            api.contains("profile?.trim()?.takeIf { it.isNotEmpty() && !it.equals(\"default\", ignoreCase = true) }"),
        )
    }

    @Test
    fun `un import reussi seulement marque le serveur comme importe`() {
        assertTrue(
            "la marque est posée dans la branche de succès",
            boucle.contains("if (importServerSessions(server)) {") &&
                boucle.contains("importedFor = server.configSignature"),
        )
        // La forme fautive : marquer après l'appel, sans regarder le résultat.
        assertFalse(
            "la marque ne doit plus être posée inconditionnellement",
            boucle.contains("importServerSessions(server)\n                importedFor = server.configSignature"),
        )
    }

    @Test
    fun `un echec reessaie, avec une attente qui plafonne`() {
        assertTrue("la boucle réessaie tant que ce n'est pas fait", boucle.contains("while (importedFor != server.configSignature)"))
        assertTrue("elle attend entre deux essais", boucle.contains("kotlinx.coroutines.delay(attente)"))
        assertTrue("l'attente double", boucle.contains("(attente * 2)"))
        assertTrue(
            "et plafonne : sans plafond, un serveur injoignable ferait tourner une boucle serrée",
            boucle.contains(".coerceAtMost(IMPORT_RETRY_CEILING_MS)"),
        )
        assertTrue("graine du premier essai", code.contains("private const val IMPORT_RETRY_FIRST_MS = 3_000L"))
        assertTrue("plafond d'une minute", code.contains("private const val IMPORT_RETRY_CEILING_MS = 60_000L"))
    }
}
