package com.onyx.app.ui.chat.components

import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.OnyxSurfaces
import com.onyx.app.ui.design.Feedback
import com.onyx.app.ui.design.SurfaceRole
import com.onyx.app.ui.design.rowArrival
import com.onyx.app.ui.design.surfaceRole

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.design.ambientLoop
import com.onyx.app.ui.design.fitWords
import com.onyx.app.ui.design.reducedStagger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.LiveMark
import com.onyx.app.ui.design.OnyxMotion
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.WaveformPulse
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.semanticsLabel
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.theme.LocalOnyxTheme
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Une carte à ouvrir depuis l'écran de démarrage (`StartScreen.Card`).
 *
 * [hint] est l'aide lue à la voix (« Opens your most recent conversation ») ; [relative] est
 * l'horodatage relatif montré sous le titre d'un cartouche de reprise ; [busy] affiche le petit
 * témoin d'attente à la place du chevron pendant que la carte travaille.
 */
data class StartCard(
    val id: String,
    @DrawableRes val symbol: Int,
    val title: String,
    val subtitle: String,
    val hint: String = "",
    val relative: String? = null,
    val busy: Boolean = false,
    val isContinue: Boolean = false,
)

/** Le préfixe d'un cartouche de reprise, comme `Continue: \(last.title)` côté référence. */
private const val CONTINUE_PREFIX = "Continue: "

/**
 * Combien de lignes un sous-titre de carte a le droit de prendre.
 *
 * Un cartouche de reprise porte une phrase entière (« Ouvre votre conversation la plus récente ») :
 * sur une seule ligne, elle se coupait en plein mot derrière une ellipse. Deux lignes, et la phrase
 * se lit entière. Les autres cartes gardent une ligne, leur sous-titre étant court.
 */
internal fun cardSubtitleLines(isContinue: Boolean): Int = if (isContinue) 2 else 1

/**
 * Ce qu'affiche une conversation vide : la marque de l'app, un bonjour selon l'heure, et jusqu'à
 * trois points de départ, chacun à un seul tap.
 *
 * Port de `StartScreen` : le cercle d'accent avec sa forme d'onde immobile, un anneau qui
 * s'étend tout autour, puis les cartes.
 */
@Composable
fun StartScreen(
    title: String,
    cards: List<StartCard>,
    onOpenCard: (StartCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current

    // Les pièces entrent une à une (`StartScreen.Arriving`) : une toutes les 90 ms, la première
    // après 60 ms. `shown` compte celles qui sont arrivées ; chaque pièce connaît son index.
    // `if !reduceMotion { try? await Task.sleep(…) }` (`StartScreen:121`) : tout arrive
    // d'un coup sous « Réduire les animations ».
    var shown by remember { mutableIntStateOf(0) }
    val firstPiece = reducedStagger(60)
    val nextPiece = reducedStagger(90)
    LaunchedEffect(Unit) {
        shown = 0
        for (count in 1..6) {
            delay(if (count == 1) firstPiece else nextPiece)
            shown = count
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // La marge d'écran de la maison (16 dp), et non plus 28 dp écrit ici : le bloc de
            // départ et le compositeur partagent ainsi la **même** colonne de départ — deux retraits
            // concurrents sur le même écran se lisent « pas propre » même quand chacun est juste.
            .padding(horizontal = OnyxSpace.ScreenMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Loose),
    ) {
        Box(modifier = Modifier.arriving(index = 0, shown = shown)) { AppMark() }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap),
        ) {
            BasicText(
                text = greeting(),
                style = theme.title.copy(color = theme.textColor, fontWeight = FontWeight.Bold),
                modifier = Modifier.arriving(index = 1, shown = shown),
            )
            BasicText(
                text = OnyxLocalization.format("What should %1\$s look into?", title),
                style = theme.body.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().arriving(index = 2, shown = shown),
            )
        }

        // -----------------------------------------------------------------------------------
        // **Le filet qui structure l'accueil vide.**
        //
        // Le défaut relevé sur la capture : « l'écran d'accueil vide a d'énormes vides verticaux,
        // rien ne structure la page ». La cause n'est pas l'espace, c'est qu'il n'y a **rien pour
        // le lire** : un îlot flottant au milieu de deux bandes vides de même hauteur. Le filet de
        // la maison sépare la tête (« bonjour ») de la liste des départs, et chaque cartouche prend
        // le **rôle carte** ([SurfaceRole.Card]) — donc son filet : le groupe se lit comme une
        // liste posée sur la page, pas comme des boîtes devinées dans le noir.
        // -----------------------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OnyxSpace.Stroke)
                .background(OnyxSurfaces.hairline(theme))
                .arriving(index = 3, shown = shown),
        )

        // Les cartouches de reprise (« Continue: … ») sont ceux que l'appelant marque
        // `isContinue` — ou dont le titre commence par « Continue: » — et gardent l'ordre donné,
        // sous l'en-tête, comme les cartes de `StartScreen.cards` côté référence.
        //
        // La colonne est toujours posée, même vide : dans la référence, le troisième enfant du VStack
        // existe toujours, donc l'espace de 24 points au-dessus reste le même quand il n'y a
        // aucune carte.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
        ) {
            cards.forEachIndexed { index, card ->
                Box(
                    // La cascade ([MotionFamily.Row]) prolonge celle des pièces de l'en-tête.
                    modifier = Modifier
                        .arriving(index = 4 + index, shown = shown)
                        .rowArrival(index),
                ) {
                    StartCardRow(card = card, onClick = { onOpenCard(card) })
                }
            }
        }
    }
}

/**
 * Une pièce de l'écran de départ qui arrive : d'un cran plus bas et de rien (`StartScreen.Arriving`).
 * `index` est la place de la pièce, `shown` le nombre de pièces déjà arrivées.
 */
@Composable
private fun Modifier.arriving(index: Int, shown: Int): Modifier {
    val arrived = shown > index
    val alpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = OnyxMotion.Sites.ArrivingPiece,
        label = "arrivingAlpha$index",
    )
    val travel by animateDpAsState(
        targetValue = if (arrived) 0.dp else 10.dp,
        animationSpec = OnyxMotion.Sites.ArrivingPieceDp,
        label = "arrivingTravel$index",
    )
    return this.alpha(alpha).offset(y = travel)
}

/** La marque : un cercle plein d'accent, sa forme d'onde, et l'anneau qui pulse. */
@Composable
private fun AppMark() {
    val theme = LocalOnyxTheme.current
    val transition = rememberInfiniteTransition(label = "mark")
    // `mark.onAppear { guard !reduceMotion else { return } }` (`StartScreen:151-152`) :
    // l'anneau ne pulse pas, il reste à son repos (échelle 0,9, opacité 0,55).
    val pulse = ambientLoop(0f) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = OnyxMotion.Sites.MarkPulse,
                repeatMode = RepeatMode.Restart,
            ),
            label = "markPulse",
        ).value
    }

    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        // L'anneau qui s'étend : un trait d'accent qui grandit en s'effaçant — `strokeBorder`, pas
        // un disque plein (`StartScreen.mark`).
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(0.9f + 0.8f * pulse)
                .drawBehind {
                    drawCircle(
                        color = theme.accent.copy(alpha = (1f - pulse) * 0.55f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                },
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(theme.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            WaveformPulse(color = theme.userText, height = 20f, spacing = 2.5f, still = true)
        }
    }
}

/**
 * Une carte de départ : symbole, titre, sous-titre, chevron. Un cartouche de reprise passe par
 * [ContinueCard], tout le reste par la même surface.
 */
@Composable
private fun StartCardRow(card: StartCard, onClick: () -> Unit) {
    val isContinue = card.isContinue || card.title.startsWith(CONTINUE_PREFIX)
    if (isContinue) {
        ContinueCard(card = card, onClick = onClick)
    } else {
        StartCardSurface(card = card, onClick = onClick)
    }
}

/**
 * Un cartouche de reprise : « Continue: … », sous-titré par l'horodatage relatif de la
 * conversation. Sans horodatage fourni, le sous-titre reste celui de la carte.
 */
@Composable
fun ContinueCard(card: StartCard, onClick: () -> Unit) {
    StartCardSurface(card = card.copy(subtitle = card.relative ?: card.subtitle), onClick = onClick)
}

/** La surface d'une carte : les espacements et la forme de la référence (`cardView`). */
@Composable
private fun StartCardSurface(card: StartCard, onClick: () -> Unit) {
    val theme = LocalOnyxTheme.current
    val isContinue = card.isContinue || card.title.startsWith(CONTINUE_PREFIX)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // **Le rôle « carte »** ([SurfaceRole.Card]) — donc le fond de la famille **et le
            // filet**. C'était `.background(theme.surfaceColor, RoundedCornerShape(TileCorner))` :
            // en sombre, `surfaceColor` vaut 1,096:1 sur la page et la carte n'avait **aucun
            // filet** — « la carte Continuer est noire sur noire, on devine à peine ses bords ».
            // Un cartouche se délimite par le filet de la maison, pas par un fond plus clair.
            .surfaceRole(SurfaceRole.Card)
            .then(if (card.hint.isNotEmpty()) Modifier.semanticsLabel(card.hint) else Modifier)
            .onyxClickable(pressedScale = Feedback.PressScale, enabled = !card.busy, onClick = onClick)
            .padding(horizontal = OnyxSpace.CardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Le symbole occupe une largeur fixe : les titres s'alignent d'une carte à l'autre.
        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            OnyxIcon(symbol = card.symbol, tint = theme.textColor, size = 18.dp)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Le titre est coupé **à une frontière de mot** (`fitWords`) : `TextOverflow.Ellipsis`
            // seul rognait le dernier mot en deux (« Continuer : Tester la navigation r… », mesuré
            // à 411 dp) — la charte l'interdit. Le sous-titre garde l'ellipse du moteur : il est
            // fait de mots courts et d'un horodatage qu'on peut couper sans casser un mot.
            BoxWithConstraints {
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val titleStyle = theme.body.copy(color = theme.textColor, fontWeight = FontWeight.Medium)
                val available = with(density) { maxWidth.toPx() }
                val fitted = remember(card.title, available, titleStyle) {
                    fitWords(
                        text = card.title,
                        measure = { line ->
                            measurer.measure(AnnotatedString(line), titleStyle).size.width.toFloat()
                        },
                        available = available,
                        maxLines = 1,
                    )
                }
                BasicText(
                    text = fitted,
                    localize = false,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            BasicText(
                text = card.subtitle,
                localize = false,
                style = theme.footnote.copy(color = theme.secondaryText),
                maxLines = cardSubtitleLines(isContinue),
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.busy) {
            // Le petit témoin d'attente de la référence, à la place du chevron pendant que la carte travaille.
            LiveMark(color = theme.tertiaryText)
        } else {
            OnyxIcon(Symbols.ChevronRight, tint = theme.tertiaryText, size = 12.dp)
        }
    }
}

/**
 * Le dégagement du haut de l'accueil vide : la marche `Loose` de l'échelle (24 dp) × 4.
 *
 * Quatre marches au lieu d'un centrage : le bloc de départ se pose en haut de la zone, et le vide
 * qui reste est **celui du bas** — la place où la conversation va venir. Un bloc centré laissait
 * deux bandes égales dont aucune ne se lisait comme une réserve.
 */
internal val EmptyHomeTopInset = OnyxSpace.Loose * 4

/** « Good morning. » selon l'heure, comme `StartScreen.greeting(for:)`. */
internal fun greeting(now: Calendar = Calendar.getInstance()): String =
    when (now.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning."
        in 12..16 -> "Good afternoon."
        in 17..21 -> "Good evening."
        else -> "Hello."
    }
