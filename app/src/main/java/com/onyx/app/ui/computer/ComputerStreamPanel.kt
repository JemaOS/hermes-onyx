package com.onyx.app.ui.computer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.ComputerLadder
import com.onyx.app.data.net.ComputerQuality
import com.onyx.app.data.net.ComputerStreamStats
import androidx.compose.foundation.clickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Les détails du flux — les chiffres qui règlent la qualité, montrés en clair.
 *
 * Ils viennent du **même** chemin que celui qui décide : la jauge du client publie ces mesures et
 * les donne au gouverneur dans le même souffle. Une seconde mesure, à côté, finirait par contredire
 * la décision qu'elle prétend expliquer.
 *
 * Rafraîchi une fois par seconde (la cadence de la jauge) : le panneau suit un `StateFlow`, donc
 * une mise à jour ne recompose que lui, et à 1 Hz — rien qui coûte à l'aperçu.
 */
@Composable
internal fun ComputerStreamPanel(
    stats: ComputerStreamStats,
    ladder: ComputerLadder,
    onEconomical: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.pageColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Stream details",
                style = theme.title3.copy(color = theme.textColor),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = "Done",
                style = theme.body.copy(color = theme.accent),
                modifier = Modifier.clickable(onClick = onDone),
            )
        }

        BasicText(
            text = "What the link is doing right now — the same numbers that set the quality.",
            style = theme.footnote.copy(color = theme.tertiaryText),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (!stats.measured) {
            BasicText(
                text = "Nothing measured yet.",
                style = theme.footnote.copy(color = theme.tertiaryText),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        StatRow("Round-trip latency", "${stats.rttMs} ms")
        StatRow("Jitter", "± ${stats.jitterMs} ms")
        StatRow("Frames per second", formatFps(stats.fps))
        StatRow("Frames dropped", stats.dropped.toString())
        StatRow("Frames missed", stats.missed.toString())
        StatRow("Requested size", "${stats.width}×${stats.width * 9 / 16}")
        StatRow("Estimated bitrate", "${stats.kbps} kbit/s")
        StatRow(
            "Quality",
            "q${stats.quality} · ${ladder.step + 1}/${ladder.total}",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Economical link",
                    style = theme.body.copy(color = theme.textColor),
                )
                BasicText(
                    text = if (ladder.economical) {
                        "Economical link keeps the lightest setting — for a metered network."
                    } else {
                        "The link sets its own level: it drops fast when it degrades, climbs back slowly."
                    },
                    style = theme.caption.copy(color = theme.tertiaryText),
                )
            }
            SwitchPill(
                on = ladder.economical,
                onToggle = { onEconomical(!ladder.economical) },
            )
        }

        Box(modifier = Modifier.navigationBarsPadding())
    }
}

/** Une ligne de chiffre : le nom à gauche, la valeur en chiffres alignés à droite. */
@Composable
private fun StatRow(label: String, value: String) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = theme.body.copy(color = theme.textColor),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = value,
            localize = false,
            style = theme.body.copy(
                color = theme.secondaryText,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

/**
 * L'interrupteur de la liaison économe : **deux mots**, jamais une couleur seule.
 *
 * La couleur dit l'état d'un regard ; le mot le dit à qui ne la voit pas. Un interrupteur dont le
 * seul signe est un fond coloré ne se lit pas.
 */
@Composable
private fun SwitchPill(on: Boolean, onToggle: () -> Unit) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .background(
                if (on) theme.accent.copy(alpha = 0.22f) else theme.textColor.copy(alpha = 0.06f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (on) "Economical link" else "Automatic",
            style = theme.footnote.copy(color = if (on) theme.accent else theme.secondaryText),
            maxLines = 1,
        )
    }
}

/** Une image par seconde se dit avec une décimale : `6.0`, jamais `6` qui cache un lien faible. */
private fun formatFps(fps: Float): String {
    val tenths = (fps * 10f).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
