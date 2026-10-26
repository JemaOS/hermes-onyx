package com.onyx.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onyx.app.data.model.serve.ServeLogLine
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxSegmented
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.OnyxSystem
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * Réglages → Gateway → Logs.
 *
 * Comme `GatewayLogView`: les trois journaux, le filtre, la recherche, le partage, et les
 * lignes colorées par niveau.
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lines = viewModel.visibleLines()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxSystem.background.resolve(theme.dark))
            .statusBarsPadding(),
    ) {
        OnyxPageHeader(title = "Logs", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OnyxSegmented(
                options = LogFile.entries.map { it.label },
                selectedIndex = LogFile.entries.indexOf(state.file),
                onSelect = { index -> viewModel.select(LogFile.entries[index]) },
            )

            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LogFilter.entries.forEach { filter ->
                    val selected = filter == state.filter
                    BasicText(
                        text = filter.label,
                        style = theme.footnote.copy(
                            color = if (selected) theme.accent else theme.secondaryText,
                        ),
                        modifier = Modifier.onyxClickable { viewModel.setFilter(filter) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(theme.surfaceColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (state.search.isEmpty()) {
                    BasicText("Search this log", style = theme.footnote.copy(color = theme.tertiaryText))
                }
                BasicTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    textStyle = theme.footnote.copy(color = theme.primaryText),
                    singleLine = true,
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OnyxIcon(Symbols.ArrowClockwise, tint = theme.accent, size = 13.dp)
                    BasicText(
                        text = "Refresh",
                        style = theme.footnote.copy(color = theme.accent),
                        modifier = Modifier.onyxClickable { viewModel.refresh() },
                    )
                }
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OnyxIcon(Symbols.Share, tint = theme.accent, size = 13.dp)
                    BasicText(
                        text = "Share",
                        style = theme.footnote.copy(color = theme.accent),
                        modifier = Modifier.onyxClickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.exportText())
                            }
                            context.startActivity(Intent.createChooser(intent, "Share this log"))
                        },
                    )
                }
            }
        }

        when {
            state.loading && lines.isEmpty() -> CenteredNote("Loading from Onyx…")

            state.problem != null && lines.isEmpty() -> CenteredNote(state.problem.orEmpty(), localize = false)

            lines.isEmpty() -> CenteredNote(
                if (state.search.isBlank()) "Nothing in this log." else "No line matches.",
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp,
                ),
            ) {
                items(lines) { line -> LogRow(line) }
            }
        }
    }
}

@Composable
private fun LogRow(line: ServeLogLine) {
    val theme = LocalOnyxTheme.current
    val color = when {
        line.isError() -> theme.error
        line.isWarning() -> theme.warning
        else -> theme.primaryText
    }
    BasicText(
        text = line.text,
        localize = false,
        style = theme.caption.copy(color = color, fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    )
}

@Composable
private fun CenteredNote(text: String, localize: Boolean = true) {
    val theme = LocalOnyxTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        BasicText(text = text, localize = localize, style = theme.body.copy(color = theme.secondaryText))
    }
}
