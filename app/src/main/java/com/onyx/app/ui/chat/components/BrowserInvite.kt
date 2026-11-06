package com.onyx.app.ui.chat.components

import androidx.compose.foundation.background
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
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ReferencePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **À quelle conversation appartient une demande de prise en main.**
 *
 * La demande, elle, appartient au *serveur* : `onyx-browser/health` la publie tant qu'un humain ne
 * l'a ni prise ni refusée. L'app la lisait comme un état global, donc **chaque conversation ouverte
 * affichait la même bande** — on quittait le fil où l'agent avait demandé de l'aide, et l'invitation
 * suivait dans les autres fils alors qu'on n'avait rien fait. C'était le défaut signalé.
 *
 * Ici la demande est rattachée à **une** conversation, et à elle seule : les autres fils ne
 * l'affichent plus. La conversation **vivante** (celle dont le tour tourne, donc celle qui a demandé)
 * passe devant ; à défaut, la première à la voir. Une fois refusée, la demande est oubliée.
 *
 * Le registre vit en mémoire et se vide des demandes disparues : une carte en cours, jamais un
 * historique.
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
 * navigateur vit, elle la montre **dans la seule conversation à qui elle appartient** (voir
 * [TakeoverClaim]), avec les deux gestes utiles : prendre la main, ou **refuser**.
 *
 * Refuser n'est pas ignorer. Ignorer laissait l'agent attendre un quart d'heure et la carte
 * s'incruster ; refuser le lui dit tout de suite, et il reprend son tour en sachant à quoi s'en
 * tenir. La carte tombe dans la seconde, sans attendre le prochain sondage.
 *
 * Le rythme est **lent par construction** : 20 s quand il n'y a rien (une requête d'état toutes les
 * vingt secondes ne coûte rien à personne), 8 s quand une demande existe — c'est le moment où l'on
 * veut que la disparition soit rapide.
 */
@Composable
fun BrowserInvite(
    server: ServerConfig,
    conversationId: String,
    live: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
        live,
    ) {
        while (true) {
            val sante = BrowserScreenApi.health(server)
            val demande = sante?.pending
            // Un navigateur mort ne peut rien montrer : afficher la bande enverrait la personne
            // vers un panneau vide, ce qui se lit comme une panne de l'app.
            val valide = demande != null && sante?.browser == true && demande.id != refusee
            if (valide) {
                TakeoverClaim.retainOnly(setOf(demande.id))
                val proprietaire = TakeoverClaim.claim(demande.id, conversationId, live)
                attente = demande.takeIf { proprietaire == conversationId }
            } else {
                TakeoverClaim.retainOnly(emptySet())
                attente = null
                if (demande == null) refusee = null
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
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            val id = demande.id
                            refusee = id
                            attente = null
                            TakeoverClaim.forget(id)
                            scope.launch { BrowserScreenApi.decline(server) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = OnyxLocalization["Decline"],
                        localize = false,
                        style = theme.caption.copy(fontWeight = FontWeight.Medium),
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
