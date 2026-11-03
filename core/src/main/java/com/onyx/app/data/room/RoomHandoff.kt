package com.onyx.app.data.room

import java.util.Locale

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
