package com.onyx.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * dégradé de son visage, le même partout), **le VISAGE de son bot** au centre ([faceFor] → [BotLook],
 * la créature tirée de son nom de profil — la même que la fiche, l'en-tête, le volet et les onglets),
 * un **liséré autour du visage**, et **deux lisérés d'appui** qui la détachent de n'importe quelle
 * couverture.
 *
 * **Le visage a remplacé un glyphe monochrome, et cela change la PREUVE.** Un glyphe se prouvait
 * **par construction** : `OnyxContrast.filled` écartait la teinte juste assez pour que `inkOn` y
 * tienne [GlyphMin], et la marque portait sa garantie dans son propre code. Un **visage est
 * coloré** : sa teinte n'est pas choisie par la marque, elle vient de `BotFace.gradient`, et son
 * corps est peint dans **exactement la couleur du disque** — le rapport corps ↔ disque vaut donc
 * `1,00:1` (mesuré : [bodyOnDisc]). Il faut donc trois preuves distinctes au lieu d'une :
 *
 *  1. **le disque se détache de la couverture** — c'est [coverageProof], inchangé : le liséré
 *     intérieur est l'encre de la marque, l'extérieur son **contraire**, et pour toute luminance de
 *     couverture l'un des deux tient [EdgeMin] ([separation] ≥ [EdgeMin]) ;
 *  2. **le liséré du VISAGE se détache du disque** — c'est [faceContrast] : à 1 dp et à l'encre de
 *     la marque (donc ≥ [GlyphMin] sur le disque par construction), il porte la **silhouette** du
 *     visage, puisque le corps du visage, lui, se confond avec le disque ;
 *  3. **les traits du visage se lisent sur son corps** — les yeux, la bouche, l'encre des sourcils :
 *     la preuve lit l'encre **réellement peinte** ([featuresFor] : `BotAvatar` passe au blanc sous
 *     0,03 de luminance, donc un visage noir reçoit une encre blanche, et c'est celle-là qu'on
 *     mesure).
 *
 * **Aucun seuil n'est abaissé.** Le liséré est un trait (3:1, [EdgeMin]) et le disque doit tenir
 * [EdgeMin] contre la couverture ; les traits du visage sont des marques, donc [EdgeMin] aussi. Le
 * verrou (`IdentityMarkLockTest`) relit les trois, sur les deux apparences, et nomme la couleur
 * fautive.
 *
 * **Un cas qui ne passe pas, et il est dit plutôt que maquillé** : un visage dont le corps est
 * presque noir sur un disque presque noir perdrait sa silhouette si le liséré était de la même
 * encre ; `featuresFor` bascule alors les traits au blanc, et le liséré du visage reste l'encre
 * mesurée — c'est la valeur la plus basse de [faceProof] qui le dit, apparence par apparence.
 *
 * **Les valeurs sont publiques et uniques** : [Diameter], [Corner], [Face], [StrokeWidth], [Inset].
 * Aucun site d'appel n'écrit de dimension : le verrou relit ces constantes, échoue si deux écrans
 * montrent deux teintes pour la même identité, si un contraste passe sous le seuil dans une
 * apparence, ou si une marque sort de la géométrie (voir [fitsGeometry]).
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

    /**
     * **Le diamètre du VISAGE : le disque moins ses deux lisérés — 28 dp sur une marque de 32.**
     *
     * C'est la valeur qui remplace l'ancienne taille de glyphe (15 dp) : la marque ne porte plus un
     * signe monochrome mais **le visage du bot** ([BotLook], la même résolution que partout
     * ailleurs), et un visage occupe le disque. La constante est **dérivée** du diamètre et du
     * trait, comme le rayon de coin : aucune des trois ne peut dériver des autres.
     */
    val Face: Dp get() = Diameter - StrokeWidth * 4

    /**
     * **L'écart au bord de la carte : la marche `Tight` de la maison (8 dp).** C'est lui qui pose la
     * pastille dans le coin haut-droit d'une carte, et lui seul : le site d'appel ne le réécrit pas.
     */
    val Inset: Dp = OnyxBase.Tight

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
     * disque, un visage qui tient dans le disque moins ses quatre épaisseurs de trait. Le verrou
     * interroge ce verdict pour chaque site d'appel qu'il relève.
     */
    fun fitsGeometry(
        diameter: Float = Diameter.value,
        corner: Float = Corner.value,
        stroke: Float = StrokeWidth.value,
        face: Float = Face.value,
    ): Boolean =
        abs(corner - diameter / 2f) <= 0.01f &&
            stroke > 0f &&
            2f * stroke < diameter &&
            face > 0f &&
            face <= diameter - 4f * stroke

    // =============================================================================================
    // 2 · L'identité — une seule résolution, la même pour tous les écrans
    // =============================================================================================

    /**
     * **Le visage d'un profil** — la même résolution que partout ailleurs ([botFace], sur le nom
     * de profil et le code enregistré). C'est ce qui remplace l'ancien signe monochrome : la marque
     * ne dit plus l'initiale, elle montre **la créature du bot**.
     *
     * Conséquence sur la preuve, et c'est tout l'objet de [faceProof] : un glyphe monochrome se
     * prouvait **par construction** (l'encre est choisie contre son disque) ; un **visage est
     * coloré**, donc il faut mesurer trois choses au lieu d'une — que le disque se détache de la
     * couverture (c'est [coverageProof], inchangé), que le **liséré du visage** se détache du
     * disque, et que les **traits du visage** se détachent de son corps.
     */
    fun faceFor(profile: String, faceCode: String?): BotFace = botFace(profile, faceCode)

    /** L'encre des traits du visage — celle de `BotAvatar` (`0.09, 0.09, 0.13`). */
    val FeatureInk: Color = Color(0.09f, 0.09f, 0.13f)

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

    /**
     * **Le rapport d'encre de la marque sur son disque** — l'ancien contraste du glyphe, conservé
     * pour la trace : il mesure l'encre qui porte le liséré du visage et le contour du point de
     * présence. Jamais sous [GlyphMin] (du texte, 4,5:1), par construction ([discFor] écarte la
     * teinte juste assez).
     */
    fun markInkContrast(disc: Color): Float = OnyxContrast.ratio(disc, inkFor(disc))

    /** Le **corps du visage** : son clair et son profond ([BotFace.gradient], ce que `BotAvatar` peint). */
    fun faceBodyFor(profile: String, faceCode: String?, theme: ResolvedTheme): Pair<Color, Color> =
        BotFace.gradient(botFace(profile, faceCode).colour, theme)

    /**
     * **L'encre RÉELLE des traits du visage.** `BotAvatar` passe au blanc sur un corps presque noir
     * (`deep` sous 0,03 de luminance) : la preuve doit lire l'encre **peinte**, pas celle du cas
     * général — c'est exactement le piège qui avait laissé passer « l'encre annoncée n'est pas
     * l'encre peinte ».
     */
    fun featuresFor(bodyDeep: Color): Color =
        if (OnyxContrast.luminance(bodyDeep) < 0.03f) Color.White.copy(alpha = 0.9f) else FeatureInk

    /**
     * **Le visage se détache-t-il de son disque, et se lit-il dedans ?** Mesuré, pas supposé : le
     * liséré du visage (l'encre de la marque, 1 dp) contre **le disque** et contre **le corps du
     * visage** — clair et profond, parce que le corps est un dégradé —, et les **traits** du visage
     * contre ce même corps. Le minimum des cinq est la valeur qui décide.
     *
     * **Le défaut mesuré, et son correctif.** Le corps du visage est peint dans le **même** dégradé
     * que le disque (le profond de son visage) : le rapport corps ↔ disque vaut donc **1,00:1**, et
     * sans liséré le visage ne se lit pas comme un visage — seuls ses yeux et sa bouche se voient.
     * Ce sont donc **les deux lisérés d'appui** — déjà là, l'encre et son contraire — qui portent la
     * silhouette : l'encre tient [GlyphMin] sur le disque par construction, et le contraire prend le
     * relais là où le visage est lavé. La mesure porte sur **les deux**, contre chacun des trois
     * fonds que le bord du visage touche vraiment (le disque, le bout clair du corps, son bout
     * profond), plus les traits sur le corps.
     */
    fun faceContrast(
        disc: Color,
        bodyLight: Color,
        bodyDeep: Color,
        featureInk: Color = FeatureInk,
    ): Float {
        // **Le bord du visage est cerné par les DEUX lisérés de la maison** — l'encre de la marque
        // (au ras du visage) et son **contraire** (juste au-dessus) : pour toute luminance de bord,
        // l'un des deux tranche. Mesuré : avec **un seul** liséré, un visage indigo ne tenait que
        // **1,87:1** sur son bout clair (l'encre blanche sur un corps lavé) ; avec les deux, le
        // pire tombe sur la paire et non sur l'un d'eux.
        fun meilleur(fond: Color): Float = maxOf(
            OnyxContrast.ratio(ringFor(disc), fond),
            OnyxContrast.ratio(outerRingFor(disc), fond),
        )
        return minOf(
            meilleur(disc),
            meilleur(bodyLight),
            meilleur(bodyDeep),
            OnyxContrast.ratio(featureInk, bodyDeep),
            OnyxContrast.ratio(featureInk, bodyLight),
        )
    }

    /** **Le corps du visage posé sur son disque** — 1,00:1 quand les deux sont la même couleur. */
    fun bodyOnDisc(bodyDeep: Color, disc: Color): Float = OnyxContrast.ratio(bodyDeep, disc)

    /** Les couleurs de visage de l'épreuve : la palette de la maison, plus les deux de Onyx. */
    private val proofColours: List<Int> =
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15) + BotFace.themed

    /**
     * **La preuve du VISAGE**, dans une apparence : le plus petit rapport atteignable sur toutes les
     * couleurs de visage de la maison. Le minimum est le seuil de la maison ([EdgeMin], 3:1 — un
     * liséré est un trait) ; le verrou relit ce nombre, sur les deux apparences.
     */
    fun faceProof(theme: ResolvedTheme): Float {
        var worst = Float.MAX_VALUE
        for (colour in proofColours) {
            val (light, deep) = BotFace.gradient(colour, theme)
            val disc = OnyxContrast.filled(deep, GlyphMin)
            worst = minOf(worst, faceContrast(disc, light, deep, featuresFor(deep)))
        }
        return worst
    }

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
        /** **Le visage du bot** — la même résolution que la fiche, l'en-tête et le volet. */
        val face: BotFace,
    )

    /** L'anatomie peinte d'une identité, dans une apparence donnée. */
    fun paintFor(profile: String, faceCode: String?, theme: ResolvedTheme): Paint {
        val disc = discFor(profile, faceCode, theme)
        return Paint(
            disc = disc,
            ink = inkFor(disc),
            ring = ringFor(disc),
            outerRing = outerRingFor(disc),
            face = faceFor(profile, faceCode),
        )
    }

    /**
     * **Le socle de la marque** : les deux lisérés d'appui et le disque opaque. C'est ce qui se
     * dessine **sous** le visage, et c'est ce qui prouve la séparation contre la couverture.
     *
     * Toutes les dimensions sont en pixels du canevas — c'est le seul endroit où les constantes de
     * la maison (en dp) sont traduites, et elles le sont par le site d'appel (`IdentityMark`).
     */
    fun DrawScope.drawMarkBase(paint: Paint, diameter: Float, stroke: Float) {
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
        drawCircle(color = paint.disc, radius = radius - 2f * stroke, center = centre)
    }

    /**
     * **Le dessus de la marque** : le **liséré du visage** — c'est lui qui porte la silhouette,
     * puisque le corps du visage est peint dans la couleur du disque ([faceContrast]) — puis le
     * point de présence, dont le contour est l'encre de la marque.
     */
    fun DrawScope.drawMarkTop(
        paint: Paint,
        diameter: Float,
        stroke: Float,
        presence: Color? = null,
        presenceDiameter: Float = 0f,
        presenceInset: Float = 0f,
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f
        // Le liséré du visage : au bord de son cercle, à l'encre mesurée de la marque.
        drawCircle(
            color = paint.ink,
            radius = radius - 2f * stroke,
            center = centre,
            style = Stroke(width = stroke),
        )
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
 * Une pastille pleine de sa couleur, **le visage de son bot** au centre (et non plus un glyphe
 * monochrome), un liséré autour du visage, deux lisérés d'appui — et, quand une conversation a
 * quelque chose à dire, un point de présence sur la pastille. Aucune dimension n'est écrite ici :
 * tout vient de [IdentityMark].
 *
 * **Pourquoi le visage est-il posé par une composition et non par le canevas ?** Le visage est
 * celui de partout ([BotLook] : la même créature que la fiche, l'en-tête et le volet, tirée du nom
 * de profil) : le redessiner ici en serait une seconde version, qui divergerait. La marque empile
 * donc trois couches — le socle (lisérés + disque) au canevas, le visage au-dessus, puis son liséré
 * et le point d'état — sur **une seule** empreinte de [IdentityMark.Diameter].
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
    val face = IdentityMark.Face
    Box(
        modifier = modifier
            .size(diameter)
            .semantics { this.contentDescription = contentDescription ?: profile },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            with(IdentityMark) {
                // Les constantes de la maison sont **en dp** : le canevas, lui, compte en **pixels**.
                // C'est ici, et nulle part ailleurs, que la traduction se fait.
                drawMarkBase(paint, diameter.toPx(), StrokeWidth.toPx())
            }
        }
        // Le visage, dans le disque : la même source que partout, à plat (il ne cligne pas dans une
        // marque de 32 dp) et sans monogramme — la marque ne porte plus d'initiale.
        Box(Modifier.size(face).clip(CircleShape)) {
            BotLook(
                name = profile,
                faceCode = faceCode,
                size = face,
                showsInitial = false,
                blinks = false,
            )
        }
        Canvas(Modifier.matchParentSize()) {
            with(IdentityMark) {
                drawMarkTop(
                    paint = paint,
                    diameter = diameter.toPx(),
                    stroke = StrokeWidth.toPx(),
                    presence = dot,
                    presenceDiameter = PresenceDiameter.toPx(),
                    presenceInset = PresenceInset.toPx(),
                )
            }
        }
    }
}
