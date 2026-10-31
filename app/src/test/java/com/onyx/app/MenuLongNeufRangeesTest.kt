package com.onyx.app

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.BackgroundProcess
import com.onyx.app.data.net.ChatContextSlice
import com.onyx.app.data.net.ModelCatalog
import com.onyx.app.ui.chat.components.chatSessionIdForCopy
import com.onyx.app.ui.chat.components.chatSheetRows
import com.onyx.app.ui.chat.components.contextSliceAlpha
import com.onyx.app.ui.chat.components.processStatusLabel
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.ledger.ledgerPriceSource
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * **Le verrou du menu long du fil** — ses neuf rangées, et pas une seule qui plante.
 *
 * Le défaut d'origine : « Relevé de l'agent » **fermait l'application**. La rangée ouvrait bien sa
 * route, mais l'écran composait sa ligne de provenance des prix avec un gabarit à **trois** trous
 * et **deux** valeurs : `MissingFormatArgumentException` levée dans l'effet de chargement, au fil
 * principal, avant la première image. Le processus mourait — la pile est dans le journal du lot.
 *
 * Ce verrou ouvre **chaque destination** et **échoue si l'une lève une exception**. Deux niveaux,
 * parce que la moitié de ces destinations n'est pas exécutable sur la JVM (ce sont des écrans
 * Compose, il n'y a pas de Robolectric ici) :
 *
 *  1. **l'ouverture réelle** — tout ce que la rangée exécute avant sa première image et qui vit
 *     hors composition est appelé pour de vrai, dans une boucle qui nomme la rangée fautive ;
 *  2. **la destination** — chaque rangée est reliée à sa route, qui doit exister dans le `when` de
 *     `OnyxApp`, et les neuf doivent être exactement celles que la feuille rend.
 *
 * Le niveau 2 empêche une rangée muette ; le niveau 1 attrape le plantage. **Sa morsure** : remettre
 * l'appel à deux valeurs dans `ledgerPriceSource` fait rougir
 * `la provenance des prix porte ses trois valeurs` (le gabarit non rempli ne vaut pas la chaîne
 * attendue), et le balayage d'appareil `qa/20261029-releve-crash/menu-long-verrou.py` rougit sur
 * `Relevé de l’agent` avec `FATAL EXCEPTION` dans le journal.
 */
class MenuLongNeufRangeesTest {

    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val sheet = File("src/main/java/com/onyx/app/ui/chat/components/ChatActionSheet.kt").readText()

    /**
     * Les neuf rangées du menu, **dans l'ordre où le propriétaire les lit** (sa capture) : la
     * nouvelle conversation d'abord, le relevé en dernier.
     */
    private val nineRows = listOf(
        "New Conversation",
        "Usage & cost",
        "Background processes",
        "Restore points",
        "Copy session id",
        "Computer",
        "Terminal",
        "Settings",
        "Agent ledger",
    )

    /** Les rangées que l'app déclare dans le menu du fil (les autres viennent de la feuille). */
    private val declared = listOf("New Conversation", "Computer", "Terminal", "Settings", "Agent ledger")

    private val server = ServerConfig(
        id = "verrou",
        name = "Verrou",
        serveBaseUrl = "http://10.0.2.2:9120/",
        hermesProfile = "kilo",
    )

    // -- 1. La feuille rend exactement ces neuf rangées, dans cet ordre ----------------------

    @Test
    fun `la feuille rend les neuf rangees du menu, dans leur ordre`() {
        val rows = chatSheetRows(declared.map { label -> MenuItem(label, Symbols.Ellipsis) {} })
        assertEquals(nineRows, rows.map { it.label })
    }

    @Test
    fun `chaque rangee porte son propre pictogramme`() {
        // Deux rangées de la même feuille ne peuvent pas partager leur glyphe : c'était le défaut
        // visible sur la capture du propriétaire (« Consommation et coût » et « Relevé de l'agent »
        // portaient tous deux les barres verticales).
        val declaredSymbols = Regex("""MenuItem\("([^"]+)", Symbols\.(\w+)\)""")
            .findAll(app)
            .associate { it.groupValues[1] to it.groupValues[2] }
            .filterKeys { it in declared }
        assertEquals("toutes les rangées déclarées du menu doivent avoir leur icône", declared.size, declaredSymbols.size)

        val owned = mapOf(
            "Usage & cost" to "ChartBar",
            "Background processes" to "Clock",
            "Restore points" to "ArrowClockwise",
            "Copy session id" to "DocOnDoc",
        )
        val all = declaredSymbols + owned
        assertEquals("le menu doit couvrir ses neuf rangées", nineRows.toSet(), all.keys)
        val duplicates = all.entries.groupBy { it.value }.filterValues { it.size > 1 }
        assertTrue("pictogramme partagé par plusieurs rangées : $duplicates", duplicates.isEmpty())
    }

    // -- 2. Chaque rangée mène à une destination qui existe ------------------------------------

    @Test
    fun `chaque rangee mene a une destination qui existe`() {
        // Les rangées qui ouvrent un écran : la route est nommée dans le menu et doit avoir sa
        // branche dans le `when` de l'app.
        val screenRows = mapOf(
            "New Conversation" to "Route.CHAT.name",
            "Computer" to "Route.COMPUTER.name",
            "Terminal" to "Route.TERMINAL.name",
            "Settings" to "Route.SETTINGS.name",
            "Agent ledger" to "Route.LEDGER.name",
        )
        screenRows.forEach { (label, route) ->
            assertTrue("« $label » ne nomme pas $route", app.contains("navTo($route)"))
            val branch = route.removeSuffix(".name")
            assertTrue("aucune branche $branch dans le rendu", app.contains("$branch -> "))
        }
        // Les quatre rangées que la feuille possède ouvrent leur panneau sur place.
        listOf("ChatSheetAction.USAGE", "ChatSheetAction.PROCESSES", "ChatSheetAction.RESTORES", "ChatSheetAction.COPY_ID")
            .forEach { action -> assertTrue("« $action » n'est pas branchée", sheet.contains(action)) }
    }

    // -- 3. L'ouverture de chaque destination, exécutée --------------------------------------

    @Test
    fun `aucune destination du menu ne leve d exception a l ouverture`() {
        // Ce que chaque rangée exécute **avant sa première image**, hors composition. La boucle
        // nomme la rangée fautive : c'est elle, et pas « un test du relevé », qui dit où ça casse.
        val openings: List<Pair<String, () -> Any?>> = listOf(
            // « Consommation et coût » : la pastille de chaque poste, calculée avant d'être peinte.
            "Usage & cost" to {
                contextSliceAlpha(ChatContextSlice(id = "tools", label = "Tools", tokens = 1200), 4000f)
            },
            // « Processus en arrière-plan » : l'état de chaque ligne, dans ses quatre cas.
            "Background processes" to {
                listOf(
                    processStatusLabel(BackgroundProcess(id = "p1", command = "gradle", status = "running", uptimeSeconds = 65)),
                    processStatusLabel(BackgroundProcess(id = "p2", command = "ls", status = "exited", exitCode = 0)),
                    processStatusLabel(BackgroundProcess(id = "p3", command = "false", status = "exited", exitCode = 2)),
                    processStatusLabel(BackgroundProcess(id = "p4", command = "wait", status = "running")),
                )
            },
            // « Copier l'identifiant de session » : l'identifiant se rend **entier**.
            "Copy session id" to { chatSessionIdForCopy("session-0123456789") },
            // « Relevé de l'agent » : la provenance des prix, composée par l'écran lui-même.
            "Agent ledger" to { ledgerPriceSource(server, listOf("kilo", "hermes"), Locale.FRANCE) },
        )
        openings.forEach { (row, open) ->
            try {
                open()
            } catch (thrown: Throwable) {
                fail("« $row » a levé ${thrown::class.java.name} à l'ouverture : ${thrown.message}")
            }
        }
    }

    /**
     * **La morsure du verrou.** Une provenance qui perd une valeur n'est pas une provenance : le
     * gabarit a trois trous, il reçoit trois valeurs, et le texte le dit.
     */
    @Test
    fun `la provenance des prix porte ses trois valeurs`() {
        val source = ledgerPriceSource(server, listOf("kilo", "hermes"), Locale.FRANCE)
        assertEquals(
            "${server.serveBase} · ${ModelCatalog.MODEL_OPTIONS_ROUTE}?profile=kilo, hermes",
            source,
        )
        assertFalse("un gabarit non rempli est resté à l'écran : $source", source.contains("%"))
        assertFalse("la provenance est vide", source.isBlank())
        assertNotNull(source)
    }
}
