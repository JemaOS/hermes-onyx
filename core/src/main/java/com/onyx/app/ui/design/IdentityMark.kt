package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlin.math.abs

/**
 * **LA MARQUE D'IDENTITÉ — une seule pièce, une seule géométrie, une seule teinte par identité.**
 *
 * Le défaut relevé par le propriétaire : dans la liste, **chaque carte de conversation porte en haut
 * à droite une petite pastille circulaire sans règle apparente** — un rond ambre sur une carte, un
 * rond bleu-violet sur une autre, posés sur une couverture qui peut être claire ou sombre, donc de
 * lisibilité non acquise. Deux ronds de teintes différentes : ni monogramme, ni glyphe, ni identité.
 *
 * Trois causes, et une seule réponse — ce fichier :
 *
 *  1. **la pastille du coin portait l'ÉTAT, pas l'identité** (`ChatSidebar.kt`, `ToneDisc` : l'ambre
 *     d'une question qui attend, l'accent d'un tour en cours). Le **même bot** montrait donc une
 *     marque sur la carte et une autre dans la liste des bots, la fiche, l'en-tête ou le volet — la
 *     divergence que la maison interdit ;
 *  2. **deux géométries pour la même idée** : la pastille d'état de la carte faisait 26 dp, le point
 *     d'état d'une carte de bot une autre taille, chacun avec ses valeurs écrites à la main sur son
 *     site d'appel ;
 *  3. **la lisibilité n'était pas construite** : sous l'encre d'état, la pastille posait la vignette
 *     de la couverture et un voile noir à 30 % **écrits en dur au site d'appel**. L'encre annoncée
 *     au test de contraste était donc mesurée contre le *disque*, alors que le fond réellement peint
 *     était un mélange — l'encre annoncée n'était pas l'encre peinte.
 *
 * **Ce que la marque est.** Une pastille pleine de la **couleur du bot** ([tintFor] : le profond du
 * dégradé de son visage, le même partout), un **glyphe monochrome** — son initiale, ou la **rune de
 * la maison** pour le bot principal — à l'encre qui se lit le mieux dessus, et **deux lisérés d'appui**
 * qui la détachent de n'importe quelle couverture.
 *
 * **Pourquoi deux lisérés, et pourquoi c'est prouvable.** Le disque est **opaque**, donc le glyphe se
 * lit indépendamment de la couverture ([glyphContrast] ≥ [GlyphMin], garanti par construction :
 * `OnyxContrast.filled` écarte la teinte juste assez, `inkOn` choisit l'encre). Reste à garantir que
 * la *marque* ne se fond pas dans la couverture, claire **ou** sombre **ou** chargée. C'est de
 * l'arithmétique, pas un réglage : le liséré intérieur est l'encre du glyphe, le liséré extérieur son
 * **contraire** ; pour toute luminance de couverture, l'un des deux tient le seuil de la maison
 * ([separation] ≥ [EdgeMin], voir [coverageProof]). Un fond sombre est détaché par le liséré clair,
 * un fond clair par le liséré sombre, un fond moyen par les deux.
 *
 * **Les valeurs sont publiques et uniques** : [Diameter], [Corner], [Glyph], [StrokeWidth], [Inset].
 * Aucun site d'appel n'écrit de dimension : le verrou (`IdentityMarkLockTest`) relit ces constantes,
 * échoue si deux écrans montrent deux teintes pour la même identité, si un contraste passe sous le
 * seuil dans une apparence, ou si une marque sort de la géométrie (voir [fitsGeometry]).
 */
object IdentityMark {

    // =============================================================================================
    // 1 · La géométrie — les constantes de la maison. Aucune n'est écrite sur un site d'appel.
    // =============================================================================================

    /** **Le diamètre de la pastille : 32 dp.** La constante de la maison pour une marque d'identité. */
    val Diameter: Dp = 32.dp

    /**
     * **Le rayon de coin : la moitié du diamètre, donc un cercle.** La référence de style du
     * propriétaire pose « un glyphe mono dans une pastille » : une pastille est ronde. Le rayon est
     * **dérivé** du diamètre ([CornerOf]) pour qu'aucune des deux valeurs ne puisse dériver de l'autre.
     */
    val Corner: Dp get() = CornerOf(Diameter)

    /** **L'épaisseur du trait : 1 dp** — la marche `Hair` de la maison, portée par les deux lisérés. */
    val StrokeWidth: Dp = 1.dp

    /** **La taille du glyphe : 15 dp** — un peu moins de la moitié du disque, comme la rune. */
    val Glyph: Dp = 15.dp

    /**
     * **L'écart au bord de la carte : la marche `Tight` de la maison (8 dp).** C'est lui qui pose la
     * pastille dans le coin haut-droit d'une carte, et lui seul : le site d'appel ne le réécrit pas.
     */
    val Inset: Dp = OnyxSocle.Tight

    /** **Le diamètre du point de présence : 11 dp** — l'état d'une conversation, porté par la marque. */
    val PresenceDiameter: Dp = 11.dp

    /** **Son écart au bord de la pastille : 1 dp** — il mord le disque, il ne le quitte pas. */
    val PresenceInset: Dp = 1.dp

    /** Le rayon de coin d'une pastille de diamètre [diameter] : un cercle, toujours. */
    fun CornerOf(diameter: Dp): Dp = diameter / 2

    /**
     * Les tailles admises d'une marque. Une taille hors de cette liste est une improvisation : le
     * verrou la refuse.
     */
    val Sizes: List<Dp> = listOf(32.dp, 40.dp, 56.dp)

    /** Le seuil du glyphe : c'est **du texte**, donc 4,5:1. */
    const val GlyphMin: Float = OnyxContrast.TEXT_MIN

    /** Le seuil des lisérés et du point de présence : **un trait**, donc 3:1. */
    const val EdgeMin: Float = OnyxContrast.ICON_MIN

    /**
     * **La géométrie est-elle celle de la maison ?** Un disque rond, un trait qui tient dans le
     * disque, un glyphe qui tient dans le disque moins ses quatre épaisseurs de trait. Le verrou
     * interroge ce verdict pour chaque site d'appel qu'il relève.
     */
    fun fitsGeometry(
        diameter: Float = Diameter.value,
        corner: Float = Corner.value,
        stroke: Float = StrokeWidth.value,
        glyph: Float = Glyph.value,
    ): Boolean =
        abs(corner - diameter / 2f) <= 0.01f &&
            stroke > 0f &&
            2f * stroke < diameter &&
            glyph > 0f &&
            glyph <= diameter - 4f * stroke

    // =============================================================================================
    // 2 · L'identité — une seule résolution, la même pour tous les écrans
    // =============================================================================================

    /** Le glyphe d'une identité : **son initiale**, ou **la rune de la maison**. */
    sealed interface Sign {
        /** L'initiale du nom (l'émoji quand il n'y a pas de lettre), en capitale. */
        data class Initial(val text: String) : Sign

        /** Le signe de la maison — la rune du lanceur — pour le bot principal. */
        data object House : Sign
    }

    /**
     * **Le glyphe d'un profil.** Le bot principal (profil `default`, celui que la maison appelle
     * « Onyx ») porte **la rune du lanceur** ; tout autre bot porte **son initiale**. Un nom sans
     * lettre, sans chiffre et sans émoji reçoit la rune plutôt qu'une pastille vide : une marque
     * muette n'est pas une marque.
     */
    fun glyphFor(profile: String): Sign {
        val name = profile.trim()
        if (name.isEmpty() || name.equals("default", ignoreCase = true)) return Sign.House
        val initial = BotFace.initialFor(name)
        return if (initial.isEmpty()) Sign.House else Sign.Initial(initial)
    }

    /** La teinte de l'identité : le **profond du visage du bot**, le même partout ([botFace]). */
    fun tintFor(profile: String, faceCode: String?, theme: ResolvedTheme): Color =
        BotFace.gradient(botFace(profile, faceCode).colour, theme).second

    /**
     * Le disque peint : la teinte de l'identité, **écartée juste assez** pour que son encre tienne
     * [GlyphMin]. La teinte reste celle du bot ; seul un écart invisible au regard (l'indigo profond
     * de la palette est à 4,36:1, il bouge de quelques pour cent) sépare une teinte qui ne porterait
     * pas son mot.
     */
    fun discFor(profile: String, faceCode: String?, theme: ResolvedTheme): Color =
        OnyxContrast.filled(tintFor(profile, faceCode, theme), GlyphMin)

    /** L'encre du glyphe sur [disc] : du blanc ou du presque-noir de la maison, mesuré. */
    fun inkFor(disc: Color): Color = OnyxContrast.inkOn(disc)

    /** Le liséré **intérieur** (au bord du disque) : l'encre du glyphe, pour le détacher du disque. */
    fun ringFor(disc: Color): Color = inkFor(disc)

    /**
     * Le liséré **extérieur** : le **contraire** de l'encre du glyphe. C'est lui qui détache la marque
     * d'une couverture sombre quand le liséré intérieur y disparaîtrait, et réciproquement.
     */
    fun outerRingFor(disc: Color): Color =
        if (inkFor(disc) == Color.White) OnyxContrast.DarkInk else Color.White

    /** Le rapport de contraste du glyphe sur son disque — jamais sous [GlyphMin]. */
    fun glyphContrast(disc: Color): Float = OnyxContrast.ratio(disc, inkFor(disc))

    /**
     * **Le rapport de séparation de la marque contre une couverture** : le meilleur des trois — le
     * disque lui-même, le liséré intérieur, le liséré extérieur. C'est lui que le verrou compare au
     * seuil [EdgeMin] pour une couverture claire, une sombre et une chargée.
     */
    fun separation(disc: Color, cover: Color): Float = maxOf(
        OnyxContrast.ratio(disc, cover),
        OnyxContrast.ratio(ringFor(disc), cover),
        OnyxContrast.ratio(outerRingFor(disc), cover),
    )

    /**
     * Les disques sur lesquels la preuve de couverture est faite : **pris dans la palette du
     * visage** ([BotFace.pair]), jamais écrits à la main — c'est la palette de la maison, et les
     * deux pôles d'encre (clair, sombre) y sont tous les deux représentés.
     */
    private val proofDiscs: List<Color> = listOf(0, 1, 3, 4, 12).map { BotFace.pair(it).second }

    /**
     * **La preuve de couverture** : le plus petit rapport de séparation atteignable, sur toute
     * luminance de couverture et sur les disques de la palette. Le minimum est le seuil de la maison
     * ([EdgeMin]) — la marque se détache donc d'une couverture claire, sombre **ou** chargée, sans
     * rien connaître d'elle. Le verrou relit ce nombre.
     */
    fun coverageProof(samples: Int = 256): Float {
        var worst = Float.MAX_VALUE
        for (disc in proofDiscs) {
            for (index in 0..samples) {
                val level = index.toFloat() / samples
                worst = minOf(worst, separation(disc, Color(level, level, level)))
            }
        }
        return worst
    }

    // =============================================================================================
    // 3 · L'état — le point de présence, une géométrie et une teinte de la maison
    // =============================================================================================

    /** L'état qu'une conversation montre **sur** sa marque : un tour, une question, du neuf. */
    enum class Presence { RUNNING, WAITING, UNREAD }

    /**
     * La teinte d'un état : l'accent pour un tour en cours ou du neuf, l'ambre pour une question qui
     * attend. `null` : rien à dire — aucune pastille, aucun mensonge.
     */
    fun presenceTint(presence: Presence?, theme: ResolvedTheme): Color? = when (presence) {
        Presence.RUNNING, Presence.UNREAD -> theme.accent
        Presence.WAITING -> theme.warning
        null -> null
    }

    /**
     * La teinte du point, **écartée du disque** juste assez pour tenir [EdgeMin] sur lui : c'est le
     * fond réellement peint sous le point — le disque opaque —, jamais la page ni la couverture.
     *
     * La dérive va du côté qui a de la place : au-dessus du point d'équilibre ([Balance]), c'est le
     * noir qui éloigne le plus ; en dessous, le blanc. La teinte d'état est conservée tant qu'elle se
     * lit déjà ; sinon elle s'approche du côté libre **du plus petit pas qui suffit** (dichotomie :
     * le résultat ne dépend pas du chemin, donc il se mesure).
     */
    fun presenceInk(presence: Presence?, disc: Color, theme: ResolvedTheme): Color? {
        val tint = presenceTint(presence, theme) ?: return null
        if (OnyxContrast.ratio(tint, disc) >= EdgeMin) return tint
        val towards = if (OnyxContrast.luminance(disc) > Balance) Color.Black else Color.White
        if (OnyxContrast.ratio(towards, disc) < EdgeMin) return tint
        var none = 0f
        var enough = 1f
        repeat(24) {
            val middle = (none + enough) / 2f
            if (OnyxContrast.ratio(androidx.compose.ui.graphics.lerp(tint, towards, middle), disc) >= EdgeMin) {
                enough = middle
            } else {
                none = middle
            }
        }
        return androidx.compose.ui.graphics.lerp(tint, towards, enough)
    }

    /**
     * **Le point d'équilibre** du disque : la luminance où le blanc et le noir s'en écartent
     * également (`(L + 0,05)² = 1,05 × 0,05`). En dessous, c'est le blanc qui a de la place ; au
     * dessus, le noir. C'est le seuil du choix de la dérive du point de présence.
     */
    const val Balance: Float = 0.179f

    // =============================================================================================
    // 4 · Le dessin
    // =============================================================================================

    /** L'anatomie peinte d'une marque : ses couleurs arrêtées une fois, mesurables à part. */
    data class Paint(
        val disc: Color,
        val ink: Color,
        val ring: Color,
        val outerRing: Color,
        val sign: Sign,
    )

    /** L'anatomie peinte d'une identité, dans une apparence donnée. */
    fun paintFor(profile: String, faceCode: String?, theme: ResolvedTheme): Paint {
        val disc = discFor(profile, faceCode, theme)
        return Paint(
            disc = disc,
            ink = inkFor(disc),
            ring = ringFor(disc),
            outerRing = outerRingFor(disc),
            sign = glyphFor(profile),
        )
    }

    /**
     * Le dessin de la marque dans le carré courant : les deux lisérés, le disque, le glyphe.
     *
     * **Toutes les dimensions sont en pixels du canevas** — c'est le seul endroit où les constantes
     * de la maison (en dp) sont traduites, et elles le sont par le site d'appel ([IdentityMark]).
     */
    fun DrawScope.drawMark(
        paint: Paint,
        diameter: Float,
        stroke: Float,
        glyphSize: Float,
        presence: Color? = null,
        presenceDiameter: Float = 0f,
        presenceInset: Float = 0f,
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f
        // Les deux lisérés sont **dans** le disque : l'empreinte en mise en page est le diamètre.
        drawCircle(
            color = paint.outerRing,
            radius = radius - stroke / 2f,
            center = centre,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = paint.ring,
            radius = radius - 3f * stroke / 2f,
            center = centre,
            style = Stroke(width = stroke),
        )
        val discRadius = radius - 2f * stroke
        drawCircle(color = paint.disc, radius = discRadius, center = centre)
        when (val sign = paint.sign) {
            is Sign.House -> {
                // La rune de la maison : elle occupe 0,40 du disque, son anatomie d'emblème.
                val height = minOf(discRadius * 1.6f, glyphSize * 1.7f)
                val (points, half) = OnyxRune.placed(centre.x, centre.y, height)
                val path = Path().apply {
                    points.forEachIndexed { index, vertex ->
                        if (index == 0) moveTo(vertex.x, vertex.y) else lineTo(vertex.x, vertex.y)
                    }
                }
                drawPath(
                    path = path,
                    color = paint.ink,
                    style = Stroke(width = half * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            is Sign.Initial -> drawIntoCanvas { canvas ->
                val androidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = glyphSize
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.SANS_SERIF,
                        android.graphics.Typeface.BOLD,
                    )
                    color = paint.ink.toArgb()
                }
                val metrics = androidPaint.fontMetrics
                canvas.nativeCanvas.drawText(
                    sign.text,
                    centre.x,
                    centre.y - (metrics.ascent + metrics.descent) / 2f,
                    androidPaint,
                )
            }
        }
        // Le point de présence : son contour est l'encre de la marque (la même épaisseur que les
        // lisérés), sa teinte est celle de l'état, mesurée sur le disque.
        val dot = presence ?: return
        val dotRadius = presenceDiameter / 2f
        val dotCentre = Offset(
            x = diameter - dotRadius - presenceInset,
            y = diameter - dotRadius - presenceInset,
        )
        drawCircle(color = dot, radius = dotRadius, center = dotCentre)
        drawCircle(
            color = paint.ink,
            radius = dotRadius + stroke / 2f,
            center = dotCentre,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * **La marque d'identité d'un bot ou d'une conversation, dessinée.**
 *
 * Une pastille pleine de sa couleur, son initiale (ou la rune de la maison) à l'encre mesurée, deux
 * lisérés d'appui — et, quand une conversation a quelque chose à dire, un point de présence sur la
 * pastille. Aucune dimension n'est écrite ici : tout vient de [IdentityMark].
 */
@Composable
fun IdentityMark(
    profile: String,
    faceCode: String? = null,
    modifier: Modifier = Modifier,
    diameter: Dp = IdentityMark.Diameter,
    presence: IdentityMark.Presence? = null,
    contentDescription: String? = null,
) {
    val theme = LocalOnyxTheme.current
    val paint = IdentityMark.paintFor(profile, faceCode, theme)
    val dot = IdentityMark.presenceInk(presence, paint.disc, theme)
    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { this.contentDescription = contentDescription ?: profile },
    ) {
        with(IdentityMark) {
            // Les constantes de la maison sont **en dp** : le canevas, lui, compte en **pixels**.
            // C'est ici, et nulle part ailleurs, que la traduction se fait.
            drawMark(
                paint = paint,
                diameter = diameter.toPx(),
                stroke = StrokeWidth.toPx(),
                glyphSize = Glyph.toPx(),
                presence = dot,
                presenceDiameter = PresenceDiameter.toPx(),
                presenceInset = PresenceInset.toPx(),
            )
        }
    }
}
