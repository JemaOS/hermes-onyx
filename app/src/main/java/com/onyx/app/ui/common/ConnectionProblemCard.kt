package com.onyx.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Ce que le fil montre quand le gateway ne répond pas (`ConnectionProblemCard`).
 *
 * Le problème en clair, ce qui le corrige, et l'erreur brute repliée.
 */
@Composable
fun ConnectionProblemCard(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    var showDetails by remember { mutableStateOf(false) }
    val kind = kindOf(message)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(theme.surfaceColor, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(theme.warning.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            OnyxIcon(symbol = symbolOf(kind), tint = theme.warning, size = 22.dp)
        }

        BasicText(
            text = titleOf(kind),
            style = theme.title3.copy(color = theme.textColor, fontWeight = FontWeight.Bold),
        )
        BasicText(
            text = explanationOf(kind),
            style = theme.subheadline.copy(color = theme.secondaryText),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (needsSettings(kind)) {
                BasicText(
                    text = "Open Settings",
                    style = theme.body.copy(color = theme.userText, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .background(theme.accent, RoundedCornerShape(50))
                        .onyxClickable(onClick = onOpenSettings)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            BasicText(
                text = "Try Again",
                style = theme.body.copy(
                    color = if (needsSettings(kind)) theme.accent else theme.userText,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .then(
                        if (needsSettings(kind)) Modifier
                        else Modifier.background(theme.accent, RoundedCornerShape(50)),
                    )
                    .onyxClickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        BasicText(
            text = if (showDetails) "Hide details" else "Details",
            style = theme.footnote.copy(color = theme.accent),
            modifier = Modifier.onyxClickable { showDetails = !showDetails },
        )
        AnimatedVisibility(visible = showDetails) {
            BasicText(
                text = message,
                localize = false,
                style = theme.caption.copy(
                    color = theme.tertiaryText,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
        }
    }
}

/** La famille du problème, devinée du message — comme `ConnectionProblemCard.kind`. */
private enum class ProblemKind { LOGIN, KEY, PROFILE, UNREACHABLE, OTHER }

private fun kindOf(message: String): ProblemKind {
    val m = message.lowercase()
    return when {
        m.contains("profil") || m.contains("profile") -> ProblemKind.PROFILE
        m.contains("key") || m.contains("clé") || m.contains("401") || m.contains("autorisé") -> ProblemKind.KEY
        m.contains("identifiant") || m.contains("password") || m.contains("login") || m.contains("connexion") -> ProblemKind.LOGIN
        m.contains("injoignable") || m.contains("coupée") || m.contains("répondu") || m.contains("reach") -> ProblemKind.UNREACHABLE
        else -> ProblemKind.OTHER
    }
}

private fun symbolOf(kind: ProblemKind): Int = when (kind) {
    ProblemKind.LOGIN, ProblemKind.KEY -> Symbols.Key
    ProblemKind.PROFILE -> Symbols.Person2
    ProblemKind.UNREACHABLE -> Symbols.WifiExclamation
    ProblemKind.OTHER -> Symbols.ExclamationTriangle
}

private fun titleOf(kind: ProblemKind): String = when (kind) {
    ProblemKind.LOGIN -> "Sign in to the Hermes Dashboard"
    ProblemKind.KEY -> "Add your gateway key"
    ProblemKind.PROFILE -> "This profile isn't available"
    ProblemKind.UNREACHABLE -> "Can't reach your gateway"
    ProblemKind.OTHER -> "Couldn't reach the server"
}

private fun explanationOf(kind: ProblemKind): String = when (kind) {
    ProblemKind.LOGIN -> "Your conversations live on the desktop gateway, and it needs you to sign in before it will share them."
    ProblemKind.KEY -> "Onyx needs the gateway's API key before it can read your conversations."
    ProblemKind.PROFILE -> "This server has no profile by that name, or doesn't serve it over the API. Pick another under Settings → Servers."
    ProblemKind.UNREACHABLE -> "Check that the gateway is running and that this phone is on your network or tailnet."
    ProblemKind.OTHER -> "The gateway answered, but not in a way Onyx understood."
}

private fun needsSettings(kind: ProblemKind): Boolean = kind != ProblemKind.OTHER
