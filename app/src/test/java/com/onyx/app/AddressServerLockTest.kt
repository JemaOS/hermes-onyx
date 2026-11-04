package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import com.onyx.app.data.net.ChatCompletionsClient
import com.onyx.app.data.net.humanFailure
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou du défaut **vu à l'écran** sur l'écran Modèles.
 *
 * Ce qui était à l'écran, sur fond ambre : `Expected URL scheme 'http' or 'https' but no scheme was
 * found for /v1/mo…`. Deux manques, et ce fichier mord sur les deux :
 *
 * 1. une adresse de serveur **sans schéma** (`192.168.1.83:9119`) doit être **acceptée** — donc
 *    donner une URL valide, pas un refus ni une exception ;
 * 2. une **exception brute** ne doit **jamais** s'afficher à la place d'un message : ni
 *    `Expected URL scheme`, ni `java.`, ni `java.net.`, ni un nom de classe.
 *
 * La cause mesurée n'était pas là où le message le laissait croire : l'URL mal formée était
 * `/v1/models` **sans hôte**, parce que la base de l'API était **vide** (serveur réglé par son seul
 * Dashboard : `baseUrl = ""`, `serveBaseUrl` posé). La partie (a) prouve la normalisation, la
 * partie (b) prouve que **rien** de brut ne peut plus sortir, d'où qu'il vienne.
 */
class AddressServerLockTest {

    // ---------------------------------------------------------------------------------------
    // (a) Une adresse sans schéma est acceptée, et normalisée
    // ---------------------------------------------------------------------------------------

    @Test
    fun `une adresse sans schema donne une URL valide, en http`() {
        for (brut in listOf("192.168.1.83:9119", "127.0.0.1:9119", "onyx.local:9119")) {
            val config = ServerConfig(id = "t", baseUrl = brut)
            assertEquals(
                "l'adresse '$brut' doit valoir http://$brut (une machine locale ne parle pas TLS)",
                "http://$brut",
                config.apiBaseUrl,
            )
            // La preuve qui compte : OkHttp accepte l'URL (avant, `Request.Builder().url(...)`
            // levait `Expected URL scheme … and no scheme was found`).
            assertNotNull(
                "'$brut' doit composer une URL que les transports acceptent",
                "${config.apiBaseUrl}/v1/models".toHttpUrlOrNull(),
            )
        }
    }

    @Test
    fun `une adresse qui porte son schema est respectee, chemin et barre finale compris`() {
        assertEquals(
            "https://gw.example:8443/onyx",
            ServerConfig(id = "t", baseUrl = "https://gw.example:8443/onyx/").apiBaseUrl,
        )
        assertEquals(
            "http://gw.example/onyx",
            ServerConfig(id = "t", baseUrl = "http://gw.example/onyx").apiBaseUrl,
        )
        // Le Dashboard suit la même règle.
        assertEquals(
            "http://127.0.0.1:9119",
            ServerConfig(id = "t", serveBaseUrl = "127.0.0.1:9119").serveBase,
        )
        assertEquals(
            "https://me.trycloudflare.com",
            ServerConfig(id = "t", serveBaseUrl = "https://me.trycloudflare.com/").serveBase,
        )
    }

    @Test
    fun `une base d'API vide ne leve plus d'exception brute`() {
        // Le cas EXACT de la capture : le serveur est réglé par son seul Dashboard.
        val config = ServerConfig(
            id = "t",
            baseUrl = "",
            serveBaseUrl = "192.168.1.83:9119",
            transport = Transport.HERMES_SERVE,
        )
        val probe = runBlocking { ChatCompletionsClient(config).testConnection() }
        assertFalse("le serveur ne répond pas, et il faut le dire", probe.ok)
        assertNotRaw(probe.message)
        assertTrue(
            "la sonde doit dire qu'il manque une adresse d'API, en mots : « ${probe.message} »",
            probe.message.isNotBlank(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // (b) Aucune exception brute ne s'affiche
    // ---------------------------------------------------------------------------------------

    @Test
    fun `humanFailure ne recopie jamais le texte d'une exception, et dit quoi faire`() {
        val brutes = listOf(
            IllegalArgumentException(
                "Expected URL scheme 'http' or 'https' but no scheme was found for /v1/models",
            ),
            java.net.UnknownHostException("Unable to resolve host \"onyx\": No address associated with hostname"),
            java.net.ConnectException("Failed to connect to /192.168.1.83:9119"),
            java.net.SocketTimeoutException("connect timed out"),
            javax.net.ssl.SSLException("Trust anchor for certification path not found."),
            java.io.IOException("unexpected end of stream"),
            RuntimeException("java.lang.IllegalStateException: boom at com.onyx.app.X.y(X.kt:1)"),
        )
        for (brute in brutes) {
            val texte = humanFailure(brute)
            assertTrue("un message est toujours rendu (${brute.javaClass.simpleName})", texte.isNotBlank())
            assertNotRaw(texte)
        }
        // Et le repli de l'appelant est respecté quand la panne n'a rien de nommable.
        assertEquals("rien de nommable", humanFailure(RuntimeException("zip"), "rien de nommable"))
    }

    @Test
    fun `aucun fichier de l'app ne compose un message avec le texte brut d'une exception`() {
        // L'idiome interdit : un message d'écran dont le repli est le **texte brut** d'une
        // exception. `error.message()` — la méthode d'un objet de domaine, pas d'un `Throwable` —
        // ne compte donc pas (`(?![\w(])`).
        val interdits = listOf(
            Regex("""\?: error\.message(?![\w(])"""),
            Regex("""\?: it\.message(?![\w(])"""),
            Regex("""\?: e\.message(?![\w(])"""),
            Regex("""\?: failure\.message(?![\w(])"""),
            Regex("""error\.message(?![\w(])"""),
            Regex("""it\.message(?![\w(])"""),
            Regex("""exceptionOrNull\(\)\s*\?\.message(?![\w(])"""),
            Regex("""\?\.localizedMessage"""),
            Regex("""error\.toString\(\)"""),
            Regex("""error\.javaClass\.simpleName"""),
        )
        // Deux fichiers ne peuvent pas être scannés ainsi, et chacun pour une raison nommée :
        val exclus = setOf(
            // Le classifieur : c'est LUI qui construit un TransportError depuis `Throwable.message`.
            // Son `userMessage()` ne recopie jamais ce texte (il le traduit en mots).
            "core/src/main/java/com/onyx/app/data/net/TransportError.kt",
            // Le moteur TTS **local** : sa ligne `VoiceDiagnostics.note(...)` est un journal de
            // diagnostic, pas un écran. L'état affiché, lui, passe par `humanFailure` — et le test
            // suivant vérifie cette ligne-là, dans ce fichier-là.
            "core/src/main/java/com/onyx/app/voice/SpeechEngine.kt",
        )

        val racine = File("..").canonicalFile
        val fautes = mutableListOf<String>()
        for (sous in listOf("app/src/main", "core/src/main")) {
            File(racine, sous).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { fichier ->
                    val relatif = racine.toPath().relativize(fichier.canonicalFile.toPath()).toString()
                    if (relatif in exclus) return@forEach
                    fichier.readLines().forEachIndexed { index, ligne ->
                        val nue = ligne.trim()
                        // Les commentaires et le KDoc ne sont pas du code : on ne scanne que celui-ci.
                        if (nue.startsWith("//") || nue.startsWith("*") || nue.startsWith("/*")) return@forEachIndexed
                        for (interdit in interdits) {
                            if (interdit.containsMatchIn(nue)) {
                                fautes += "$relatif:${index + 1}  ${interdit.pattern}  |  $nue"
                            }
                        }
                    }
                }
        }
        assertTrue(
            "le texte brut d'une exception repart à l'écran :\n" + fautes.joinToString("\n"),
            fautes.isEmpty(),
        )
    }

    @Test
    fun `le moteur TTS local lui-meme affiche un message, pas le texte de l'exception`() {
        val source = File("../core/src/main/java/com/onyx/app/voice/SpeechEngine.kt").readText()
        assertTrue(
            "l'état affiché de la voix doit passer par humanFailure",
            source.contains("problem = humanFailure(error)"),
        )
        val arret = File("../core/src/main/java/com/onyx/app/voice/VoiceFallback.kt").readText()
        assertTrue("le repli vocal affiche humanFailure(error)", arret.contains("?: humanFailure(error)"))
    }

    /** Les motifs qu'un utilisateur ne doit **jamais** lire : ni schéma manquant, ni `java.`. */
    private fun assertNotRaw(texte: String) {
        val marques = listOf(
            "Expected URL scheme",
            "no scheme was found",
            "java.",
            "java.net.",
            "Exception",
            "IllegalArgument",
            "trust anchor",
            "Trust anchor",
            "Failed to connect",
            "Unable to resolve host",
        )
        for (marque in marques) {
            assertFalse("« $marque » ne doit pas s'afficher : « $texte »", texte.contains(marque))
        }
    }
}
