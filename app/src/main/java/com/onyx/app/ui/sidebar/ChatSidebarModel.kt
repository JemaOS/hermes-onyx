package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.net.ActivityKind
import com.onyx.app.data.net.ServerProject
import com.onyx.app.data.net.listedProjects
import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.IdentityMark
import java.util.Locale
import kotlin.math.max

/**
 * Le **modèle** du volet : ce qu'une ligne montre, comment les filtres la retiennent, et les
 * compteurs des puces. Pur, sans Compose, donc **mesurable** — c'est ici que vivent les règles, et
 * les écrans ne font que les dessiner.
 *
 * La disposition reprend celle de « la fenêtre de composition » : une tête avec la passerelle et son
 * voyant, une recherche, deux positions *Chats / Bots*, des tuiles vers les écrans de l'app, une
 * rangée de projets, une rangée de filtres, puis la liste. Les bots **quittent** la liste de
 * conversations : ils vivent dans la position *Bots*, avec les salons.
 */

/** Les deux positions du sélecteur du volet. */
enum class SidebarTab(val label: String) {
    CHATS("Chats"),
    BOTS("Bots"),
}

/**
 * Les trois filtres d'attention de la liste de conversations.
 *
 * Ce sont les puces « Tout / En cours / Vous attend » : chacune compte les lignes qu'elle retient,
 * et un filtre qui ne retient rien le dit par son compte (zéro), pas par une puce grisée.
 */
enum class SidebarFilter(val label: String) {
    ALL("All"),
    RUNNING("Running"),
    WAITING("Waiting for you"),
}

/**
 * Ce qu'une ligne de conversation exprime, du plus pressant au plus calme.
 *
 * Le ton commande la pastille de la ligne (son icône et sa couleur) et c'est lui que les filtres
 * interrogent — jamais le fil brut, qui ne sait rien de ce que le serveur a signalé.
 */
enum class SidebarRowTone { IDLE, RUNNING, WAITING, UNREAD }

/** Une conversation telle que la liste du volet la montre. */
data class SidebarRow(
    val id: String,
    val title: String,
    /** La seconde ligne : l'agent et le modèle, ou l'état. `null` : rien à dire. */
    val subtitle: String?,
    /** L'ancienneté en secondes, déjà calculée — l'écran ne relit pas d'horloge. */
    val age: Double,
    val tone: SidebarRowTone,
    /** La ligne est épinglée : elle passe en tête de liste. */
    val pinned: Boolean = false,
    /** Le titre du projet, quand la liste est filtrée sur un projet : la ligne le porte alors. */
    val projectLabel: String? = null,
    /**
     * **Le nom de profil du bot de cette conversation** — la clé d'identité de sa marque.
     *
     * C'est le nom de profil que la maison emploie partout pour résoudre un visage
     * ([com.onyx.app.ui.design.BotLook] : « le même bot a deux visages » venait d'un libellé passé
     * là où un profil était attendu). La marque du coin de la carte se résout donc sur ce nom, et
     * elle est **la même** dans la liste des bots, la fiche, l'en-tête et le volet.
     */
    val identity: String = "",
    /** Le visage enregistré de ce profil, s'il y en a un ([identity]). */
    val identityFace: String? = null,
)

/** Une puce de la rangée de projets : « All projects », puis chaque projet et son compte. */
data class SidebarProjectChip(
    val id: String,
    val label: String,
    /** Le nombre de conversations, ou `null` quand la puce n'en porte pas (« All projects »). */
    val count: Int?,
)

/**
 * Le **groupe** auquel une tuile appartient : les tuiles de la référence d'abord, dans son ordre,
 * puis celles qui sont propres à Onyx — deux blocs distincts dans la grille, jamais mêlés.
 */
enum class SidebarTileGroup { REFERENCE, ONYX }

/** Une tuile de la grille : l'icône d'un écran de l'app et son intitulé. */
data class SidebarTile(
    /** La route de l'app, en clair : c'est ce que l'appelant ouvre. */
    val id: String,
    val label: String,
    @androidx.annotation.DrawableRes val symbol: Int,
    val badge: Int = 0,
    /** Le groupe de la grille : la référence (défaut) ou Onyx. */
    val group: SidebarTileGroup = SidebarTileGroup.REFERENCE,
)

/** Un bot, tel que la grille de la position *Bots* le montre. */
data class SidebarBotCard(
    val name: String,
    val label: String,
    val description: String?,
    val ageLabel: String?,
    val tone: SidebarRowTone,
    val unread: Boolean = false,
    val pinned: Boolean = false,
)

/** Un salon, tel que la section *Salons* le montre : deux visages entrelacés, un nom, ses membres. */
data class SidebarRoomRow(
    val id: String,
    val name: String,
    /** Les **libellés** des membres, pour la ligne de texte (« Onyx, Scout ») — jamais pour le visage. */
    val members: List<String>,
    /**
     * Les **noms de profil** des membres, dans le même ordre : c'est sur eux que le visage se
     * résout ([InterlacedFaces]), sinon un membre (« Onyx », libellé) montrait le soleil ambre de
     * `BotFace.forName("Onyx")` là où son profil (« default ») donne la bulle bleue.
     */
    val memberProfiles: List<String> = emptyList(),
    val ageLabel: String?,
    val unread: Boolean = false,
)

/**
 * Le ton d'une conversation : ce que le serveur a signalé d'abord, puis ce que ce téléphone a vu.
 *
 * L'ordre compte. Un tour **en cours** prime sur tout le reste (rien ne se lit avant qu'il finisse) ;
 * une question de l'agent prime sur un simple non-lu (elle attend un geste, pas un regard) ; puis le
 * non-lu, calculé sur **la réponse de l'agent** — la marque de lecture d'une conversation où la
 * personne n'a pas encore parlé ne doit pas la dire « non lue à mon sujet ».
 */
internal fun sidebarToneOf(
    conversation: Conversation,
    kind: ActivityKind?,
    seenMark: Double?,
): SidebarRowTone = when {
    kind == ActivityKind.WORKING -> SidebarRowTone.RUNNING
    kind == ActivityKind.NEEDS_YOU -> SidebarRowTone.WAITING
    sidebarHasUnreadReply(conversation, seenMark) -> SidebarRowTone.UNREAD
    else -> SidebarRowTone.IDLE
}

/**
 * Vrai quand la dernière réponse de l'agent n'a pas encore été vue.
 *
 * La marque de lecture est en secondes, la date du message en millisecondes : la comparaison se fait
 * dans la même unité, sinon une ligne se dirait non lue à chaque ouverture (le défaut d'origine).
 * Une marque `0.0` est « marquée non lue à la main » : tout ce que l'agent a dit est neuf.
 */
internal fun sidebarHasUnreadReply(conversation: Conversation, seenMark: Double?): Boolean {
    val lastAgent = conversation.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return false
    val seen = seenMark ?: return false
    return lastAgent.createdAt / 1000.0 > seen
}

/** La conversation en une ligne de volet. */
internal fun sidebarRowOf(
    conversation: Conversation,
    kind: ActivityKind?,
    seenMark: Double?,
    now: Double,
    agentName: String,
    projectLabel: String? = null,
    /** Le nom de profil du bot de cette conversation : la clé de sa marque d'identité. */
    identity: String = agentName,
    /** Le visage enregistré de ce profil, quand il y en a un. */
    identityFace: String? = null,
): SidebarRow = SidebarRow(
    id = conversation.id,
    title = conversation.title,
    subtitle = sidebarSubtitle(conversation, kind, agentName),
    age = max(0.0, now - conversation.updatedAt / 1000.0),
    tone = sidebarToneOf(conversation, kind, seenMark),
    pinned = conversation.pinned,
    projectLabel = projectLabel,
    identity = identity,
    identityFace = identityFace,
)

/**
 * **L'état d'une ligne, tel que la marque d'identité le porte** (`IdentityMark.Presence`).
 *
 * Une ligne au repos n'a rien à dire : `null` — aucune pastille, aucun mensonge. Le repos est le
 * seul ton muet ; les trois autres se lisent sur la marque, qui est la même pour tous les écrans.
 */
internal fun sidebarPresenceOf(tone: SidebarRowTone): IdentityMark.Presence? = when (tone) {
    SidebarRowTone.RUNNING -> IdentityMark.Presence.RUNNING
    SidebarRowTone.WAITING -> IdentityMark.Presence.WAITING
    SidebarRowTone.UNREAD -> IdentityMark.Presence.UNREAD
    SidebarRowTone.IDLE -> null
}

/**
 * La liste du volet, telle qu'elle se construit : la recherche d'abord, puis le filtre d'attention,
 * et enfin l'ordre — les épinglées en tête, puis du plus récent au plus ancien.
 *
 * **Les bots n'entrent pas ici.** La fonction ne prend que des [Conversation] : un bot n'est pas une
 * conversation, il vit dans la position *Bots* du volet. C'est la séparation demandée, tenue par le
 * type et pas par une convention.
 */
internal fun sidebarRows(
    conversations: List<Conversation>,
    liveKinds: Map<String, ActivityKind>,
    seenMarks: Map<String, Double?>,
    now: Double,
    query: String = "",
    includeMessages: Boolean = true,
    filter: SidebarFilter = SidebarFilter.ALL,
    agentName: String = "",
    projectLabel: String? = null,
    /**
     * **L'identité d'une conversation** : le nom de profil de son bot, par identifiant de serveur.
     * Un serveur inconnu retombe sur le bot principal ([agentName]) — jamais une marque muette.
     */
    identityOf: (String) -> String = { agentName },
    /** Le visage enregistré d'un profil, quand il y en a un. */
    faceCodeOf: (String) -> String? = { null },
): List<SidebarRow> = conversations
    .filter { sidebarMatches(it, query, includeMessages) }
    .map { conversation ->
        val identity = identityOf(conversation.serverId)
        sidebarRowOf(
            conversation = conversation,
            kind = conversation.serverSessionId?.let { liveKinds[it] },
            seenMark = seenMarks[conversation.id],
            now = now,
            agentName = agentName,
            projectLabel = projectLabel,
            identity = identity,
            identityFace = faceCodeOf(identity),
        )
    }
    .filter { sidebarFilterKeeps(filter, it.tone) }
    .sortedWith(compareByDescending<SidebarRow> { it.pinned }.thenBy { it.age })

/**
 * La seconde ligne d'une conversation : son **état** quand le serveur en signale un, `null` sinon —
 * la ligne reste alors à une seule ligne, et rien n'est inventé.
 */
internal fun sidebarSubtitle(conversation: Conversation, kind: ActivityKind?, agentName: String): String? =
    when (kind) {
        ActivityKind.WORKING -> "Working"
        ActivityKind.NEEDS_YOU -> "Waiting for you"
        else -> null
    }

/** Le filtre retient-il une ligne de ce ton ? */
internal fun sidebarFilterKeeps(filter: SidebarFilter, tone: SidebarRowTone): Boolean = when (filter) {
    SidebarFilter.ALL -> true
    SidebarFilter.RUNNING -> tone == SidebarRowTone.RUNNING
    SidebarFilter.WAITING -> tone == SidebarRowTone.WAITING
}

/** Ce que chaque filtre retient, dans l'ordre des puces. */
internal fun sidebarFilterCounts(tones: List<SidebarRowTone>): Map<SidebarFilter, Int> =
    SidebarFilter.entries.associateWith { filter -> tones.count { sidebarFilterKeeps(filter, it) } }

/** Les puces de projets : « All projects » en tête, puis les projets que la liste montre. */
internal fun sidebarProjectChips(projects: List<ServerProject>): List<SidebarProjectChip> =
    listOf(SidebarProjectChip(ALL_PROJECTS_ID, "All projects", null)) +
        listedProjects(projects).map { SidebarProjectChip(it.id, it.displayLabel, it.sessionCount) }

/** L'identifiant de la puce « All projects » : aucun projet n'a cet identifiant. */
const val ALL_PROJECTS_ID = ""

/** La recherche du volet : le titre, et le texte des messages quand la portée l'inclut. */
internal fun sidebarMatches(
    conversation: Conversation,
    needle: String,
    includeMessages: Boolean,
): Boolean {
    if (needle.isBlank()) return true
    if (conversation.title.contains(needle, ignoreCase = true)) return true
    return includeMessages && conversation.messages.any { it.text.contains(needle, ignoreCase = true) }
}

/**
 * **Ce que la recherche interroge** — la phrase que le volet montre quand rien ne correspond.
 *
 * Elle est écrite ici, à côté de [sidebarMatches], parce que c'est la fonction qu'elle décrit :
 * `sidebarMatches` regarde **le titre** de la conversation et **le texte de ses messages**, jamais
 * l'identifiant de session ni le nom du serveur. La phrase de la référence, elle, promet en plus
 * « les identifiants de session » : recopiée telle quelle, elle aurait annoncé une chose que ce
 * code ne fait pas. `SidebarSearchTest` mesure les deux — la phrase **et** la fonction — pour qu'ils
 * ne puissent plus diverger.
 */
internal const val SearchScopeSentence: String =
    "Searches the titles of your conversations and the text of their messages."

/** Le titre de l'état vide de la recherche, quand une requête est écrite. */
internal const val SearchEmptyTitle: String = "No results"

/**
 * L'attente de la frappe : une requête ne part qu'une fois la frappe **arrêtée**, jamais à chaque
 * caractère. 250 ms est la valeur des champs de recherche de la maison (assez pour absorber une
 * rafale de frappe, trop court pour être perçu comme une lenteur).
 */
internal const val SearchDebounceMillis: Long = 250

/**
 * La requête **retenue** : on attend [SearchDebounceMillis], puis on rend la requête tapée.
 *
 * La fonction est suspendue et pure de tout Compose : la recomposition annule l'effet à chaque
 * frappe, donc seule la **dernière** requête atteint [onSettled]. Une requête vide se rend tout de
 * suite — effacer le texte n'attend pas, sinon la liste resterait filtrée un quart de seconde.
 */
internal suspend fun debouncedSearchQuery(query: String, onSettled: (String) -> Unit) {
    if (query.isEmpty()) {
        onSettled("")
        return
    }
    kotlinx.coroutines.delay(SearchDebounceMillis)
    onSettled(query)
}

/**
 * Le mot le plus long d'un libellé de tuile qui tient sur **une ligne**, mesuré.
 *
 * Le libellé d'une tuile ne se coupe **jamais au milieu d'un mot** (« Compétence » / « s ») : il
 * tient sur une ligne ou se replie **entre ses mots**. La grille met deux tuiles par rangée ; sur la
 * fenêtre la plus étroite que l'app sert (volet en surimpression à 360 dp), une tuile laisse environ
 * 92 dp à son libellé, et à `caption` (12 sp) et police **1,3×** le mot le plus long qui y entre
 * fait onze caractères. « Compétences » en fait onze ; « Fichiers du serveur » se replie entre ses
 * mots. `SidebarTileLabelTest` verrouille ce budget.
 */
internal const val TileLabelWordLimit: Int = 11


/**
 * L'ancienneté d'une ligne, courte comme la référence la montre : « maintenant », « 12m », « 1h »,
 * « 3d », « 2w ». Zéro ou négatif : la chaîne vide, et la ligne n'écrit rien.
 */
internal fun sidebarAgeLabel(ageSeconds: Double): String {
    if (ageSeconds <= 0.0) return ""
    val minutes = ageSeconds / 60.0
    val hours = minutes / 60.0
    val days = hours / 24.0
    return when {
        minutes < 1.0 -> "now"
        minutes < 60.0 -> "${minutes.toInt()}m"
        hours < 24.0 -> "${hours.toInt()}h"
        days < 7.0 -> "${days.toInt()}d"
        else -> "${(days / 7.0).toInt()}w"
    }
}

/**
 * **L'ancienneté de la copie enregistrée**, telle qu'une phrase la dit : « just now », « 12 min
 * ago », « 9 h ago », « 3 d ago », « 2 w ago » — et leurs équivalents français.
 *
 * C'est le pendant long de [sidebarAgeLabel] (qui écrit « 12m » sur une rangée) : une phrase se lit,
 * donc elle écrit ses unités en toutes lettres. `null` — aucune copie sur ce téléphone — rend la
 * chaîne vide, et la ligne se tait au lieu d'inventer une date.
 *
 * La langue est un **paramètre** : la phrase est ainsi vérifiable sans appareil et sans dépendre du
 * réglage de langue de la machine qui exécute les tests.
 */
internal fun savedCopyAgeLabel(
    ageSeconds: Double?,
    locale: Locale = OnyxLanguage.preferred,
): String {
    if (ageSeconds == null || ageSeconds < 0.0) return ""
    val minutes = ageSeconds / 60.0
    val hours = minutes / 60.0
    val days = hours / 24.0
    return when {
        minutes < 1.0 -> OnyxLocalization.translate("just now", locale)
        minutes < 60.0 -> OnyxLocalization.format("%d min ago", minutes.toInt(), locale = locale)
        hours < 24.0 -> OnyxLocalization.format("%d h ago", hours.toInt(), locale = locale)
        days < 7.0 -> OnyxLocalization.format("%d d ago", days.toInt(), locale = locale)
        else -> OnyxLocalization.format("%d w ago", (days / 7.0).toInt(), locale = locale)
    }
}

/**
 * **La ligne d'état d'une passerelle injoignable** : une phrase, et rien d'autre.
 *
 * C'est la demande du propriétaire : hors ligne, le volet dit **ce qu'il a** et **ce qui manque**,
 * dans une seule phrase grise — « Copie enregistrée · il y a 9 h. Le serveur est injoignable pour le
 * moment. » —, et le seul geste offert est un « Réessayer » discret. **Aucun code d'erreur**, aucun
 * bandeau rouge : le volet n'est pas un journal, c'est une liste qu'on peut encore lire.
 *
 * La fonction est **pure** à dessein : la phrase est vérifiable sans appareil, et elle ne peut pas
 * se mettre à contenir une exception, une pile d'appels ou un statut HTTP sans qu'un test échoue.
 * Elle est **traduite** (la phrase est assemblée ici, pas dans l'écran) parce que l'âge y est
 * variable : une clé unique ne saurait pas porter « il y a 9 h ».
 */
internal fun savedCopyNotice(
    ageSeconds: Double?,
    locale: Locale = OnyxLanguage.preferred,
): String {
    val age = savedCopyAgeLabel(ageSeconds, locale)
    val copy = if (age.isEmpty()) {
        OnyxLocalization.translate("Saved copy on this phone.", locale)
    } else {
        "${OnyxLocalization.translate("Saved copy", locale)} · $age."
    }
    return "$copy ${OnyxLocalization.translate("The server is unreachable for now.", locale)}"
}

/** Le libellé du bouton discret de la ligne d'état — un mot, jamais « Retry (HTTP 502) ». */
internal const val SAVED_COPY_RETRY_LABEL: String = "Try again"

/**
 * Les salons du roster en lignes de volet, du plus récent au plus ancien.
 */
internal fun sidebarRoomRows(
    rooms: List<SidebarRoomSource>,
    unreadRoomIds: Set<String>,
): List<SidebarRoomRow> = rooms
    .sortedByDescending { it.updatedAt ?: 0.0 }
    .map { room ->
        SidebarRoomRow(
            id = room.id,
            name = room.name,
            members = room.members,
            memberProfiles = room.memberProfiles,
            ageLabel = sidebarAgeLabel(room.ageSeconds).takeIf { it.isNotEmpty() },
            unread = room.id in unreadRoomIds,
        )
    }

/** Ce qu'un salon doit fournir pour devenir une ligne : le volet n'en sait pas plus. */
data class SidebarRoomSource(
    val id: String,
    val name: String,
    /** Les libellés des membres (le texte de la ligne). */
    val members: List<String>,
    /** Les noms de profil des membres, dans le même ordre (le visage de la pile). */
    val memberProfiles: List<String> = emptyList(),
    val updatedAt: Double? = null,
    val ageSeconds: Double = 0.0,
)

/**
 * Une encre **lisible sur son fond** : la couleur demandée, **écartée de son fond** juste assez
 * pour tenir [minRatio], dans la direction qui augmente le contraste — jamais vers le fond.
 *
 * Pourquoi cette fonction existe : `tertiaryLabel` du système mesure 1,7:1 en clair sur la page du
 * volet, et le vert du système 1,7:1 aussi. Un âge de conversation, une croix de recherche ou un
 * voyant d'état choisis « à l'œil » passent donc sous les seuils WCAG sur la plupart des thèmes —
 * mesuré, pas supposé (`SidebarContrastTest`). Ici l'encre est **mesurée puis corrigée** : la
 * couleur reste reconnaissable (elle bouge du plus petit pas qui suffit), et le seuil est tenu.
 *
 * [maxShift] borne la correction : une couleur qu'aucun déplacement ne sauverait est rendue au plus
 * près, plutôt que d'être tirée jusqu'au noir et de perdre son identité.
 */
internal fun sidebarInk(
    ink: androidx.compose.ui.graphics.Color,
    ground: androidx.compose.ui.graphics.Color,
    minRatio: Float,
    maxShift: Float = 0.75f,
): androidx.compose.ui.graphics.Color {
    val solid = com.onyx.app.ui.design.OnyxContrast.over(ink, ground)
    // Déjà lisible : on l'**atténue** le plus possible sans passer sous le seuil. C'est ce qui
    // distingue une seconde ligne, un âge ou une icône au repos d'un titre — une encre pleine
    // partout rendrait la liste plate, et un « gris » choisi à la main mesurerait 1,7:1.
    if (com.onyx.app.ui.design.OnyxContrast.ratio(solid, ground) >= minRatio) {
        return com.onyx.app.ui.design.OnyxContrast.dimmed(solid, ground, minRatio)
    }
    // Le fond clair appelle du noir, le fond sombre du blanc : on s'éloigne du fond.
    val towards = if (com.onyx.app.ui.design.OnyxContrast.luminance(ground) < 0.5f) {
        androidx.compose.ui.graphics.Color.White
    } else {
        androidx.compose.ui.graphics.Color.Black
    }
    val reach = maxShift.coerceIn(0f, 1f)
    fun at(t: Float) = androidx.compose.ui.graphics.lerp(solid, towards, t)
    if (com.onyx.app.ui.design.OnyxContrast.ratio(at(reach), ground) < minRatio) return at(reach)
    var notYet = 0f
    var enough = reach
    repeat(24) {
        val middle = (notYet + enough) / 2f
        if (com.onyx.app.ui.design.OnyxContrast.ratio(at(middle), ground) >= minRatio) enough = middle else notYet = middle
    }
    return at(enough)
}
