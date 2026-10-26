package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le câblage de l'écran « Ordinateur », vérifié sur les fichiers.
 *
 * Ces assertions lisent le source (comme les autres tests de câblage du dépôt) parce que le
 * comportement vit dans des gestes Compose, un WebSocket et un service Python que la JVM
 * n'exécute pas ici. Ce qu'elles gardent, ce sont les règles qui ne doivent pas se perdre:
 * une commande ne part jamais seule, un refus ne part pas du tout, et quitter l'écran ne coupe
 * pas la session.
 */
class ComputerBridgeWiringTest {

    private fun read(path: String) = File(path).readText()

    private val screen = read("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt")
    private val session = read("src/main/java/com/onyx/app/ui/computer/ComputerSession.kt")
    private val store = read("src/main/java/com/onyx/app/data/local/ComputerBridgeStore.kt")
    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val container = read("src/main/java/com/onyx/app/di/AppContainer.kt")
    private val bridge = read("../tools/onyx-computer-bridge/bridge.py")

    @Test
    fun `l'ecran ordinateur est atteignable, et pas par un chemin detourne`() {
        assertTrue(app.contains("COMPUTER,"))
        assertTrue(app.contains("""add(MenuItem("Computer", Symbols.DesktopComputer) { navTo(Route.COMPUTER.name) })"""))
        assertTrue(app.contains("Route.COMPUTER -> ComputerScreen("))
        assertTrue(app.contains("session = AppContainer.computerSession,"))
    }

    @Test
    fun `la session appartient au processus, pas a l'ecran`() {
        // Le nœud du lot: sortir de l'écran ne doit ni couper la connexion ni rendre la main.
        assertTrue(container.contains("computerSession = com.onyx.app.ui.computer.ComputerSession(computer)"))
        assertFalse(
            "quitter l'écran ne doit pas fermer la session",
            screen.contains("onDispose { session.stop()"),
        )
        assertFalse("ni rendre la main en sortant", screen.contains("onDispose { session.release()"))
        assertTrue("la session garde la dernière image", session.contains("val frame get() = client.frame"))
    }

    @Test
    fun `une commande ne part qu'apres approbation`() {
        // `run` pose la question, `approve` envoie, et rien d'autre n'envoie une commande.
        assertTrue(session.contains("_approval.value = ComputerApproval(ComputerInput.RUN, trimmed)"))
        assertTrue(session.contains("ComputerInput.RUN -> client.run(pending.detail, approved = true)"))
        assertFalse(
            "aucun chemin n'envoie une commande sans approbation",
            session.contains("client.run(pending.detail, approved = false)"),
        )
        assertFalse(session.contains(".run(command, approved = false)"))
    }

    @Test
    fun `un refus ne part pas du tout`() {
        val refusal = session.substringAfter("fun refuse()").substringBefore("fun clearRefusal()")
        assertFalse("un refus ne doit rien envoyer", refusal.contains("client."))
        assertTrue(refusal.contains("_refused.value = pending.detail"))
    }

    @Test
    fun `l'ecran demande l'approbation avant de laisser partir une action sensible`() {
        assertTrue(screen.contains("""AlertAction("Refuse") { session.refuse() }"""))
        assertTrue(screen.contains("""AlertAction("Approve", destructive = true) { session.approve() }"""))
        assertTrue(screen.contains("onDismiss = null"))
        assertTrue(screen.contains("""title = if (pending.action == ComputerInput.RUN) "Run this command?" """))
    }

    @Test
    fun `chaque entree du menu referme le menu`() {
        // Un menu qui reste ouvert sous la question qu'il ouvre se répond à travers lui.
        val items = screen.substringAfter("items = buildList {").substringBefore("onDismiss = { showMenu = false }")
        assertEquals(6, Regex("showMenu = false").findAll(items).count())
    }

    @Test
    fun `le jeton vit dans le trousseau et ne s'affiche jamais en entier`() {
        assertTrue(store.contains("SecretStore(context)"))
        assertTrue(store.contains("secrets.put(KEY_TOKEN, trimmed)"))
        // Quand le trousseau refuse, l'app le dit au lieu de perdre le jeton en silence.
        assertTrue(store.contains("val keystoreRefused: Boolean"))
        assertTrue(screen.contains("keystoreRefused = session.keystoreRefused,"))
        assertTrue(screen.contains("it lasts for this session only."))
        assertTrue("l'adresse seule va dans les préférences", store.contains("""getString(KEY_ADDRESS, "")"""))
        assertTrue("l'écran masque le champ du jeton", screen.contains("secret = true"))
        assertTrue("le jeton n'est montré que masqué", screen.contains("hasStoredToken = session.maskedToken.isNotEmpty()"))
        // La seule sortie du jeton est le presse-papiers, jamais un texte affiché.
        val uses = Regex("tokenForCopy\\(\\)").findAll(screen).count()
        assertTrue("le jeton ne sort que par la copie", uses == 1)
        assertTrue(screen.contains("ClipData.newPlainText"))
    }

    @Test
    fun `le pont ecoute en local, exige un jeton et refuse sans approbation`() {
        assertTrue(bridge.contains("""parser.add_argument("--host", default="127.0.0.1""""))
        assertTrue(bridge.contains("if not authorized(request, bridge.token):"))
        assertTrue(bridge.contains("needs_approval"))
        assertTrue(bridge.contains("if not self.lease.may_send_input(viewer):"))
        assertTrue(bridge.contains("not_holder"))
        // Un point reçu dans l'espace « screen » n'est pas mis à l'échelle une seconde fois.
        assertTrue(bridge.contains("""if str(message.get("space") or "view") == "screen":"""))
        // Le jeton n'est jamais journalisé ni rendu.
        assertTrue(bridge.contains("log_line(\"token_rejected\", path=request.path)"))
        assertFalse(bridge.contains("token=bridge.token"))
        assertTrue(bridge.contains("\"ok\": True"))
    }

    @Test
    fun `le pont ne tue rien et ne bloque rien qui vienne du proprietaire`() {
        // Les seuls refus d'avance sont les motifs destructeurs, gravés des deux côtés.
        assertTrue(bridge.contains("DENY_COMMANDS"))
        assertTrue(bridge.contains("""\bmkfs(\.|\s)"""))
        val core = read("../core/src/main/java/com/onyx/app/data/net/ComputerSensitivity.kt")
        assertTrue(core.contains("""\bmkfs(\.|\s)"""))
    }
}
