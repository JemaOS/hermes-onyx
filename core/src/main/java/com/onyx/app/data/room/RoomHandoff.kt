package com.onyx.app.data.room

import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.data.model.serve.GroupMember
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * **Le passage de main, version client** — celle qui marche sur un Hermes de base.
 *
 * Le Hermes d'un utilisateur n'a pas de route `groups.handoff` : sa room est un fil où l'on
 * s'adresse à des bots. Onyx n'a donc pas besoin qu'il en ait une pour rendre le geste utile.
 *
 * La convention est celle que les gens écrivent déjà : dans une room, le bot qui coordonne
 * répond en nommant celui qui prend la suite — « @Coder prend la suite, c'est du Kotlin ».
 * Onyx lit cette mention, la montre comme un passage de main, et **change le destinataire par
 * défaut** de la room. Rien n'est inventé côté serveur, rien n'est envoyé nulle part.
 *
 * Version serveur (plus tard, si Hermes la fournit) : le meneur devient un état partagé entre
 * appareils au lieu d'un réglage local. La forme lue ici ne change pas — un `@membre` reste un
 * `@membre`.
 */
data class RoomHandoff(
    /** Le membre qui prend la main, **écrit comme dans la room** (jamais en minuscules). */
    val to: String,
    /** La phrase qui porte la mention, bornée — ce que la ligne de journal affiche. */
    val sentence: String,
)

/**
 * Une mention `@nom` qui désigne un membre.
 *
 * Le look-behind interdit ce qui précède une adresse : dans `jean@exemple.fr`, `exemple` n'est
 * pas une mention. Une mention peut contenir des lettres, des chiffres, `-` et `_` — jamais un
 * point, qui appartient aux adresses.
 */
private val MENTION = Regex("(?<![A-Za-z0-9_.-])@([A-Za-z0-9][A-Za-z0-9_-]{0,63})")

/** Un bloc de code n'est pas une consigne : on le retire avant de lire. */
private val FENCED_CODE = Regex("```.*?```", RegexOption.DOT_MATCHES_ALL)

/** Même raison pour le code en ligne. */
private val INLINE_CODE = Regex("`[^`]*`")

/** Au-delà, la ligne de journal devient un paragraphe : on la coupe. */
private const val SENTENCE_LIMIT = 240

/**
 * Le passage de main proposé par [answer], ou `null` s'il n'y en a pas.
 *
 * Règles, dans cet ordre :
 * 1. Ce qui est dans du code ne compte pas.
 * 2. Une mention qui **est** l'auteur ne compte pas (un bot qui se cite ne se passe pas la main).
 * 3. Une mention qui ne désigne **aucun membre** de la room ne compte pas — pas de destinataire
 *    fantôme, on continue de chercher.
 * 4. La première mention valide gagne, et elle est rendue **telle que la room écrit ce membre**
 *    (insensible à la casse : `@coder` rend `Coder`).
 *
 * @param members les profils de la room — c'est leur écriture qui fait foi.
 * @param from le profil qui répond ; `""` si l'auteur est inconnu (le bot principal, par exemple).
 */
fun roomHandoffProposal(answer: String, from: String, members: List<String>): RoomHandoff? {
    if (answer.isBlank() || members.isEmpty()) return null

    val visible = INLINE_CODE.replace(FENCED_CODE.replace(answer, " "), " ")
    val canonical = LinkedHashMap<String, String>()
    members.forEach { member -> canonical.putIfAbsent(member.lowercase(Locale.ROOT), member) }

    for (match in MENTION.findAll(visible)) {
        val named = match.groupValues[1]
        if (from.isNotBlank() && named.equals(from, ignoreCase = true)) continue
        val member = canonical[named.lowercase(Locale.ROOT)] ?: continue
        return RoomHandoff(to = member, sentence = sentenceAround(visible, match.range))
    }
    return null
}

/**
 * La phrase qui porte la mention : du point (ou du début) au point suivant.
 *
 * Une phrase vide ne serait pas une phrase — on rend alors la mention seule plutôt qu'une ligne
 * muette, parce qu'un passage de main sans phrase reste un passage de main.
 */
private fun sentenceAround(text: String, range: IntRange): String {
    var start = range.first
    while (start > 0 && text[start - 1] !in ".!?\n") start--
    var end = range.last + 1
    while (end < text.length && text[end] !in ".!?\n") end++
    val sentence = text.substring(start, end).trim().trim('.', '!', '?', ' ').replace(Regex("\\s+"), " ")
    if (sentence.isEmpty()) return text.substring(range.first, range.last + 1)
    return if (sentence.length <= SENTENCE_LIMIT) sentence else sentence.take(SENTENCE_LIMIT).trimEnd() + "…"
}

/** La première mention `@nom` de [text], hors code — celle que la personne a écrite. `null` sinon. */
fun roomMentioned(text: String): String? {
    val visible = INLINE_CODE.replace(FENCED_CODE.replace(text, " "), " ")
    return MENTION.find(visible)?.groupValues?.get(1)
}

/**
 * Le texte tel qu'il **part** dans une salle où [to] est le meneur.
 *
 * Le meneur n'est pas un chef : c'est un **destinataire par défaut**. Il n'y a donc rien de nouveau
 * sur le fil — une salle s'adresse à quelqu'un en l'écrivant, et la mention est déjà la forme que la
 * personne emploie. Deux règles, dans cet ordre :
 *
 * 1. une mention écrite par la personne (`@X`) **prime** : le texte est rendu tel quel ;
 * 2. sans mention et avec un meneur, le texte est adressé à lui (`@sa-poignée ` devant), ce qui est
 *    exactement ce que la personne aurait tapé pour l'adresser.
 *
 * Sans meneur — ou si [to] ne désigne aucun membre — le texte est rendu **inchangé** : c'est le
 * comportement d'une salle qui n'a pas de meneur, et il ne bouge pas.
 */
fun roomAddressed(text: String, to: String?, members: List<GroupMember>): String {
    val target = members.firstOrNull { it.profile == to } ?: return text
    if (text.isBlank() || roomMentioned(text) != null) return text
    val handle = target.handle.ifBlank { target.profile }
    return "@" + handle + " " + text
}

/** La sorte d'événement qu'une ligne de passage de main porte dans le journal de la salle. */
const val ROOM_HANDOFF_KIND = "room.handoff"

/**
 * **Un passage de main lu dans le journal** : une ligne, une seule, à la place de la réponse qui le
 * porte.
 *
 * [seq] est celui de cette réponse — c'est sa **place** dans le journal, et sa **clé** : deux
 * lectures du même journal rendent donc la même ligne, au même endroit, et elle ne peut pas entrer
 * deux fois. La ligne ne réordonne rien : les rangées existantes gardent leur ordre, la ligne
 * s'ajoute après la réponse qui la porte.
 */
data class RoomHandoffLine(
    val seq: Int,
    /** L'heure de la réponse qui porte le passage — la ligne se date comme elle. */
    val atMillis: Long,
    /** Le profil du membre qui passe la main. */
    val from: String,
    /** Le profil du membre qui la prend — écrit comme la room l'écrit. */
    val to: String,
    /** La phrase qui porte la mention (bornée par [roomHandoffProposal]). */
    val sentence: String,
) {
    /**
     * La ligne telle que le journal la dessine : la même liste (`GroupEvent`), le même ordre, la
     * même clé — donc aucun second rendu possible.
     */
    fun asEvent(): GroupEvent = GroupEvent(
        seq = seq,
        kind = ROOM_HANDOFF_KIND,
        actorKind = "member",
        actorID = from,
        payload = buildJsonObject {
            put("from", from)
            put("to", to)
            put("sentence", sentence)
        },
        createdAtMillis = atMillis,
    )
}

/**
 * **Les passages de main d'un journal**, dans l'ordre où la salle les a dits.
 *
 * Le dernier passage est le destinataire par défaut de la suite ; c'est aussi ce que la ligne
 * affiche. Deux règles :
 *
 * 1. seules les **réponses** comptent (`message.member`) : une note de tour, un message de la
 *    personne, un renommage ne passent pas la main ;
 * 2. un passage **vers celui qui a déjà la main** n'en est pas un nouveau — deux réponses
 *    successives qui nomment le même `@membre` n'ajoutent qu'une ligne, parce que le destinataire
 *    n'a pas changé.
 *
 * @param members les profils de la salle : leur écriture fait foi, par la même clé que le fil
 *   (`id` de l'événement → `profile` du membre).
 */
fun roomHandoffs(events: List<GroupEvent>, members: List<GroupMember>): List<RoomHandoffLine> {
    if (members.isEmpty()) return emptyList()
    val profiles = members.map { it.profile }
    val lines = mutableListOf<RoomHandoffLine>()
    var recipient: String? = null
    for (event in events) {
        if (event.kind != "message.member") continue
        // L'auteur par sa clé de membre (`id`), comme le fil ; une copie Desktop écrit le profil
        // lui-même — on le lit donc aussi, plutôt que de perdre le passage.
        val member = members.firstOrNull { it.id == event.actorID }
            ?: members.firstOrNull { it.profile == event.actorID }
            ?: continue
        val proposal = roomHandoffProposal(event.text, from = member.profile, members = profiles) ?: continue
        // Le destinataire n'a pas changé : ce n'est pas un nouveau passage.
        if (proposal.to == recipient) continue
        recipient = proposal.to
        lines += RoomHandoffLine(
            seq = event.seq,
            atMillis = event.createdAtMillis,
            from = member.profile,
            to = proposal.to,
            sentence = proposal.sentence,
        )
    }
    return lines
}
