package com.onyx.app.ui.browser

import com.onyx.app.data.net.BrowserFrames
import com.onyx.app.data.net.ScreenInput
import com.onyx.app.data.net.webSocketUrl
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le contrat du navigateur partagé, sans écran ni serveur.**
 *
 * Ce qui est tenu ici est exactement ce qui casse en silence quand on touche à la feature :
 *
 * 1. **l'adresse du socket** — `https` devient `wss`, un chemin déjà là est respecté, et une base
 *    qui n'est pas absolue rend `null` au lieu d'une URL bancale ;
 * 2. **le corps d'un geste** — fractions bornées, `seq` présent, texte plafonné : c'est ce corps
 *    que le serveur refuse s'il est périmé ;
 * 3. **le décodage d'une trame** — une trame incomplète ne doit jamais devenir une image à moitié ;
 * 4. **le coffre** — l'origine et l'empreinte, et l'absence de tout champ de valeur.
 */
class BrowserScreenTest {

    // MARK: - 1. L'adresse du socket

    @Test
    fun `une base https donne un wss, avec le chemin et le ticket`() {
        assertEquals(
            "wss://onyx.test/onyx-browser/ws?ticket=abc",
            webSocketUrl("https://onyx.test", "/onyx-browser/ws", "abc"),
        )
    }

    @Test
    fun `une base http donne un ws, et les barres obliques ne s empilent pas`() {
        assertEquals(
            "ws://192.168.1.4:8890/onyx-browser/ws?ticket=abcdef",
            webSocketUrl("http://192.168.1.4:8890/", "onyx-browser/ws", "abcdef"),
        )
    }

    @Test
    fun `une base sans schéma ne fabrique pas d adresse`() {
        assertNull(webSocketUrl("onyx.test", "/ws", "t"))
        assertNull(webSocketUrl("", "/ws", "t"))
    }

    // MARK: - 2. Le corps d'un geste

    @Test
    fun `un tap porte ses fractions, son numero de trame et le viewport`() {
        val body = ScreenInput.Tap(0.25f, 0.75f).body(seq = 7, css = 390 to 844).toString()
        assertTrue(body, body.contains("\"action\":\"click\""))
        assertTrue(body, body.contains("\"seq\":7"))
        assertTrue(body, body.contains("\"x\":0.25"))
        assertTrue(body, body.contains("\"css\":[390,844]"))
    }

    @Test
    fun `une fraction hors bornes est ramenee dans l image`() {
        val body = ScreenInput.Tap(1.8f, -0.4f).body(1, null).toString()
        assertTrue(body, body.contains("\"x\":1.0"))
        assertTrue(body, body.contains("\"y\":0.0"))
    }

    @Test
    fun `le texte envoye est plafonne a 2000 caracteres`() {
        val long = "a".repeat(5_000)
        val body = ScreenInput.Type(long).body(1, null).toString()
        assertTrue("le corps reste borné", body.length < 3_000)
    }

    @Test
    fun `un remplissage depuis le coffre envoie le nom, jamais la valeur`() {
        val body = ScreenInput.Fill("reddit").body(3, null).toString()
        assertTrue(body, body.contains("\"action\":\"fill\""))
        assertTrue(body, body.contains("\"secret\":\"reddit\""))
        assertFalse("aucune valeur dans le corps", body.contains("password"))
    }

    // MARK: - 3. Le décodage d'une trame

    @Test
    fun `une trame complete se lit, une trame incomplete ne devient rien`() {
        val ok = """
            {"type":"frame","seq":12,"data":"QQ==","url":"https://github.com/login",
             "title":"Sign in","width":390,"height":844,"css":[390,844]}
        """.trimIndent()
        val frame = BrowserFrames.frameOf(ok)
        assertEquals(12, frame?.seq)
        assertEquals("https://github.com/login", frame?.url)
        assertEquals(390, frame?.cssWidth)
        assertEquals(844, frame?.cssHeight)

        assertNull("sans image, pas de trame", BrowserFrames.frameOf("""{"type":"frame","seq":1}"""))
        assertNull("un bail n'est pas une trame", BrowserFrames.frameOf("""{"type":"lease","holder":"human"}"""))
    }

    // MARK: - 4. Le coffre

    @Test
    fun `l hote et l empreinte sont ceux du coffre`() {
        assertEquals("github.com", BrowserScreenViewModel.hostOf("https://github.com/login"))
        assertEquals("reddit.com", BrowserScreenViewModel.hostOf("https://www.reddit.com/r/hermes"))
        assertEquals("", BrowserScreenViewModel.hostOf("pas une url"))
        assertEquals("1234", BrowserScreenViewModel.fingerprintOf("motdepasse1234"))
        assertEquals("****", BrowserScreenViewModel.fingerprintOf("court"))
    }

    @Test
    fun `l ecriture au coffre lie l origine et ne relit jamais la valeur`() {
        val screen = File("src/main/java/com/onyx/app/ui/browser/BrowserScreen.kt").readText()
        assertTrue("le nom, la valeur et l'origine partent ensemble",
            screen.contains("put(\n                    name = offer.name,\n                    value = value,\n                    usedBy = listOf(offer.host),"))
        assertTrue("le champ est masqué", screen.contains("PasswordVisualTransformation()"))
        assertTrue("clavier mot de passe", screen.contains("keyboardType = KeyboardType.Password"))
        assertTrue("pas d'autocorrection sur un secret", screen.contains("autoCorrectEnabled = false"))
        assertFalse("aucun journal de la valeur", screen.contains("Log.d") || screen.contains("println"))
    }

    @Test
    fun `les modeles du serveur n ont aucun champ pour une valeur`() {
        val api = File("../core/src/main/java/com/onyx/app/data/net/BrowserScreenApi.kt").readText()
        // « value » y est le ticket, pas un secret : ce qu'on interdit, c'est un champ pour la
        // valeur d'un mot de passe — le serveur ne la renvoie jamais, l'app n'a rien à accueillir.
        assertFalse("aucun modèle ne porte de secret", api.contains("val secret:"))
        assertFalse("aucun modèle ne porte de mot de passe", api.contains("val password:"))
        assertTrue("le corps d'un bail ne contient que l'action", api.contains("fun leaseBody(action: String"))
    }

    @Test
    fun `la cle voyage sur la poignee de main, sinon le relais repond 401`() {
        val client = File("../core/src/main/java/com/onyx/app/data/net/BrowserScreenClient.kt").readText()
        assertTrue(
            "le WebSocket porte l'en-tête Authorization",
            client.contains("addHeader(\"Authorization\", \"Bearer \$it\")"),
        )
        assertTrue("la clé est celle du profil effectif", client.contains("server.effectiveApiKey"))
        assertTrue("retenue à la construction", client.contains("private val cle: String = server.effectiveApiKey"))
        // Le ticket reste dans l'URL : il est à usage unique et propre à la connexion.
        assertTrue("le ticket reste dans l'adresse", client.contains("webSocketUrl(base, BrowserScreenApi.WS_PATH, ticket)"))
    }

    @Test
    fun `la fin d une prise en main ne reste pas silencieuse`() {
        val client = File("../core/src/main/java/com/onyx/app/data/net/BrowserScreenClient.kt").readText()
        val ecran = File("src/main/java/com/onyx/app/ui/browser/BrowserScreen.kt").readText()
        // Le serveur rend la main d'office : page morte (Hermes ferme un navigateur inactif après
        // deux minutes) ou délai de quinze minutes. Ignoré, l'écran restait sur la dernière image
        // marquée « en attente » — la personne attendait un rendez-vous qui n'existait plus.
        assertFalse(
            "takeover_done n'est plus avalé",
            client.contains("\"takeover_done\" -> Unit"),
        )
        assertTrue("il porte l'état de perte", client.contains("\"lost\""))
        assertTrue("l'écran reçoit la fin", ecran.contains("onEnded = { lost, _ ->"))
        assertTrue("et une page absente se dit", ecran.contains("onIdle = {"))
        assertFalse("idle n'est plus avalé", client.contains("\"idle\" -> Unit"))
    }

    @Test
    fun `le chien de garde mesure l activite, pas seulement les trames`() {
        val client = File("../core/src/main/java/com/onyx/app/data/net/BrowserScreenClient.kt").readText()
        val ecran = File("src/main/java/com/onyx/app/ui/browser/BrowserScreen.kt").readText()
        // **Le bug qui figeait l'écran sur « En attente… » pour toujours.**
        //
        // Le chien de garde ne comptait que les **trames**. Un écran sans page à montrer ne
        // reçoit que des `idle` : son `lastFrameAt` restait à zéro, la garde ne se déclenchait
        // jamais, et le socket mort en silence derrière le relais n'était jamais noticed. La
        // reconnexion automatique existait, mais rien ne la déclenchait.
        //
        // Le remède : c'est le **silence** qui dit qu'un flux est mort, et tout message reçu le
        // prouve — un `idle` autant qu'une trame.
        assertTrue(
            "le client signale toute activité",
            client.contains("private val onAlive: () -> Unit = {}"),
        )
        assertTrue(
            "et il la signale à chaque message",
            client.contains("onAlive()"),
        )
        assertTrue(
            "l'écran mesure l'activité",
            ecran.contains("lastAliveAt"),
        )
        assertTrue(
            "la garde se sert de l'activité",
            ecran.contains("val last = lastAliveAt"),
        )
        assertFalse(
            "la garde ne se sert plus des trames seules",
            ecran.contains("val last = lastFrameAt"),
        )
        assertTrue(
            "et l'activité est armée à chaque connexion",
            ecran.contains("lastAliveAt = System.currentTimeMillis()"),
        )
    }

    @Test
    fun `les libelles de l ecran existent en francais`() {
        // **Le chemin part de la racine du dépôt**, pas du module : Gradle exécute le test avec
        // le répertoire du projet comme point de départ, et `../core/...` sortait du dépôt.
        val racine = File(System.getProperty("user.dir")).let { base ->
            if (File(base, "core/src/main/java/com/onyx/app/localization").isDirectory) base
            else base.parentFile ?: base
        }
        val fr = File(
            racine,
            "core/src/main/java/com/onyx/app/localization/OnyxFrenchSettings.kt",
        ).readText()
        listOf(
            "The agent's browser", "Waiting for the agent's browser…", "Take over", "Hand back",
            "You're driving", "Tab", "Show the browser", "Connecting…",
            "The page closed during the takeover. Ask the agent to open it again.",
            "No page is open. Ask the agent to open one.",
        ).forEach { cle ->
            assertTrue("manque : $cle", fr.contains("\"$cle\" to"))
        }
    }
}
