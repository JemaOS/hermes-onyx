package com.onyx.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **L'attente d'un écran qui lit le serveur : des rangées grises, pas un tourniquet plein écran.**
 *
 * L'audit des routes (T5) a relevé la même faute sur une quinzaine d'écrans : pendant la lecture,
 * ils n'affichaient **rien**, si bien qu'un serveur lent donnait un écran qui semblait vide — et
 * l'état vide, lui, a un sens bien précis qu'un « rien » ne doit pas usurper. Le remède retenu par
 * l'audit est **un seul composant**, posé par chaque écran concerné : c'est celui-ci, et pas un
 * second système d'état.
 *
 * Pourquoi un squelette et non le `OnyxProgressView` de la maison : sur une **liste**, un
 * tourniquet tourne au milieu du vide et ne dit pas *ce qui* arrive. Trois barres grises à la
 * place des rangées disent la même chose sans mentir sur la forme de ce qui vient, et elles ne
 * bougent pas — donc « Réduire les animations » n'a rien à figer ici, et rien ne clignote.
 *
 * [label] dit **ce qu'on attend** (« Chargement depuis Onyx… », « Chargement des modèles… »), dans
 * la langue de l'app : c'est la même phrase que les écrans portaient déjà, maintenant sur des
 * rangées plutôt que seule sur une ligne.
 */
@Composable
fun OnyxLoadingRows(
    label: String,
    modifier: Modifier = Modifier,
    /** Le nombre de rangées d'attente : trois suffisent à dire « une liste arrive ». */
    rows: Int = 3,
) {
    val theme = LocalOnyxTheme.current
    OnyxFormSection(modifier = modifier) {
        OnyxRow {
            BasicText(
                text = label,
                style = theme.footnote.copy(color = theme.secondaryText),
                // Lu par les lecteurs d'écran quand il arrive, comme le `liveRegion` des autres
                // attentes de l'app.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        repeat(rows) { index ->
            OnyxRowDivider()
            OnyxRow {
                // La largeur varie d'une rangée à l'autre (comme des noms de longueurs
                // différentes) : une pile de barres égales se lit comme un ornement, pas comme une
                // liste qui arrive.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(SkeletonWidths[index % SkeletonWidths.size])
                        .height(11.dp)
                        .background(theme.fill, RoundedCornerShape(OnyxSpace.Hair)),
                )
            }
        }
    }
}

/** Les trois largeurs du squelette — des parts du cadre, jamais des valeurs au hasard. */
private val SkeletonWidths = listOf(0.62f, 0.44f, 0.53f)

/**
 * **La panne de liaison, décidée une fois pour toutes.**
 *
 * Le fil (ChatScreen) montre `ConnectionProblemCard` depuis toujours, mais hors du fil, un échec
 * se lisait en texte d'erreur local — ou pas du tout (audit, T6). Cette fonction est le **seul
 * endroit** où l'on décide si un message d'échec dit *le serveur n'a jamais répondu* (donc : il
 * faut réessayer, et l'adresse peut être en cause) ou *le serveur a refusé* (401, 404 d'un
 * endpoint, réponse illisible : réessayer ne répare rien, il faut corriger les réglages).
 *
 * Elle lit **les phrases que [com.onyx.app.data.net.TransportError.userMessage] produit
 * réellement**, dans les deux langues, plus les mots des exceptions de bas niveau quand c'est le
 * message brut qui remonte (« Unable to resolve host », « Connection refused »). Un test la
 * confronte aux cinq genres de `TransportError.isConnectivity` et aux refus : la règle ne peut pas
 * dériver des messages sans qu'un test échoue.
 */
fun looksLikeServerTrouble(problem: String?): Boolean {
    if (problem.isNullOrBlank()) return false
    val text = problem.lowercase()
    return TroubleMarkers.any { it in text }
}

/**
 * **La route du serveur qui n'existe pas** — à ne pas confondre avec une panne.
 *
 * C'est la réponse à l'audit §5 : trois écrans d'Onyx dépendent d'une route que certains gateways
 * n'ont pas. Tant qu'on les traite comme une erreur de liaison, l'utilisateur lit « réessayez » pour
 * quelque chose qui ne répondra **jamais** ; il faut le dire autrement : *cette route n'est pas là,
 * voilà ce qu'elle apporterait*.
 *
 * La règle couvre les deux formes que prend une méthode inconnue dans ce code : le JSON-RPC
 * « Method not found » (`-32601`, la forme d'Hermes), et la réponse HTTP 404 d'un endpoint REST,
 * plus les messages bruts qui les portent. Comme [looksLikeServerTrouble], elle est vérifiée par un
 * test contre les objets [com.onyx.app.data.net.TransportError] qui les produisent vraiment.
 */
fun looksLikeMissingRoute(error: Throwable?): Boolean = when (error) {
    null -> false
    is com.onyx.app.data.net.TransportError.Rpc -> error.code == -32601
    is com.onyx.app.data.net.TransportError.Http -> error.status == 404
    else -> {
        val text = error.message.orEmpty().lowercase()
        MissingRouteMarkers.any { it in text }
    }
}

private val MissingRouteMarkers = listOf(
    "method not found",
    "method_not_found",
    "unknown method",
    "n'existe pas",
    "route absente",
)

private val TroubleMarkers = listOf(
    // Les phrases de TransportError.userMessage, en français.
    "injoignable",
    "adresse introuvable",
    "pas répondu à temps",
    "connexion s'est coupée",
    "adresse du serveur est invalide",
    // Les mêmes, en anglais.
    "unreachable",
    "address not found",
    "did not respond in time",
    "connection was lost",
    "server address is invalid",
    // Les titres de la carte du fil (`ConnectionProblemCard.titleOf`), quand c'est le titre — et
    // non le message brut — qu'un écran passe : ce sont eux que l'utilisateur lit pour dire qu'une
    // liaison a échoué.
    "impossible de contacter",
    "can't reach",
    "couldn't reach",
    // Les messages bruts qui remontent quand une exception n'est pas passée par userMessage.
    "unable to resolve host",
    "failed to connect",
    "connection refused",
    "connect timed out",
    "no route to host",
    "network is unreachable",
)

/**
 * **L'échec d'une lecture serveur, avec de quoi réessayer** — la moitié « erreur » de l'état d'un
 * écran de données.
 *
 * Deux formes, et **une seule décision** : si le serveur n'a pas répondu ([looksLikeServerTrouble]),
 * on montre la carte du fil (`ConnectionProblemCard`) — elle porte déjà la cause en clair, le
 * détail replié et le bouton de réessai, dans les deux langues ; sinon, on montre le refus tel
 * quel, dans la rangée d'avertissement que les écrans de la maison utilisent déjà, avec le même
 * « Réessayer ».
 *
 * Aucun écran ne doit rester muet : c'est la règle que ce composant rend impossible à oublier.
 * [onOpenSettings] est facultatif — un écran qui n'a pas de chemin vers les Réglages
 * (comme un écran porté par le volet) n'affiche simplement pas ce bouton-là.
 */
@Composable
fun OnyxTroublePanel(
    problem: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val theme = LocalOnyxTheme.current
    if (looksLikeServerTrouble(problem)) {
        ConnectionProblemCard(
            message = problem,
            onRetry = onRetry,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    OnyxFormSection(modifier = modifier) {
        OnyxRow {
            BasicText(
                text = problem,
                localize = false,
                style = theme.footnote.copy(color = theme.warning),
                modifier = Modifier.weight(1f),
            )
        }
        OnyxRowDivider()
        // Le libellé passe par le dictionnaire (« Réessayer » en français) : c'est le mot de la
        // maison, celui de la ligne d'état du volet.
        OnyxRow(onClick = onRetry) {
            BasicText("Try Again", style = theme.body.copy(color = theme.accent))
        }
    }
}
