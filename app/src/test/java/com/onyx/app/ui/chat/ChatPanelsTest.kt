package com.onyx.app.ui.chat

import com.onyx.app.data.model.Attachment
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ToolActivity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que les onglets **Flux** et **Fichiers** tirent de la conversation : les cartes du bot, les
 * fichiers échangés, et la date des groupes.
 *
 * Aucune de ces vues n'invente de donnée serveur : tout vient du fil, et ces règles sont pures, donc
 * vérifiables sans appareil. Les onglets **Idées** et **Objectifs** ont été supprimés (demande du
 * propriétaire) : leurs règles (suggestions, catégories, progression) ne sont plus testées ici,
 * parce que le code qui les portait n'existe plus.
 */
class ChatPanelsTest {

    private val utc = ZoneId.of("UTC")

    private fun user(id: String, text: String, at: Long = 0L) =
        ChatMessage(id = id, role = Role.USER, text = text, createdAt = at)

    private fun bot(id: String, text: String, at: Long = 0L, steer: Boolean = false) =
        ChatMessage(id = id, role = Role.ASSISTANT, text = text, createdAt = at, isSteer = steer)

    // — Le Feed —

    @Test
    fun `une carte par reponse ecrite, la plus recente en tete`() {
        val messages = listOf(
            user("u1", "Combien ai-je dépensé ?"),
            bot("a1", "Les dépenses de septembre ont été de 420. Veux-tu un plafond ?", at = 1_000L),
            bot("a2", "Voici le détail.", at = 2_000L),
        )
        val cards = ChatFeed.cards(messages, "Quill")
        // La plus récente d'abord, et une seule carte par réponse.
        assertEquals(listOf("a2", "a1"), cards.map { it.id })
        assertEquals(2_000L, ChatFeed.card(messages[2], "Quill").atMillis)
        // Le titre est la première phrase ; le texte court, la suite.
        assertEquals("Les dépenses de septembre ont été de 420", cards[1].title)
        assertTrue(cards[1].text.startsWith("Veux-tu un plafond ?"))
    }

    @Test
    fun `une reponse sans texte, ou une note de pilotage, ne fait pas de carte`() {
        val messages = listOf(
            bot("a1", ""),
            bot("a2", "Une vraie réponse."),
            // Un outil seul : rien à montrer.
            ChatMessage(id = "a3", role = Role.ASSISTANT, text = "", createdAt = 0L, tools = listOf(
                ToolActivity(id = "t", name = "read_file"),
            )),
            // Une note de pilotage n'est pas une production du bot.
            bot("a4", "Une note de pilotage.", steer = true),
            user("u1", "Une question."),
        )
        assertEquals(listOf("a2"), ChatFeed.cards(messages, "Quill").map { it.id })
    }

    @Test
    fun `un titre tres long est borne, et un texte sans phrase prend son debut`() {
        val long = listOf(
            "Le budget", "de septembre", "a été de quatre cent vingt euros exactement cette année",
        ).joinToString(" ")
        val card = ChatFeed.card(bot("a1", long), "Quill")
        assertTrue(card.title.length <= ChatFeed.TitleLimit)
        assertTrue(card.detail.startsWith("Le budget"))
        // Un titre vide retombe sur le nom du bot : jamais une carte sans en-tête.
        assertEquals("Quill", ChatFeed.card(bot("a2", "   \n  "), "Quill").title)
        // Le détail est borné lui aussi.
        val huge = "mot ".repeat(2_000)
        assertTrue(ChatFeed.card(bot("a3", huge), "Quill").detail.length <= ChatFeed.DetailLimit)
    }

    @Test
    fun `les cartes se groupent par jour`() {
        val now = Instant.parse("2026-10-27T20:00:00Z").toEpochMilli()
        fun at(text: String) = Instant.parse(text).toEpochMilli()
        assertEquals("Today", ChatFeed.dayLabel(at("2026-10-27T08:00:00Z"), now, utc))
        assertEquals("Today", ChatFeed.dayLabel(at("2026-10-27T19:59:00Z"), now, utc))
        assertEquals("Yesterday", ChatFeed.dayLabel(at("2026-10-26T22:00:00Z"), now, utc))
        assertEquals("This week", ChatFeed.dayLabel(at("2026-10-24T10:00:00Z"), now, utc))
        assertEquals("This week", ChatFeed.dayLabel(at("2026-10-21T10:00:00Z"), now, utc))
        assertEquals("Earlier", ChatFeed.dayLabel(at("2026-10-20T23:59:00Z"), now, utc))
        assertEquals("Earlier", ChatFeed.dayLabel(0L, now, utc))
        // Une date de la machine n'entre pas dans le libellé : ce sont des clés, pas du texte.
        assertEquals("Today", ChatFeed.dayLabel(now, now + 1_000L, utc))
    }

    // — Les Files —

    @Test
    fun `les fichiers echanges sont ceux du fil, sans doublon`() {
        val messages = listOf(
            user("u1", "Voici le contrat /tmp/contrat.pdf"),
            bot(
                id = "a1",
                text = "Voici le rapport /tmp/rapport.pdf",
                at = 5L,
            ).copy(
                tools = listOf(
                    ToolActivity(id = "t1", name = "write_file", output = "/tmp/rapport.pdf et /tmp/graph.png"),
                ),
            ),
        )
        val files = ChatFiles.exchanged(messages)
        assertEquals(listOf("/tmp/contrat.pdf", "/tmp/rapport.pdf", "/tmp/graph.png"), files.map { it.path })
        assertEquals("rapport.pdf", files[1].name)
        // Le même chemin deux fois ne fait qu'une ligne.
        val twice = ChatFiles.exchanged(listOf(user("u1", "/tmp/x.png"), bot("a1", "/tmp/x.png")))
        assertEquals(listOf("/tmp/x.png"), twice.map { it.path })
    }

    @Test
    fun `une piece jointe sans nom prend son dernier segment, sinon un mot traduit`() {
        val withName = ChatMessage(
            id = "a1",
            role = Role.ASSISTANT,
            text = "Tiens.",
            createdAt = 1L,
            attachments = listOf(Attachment(id = "at1", mimeType = "image/png", uri = "content://x/y.png", name = "photo.png")),
        )
        assertEquals("photo.png", ChatFiles.exchanged(listOf(withName)).first().name)
        val withoutName = withName.copy(
            attachments = listOf(Attachment(id = "at2", mimeType = "image/png", uri = "content://x/z.png")),
        )
        assertEquals("z.png", ChatFiles.exchanged(listOf(withoutName)).first().name)
        // Rien du tout : la vue affichera son état vide, qui dit quelque chose.
        assertTrue(ChatFiles.exchanged(emptyList()).isEmpty())
        assertTrue(ChatFiles.exchanged(listOf(user("u1", "Bonjour"))).isEmpty())
    }

    // — La solidité de la source —

    @Test
    fun `aucune cle de liste n'est ecrite deux fois`() {
        // Un `item(key = "…")` écrit deux fois dans la même liste fait tomber l'écran à
        // l'exécution (« Key was already used »), et seulement dans l'état où la liste se
        // remplit : ce contrôle lit la source, là où la faute se voit.
        val source = java.io.File("src/main/java/com/onyx/app/ui/chat/ChatPanels.kt").readText()
        val literals = Regex("""key = ("[^"$]*")""").findAll(source).map { it.groupValues[1] }.toList()
        val duplicates = literals.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("clés de liste en double : $duplicates", duplicates.isEmpty())
        // Et les clés qui portent un identifiant sont bien distinctes par leur préfixe.
        val prefixed = Regex("""key = "([a-z-]+)-\$""").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(prefixed.size, prefixed.toSet().size)
    }
}
