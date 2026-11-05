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

/**
 * **La demande de prise en main, vue par l'app et non par le fil.**
 *
 * La carte posée dans la conversation ne suffit pas : elle n'existe que si l'agent écrit son appel
 * d'outil **dans la session que l'app affiche**. Or l'agent peut travailler ailleurs — un autre
 * terminal, une autre session — et le serveur, lui, sait quand même qu'une prise en main attend.
 *
 * Cette bande interroge donc `onyx-browser/health` : tant qu'une demande est en attente et qu'un
 * navigateur vit, elle la montre au-dessus du champ, avec le seul geste utile. Elle disparaît
 * d'elle-même dès que la demande est satisfaite ou retirée — rien à fermer, rien à nettoyer.
 *
 * Le rythme est **lent par construction** : 20 s quand il n'y a rien (une requête d'état toutes les
 * vingt secondes ne coûte rien à personne), 8 s quand une demande existe — c'est le moment où l'on
 * veut que la disparition soit rapide.
 */
@Composable
fun BrowserInvite(
    server: ServerConfig,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onOpen == null || !server.isUsable) return
    val theme = LocalOnyxTheme.current

    var attente by remember(server.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(server.id, server.baseUrl, server.serveBaseUrl, server.apiKey) {
        while (true) {
            val sante = BrowserScreenApi.health(server)
            val demande = sante?.pending
            // Un navigateur mort ne peut rien montrer : afficher la bande enverrait la personne
            // vers un panneau vide, ce qui se lit comme une panne de l'app.
            attente = if (demande != null && sante?.browser == true) demande.reason else null
            delay(if (attente == null) 20_000L else 8_000L)
        }
    }

    val raison = attente ?: return

    ContextCard(
        modifier = modifier,
        state = ContextCardState.WAITING,
        title = OnyxLocalization["The agent's browser asks you to take the hand"],
        detail = raison.ifBlank {
            OnyxLocalization[
                "Sign in, solve a captcha or approve a 2FA, then hand it back: what you type goes to the page, never to the conversation."
            ]
        },
        onClick = onOpen,
        action = {
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
        },
    )
}
