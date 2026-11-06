package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.BrowserScreenApi
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxEdge
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **Les demandes qu'on a refusées, pour de bon.**
 *
 * Refuser était un geste sans suite : la carte tombait, puis le sondage suivant — ou le tour
 * d'après, quand on réécrivait un prompt — la faisait revenir, avec un identifiant neuf que rien
 * ne reliait au refus. La personne disait non, et l'app lui reposait la question.
 *
 * Ce registre est **de l'application, pas d'une conversation** : une demande refusée dans un fil
 * ne réapparaît ni dans celui-là ni dans les autres. Il vit en mémoire (une demande ne survit pas
 * à un redémarrage du serveur, donc un refus non plus) et s'oublie au bout d'une demi-heure.
 *
 * Le refus est aussi **retenu par le serveur** (`lease` → `decline`) : lui seul sait à quelle
 * conversation et à quelle page il s'applique, et c'est lui qui empêche l'agent de reposer la
 * même question au tour suivant. Ce registre-ci est le filet de l'écran : il empêche la carte de
 * clignoter entre l'appui et la réponse du serveur.
 */
internal object TakeoverDeclines {
    /** Une demi-heure : au-delà, la page a changé de camp et une demande redevient légitime. */
    private const val TTL_MS = 30 * 60 * 1000L

    private val declined = HashMap<String, Long>()

    private fun key(serverId: String, id: String): String = "$serverId|$id"

    @Synchronized
    fun refuse(serverId: String, id: String) {
        declined[key(serverId, id)] = System.currentTimeMillis()
    }

    @Synchronized
    fun isRefused(serverId: String, id: String): Boolean {
        val at = declined[key(serverId, id)] ?: return false
        if (System.currentTimeMillis() - at > TTL_MS) {
            declined.remove(key(serverId, id))
            return false
        }
        return true
    }
}

/**
 * **À quelle conversation appartient une demande de prise en main.**
 *
 * La demande appartient au *serveur* : `onyx-browser/health` la publie tant qu'un humain ne l'a
 * ni prise ni refusée. Le serveur dit maintenant **quelle conversation l'a demandée** (le champ
 * `session`), et c'est cette réponse-là qui décide — plus une devinette.
 *
 * Ce registre ne sert donc qu'aux serveurs plus anciens, qui ne publient pas la conversation : la
 * demande est alors rattachée à **une** conversation, et à elle seule. La vivante passe devant une
 * fois ; ensuite elle ne se fait plus dépouiller.
 */
internal object TakeoverClaim {
    /** Propriétaire d'une demande, et s'il conduisait un tour au moment du rattachement. */
    private data class Owner(val conversationId: String, val live: Boolean)

    private val owners = HashMap<String, Owner>()

    /** Qui possède cette demande, ou `null` si personne ne l'a encore vue. */
    @Synchronized
    fun ownerOf(id: String): String? = owners[id]?.conversationId

    /**
     * Rattache la demande à [conversationId].
     *
     * **La conversation vivante passe devant, une fois.** Si une conversation à l'arrêt a vu la
     * demande la première et qu'ensuite celle dont le tour tourne se présente, c'est elle qui la
     * prend : c'est elle qui a demandé la main. Mais une conversation vivante ne se fait plus
     * dépouiller ensuite — sinon la carte sauterait d'un fil à l'autre à chaque recomposition, ce
     * qui est exactement le défaut qu'on répare.
     */
    @Synchronized
    fun claim(id: String, conversationId: String, live: Boolean): String {
        val actuel = owners[id]
        if (actuel != null && (actuel.conversationId == conversationId || actuel.live || !live)) {
            return actuel.conversationId
        }
        owners[id] = Owner(conversationId, live)
        return conversationId
    }

    /** Oublie une demande (refusée, ou satisfaite). */
    @Synchronized
    fun forget(id: String) {
        owners.remove(id)
    }

    /** Ne garde que les demandes encore en attente : le registre reste minuscule. */
    @Synchronized
    fun retainOnly(present: Set<String>) {
        owners.keys.retainAll(present)
    }
}

/**
 * **La demande de prise en main, vue par l'app et non par le fil.**
 *
 * La carte posée dans la conversation ne suffit pas : elle n'existe que si l'agent écrit son appel
 * d'outil **dans la session que l'app affiche**. Or l'agent peut travailler ailleurs — un autre
 * terminal, une autre session — et le serveur, lui, sait quand même qu'une prise en main attend.
 *
 * Cette bande interroge donc `onyx-browser/health` : tant qu'une demande est en attente et qu'un
 * navigateur vit, elle la montre **dans la seule conversation à qui elle appartient**, avec les
 * deux gestes utiles : prendre la main, ou **refuser**.
 *
 * Refuser n'est pas ignorer. Ignorer laissait l'agent attendre un quart d'heure et la carte
 * s'incruster ; refuser le lui dit tout de suite, et il reprend son tour en sachant à quoi s'en
 * tenir. Un refus ne se repose pas : le serveur le retient pour cette conversation et cette page,
 * et l'écran garde le sien (voir [TakeoverDeclines]).
 *
 * Le rythme est **lent par construction** : 20 s quand il n'y a rien (une requête d'état toutes les
 * vingt secondes ne coûte rien à personne), 8 s quand une demande existe — c'est le moment où l'on
 * veut que la disparition soit rapide.
 *
 * @param sessionId la session serveur de cette conversation (`state.serverSessionId`) : c'est elle
 *        qui dit si la demande est bien la nôtre. `null` quand le fil n'est pas encore rattaché à
 *        une session — on retombe alors sur le rattachement par conversation vivante.
 */
@Composable
fun BrowserInvite(
    server: ServerConfig,
    conversationId: String,
    live: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
) {
    if (onOpen == null || !server.isUsable) return
    val theme = LocalOnyxTheme.current
    val scope = rememberCoroutineScope()

    var attente by remember(server.id) { mutableStateOf<BrowserScreenApi.PendingRequest?>(null) }
    // La demande qu'on vient de refuser : la carte tombe tout de suite, sans attendre que le
    // serveur ait fini de la retirer (et sans qu'elle revienne au sondage suivant si le réseau
    // traîne).
    var refusee by remember(server.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        server.id,
        server.baseUrl,
        server.serveBaseUrl,
        server.apiKey,
        conversationId,
        sessionId,
        live,
    ) {
        while (true) {
            val sante = BrowserScreenApi.health(server)
            val demande = sante?.pending
            // Un navigateur mort ne peut rien montrer : afficher la bande enverrait la personne
            // vers un panneau vide, ce qui se lit comme une panne de l'app.
            val vivant = sante?.browser == true
            val dejaRefusee = demande != null && TakeoverDeclines.isRefused(server.id, demande.id)
            // **La demande dit sa conversation.** Quand le serveur la publie, on la compare à
            // celle de ce fil — et c'est tout : aucune autre conversation ne l'affiche, et celle
            // où l'on a dit non ne la revoit pas. Sans ce champ (serveur plus ancien), on garde
            // l'ancien rattachement, qui ne montre la demande que dans un fil.
            val pourMoi = demande != null && when {
                demande.session.isNotBlank() && !sessionId.isNullOrBlank() -> demande.session == sessionId
                else -> TakeoverClaim.claim(demande.id, conversationId, live) == conversationId
            }
            val valide = demande != null && vivant && !dejaRefusee && pourMoi && demande.id != refusee
            if (valide && demande != null) {
                if (demande.session.isBlank()) TakeoverClaim.retainOnly(setOf(demande.id))
                attente = demande
            } else {
                attente = null
                if (demande == null) {
                    // Plus rien en attente : le registre se vide, et le refus local s'efface pour
                    // la demande suivante — celle-ci aura un identifiant neuf, et le serveur dira
                    // s'il faut la montrer.
                    TakeoverClaim.retainOnly(emptySet())
                    refusee = null
                }
            }
            delay(if (attente == null) 20_000L else 8_000L)
        }
    }

    val demande = attente ?: return

    ContextCard(
        modifier = modifier,
        state = ContextCardState.WAITING,
        title = OnyxLocalization["The agent's browser asks you to take the hand"],
        detail = demande.reason.ifBlank {
            OnyxLocalization[
                "Sign in, solve a captcha or approve a 2FA, then hand it back: what you type goes to the page, never to the conversation."
            ]
        },
        onClick = onOpen,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // **Refuser est un geste de premier rang, pas une croix discrète.** C'est la
                // réponse honnête quand on ne veut pas saisir son mot de passe sur un écran
                // d'agent : elle doit être aussi facile à atteindre que « Prendre la main ».
                //
                // Et elle doit **se voir**. Le texte était peint avec la couleur par défaut du
                // style, qui sur cette carte se confondait avec le fond : on lisait « Refuser »
                // comme une salissure à côté du bouton d'accent, sans comprendre que c'était un
                // bouton. Un contour et l'encre du texte disent qu'on peut appuyer — le contour
                // est **le filet de la maison** (`OnyxEdge.fil`), jamais une encre de texte prise
                // pour une bordure : c'est le verrou du trait, et il a raison.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(
                            width = 1.dp,
                            color = OnyxEdge.fil(theme),
                            shape = RoundedCornerShape(50),
                        )
                        .clickable {
                            val id = demande.id
                            refusee = id
                            attente = null
                            // Le refus tient pour toute l'application, et pour de bon : ni ce
                            // fil ni un autre ne repose la question.
                            TakeoverDeclines.refuse(server.id, id)
                            TakeoverClaim.forget(id)
                            scope.launch { BrowserScreenApi.decline(server) }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = OnyxLocalization["Decline"],
                        localize = false,
                        style = theme.caption.copy(
                            color = theme.textColor,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(theme.accent)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        text = OnyxLocalization["Take over"],
                        localize = false,
                        style = theme.caption.copy(
                            color = ReferencePalette.OnAccent,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        },
    )
}