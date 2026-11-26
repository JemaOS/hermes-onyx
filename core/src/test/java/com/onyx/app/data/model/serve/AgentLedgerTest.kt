package com.onyx.app.data.model.serve

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentActivity
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.TokenUsage
import com.onyx.app.data.model.ToolActivity
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.model.TurnMetrics
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.ui.design.ServerPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * **Le relevé d'agent** — les trois règles du lot, chiffres à l'appui.
 *
 * 1. **Chaque donnée affichée vient de sa source** : les appels d'outils et les sous-agents du fil,
 *    les jetons et le coût du registre des sessions quand il a répondu, du fil sinon. Plusieurs
 *    tests vérifient la **rangée affichée**, pas seulement la donnée : c'est elle que le
 *    propriétaire lit.
 * 2. **Ce qui manque est expliqué** : un tour sans durée mesurée, un transport qui ne remonte pas
 *    les jetons, un sous-agent lancé à l'aveugle, un modèle que le fil ne consigne pas.
 * 3. **Aucun coût n'est calculé sans prix du serveur** : la ligne écrit « non fourni », et aucun
 *    montant n'apparaît nulle part. Le test [asNoCost] balaie **toutes** les rangées du relevé.
 *
 * Les montants et les prix des tests sont ceux **relevés sur le serveur réel** :
 * `minimax/minimax-m2.7` est publié à `$0.30` / `$1.20` par million de jetons, et le fournisseur
 * local du profil n'a **aucune** ligne de prix.
 */
class AgentLedgerTest {

    private val french = Locale.FRENCH
    private val english = Locale.ENGLISH

    // ————————————————————————————————————————————————————————————————————————————
    // Les formats : l'unité écrite, et rien d'inventé
    // ————————————————————————————————————————————————————————————————————————————

    @Test
    fun `les jetons s ecrivent avec leur unite`() {
        assertEquals("847 jetons", LedgerFormat.tokens(847, french))
        assertEquals("18,4k jetons", LedgerFormat.tokens(18_400, french))
        assertEquals("1M jetons", LedgerFormat.tokens(1_048_576, french))
        assertEquals("1,2M jetons", LedgerFormat.tokens(1_200_000, french))
        // Le singulier est écrit : « 1 jetons » ne se lit pas.
        assertEquals("1 jeton", LedgerFormat.tokens(1, french))
        assertEquals("847 tokens", LedgerFormat.tokens(847, english))
        assertEquals("18.4k tokens", LedgerFormat.tokens(18_400, english))
    }

    @Test
    fun `une duree s ecrit en minutes et secondes, et sous la seconde elle le dit`() {
        assertEquals("2 min 12", LedgerFormat.duration(132.4, french))
        assertEquals("45 s", LedgerFormat.duration(45.0, french))
        assertEquals("1 h 02", LedgerFormat.duration(3_725.0, french))
        // 0,4 s écrit « 0 s » se lirait comme « rien ne s'est passé ».
        assertEquals("< 1 s", LedgerFormat.duration(0.4, french))
        assertNull(LedgerFormat.duration(null, french))
    }

    @Test
    fun `un montant s ecrit a la francaise, et sous le centime il le dit`() {
        assertEquals("0,42 $", LedgerFormat.amount(0.42, french))
        assertEquals("\$0.42", LedgerFormat.amount(0.42, english))
        assertEquals("< 0,01 $", LedgerFormat.amount(0.004, french))
        assertEquals("0,00 $", LedgerFormat.amount(0.0, french))
        // Un montant absent n'est pas zéro : il n'y a rien à écrire.
        assertNull(LedgerFormat.amount(null, french))
    }

    @Test
    fun `le montant d une ligne publiee se lit, et une chaine qui n en est pas une ne se lit pas`() {
        assertEquals(0.30, LedgerFormat.moneyOf("\$0.30")!!, 1e-9)
        assertEquals(1.20, LedgerFormat.moneyOf("\$1.20")!!, 1e-9)
        // Le serveur garde la main : ce qui n'est pas un montant ne devient pas zéro.
        assertNull(LedgerFormat.moneyOf("1200 jetons"))
        assertNull(LedgerFormat.moneyOf("free"))
        assertNull(LedgerFormat.moneyOf(""))
        assertNull(LedgerFormat.moneyOf(null))
    }

    @Test
    fun `le moment du releve de prix s ecrit dans la langue`() {
        val at = LocalDateTime.of(2026, 10, 28, 17, 31).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("28 oct. 2026 à 17:31", LedgerFormat.moment(at, french))
        assertEquals("28 Oct 2026 at 17:31", LedgerFormat.moment(at, english))
    }

    // ————————————————————————————————————————————————————————————————————————————
    // L'argent : la règle, et son refus
    // ————————————————————————————————————————————————————————————————————————————

    @Test
    fun `le cout du registre passe avant tout le reste, et le reel avant l estimation`() {
        val registry = summary(actual = 0.87, estimated = 0.42)
        val money = AgentLedger.moneyOf(registry, KILO_PRICE, 1_000_000, 100_000)
        assertEquals(LedgerMoneySource.SERVER_STATED, money.source)
        assertEquals(0.87, money.usd!!, 1e-9)
        assertTrue("le fournisseur a facturé", money.billed)
    }

    @Test
    fun `l estimation du registre est retenue quand il n y a pas de cout reel`() {
        val money = AgentLedger.moneyOf(summary(estimated = 0.42), KILO_PRICE, 1_000_000, 100_000)
        assertEquals(LedgerMoneySource.SERVER_STATED, money.source)
        assertEquals(0.42, money.usd!!, 1e-9)
        assertFalse(money.billed)
    }

    @Test
    fun `un cout de zero dans le registre n est pas retenu - c est le zero trompeur`() {
        // Le serveur écrit zéro quand il n'a rien su (voir `UsageDay.cost`). Le prendre pour un
        // prix afficherait « 0,00 $ » là où il n'y a aucune donnée.
        val money = AgentLedger.moneyOf(summary(actual = 0.0, estimated = 0.0), null, 1_000_000, 100_000)
        assertNull("aucun montant", money.usd)
        assertEquals(LedgerMoneySource.NOT_PROVIDED, money.source)
        assertEquals(LedgerNoPrice.NO_PRICE_PUBLISHED, money.why)
    }

    @Test
    fun `sans cout du registre, le prix publie par le serveur autorise l estimation`() {
        // 1 000 000 de jetons d'entrée à 0,30 $ / M + 100 000 de sortie à 1,20 $ / M = 0,42 $.
        val money = AgentLedger.moneyOf(summary(), KILO_PRICE, 1_000_000, 100_000)
        assertEquals(LedgerMoneySource.SERVER_PRICE, money.source)
        assertEquals(0.42, money.usd!!, 1e-6)
        assertEquals("0,30 \$ / 1,20 \$", money.priceLine)
    }

    @Test
    fun `sans prix du serveur, aucun montant n est produit - meme avec des jetons connus`() {
        // Le tarif local du profil : le serveur n'a **pas** de ligne pour ce modèle.
        val money = AgentLedger.moneyOf(summary(), ServerPrice(), 1_000_000, 100_000)
        assertNull(money.usd)
        assertEquals(LedgerNoPrice.NO_PRICE_PUBLISHED, money.why)
        // Une ligne de prix illisible ne vaut pas mieux qu'une ligne absente : elle n'autorise
        // aucun calcul.
        val unreadable = AgentLedger.moneyOf(summary(), ServerPrice(input = "n/a", output = "n/a"), 1_000_000, 100_000)
        assertNull(unreadable.usd)
    }

    @Test
    fun `sans jeton compte, le prix seul ne produit pas de montant`() {
        val money = AgentLedger.moneyOf(summary(), KILO_PRICE, null, null)
        assertNull(money.usd)
        assertEquals(LedgerNoPrice.NO_TOKENS, money.why)
    }

    @Test
    fun `un modele declare gratuit par le serveur donne un zero qui est une donnee`() {
        val money = AgentLedger.moneyOf(summary(), ServerPrice(input = "free", output = "free", free = true), 500_000, 10_000)
        assertEquals(LedgerMoneySource.SERVER_PRICE, money.source)
        assertEquals(0.0, money.usd!!, 1e-9)
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Le relevé d'une conversation : chaque chiffre, et sa source
    // ————————————————————————————————————————————————————————————————————————————

    @Test
    fun `les appels d outils sont comptes par etat, dans le fil`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(tools = listOf(
                tool("t1", ToolStatus.COMPLETED),
                tool("t2", ToolStatus.COMPLETED),
                tool("t3", ToolStatus.FAILED),
                tool("t4", ToolStatus.RUNNING),
            )),
        )
        assertEquals(4, ledger.toolsTotal)
        assertEquals(2, ledger.toolsSucceeded)
        assertEquals(1, ledger.toolsFailed)
        assertEquals(1, ledger.toolsRunning)
        assertEquals("4", ledger.sections(french).row("Tool calls").value)
        assertEquals("2", ledger.sections(french).row("Calls that succeeded").value)
        assertEquals("1", ledger.sections(french).row("Calls that failed").value)
        assertEquals("1", ledger.sections(french).row("Still running").value)
    }

    @Test
    fun `le temps de travail est la somme des durees mesurees, et celles qui manquent sont dites`() {
        val ledger = AgentLedger.of(subject = "fil", messages = messagesWith(seconds = listOf(40.0, 52.4, 40.0), answersWithoutDuration = 1))
        assertEquals(132.4, ledger.workSeconds!!, 1e-6)
        assertEquals(3, ledger.turnsMeasured)
        assertEquals(1, ledger.turnsUnmeasured)
        assertEquals("2 min 12", ledger.sections(french).row("Working time").value)
        assertEquals("1", ledger.sections(french).row("Answers without a duration").value)
        assertEquals(
            "une réponse reste hors du total ci-dessus",
            ledger.sections(french).row("Answers without a duration").note,
        )
    }

    @Test
    fun `aucune duree mesuree se dit au lieu de peindre zero`() {
        val ledger = AgentLedger.of(subject = "fil", messages = messagesWith(answersWithoutDuration = 2))
        assertNull(ledger.workSeconds)
        assertEquals("non mesuré", ledger.sections(french).row("Working time").value)
        assertEquals("2", ledger.sections(french).row("Answers without a duration").value)
        assertTrue(ledger.sections(french).row("Working time").absent)
    }

    @Test
    fun `les sous-agents sont comptes, et ceux que le serveur ne suit pas sont dits`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(subagents = listOf(
                subagent("sa-1", child = "child-1"),
                subagent("sa-2", child = "child-2"),
                subagent("sa-3"),
            )),
        )
        assertEquals(3, ledger.subagentsLaunched)
        assertEquals(2, ledger.subagentsTracked)
        assertEquals("3", ledger.sections(french).row("Launched").value)
        assertEquals("2", ledger.sections(french).row("Tracked by the server").value)
        assertEquals(
            "une tâche a été lancée à l’aveugle : ce transport ne dit rien de son état",
            ledger.sections(french).row("Tracked by the server").note,
        )
    }

    @Test
    fun `les jetons viennent du registre quand il a repondu, du fil sinon`() {
        val fromThread = AgentLedger.of(subject = "fil", messages = messagesWith(usage = TokenUsage(input = 900, output = 120)))
        assertEquals(900L, fromThread.tokensInput)
        assertEquals(120L, fromThread.tokensOutput)
        assertEquals(LedgerSource.THIS_PHONE, fromThread.counts)
        assertEquals("comptés par ce téléphone, d’après le fil qu’il garde", fromThread.sections(french).row("Input").note)

        val fromServer = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(usage = TokenUsage(input = 900, output = 120)),
            registry = summary(input = 1_000_000, output = 100_000),
            registryAttempted = true,
        )
        assertEquals(1_000_000L, fromServer.tokensInput)
        assertEquals(LedgerSource.SERVER_REGISTRY, fromServer.counts)
        assertEquals("comptés par le registre des sessions du serveur", fromServer.sections(french).row("Input").note)
    }

    @Test
    fun `un transport qui ne remonte pas les jetons le dit, au lieu d un zero`() {
        val ledger = AgentLedger.of(subject = "fil", messages = messagesWith())
        assertNull(ledger.tokensInput)
        val row = ledger.sections(french).row("Input")
        assertEquals("non remonté", row.value)
        assertEquals("ce transport n’a remonté aucun jeton pour ce fil", row.note)
        assertTrue(row.absent)
    }

    @Test
    fun `un fil qui ne consigne pas de modele le dit, au lieu de laisser la case vide`() {
        val ledger = AgentLedger.of(subject = "fil", messages = messagesWith(), registryAttempted = true)
        val model = ledger.sections(french).row("Model")
        assertEquals("non consigné dans ce fil", model.value)
        assertTrue(model.absent)
        // Le registre a été tenté et n'a rien dit : l'écran l'écrit.
        assertEquals("le registre du serveur n’a pas répondu pour cette session", ledger.sections(french).row("Origin").note)
    }

    @Test
    fun `le modele vient du registre quand il le nomme`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(),
            registry = summary(model = "minimax/minimax-m2.7"),
            registryAttempted = true,
        )
        assertEquals("minimax/minimax-m2.7", ledger.model)
        assertEquals("minimax/minimax-m2.7", ledger.sections(french).row("Model").value)
        assertFalse(ledger.sections(french).row("Origin").absent)
    }

    @Test
    fun `un total d outils different chez le serveur est affiche a part, pas recopie`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(tools = listOf(tool("t1", ToolStatus.COMPLETED))),
            registry = summary(toolCalls = 9),
            registryAttempted = true,
        )
        assertEquals(1, ledger.toolsTotal)
        val row = ledger.sections(french).row("Tool calls (server)")
        assertEquals("9", row.value)
        assertEquals("le registre du serveur compte un total différent pour la même session", row.note)
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Les rangées du coût : la provenance, et le refus de calculer
    // ————————————————————————————————————————————————————————————————————————————

    @Test
    fun `le cout du registre est affiche avec sa provenance, sans date de table de prix`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(usage = TokenUsage(input = 1_000_000, output = 100_000)),
            registry = summary(model = "minimax/minimax-m2.7", actual = 0.87),
            registryAttempted = true,
            price = KILO_PRICE,
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val cost = ledger.sections(french, now = READ_AT).row("Actual")
        assertEquals("0,87 $", cost.value)
        // Un coût **donné** par le serveur n'est pas une estimation : la date de la table de prix
        // n'a rien à faire là, elle ferait croire à un calcul.
        assertEquals("coût du registre du serveur · $SOURCE", cost.note)
    }

    @Test
    fun `une estimation dit le prix du serveur, son adresse et sa date de releve`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(usage = TokenUsage(input = 1_000_000, output = 100_000)),
            registry = summary(model = "minimax/minimax-m2.7"),
            registryAttempted = true,
            price = KILO_PRICE,
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val sections = ledger.sections(french, now = READ_AT)
        assertEquals("0,42 $", sections.row("Estimated").value)
        val note = sections.row("Estimated").note!!
        assertTrue(note.startsWith("estimation au prix publié par le serveur"))
        assertTrue(note.contains(SOURCE))
        assertTrue(note.contains("relevé le 28 oct. 2026 à 17:31"))
        assertEquals("0,30 $ / 1,20 $", sections.row("Price").value)
        assertEquals("la ligne de prix publiée par le serveur", sections.row("Price").note)
    }

    @Test
    fun `sans prix, le releve ecrit non fourni et n ecrit aucun montant nulle part`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(usage = TokenUsage(input = 800_000, output = 60_000)),
            registry = summary(model = "sage-a1.5"),
            registryAttempted = true,
            price = ServerPrice(),
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val cost = ledger.sections(french, now = READ_AT).row("Price")
        assertEquals("non fourni", cost.value)
        assertEquals("le serveur ne publie aucun prix pour ce modèle", cost.note)
        assertTrue(cost.absent)
        assertNull(ledger.money.usd)
        asNoCost(ledger)
    }

    @Test
    fun `aucun montant n est ecrit quand la table de prix n a pas repondu`() {
        val ledger = AgentLedger.of(
            subject = "fil",
            messages = messagesWith(usage = TokenUsage(input = 800_000, output = 60_000)),
            registryAttempted = true,
        )
        assertNull(ledger.money.usd)
        assertEquals(LedgerNoPrice.NO_PRICE_PUBLISHED, ledger.money.why)
        asNoCost(ledger)
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Le cumul d'un bot
    // ————————————————————————————————————————————————————————————————————————————

    @Test
    fun `le cumul d un bot additionne ce que ses conversations ont fait`() {
        val first = AgentLedger.of(
            subject = "a",
            messages = messagesWith(seconds = listOf(40.0, 52.4), tools = listOf(tool("t1", ToolStatus.COMPLETED))),
        )
        val second = AgentLedger.of(
            subject = "b",
            messages = messagesWith(seconds = listOf(40.0), tools = listOf(tool("t2", ToolStatus.FAILED))),
        )
        val total = AgentLedger.cumulative("Onyx", listOf(first, second))
        assertEquals(LedgerScope.BOT, total.scope)
        assertEquals(2, total.conversations)
        assertEquals(132.4, total.workSeconds!!, 1e-6)
        assertEquals(2, total.toolsTotal)
        assertEquals(1, total.toolsSucceeded)
        assertEquals(1, total.toolsFailed)
        assertEquals(3, total.turnsMeasured)
    }

    @Test
    fun `un cumul dont une conversation n a pas de prix le dit, et son total reste incomplet`() {
        val priced = AgentLedger.of(
            subject = "a",
            messages = messagesWith(usage = TokenUsage(input = 1_000_000, output = 100_000)),
            registry = summary(model = "minimax/minimax-m2.7"),
            registryAttempted = true,
            price = KILO_PRICE,
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val unpriced = AgentLedger.of(
            subject = "b",
            messages = messagesWith(usage = TokenUsage(input = 800_000, output = 60_000)),
            registry = summary(model = "sage-a1.5"),
            registryAttempted = true,
            price = ServerPrice(),
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val total = AgentLedger.cumulative("Scout", listOf(priced, unpriced))
        assertEquals(0.42, total.money.usd!!, 1e-6)
        assertEquals(1, total.money.withoutPrice)
        assertEquals(1, total.withoutPrice)
        val sections = total.sections(french, now = READ_AT)
        assertEquals("1", sections.row("Conversations without a price").value)
        assertEquals("elles manquent au total ci-dessus, qui reste incomplet", sections.row("Conversations without a price").note)
    }

    @Test
    fun `un cumul ou aucune conversation n a de prix ecrit non fourni, sans montant partiel`() {
        fun unpriced(model: String) = AgentLedger.of(
            subject = "x",
            messages = messagesWith(usage = TokenUsage(input = 800_000, output = 60_000)),
            registry = summary(model = model),
            registryAttempted = true,
            price = ServerPrice(),
            priceSource = SOURCE,
            priceReadAt = READ_AT,
        )
        val total = AgentLedger.cumulative("Scout", listOf(unpriced("sage-a1.5"), unpriced("sage-a1.5")))
        assertNull("le cumul n'invente pas un total partiel", total.money.usd)
        assertEquals(2, total.money.withoutPrice)
        assertEquals("non fourni", total.sections(french, now = READ_AT).row("Price").value)
        asNoCost(total)
    }

    @Test
    fun `un bot sans conversation rend un cumul vide, pas un zero`() {
        val total = AgentLedger.cumulative("Atlas", emptyList())
        assertEquals(0, total.conversations)
        assertTrue(total.isEmpty)
        assertNull(total.money.usd)
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Les deux onglets du relevé : « cette conversation » et « par bot » (verrou f)
    // ————————————————————————————————————————————————————————————————————————————

    /**
     * **Les deux onglets ne peuvent pas rendre les mêmes chiffres quand les périmètres diffèrent.**
     *
     * Le défaut signalé : « cette conversation » et « par bot » affichaient les **mêmes lignes**,
     * seul le sous-titre changeait. La cause est l'attribution par profil — la ligne du registre
     * n'étant pas trouvée, les deux portées retombaient sur le même fil local. Ici les deux
     * conversations de `writer` ont **leur** ligne de registre, et les deux relevés se séparent par
     * les chiffres : 1 conversation contre 2, et des jetons qui s'additionnent.
     */
    @Test
    fun `par bot additionne ce que cette conversation seule ne porte pas`() {
        val one = AgentLedger.of(
            subject = "writer",
            messages = messagesWith(seconds = listOf(40.0), tools = listOf(tool("t1", ToolStatus.COMPLETED))),
            registry = summary(model = "deepseek/deepseek-v4.1-flash", input = 13977, output = 268, toolCalls = 1),
            registryAttempted = true,
        )
        val other = AgentLedger.of(
            subject = "writer",
            messages = messagesWith(seconds = listOf(52.4), tools = listOf(tool("t2", ToolStatus.COMPLETED), tool("t3", ToolStatus.FAILED))),
            registry = summary(model = "deepseek/deepseek-v4.1-flash", input = 28288, output = 9212, toolCalls = 3),
            registryAttempted = true,
        )
        val perBot = AgentLedger.cumulative("writer", listOf(one, other))

        // Le périmètre : 1 conversation contre 2.
        assertEquals(1, one.conversations)
        assertEquals(2, perBot.conversations)
        // Les chiffres : la conversation seule porte ses jetons, le cumul les additionne.
        assertEquals(13977L, one.tokensInput)
        assertEquals(42265L, perBot.tokensInput)
        assertEquals(268L, one.tokensOutput)
        assertEquals(9480L, perBot.tokensOutput)
        assertEquals(1, one.toolsTotal)
        assertEquals(3, perBot.toolsTotal)

        // Et les **lignes** rendues diffèrent : c'est ce que l'œil du propriétaire lit.
        val conversationRows = one.sections(french, now = READ_AT).flatMap { section -> section.rows.map { it.label to it.value } }
        val botRows = perBot.sections(french, now = READ_AT).flatMap { section -> section.rows.map { it.label to it.value } }
        assertFalse(
            "les deux onglets rendent des lignes identiques alors que 2 conversations contre 1",
            conversationRows == botRows,
        )
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Verrou (e) : un compteur du téléphone ne se fait pas passer pour le registre
    // ————————————————————————————————————————————————————————————————————————————

    /**
     * Sans ligne de registre pour **cette** session, le relevé dit que ses comptes viennent du
     * téléphone — il ne les habille pas en comptes du serveur.
     */
    @Test
    fun `sans ligne de registre, la provenance se dit au lieu de se faire passer pour le serveur`() {
        val ledger = AgentLedger.of(
            subject = "writer",
            messages = messagesWith(usage = TokenUsage(input = 500, output = 40)),
            registry = null,
            registryAttempted = true,
        )
        assertEquals(LedgerSource.THIS_PHONE, ledger.counts)
        assertFalse("le registre n'a pas répondu", ledger.registryAnswered)

        val model = ledger.sections(french, now = READ_AT).first { it.header == "Model" }
        val origin = model.rows.first { it.label == "Origin" }
        assertEquals("ce téléphone", origin.value)
        assertTrue("la raison doit être écrite", origin.note!!.contains("registre du serveur"))

        // Avec **sa** ligne du registre, la même session change de provenance : c'est la preuve que
        // la lecture par profil est ce qui la fait basculer.
        val served = AgentLedger.of(
            subject = "writer",
            messages = messagesWith(usage = TokenUsage(input = 500, output = 40)),
            registry = summary(model = "deepseek/deepseek-v4.1-flash", input = 13977, output = 268),
            registryAttempted = true,
        )
        assertEquals(LedgerSource.SERVER_REGISTRY, served.counts)
        assertTrue(served.registryAnswered)
        assertEquals(13977L, served.tokensInput)
        assertEquals(
            "le registre du serveur",
            served.sections(french, now = READ_AT).first { it.header == "Model" }
                .rows.first { it.label == "Origin" }.value,
        )
    }

    // ————————————————————————————————————————————————————————————————————————————
    // Les outils du test
    // ————————————————————————————————————————————————————————————————————————————

    /** Vrai nulle part dans le relevé : une valeur qui ressemble à un montant en dollars. */
    private fun asNoCost(ledger: AgentLedger) {
        val moneyRow = ledger.sections(french, now = READ_AT).first { it.header == "Cost" }
        moneyRow.rows.forEach { row ->
            assertFalse(
                "aucun montant ne doit être écrit sans prix : « ${row.label} → ${row.value} »",
                MONEY.containsMatchIn(row.value),
            )
        }
        ledger.sections(french, now = READ_AT).forEach { section ->
            section.rows.forEach { row ->
                assertFalse(
                    "aucun montant ne doit apparaître ailleurs non plus : « ${row.label} → ${row.value} »",
                    MONEY.containsMatchIn(row.value),
                )
            }
        }
    }

    private fun List<LedgerSection>.row(label: String): LedgerRow {
        val found = flatMap { it.rows }.firstOrNull { it.label == label }
        assertNotNull("la rangée « $label » doit exister", found)
        return found!!
    }

    private fun tool(id: String, status: ToolStatus) = ToolActivity(
        id = id,
        name = "terminal",
        status = status,
        startedAt = 1_000L,
        endedAt = if (status == ToolStatus.RUNNING) null else 2_000L,
    )

    private fun subagent(id: String, child: String? = null) = SubagentActivity(
        id = id,
        goal = "une tâche",
        status = if (child == null) SubagentStatus.DISPATCHED else SubagentStatus.COMPLETED,
        childSessionId = child,
    )

    private fun messagesWith(
        tools: List<ToolActivity> = emptyList(),
        subagents: List<SubagentActivity> = emptyList(),
        usage: TokenUsage? = null,
        seconds: List<Double> = emptyList(),
        answersWithoutDuration: Int = 0,
    ): List<ChatMessage> {
        // Les réponses **mesurées** ; à défaut d'en demander aucune, une seule réponse — celle qui
        // porte les outils, les sous-agents et les jetons —, sans durée mesurée.
        val durations: List<Double?> = when {
            seconds.isNotEmpty() -> seconds
            answersWithoutDuration == 0 -> listOf(null)
            else -> emptyList()
        }
        val listed = durations.mapIndexed { index, value ->
            ChatMessage(
                id = "a$index",
                role = Role.ASSISTANT,
                text = "réponse $index",
                createdAt = 1_000L + index,
                tools = if (index == 0) tools else emptyList(),
                subagents = if (index == 0) subagents else emptyList(),
                usage = if (index == 0) usage else null,
                metrics = value?.let { TurnMetrics(startedAt = 1_000L, completedAt = 1_000L + (it * 1000).toLong()) },
            )
        }
        val blank = (0 until answersWithoutDuration).map { index ->
            ChatMessage(id = "n$index", role = Role.ASSISTANT, text = "sans durée", createdAt = 2_000L + index)
        }
        return listOf(ChatMessage(id = "u", role = Role.USER, text = "une question", createdAt = 0L)) + listed + blank
    }

    private fun summary(
        model: String? = null,
        actual: Double? = null,
        estimated: Double? = null,
        input: Int? = null,
        output: Int? = null,
        toolCalls: Int? = null,
    ) = SessionSummary(
        id = "sess",
        model = model,
        toolCallCount = toolCalls,
        inputTokens = input,
        outputTokens = output,
        actualCostUsd = actual,
        estimatedCostUsd = estimated,
    )

    private companion object {
        /** Le prix publié par le serveur pour `minimax/minimax-m2.7`, mot pour mot. */
        val KILO_PRICE = ServerPrice(input = "\$0.30", output = "\$1.20", cache = "\$0.006")

        const val SOURCE = "http://10.0.2.2:8890 · api/model/options?profile=default"

        /** Le 28 octobre 2026, 17 h 31, heure locale — l'instant du relevé des prix. */
        val READ_AT: Long = LocalDateTime.of(2026, 10, 28, 17, 31)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        /** Un montant en dollars, tel que le relevé l'écrit : « 0,42 $ » ou « $0.42 ». */
        val MONEY = Regex("""[\d]+\s*[.,]\s*[\d]*\s*\$|\$\s*[\d]+""")
    }
}
