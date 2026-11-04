package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.ui.rich.MarkdownBlock
import com.onyx.app.ui.rich.splitMarkdown
import android.text.Spanned

/**
 * **Le travail d'une rangée, gardé entre deux passages dans la fenêtre de la liste.**
 *
 * Une `LazyColumn` ne garde pas ses rangées : celle qui sort de l'écran est **jetée**, et celle qui
 * y rentre est **recomposée de zéro**. Tous les `remember` de la rangée repartent donc à vide — et
 * c'est exactement ce qui se paie en faisant défiler un fil chargé :
 *
 *  - `splitMarkdown` re-découpe le texte entier à chaque entrée ;
 *  - `Markwon.setMarkdown` re-analyse et re-pose les spans de chaque paragraphe ;
 *  - `MessageDerived` repasse `looksLikePatch` / `DeliveredFiles` sur chaque sortie d'outil
 *    (12 000 caractères × N outils) ;
 *  - `LinkPreviewLinks.first` repasse son expression régulière sur la réponse.
 *
 * Ces quatre résultats ne dépendent **que du contenu**. Ce fichier les garde donc dans de petits
 * caches LRU, bornés : la mémoire ne suit pas la longueur de l'historique, et une rangée qui
 * revient à l'écran retrouve son travail au lieu de le refaire.
 *
 * Les clés sont **le contenu lui-même** (identifiant + empreinte du texte et des outils), jamais la
 * position : deux rangées identiques ne se marchent pas dessus, et une réponse qui change de texte
 * ne relit pas l'ancienne.
 */
internal object RowCaches {

    /** Assez pour l'écran et ses voisines, jamais assez pour retenir un historique entier. */
    private const val DerivedEntries = 64
    private const val BlockEntries = 24
    private const val RenderedEntries = 16
    private const val LinkEntries = 64

    private class Lru<K, V>(private val max: Int) : LinkedHashMap<K, V>(max, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > max
    }

    private val derived = Lru<String, MessageDerived>(DerivedEntries)
    private val blocks = Lru<String, List<MarkdownBlock>>(BlockEntries)
    private val rendered = Lru<String, Spanned>(RenderedEntries)
    private val links = Lru<String, String?>(LinkEntries)

    /**
     * L'empreinte du contenu d'une rangée.
     *
     * Volontairement **courte à calculer** : elle ne lit ni le texte ni les sorties, seulement leurs
     * longueurs et l'état des outils. Une sortie qui arrive change de longueur et de statut ; un
     * texte qui s'écrit change de longueur. Les deux invalident donc l'entrée — sans relire un
     * demi-mégaoctet pour le savoir.
     */
    internal fun fingerprint(message: ChatMessage, includeText: Boolean): String {
        val builder = StringBuilder(64 + message.tools.size * 24)
        builder.append(message.id).append('|').append(message.tools.size)
        for (tool in message.tools) {
            builder.append('|').append(tool.id)
                .append(':').append(tool.status.name)
                .append(':').append(tool.endedAt ?: -1L)
                .append(':').append(tool.output?.length ?: -1)
        }
        if (includeText) {
            builder.append('|').append(message.text.length).append(':').append(message.text.hashCode())
        }
        return builder.toString()
    }

    /** Ce que la rangée déduit de son contenu, calculé une fois par contenu. */
    fun derived(message: ChatMessage, includeText: Boolean): MessageDerived {
        val key = fingerprint(message, includeText)
        synchronized(derived) { derived[key] }?.let { return it }
        // Hors du verrou : le calcul peut être long, et deux rangées ne doivent pas s'attendre.
        val computed = MessageDerived.of(message, includeText)
        synchronized(derived) { derived[key] = computed }
        return computed
    }

    /** Le découpage Markdown d'un texte **fini** (`isStreaming` passe par le calcul direct). */
    fun blocks(markdown: String): List<MarkdownBlock> {
        synchronized(blocks) { blocks[markdown] }?.let { return it }
        val computed = runCatching { splitMarkdown(markdown) }
            .getOrElse { listOf(MarkdownBlock(prose = markdown)) }
        synchronized(blocks) { blocks[markdown] = computed }
        return computed
    }

    /** La prose Markwon déjà analysée, spans compris, pour un style donné. */
    fun rendered(styleKey: String, markdown: String): Spanned? =
        synchronized(rendered) { rendered[styleKey + '\u0000' + markdown] }

    fun rememberRendered(styleKey: String, markdown: String, spanned: Spanned) {
        synchronized(rendered) { rendered[styleKey + '\u0000' + markdown] = spanned }
    }

    /** L'adresse de la fiche de lien d'un texte fini, ou `null` (et ce `null` est gardé aussi). */
    fun link(text: String, compute: () -> String?): String? {
        synchronized(links) {
            if (links.containsKey(text)) return links[text]
        }
        val value = compute()
        synchronized(links) { links[text] = value }
        return value
    }
}
