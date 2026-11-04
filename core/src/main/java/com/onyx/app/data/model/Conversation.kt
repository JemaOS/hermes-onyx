package com.onyx.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Une conversation enregistrée.
 *
 * Miroir de l'`Envelope` de `ConversationStore` : un identifiant, un titre, les dates qui
 * ordonnent la liste, le serveur d'origine et le fil tel qu'il était.
 */
@Serializable
data class Conversation(
    val id: String,
    /** Le titre, pris du premier message de l'utilisateur. « New Conversation » avant ça. */
    val title: String = DefaultTitle,
    val createdAt: Long,
    val updatedAt: Long,
    /** Serveur avec qui elle a été écrite : la liste s'y rapporte. */
    val serverId: String = "",
    /** Session côté gateway, quand le transport en tient une. */
    val serverSessionId: String? = null,
    /**
     * Conversation **du serveur**, et non de ce téléphone : elle n'est qu'une projection de la
     * session listée par le registre du serveur. Le fil se lit à l'ouverture ; rien n'est
     * écrit ici. Une telle conversation n'est pas persistée comme une conversation locale —
     * le serveur en reste la seule source.
     */
    val remote: Boolean = false,
    /** Épinglée en tête de liste. */
    val pinned: Boolean = false,
    /** Rangée dans les archives. */
    val archived: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    /**
     * Les messages non envoyés survivent à la fermeture et à la réouverture de la conversation.
     */
    val outbox: List<OutboxItem> = emptyList(),
    /**
     * **Le bot de cette conversation, gardé avec elle.**
     *
     * Le bot se déduit du roster (`botOfConversation`) — mais le roster vient du réseau, et au
     * redémarrage il arrive après l'écran : la conversation s'affichait alors en Onyx (visage bleu,
     * « @default ») le temps d'une ou deux frames, puis sautait sur le vrai bot.
     *
     * Ce champ est ce que l'app a **déjà su** : le profil du bot la dernière fois qu'elle l'a
     * résolu. `null` = conversation simple (pas de bot) ; `""` = le profil principal (celui qui
     * porte la bulle de Onyx) ; sinon le nom du profil. Il ne remplace jamais le roster : il tient
     * l'écran juste jusqu'à ce qu'il arrive.
     */
    val botProfile: String? = null,
) {
    /** Le nombre de tours, c'est-à-dire de messages de l'utilisateur. */
    val turnCount: Int get() = messages.count { it.role == Role.USER }

    val isEmpty: Boolean get() = messages.isEmpty() && outbox.isEmpty()

    /**
     * Le nom donné à la conversation par « Renommer », ou `null` quand le titre n'est encore que
     * la première question (ou « New Conversation »). L'en-tête du chat préfère ce nom au premier
     * message, comme `Conversation.name` de la référence.
     */
    fun customName(): String? {
        val first = messages.firstOrNull { it.role == Role.USER }?.text
        val derived = first?.let { titleFrom(it) }
        return title.takeIf { it != DefaultTitle && it != derived }
    }

    /**
     * Le nom **posé** qui rattache la conversation à un bot : le titre, moins le suffixe de
     * branche.
     *
     * Une branche (« X (fork) ») est la **copie** d'une conversation : elle porte le nom de son
     * originale (« coder »), et c'est ce nom qui la rattache encore à son bot. C'est le lien
     * durable — celui qui tient quand le roster ne nomme aucun `canonical_session` pour ce bot.
     * Le suffixe ne change que l'affichage ; l'identité, elle, reste celle de l'originale.
     *
     * `null` quand le titre n'est que la première question (comme [customName]) : un titre
     * automatique ne lie à personne.
     */
    fun botName(): String? {
        var base = title
        while (base.length > ForkSuffix.length && base.endsWith(ForkSuffix)) {
            base = base.dropLast(ForkSuffix.length)
        }
        if (base.isBlank()) return null
        val first = messages.firstOrNull { it.role == Role.USER }?.text
        val derived = first?.let { titleFrom(it) }
        return base.takeIf { it != DefaultTitle && it != derived }
    }

    fun withMessage(message: ChatMessage, now: Long): Conversation = copy(
        messages = messages + message,
        updatedAt = now,
        title = if (title == DefaultTitle && message.role == Role.USER) titleFrom(message.text) else title,
    )

    companion object {
        const val DefaultTitle = "New Conversation"

        /**
         * Le suffixe du titre d'une **branche** — « (fork) », comme la référence.
         *
         * Il ne dit que l'affichage : la branche est la copie d'une conversation, et elle garde
         * l'identité de son originale (son bot, donc son visage et son profil).
         */
        const val ForkSuffix = " (fork)"

        /** Le titre d'une conversation : la première ligne du premier message, coupée court. */
        fun titleFrom(text: String): String {
            val firstLine = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isEmpty()) return DefaultTitle
            return if (firstLine.length <= 42) firstLine else firstLine.take(41).trimEnd() + "…"
        }

        fun start(id: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()): Conversation =
            Conversation(id = id, createdAt = now, updatedAt = now)
    }
}
