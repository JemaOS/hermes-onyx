package com.onyx.app.ui.settings

import androidx.compose.foundation.Image
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.GlassDiagnostics
import com.onyx.app.ui.design.GlassLevel
import com.onyx.app.ui.design.LocalGlassFluidity
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.OnyxMenu
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxSwitch
import com.onyx.app.ui.design.OnyxSwitchDetailRow
import com.onyx.app.ui.design.OnyxTextSizeSlider
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.glassTierNow
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.ui.voice.OrbLight
import com.onyx.app.ui.voice.VoiceOrbChoice

/**
 * **La ligne d'état du verre liquide** — la réponse, dans l'app, à « je ne sais pas où est ton verre
 * liquide ».
 *
 * Elle dit **ce que l'app fait sur cet appareil, maintenant** : le palier de la plateforme
 * (réfraction, flou, voile), la qualité retenue par la mesure de fluidité (pleine, allégée, repli), et
 * le **nombre de vitres qui échantillonnent réellement l'arrière-plan**. Ce dernier chiffre est le
 * seul qui compte pour lire une capture : « aucune surface en verre ici » veut dire qu'il n'y a rien
 * à voir **sur cet écran-là** — pas que le verre est en panne, mais qu'aucune vitre n'y recouvre de
 * contenu. C'est exactement la confusion que le propriétaire a eue.
 *
 * **Pourquoi une ligne de réglages et pas un bandeau à l'écran** : un diagnostic permanent ferait
 * partie du dessin, et le dessin doit rester ce qu'il est. La page Apparence est l'endroit où l'on
 * vient chercher « comment l'app se comporte » ; la ligne y est, et nulle part ailleurs.
 *
 * Les libellés passent par [GlassDiagnostics] (des fonctions **pures**, figées par un test), donc le
 * mot affiché ici et le mot vérifié là-bas sont le même.
 */
@Composable
fun GlassStateRow(modifier: Modifier = Modifier) {
    val theme = LocalOnyxTheme.current
    val fluidity = LocalGlassFluidity.current
    val level = fluidity?.level ?: GlassLevel.FULL
    val sampling = GlassDiagnostics.sampling
    // Le palier du **dernier** verre qui échantillonne ; à défaut, celui de la plateforme.
    val tier = if (sampling > 0) GlassDiagnostics.tier else glassTierNow
    OnyxRow(modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = "Liquid glass",
                localize = true,
                style = theme.body.copy(color = theme.primaryText),
            )
            BasicText(
                text = GlassDiagnostics.line(tier, level, sampling, Build.VERSION.SDK_INT),
                localize = false,
                style = theme.caption.copy(color = theme.secondaryText),
                modifier = Modifier.padding(top = 2.dp),
            )
            BasicText(
                text = "Refraction needs something behind it: a thread that scrolls, a photo, a " +
                    "long list. On a flat page the glass has nothing to read, and it shows nothing.",
                localize = true,
                style = theme.caption2.copy(color = theme.tertiaryText),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Une rangée de réglage avec son explication sous le titre, comme le `Toggle` de la référence où le corps
 * est un `VStack` (titre + note en pied). L'interrupteur est celui de la référence, jamais un `Switch` Material.
 *
 * La forme est celle de la référence : la **vignette d'icône grise** en tête quand un [symbol] est
 * donné, le libellé, la description grise sur deux lignes, l'interrupteur à droite, et la rangée
 * entière qui bascule.
 */
@Composable
fun BlurbToggleRow(
    title: String,
    blurb: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    symbol: Int? = null,
) {
    OnyxSwitchDetailRow(
        title = title,
        detail = blurb,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        symbol = symbol,
    )
}

/**
 * Le **curseur de taille du texte du chat** : un « A » à chaque bout, des crans, un pouce blanc,
 * et **le nom du cran courant sous la piste** — celui du milieu se dit « Défaut ». C'est la forme
 * du contrôle de la référence, à la place du menu : le choix se fait d'un doigt, et le cran retenu
 * se lit sans ouvrir quoi que ce soit.
 *
 * Le réglage reste **le même** (`chatTextSize`, cinq crans) : rien n'est ajouté, seul le contrôle
 * change de forme.
 */
@Composable
fun ChatTextSizeRow(
    steps: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val sizes = ResolvedTheme.chatTextSizes
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            text = "Chat text size",
            style = theme.body.copy(color = theme.primaryText),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        // Le libellé de chaque cran, dans l'ordre du réglage : c'est le nom du cran courant qui
        // s'affiche sous la piste.
        OnyxTextSizeSlider(
            selectedIndex = sizes.indexOf(steps).coerceAtLeast(0),
            labels = ResolvedTheme.chatTextSizes.map { chatTextStopLabel(it) },
            onSelect = { index -> onPick(sizes[index.coerceIn(0, sizes.lastIndex)]) },
        )
    }
}

/**
 * Le nom **court** d'un cran de taille de texte — celui qui s'écrit sous le curseur. Il diffère du
 * libellé long du menu (« Comme le téléphone ») : sous une piste, il faut un mot, et le cran du
 * milieu est « Défaut ».
 */
fun chatTextStopLabel(steps: Int): String = when {
    steps <= -2 -> "Smallest"
    steps == -1 -> "Small"
    steps == 0 -> "Défaut"
    steps == 1 -> "Large"
    else -> "Largest"
}

/**
 * Les réglages de l'orbe vocal, comme `VoiceOrbSettings` (VoiceOrbSettings) : le choix replié
 * sur l'orbe courant — son libellé et son aperçu —, la grille de toutes les faces une fois ouvert,
 * puis « Orb light ».
 */
@Composable
fun VoiceOrbSettingsRows(
    orb: VoiceOrbChoice,
    light: OrbLight,
    onPickOrb: (VoiceOrbChoice) -> Unit,
    onPickLight: (OrbLight) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var pickingLight by remember { mutableStateOf(false) }

    // `DisclosureSummary(title: "Voice orb")` : le libellé de l'orbe et son aperçu à droite.
    com.onyx.app.ui.design.OnyxDisclosureSummary(
        title = "Voice orb",
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = orb.label, style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText))
            VoiceOrbPicture(orb = orb, size = 24.dp)
        }
    }

    if (expanded) {
        VoiceOrbGrid(orb = orb, onPickOrb = onPickOrb)
    }

    OnyxRow(onClick = { pickingLight = true }) {
        BasicText(
            text = "Orb light",
            style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.primaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = light.label,
            style = LocalOnyxTheme.current.body.copy(color = LocalOnyxTheme.current.secondaryText),
            maxLines = 1,
        )
    }
    if (pickingLight) {
        OnyxMenu(
            items = OrbLight.entries.map { option ->
                MenuItem(
                    label = option.label,
                    checked = option == light,
                    onSelect = {
                        pickingLight = false
                        onPickLight(option)
                    },
                )
            },
            onDismiss = { pickingLight = false },
        )
    }
}

/** La grille du choix d'orbe : la note, puis chaque face avec son libellé sous elle. */
@Composable
private fun VoiceOrbGrid(orb: VoiceOrbChoice, onPickOrb: (VoiceOrbChoice) -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            text = "The bars move when you speak; the light inside follows “Orb light”. Onyx looks at you instead, and talks with the reply.",
            style = theme.footnote.copy(color = theme.secondaryText),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // `GridItem(.adaptive(minimum: 72), spacing: 12)` de la référence.
            val columns = ((maxWidth + 12.dp) / 84.dp).toInt().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                VoiceOrbChoice.entries.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        row.forEach { choice ->
                            VoiceOrbCell(
                                choice = choice,
                                selected = choice == orb,
                                onClick = { onPickOrb(choice) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Une case de la grille : l'aperçu entouré quand il est retenu, puis son libellé. */
@Composable
private fun VoiceOrbCell(
    choice: VoiceOrbChoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .onyxClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .padding(4.dp)
                .border(
                    width = if (selected) 2.5.dp else 0.dp,
                    color = if (selected) theme.accent else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(if (choice.isRound) 40.dp else 16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            VoiceOrbPicture(orb = choice, size = 58.dp)
        }
        BasicText(
            text = choice.label,
            style = theme.caption.copy(
                color = if (selected) theme.primaryText else theme.secondaryText,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

/**
 * L'aperçu d'une face d'orbe, comme `VoiceOrbPicture` : Onyx est son visage dessiné, les autres
 * sont les images `…Blank` du catalogue.
 */
@Composable
private fun VoiceOrbPicture(orb: VoiceOrbChoice, size: androidx.compose.ui.unit.Dp) {
    val face = orb.face
    if (face == null) {
        BotLook(
            name = "default",
            faceCode = BotFace.main.code(),
            size = size,
            showsInitial = false,
        )
    } else {
        Image(
            painter = painterResource(face),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}

/** Le libellé localisé d'un libellé d'orbe, pour les tests et l'accessibilité. */
internal fun orbLabel(orb: VoiceOrbChoice): String = OnyxLocalization[orb.label]

/**
 * Les six accents proposés, repris de la référence (`AccentPalette.all`) : chacun donne une teinte
 * par apparence — la claire pour un fond clair, la sombre pour un fond sombre.
 *
 * Le choix de l'accent reste **le même réglage** que la couleur d'accent par thème
 * (`ThemeColors.accent`) : ces pastilles n'ajoutent pas une seconde clé, elles écrivent la première.
 */
enum class AccentPreset(val id: String, val light: Long, val dark: Long) {
    BLUE("Blue", 0xFF2F6BF5, 0xFF2F6BF5),
    VIOLET("Violet", 0xFF7C3AED, 0xFFA78BFA),
    GREEN("Green", 0xFF137538, 0xFF3FB950),
    ORANGE("Orange", 0xFFB04700, 0xFFF0883E),
    PINK("Pink", 0xFFB0327E, 0xFFF778BA),
    TEAL("Teal", 0xFF0C6A84, 0xFF22D3EE),
    ;

    /** La couleur de cet accent dans l'apparence courante. */
    fun color(dark: Boolean): androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color(if (dark) this.dark else this.light)

    companion object {
        fun all(): List<AccentPreset> = entries
    }
}

/**
 * La rangée de pastilles d'accent : une pastille par accent proposé, **la retenue cerclée** de sa
 * propre couleur. Même réglage que la couleur d'accent du thème — rien de dupliqué.
 *
 * Les mesures sont celles de la référence : une pastille de **38 dp** dans une cible de **48 dp**,
 * un anneau de **2 dp** sur la retenue. Sur un écran étroit ou à police agrandie, la rangée **passe
 * à la ligne** (`FlowRow`) au lieu de laisser la dernière pastille sortir de la carte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentSwatchRow(
    current: androidx.compose.ui.graphics.Color,
    dark: Boolean,
    onPick: (androidx.compose.ui.graphics.Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier) {
        AccentPreset.all().forEach { preset ->
            val color = preset.color(dark)
            val selected = sameAccent(current, color)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onyxClickable { onPick(color) }
                    .semantics(mergeDescendants = true) {
                        this.selected = selected
                        this.contentDescription = preset.id
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) color else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(4.dp)
                        .background(color, RoundedCornerShape(50)),
                )
            }
        }
    }
}

/**
 * Deux accents sont « le même » s'ils ont la même couleur à l'arrondi près : la pastille retenue se
 * cerne d'après la valeur réellement enregistrée, sans exiger l'identité bit à bit d'un flottant.
 */
internal fun sameAccent(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Boolean {
    fun q(v: Float) = Math.round(v * 255f)
    return q(a.red) == q(b.red) && q(a.green) == q(b.green) && q(a.blue) == q(b.blue)
}
