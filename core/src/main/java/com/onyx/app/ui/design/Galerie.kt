package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.delay

/**
 * **Les blocs du langage « Galerie », en composables — dont le BLOC DE CONVERSATION.**
 *
 * **Ce que ce fichier est devenu, et pourquoi.** Il portait une carte de conversation **illustrée** :
 * une couverture générative pleine largeur (le fluide animé, ou ses trois compositions abstraites)
 * avec un voile de pied et du texte blanc posé dessus. Le propriétaire, capture à l'appui — les
 * cartes encadrées en rouge autour d'une « grosse bille grise » —, a remplacé cette direction, mot
 * pour mot : *« pour les blocs de conversation tu vas pas mettre d'images, ça va être très simple :
 * une plaque de verre avec liquid glass, le visage du bot toujours en rapport avec le nom et synchro
 * si je change son visage, le visage doit être animé »*.
 *
 * La carte est donc **[BlocConversation]** : une **plaque de verre** — le même verre que les autres
 * plaques de la maison ([GlassSurface.CONVERSATION_CARD] : la teinte franche de `VerreTeinte.kt`,
 * l'arête d'[OnyxEdge], le flou de la source, et **aucun grain**) — portant **le visage de son bot**,
 * résolu par la **seule** clé de la maison ([botFace], donc [BotLook]) sur le nom de profil, et
 * **animé** par les pouls de repos ([BotIdle]).
 *
 * **Le matériau de la planche n'est pas supprimé en silence.** [FluideCover.FluideConversationCover],
 * [ConversationCover] et les trois compositions de [ChoixCouverture] restent déclarés, nommés et
 * documentés : ce sont des **matériaux de la planche**, que les cartes **ne peignent plus** (le
 * propriétaire a refusé les trois compositions, la troisième comprise).
 *
 * Un seul accent par écran reste la règle : il n'y a plus de filet signature sur les cartes (il
 * n'existait que pour séparer un titre d'une image). La pastille d'état d'une carte est la **marque
 * d'identité** ([IdentityMark]), posée dans le coin haut-droit — elle ne bouge pas, et elle vient
 * d'être passée au visage.
 */

/**
 * **Les constantes du bloc de conversation** — nommées, dérivées des jetons de la maison, et
 * mesurables : c'est ce que le verrou lit pour dire « la taille du visage est 44 dp », « la hauteur
 * minimale est 76 dp », « l'encre tient 4,5:1 sur la pire plaque ».
 */
object BlocConversation {

    // ---------------------------------------------------------------------------------------------
    // **Les mesures d'encre, partagées par toutes les cartes.**
    //
    // Elles ne dépendent que de l'apparence (`ResolvedTheme`, dont l'instance est stable tant que le
    // thème ne change pas) : les recalculer **par carte** faisait payer à chaque carte qui entre
    // dans la liste des dizaines de milliers de rapports de contraste — c'est ce qui hachait le
    // défilement du volet. Ces caches ne changent pas un pixel : le dessin lit exactement les mêmes
    // chiffres, une seule fois.
    //
    // La clé est l'instance de thème : elle est retenue par `remember(theme, dark, fonts, colors,
    // pureBlack, halo)` dans [com.onyx.app.ui.theme.OnyxTheme], donc il n'y a pas d'entrée morte à
    // chaque image.
    // ---------------------------------------------------------------------------------------------
    private val zoneAlphas = java.util.concurrent.ConcurrentHashMap<ResolvedTheme, Float>()
    private val groundsCache = java.util.concurrent.ConcurrentHashMap<ResolvedTheme, List<Color>>()
    private val inks = java.util.concurrent.ConcurrentHashMap<Pair<ResolvedTheme, Color>, Color>()
    private val rowProfiles =
        java.util.concurrent.ConcurrentHashMap<Triple<ResolvedTheme, Float, Float>, List<Float>>()

    /**
     * **La taille du visage : 44 dp** — la vignette de la maison ([OnyxSocle.ThumbnailSize], la même
     * valeur que la vignette d'un fichier). Le visage est le « média » d'une ligne, il prend la
     * taille d'un média de ligne ; plus grand, la carte cesse d'être une ligne qu'on balaie.
     */
    val FaceSize: Dp = OnyxSocle.ThumbnailSize

    /**
     * **La hauteur minimale de la plaque : 76 dp** — le visage plus le rembourrage de carte des deux
     * côtés ([OnyxSocle.CardPadding], 16 dp). La plaque **grandit** au-delà si les trois lignes de
     * texte en ont besoin : elle est une plaque qui porte son contenu, pas une vignette à hauteur
     * fixe.
     */
    val MinHeight: Dp = FaceSize + OnyxSocle.CardPadding * 2

    /** **Le coin : celui des plaques de verre de la maison** (22 dp, `GlassBarCorner`). */
    val Corner: Dp = GlassBarCorner

    /** La marche de la hiérarchie : le titre en corps (15 sp). */
    val TitleSize: TextUnit = OnyxSocle.Body

    /** La seconde ligne — l'aperçu — en méta (12 sp). */
    val PreviewSize: TextUnit = OnyxSocle.Meta

    /** L'heure (et le projet) en micro monospace (11 sp) : la plus petite marche, lue en dernier. */
    val MetaSize: TextUnit = OnyxSocle.Micro

    /**
     * **La réserve du coin haut-droit — PLUS PEINTE SUR LES CARTES.**
     *
     * Elle réservait la place de la **marque d'identité** ([IdentityMark.Diameter] +
     * [IdentityMark.Inset]) au coin haut-droit d'une carte. Le propriétaire l'a **refusée** (ronde,
     * redondante avec le visage — déjà à gauche de la carte — et moche : « supprime la petite partie
     * avec le cercle, le visage c'est moche ») : la marque **n'est plus peinte sur une carte**. La
     * constante reste **déclarée** — le composant [IdentityMark] n'est pas supprimé en silence — pour
     * que le prochain lot sache que sa présence sur une carte est **interdite**, pas oubliée.
     */
    val MarkReserve: Dp = IdentityMark.Diameter + IdentityMark.Inset

    /**
     * **Les animations du visage d'une carte.** Ce sont les **pouls de repos** de la maison
     * ([BotIdleCue]) : un clignement, un regard, un souffle, un micro-sourire, un hochement — des
     * pulsions **courtes**, séparées par 4 à 9 secondes d'immobilité totale (aucune image n'est
     * demandée entre deux pouls). C'est le choix de sobriété : une boucle continue détournerait
     * l'œil du titre, et une liste de trente cartes la paierait trente fois.
     */
    val Animations: List<BotIdleCue> = BotIdleCue.entries

    /** La surface de verre du bloc — celle de la maison, avec son arête et sans grain. */
    val Surface: GlassSurface = GlassSurface.CONVERSATION_CARD

    /** La forme de la plaque : une constante de la maison, jamais un nombre écrit sur place. */
    fun shape(): Shape = RoundedCornerShape(Corner)

    /**
     * **Le visage d'une carte** — exactement la résolution de partout ([botFace], sur le nom de
     * profil et le code enregistré). Une seule source : la marque, les avatars, la barre d'onglets
     * et la carte ne peuvent pas montrer deux créatures pour un bot.
     */
    fun visage(profile: String, faceCode: String?): BotFace = botFace(profile, faceCode)

    /**
     * **Les voiles qu'une plaque peut peindre** : le verre échantillonné (la teinte des plaques à
     * son opacité peinte) et le **repli** ([glassScrimAlpha], plus dense, celui que la vitre peint
     * elle-même quand elle n'a rien à échantillonner). Le pire cas honnête est le plus dense, mais
     * les **deux** sont mesurés : c'est ce couple qui décide de l'encre.
     */
    fun veils(theme: ResolvedTheme): List<Color> {
        val inks = glassInks(Surface, theme)
        val tint = glassTintColour(Surface, theme.dark)
        return listOf(
            tint.copy(alpha = glassPaintTintAlpha(Surface, theme.dark, inks)),
            tint.copy(alpha = glassScrimAlpha(Surface, theme.dark, inks)),
        )
    }

    /** Le voile le plus dense de la plaque — la teinte la plus engagée. */
    fun veil(theme: ResolvedTheme): Color = veils(theme).maxBy { it.alpha }

    /** Ce qu'il y a **derrière** la plaque : le pire cas est le blanc pur ou le noir pur. */
    fun ground(theme: ResolvedTheme, under: Color, veil: Color): Color = compositedBackdrop(veil, under)

    // =============================================================================================
    // LA ZONE D'ENCRE PROTÉGÉE — le levier qui rend le texte lisible sur une plaque traversante
    // =============================================================================================

    /**
     * **LA ZONE D'ENCRE PROTÉGÉE — un voile local sous le texte, jamais la plaque entière.**
     *
     * La densité traversante de la plaque (**0,57** en clair, la décision du propriétaire) laisse
     * passer **43 %** du fond. Une même encre ne peut alors pas tenir 4,5:1 sur **les quatre** fonds
     * qu'une carte peut peindre (le verre et son repli, chacun sur du blanc pur et du noir pur).
     * Mesuré au rouge de la passe, apparence claire : le noir pur sur le fond le plus sombre =
     * **3,55:1**, le blanc pur = **1,51:1**, l'encre du thème = **1,26:1** — toutes sous le seuil.
     *
     * La réponse n'est ni de densifier la plaque (elle resterait traversante et teintée, la décision
     * du propriétaire) ni d'abaisser le seuil : c'est le levier proposé et jamais fait — un **voile
     * local sous le texte** (le titre, l'aperçu, l'heure), qui rapproche le fond **réellement derrière
     * le texte** de la page de l'apparence. L'encre de la maison y retrouve son seuil, sans rien
     * changer à la matière de la carte.
     *
     * **La zone n'est jamais opaque** : son opacité est le plus petit pas qui suffit
     * ([inkZoneAlpha]), jamais 1.
     */
    fun inkZoneColour(theme: ResolvedTheme): Color = theme.pageColor

    /**
     * **Le pas du voile de la zone : le plus petit qui suffit, jamais un chiffre rond choisi.**
     * Le seuil n'est pas touché (`OnyxContrast.TEXT_MIN` reste 4,50) : c'est la **géométrie** de la
     * zone qui change (voir [InkZoneFade]), jamais l'encre qu'elle garantit.
     */
    private const val InkZoneStep: Float = 0.02f

    /**
     * **Le plancher de la zone : elle existe dans les deux apparences**, même quand la plaque
     * suffirait seule.
     */
    private const val InkZoneFloor: Float = 0.16f

    /**
     * **LA BANDE D'EFFACEMENT DE LA ZONE — 16 dp : tout le rembourrage de la carte, et pas un dp de
     * plus.**
     *
     * Le défaut du propriétaire, capture à l'appui : *« tu as un encadré dans le verre, ça fait deux
     * couches pour rien »*. La zone d'encre du lot précédent était un **rectangle à coins arrondis**
     * posé dans la plaque : sa couleur diffère de celle de la plaque (c'est la page de l'apparence,
     * à [inkZoneAlpha]), donc **son bord se voyait**. Mesuré au pixel sur la liste, apparence claire
     * (`qa/visages-synchro/shots/liste-clair-repos.png`) : la zone saute de **L ≈ 176 à L ≈ 188** —
     * une marche de **+12** sur toute la largeur, à `y = 1715` (bord haut) et `y = 1807` (bord bas,
     * −12) — l'« encadré » que l'œil lit comme une seconde plaque.
     *
     * Le remède ne touche ni la densité de la plaque (elle reste **traversante**) ni le seuil : la
     * zone s'**efface** vers le transparent sur cette bande, en haut et en bas de la plaque. Il n'y a
     * donc **plus aucune arête** : au bord, son opacité vaut **zéro** (l'écart de luminance au bord
     * est nul — voir [inkZoneAlphaAt]), et ses bords **gauche et droit** tombent sur le bord de la
     * plaque (la zone prend toute la largeur, recadrée par la forme arrondie du verre), donc il n'y a
     * pas de bord vertical non plus.
     *
     * **Pourquoi 16 dp, et pas 12 (mesuré à l'appareil, lot `liquide-unifie`).** Le lot
     * « encadre-verre » avait laissé **deux défauts de dessin** dans la bande (voir
     * [inkZoneBandRows] : des sous-rangs qui se **chevauchaient**, donc une bande plus **dense** que
     * son plateau, et une bande basse **à l'envers**) — réparés, il restait que la bande était
     * **courte** : à 12 dp, un écran à **densité 1** ne dispose que de **12 pixels** pour descendre de
     * `inkZoneAlpha`, soit une marche de ≈ **2,4 niveaux de luminance par pixel**, au-dessus du seuil
     * du verrou (**un niveau sur 255**) — le seuil n'étant pas négociable, c'est la **bande** qui
     * s'allonge. À 16 dp, la marche par pixel passe sous le seuil à **toutes** les densités, **1×
     * compris** (relevé du verrou : voir `BlocsVerreVerrouTest`, section 5 bis).
     *
     * **Pourquoi 16 dp tient encore la garantie.** Le texte d'une carte est posé dans le rembourrage
     * de la plaque ([OnyxSocle.CardPadding], **16 dp**) : le titre, l'aperçu et l'heure sont donc à
     * **16 dp au moins** du bord haut et du bord bas. La zone atteint son opacité **pleine** à
     * [InkZoneFade] du bord — donc **exactement au premier glyphe** — et tout le texte repose sur le
     * plateau, à l'opacité que [inkZoneAlpha] a mesurée. La garantie 4,5:1 est **celle du lot
     * précédent, inchangée**, sur la même pire zone (le verrou le mesure : l'opacité au rembourrage
     * vaut exactement l'opacité pleine).
     */
    val InkZoneFade: Dp = OnyxSocle.CardPadding

    /**
     * **Le nombre de marches de la bande d'effacement** — la zone n'emploie **aucun dégradé** (le
     * verrou « plus aucune image générée sur une carte » interdit l'outil de dégradé dans ce fichier,
     * et il n'est **pas** désarmé) : elle descend par **marches** dont on peut nommer la plus grande.
     * 48 marches sur 12 dp, soit **0,25 dp** par marche : la plus grande variation d'opacité vaut
     * `inkZoneAlpha / 48`, et la plus grande variation de **luminance** — celle que le verrou mesure —
     * reste **sous un niveau sur 255** (le seuil du verrou n'est pas écrit : il mesure l'écart de
     * luminance d'un niveau sur 255 sur chaque plaque).
     */
    const val InkZoneStrips: Int = 48

    /**
     * **L'opacité de la zone à `edgeDistance` du bord le plus proche** — la fonction que le **dessin**
     * échantillonne (une marche par [InkZoneStrips]) et que le **verrou** mesure. Elle vaut **zéro**
     * au bord (aucune arête : l'écart de luminance au bord est **exactement nul**) et l'opacité pleine
     * ([inkZoneAlpha]) dès [InkZoneFade], donc partout où le texte est posé.
     */
    fun inkZoneAlphaAt(theme: ResolvedTheme, edgeDistance: Dp, full: Float = inkZoneAlpha(theme)): Float {
        val fade = InkZoneFade.value
        if (fade <= 0f) return full
        return (full * (edgeDistance.value / fade)).coerceIn(0f, full)
    }

    /**
     * **LE NOMBRE DE RANGS DE LA BANDE D'EFFACEMENT** — un rang par pixel de l'écran, jamais un
     * chevauchement.
     *
     * C'est la correction du défaut du propriétaire. La bande se dessinait en `InkZoneStrips`
     * rectangles de **`pas + 1 px`** : ils se **chevauchaient** d'un pixel, donc leurs opacités se
     * **composaient** (`1 − Π(1 − aᵢ)`) et la bande atteignait ≈ `0,39` d'opacité là où son plateau
     * en vaut `0,16` — elle était **plus dense que son plateau**. Mesuré au pixel sur la liste,
     * apparence claire, carte « scout » (`qa/liquide-unifie/shots/`) : la contribution de la zone
     * valait **13,5** au creux de la bande haute contre **7,2** sur le plateau, avec une marche de
     * **−4,0 puis −3,4** de luminance à `y = 1693`/`1694`. Et la bande basse lisait la rampe **à
     * l'envers** (`ramp[i]` croissant vers le bord bas) : **presque opaque au bord** et transparente
     * vers l'intérieur — le second liseré (marche de **−3,2 puis −4,0** à `y = 1829`/`1830`).
     * Éteindre la zone fait disparaître **les deux** marches : c'est l'attribution, par ablation.
     */
    fun inkZoneBandRows(bandPx: Float): Int = kotlin.math.ceil(bandPx).toInt().coerceAtLeast(0)

    /**
     * **LE PROFIL RÉELLEMENT COMPOSITÉ PAR LA ZONE — rang par pixel.** Le dessin le suit tel quel
     * (un `drawRect` de **exactement 1 px** par rang) : chaque rang est peint **une fois**, donc le
     * profil composité **est** ce profil — il n'y a plus rien à composer.
     *
     * `bandPx` est la bande en **pixels** ([InkZoneFade] × densité), et `density` ramène le **centre**
     * de chaque rang en dp pour lire [inkZoneAlphaAt] : le chiffre du verrou et celui du dessin
     * viennent de la même fonction.
     */
    fun inkZoneRowAlphas(theme: ResolvedTheme, bandPx: Float, density: Float): List<Float> {
        if (density <= 0f) return emptyList()
        // **Le profil se mesure une fois pour toutes les cartes.** Chaque rang interroge
        // [inkZoneAlphaAt], qui interroge [inkZoneAlpha] — et celle-ci balaie les pas d'opacité et
        // les fonds de la plaque. Recalculer ce profil **par carte** coûtait des dizaines de
        // milliers de rapports de contraste à chaque carte qui entrait dans la liste : c'est ce qui
        // hachait le défilement. Le profil ne dépend que du thème, de la bande et de la densité,
        // donc il est partagé.
        return rowProfiles.computeIfAbsent(Triple(theme, bandPx, density)) {
            val rows = inkZoneBandRows(bandPx)
            // L'opacité pleine est mesurée **une fois** pour toute la bande (c'était le coût : un
            // balayage des fonds par rang), puis chaque rang garde la règle du verrou.
            val full = inkZoneAlpha(theme)
            List(rows) { i ->
                inkZoneAlphaAt(theme, (((i + 0.5f) / density).dp), full)
            }
        }
    }

    /** Les encres préférées d'une carte, dans l'ordre de la hiérarchie : titre, aperçu, heure. */
    private fun preferredInks(theme: ResolvedTheme): List<Color> =
        listOf(socleTitleInk(theme), theme.secondaryText, theme.tertiaryText)

    /** Les fonds d'une carte **avec la zone d'encre** à l'opacité [alpha] (page de l'apparence). */
    fun groundsWithZone(theme: ResolvedTheme, alpha: Float): List<Color> {
        val zone = inkZoneColour(theme).copy(alpha = alpha)
        return veils(theme).flatMap { veil ->
            listOf(Color.White, Color.Black).map { compositedBackdrop(zone, compositedBackdrop(veil, it)) }
        }
    }

    /** L'encre (règle de la maison, au point fixe) tenue au seuil sur [fonds]. */
    private fun inkOver(fonds: List<Color>, preferred: Color): Color {
        var ink = preferred
        repeat(4) {
            val worst = fonds.minBy { OnyxContrast.ratio(ink, it) }
            val next = readableInk(ink, worst)
            if (next == ink) return ink
            ink = next
        }
        return ink
    }

    /**
     * **L'opacité de la zone d'encre** : le plus petit pas (dès [InkZoneFloor]) auquel les trois
     * encres de la carte tiennent [OnyxContrast.TEXT_MIN] sur **tous** les fonds que la carte peut
     * peindre, zone comprise. Fonction **sans état** : le chiffre que le verrou lit est celui que la
     * carte peint.
     */
    fun inkZoneAlpha(theme: ResolvedTheme): Float =
        zoneAlphas.computeIfAbsent(theme) { measureInkZoneAlpha(it) }

    /** La mesure elle-même — appelée **une fois par apparence** (voir [inkZoneAlpha]). */
    private fun measureInkZoneAlpha(theme: ResolvedTheme): Float {
        var alpha = InkZoneFloor
        while (alpha < 1f) {
            val fonds = groundsWithZone(theme, alpha)
            val tient = preferredInks(theme).all { pref ->
                val ink = inkOver(fonds, pref)
                fonds.all { OnyxContrast.ratio(ink, it) >= OnyxContrast.TEXT_MIN }
            }
            if (tient) return alpha
            alpha += InkZoneStep
        }
        return 1f
    }

    /**
     * **Tous les fonds qu'une carte peut réellement peindre** : les deux aplats extrêmes (blanc pur
     * et noir pur) derrière **chacun** des deux voiles, **puis la zone d'encre protégée** posée
     * dessus. C'est cette liste — et rien d'autre — qui décide de l'encre.
     */
    fun grounds(theme: ResolvedTheme): List<Color> =
        groundsCache.computeIfAbsent(theme) { groundsWithZone(it, inkZoneAlpha(it)) }

    /** Le fond le moins favorable à [preferred] : c'est lui qui décide de l'encre. */
    fun worstGround(theme: ResolvedTheme, preferred: Color): Color =
        grounds(theme).minBy { OnyxContrast.ratio(preferred, it) }

    /**
     * **L'encre d'un rôle sur la plaque** : la teinte du thème, tenue au seuil du texte
     * ([OnyxContrast.TEXT_MIN]) contre **tous** les fonds que la carte peut peindre. La règle de la
     * maison (`readableInk`) est le seul écrivain — aucune couleur n'est choisie ici — et elle est
     * appliquée jusqu'au point fixe : tant qu'un fond la fait passer sous le seuil, elle bouge du
     * pas de la maison. Elle converge au plus en trois pas (vers le noir ou le blanc pur).
     */
    fun ink(theme: ResolvedTheme, preferred: Color): Color =
        inks.computeIfAbsent(theme to preferred) { (appearance, tint) -> inkOver(grounds(appearance), tint) }

    /** L'encre du **titre** : l'encre pleine de la maison, tenue au seuil sur la plaque. */
    fun titleInk(theme: ResolvedTheme): Color = ink(theme, socleTitleInk(theme))

    /** L'encre de la **seconde ligne** (l'aperçu) : la méta du thème, tenue au seuil. */
    fun secondInk(theme: ResolvedTheme): Color = ink(theme, theme.secondaryText)

    /** L'encre de l'**heure** et du projet : la plus discrète du thème, tenue au seuil. */
    fun thirdInk(theme: ResolvedTheme): Color = ink(theme, theme.tertiaryText)
}

/** La plaque de verre du bloc de conversation — la vitre de la maison, avec son arête. */
@Composable
fun Modifier.blocConversationSurface(): Modifier =
    this.liquidGlass(GlassSurface.CONVERSATION_CARD, BlocConversation.shape())

/**
 * **LE BLOC DE CONVERSATION — une plaque de verre, et le visage de son bot.**
 *
 * Une carte = une plaque : le verre des plaques ([GlassSurface.CONVERSATION_CARD] — la teinte
 * franche en clair, l'arête claire du haut, le flou de la source, **sans grain**), posée au coin de
 * la famille ([BlocConversation.Corner]). Aucune image, aucune composition générée, aucun voile de
 * pied : le texte repose **sur le verre**, à l'encre mesurée ([BlocConversation.titleInk] et ses
 * voisines, ≥ 4,5:1 sur la pire plaque, dans les deux apparences).
 *
 * **Le visage** ([BlocConversationFace]) est celui du bot de la conversation : `row.identity` (le
 * nom de **profil**, la clé de la maison) et `row.identityFace` (le visage **enregistré**, lu sur
 * l'identité vivante à chaque composition). Changer le visage d'un bot change donc le visage de sa
 * carte, sans qu'aucune copie n'ait été faite à la création de la conversation.
 *
 * **La hiérarchie** : le visage (44 dp) à gauche, le titre (15 sp, gras) puis l'aperçu (12 sp) — **il
 * n'y a plus de marque dans le coin haut-droit** (le propriétaire l'a refusée : ronde, redondante avec
 * le visage, et moche ; voir [BlocConversation.MarkReserve]) —, et sur la dernière ligne le projet
 * puis **l'heure** en micro-monospace (11 sp), à droite. Le texte repose sur la **zone d'encre
 * protégée** ([BlocConversation.inkZoneAlpha]) : un voile local discret qui rapproche le fond derrière
 * le texte de la page, sans opacifier la plaque.
 */
@Composable
fun BlocConversation(
    title: String,
    /**
     * **Le nom de profil du bot de cette conversation** — la clé de son visage, la même que la
     * marque d'identité, les avatars et la barre d'onglets ([botFace]).
     */
    identity: String,
    /** Le visage **enregistré** de ce profil, quand il y en a un (lu sur l'identité vivante). */
    faceCode: String?,
    preview: String? = null,
    /** Le projet de la conversation, quand la liste est filtrée sur un projet. */
    metaLeft: String? = null,
    /** **L'heure** de la conversation, déjà calculée par le volet (`sidebarAgeLabel`). */
    metaRight: String? = null,
    pinned: Boolean = false,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    // **Les trois encres sont mesurées une fois par thème, pas à chaque recomposition.** `ink()`
    // balaie les fonds possibles de la plaque et cherche l'encre qui tient 4,5:1 jusqu'au point
    // fixe : trois de ces recherches à chaque composition d'une carte, sur une liste qui en montre
    // huit, pèsent lourd pour un résultat qui ne dépend que du thème.
    val titleInk = remember(theme) { BlocConversation.titleInk(theme) }
    val previewInk = remember(theme) { BlocConversation.secondInk(theme) }
    val metaInk = remember(theme) { BlocConversation.thirdInk(theme) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = BlocConversation.MinHeight)
            .blocConversationSurface()
            // **La zone d'encre protégée** : un voile de la page de l'apparence, PLEINE PLAQUE, qui
            // s'efface vers le transparent sur ses bords haut et bas — il n'a donc **aucune arête** et
            // ne se lit pas comme une seconde plaque. Il rend le titre et l'heure lisibles (4,5:1)
            // sur la plaque traversante, sans toucher à sa densité. Il est peint **par-dessus le
            // verre et sous l'encre** : son opacité au bord est **zéro**, donc l'arête du verre
            // ([drawGlassPlateEdge]) reste intacte là où elle est posée.
            .blocConversationInkZone(theme)
            .then(if (onClick != null) Modifier.onyxClickable(onClick = onClick) else Modifier)
            .tapLabel(title),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OnyxSocle.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
        ) {
            // Le visage du bot : la même créature que partout, et elle vit.
            BlocConversationFace(name = identity, faceCode = faceCode)
            Column(
                modifier = Modifier
                    // Le texte va jusqu'au bord : la marque du coin a été retirée, et la zone d'encre
                    // est portée par la plaque (plus par ce bloc) — le rembourrage de la carte suffit
                    // à tenir les glyphes dans son plateau.
                    .weight(1f),
            ) {
                BasicText(
                    text = title,
                    localize = false,
                    style = socleTitleStyle(theme).copy(
                        fontSize = BlocConversation.TitleSize,
                        color = titleInk,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!preview.isNullOrBlank()) {
                    BasicText(
                        text = preview,
                        // **Le texte d'un message n'est pas une chaîne de l'app** : il ne se traduit
                        // pas et ne passe pas par le catalogue.
                        localize = false,
                        style = socleMetaStyle(theme).copy(
                            fontSize = BlocConversation.PreviewSize,
                            color = previewInk,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = OnyxSpace.Hair),
                    )
                }
                if (!metaLeft.isNullOrBlank() || !metaRight.isNullOrBlank() || pinned || (count ?: 0) > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = OnyxSpace.Hair),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
                    ) {
                        if (!metaLeft.isNullOrBlank()) {
                            BasicText(
                                text = metaLeft.orEmpty(),
                                localize = true,
                                style = socleMetaStyle(theme).copy(
                                    fontSize = BlocConversation.MetaSize,
                                    color = metaInk,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (pinned) {
                            // L'épingle : l'encre mesurée de la carte, jamais un blanc d'image.
                            OnyxIcon(
                                symbol = Symbols.Pin,
                                tint = metaInk,
                                size = OnyxSpace.Snug,
                                contentDescription = null,
                            )
                        }
                        if (count != null && count > 0) {
                            // Le compteur : du texte, à l'encre mesurée — plus de pastille, donc plus
                            // de surface non mesurée sur la plaque.
                            BasicText(
                                text = count.toString(),
                                localize = false,
                                style = socleMonoStyle(theme).copy(
                                    fontSize = BlocConversation.MetaSize,
                                    color = metaInk,
                                ),
                                maxLines = 1,
                            )
                        }
                        if (!metaRight.isNullOrBlank()) {
                            BasicText(
                                text = metaRight,
                                localize = false,
                                style = socleMonoStyle(theme).copy(
                                    fontSize = BlocConversation.MetaSize,
                                    color = metaInk,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * **La zone d'encre protégée d'une carte — un voile SANS ARÊTE, à la mesure de la plaque.**
 *
 * Elle couvre **toute la plaque** et s'efface vers le transparent sur une bande de
 * [BlocConversation.InkZoneFade] en haut et en bas : ses bords **gauche et droit** tombent sur le
 * bord de la plaque (donc sur la forme arrondie du verre, qui la recadre), et ses bords **haut et
 * bas** ont une opacité **nulle**. Il n'y a donc **aucune arête** à voir — la carte se lit comme
 * **une seule surface**, alors que le texte repose bien sur l'opacité mesurée
 * ([BlocConversation.inkZoneAlpha]) : le rembourrage de la carte ([OnyxSocle.CardPadding], 16 dp)
 * place les glyphes dans le **plateau** du profil ([BlocConversation.inkZoneAlphaAt]).
 *
 * **LE CADRE VIENAIT D'ICI, ET IL EST RÉPARÉ — deux causes, mesurées à l'appareil.**
 *
 * 1. **Les bandes se chevauchaient.** Elles étaient peintes en `InkZoneStrips` rectangles de
 *    `pas + 1 px` : d'un rang à l'autre, **deux ou trois** rectangles se recouvraient, et leurs
 *    opacités se **composaient** (`1 − Π(1 − aᵢ)`). Une bande visant `0,16` atteignait donc ≈ `0,39`
 *    et **contredisait le plateau**, qui restait à `0,16` — mesuré au pixel sur la liste, apparence
 *    claire, carte « scout » : la contribution de la zone valait **13,5** au creux de la bande haute
 *    contre **7,2** sur le plateau, avec une **marche de −4,0 puis −3,4** de luminance à
 *    `y = 1693`/`1694` (l'éteinte de la zone fait disparaître ces deux marches : c'est **la** preuve
 *    d'attribution).
 * 2. **La bande basse était à l'envers.** Elle lisait `ramp[i]` **croissant** avec `i` en partant de
 *    `size.height − band` : la zone était donc **transparente vers l'intérieur** et **presque opaque
 *    au bord bas** — l'exact contraire de la bande haute, et le second liseré du propriétaire
 *    (marche mesurée de **−3,2 puis −4,0** à `y = 1829`/`1830`, et un bord bas plus **clair** que le
 *    milieu de la plaque, `220,6` contre `202,8`).
 *
 * **Le remède tient en une phrase : un rang par pixel, peint une seule fois.** Chaque rang reçoit
 * l'opacité de [BlocConversation.inkZoneRowAlphas] (le profil lu **au centre du rang**), par un
 * `drawRect` de **exactement 1 px** — donc **aucun recouvrement, aucune composition**, et la bande
 * basse est le **miroir** de la haute. Le profil composité **égale** alors le profil visé, ce que le
 * verrou mesure (`BlocsVerreVerrouTest`, section 5) : plus aucune marche au-delà d'un niveau sur 255,
 * plus aucune bande plus dense que son plateau, et l'opacité au bord reste **zéro**.
 *
 * **Aucun dégradé.** Le verrou « plus aucune image générée sur une carte » interdit l'outil de
 * dégradé dans ce fichier, et il n'est **pas** désarmé : la bande se descend par **rangs** dont la
 * plus grande marche vaut `inkZoneAlpha / (bande en pixels)` — sous le niveau de luminance que le
 * verrou mesure.
 */
@Composable
private fun Modifier.blocConversationInkZone(theme: ResolvedTheme): Modifier {
    val colour = BlocConversation.inkZoneColour(theme)
    val full = remember(theme) { BlocConversation.inkZoneAlpha(theme) }
    val density = LocalDensity.current
    val band = remember(density) { with(density) { BlocConversation.InkZoneFade.toPx() } }
    // Le profil **réellement composité**, rang par pixel : le verrou lit cette fonction, le dessin la
    // suit — le chiffre du test et le chiffre peint ne peuvent pas diverger.
    val rows = remember(theme, density, band) {
        BlocConversation.inkZoneRowAlphas(theme, band, density.density)
    }
    // **Les couleurs de la bande, calculées UNE fois.** Le dessin les lisait autrefois en composant
    // `colour.copy(alpha = …)` à chaque rang et à chaque image : pendant un défilement, cela faisait
    // ~90 allocations de couleur par carte et par image, pour un résultat identique d'une image à
    // l'autre. Elles sont ici figées avec le profil — même peinture, aucune allocation dans la
    // boucle.
    val rowColours = remember(colour, rows) { rows.map { alpha -> colour.copy(alpha = alpha) } }
    val plateau = remember(colour, full) { colour.copy(alpha = full) }
    return this.drawWithContent {
        if (band <= 0f || size.height <= band * 2f) {
            // Plaque plus courte que ses deux bandes : le plateau seul (la carte est minuscule).
            drawRect(plateau)
        } else {
            val count = rows.size
            // La bande haute : un rang par pixel, **sans recouvrement** — donc sans composition.
            rows.forEachIndexed { i, a ->
                if (a > 0f) {
                    drawRect(
                        color = rowColours[i],
                        topLeft = Offset(0f, i.toFloat()),
                        size = Size(size.width, 1f),
                    )
                }
            }
            // Le plateau : l'opacité pleine, sur toute la largeur, là où le texte est posé.
            drawRect(
                color = plateau,
                topLeft = Offset(0f, count.toFloat()),
                size = Size(size.width, size.height - 2f * count),
            )
            // La bande basse : le **MIROIR** de la haute — transparente au bord bas, pleine à
            // InkZoneFade du bord. Le lot précédent la lisait à l'envers : c'était le second liseré.
            rows.forEachIndexed { i, _ ->
                val a = rows[count - 1 - i]
                if (a > 0f) {
                    drawRect(
                        color = rowColours[count - 1 - i],
                        topLeft = Offset(0f, size.height - count + i),
                        size = Size(size.width, 1f),
                    )
                }
            }
        }
        drawContent()
    }
}

/**
 * **Le visage d'une carte : le bot, et il vit.**
 *
 * **Ce qui bouge, et pourquoi c'est celui-là.** Le visage rejoue les **pouls de repos** de la maison
 * ([BotIdle] : clignement, regard, souffle, micro-sourire, hochement — [BlocConversation.Animations]),
 * tirés sur la **graine du bot** (son nom et son visage : deux bots ne clignent pas ensemble, et un
 * même bot rejoue la même vie). C'est le choix le moins coûteux comme le plus sobre : les pouls sont
 * **courts** (170 ms à 2,6 s) et séparés par **4 à 9 secondes d'immobilité totale**, pendant
 * lesquelles **aucune image n'est demandée** ; une boucle continue, elle, ferait tourner une horloge
 * par carte visible et attirerait l'œil loin du titre.
 *
 * **Rien ne se décale** : la boîte du visage est **constante** ([BlocConversation.FaceSize]) et tout
 * ce qui bouge — le regard, le clignement, la respiration, l'inclinaison — est **dessiné** dedans.
 * Le titre voisin n'est jamais poussé.
 *
 * **Sous « Réduire les animations », le visage est FIGÉ** : la boucle ne démarre pas du tout
 * (`if (reduceMotion) return@LaunchedEffect`), donc aucun pouls, aucune image demandée, et le visage
 * reste à sa pose de repos — la même règle que `Fluide.Level.FIGE` pour le matériau de la planche.
 *
 * **Une seule source d'instant** : [BotLook] reçoit `blinks = false`, puisque c'est **ce composable**
 * qui donne l'instant du visage. Deux clignotements superposés seraient un tic, pas une vie.
 */
@Composable
fun BlocConversationFace(
    name: String,
    faceCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = BlocConversation.FaceSize,
) {
    val reduceMotion = LocalReduceMotion.current
    // **Le visage se repose pendant un défilement.** Les pouls demandent une image à chaque trame
    // (`withFrameNanos`) et repeignent le visage : sur une liste de dix cartes, deux ou trois
    // visages battent à tout instant, et cette animation volait les trames du geste — c'est ce qui
    // restait « lent » après les autres correctifs. Un doigt qui fait défiler ne regarde pas un
    // clignement : le pouls se suspend, la pose reste celle du repos (la boîte du visage ne bouge
    // pas), et la vie reprend seule à l'arrêt.
    val scrolling = LocalGlassScrollFreeze.current
    // L'inclinaison propre du visage : un hochement part de là, jamais de zéro.
    val tilt = remember(name, faceCode) { BlocConversation.visage(name, faceCode).tilt.toFloat() }
    // La graine suit le bot : la carte montre la vie de **ce** bot, pas un rythme partagé.
    val seed = remember(name, faceCode) { BotIdle.seedFor(name, faceCode) }
    var frame by remember(name, faceCode) { mutableStateOf<BotIdleFrame?>(null) }

    LaunchedEffect(seed, reduceMotion, scrolling) {
        frame = null
        if (reduceMotion || scrolling) return@LaunchedEffect
        var index = 0
        while (true) {
            val pulse = BotIdle.pulse(seed, index)
            // L'attente : **aucune image demandée** entre deux pouls.
            delay(pulse.gapMillis)
            val started = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val progress = (now - started) / 1_000_000f / pulse.durationMillis
                if (progress >= 1f) break
                frame = botIdleFrame(pulse, progress, tilt)
            }
            frame = null
            index += 1
        }
    }

    val shown = frame
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        BotLook(
            name = name,
            faceCode = faceCode,
            size = size,
            mood = shown?.mood ?: BotMood.IDLE,
            showsInitial = false,
            blinks = false,
            pose = shown?.pose,
        )
    }
}

/**
 * **La ligne d'état du grand titre** : une pastille de présence, puis un texte — l'adresse de la
 * passerelle, un identifiant. C'est le bloc `headblk` de la planche (`.mt.addr`, avec son `.pdot`).
 */
@Composable
fun GalerieStatusLine(
    text: String,
    dot: Color,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        BasicText(
            text = text,
            localize = false,
            style = (if (mono) socleMonoStyle(theme) else socleMetaStyle(theme))
                .copy(color = theme.secondaryText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * **L'intitulé de section** de la planche : des **petites capitales** ouvertes (+0,16 em), un
 * **compteur en monospace** à droite. C'est le bloc `.secrow` (`.sec` + `.cnt`).
 */
@Composable
fun GalerieSectionLabel(
    text: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .padding(end = OnyxSocle.ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OnyxSocle.Tight),
    ) {
        BasicText(
            // **La majuscule vient APRÈS la traduction** : l'inverser laissait l'intitulé en
            // anglais dans une interface française.
            text = OnyxLocalization.translate(text).uppercase(),
            style = socleSectionStyle(theme).copy(color = theme.secondaryText),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            BasicText(
                text = count.toString(),
                localize = false,
                style = socleMonoStyle(theme).copy(color = theme.secondaryText),
                maxLines = 1,
            )
        }
    }
}
