package com.onyx.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onyx.app.data.net.RecordDraft
import com.onyx.app.data.net.RecordReview
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.capabilities.EmptyLine
import com.onyx.app.ui.capabilities.FailureBanner
import com.onyx.app.ui.common.OnyxLoadingRows
import com.onyx.app.ui.design.ContentPill
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.OnyxEdge
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.design.PillSize
import com.onyx.app.ui.design.PillVariant
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.bodyInk
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **« Montrer une fois »** — la démonstration qui devient un skill.
 *
 * L'écran a trois visages, et aucun n'est muet :
 *
 * 1. **avant** — ce qui va être gardé (les gestes, jamais l'écran) et le bouton qui ouvre le bail ;
 * 2. **pendant** — un bandeau [RecordBanner] posé sur l'aperçu du PC : chronomètre, plafond de dix
 *    minutes, et « Terminer ». Le bandeau est **le seul** endroit d'où l'on arrête ;
 * 3. **après** — l'aperçu du brouillon, étape par étape, avec celles qui sont **à relire**, puis
 *    « En faire un skill » ou « Jeter ».
 *
 * Les règles de cet écran viennent de la spec (`docs/SPEC-SKILLS-RECORD.md` §7.3 et §8.1) : un
 * refus d'état (bail déjà ouvert, rien à arrêter) n'est pas une panne ; une route absente parle de
 * la fonction, pas de l'adresse du serveur ; et rien ne promet une capture d'images.
 *
 * @param bridgeReady vrai quand le pont est configuré **et** en direct. La feuille l'utilise pour
 *   dire « l'écran du PC n'est pas encore ouvert » au lieu d'un bouton qui ne mènerait nulle part.
 */
@Composable
fun RecordSheet(
    state: RecordController.UiState,
    bridgeReady: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val fg = bodyInk(theme, theme.formPageColor)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.formPageColor)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Demonstration", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            state.problem?.let { problem ->
                OnyxFormSection {
                    OnyxRow { FailureBanner(text = problem) }
                }
            }
            state.notice?.let { notice ->
                OnyxFormSection {
                    OnyxRow {
                        BasicText(
                            text = notice,
                            localize = false,
                            style = theme.footnote.copy(color = fg),
                        )
                    }
                }
            }

            when (state.phase) {
                RecordController.Phase.IDLE -> {
                    OnyxFormSection(
                        header = "Demonstration",
                        footer = "Onyx keeps your gestures, not your screen: no image, no sound, no password.",
                    ) {
                        if (!bridgeReady) {
                            OnyxRow { EmptyLine("The computer screen is not open yet.") }
                        } else {
                            OnyxRow {
                                BasicText(
                                    text = "The recording stops on its own after ten minutes.",
                                    style = theme.footnote.copy(color = fg),
                                )
                            }
                        }
                    }
                    OnyxFormSection {
                        OnyxRow {
                            ContentPill(
                                text = "Show me once",
                                localize = true,
                                onClick = onStart,
                                enabled = bridgeReady,
                                variant = PillVariant.Primary,
                                size = PillSize.Medium,
                                symbol = Symbols.HandRaised,
                            )
                        }
                    }
                }

                RecordController.Phase.STARTING, RecordController.Phase.STOPPING ->
                    OnyxLoadingRows(label = "Loading from Onyx…")

                RecordController.Phase.RECORDING -> {
                    if (!bridgeReady) {
                        // Le pont a lâché pendant la capture : le bail court toujours, et le
                        // journal est sur cet appareil (D2). On le dit, au lieu de laisser croire
                        // que les gestes suivants seront notés.
                        OnyxFormSection {
                            OnyxRow {
                                EmptyLine(
                                    "The bridge is no longer connected. Your gestures stay on this device.",
                                )
                            }
                        }
                    }
                    OnyxFormSection(
                        header = "Recording",
                        footer = "Onyx keeps your gestures, not your screen: no image, no sound, no password.",
                    ) {
                        OnyxRow {
                            BasicText(
                                text = OnyxLocalization.format(
                                    "Recording — stops on its own in %1\$s.",
                                    recordClockText(recordRemainingMs(state)),
                                ),
                                localize = false,
                                style = theme.body.copy(color = fg),
                            )
                        }
                        OnyxRowDivider()
                        OnyxRow {
                            BasicText(
                                text = OnyxLocalization.format("%1\$d actions", state.actions),
                                localize = false,
                                style = theme.footnote.copy(color = fg),
                            )
                        }
                    }
                    OnyxFormSection {
                        OnyxRow {
                            ContentPill(
                                text = "Stop recording",
                                localize = true,
                                onClick = onStop,
                                symbol = Symbols.CircleFill,
                                variant = PillVariant.Primary,
                            )
                        }
                    }
                }

                RecordController.Phase.REVIEW -> {
                    val draft = state.draft
                    val markdown = draft?.markdown ?: state.localMarkdown.orEmpty()
                    val steps = recordStepLines(markdown, draft?.review.orEmpty())
                    OnyxFormSection(
                        header = "Steps",
                        footer = if (state.actions == 0) {
                            "Nothing was recorded: there is no gesture to turn into a skill."
                        } else {
                            null
                        },
                    ) {
                        if (steps.isEmpty()) {
                            OnyxRow {
                                EmptyLine("Nothing was recorded: there is no gesture to turn into a skill.")
                            }
                        } else {
                            steps.forEachIndexed { index, step ->
                                if (index > 0) OnyxRowDivider()
                                OnyxRow {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                                    ) {
                                        BasicText(
                                            text = step.text,
                                            localize = false,
                                            style = theme.body.copy(color = fg),
                                        )
                                        step.review?.let { reason ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                OnyxIcon(
                                                    Symbols.ExclamationTriangle,
                                                    tint = theme.warning,
                                                    size = 12.dp,
                                                )
                                                BasicText(
                                                    text = OnyxLocalization.format("To review · %1\$s", reason),
                                                    localize = false,
                                                    style = theme.caption.copy(color = theme.warning),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OnyxFormSection(footer = "This demonstration will be lost. Nothing is written to the server.") {
                        OnyxRow {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                            ) {
                                ContentPill(
                                    text = "Turn it into a skill",
                                    localize = true,
                                    onClick = onKeep,
                                    variant = PillVariant.Primary,
                                    enabled = steps.isNotEmpty(),
                                )
                                ContentPill(
                                    text = "Throw it away",
                                    localize = true,
                                    onClick = onDiscard,
                                    variant = PillVariant.Outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * **Le bandeau de capture**, posé sur l'aperçu du PC.
 *
 * Il est dessiné par l'écran Ordinateur pendant que la feuille est refermée : c'est le seul état
 * qui doive rester sous les yeux **pendant** qu'on montre. Le bouton « Saisie secrète » n'est pas un
 * réglage : il vaut pour la saisie suivante, et retombe après elle. C'est la seule façon, côté
 * téléphone, de marquer un champ que le pont ne décrit pas — un mot de passe tapé dans une session
 * du PC n'est pas devinable, donc c'est l'utilisateur qui le déclare.
 */
@Composable
fun RecordBanner(
    remainingMs: Long,
    actions: Int,
    secretTyping: Boolean,
    onToggleSecret: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val panel = theme.formSurfaceColor
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(OnyxSpace.ControlCorner)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // **Deux dispositions, choisie par la largeur — pas par la classe d'appareil.**
        //
        // Défaut relevé sur le Magic V3 **plié** : le bandeau tenait sur une seule ligne (un texte,
        // deux pilules), et à 360 dp la rangée ne passait pas. Le texte se réduisait à rien, les
        // deux pilules prenaient toute la place et l'une s'écrivait sur deux lignes — « on voit
        // rien ». Sous [NarrowWidth], le bandeau passe donc sur **deux rangs** : le chronomètre et le
        // compte au-dessus, les deux gestes en dessous, à parts égales. Déplié, la place suffit : il
        // revient à une ligne, comme la planche.
        val narrow = bannerStacksVertically(maxWidth.value)
        // **Le trait de la maison** : sur un aperçu du PC en clair, un panneau clair sans bord ne se
        // voyait pas. Le filet le pose sur la page, quelle que soit la couleur dessous.
        val frame = Modifier
            .fillMaxWidth()
            .background(panel, shape)
            // Le **filet de la maison** ([OnyxEdge.fil]) : c'est le seul trait admis sur une
            // surface, et il est mesuré (12 %) — un `separator` nu dépassait le jeton.
            .border(OnyxSpace.Stroke, OnyxEdge.fil(theme), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)

        Column(modifier = frame, verticalArrangement = Arrangement.spacedBy(OnyxSpace.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnyxIcon(Symbols.CircleFill, tint = theme.error, size = 10.dp)
                // **Le chronomètre en premier, et en clair.** « Recording — stops on its own in
                // 7 min 12 s. » est une phrase ; sur deux rangs elle n'a plus la place d'être lue, et
                // c'est **le temps** qui compte sous les yeux pendant qu'on montre.
                BasicText(
                    text = recordClockText(remainingMs),
                    localize = false,
                    style = theme.subheadline.copy(
                        color = bodyInk(theme, panel),
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                BasicText(
                    text = OnyxLocalization.format("%1\$d actions", actions),
                    localize = false,
                    style = theme.caption.copy(color = bodyInk(theme, panel)),
                    maxLines = 1,
                )
            }

            // **Les deux gestes**, côte à côte et à parts égales quand la place manque : ils
            // restent atteignables au pouce, et aucun ne se replie sur deux lignes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OnyxSpace.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContentPill(
                    text = "Secret typing",
                    localize = true,
                    onClick = onToggleSecret,
                    variant = if (secretTyping) PillVariant.Primary else PillVariant.Outline,
                    size = PillSize.Small,
                    modifier = if (narrow) Modifier.weight(1f) else Modifier,
                )
                ContentPill(
                    text = "Stop recording",
                    localize = true,
                    onClick = onStop,
                    symbol = Symbols.CircleFill,
                    variant = PillVariant.Primary,
                    size = PillSize.Small,
                    modifier = if (narrow) Modifier.weight(1f) else Modifier,
                )
            }
        }
    }
}

/**
 * **La largeur sous laquelle le bandeau passe sur deux rangs.** 420 dp : un téléphone plié (360),
 * un pliable fermé ou une fenêtre étroite ; au-delà — déplié, tablette, paysage — la rangée unique
 * de la planche tient.
 */
internal val NarrowWidth = 420.dp

/**
 * **Le bandeau passe-t-il sur deux rangs ?** La règle, hors du dessin, pour qu'elle se mesure : un
 * téléphone plié (360 dp) l'exige, un pliable déplié (673) ou une tablette non.
 */
internal fun bannerStacksVertically(availableWidthDp: Float): Boolean =
    availableWidthDp < NarrowWidth.value

/** Une étape du brouillon, prête à peindre : son texte, et la raison de la relire. */
data class RecordStepLine(val number: Int, val text: String, val review: String? = null)

/** Une étape à relire, quand le serveur ne l'a pas dite (repli local). */
data class RecordReviewLine(val step: Int, val reason: String)

/**
 * **Le brouillon, ligne par ligne.** Les deux sources — l'enveloppe de `skills.draft` et le
 * Markdown converti sur l'appareil — ont la même forme (le convertisseur Kotlin est la référence
 * d'octets), donc le même lecteur les sert toutes les deux.
 *
 * Une raison est déjà dans la ligne quand le Markdown l'y a mise (« … — à relire : libellé
 * absent ») ; sinon [review] la donne en code, et le catalogue la traduit. Un code inconnu est
 * montré tel quel : une étape qu'on ne sait pas expliquer reste une étape à relire.
 */
fun recordStepLines(markdown: String, review: List<RecordReview> = emptyList()): List<RecordStepLine> {
    val reasons = review.associate { it.step to it.reason }
    return markdown.lineSequence().mapNotNull { raw ->
        val match = STEP_LINE.find(raw.trim()) ?: return@mapNotNull null
        val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val body = match.groupValues[2]
        val inline = body.substringAfter(REVIEW_MARKER, "").takeIf { REVIEW_MARKER in body }
        val text = body.substringBefore(REVIEW_MARKER).trim()
        val reason = inline?.trim()?.takeIf { it.isNotEmpty() }
            ?: reasons[number]?.let { code -> OnyxLocalization[code] }
        RecordStepLine(number = number, text = text, review = reason)
    }.toList()
}

/** Les étapes à relire d'un Markdown converti localement — les raisons y sont déjà en clair. */
fun recordLocalReview(markdown: String): List<RecordReviewLine> =
    recordStepLines(markdown).mapNotNull { step ->
        step.review?.let { reason -> RecordReviewLine(step.number, reason) }
    }

/**
 * Le chronomètre : « 9 min 12 s », « 45 s ». Jamais un nombre nu — « 552 » ne se lit pas.
 *
 * La composition ne passe par aucun format dépendant de la locale : les deux langues de l'app
 * écrivent « min » et « s » pareil, et un nombre décimal ici n'aurait aucun sens.
 */
fun recordClockText(remainingMs: Long): String {
    val total = (remainingMs / 1000L).coerceAtLeast(0L)
    val minutes = total / 60L
    val seconds = total % 60L
    return if (minutes > 0L) "$minutes min $seconds s" else "$seconds s"
}

/** Le temps restant avant le plafond du bail, d'après son échéance locale. */
fun recordRemainingMs(state: RecordController.UiState): Long =
    (state.deadlineAtMs - System.currentTimeMillis()).coerceAtLeast(0L)

/**
 * **L'entrée ne se dessine que si elle peut mener quelque part** : le serveur annonce la capacité
 * (`ServerProbe.allows(RECORD)`), et le pont est en direct. Sinon rien — ni ligne grisée, ni bouton
 * mort (spec §7.3, §0 règle 2).
 */
fun recordEntryVisible(serverAllowsRecording: Boolean, bridgeConfigured: Boolean): Boolean =
    serverAllowsRecording && bridgeConfigured

private val STEP_LINE = Regex("""^(\d+)\.\s+(.*)$""")

private const val REVIEW_MARKER = "— à relire :"
