package com.onyx.app.ui.design

import com.onyx.app.localization.OnyxLocalization

/**
 * **Le repli déclaré** d'un tour : le modèle configuré, le modèle réellement **en force**, et le
 * fournisseur réel de ce dernier.
 *
 * Un tour peut être servi par un **autre modèle** que celui configuré : la configuration du profil
 * porte une **chaîne de secours** (`fallback_providers`) — quand le fournisseur principal échoue
 * (HTTP 403 « requires a challenge », coupure, quota), la passerelle bascule sur le modèle **local**
 * et **le tour n'est pas perdu** (c'est voulu). L'interface, elle, nommait le modèle **configuré**
 * et jamais celui **en force** : l'en-tête annonçait `deepseek/deepseek-v4.1-flash` pendant qu'un
 * modèle local (`ornith1.5-9b-3mper0rr-obliterated`, `lmstudio`) répondait — un mensonge de
 * l'interface, et c'est ce que cette classe corrige.
 *
 * La passerelle **déclare** le basculement sur le flux, en clair :
 * `⚠️ Model fallback: <ancien> via <fournisseur> unavailable (<raison>); using <nouveau> via <fournisseur>.`
 * — elle ne le met **pas** dans `session.info` (qui continue de porter le modèle configuré). C'est
 * donc **cette déclaration** que l'app lit pour savoir quel modèle a servi le tour.
 */
data class FallbackDeclaration(
    /** Le modèle configuré du profil — celui que l'interface annonçait à tort. */
    val configuredModel: String,
    val configuredProvider: String,
    /** Le modèle qui a **réellement servi** le tour. */
    val servedModel: String,
    val servedProvider: String,
) {
    /** Le nom du modèle configuré, sans préfixe de fournisseur. */
    val configuredLabel: String get() = ModelChoice.labelOf(configuredModel)

    /** Le nom du modèle en force, sans préfixe de fournisseur. */
    val servedLabel: String get() = ModelChoice.labelOf(servedModel)

    /** Vrai si le modèle en force tourne **sur la machine**, sans fournisseur facturé. */
    val servedIsLocal: Boolean get() = isLocalProvider(servedProvider)

    /** Le modèle en force tel qu'on le nomme : « ornith1.5-9b (lmstudio, local) ». */
    fun servedLine(): String = buildString {
        append(servedLabel)
        append(" (")
        append(servedProvider)
        if (servedIsLocal) append(", ").append(OnyxLocalization["local"])
        append(")")
    }

    /**
     * La ligne **déclarée** du repli : « deepseek-v4.1-flash → repli : ornith1.5-9b (lmstudio, local) ».
     * C'est ce que l'en-tête écrit quand un repli est à l'œuvre — le modèle en force, et celui qu'il
     * remplace, fournisseur réel compris.
     */
    fun declaredLine(): String = OnyxLocalization.format(
        "%1\$s → fallback: %2\$s",
        configuredLabel,
        servedLine(),
    )

    companion object {
        /** Les fournisseurs qui servent un modèle **local** : leur tarif par million n'existe pas. */
        val LOCAL_PROVIDERS: Set<String> =
            setOf("lmstudio", "lm-studio", "ollama", "llamacpp", "llama.cpp", "vllm", "local")

        fun isLocalProvider(provider: String?): Boolean =
            provider?.trim()?.lowercase() in LOCAL_PROVIDERS
    }
}

/**
 * La **déclaration de repli** telle que la passerelle l'écrit sur le flux, ou `null`.
 *
 * Le texte vient de `agent.conversation_loop` (`⚠️ Model fallback: … unavailable …; using … via
 * ….`) et arrive à l'app comme un `status.update`. Le lire est le seul moyen honnête de savoir
 * quel modèle a servi le tour : `session.info` porte le modèle **configuré**.
 */
object FallbackNotice {

    private val PATTERN = Regex(
        "Model fallback:\\s*(?<configured>.+?)\\s+via\\s+(?<configuredProvider>[\\w:-]+)\\s+" +
            "unavailable\\b.*?;\\s*using\\s+(?<served>.+?)\\s+via\\s+(?<servedProvider>[\\w:-]+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** La déclaration contenue dans [text], ou `null` si ce n'est pas une déclaration de repli. */
    fun parse(text: String?): FallbackDeclaration? {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return null
        val match = PATTERN.find(body) ?: return null
        val configured = match.groups["configured"]?.value?.trim().orEmpty()
        val served = match.groups["served"]?.value?.trim().orEmpty()
        if (configured.isEmpty() || served.isEmpty()) return null
        return FallbackDeclaration(
            configuredModel = configured,
            configuredProvider = match.groups["configuredProvider"]?.value?.trim().orEmpty(),
            servedModel = served,
            servedProvider = match.groups["servedProvider"]?.value?.trim().orEmpty(),
        )
    }
}

/**
 * Ce que le **modèle affiché** doit être : celui **en force**, jamais le configuré à sa place.
 *
 * L'en-tête, la puce du compositeur, le sélecteur et les récents lisent tous la **même** clé
 * ([com.onyx.app.ui.chat.ChatUiState.modelInForce]) ; cette fonction en décide la valeur. Sous un
 * repli déclaré, c'est le modèle **servi** — un vrai nom de modèle, jamais un alias de passerelle.
 */
object ModelFallback {

    /** Le nom à peindre (et, partant, celui qui part) : le modèle servi du repli, sinon l'en-vigueur. */
    fun displayName(fallback: FallbackDeclaration?, inForce: String?): String? {
        val served = fallback?.servedModel?.trim().orEmpty()
        return if (served.isNotEmpty() && !ModelName.isAlias(served)) served else inForce
    }

    /**
     * La ligne de **déclaration** à poser sous le nom du modèle : le repli nommé, ou `null` quand
     * le tour est servi par le modèle en vigueur (rien à déclarer).
     */
    fun declaredLine(fallback: FallbackDeclaration?): String? = fallback?.declaredLine()

    /**
     * Le niveau de réflexion à peindre : un modèle de repli **local** ne propose pas les crans du
     * modèle configuré, donc on n'écrit pas un cran qui ne pilote rien.
     */
    fun reflectionLabel(selectedLabel: String, fallback: FallbackDeclaration?): String =
        if (fallback?.servedIsLocal == true) "" else selectedLabel

    /**
     * La ligne de prix d'un modèle : **aucune** quand le tour est servi en local — afficher
     * « 0,30 $ / 1,20 $ » pour un tour servi sur la machine serait faux.
     */
    fun costLine(priceLine: String?, fallback: FallbackDeclaration?): String? =
        if (fallback?.servedIsLocal == true) null else priceLine
}
