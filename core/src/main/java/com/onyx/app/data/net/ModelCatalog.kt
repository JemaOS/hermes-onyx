package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.ui.design.ModelChoice
import com.onyx.app.ui.design.ModelOffer
import com.onyx.app.ui.design.ProfileState
import com.onyx.app.ui.design.ServerPrice
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Le catalogue des modèles : `GET /v1/models`, **lu au plus large**.
 *
 * Le serveur ne rend pas les mêmes champs d'une version à l'autre. Un endpoint nu ne donne que
 * l'identifiant ; un endpoint fournisseur donne aussi le fournisseur, la fenêtre de contexte et
 * parfois un tarif. Ce lecteur garde **tout ce qui est présent** et laisse le reste `null` — c'est
 * la règle du propriétaire : un prix n'est jamais deviné, il n'est écrit que lorsqu'il vient de la
 * source. Le sélecteur n'affiche alors aucun prix.
 *
 * Rien n'échoue ici : une réponse illisible rend une liste vide, et l'écran dit ce qui manque.
 */
object ModelCatalog {

    /** La route des modèles d'un endpoint OpenAI-compatible. */
    const val ENDPOINT_MODELS = "/v1/models"

    /** Le registre des profils du tableau de bord : `GET api/profiles`. */
    const val PROFILES_ROUTE = "api/profiles"

    /** Les modèles d'un profil : `GET api/model/options?profile=<nom>`. */
    const val MODEL_OPTIONS_ROUTE = "api/model/options"

    /**
     * **L'alias de la passerelle Hermes.**
     *
     * `GET /v1/models` d'un gateway ne liste que cette seule ligne, qui route vers le modèle réel.
     * Ce n'est **jamais** un nom de modèle : la feuille ne doit jamais l'écrire, sous peine de
     * cacher au propriétaire ses vrais modèles — le local du profil `default`, les distants du
     * profil `kilo`.
     */
    const val GATEWAY_ALIAS = "hermes-agent"

    /** Le nom du profil principal, quand le serveur ne le dit pas. */
    const val DEFAULT_PROFILE = "default"

    /** Vrai si un identifiant n'est que l'alias de la passerelle — donc jamais un nom de modèle. */
    fun isGatewayAlias(id: String): Boolean = id.trim().equals(GATEWAY_ALIAS, ignoreCase = true)

    /** Les modèles montrables : l'alias de la passerelle est écarté, le reste est gardé tel quel. */
    fun usable(models: List<ModelChoice>): List<ModelChoice> = models.filterNot { isGatewayAlias(it.id) }

    /** Le plafond de lecture du corps : un catalogue de modèles reste petit. */
    private const val MAX_BODY = 1_000_000L

    /** L'attente d'une liste de modèles par profil : un fournisseur distant peut être lent. */
    private const val MODEL_OPTIONS_TIMEOUT_SECONDS = 25L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Les modèles du serveur actif, ou une liste vide quand il ne répond rien d'exploitable.
     *
     * L'alias de passerelle ([GATEWAY_ALIAS]) en est **écarté** : ce n'est pas un nom de modèle. Le
     * profil effectif du serveur est posé sur chaque rangée, pour que la feuille sache à qui elle
     * parle.
     */
    suspend fun fetch(config: ServerConfig): List<ModelChoice> = withContext(Dispatchers.IO) {
        if (!config.isUsable) return@withContext emptyList()
        val request = Request.Builder()
            .url(config.apiBaseUrl + ENDPOINT_MODELS)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()
        val parsed = try {
            OnyxHttp.shortClient(12L).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = runCatching { response.peekBody(MAX_BODY).string() }.getOrDefault("")
                parse(body)
            }
        } catch (error: IOException) {
            emptyList()
        }
        val profile = config.effectiveProfile ?: DEFAULT_PROFILE
        usable(parsed).map { it.copy(profile = it.profile ?: profile) }
    }

    // -----------------------------------------------------------------------------------------
    // L'agrégat des profils — la feuille montre **tous** les profils, pas l'alias de la passerelle
    // -----------------------------------------------------------------------------------------

    /**
     * Les modèles de **tous** les profils Hermes, pour la feuille de modèle.
     *
     * Pourquoi cette source, et pas `GET /v1/models` :
     *
     * 1. La passerelle (`/v1/models`, et donc `/p/<profil>/v1/models`) ne liste que **son alias**
     *    `hermes-agent`, quel que soit le modèle réellement servi — c'est ce que le propriétaire
     *    voyait, une seule ligne qui ne dit rien.
     * 2. `GET api/profiles` du tableau de bord donne **chaque** profil avec le modèle et le
     *    fournisseur qu'il sert : le local `sage-a1.5` (`lmstudio`) du profil `default`, les
     *    distants `deepseek/deepseek-v4.1-flash` (`kilocode`) du profil `kilo`.
     * 3. `GET api/model/options?profile=<nom>` donne, **par profil**, la liste des modèles de ses
     *    fournisseurs connectés — donc le catalogue local du profil `default` *et* le catalogue
     *    distant du profil `kilo`.
     *
     * Un profil dont la liste ne répond pas n'interrompt rien : il sort en [ProfileState.PARTIAL]
     * (son modèle configuré est connu) ou [ProfileState.UNREACHABLE], et les autres restent entiers.
     */
    suspend fun fetchAll(config: ServerConfig): List<ProfileCatalog> = withContext(Dispatchers.IO) {
        if (!config.isUsable) return@withContext emptyList()

        val profiles = runCatching { fetchProfiles(config) }.getOrDefault(emptyList())

        // Le tableau de bord n'a pas répondu : on retombe sur le serveur actif, avec son seul
        // modèle — et **jamais** son alias de passerelle.
        if (profiles.isEmpty()) {
            val models = runCatching { fetch(config) }.getOrDefault(emptyList())
            val name = config.effectiveProfile ?: DEFAULT_PROFILE
            if (models.isEmpty() && !config.hasServeCredentials) return@withContext emptyList()
            val state = if (models.isEmpty()) ProfileState.UNREACHABLE else ProfileState.READY
            return@withContext listOf(ProfileCatalog(profile = name, models = models, state = state))
        }

        // Les listes de chaque profil, lues **en parallèle** : six profils ne se paient pas l'un
        // après l'autre. Une qui échoue (délai, 401, profil sans fournisseur connecté) rend `null`
        // pour **ce seul** profil.
        val options: Map<String, List<ProviderModels>?> = coroutineScope {
            profiles.associate { profile ->
                profile.name to async {
                    runCatching { fetchModelOptions(config, profile.name) }.getOrNull()
                }
            }.mapValues { (_, deferred) -> deferred.await() }
        }

        buildCatalog(
            profiles = profiles,
            options = options.mapNotNull { (name, rows) -> rows?.let { name to it } }.toMap(),
        )
    }

    /** `GET api/profiles` : le registre des profils du tableau de bord. Vide s'il n'est pas joignable. */
    suspend fun fetchProfiles(config: ServerConfig): List<ProfileRow> {
        if (!config.hasServeCredentials) return emptyList()
        val payload = ServeClient(config).request(PROFILES_ROUTE)
        return parseProfiles(payload.toString())
    }

    /**
     * `GET api/model/options?profile=<nom>` : les fournisseurs connectés d'un profil et leurs
     * modèles. `timeoutSeconds` borne l'attente — la liste peut interroger un fournisseur distant.
     */
    suspend fun fetchModelOptions(config: ServerConfig, profile: String): List<ProviderModels> {
        if (!config.hasServeCredentials) return emptyList()
        val payload = ServeClient(config).request(
            path = MODEL_OPTIONS_ROUTE,
            query = mapOf("profile" to profile),
            timeoutSeconds = MODEL_OPTIONS_TIMEOUT_SECONDS,
        )
        return parseModelOptions(payload.toString())
    }

    /**
     * Le registre des profils, lu **sans rien inventer**.
     *
     * Accepte `{ "profiles": [ … ] }` comme `{ "data": [ … ] }` ou un tableau nu. Chaque entrée
     * garde son nom (obligatoire), son modèle et son fournisseur quand ils sont là ; un profil sans
     * nom est ignoré.
     */
    internal fun parseProfiles(body: String): List<ProfileRow> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val rows: List<JsonElement> = when {
            root is JsonObject && root["profiles"] is JsonArray -> (root["profiles"] as JsonArray).toList()
            root is JsonObject && root["data"] is JsonArray -> (root["data"] as JsonArray).toList()
            root is JsonArray -> root.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull { element ->
            val row = element.asObject() ?: return@mapNotNull null
            val name = row.firstString("name", "profile")?.trim() ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            ProfileRow(
                name = name,
                model = row.firstString("model"),
                provider = row.firstString("provider"),
                isDefault = row["is_default"].asBool() ?: (name == DEFAULT_PROFILE),
                gatewayRunning = row["gateway_running"].asBool() ?: false,
            )
        }
    }

    /**
     * `GET api/model/options` lu au plus large : un fournisseur par entrée, ses identifiants de
     * modèles en clair, **son nom écrit** et **sa table de prix**.
     *
     * Le tarif est la seule source de prix de l'app : la route publie, par fournisseur,
     * `pricing = { "<identifiant>": { "input": "$0.30", "output": "$1.20", "cache": "$0.006",
     * "free": false } }` — des montants **déjà formatés**, en dollars par million de jetons. Ce lecteur
     * les garde tels quels ; un modèle sans ligne reste **sans prix**, et la pastille le dit en
     * n'écrivant que le nom du fournisseur.
     *
     * Un fournisseur sans modèle n'apparaît pas.
     */
    internal fun parseModelOptions(body: String): List<ProviderModels> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val rows: List<JsonElement> = when {
            root is JsonObject -> root.array("providers")
            root is JsonArray -> root.toList()
            else -> return emptyList()
        }
        return rows.mapNotNull { element ->
            val row = element.asObject() ?: return@mapNotNull null
            val slug = row.firstString("slug", "id", "provider", "name")?.trim() ?: return@mapNotNull null
            val models = row.array("models")
                .mapNotNull { it.asString()?.trim()?.takeIf { text -> text.isNotEmpty() } }
            if (models.isEmpty()) return@mapNotNull null
            ProviderModels(
                slug = slug,
                name = row.firstString("name") ?: slug,
                models = models,
                pricing = parsePricing(row["pricing"]),
            )
        }
    }

    /**
     * La table de prix d'un fournisseur : identifiant → montants publiés, **tels quels**.
     *
     * Une entrée sans montant exploitable est **écartée** plutôt que gardée vide : le modèle se lira
     * alors comme un modèle sans prix, pas comme un modèle à zéro.
     */
    internal fun parsePricing(element: JsonElement?): Map<String, ServerPrice> {
        val table = runCatching { element?.jsonObject }.getOrNull() ?: return emptyMap()
        return table.mapNotNull { (id, value) ->
            val key = id.trim()
            if (key.isEmpty()) return@mapNotNull null
            val row = runCatching { value.jsonObject }.getOrNull() ?: return@mapNotNull null
            val price = ServerPrice(
                input = text(row["input"]),
                output = text(row["output"]),
                cache = text(row["cache"]),
                free = runCatching { row["free"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false,
            )
            price.takeIf { it.hasValue }?.let { key to it }
        }.toMap()
    }

    /**
     * L'agrégation pure : le registre des profils + les listes par profil → la feuille.
     *
     * Pour chaque profil, un modèle **par identifiant**, le modèle configuré du profil en tête :
     *
     * - l'alias de passerelle (`hermes-agent`) est **écarté** — ce n'est pas un nom de modèle ;
     * - un profil dont la liste a répondu est [ProfileState.READY] ;
     * - un profil sans liste mais avec **un** modèle connu est [ProfileState.PARTIAL] — il se montre ;
     * - un profil dont on n'a rien est [ProfileState.UNREACHABLE] — il apparaît quand même, avec son
     *   état, sans effacer les autres.
     */
    internal fun buildCatalog(
        profiles: List<ProfileRow>,
        options: Map<String, List<ProviderModels>>,
    ): List<ProfileCatalog> = profiles.map { profile ->
        val providers = options[profile.name].orEmpty()
        val nameBySlug = providers.associate { it.slug to it.name }
        val priceBySlug = providers.associate { it.slug to it.pricing }
        val merged = linkedMapOf<String, ModelChoice>()
        // Chaque modèle garde **une rangée**, et **toutes** ses offres : le même identifiant servi
        // par deux fournisseurs (le local `lmstudio` et le distant `kilocode`) se dessine une fois,
        // avec une pastille par fournisseur — c'est la forme de la référence.
        fun add(id: String, slug: String, name: String?, price: ServerPrice?) {
            if (isGatewayAlias(id)) return
            val clean = slug.trim().ifEmpty { ModelChoice.providerOf(id) }
            val offer = ModelOffer(
                provider = clean,
                providerName = name?.trim()?.takeIf { it.isNotEmpty() } ?: clean,
                price = price?.takeIf { it.hasValue },
            )
            val existing = merged[id]
            merged[id] = when {
                existing == null -> ModelChoice(
                    id = id,
                    provider = clean,
                    providerName = offer.providerName,
                    price = offer.price,
                    offers = listOf(offer),
                    profile = profile.name,
                )
                // Un même fournisseur n'est pas ajouté deux fois : une seule pastille.
                existing.offers.any { it.provider == clean } -> existing
                else -> existing.copy(offers = existing.offers + offer)
            }
        }
        // 1) Le modèle configuré du profil : la donnée la plus fiable, écrite par le profil lui-même.
        //    Son fournisseur est nommé et tarifé par la table du fournisseur quand celle-ci le
        //    connaît — c'est ce qui fait porter au modèle configuré la **même** pastille que la ligne
        //    du fournisseur (le `deepseek/deepseek-v4.1-flash` du profil `kilo` et son
        //    `Kilo Code · 0,30 $ / 1,20 $`).
        profile.model?.let { id ->
            val slug = profile.provider?.trim()?.takeIf { it.isNotEmpty() } ?: ModelChoice.providerOf(id)
            add(id, slug, nameBySlug[slug], priceBySlug[slug]?.get(id))
        }
        // 2) Les modèles de chaque fournisseur connecté du profil, avec leur nom et leur prix.
        providers.forEach { provider ->
            provider.models.forEach { id -> add(id, provider.slug, provider.name, provider.pricing[id]) }
        }
        val state = when {
            options.containsKey(profile.name) -> ProfileState.READY
            merged.isNotEmpty() -> ProfileState.PARTIAL
            else -> ProfileState.UNREACHABLE
        }
        ProfileCatalog(profile = profile.name, models = merged.values.toList(), state = state)
    }

    /**
     * Lit un corps de `GET /v1/models`.
     *
     * Accepte la forme `{ "data": [ … ] }` comme un tableau nu. Pour chaque entrée, garde
     * l'identifiant, le fournisseur (`owned_by` ou `provider`), la fenêtre de contexte
     * (`context_length`, `context_window` ou `max_context_length`) et, **quand il est là seulement**,
     * le tarif d'entrée et de sortie.
     */
    internal fun parse(body: String): List<ModelChoice> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val rows: List<JsonElement> = when {
            root is JsonObject && root["data"] is JsonArray -> (root["data"] as JsonArray).toList()
            root is JsonArray -> root.toList()
            else -> return emptyList()
        }

        return rows.mapNotNull { element -> parseRow(element) }
    }

    private fun parseRow(element: JsonElement): ModelChoice? {
        val row = runCatching { element.jsonObject }.getOrNull() ?: return null
        val id = text(row["id"])?.trim().orEmpty()
        if (id.isEmpty()) return null

        val provider = text(row["owned_by"]) ?: text(row["provider"])
        val label = text(row["name"])?.takeIf { it.isNotBlank() }
        val context = whole(row["context_length"])
            ?: whole(row["context_window"])
            ?: whole(row["max_context_length"])
        val capabilities = runCatching {
            (row["capabilities"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull().orEmpty()

        // Le tarif : tantôt un objet `pricing` en dollars **par jeton** (OpenRouter), tantôt un
        // champ direct par million de jetons. On lit les deux, et on n'écrit un prix que là où la
        // source en donne un.
        val pricing = runCatching { row["pricing"]?.jsonObject }.getOrNull()
        val perTokenPrompt = decimal(pricing?.get("prompt"))
        val perTokenCompletion = decimal(pricing?.get("completion"))
        val promptPerMillion = decimal(row["prompt_price_per_mtok"]) ?: perTokenPrompt?.times(1_000_000.0)
        val completionPerMillion = decimal(row["completion_price_per_mtok"]) ?: perTokenCompletion?.times(1_000_000.0)

        return ModelChoice(
            id = id,
            label = label ?: ModelChoice.labelOf(id),
            provider = provider?.takeIf { it.isNotBlank() } ?: ModelChoice.providerOf(id),
            contextLength = context,
            capabilities = capabilities,
            promptPricePerMTok = promptPerMillion,
            completionPricePerMTok = completionPerMillion,
        )
    }

    private fun text(element: JsonElement?): String? =
        runCatching { element?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun whole(element: JsonElement?): Int? =
        runCatching { element?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun decimal(element: JsonElement?): Double? =
        runCatching { element?.jsonPrimitive?.doubleOrNull }.getOrNull()
}

/**
 * Une ligne de `GET api/profiles` : un profil Hermes, et le modèle qu'il sert.
 *
 * [model] et [provider] restent `null` quand le profil ne les donne pas — on n'en invente aucun.
 */
data class ProfileRow(
    val name: String,
    val model: String?,
    val provider: String?,
    val isDefault: Boolean = false,
    val gatewayRunning: Boolean = false,
)

/**
 * Les modèles d'un fournisseur d'un profil, tels que `GET api/model/options` les rend : son **slug**,
 * son **nom écrit** (« LM Studio », « Kilo Code »), ses identifiants, et **sa table de prix** —
 * `pricing` publie, par identifiant, les montants en dollars par million de jetons que le
 * fournisseur annonce. Un fournisseur qui n'en publie pas rend une table vide : le modèle se lira
 * alors **sans prix**, jamais à zéro.
 */
data class ProviderModels(
    val slug: String,
    val name: String,
    val models: List<String>,
    val pricing: Map<String, ServerPrice> = emptyMap(),
)

/**
 * Un profil et ses modèles, prêts pour la feuille : le **modèle réel** de chaque rangée, son profil,
 * et l'état de la liste quand elle n'a pas répondu.
 */
data class ProfileCatalog(
    val profile: String,
    val models: List<ModelChoice>,
    val state: ProfileState,
)
