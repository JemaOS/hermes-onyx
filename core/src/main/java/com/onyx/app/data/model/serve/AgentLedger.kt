package com.onyx.app.data.model.serve

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.data.net.SessionSummary
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.ServerPrice
import java.util.Locale
import kotlin.math.roundToLong

/**
 * **Le relevé d'agent** — ce que l'agent a fait, avec quoi, pendant combien de temps, et ce que
 * cela a coûté.
 *
 * Trois principes, et c'est tout le fichier :
 *
 * 1. **Aucun montant n'est calculé sans un prix venu du serveur.** Le coût se lit d'abord dans le
 *    **registre des sessions** (`GET api/sessions`, `actual_cost_usd` / `estimated_cost_usd`) ;
 *    à défaut, il peut être **estimé** à partir du **prix unitaire que le serveur publie**
 *    (`GET api/model/options` → `pricing`) ; s'il n'y a ni l'un ni l'autre, le relevé écrit
 *    **« non fourni »** et **n'écrit aucun chiffre**. Un `0,00 $` de remplissage serait un
 *    mensonge : il a exactement la même forme qu'un modèle réellement gratuit.
 * 2. **Une donnée absente se dit.** Chaque rangée sait d'où elle vient, et chaque trou a sa phrase :
 *    un tour sans durée mesurée, un transport qui ne remonte pas les jetons, un sous-agent lancé à
 *    l'aveugle, un registre qui n'a pas répondu. Jamais une case vide, jamais un zéro trompeur.
 * 3. **C'est un écran de données.** [AgentLedger.sections] rend le relevé **déjà mis en forme** —
 *    libellés traduits et unités écrites (« 18,4k jetons », « 2 min 12 », « 0,42 $ ») — si bien que
 *    le nombre affiché et le nombre vérifié par les tests sont **le même**.
 *
 * Les chiffres du fil viennent des messages eux-mêmes ([ChatMessage.usage], `.metrics`, `.tools`,
 * `.subagents`), ceux du serveur du [SessionSummary] apparié à la conversation.
 */

/** L'échelle d'un relevé : une conversation, ou tout un bot. */
enum class LedgerScope {
    CONVERSATION,
    BOT,
}

/**
 * D'où viennent les comptes d'une rangée.
 *
 * Le relevé le **dit** : un compte tenu par le serveur et un compte tenu par ce téléphone ne se
 * lisent pas de la même façon (le second s'arrête à ce que l'appareil a vu).
 */
enum class LedgerSource {
    /** Le registre des sessions du serveur (`GET api/sessions`). */
    SERVER_REGISTRY,

    /** Le fil gardé sur ce téléphone. */
    THIS_PHONE,
}

/**
 * Pourquoi le relevé n'écrit aucun montant.
 *
 * [label] est la **clé anglaise** du dictionnaire : le relevé la traduit en composant la phrase.
 * `null` quand un montant a bel et bien été fourni.
 */
enum class LedgerNoPrice(val label: String?) {
    NONE(null),

    /**
     * Le serveur n'a **pas** de prix pour ce modèle : un serveur local (le fournisseur local d'un
     * profil, par exemple) ne publie aucune ligne de tarif. C'est le cas normal, pas une panne.
     */
    NO_PRICE_PUBLISHED("the server publishes no price for this model"),

    /** La table de prix du serveur n'a pas répondu : on ne sait pas, donc on n'invente pas. */
    PRICE_TABLE_ABSENT("the server's price table did not answer"),

    /** Le modèle n'est pas dans la table du serveur : elle existe, mais il n'y est pas. */
    MODEL_UNKNOWN("the server's price table does not list this model"),

    /** Aucun jeton compté : il n'y a rien à facturer, et un montant serait un chiffre en l'air. */
    NO_TOKENS("no tokens were counted, so no cost could be estimated"),
}

/** D'où vient le montant affiché. */
enum class LedgerMoneySource {
    /** Le serveur a donné le coût lui-même (le registre des sessions). */
    SERVER_STATED,

    /** Le montant est **estimé** au prix unitaire publié par le serveur. */
    SERVER_PRICE,

    /** Aucun montant : ni coût ni prix. Rien n'est affiché. */
    NOT_PROVIDED,
}

/**
 * Ce que le relevé écrit pour l'argent.
 *
 * [usd] est `null` **exactement** quand il n'y a pas de prix : c'est cette nullité qui produit
 * « non fourni ». Un montant de zéro n'existe que si le serveur l'a dit (modèle déclaré gratuit,
 * ou coût réel non nul).
 */
data class LedgerMoney(
    val usd: Double? = null,
    val source: LedgerMoneySource = LedgerMoneySource.NOT_PROVIDED,
    /** Le fournisseur a **facturé** ce montant, plutôt que le serveur ne l'estime. */
    val billed: Boolean = false,
    /** La ligne de prix publiée par le serveur (« 0,30 $ / 1,20 $ »), pour la provenance. */
    val priceLine: String? = null,
    /** Nombre de conversations du cumul restées **sans** prix : le total est alors incomplet. */
    val withoutPrice: Int = 0,
    /** Pourquoi il n'y a pas de montant. */
    val why: LedgerNoPrice = LedgerNoPrice.NONE,
) {
    val hasAmount: Boolean get() = usd != null
}

/** Une rangée du relevé : un libellé, une valeur **déjà mise en forme**, et sa note. */
data class LedgerRow(
    /** La clé anglaise du libellé (« Working time ») : l'écran la traduit. */
    val label: String,
    /** La valeur telle qu'elle s'affiche — nombres et unités compris (« 2 min 12 »). */
    val value: String,
    /**
     * Ce qui explique la valeur : sa provenance, ou **ce qui manque**. **Déjà composée et
     * traduite** (elle porte des nombres mis en forme) : l'écran la rend verbatim.
     */
    val note: String? = null,
    /**
     * Vrai quand la valeur **dit une absence** (« non fourni », « non consigné ») : l'écran la
     * dessine en teinte reculée, sans couleur d'alerte — ce n'est pas une panne, c'est un fait.
     */
    val absent: Boolean = false,
    /** Vrai quand la valeur est un identifiant ou un nombre : l'écran la rend en chasse fixe. */
    val monospace: Boolean = true,
)

/** Un groupe de rangées, avec son en-tête (la clé anglaise de la section). */
data class LedgerSection(val header: String, val rows: List<LedgerRow>)

/**
 * Le relevé lui-même : les chiffres bruts, puis [sections] pour l'écran.
 *
 * Les champs sont bruts **exprès** : les tests interrogent les deux — la donnée, et la rangée
 * qu'elle produit.
 */
data class AgentLedger(
    val scope: LedgerScope,
    /** Le sujet : le titre de la conversation, ou le libellé du bot. */
    val subject: String,
    /** Le nombre de conversations couvertes : 1 pour une conversation, N pour un cumul. */
    val conversations: Int = 1,
    /** Le modèle du fil, ou `null` quand rien ne le consigne. */
    val model: String? = null,
    /** D'où viennent les comptes de jetons. */
    val counts: LedgerSource = LedgerSource.THIS_PHONE,
    /** Faux quand le registre du serveur n'a pas répondu (il a été tenté, et il a manqué). */
    val registryAnswered: Boolean = false,
    /** Le registre a été tenté (une conversation sans session serveur ne le tente pas). */
    val registryAttempted: Boolean = false,
    /** Le temps de travail mesuré, en secondes ; `null` quand **aucune** durée n'a été mesurée. */
    val workSeconds: Double? = null,
    /** Les réponses dont la durée a réellement été mesurée. */
    val turnsMeasured: Int = 0,
    /** Les réponses reçues dont la durée **n'a pas** été mesurée : elles manquent au total. */
    val turnsUnmeasured: Int = 0,
    val toolsTotal: Int = 0,
    val toolsSucceeded: Int = 0,
    val toolsFailed: Int = 0,
    val toolsRunning: Int = 0,
    /** Le total d'appels que le registre du serveur annonce, quand il diffère du fil. */
    val registryToolCalls: Int? = null,
    val apiCalls: Int? = null,
    val subagentsLaunched: Int = 0,
    /** Ceux dont le serveur a suivi l'état (un enfant identifié, ou une fin annoncée). */
    val subagentsTracked: Int = 0,
    val tokensInput: Long? = null,
    val tokensOutput: Long? = null,
    val tokensCacheRead: Long? = null,
    val money: LedgerMoney = LedgerMoney(),
    /** L'adresse et la route des prix (« http://…:9119 · api/model/options?profile=default »). */
    val priceSource: String? = null,
    /** Quand les prix ont été relevés (millisecondes), `null` s'ils ne l'ont pas été. */
    val priceReadAt: Long? = null,
    /** Les conversations du cumul sans prix. */
    val withoutPrice: Int = 0,
) {

    /** Vrai quand il n'y a rien du tout à montrer : ni travail, ni jetons, ni appels. */
    val isEmpty: Boolean
        get() = turnsMeasured == 0 && turnsUnmeasured == 0 && toolsTotal == 0 &&
            subagentsLaunched == 0 && tokensInput == null && tokensOutput == null

    /**
     * Le relevé tel qu'il s'affiche, **section par section**.
     *
     * Chaque rangée est une donnée présente, ou une absence **nommée**. Les phrases sont
     * **composées ici** : chaque morceau passe au dictionnaire **avant** d'être joint à ses
     * nombres (une phrase déjà cousue ne se traduit plus).
     *
     * [now] ne sert qu'à dater la provenance quand la lecture n'en porte pas : rien ici ne dépend de
     * l'heure de la composition, donc le relevé ne change pas d'une ouverture à l'autre.
     */
    fun sections(locale: Locale = OnyxLanguage.preferred, now: Long = 0L): List<LedgerSection> = listOf(
        modelSection(locale),
        workSection(locale),
        toolsSection(locale),
        subagentsSection(locale),
        tokensSection(locale),
        costSection(locale, now),
    )

    // ---------------------------------------------------------------------------------------------
    // Les sections
    // ---------------------------------------------------------------------------------------------

    private fun modelSection(locale: Locale): LedgerSection {
        val rows = mutableListOf<LedgerRow>()
        rows += if (model.isNullOrBlank()) {
            LedgerRow("Model", phrase(locale, KEY_NOT_RECORDED), absent = true, monospace = false)
        } else {
            LedgerRow("Model", model)
        }
        if (registryAttempted) {
            rows += if (registryAnswered) {
                LedgerRow("Origin", phrase(locale, KEY_SERVER_REGISTRY), note = phrase(locale, KEY_COUNTS_FROM_SERVER), monospace = false)
            } else {
                LedgerRow("Origin", phrase(locale, KEY_THIS_PHONE), note = phrase(locale, KEY_REGISTRY_SILENT), monospace = false, absent = true)
            }
        } else {
            rows += LedgerRow("Origin", phrase(locale, KEY_THIS_PHONE), note = phrase(locale, KEY_NO_SERVER_SESSION), monospace = false)
        }
        apiCalls?.let { rows += LedgerRow("API calls", LedgerFormat.count(it.toLong(), locale)) }
        return LedgerSection("Model", rows)
    }

    private fun workSection(locale: Locale): LedgerSection {
        val rows = mutableListOf<LedgerRow>()
        val measured = workSeconds
        rows += if (measured == null) {
            LedgerRow(
                "Working time",
                phrase(locale, KEY_NOT_MEASURED),
                note = phrase(locale, KEY_NO_DURATION_PHRASE),
                absent = true,
                monospace = false,
            )
        } else {
            LedgerRow(
                "Working time",
                LedgerFormat.duration(measured, locale) ?: phrase(locale, KEY_NOT_MEASURED),
                note = plural(locale, KEY_MEASURED_ON_ONE, KEY_MEASURED_ON, turnsMeasured),
            )
        }
        if (turnsUnmeasured > 0) {
            rows += LedgerRow(
                "Answers without a duration",
                LedgerFormat.count(turnsUnmeasured.toLong(), locale),
                note = plural(locale, KEY_OUTSIDE_TOTAL_ONE, KEY_OUTSIDE_TOTAL, turnsUnmeasured),
                absent = true,
            )
        }
        return LedgerSection("Work", rows)
    }

    private fun toolsSection(locale: Locale): LedgerSection {
        val rows = mutableListOf<LedgerRow>(
            LedgerRow("Tool calls", LedgerFormat.count(toolsTotal.toLong(), locale), note = phrase(locale, KEY_FROM_THREAD)),
            LedgerRow("Calls that succeeded", LedgerFormat.count(toolsSucceeded.toLong(), locale)),
            LedgerRow("Calls that failed", LedgerFormat.count(toolsFailed.toLong(), locale)),
        )
        rows += if (toolsRunning > 0) {
            LedgerRow(
                "Still running",
                LedgerFormat.count(toolsRunning.toLong(), locale),
                note = plural(locale, KEY_STILL_RUNNING_ONE, KEY_STILL_RUNNING, toolsRunning),
            )
        } else {
            LedgerRow("Still running", LedgerFormat.count(0, locale))
        }
        registryToolCalls?.takeIf { it != toolsTotal }?.let { announced ->
            rows += LedgerRow(
                "Tool calls (server)",
                LedgerFormat.count(announced.toLong(), locale),
                note = phrase(locale, KEY_REGISTRY_OTHER_TOTAL),
                absent = true,
            )
        }
        return LedgerSection("Tools", rows)
    }

    private fun subagentsSection(locale: Locale): LedgerSection {
        val rows = mutableListOf<LedgerRow>(
            LedgerRow("Launched", LedgerFormat.count(subagentsLaunched.toLong(), locale)),
        )
        if (subagentsLaunched == 0) {
            // Le zéro est **vrai** et il est nommé : la rangée dit ce qu'elle compte, donc elle
            // n'est pas un zéro trompeur — c'est le seul cas où un zéro se lit sans réserve.
            return LedgerSection("Subagents", rows)
        }
        val blind = (subagentsLaunched - subagentsTracked).coerceAtLeast(0)
        rows += if (blind > 0) {
            LedgerRow(
                "Tracked by the server",
                LedgerFormat.count(subagentsTracked.toLong(), locale),
                note = plural(locale, KEY_BLIND_ONE, KEY_BLIND, blind),
                absent = true,
            )
        } else {
            LedgerRow(
                "Tracked by the server",
                LedgerFormat.count(subagentsTracked.toLong(), locale),
                note = phrase(locale, KEY_ALL_ANNOUNCED),
            )
        }
        return LedgerSection("Subagents", rows)
    }

    private fun tokensSection(locale: Locale): LedgerSection {
        val source = phrase(locale, if (counts == LedgerSource.SERVER_REGISTRY) KEY_COUNTS_FROM_SERVER else KEY_COUNTS_FROM_PHONE)
        val rows = mutableListOf<LedgerRow>()
        if (tokensInput == null && tokensOutput == null) {
            rows += LedgerRow(
                "Input",
                phrase(locale, KEY_NOT_REPORTED),
                note = phrase(locale, KEY_NO_TOKENS_PHRASE),
                absent = true,
                monospace = false,
            )
        } else {
            rows += LedgerRow("Input", LedgerFormat.tokens(tokensInput ?: 0L, locale), note = source)
            rows += LedgerRow("Output", LedgerFormat.tokens(tokensOutput ?: 0L, locale))
            rows += LedgerRow("Total", LedgerFormat.tokens((tokensInput ?: 0L) + (tokensOutput ?: 0L), locale))
            tokensCacheRead?.takeIf { it > 0 }?.let { cache ->
                rows += LedgerRow("Cache read", LedgerFormat.tokens(cache, locale))
            }
        }
        return LedgerSection("Tokens", rows)
    }

    private fun costSection(locale: Locale, now: Long): LedgerSection {
        val rows = mutableListOf<LedgerRow>()
        if (money.hasAmount) {
            rows += LedgerRow(
                label = if (money.billed) "Actual" else "Estimated",
                value = LedgerFormat.amount(money.usd, locale) ?: phrase(locale, KEY_NOT_PROVIDED),
                note = moneyProvenance(locale, now),
            )
            money.priceLine?.let { line ->
                rows += LedgerRow("Price", line, note = phrase(locale, KEY_PRICE_LINE_FROM_SERVER))
            }
        } else {
            rows += LedgerRow(
                label = "Price",
                value = phrase(locale, KEY_NOT_PROVIDED),
                note = money.why.label?.let { OnyxLocalization.translate(it, locale) }
                    ?: phrase(locale, KEY_NO_PRICE_AT_ALL),
                absent = true,
                monospace = false,
            )
        }
        if (money.withoutPrice > 0) {
            rows += LedgerRow(
                "Conversations without a price",
                LedgerFormat.count(money.withoutPrice.toLong(), locale),
                note = phrase(locale, KEY_MISSING_FROM_TOTAL),
                absent = true,
            )
        }
        return LedgerSection("Cost", rows)
    }

    /**
     * « coût du registre du serveur » ou « estimation au prix publié par le serveur ·
     * http://…:9119 · relevé le 28 oct. 2026 à 17:31 ».
     *
     * Un coût **donné** par le serveur ne porte pas la date de la table de prix : ce n'est pas
     * elle qui l'a produit, et l'écrire ferait croire à une estimation.
     */
    private fun moneyProvenance(locale: Locale, now: Long): String {
        return when (money.source) {
            LedgerMoneySource.NOT_PROVIDED -> ""

            LedgerMoneySource.SERVER_STATED -> listOfNotNull(
                phrase(locale, KEY_COST_FROM_SERVER),
                priceSource?.takeIf { it.isNotBlank() },
            ).joinToString(SEPARATOR)

            LedgerMoneySource.SERVER_PRICE -> listOfNotNull(
                phrase(locale, KEY_PRICE_FROM_SERVER),
                priceSource?.takeIf { it.isNotBlank() },
                (priceReadAt ?: now.takeIf { it > 0 })?.let {
                    OnyxLocalization.format(KEY_READ_ON, LedgerFormat.moment(it, locale), locale = locale)
                },
            ).joinToString(SEPARATOR)
        }
    }

    /** Un morceau de phrase du dictionnaire, sans nombre à insérer. */
    private fun phrase(locale: Locale, key: String): String = OnyxLocalization.translate(key, locale)

    /**
     * La même, avec **un** nombre : le singulier et le pluriel sont deux clés distinctes.
     *
     * Une phrase cousue ne se traduit plus : c'est le morceau qui est traduit, **puis** rempli.
     */
    private fun plural(locale: Locale, one: String, many: String, count: Int): String =
        if (count == 1) {
            OnyxLocalization.translate(one, locale)
        } else {
            OnyxLocalization.format(many, count, locale = locale)
        }

    companion object {

        /** Le séparateur des morceaux d'une note : « · », celui de la maison. */
        const val SEPARATOR = " · "

        // ——— Les clés anglaises, tenues ici pour que la rangée, le dictionnaire et le test
        // ——— parlent du même texte.

        const val KEY_NOT_RECORDED = "not recorded by this thread"
        const val KEY_SERVER_REGISTRY = "the server's registry"
        const val KEY_THIS_PHONE = "this phone"
        const val KEY_COUNTS_FROM_SERVER = "counted by the server's session registry"
        const val KEY_COUNTS_FROM_PHONE = "counted by this phone, from the thread it kept"
        const val KEY_REGISTRY_SILENT = "the server's registry did not answer for this session"
        const val KEY_NO_SERVER_SESSION = "this conversation has no session on the server"
        const val KEY_NOT_MEASURED = "not measured"
        const val KEY_NOT_REPORTED = "not reported"
        const val KEY_NOT_PROVIDED = "not provided"
        const val KEY_COST_FROM_SERVER = "cost from the server's registry"
        const val KEY_PRICE_FROM_SERVER = "estimated at the price the server publishes"
        const val KEY_PRICE_LINE_FROM_SERVER = "the price line the server publishes"
        const val KEY_NO_PRICE_AT_ALL = "neither the server's registry nor its price table gave a price"
        const val KEY_NO_DURATION_PHRASE = "no answer of this thread carried a measured duration"
        const val KEY_NO_TOKENS_PHRASE = "this transport did not report any tokens for this thread"
        const val KEY_FROM_THREAD = "counted one by one in the thread"
        const val KEY_ALL_ANNOUNCED = "every launch was announced by the server"
        const val KEY_MISSING_FROM_TOTAL = "they are missing from the total above, which stays incomplete"
        const val KEY_REGISTRY_OTHER_TOTAL = "the server's registry counts a different total for the same session"
        const val KEY_MEASURED_ON_ONE = "measured on one answer"
        const val KEY_MEASURED_ON = "measured on %1\$d answers"
        const val KEY_OUTSIDE_TOTAL_ONE = "one answer stays outside the total above"
        const val KEY_OUTSIDE_TOTAL = "%1\$d answers stay outside the total above"
        const val KEY_STILL_RUNNING_ONE = "one call had not finished when this ledger was written"
        const val KEY_STILL_RUNNING = "%1\$d calls had not finished when this ledger was written"
        const val KEY_BLIND_ONE = "one task was launched blind: this transport says nothing about its state"
        const val KEY_BLIND = "%1\$d were launched blind: this transport says nothing about their state"
        const val KEY_READ_ON = "read on %1\$s"

        // ——— Les deux routes du serveur, et ce que l'écran dit de chacune : deux lectures, deux
        // ——— issues. Elles vivent ici pour que l'écran, le dictionnaire et le test parlent du même
        // ——— texte.
        const val KEY_READING_REGISTRY = "reading the server's session registry…"
        const val KEY_READING_PRICES = "reading the server's price table…"
        const val KEY_REGISTRY_READ = "the server's session registry answered"
        const val KEY_PRICES_READ = "the server's price table answered"
        const val KEY_REGISTRY_MISSING =
            "the session registry could not be read (%1\$s) — the counts below are this phone's"
        const val KEY_PRICES_MISSING =
            "the price table could not be read (%1\$s) — no cost can be estimated without it"
        const val KEY_NO_DASHBOARD = "the Dashboard's credentials are missing on this phone"
        const val KEY_BOTS_WITHOUT_CHATS = "%1\$d bots have no conversation on this phone yet." 

        /**
         * Le relevé d'**une conversation**.
         *
         * [messages] est le fil gardé par ce téléphone ; [registry] la ligne du registre des
         * sessions qui lui correspond (`GET api/sessions`), ou `null` quand elle n'existe pas ou
         * que la route n'a pas répondu ; [price] le tarif que le serveur publie pour le modèle.
         *
         * Les comptes du fil (temps, outils, sous-agents) viennent **toujours** des messages :
         * ce sont les seuls à distinguer un appel réussi d'un appel échoué, ce que le registre ne
         * fait pas. Les jetons et le coût viennent du registre **quand il a répondu**, sinon du fil.
         */
        fun of(
            subject: String,
            messages: List<ChatMessage>,
            registry: SessionSummary? = null,
            registryAttempted: Boolean = false,
            model: String? = null,
            price: ServerPrice? = null,
            priceSource: String? = null,
            priceReadAt: Long? = null,
        ): AgentLedger {
            val tools = messages.flatMap { it.tools }
            val subagents = messages.flatMap { it.subagents }
            val measured = messages.mapNotNull { it.metrics?.total }
            val answers = messages.count { it.role == Role.ASSISTANT }
            val localInput = messages.mapNotNull { it.usage }.sumOf { it.input.toLong() }.takeIf { it > 0 }
            val localOutput = messages.mapNotNull { it.usage }.sumOf { it.output.toLong() }.takeIf { it > 0 }
            val localCache = messages.mapNotNull { it.usage?.cached }.sumOf { it.toLong() }.takeIf { it > 0 }

            val registryTokens = registry?.takeIf { (it.inputTokens ?: 0) > 0 || (it.outputTokens ?: 0) > 0 }
            val input = registryTokens?.inputTokens?.toLong() ?: localInput
            val output = registryTokens?.outputTokens?.toLong() ?: localOutput
            val cache = registryTokens?.cacheReadTokens?.toLong() ?: localCache

            val resolvedModel = registry?.model?.takeIf { it.isNotBlank() }
                ?: model?.takeIf { it.isNotBlank() }

            return AgentLedger(
                scope = LedgerScope.CONVERSATION,
                subject = subject,
                conversations = 1,
                model = resolvedModel,
                counts = if (registryTokens != null) LedgerSource.SERVER_REGISTRY else LedgerSource.THIS_PHONE,
                registryAnswered = registry != null,
                registryAttempted = registryAttempted,
                workSeconds = measured.takeIf { it.isNotEmpty() }?.sum(),
                turnsMeasured = measured.size,
                turnsUnmeasured = (answers - measured.size).coerceAtLeast(0),
                toolsTotal = tools.size,
                toolsSucceeded = tools.count { it.status == ToolStatus.COMPLETED },
                toolsFailed = tools.count { it.status == ToolStatus.FAILED },
                toolsRunning = tools.count { it.status == ToolStatus.RUNNING },
                registryToolCalls = registry?.toolCallCount,
                apiCalls = registry?.apiCallCount,
                subagentsLaunched = subagents.size,
                subagentsTracked = subagents.count {
                    it.hasTranscript || it.status == SubagentStatus.COMPLETED || it.status == SubagentStatus.FAILED
                },
                tokensInput = input,
                tokensOutput = output,
                tokensCacheRead = cache,
                money = moneyOf(registry, price, input, output),
                priceSource = priceSource,
                priceReadAt = priceReadAt,
            )
        }

        /**
         * Le relevé d'un fil déjà enregistré (sans passer par l'écran).
         *
         * [registryAttempted] dit que la **route** a été lue ; le relevé n'en fait un essai pour
         * **cette** conversation que si elle porte un identifiant de session à y chercher — une
         * conversation sans session n'a pas à se plaindre d'un registre qui n'a rien dit d'elle.
         */
        fun of(
            conversation: Conversation,
            registry: SessionSummary? = null,
            registryAttempted: Boolean = false,
            price: ServerPrice? = null,
            priceSource: String? = null,
            priceReadAt: Long? = null,
        ): AgentLedger = of(
            subject = conversation.title,
            messages = conversation.messages,
            registry = registry,
            registryAttempted = registryAttempted && !conversation.serverSessionId.isNullOrBlank(),
            price = price,
            priceSource = priceSource,
            priceReadAt = priceReadAt,
        )

        /**
         * Le **cumul d'un bot** : le même relevé à l'échelle de tout ce que ce bot a fait sur cet
         * appareil.
         *
         * Le total d'argent est **le plus honnête possible et pas plus** : les conversations qui ont
         * un montant s'additionnent ; celles qui n'ont **aucun** prix sont comptées et **dites**
         * (« N conversations sans prix »). Si aucune n'en a, le cumul écrit « non fourni » — il
         * n'invente pas un total partiel qui se lirait comme un total.
         *
         * La provenance des prix est celle de la **dernière** lecture : c'est la date à laquelle la
         * table a été relevée, et elle est écrite sur la rangée.
         */
        fun cumulative(subject: String, ledgers: List<AgentLedger>): AgentLedger {
            if (ledgers.isEmpty()) {
                return AgentLedger(scope = LedgerScope.BOT, subject = subject, conversations = 0)
            }
            val priced = ledgers.filter { it.money.hasAmount }
            val missing = ledgers.size - priced.size
            val models = ledgers.mapNotNull { it.model?.takeIf { model -> model.isNotBlank() } }.distinct()
            val money = when {
                priced.isEmpty() -> LedgerMoney(
                    why = ledgers.mapNotNull { it.money.why.takeIf { reason -> reason != LedgerNoPrice.NONE } }
                        .firstOrNull() ?: LedgerNoPrice.NO_PRICE_PUBLISHED,
                    withoutPrice = ledgers.size,
                )

                else -> LedgerMoney(
                    usd = priced.sumOf { it.money.usd ?: 0.0 },
                    source = if (priced.all { it.money.source == LedgerMoneySource.SERVER_STATED }) {
                        LedgerMoneySource.SERVER_STATED
                    } else {
                        LedgerMoneySource.SERVER_PRICE
                    },
                    billed = priced.all { it.money.billed },
                    withoutPrice = missing,
                )
            }
            val measured = ledgers.sumOf { it.turnsMeasured }
            val unmeasured = ledgers.sumOf { it.turnsUnmeasured }
            return AgentLedger(
                scope = LedgerScope.BOT,
                subject = subject,
                conversations = ledgers.size,
                model = models.singleOrNull(),
                counts = if (ledgers.any { it.counts == LedgerSource.SERVER_REGISTRY }) {
                    LedgerSource.SERVER_REGISTRY
                } else {
                    LedgerSource.THIS_PHONE
                },
                registryAnswered = ledgers.any { it.registryAnswered },
                registryAttempted = ledgers.any { it.registryAttempted },
                workSeconds = ledgers.mapNotNull { it.workSeconds }.takeIf { it.isNotEmpty() }?.sum(),
                turnsMeasured = measured,
                turnsUnmeasured = unmeasured,
                toolsTotal = ledgers.sumOf { it.toolsTotal },
                toolsSucceeded = ledgers.sumOf { it.toolsSucceeded },
                toolsFailed = ledgers.sumOf { it.toolsFailed },
                toolsRunning = ledgers.sumOf { it.toolsRunning },
                apiCalls = ledgers.mapNotNull { it.apiCalls }.takeIf { it.isNotEmpty() }?.sum(),
                subagentsLaunched = ledgers.sumOf { it.subagentsLaunched },
                subagentsTracked = ledgers.sumOf { it.subagentsTracked },
                tokensInput = ledgers.mapNotNull { it.tokensInput }.takeIf { it.isNotEmpty() }?.sum(),
                tokensOutput = ledgers.mapNotNull { it.tokensOutput }.takeIf { it.isNotEmpty() }?.sum(),
                tokensCacheRead = ledgers.mapNotNull { it.tokensCacheRead }.takeIf { it.isNotEmpty() }?.sum(),
                money = money,
                priceSource = ledgers.mapNotNull { it.priceSource }.firstOrNull(),
                priceReadAt = ledgers.mapNotNull { it.priceReadAt }.minOrNull(),
                withoutPrice = missing,
            )
        }

        /**
         * La règle d'argent, en un endroit.
         *
         * 1. Le **registre** a un coût réel (> 0) → c'est lui, facturé.
         * 2. Le registre a une estimation (> 0) → c'est elle.
         * 3. Sinon, un **prix unitaire publié par le serveur** et des jetons → le montant est
         *    **estimé** à partir de ce prix, et la rangée le dit.
         * 4. Rien de tout cela → **aucun montant**, et la raison est nommée.
         *
         * Un coût réel ou estimé de **zéro** dans le registre n'est pas retenu : le serveur écrit
         * zéro quand il n'a rien su (voir `UsageDay.cost`), et l'afficher comme un prix serait
         * exactement le zéro trompeur que la règle interdit.
         */
        fun moneyOf(
            registry: SessionSummary?,
            price: ServerPrice?,
            input: Long?,
            output: Long?,
        ): LedgerMoney {
            val actual = registry?.actualCostUsd ?: 0.0
            if (actual > 0.0) {
                return LedgerMoney(usd = actual, source = LedgerMoneySource.SERVER_STATED, billed = true)
            }
            val estimated = registry?.estimatedCostUsd ?: 0.0
            if (estimated > 0.0) {
                return LedgerMoney(usd = estimated, source = LedgerMoneySource.SERVER_STATED)
            }
            if (price == null || !price.hasValue) {
                return LedgerMoney(why = LedgerNoPrice.NO_PRICE_PUBLISHED)
            }
            if (price.free) {
                // Le serveur **déclare** le modèle gratuit : zéro est alors une donnée, pas un vide.
                return LedgerMoney(usd = 0.0, source = LedgerMoneySource.SERVER_PRICE, priceLine = price.line())
            }
            val unitIn = LedgerFormat.moneyOf(price.input)
            val unitOut = LedgerFormat.moneyOf(price.output)
            if (unitIn == null && unitOut == null) {
                // La ligne existe mais ne porte aucun montant lisible : elle n'autorise aucun calcul.
                return LedgerMoney(why = LedgerNoPrice.NO_PRICE_PUBLISHED, priceLine = price.line())
            }
            val inTokens = input ?: 0L
            val outTokens = output ?: 0L
            if (inTokens + outTokens == 0L) {
                return LedgerMoney(why = LedgerNoPrice.NO_TOKENS, priceLine = price.line())
            }
            val amount = inTokens / 1_000_000.0 * (unitIn ?: 0.0) + outTokens / 1_000_000.0 * (unitOut ?: 0.0)
            return LedgerMoney(
                usd = amount,
                source = LedgerMoneySource.SERVER_PRICE,
                priceLine = price.line(),
            )
        }
    }
}

/**
 * Les formats de chiffres du relevé — un seul endroit, donc jamais deux écritures d'un même nombre.
 *
 * Le français prend la virgule décimale et le symbole **après** le montant (« 0,42 $ »), comme la
 * feuille de modèle le fait déjà pour les prix du serveur.
 */
object LedgerFormat {

    /**
     * « 18,4k jetons », « 847 jetons », « 1,2M jetons » — l'unité est **écrite**, comme demandé :
     * un chiffre nu ne dit pas s'il s'agit de jetons, de secondes ou d'appels.
     */
    fun tokens(value: Long, locale: Locale = OnyxLanguage.preferred): String {
        val french = OnyxLocalization.isFrench(locale)
        val unit = if (value == 1L) {
            if (french) "jeton" else "token"
        } else {
            if (french) "jetons" else "tokens"
        }
        return "${compact(value, locale)} $unit"
    }

    /** Un compte nu (« 3 »), pour les appels d'outils, les tours et les sous-agents. */
    fun count(value: Long, locale: Locale = OnyxLanguage.preferred): String = compact(value, locale)

    /**
     * « 2 min 12 », « 45 s », « 1 h 04 » — et **« < 1 s »** sous la seconde, parce qu'une durée de
     * 0,4 s écrite « 0 s » se lirait comme « rien ne s'est passé ».
     */
    fun duration(seconds: Double?, locale: Locale = OnyxLanguage.preferred): String? {
        if (seconds == null || !seconds.isFinite() || seconds < 0) return null
        if (seconds < 1.0) return "< 1 s"
        val whole = seconds.roundToLong()
        return when {
            whole < 60 -> "$whole s"
            whole < 3600 -> "${whole / 60} min ${(whole % 60).toString().padStart(2, '0')}"
            else -> "${whole / 3600} h ${((whole % 3600) / 60).toString().padStart(2, '0')}"
        }
    }

    /**
     * « 0,42 $ » en français, « $0.42 » en anglais.
     *
     * Sous le centime, « < 0,01 $ » : l'écrire « 0,00 $ » ferait passer une dépense pour rien.
     */
    fun amount(usd: Double?, locale: Locale = OnyxLanguage.preferred): String? {
        if (usd == null || !usd.isFinite() || usd < 0) return null
        val french = OnyxLocalization.isFrench(locale)
        if (usd > 0.0 && usd < 0.01) return if (french) "< 0,01 $" else "< \$0.01"
        val cents = (usd * 100).roundToLong()
        val whole = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        return if (french) "$whole,$rest $" else "\$$whole.$rest"
    }

    /**
     * « 28 oct. 2026 à 17:31 » — le moment où la table de prix a été relevée.
     *
     * Le jour et le mois sont écrits **par table**, comme les dates de l'écran des aperçus : une
     * forme dépendante des données de locale de l'appareil ne donnerait pas exactement les langues
     * de l'app.
     */
    fun moment(millis: Long, locale: Locale = OnyxLanguage.preferred): String {
        val french = OnyxLocalization.isFrench(locale)
        val moment = java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        val months = if (french) MONTHS_FRENCH else MONTHS_ENGLISH
        val month = months.getOrNull(moment.monthValue - 1) ?: moment.monthValue.toString()
        val stamp = "${moment.dayOfMonth} $month ${moment.year}"
        val clock = "${moment.hour.toString().padStart(2, '0')}:${moment.minute.toString().padStart(2, '0')}"
        return if (french) "$stamp à $clock" else "$stamp at $clock"
    }

    /** 999 · 8,2k · 3,1M — la décimale inutile ne s'écrit pas (« 3k », pas « 3,0k »). */
    private fun compact(value: Long, locale: Locale): String {
        if (value < 1_000) return value.toString()
        val dot = if (OnyxLocalization.isFrench(locale)) "," else "."
        val (divisor, suffix) = if (value < 1_000_000) 1_000L to "k" else 1_000_000L to "M"
        val tenths = value * 10 / divisor
        return if (tenths % 10 == 0L) "${tenths / 10}$suffix" else "${tenths / 10}$dot${tenths % 10}$suffix"
    }

    /**
     * Le montant d'une chaîne publiée par le serveur (« $0.30 ») en dollars par million de jetons.
     *
     * `null` — et non zéro — quand ce n'est pas un montant : c'est ce `null` qui empêche le calcul,
     * et donc l'invention d'un coût. Le serveur garde la main sur sa ponctuation ; on ne lit que le
     * nombre.
     */
    fun moneyOf(published: String?): Double? {
        val text = published?.trim()?.removePrefix("$")?.trim() ?: return null
        if (text.isEmpty()) return null
        return runCatching { java.lang.Double.parseDouble(text) }.getOrNull()?.takeIf { it >= 0.0 }
    }

    private val MONTHS_ENGLISH = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private val MONTHS_FRENCH = listOf(
        "janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.",
    )
}
