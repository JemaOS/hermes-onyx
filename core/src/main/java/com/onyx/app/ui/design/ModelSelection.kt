package com.onyx.app.ui.design

/**
 * Le **choix du modèle** — la logique pure du sélecteur, tenue ici et nulle part ailleurs.
 *
 * Rien de Material, rien de spécifique à un écran : ce fichier ne décrit que ce que le sélecteur
 * *décide* — le fournisseur d'un identifiant, son monogramme, ce qu'une recherche retient, l'ordre
 * d'une liste (favoris, récents, le reste) et la correspondance entre les crans de réflexion de la
 * référence et **ce que le serveur accepte vraiment**. C'est ce que les tests interrogent, donc le
 * chiffre annoncé et le chiffre vérifié sont le même — sans appareil.
 *
 * Deux règles du propriétaire y sont appliquées :
 *
 * 1. **Aucun prix n'est inventé.** Un [ModelChoice] ne porte un tarif que si la source l'a donné
 *    (`promptPricePerMTok` / `completionPricePerMTok` restent `null` sinon), et [priceLine] ne
 *    rend rien dans ce cas : la ligne de la liste montre alors ce qui existe — l'identifiant du
 *    fournisseur, et la fenêtre de contexte quand elle est connue.
 * 2. **Les crans de réflexion sont ceux du serveur.** La référence en montre sept ; le serveur n'en
 *    connaît pas sept. [ReasoningLevels] propose la liste réellement disponible et dit, à part, ce
 *    qui manque ([ReasoningLevels.Plan.missing]) — jamais un cran dessiné pour rien.
 */

// ---------------------------------------------------------------------------------------------
// Le prix, tel que le serveur le donne — jamais calculé, jamais deviné
// ---------------------------------------------------------------------------------------------

/**
 * Le **tarif d'un modèle chez un fournisseur**, tel que le serveur le rend.
 *
 * `GET api/model/options` publie, pour chaque fournisseur connecté, une table
 * `pricing = { "<identifiant>": { "input": "$0.30", "output": "$1.20", "cache": "$0.006",
 * "free": false } }` — des **chaînes déjà formatées**, en dollars par million de jetons. Cette
 * classe ne fait que **garder** ces chaînes telles quelles : aucun calcul, aucune estimation,
 * aucune table locale. C'est la seule source de prix de l'app.
 *
 * Quand le serveur n'a pas de ligne pour un modèle (le tarif n'est pas encore lu, ou le
 * fournisseur n'en publie pas — un serveur local, par exemple), [input] et [output] restent `null`
 * et [hasValue] est faux : la pastille de la rangée écrit **le fournisseur seul**, jamais un
 * montant.
 */
data class ServerPrice(
    /** Le prix d'entrée publié par le serveur, mot pour mot (« $0.30 »). */
    val input: String? = null,
    /** Le prix de sortie publié par le serveur, mot pour mot (« $1.20 »). */
    val output: String? = null,
    /** Le prix de lecture de cache, quand le serveur le publie (« $0.006 »). */
    val cache: String? = null,
    /** Vrai quand le serveur déclare le modèle gratuit (entrée et sortie à « free »). */
    val free: Boolean = false,
) {
    /** Vrai dès que le serveur a dit quelque chose sur le prix de ce modèle. */
    val hasValue: Boolean get() = free || !input.isNullOrBlank() || !output.isNullOrBlank()

    /**
     * Le prix écrit à la française : « 0,30 $ / 1,20 $ » — entrée puis sortie, par million de
     * jetons. Le serveur écrit « $0.30 » ; on ne fait que **remettre le montant dans la
     * ponctuation de la langue** (virgule décimale, symbole après). Aucun chiffre n'est produit
     * ici qui ne vienne du serveur : un montant illisible est rendu tel quel plutôt que retouché.
     *
     * `null` quand le serveur n'a rien dit — jamais « 0 $ ».
     */
    fun line(french: Boolean = true): String? {
        if (free) return if (french) FreeLabel else FreeLabelEn
        val entry = frenchOf(input, french)
        val exit = frenchOf(output, french)
        if (entry == null && exit == null) return null
        return listOfNotNull(entry, exit).joinToString(" / ")
    }

    /** Le même montant, en gardant la ponctuation du serveur (l'app en anglais). */
    fun lineAsServed(): String? = line(french = false)

    private fun frenchOf(raw: String?, french: Boolean): String? =
        if (french) french(raw) else raw?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** Ce qu'une pastille écrit pour un modèle que le serveur déclare gratuit. */
        const val FreeLabel = "gratuit"
        const val FreeLabelEn = "free"

        /**
         * « $0.30 » → « 0,30 $ ». Le symbole passe **après** le montant et la décimale prend la
         * virgule, comme la référence l'écrit. Une chaîne qui n'est pas un montant en dollars est
         * rendue telle quelle — on ne réécrit pas ce qu'on n'a pas compris.
         */
        internal fun french(raw: String?): String? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val body = value.removePrefix("$").trim()
            val swapped = body.replace('.', ',')
            return if (value.startsWith("$")) "$swapped $" else swapped
        }
    }
}

/**
 * Une **offre** : un fournisseur qui propose un modèle, et le prix qu'il publie pour lui.
 *
 * C'est l'unité de la pastille d'information d'une rangée — `LM Studio` (qui ne publie aucun
 * prix), `Kilo Code · 0,30 $ / 1,20 $`.
 */
data class ModelOffer(
    /** Le slug du fournisseur (« kilocode »), tel que le serveur le nomme. */
    val provider: String,
    /** Le nom du fournisseur tel que le serveur l'écrit (« Kilo Code »). */
    val providerName: String = provider,
    /** Le prix publié par ce fournisseur, ou `null` quand il n'en publie pas. */
    val price: ServerPrice? = null,
) {
    /**
     * La pastille de cette offre : le **nom du fournisseur** seul, ou suivi du prix **quand le
     * serveur en a donné un**. Jamais un « — » ni un montant de remplissage : une offre sans prix
     * se lit exactement comme la référence la montre, `LM Studio`.
     */
    fun chip(french: Boolean = true): String {
        val line = price?.line(french) ?: return providerName
        return "$providerName$ChipSeparator$line"
    }

    /** Le nom affiché, sans le prix — pour la recherche et l'en-tête. */
    val label: String get() = providerName

    companion object {
        /** Le séparateur de la pastille : « Kilo Code · 0,30 $ / 1,20 $ ». */
        const val ChipSeparator = " · "
    }
}

/**
 * Une **rangée du sélecteur** : le modèle, et **une offre par fournisseur qui le propose**.
 *
 * C'est la forme que la référence dessine : un modèle s'écrit **une seule fois** — son monogramme,
 * son nom — et sous lui les pastilles de ses fournisseurs. Un modèle servi localement *et* à
 * distance porte donc deux pastilles sur une seule rangée, ce qu'un [ModelChoice] par fournisseur ne
 * pouvait pas montrer.
 */
data class ModelEntry(
    override val id: String,
    override val label: String = ModelChoice.labelOf(id),
    val profile: String? = null,
    val offers: List<ModelOffer> = emptyList(),
    val contextLength: Int? = null,
) : ModelRowRef {
    /** Le monogramme : la première lettre du nom, en capitale — jamais un carré vide. */
    val monogram: String get() = ModelChoice.monogramOf(label)

    override val listKey: String
        get() = profile?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it/$id" } ?: id

    /** Le nom du profil à écrire pour cette rangée — jamais vide. */
    override fun profileName(): String =
        profile?.trim()?.takeIf { it.isNotEmpty() } ?: ModelChoice.LocalProvider

    /** La rangée se retrouve par son nom, son profil, ou **n'importe lequel** de ses fournisseurs. */
    override val searchText: String
        get() = (listOf(id, label, profileName()) + offers.flatMap { listOf(it.provider, it.providerName) })
            .joinToString(" ")
            .lowercase()

    /** Vrai dès qu'**une** offre porte un prix donné par le serveur. */
    val hasPrice: Boolean get() = offers.any { it.price?.hasValue == true }

    /** Les pastilles d'information de la rangée, dans l'ordre du serveur. */
    fun offerChips(french: Boolean = true): List<String> = offers.map { it.chip(french) }

    /** Vrai si ce modèle a une offre chez ce fournisseur (la pastille de filtre de la feuille). */
    fun offeredBy(provider: String): Boolean = offers.any { it.provider == provider }

    /**
     * Le sous-titre d'une ligne : **le profil**, puis chaque offre. Il sert la recherche et la
     * lecture d'accessibilité ; la rangée, elle, dessine les pastilles.
     */
    fun rowSubtitle(french: Boolean = true): String =
        (listOf(profileName()) + offerChips(french)).joinToString(ModelOffer.ChipSeparator)
}

// ---------------------------------------------------------------------------------------------
// Un modèle, tel que le sélecteur le montre
// ---------------------------------------------------------------------------------------------

/** Ce qu'une rangée doit savoir dire d'elle-même pour être triée, filtrée et regroupée. */
interface ModelRowRef {
    /** L'identifiant que le serveur accepte (« openai/gpt-5 »). */
    val id: String

    /** Le nom écrit sur la rangée, sans le préfixe du fournisseur. */
    val label: String

    /** La clé de liste : **le profil qualifie l'identifiant**. */
    val listKey: String

    /** Le nom du profil de cette rangée — jamais vide. */
    fun profileName(): String

    /**
     * Tout ce par quoi une recherche retrouve la rangée : l'identifiant, le nom, le profil et
     * **chacun** de ses fournisseurs. Réuni ici pour que la recherche d'une ligne et celle d'une
     * rangée agrégée soient la même règle, et pas deux qui divergent.
     */
    val searchText: String
}

/**
 * Un modèle du sélecteur.
 *
 * [id] est l'identifiant que le serveur accepte (« openai/gpt-5 ») ; [label] le nom sans le
 * préfixe du fournisseur (« gpt-5 »), ce que la rangée écrit ; [provider] l'identifiant du
 * fournisseur, écrit **sous** le nom (la référence le fait). [contextLength] et [capabilities]
 * viennent du serveur quand il les donne ; les deux tarifs restent `null` quand il ne les donne pas
 * — et [priceLine] n'affiche alors aucun prix.
 */
data class ModelChoice(
    override val id: String,
    override val label: String = ModelChoice.labelOf(id),
    val provider: String = ModelChoice.providerOf(id),
    /**
     * Le **nom** du fournisseur tel que le serveur l'écrit (« Kilo Code », « LM Studio ») — la
     * pastille d'information écrit ce nom, pas le slug. `null` quand la source ne l'a pas donné :
     * la pastille retombe alors sur le slug, jamais sur un nom inventé.
     */
    val providerName: String? = null,
    /**
     * Le **tarif publié par ce fournisseur** pour ce modèle, tel quel
     * (`api/model/options` → `pricing`). `null` quand le serveur n'en donne pas : la pastille
     * écrit alors le fournisseur seul. Aucun montant n'est jamais calculé ici.
     */
    val price: ServerPrice? = null,
    /**
     * **Toutes les offres** de ce modèle, un fournisseur chacune, dans l'ordre du serveur.
     *
     * Un modèle peut vivre chez plusieurs fournisseurs à la fois — le `minimax/minimax-m2.7` servi
     * par le `lmstudio` local **et** par `kilocode`. [provider]/[providerName]/[price] gardent la
     * **première** offre (celle de la source la plus directe : `/v1/models`, ou le modèle configuré
     * d'un profil) ; cette liste porte l'ensemble, et c'est elle que [ModelSelection.entries] lit.
     * Vide, [toOffer] retombe sur la seule offre décrite par les trois champs ci-dessus.
     */
    val offers: List<ModelOffer> = emptyList(),
    /**
     * Le **profil Hermes** d'où vient ce modèle (« default », « kilo », …).
     *
     * Le même identifiant peut vivre sous plusieurs profils — le modèle local `sage-a1.5` du profil
     * `default` et les modèles distants du profil `kilo` coexistent. Le profil n'est donc pas un
     * détail cosmétique : c'est lui qui distingue deux rangées d'identifiant identique, et c'est lui
     * que la feuille écrit sous le nom. `null` seulement pour un modèle hors de tout profil (le
     * repli d'un serveur unique).
     */
    val profile: String? = null,
    val contextLength: Int? = null,
    val capabilities: List<String> = emptyList(),
    val promptPricePerMTok: Double? = null,
    val completionPricePerMTok: Double? = null,
) : ModelRowRef {
    /** Le monogramme : la première lettre du nom, en capitale — jamais un carré vide. */
    val monogram: String get() = ModelChoice.monogramOf(label)

    /**
     * La clé de liste : **le profil qualifie l'identifiant**.
     *
     * Deux profils qui servent le même modèle (le `deepseek/deepseek-v4.1-flash` de `kilo`, de
     * `coder`, de `scout`…) donnent alors deux rangées distinctes — la liste ne peut ni les
     * dédoublonner par erreur ni collisionner sur leur clé.
     */
    override val listKey: String
        get() = profile?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it/$id" } ?: id

    /** L'**offre** de cette ligne : son fournisseur, son nom, et le prix publié s'il y en a un. */
    fun toOffer(): ModelOffer = ModelOffer(
        provider = provider,
        providerName = providerName?.trim()?.takeIf { it.isNotEmpty() } ?: provider,
        price = price?.takeIf { it.hasValue },
    )

    /** Le nom du profil à écrire pour cette rangée — jamais vide. */
    override fun profileName(): String =
        profile?.trim()?.takeIf { it.isNotEmpty() } ?: ModelChoice.LocalProvider

    /** La ligne se retrouve par son nom, son profil, son fournisseur, ou son identifiant complet. */
    override val searchText: String
        get() = listOf(id, label, provider, providerName.orEmpty(), profileName())
            .joinToString(" ")
            .lowercase()

    /** Vrai si le serveur a donné un tarif, entrée ou sortie. Sinon rien ne se peint. */
    val hasPrice: Boolean get() = promptPricePerMTok != null || completionPricePerMTok != null

    companion object {
        /** Le fournisseur d'un identifiant : la partie avant « / », sinon `local`. */
        fun providerOf(id: String): String {
            val trimmed = id.trim()
            val provider = trimmed.substringBefore('/', missingDelimiterValue = "")
            return provider.ifEmpty { LocalProvider }
        }

        /** Le nom sans le préfixe du fournisseur : « openai/gpt-5 » → « gpt-5 ». */
        fun labelOf(id: String): String {
            val trimmed = id.trim()
            return if (trimmed.contains('/')) trimmed.substringAfter('/') else trimmed
        }

        /** Le monogramme d'un nom : sa première lettre utile, en capitale. */
        fun monogramOf(name: String): String {
            val letter = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return DefaultMonogram
            return letter.uppercaseChar().toString()
        }

        /** Le fournisseur d'un modèle sans préfixe : le serveur lui-même. */
        const val LocalProvider = "local"
        const val DefaultMonogram = "?"
    }

    /**
     * La ligne de prix — **seulement quand la source l'a donnée**.
     *
     * Rend `null` dès qu'aucun tarif n'est connu : c'est la règle du propriétaire — pas de prix
     * inventé, pas de « — » qui laisse croire à un prix manquant. Quand elle existe, la ligne dit
     * l'entrée et la sortie, par million de jetons.
     */
    fun priceLine(): String? {
        if (!hasPrice) return null
        val entry = promptPricePerMTok?.let { "${formatPrice(it)} in" }
        val exit = completionPricePerMTok?.let { "${formatPrice(it)} out" }
        return listOfNotNull(entry, exit).joinToString(" · ")
    }

    /**
     * Les morceaux du sous-titre : le **fournisseur**, puis ce qui existe vraiment — le prix quand
     * le serveur le donne, sinon la fenêtre de contexte quand elle est connue. Jamais un prix nu.
     */
    private fun subtitleParts(): List<String> = buildList {
        add(provider)
        priceLine()?.let { add(it) }
        if (priceLine() == null) contextLine()?.let { add(it) }
    }

    /**
     * Le sous-titre de la rangée : le **fournisseur**, puis le prix ou la fenêtre de contexte.
     */
    fun subtitle(): String = subtitleParts().joinToString(" · ")

    /**
     * Le sous-titre **de la feuille** : **le profil d'abord**, puis le fournisseur et ce que la
     * source a donné.
     *
     * Le propriétaire doit lire *lequel* de ses profils porte ce modèle — le même identifiant peut
     * vivre sous `kilo`, `coder` et `scout`. Le profil est donc écrit **sur chaque rangée**, y
     * compris dans les sections transverses (favoris, récents) où l'en-tête ne le dit pas.
     */
    fun rowSubtitle(): String =
        (listOf(profileName()) + subtitleParts()).joinToString(" · ")

    /** « 128k » : la fenêtre de contexte, quand le serveur la donne. */
    fun contextLine(): String? {
        val context = contextLength ?: return null
        if (context <= 0) return null
        return if (context >= 1000) {
            val thousands = context / 1000
            if (context % 1000 == 0) "${thousands}k" else "${context}k"
        } else {
            context.toString()
        }
    }

    private fun formatPrice(value: Double): String {
        // Un tarif par million de jetons se lit en dollars, deux décimales au plus : « $3 » ou
        // « $0,15 » (virgule française quand la langue est le français).
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        val body = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
        return "$$body"
    }
}

// ---------------------------------------------------------------------------------------------
// Ce que la recherche et l'ordre retiennent
// ---------------------------------------------------------------------------------------------

/**
 * La liste du sélecteur, **par sections** : les favoris, les récents, puis le reste. C'est ce que
 * la feuille écrit, un en-tête par section.
 */
data class ModelSections<T : ModelRowRef>(
    val favourites: List<T>,
    val recents: List<T>,
    val rest: List<T>,
)

/**
 * L'état d'un **profil** dans la feuille.
 *
 * Un profil dont la liste de modèles ne répond pas ne doit **jamais** casser la liste : il apparaît
 * avec son état, les autres profils restent lisibles. [READY] quand la source a rendu ses modèles ;
 * [PARTIAL] quand elle n'a pas répondu mais que le profil a **un** modèle connu (le registre
 * `/api/profiles` l'a dit) — on le montre, en le disant ; [UNREACHABLE] quand on n'a rien de lui.
 */
enum class ProfileState { READY, PARTIAL, UNREACHABLE }

/** Un profil de la feuille : son nom, et l'état de sa liste de modèles. */
data class ProfileView(val name: String, val state: ProfileState = ProfileState.READY)

/** Les modèles d'un profil, dans l'ordre où la feuille les écrit (déjà triés). */
data class ProfileGroup<T : ModelRowRef>(val profile: String, val choices: List<T>)

/** Un fournisseur tel que la feuille le filtre : son slug, son nom écrit, et son nombre de modèles. */
data class ProviderFacet(val provider: String, val providerName: String, val count: Int)

object ModelSelection {

    /** La valeur de la pastille « Tout » : aucun filtre de fournisseur. */
    const val AllProviders = ""

    /**
     * Vrai si [choice] répond à [query] — par **nom de modèle, de fournisseur ou de profil**, comme
     * le propriétaire le cherche (« kilo », « lmstudio », « sage »). Une requête vide retient tout ;
     * la casse et les espaces de bord ne comptent pas.
     */
    fun matches(choice: ModelRowRef, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return choice.searchText.contains(needle)
    }

    /** Les modèles qu'une recherche retient, dans leur ordre d'entrée. */
    fun <T : ModelRowRef> filter(choices: List<T>, query: String): List<T> =
        choices.filter { matches(it, query) }

    /**
     * L'ordre de la liste, **par sections** : **les favoris d'abord**, puis **les récents**, puis le
     * reste par ordre alphabétique.
     *
     * Un favori qui est aussi un récent ne se dédouble pas (il reste dans les favoris), et un
     * identifiant gardé qui n'existe plus dans la liste est simplement ignoré.
     *
     * Tout se compte sur [ModelRowRef.listKey] — **le profil qualifie l'identifiant** — pour que
     * deux profils servant le même modèle ne se confondent pas. Sans profil, la clé est l'identifiant
     * lui-même : rien ne change pour un serveur unique.
     */
    fun <T : ModelRowRef> sections(
        choices: List<T>,
        favourites: Set<String>,
        recents: List<String>,
    ): ModelSections<T> {
        val byKey = choices.associateBy { it.listKey }
        val byId = choices.associateBy { it.id }
        val recentsSeen = linkedSetOf<String>()
        val recentOrder = recents.filter { recentsSeen.add(it) }

        fun recentRank(key: String): Int = recentOrder.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it }

        val favouriteRows = choices.filter { it.listKey in favourites || it.id in favourites }
            .sortedWith(compareBy({ recentRank(it.listKey) }, { it.listKey }))
        val favouriteKeys = favouriteRows.map { it.listKey }.toSet()
        // Les récents se retrouvent par leur clé de liste **ou** par leur identifiant : une entrée
        // gardée avant que le profil n'existe encore reste retrouvée.
        val recentRows = recentOrder.mapNotNull { byKey[it] ?: byId[it] }.filter { it.listKey !in favouriteKeys }
        val recentKeys = recentRows.map { it.listKey }.toSet()
        // Le reste garde **l'ordre de la source** — le catalogue d'un profil écrit son modèle
        // configuré puis ses fournisseurs dans l'ordre du serveur : le local du profil `default`
        // reste donc en tête de son profil, au lieu d'être noyé dans un tri alphabétique.
        val rest = choices.filter { it.listKey !in favouriteKeys && it.listKey !in recentKeys }

        return ModelSections(favourites = favouriteRows, recents = recentRows, rest = rest)
    }

    /** La liste mise à plat, dans l'ordre des sections : favoris, récents, puis le reste. */
    fun <T : ModelRowRef> ordered(
        choices: List<T>,
        favourites: Set<String>,
        recents: List<String>,
    ): List<T> {
        val sections = sections(choices, favourites, recents)
        return sections.favourites + sections.recents + sections.rest
    }

    /** Le nom du profil d'une rangée — jamais vide (le repli d'un serveur unique). */
    fun profileOf(choice: ModelRowRef): String = choice.profileName()

    /**
     * Le **nombre de rangées** que la ligne « Tous les modèles · N › » déplie : tout ce qui n'est ni
     * épinglé ni récent. C'est le compteur montré par la feuille, jamais le total des fournisseurs.
     */
    fun <T : ModelRowRef> restCount(sections: ModelSections<T>): Int = sections.rest.size

    /**
     * Vrai quand la liste doit s'ouvrir **dépliée** : sans rien d'épinglé ni de récent, replier la
     * liste ne montrerait que la ligne « Tous les modèles » — une feuille vide. La ligne reste donc
     * absente à la première ouverture, et apparaît dès qu'il y a quelque chose à garder devant.
     */
    fun <T : ModelRowRef> opensExpanded(sections: ModelSections<T>): Boolean =
        sections.favourites.isEmpty() && sections.recents.isEmpty()

    /**
     * Le **regroupement par profil** : les modèles d'un même profil restent côte à côte, dans
     * l'ordre d'apparition, et un profil qui n'apparaît plus laisse place aux suivants.
     *
     * C'est la structure que la feuille écrit : **un en-tête par profil**, pour que l'on voie d'un
     * coup d'œil les modèles **locaux** du profil `default` à côté des modèles distants du profil
     * `kilo` — au lieu d'une seule liste plate où deux profils servant le même modèle se
     * confondraient.
     */
    fun <T : ModelRowRef> groups(choices: List<T>): List<ProfileGroup<T>> {
        val byProfile = linkedMapOf<String, MutableList<T>>()
        choices.forEach { choice ->
            byProfile.getOrPut(profileOf(choice)) { mutableListOf() }.add(choice)
        }
        return byProfile.map { (profile, rows) -> ProfileGroup(profile, rows) }
    }

    // -----------------------------------------------------------------------------------------
    // L'agrégation : un modèle par rangée, une offre par fournisseur
    // -----------------------------------------------------------------------------------------

    /**
     * Les **rangées** de la feuille : un modèle écrit **une fois**, avec **toutes** les offres de
     * ses fournisseurs.
     *
     * C'est la forme que la référence dessine et que le catalogue ne rend pas telle quelle : le
     * serveur liste un modèle **par fournisseur** (`lmstudio` *et* `kilocode` servent
     * `minimax/minimax-m2.7`), alors que la rangée doit montrer `LM Studio` **et**
     * `Kilo Code · 0,30 $ / 1,20 $` côte à côte. L'agrégation se fait sur [ModelRowRef.listKey] —
     * profil compris — donc deux profils qui servent le même modèle gardent chacun leur rangée.
     *
     * L'ordre est celui de la source : **le modèle configuré du profil d'abord**, puis les
     * fournisseurs dans l'ordre du serveur. Le local du profil `default` reste en tête de sa
     * section, comme sur la capture du propriétaire.
     */
    fun entries(choices: List<ModelChoice>): List<ModelEntry> {
        val byKey = linkedMapOf<String, MutableList<ModelChoice>>()
        choices.forEach { byKey.getOrPut(it.listKey) { mutableListOf() }.add(it) }
        return byKey.map { (_, rows) ->
            val first = rows.first()
            // Une offre par **fournisseur** : deux lignes du même fournisseur ne se dédoublonnent
            // pas en deux pastilles. Une ligne qui porte déjà ses offres (le catalogue les a
            // réunies) est lue telle quelle ; sinon c'est son fournisseur propre qui la décrit.
            val offers = linkedMapOf<String, ModelOffer>()
            rows.forEach { row ->
                val list = row.offers.ifEmpty { listOf(row.toOffer()) }
                list.forEach { offer -> offers.putIfAbsent(offer.provider, offer) }
            }
            ModelEntry(
                id = first.id,
                label = first.label,
                profile = first.profile,
                offers = offers.values.toList(),
                contextLength = rows.firstNotNullOfOrNull { it.contextLength },
            )
        }
    }

    /**
     * Les **fournisseurs** que la feuille propose en pastilles de filtre : chaque fournisseur qui
     * offre au moins une rangée, dans l'ordre où il apparaît, **son nom écrit par le serveur**, et
     * son nombre de modèles. Aucun fournisseur n'est inventé : la liste vient des rangées
     * réellement reçues.
     */
    fun providers(entries: List<ModelEntry>): List<ProviderFacet> {
        val seen = linkedMapOf<String, ProviderFacet>()
        entries.forEach { entry ->
            entry.offers.forEach { offer ->
                val current = seen[offer.provider]
                seen[offer.provider] = if (current == null) {
                    ProviderFacet(offer.provider, offer.providerName, 1)
                } else {
                    current.copy(count = current.count + 1)
                }
            }
        }
        return seen.values.toList()
    }

    /**
     * Les rangées qu'un filtre de fournisseur retient. [provider] vide (« Tout ») retient tout ;
     * sinon seule une rangée qui a **une offre** chez ce fournisseur passe — c'est la rangée
     * entière qui reste, avec toutes ses pastilles.
     */
    fun byProvider(entries: List<ModelEntry>, provider: String): List<ModelEntry> =
        if (provider.isBlank()) entries else entries.filter { it.offeredBy(provider) }

    /**
     * Le sous-titre d'un en-tête de profil : **le profil, et son état quand il n'est pas normal**.
     * Un profil joignable n'a pas besoin d'un mot de plus ; un profil injoignable le dit.
     */
    fun profileSubtitle(view: ProfileView): String? = when (view.state) {
        ProfileState.READY -> null
        ProfileState.PARTIAL -> "model list unavailable"
        ProfileState.UNREACHABLE -> "unreachable"
    }

    /**
     * Vrai si le favori d'un identifiant se **garde** d'une relance à l'autre : c'est la règle que
     * le test vérifie, et elle tient à une seule chose — le jeu de favoris est relu tel quel.
     */
    fun favouritesSurviveReload(before: Set<String>, after: Set<String>): Boolean = before == after
}

// ---------------------------------------------------------------------------------------------
// Les crans de réflexion — ceux du serveur, pas ceux de la référence
// ---------------------------------------------------------------------------------------------

object ReasoningLevels {

    /**
     * Les sept crans que **la référence** montre, dans son ordre : Minimal → Ultra.
     * Ils ne sont pas dessinés tels quels ; ils servent à dire ce qui manque.
     */
    val referenced: List<String> =
        listOf("Minimal", "Low", "Medium", "High", "Extra high", "Max", "Ultra")

    /**
     * Ce que **le serveur accepte vraiment**, valeur → libellé. C'est la liste de l'app d'origine
     * (`SettingsRepository.REASONING_EFFORTS`) : `""` laisse le serveur décider, `none` lui demande
     * de ne pas réfléchir, et les six autres graduent l'effort.
     */
    val server: List<Pair<String, String>> = listOf(
        "" to "Default",
        "none" to "None",
        "minimal" to "Minimal",
        "low" to "Low",
        "medium" to "Medium",
        "high" to "High",
        "xhigh" to "X-High",
        "max" to "Max",
    )

    /**
     * La correspondance entre un cran de la référence et une valeur du serveur.
     *
     * « Extra high » de la référence est le `xhigh` du serveur (le même cran, un autre mot).
     * « Ultra », lui, **n'existe pas** côté serveur : il n'est donc pas dans cette table, et
     * [plan] le déclare manquant plutôt que de l'inventer.
     */
    val mapping: Map<String, String> = mapOf(
        "Minimal" to "minimal",
        "Low" to "low",
        "Medium" to "medium",
        "High" to "high",
        "Extra high" to "xhigh",
        "Max" to "max",
    )

    /** Ce qu'un plan retient : les puces réellement proposées, et les crans de la référence absents. */
    data class Plan(
        /** La liste du serveur, telle quelle — rien n'y est ajouté. */
        val chips: List<Pair<String, String>>,
        val missing: List<String>,
        /**
         * La **rangée telle qu'elle se dessine** : les crans de la référence, **dans son ordre**
         * (Minimal → Max), suivis des deux crans que le serveur connaît en plus (Default, None).
         *
         * C'est ce que la feuille peint. L'ordre de la référence est tenu pour que la rangée se lise
         * exactement comme sur sa capture — « Faible, Moyen, Élevé, Très élevé, Max » après
         * défilement — et les deux crans supplémentaires sont **rejetés en fin de rangée** : ils
         * restent atteignables en défilant, sans déplacer ce que la référence montre. Aucun cran
         * refusé par le serveur n'y figure : `Ultra` n'y est pas.
         */
        val row: List<Pair<String, String>> = chips,
    )

    /**
     * L'ordre de la **rangée**, tel que la référence le dessine : les crans qu'elle montre, dans son
     * ordre, chacun avec la valeur que le serveur accepte pour lui. « Extra high » de la référence
     * porte le libellé de la référence (« Très élevé » en français) et la valeur `xhigh` du serveur.
     */
    val rowOrder: List<Pair<String, String>> = listOf(
        "minimal" to "Minimal",
        "low" to "Low",
        "medium" to "Medium",
        "high" to "High",
        "xhigh" to "Extra high",
        "max" to "Max",
    )

    /**
     * Les crans que le serveur connaît **en plus** de ceux de la référence, rejetés en fin de
     * rangée : « Default » (laisser le serveur décider) et « None » (ne pas réfléchir). Ils restent
     * atteignables — rien n'est retiré — sans prendre la place de ce que la référence montre.
     */
    val extraOrder: List<Pair<String, String>> = listOf(
        "" to "Default",
        "none" to "None",
    )

    /**
     * Le plan de la rangée « Réflexion » : les puces que le serveur accepte ([exposed], par défaut
     * [server]), **les crans de la référence qu'il ne connaît pas** ([Plan.missing]), et la rangée
     * peinte [Plan.row] — l'ordre de la référence d'abord, ses crans surnuméraires ensuite.
     *
     * Un cran absent n'est jamais dessiné — un appelant ne peut donc pas peindre « Ultra » sur un
     * serveur qui refuse cette valeur, ni « Très élevé » sur un serveur qui n'a pas `xhigh`. Quand
     * tout est là, [Plan.missing] est vide.
     */
    fun plan(exposed: List<Pair<String, String>> = server): Plan {
        val known = exposed.map { it.first }.toSet()
        val missing = referenced.filter { name ->
            val value = mapping[name]
            value == null || value !in known
        }
        val row = (rowOrder + extraOrder).filter { (value, _) -> value in known }
        return Plan(chips = exposed, missing = missing, row = row)
    }

    /** Vrai si le serveur accepte cette valeur. Un appelant refuse proprement le reste. */
    fun accepts(value: String, exposed: List<Pair<String, String>> = server): Boolean =
        exposed.any { it.first == value }

    /** Le libellé d'une valeur du serveur, ou la valeur telle quelle si elle n'est pas connue. */
    fun label(value: String, exposed: List<Pair<String, String>> = server): String =
        exposed.firstOrNull { it.first == value }?.second ?: value
}

// ---------------------------------------------------------------------------------------------
// Le nom du modèle — ce que le serveur sert, jamais l'alias qu'on lui envoie
// ---------------------------------------------------------------------------------------------

/**
 * **Quel nom écrire pour le modèle.**
 *
 * L'app n'envoie pas à la passerelle le nom du modèle qu'elle affiche : elle envoie un **alias**
 * (`hermes-agent`), et c'est le serveur qui choisit le modèle derrière. Afficher l'alias revient
 * donc à nommer le tuyau au lieu du modèle — et c'est exactement ce que la personne a vu : la puce
 * du compositeur disait `hermes-agent`, la barre du haut aussi.
 *
 * La règle, une seule, dans les deux endroits qui nomment le modèle (barre du haut, puce) :
 *
 * 1. **Ce que le serveur a dit** (`session.info` → [TurnEvent.ModelInUse]) prime, toujours : c'est
 *    le seul nom qui suit un changement de modèle en cours de conversation.
 * 2. Sinon, le modèle configuré sur le serveur — **sauf** si c'est un alias de passerelle : un
 *    alias n'est pas un nom de modèle, et le peindre serait mentir.
 * 3. Sinon `null` : l'appelant écrit « Default model » (le libellé traduit), jamais l'alias.
 */
object ModelName {

    /**
     * Les noms qui désignent la **passerelle**, pas un modèle : l'app les envoie tels quels et le
     * serveur résout derrière. Comparés sans casse ni espaces, car un champ de réglage se tape à
     * la main.
     */
    val gatewayAliases: Set<String> = setOf(
        "hermes-agent",
        "hermes",
        "hermes-agent-v1",
        "gateway",
        "default",
    )

    /** Vrai si la valeur est un alias de passerelle (ou vide) : elle ne peut pas s'afficher. */
    fun isAlias(model: String?): Boolean {
        val value = model?.trim()?.lowercase().orEmpty()
        return value.isEmpty() || value in gatewayAliases
    }

    /**
     * Le nom à peindre : celui du serveur s'il l'a dit, sinon le modèle configuré quand c'en est
     * un vrai, sinon `null` (« Default model »).
     */
    fun display(served: String?, configured: String?): String? {
        val fromServer = served?.trim().orEmpty()
        // Un alias renvoyé par le serveur ne s'affiche pas non plus : c'est le même mensonge.
        if (fromServer.isNotEmpty() && !isAlias(fromServer)) return fromServer
        val configuredName = configured?.trim().orEmpty()
        if (configuredName.isNotEmpty() && !isAlias(configuredName)) return configuredName
        return null
    }
}

// ---------------------------------------------------------------------------------------------
// La puce du compositeur — le nom du modèle et le niveau de réflexion
// ---------------------------------------------------------------------------------------------

object ModelChip {

    /** Le séparateur de la puce : « Modèle | Réflexion », comme la référence l'écrit. */
    const val SEPARATOR = " | "

    /**
     * Le libellé entier de la puce : le modèle retenu, puis le niveau de réflexion.
     *
     * Un modèle vide retombe sur le mot « model » (l'app ne peint jamais une puce sans nom) ; une
     * réflexion vide (le `Default` du serveur) n'ajoute pas de second segment — la puce dit alors
     * le seul modèle, plutôt qu'un « Default » qui ne veut rien dire à l'écran.
     */
    fun label(model: String?, reflection: String?): String {
        val name = model?.trim().orEmpty().ifEmpty { FallbackModel }
        val level = reflection?.trim().orEmpty()
        return if (level.isEmpty() || level == FallbackReflection) name else name + SEPARATOR + level
    }

    /** La largeur de la puce ne déborde jamais : on réduit, puis on abrège aux frontières. */
    fun plan(
        label: String,
        availableDp: Float,
        chipInsetDp: Float,
        gapDp: Float,
        floorSp: Float = ChipRowFit.FLOOR_SP,
        width: (String, Float) -> Float,
    ): ChipLabelPlan {
        val plan = ChipRowFit.labelPlan(
            labels = listOf(label),
            availableDp = availableDp,
            insetsDp = listOf(chipInsetDp),
            gapDp = gapDp,
            floorSp = floorSp,
            sep = SEPARATOR.trim().first(),
            width = width,
        )
        // Le segment laissé par l'abrègement garde l'espace du séparateur : on le retire, sans
        // jamais toucher à un caractère du mot lui-même.
        return plan.copy(labels = plan.labels.map { it.trim() })
    }

    const val FallbackModel = "model"
    const val FallbackReflection = "Default"
}
