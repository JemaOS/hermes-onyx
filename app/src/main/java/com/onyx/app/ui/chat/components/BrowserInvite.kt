package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

    /**
     * **La clé est la conversation, pas la demande.**
     *
     * Refuser une demande précise ne servait à rien : le tour suivant en publiait une **neuve**,
     * avec un identifiant que rien ne reliait au refus, et la carte revenait dans le fil où l'on
     * venait de dire non. Le serveur retient maintenant le refus (par conversation et par page) ;
     * ce registre-ci fait de même, pour que l'écran n'ait pas à croire le serveur sur parole.
     *
     * Les deux clés sont gardées : celle de la conversation (le refus vaut pour la suite), et
     * celle de la demande (l'identifiant exact qu'on vient de refuser, avant même que le serveur
     * ait répondu). La première est celle qui règle le défaut signalé.
     */
    private fun bySession(serverId: String, session: String): String = "$serverId|session|$session"

    private fun byRequest(serverId: String, id: String): String = "$serverId|id|$id"

    @Synchronized
    fun refuse(serverId: String, session: String, id: String = "") {
        val now = System.currentTimeMillis()
        if (session.isNotBlank()) declined[bySession(serverId, session)] = now
        if (id.isNotBlank()) declined[byRequest(serverId, id)] = now
    }

    @Synchronized
    fun isRefused(serverId: String, session: String, id: String = ""): Boolean {
        val sessionKey = session.takeIf { it.isNotBlank() }?.let { bySession(serverId, it) }
        val requestKey = id.takeIf { it.isNotBlank() }?.let { byRequest(serverId, it) }
        return fresh(sessionKey) || fresh(requestKey)
    }

    private fun fresh(key: String?): Boolean {
        if (key == null) return false
        val at = declined[key] ?: return false
        if (System.currentTimeMillis() - at > TTL_MS) {
            declined.remove(key)
            return false
        }
        return true
    }

    /** Un fil qui change de session serveur n'hérite pas du refus d'un autre. */
    @Synchronized
    fun forgetSession(serverId: String, session: String) {
        if (session.isNotBlank()) declined.remove(bySession(serverId, session))
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
            // **Le refus se retient par conversation.** Une demande neuve — identifiant neuf,
            // même fil — ne doit pas rouvrir la question à laquelle on a déjà répondu non.
            val cleDeRefus = demande?.session.orEmpty().ifBlank { sessionId.orEmpty() }
            val dejaRefusee = demande != null &&
                TakeoverDeclines.isRefused(server.id, cleDeRefus, demande.id)
            // **La demande dit sa conversation.** Quand le serveur la publie, on la compare à
            // celle de ce fil — et c'est tout : aucune autre conversation ne l'affiche, et celle
            // où l'on a dit non ne la revoit pas. Sans ce champ (serveur plus ancien), on garde
            // l'ancien rattachement, qui ne montre la demande que dans un fil.
            val pourMoi = demande != null && when {
                demande.session.isNotBlank() && !sessionId.isNullOrBlank() -> demande.session == sessionId
                else -> TakeoverClaim.claim(demande.id, conversationId, live) == conversationId
            }
            val valide = demande != null && vivant && !dejaRefusee && pourMoi && demande.id != refusee
            if (valide) {
                val retenue = demande!!
                if (retenue.session.isBlank()) TakeoverClaim.retainOnly(setOf(retenue.id))
                attente = retenue
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
    ) {
        // **Deux boutons ne tiennent pas dans l'en-tête d'une carte.**
        //
        // L'action d'une carte est une place **à droite du titre** (`ContextCard`, règle 4). Deux
        // pastilles y prenaient toute la largeur : le titre tombait à quelques pixels, la carte
        // devenait illisible, et « Refuser » se lisait comme un reste de texte collé au bouton
        // d'accent — « on voit rien ». Une carte qui offre **deux** gestes les pose sous son texte,
        // sur toute la largeur : c'est déjà ce que fait la carte d'approbation (`ApprovalCard`),
        // et c'est exactement la même situation.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // **Refuser est un geste de premier rang, pas une croix discrète.** C'est la réponse
            // honnête quand on ne veut pas saisir son mot de passe sur un écran d'agent : elle
            // doit être aussi facile à atteindre que « Prendre la main ».
            //
            // **Et elle doit se voir.** Le libellé était peint avec la couleur par défaut du
            // style, qui sur cette carte se confondait avec le fond : on ne lisait « Refuser »
            // que comme une salissure, sans comprendre que c'était un bouton. Un fond d'encre à
            // quatorze pour cent et l'encre pleine — l'idiome du bouton bordé des cartes
            // d'approbation (`BorderedChoiceButton`) — disent qu'on peut appuyer, sans disputer
            // l'accent, qui reste le geste premier.
            //
            // La cible fait 48 dp, comme tous les contrôles de la maison : à 8 dp de marge pour
            // un texte de 13, le bouton tombait sous la cible minimale et le système l'élargissait
            // de lui-même, jusqu'à recouvrir son voisin.
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.textColor.copy(alpha = 0.14f))
                    .clickable {
                        val id = demande.id
                        refusee = id
                        attente = null
                        // Le refus tient pour toute l'application, et pour de bon : ni ce fil ni
                        // un autre ne repose la question. La clé est la conversation, pas la
                        // demande — le tour suivant en publiera une neuve, et c'est celle-là
                        // qu'il faut déjà savoir refusée.
                        TakeoverDeclines.refuse(
                            serverId = server.id,
                            session = demande.session.ifBlank { sessionId.orEmpty() },
                            id = id,
                        )
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
                    .heightIn(min = 48.dp)
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
    }
}