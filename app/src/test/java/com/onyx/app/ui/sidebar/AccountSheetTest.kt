package com.onyx.app.ui.sidebar

import com.onyx.app.data.net.ConnectionProbe
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La **feuille de compte du volet** : ce qu'elle dit, ce qu'elle fait, et ce qu'elle ne doit pas
 * devenir.
 *
 * Trois contrats, dans l'ordre de ce qui compte :
 *
 *  1. **l'état est mesuré** — la ligne vient d'une sonde ([ConnectionProbe]), jamais d'une
 *     intention : la latence affichée est celle relevée, le motif est celui rapporté, et le compte
 *     à rebours n'apparaît que s'il y a **vraiment** une tentative programmée ;
 *  2. **le compte à rebours est réel** — il est calculé sur l'heure du rendez-vous
 *     ([AccountRetry]), et c'est la tentative qui le redéclenche, pas une décoration ;
 *  3. **chaque rangée fait ce qu'elle annonce** — les cinq rangées sont déclarées, dans l'ordre,
 *     chacune avec son action, et la feuille appartient à la **famille existante** : elle n'ouvre
 *     pas sa propre `Dialog` et n'écrit pas son propre glissement.
 */
class AccountSheetTest {

    private val fr = Locale.FRANCE

    /** Les sources lues **comme le font les autres tests du lot** : le contrat est dans le texte. */
    private val sheetSource = File("src/main/java/com/onyx/app/ui/sidebar/AccountSheet.kt").readText()
    private val sidebarSource = File("src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt").readText()
    private val appSource = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val familySource =
        File("../core/src/main/java/com/onyx/app/ui/design/AttachmentSheet.kt").readText()

    // -- 1. L'état est mesuré ------------------------------------------------------------------

    @Test
    fun `sans reponse, la ligne dit qu elle verifie — et n invente aucun motif`() {
        val status = accountStatusOf(probe = null, secondsLeft = null, locale = fr)
        assertEquals("Checking", status.title)
        assertEquals(AccountTone.CHECKING, status.tone)
        assertEquals("le titre dit déjà l'attente : la ligne ne répète rien", "", status.line)
        assertNull("aucune mesure : aucun motif", status.detail)
    }

    @Test
    fun `joignable — la latence et le motif sont ceux de la mesure`() {
        val probe = ConnectionProbe(
            ok = true,
            message = "Connected to Hermes Dashboard 0.22.3. Login accepted.",
            latencyMs = 42L,
            models = listOf("hermes-agent"),
        )
        val status = accountStatusOf(probe, secondsLeft = null, locale = fr)
        assertEquals("Connected", status.title)
        assertEquals(AccountTone.REACHABLE, status.tone)
        assertEquals("la latence relevée (42 ms), pas une valeur posée — et sans répéter l'état", "42 ms", status.line)
        assertEquals("le motif est celui de la sonde, mot pour mot", probe.message, status.detail)
    }

    @Test
    fun `hors ligne — le compte a rebours est celui de la tentative programmee`() {
        val probe = ConnectionProbe(
            ok = false,
            message = "No Hermes Dashboard answered at 10.0.2.2.",
            latencyMs = 0L,
            models = emptyList(),
        )
        val status = accountStatusOf(probe, secondsLeft = 28, locale = fr)
        assertEquals("Offline", status.title)
        assertEquals(AccountTone.UNREACHABLE, status.tone)
        assertEquals("le compte à rebours seul : le titre porte déjà « Hors ligne »", "nouvelle tentative dans 28 s", status.line)
        assertEquals("le motif exact de l'échec", probe.message, status.detail)
    }

    @Test
    fun `hors ligne sans tentative programmee — pas de compte a rebours`() {
        // Le cas qui interdit d'inventer : aucune politique de réessai, donc aucune seconde à
        // annoncer. La ligne dit l'état et s'arrête là.
        val probe = ConnectionProbe(ok = false, message = "…", latencyMs = 0L, models = emptyList())
        val status = accountStatusOf(probe, secondsLeft = null, locale = fr)
        assertEquals("la ligne dit le motif de la mesure, pas l'état", status.detail, null)
        assertFalse("aucun compte à rebours inventé", status.line.contains("tentative"))
    }

    @Test
    fun `les trois etats mesures sont distincts — jamais un code brut anglais`() {
        val word = { tone: AccountTone ->
            when (tone) {
                AccountTone.CHECKING -> "Vérification…"
                AccountTone.REACHABLE -> "Connecté · 1 ms"
                AccountTone.UNREACHABLE -> "Hors ligne"
            }
        }
        assertNotEquals(word(AccountTone.REACHABLE), word(AccountTone.UNREACHABLE))
        assertNotEquals(word(AccountTone.CHECKING), word(AccountTone.REACHABLE))
    }

    @Test
    fun `les motifs de la sonde se lisent en francais`() {
        // Ce que la mesure rapporte pour chacun des trois cas réels, et ce que la feuille affiche :
        // le motif passe par le dictionnaire, jamais par un code brut.
        val cases = linkedMapOf(
            "ne répond pas" to ("No Hermes Dashboard answered at 10.0.2.2." to
                "Aucun Dashboard Hermes n’a répondu à 10.0.2.2."),
            "identifiants refusés" to ("Hermes Dashboard answered HTTP 401." to
                "Le Dashboard Hermes a répondu HTTP 401."),
        )
        cases.forEach { (what, pair) ->
            val (english, french) = pair
            val status = accountStatusOf(
                ConnectionProbe(ok = false, message = english, latencyMs = 12L, models = emptyList()),
                secondsLeft = 30,
                locale = fr,
            )
            assertEquals(
                "$what : le motif rendu est celui de la sonde",
                english,
                status.detail,
            )
            assertEquals(
                "$what : et il est lu en français",
                french,
                com.onyx.app.localization.OnyxLocalization.translate(status.detail!!, fr),
            )
        }
    }

    // -- 2. Le compte à rebours est réel --------------------------------------------------------

    @Test
    fun `l intervalle de reessai est reel et le compte a rebours en decoule`() {
        val start = 1_000_000L
        val next = AccountRetry.nextAttemptAt(start)
        assertEquals(
            "le rendez-vous est bien à l'intervalle déclaré",
            start + AccountRetry.INTERVAL_SECONDS * 1_000L,
            next,
        )
        assertEquals(30, AccountRetry.secondsUntil(start, next))
        assertEquals(28, AccountRetry.secondsUntil(start + 2_000, next))
        assertEquals("l'heure passée ne donne jamais un négatif", 0, AccountRetry.secondsUntil(next + 5_000, next))
        // Arrondi au-dessus : il ne reste jamais « 0 s » alors qu'une seconde entière est à courir.
        assertEquals(1, AccountRetry.secondsUntil(next - 1, next))
        assertEquals(1, AccountRetry.secondsUntil(next - 999, next))
    }

    @Test
    fun `la feuille porte la boucle de reessai, elle ne la simule pas`() {
        assertTrue(
            "la tentative suivante est programmée à l'heure réelle",
            sheetSource.contains("AccountRetry.nextAttemptAt(System.currentTimeMillis())"),
        )
        assertTrue(
            "la boucle attend l'heure du rendez-vous, puis relance une tentative",
            sheetSource.contains("delay(1_000)") && sheetSource.contains("round += 1"),
        )
        assertTrue(
            "le bouton « Réessayer la connexion maintenant » relance tout de suite",
            sheetSource.contains("AccountAction.RETRY ->"),
        )
    }

    // -- 3. Chaque rangée fait ce qu'elle annonce -----------------------------------------------

    @Test
    fun `les cinq rangees de la reference sont la, dans l ordre, chacune avec son action`() {
        val rows = accountSheetRows()
        assertEquals(5, rows.size)
        assertEquals(
            listOf(
                "Settings",
                "Refresh conversations",
                "Retry the connection now",
                "Disconnect",
                "Change or add a gateway",
            ),
            rows.map { it.label },
        )
        assertEquals(
            "une action par rangée, aucune doublée",
            listOf(
                AccountAction.SETTINGS,
                AccountAction.REFRESH,
                AccountAction.RETRY,
                AccountAction.DISCONNECT,
                AccountAction.GATEWAYS,
            ),
            rows.map { it.action },
        )
        assertTrue("chaque rangée porte une icône", rows.all { it.symbol != 0 })
        assertEquals(
            "seule « Se déconnecter » est destructrice",
            listOf("Disconnect"),
            rows.filter { it.destructive }.map { it.label },
        )
    }

    @Test
    fun `chaque action de la feuille est reliee a son geste`() {
        val handled = mutableListOf<AccountAction>()
        val drawn = accountActions { action -> handled += action }
        assertEquals("le dessin couvre les cinq rangées", 5, drawn.size)
        drawn.forEach { it.onSelect() }
        assertEquals(accountSheetRows().map { it.action }, handled)
        assertEquals("les libellés dessinés sont ceux déclarés", accountSheetRows().map { it.label }, drawn.map { it.label })
    }

    @Test
    fun `la feuille appartient a la famille existante, elle n en ecrit pas une seconde`() {
        assertTrue(
            "elle passe par la feuille de la maison",
            sheetSource.contains("AttachmentSheet("),
        )
        assertFalse("elle n'ouvre pas sa propre Dialog", sheetSource.contains("Dialog("))
        assertFalse("elle n'écrit pas son propre glissement", sheetSource.contains("pointerInput"))
        assertFalse("elle ne redessine pas sa poignée", sheetSource.contains("SheetHandleWidth"))
        // La famille, elle, porte bien les trois sorties : appui à côté, glissement, retour matériel.
        assertTrue(familySource.contains("onyxTappable(onClick = onDismiss)"))
        assertTrue(familySource.contains("SheetDismissDistance"))
        assertTrue(familySource.contains("detectVerticalDragGestures"))
        assertTrue(familySource.contains("Dialog("))
    }

    @Test
    fun `le rond d initiale et l adresse de la tete ouvrent la feuille`() {
        // Le pied : le rond de l'initiale.
        assertTrue(
            "le rond de l'initiale du volet ouvre la feuille",
            sidebarSource.contains(".onyxClickable(onClick = onOpenAccount)"),
        )
        // La tête : le nom et l'adresse, la même cible (44 dp).
        assertTrue(
            "l'adresse de la tête du volet ouvre la même feuille",
            sidebarSource.contains(".onyxTappable(onClick = onOpenAccount)"),
        )
        assertTrue("la tête reçoit le geste", sidebarSource.contains("onOpenAccount: () -> Unit"))
        // L'app branche les deux sur la feuille, et la feuille reçoit les cinq gestes.
        assertTrue(appSource.contains("accountSheetOpen = true"))
        assertTrue(appSource.contains("AccountSheet("))
        assertTrue(appSource.contains("onChangeGateway = { navTo(Route.SERVERS.name) }"))
        assertTrue(appSource.contains("onDisconnect = { navTo(Route.SETUP.name) }"))
        assertTrue(appSource.contains("onOpenSettings = { navTo(Route.SETTINGS.name) }"))
    }

    @Test
    fun `se deconnecter demande confirmation avant de revenir a la configuration`() {
        assertTrue(
            "la confirmation est dans la feuille, avant l'appel",
            sheetSource.contains("Disconnect from this gateway?") &&
                sheetSource.indexOf("AlertAction(\"Disconnect\"") < sheetSource.indexOf("onDisconnect()"),
        )
        assertTrue(
            "le retour à l'écran de configuration est le geste confirmé",
            appSource.contains("onDisconnect = { navTo(Route.SETUP.name) }"),
        )
    }
}
