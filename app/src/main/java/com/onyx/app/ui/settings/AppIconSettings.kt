package com.onyx.app.ui.settings

import com.onyx.app.ui.design.OnyxSpace

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.onyx.app.R
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les icônes réellement fournies par `AppIconSettings`, dans le même ordre. */
enum class AppIconChoice(
    val rawValue: String,
    val label: String,
    val resourceName: String,
    val aliasName: String,
    @DrawableRes val imageRes: Int,
) {
    FACE_MIST("AppIconFaceMist", "Onyx Face", "ic_launcher_face_mist", ".launcher.FaceMist", R.mipmap.ic_launcher_face_mist),
    GRAPHITE("AppIcon", "Graphite", "ic_launcher_graphite", ".launcher.Graphite", R.mipmap.ic_launcher_graphite),
    GRAPHITE_BLOCK("AppIconGraphiteBlock", "Graphite Block", "ic_launcher_graphite_block", ".launcher.GraphiteBlock", R.mipmap.ic_launcher_graphite_block),
    STEEL_BLOCK("AppIconSteelBlock", "Steel", "ic_launcher_steel_block", ".launcher.SteelBlock", R.mipmap.ic_launcher_steel_block),
    MIST("AppIconMist", "Mist", "ic_launcher_mist", ".launcher.Mist", R.mipmap.ic_launcher_mist),
    GRAPHITE_FLAT("AppIconGraphiteFlat", "Matte Graphite", "ic_launcher_graphite_flat", ".launcher.GraphiteFlat", R.mipmap.ic_launcher_graphite_flat),
    GRAPHITE_BLOCK_FLAT("AppIconGraphiteBlockFlat", "Matte Graphite Block", "ic_launcher_graphite_block_flat", ".launcher.GraphiteBlockFlat", R.mipmap.ic_launcher_graphite_block_flat),
    STEEL_FLAT("AppIconSteelFlat", "Matte Steel", "ic_launcher_steel_flat", ".launcher.SteelFlat", R.mipmap.ic_launcher_steel_flat),
    MIST_FLAT("AppIconMistFlat", "Matte Mist", "ic_launcher_mist_flat", ".launcher.MistFlat", R.mipmap.ic_launcher_mist_flat),
    NAVY_BLOCK("AppIconNavyBlock", "Navy", "ic_launcher_navy_block", ".launcher.NavyBlock", R.mipmap.ic_launcher_navy_block),
    INK("AppIconInk", "Ink", "ic_launcher_ink", ".launcher.Ink", R.mipmap.ic_launcher_ink),
    PAPER_NAVY("AppIconPaperNavy", "Paper Navy", "ic_launcher_paper_navy", ".launcher.PaperNavy", R.mipmap.ic_launcher_paper_navy),
    GOLD("AppIconGold", "Gold", "ic_launcher_gold", ".launcher.Gold", R.mipmap.ic_launcher_gold),
    CLASSIC("AppIconClassic", "Classic", "ic_launcher_classic", ".launcher.Classic", R.mipmap.ic_launcher_classic),
    ;

    companion object {
        /**
         * L'icône **figée** de l'APK : le carré arrondi sombre, le cercle et la rune
         * (`ic_launcher_graphite_block`). C'est l'illustration que le propriétaire a retenue comme
         * référence ; elle ne doit plus changer par une variante de build, un thème ou une refonte.
         * Le manifeste porte le même choix (`android:enabled` sur l'alias GraphiteBlock) et
         * `NomEtIconeDuLanceurTest` le vérifie des deux côtés.
         */
        val primary: AppIconChoice = GRAPHITE_BLOCK
    }
}

/** Change l'alias launcher actif ; son état est persisté par Android, y compris au redémarrage. */
class AppIconController(private val context: Context) {
    private val manager: PackageManager get() = context.packageManager

    fun current(): AppIconChoice {
        val states = AppIconChoice.entries.map { it to state(it) }
        return states.firstOrNull { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }?.first
            ?: states.firstOrNull {
                it.first == AppIconChoice.primary && it.second == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }?.first
            ?: AppIconChoice.primary
    }

    fun choose(choice: AppIconChoice) {
        if (current() == choice) return
        val previous = AppIconChoice.entries.associateWith { state(it) }
        try {
            // On active d'abord le nouveau lanceur : même en cas d'échec ensuite, un lanceur reste.
            set(choice, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            AppIconChoice.entries.filter { it != choice }.forEach {
                set(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            }
        } catch (error: Exception) {
            previous.forEach { (icon, state) -> runCatching { set(icon, state) } }
            throw error
        }
    }

    private fun state(choice: AppIconChoice): Int = manager.getComponentEnabledSetting(component(choice))

    private fun set(choice: AppIconChoice, state: Int) {
        manager.setComponentEnabledSetting(component(choice), state, PackageManager.DONT_KILL_APP)
    }

    // L'alias est déclaré dans le manifeste sous `${applicationId}.launcher.Graphite` : Android
    // résout un composant contre le paquet **réellement installé**, pas contre le paquet du code.
    // On ne compose donc le nom qu'avec `context.packageName` — jamais avec un paquet écrit en
    // dur, qui resterait faux à la première installation sous un autre identifiant.
    private fun component(choice: AppIconChoice): ComponentName =
        ComponentName(context.packageName, context.packageName + choice.aliasName)
}

/** La ligne « App icon » d'Apparence, puis la grille de toutes les variantes embarquées. */
@Composable
fun AppIconSettings(modifier: Modifier = Modifier, embedded: Boolean = false) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val controller = remember(context) { AppIconController(context.applicationContext) }
    var selected by remember { mutableStateOf(controller.current()) }
    // Comme `AppIconSettings` (`@State private var expanded = false`) : la grille reste
    // **repliée** sur la rangée qui porte l'icône choisie ; le chevron ne fait que la déplier.
    var expanded by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun choose(choice: AppIconChoice) {
        if (changing || choice == selected) return
        val previous = selected
        selected = choice
        failure = null
        changing = true
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                runCatching { controller.choose(choice) }.exceptionOrNull()
            }
            if (problem == null) {
                selected = controller.current()
            } else {
                selected = previous
                failure = OnyxLocalization.format("Couldn't change the icon: %1\$s", problem.message.orEmpty())
            }
            changing = false
        }
    }

    // La rangée et sa grille, sans carte : `Appearance` les pose dans la sienne, comme le
    // `DisclosureGroup` d'`AppIconSettings` dans la section Apparence.
    val rows: @Composable () -> Unit = {
        OnyxRow(onClick = { expanded = !expanded }) {
            BasicText(
                text = "App icon",
                style = theme.body.copy(color = theme.primaryText),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = selected.label,
                style = theme.body.copy(color = theme.secondaryText),
            )
            Image(
                bitmap = launcherIcon(selected.imageRes, 24.dp),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(OnyxSpace.ControlCorner)),
            )
            OnyxIcon(
                symbol = Symbols.ChevronDown,
                tint = theme.tertiaryText,
                size = 12.dp,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                // Grille adaptative de 72 dp + 12 dp d'espacement, comme le LazyVGrid la référence.
                val columns = ((maxWidth + 12.dp) / 84.dp).toInt().coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIconChoice.entries.chunked(columns).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { choice ->
                                AppIconCell(
                                    choice = choice,
                                    selected = selected == choice,
                                    enabled = !changing,
                                    onClick = { choose(choice) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            failure?.let {
                BasicText(
                    text = it,
                    localize = false,
                    style = theme.footnote.copy(color = theme.error),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (embedded) rows() else OnyxFormSection(modifier = modifier) { rows() }
}

internal val AppIconCellOuterSize = 66.dp

/**
 * L'aperçu d'une icône de lanceur, en **bitmap**.
 *
 * `painterResource` n'accepte que les `VectorDrawable` et les images matricielles (PNG, JPG,
 * WEBP). Or, `minSdk = 26` : sur Android 8 et plus, la ressource
 * `mipmap-anydpi-v26/ic_launcher_<variante>.xml` est une **icône adaptative**
 * (`<adaptive-icon>`) qui **prend le pas** sur les PNG de densité du même nom. L'appel levait donc
 * « Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP » et **fermait
 * l'application** dès l'ouverture de Réglages → Apparence (la rangée « App icon » est dessinée à
 * l'arrivée sur la page).
 *
 * On charge donc le drawable par la plateforme — ce qui accepte **tous** les types, icône
 * adaptative comprise — et on le rasterise à la taille demandée. La correction vaut pour la rangée
 * comme pour la grille dépliée.
 */
@Composable
private fun launcherIcon(res: Int, size: Dp): ImageBitmap {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(res, size, context, density.density) {
        val side = with(density) { size.roundToPx() }.coerceAtLeast(1)
        val bitmap = ContextCompat.getDrawable(context, res)
            ?.toBitmap(side, side, Bitmap.Config.ARGB_8888)
            ?.takeIf { !it.isRecycled }
        // Jamais d'exception sur une ressource : un pixel transparent plutôt qu'un écran fermé.
        (bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)).asImageBitmap()
    }
}

@Composable
private fun AppIconCell(
    choice: AppIconChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val label = OnyxLocalization[choice.label]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .onyxClickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .size(AppIconCellOuterSize)
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, theme.accent, RoundedCornerShape(OnyxSpace.TileCorner))
                    } else {
                        Modifier
                    },
                )
                .padding(3.dp),
        ) {
            Image(
                bitmap = launcherIcon(choice.imageRes, 60.dp),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(OnyxSpace.ThumbnailCorner)),
            )
        }
        BasicText(
            text = choice.label,
            style = theme.caption.copy(
                color = if (selected) theme.primaryText else theme.secondaryText,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
            minLines = 2,
        )
    }
}
