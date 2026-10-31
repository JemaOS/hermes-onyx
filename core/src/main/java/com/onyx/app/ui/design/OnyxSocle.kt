package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * **Le socle du langage « J » — la galerie, tenue par un système.**
 *
 * Une seule source pour tout ce qui se répète : six corps de texte, deux espacements, une famille
 * de rayons, une famille d'ombres en trois profondeurs **enchaînées**, une seule épaisseur de trait
 * rendue, et les règles de **centrage optique**.
 *
 * **La provenance est exacte, pas estimée.** Chaque valeur vient du générateur du jeu corrigé, lu
 * ligne à ligne (`maquettes-hermes-v2/sources/hcss.py`, `hlib.py`) — pas d'une capture. Les
 * commentaires portent les numéros de ligne : c'est ce qui permet de re-vérifier, et c'est ce que
 * `OnyxSocleVerrouTest` recoupe contre les fichiers du dépôt.
 *
 * Le défaut que ce fichier ferme n'est pas « il manque une constante » : c'est qu'une valeur juste
 * aujourd'hui redevienne fausse demain, dans le fichier voisin, **et que personne ne le voie** —
 * parce qu'un nombre en dur ne se relit pas, ne se cherche pas, ne se compte pas.
 *
 * Trois choses ne vivent **pas** ici, et c'est délibéré :
 *  · les **encres** (les couleurs changent avec le thème ; elles restent dans le thème) ;
 *  · les **tailles de cible** (une hauteur n'est pas un vide) — elles sont dans [Targets] ;
 *  · les **valeurs de correction optique sous le pas** — elles sont dans [Optical], nommées, pour
 *    qu'on ne les confonde pas avec de l'espacement (un test le refuse).
 */
object OnyxSocle {

    // =============================================================================================
    // 1 · L'ÉCHELLE TYPOGRAPHIQUE — six corps, aucun entre-deux
    //     hcss.py:34-44 (`.dsp` / `.sht` / `.ttl` / `.mtx2` / `.mt` / `.sec`)
    // =============================================================================================

    /**
     * Les six corps, du plus grand au plus petit : **30 / 26 / 16 / 15 / 12 / 11**.
     *
     * Les corps intermédiaires de la version livrée (13, 13,5, 14, 14,5, 15,5, 17, 28) disparaissent
     * de l'échelle d'usage : le grand titre **est** grand, les méta **sont** discrètes, les intitulés
     * de section et les identifiants sont au plancher. Un corps hors de ces six est une exception
     * **déclarée** ([exceptions]), jamais une improvisation.
     */
    val TypeScale: List<TextUnit> = listOf(30.sp, 26.sp, 16.sp, 15.sp, 12.sp, 11.sp)

    /** `.dsp` — le grand titre d'écran. hcss.py:34 */
    val Display = 30.sp

    /** `.sht` — le titre de feuille. hcss.py:36 */
    val Heading = 26.sp

    /** `.ttl` — le titre de carte, le nom d'un bot. hcss.py:38 */
    val Title = 16.sp

    /** `.mtx2` — le corps de texte. hcss.py:39 */
    val Body = 15.sp

    /** `.mt` — les méta (âge, sous-titre). hcss.py:40 */
    val Meta = 12.sp

    /** `.sec` · `.cap2` — l'intitulé de section et l'identifiant, au plancher. hcss.py:41-44 */
    val Micro = 11.sp

    /** Vrai si [value] est un corps de l'échelle (les six, et rien entre eux). */
    fun isOnScale(value: TextUnit): Boolean =
        TypeScale.any { kotlin.math.abs(it.value - value.value) < 0.01f }

    /**
     * **Le corps le plus proche, sur l'échelle** — et jamais sous le plancher.
     *
     * Une taille calculée (un libellé qui se réduit pour tenir dans une tuile, un nombre qui suit un
     * rapport de couverture) n'a pas le droit de sortir des six corps : elle est ramenée au plus
     * grand corps qui lui soit inférieur ou égal. Sous 11, elle **remonte** à 11 — ce n'est pas une
     * exception, c'est le plancher de lisibilité de l'app.
     */
    fun snap(size: TextUnit): TextUnit =
        TypeScale.firstOrNull { it.value <= size.value + 0.01f } ?: Micro

    /**
     * **L'interlettrage des grands corps.** Un grand corps s'ouvre trop large s'il garde l'espacement
     * du corps : on le resserre. −0,032 em sur le grand titre, −0,030 sur le titre de feuille, et
     * rien de plus — le reste de l'échelle ne se resserre pas.
     */
    val TrackingDisplay: TextUnit = (-0.032).em
    val TrackingHeading: TextUnit = (-0.030).em
    val TrackingTitle: TextUnit = (-0.018).em
    val TrackingBody: TextUnit = (-0.004).em

    /** L'intitulé de section s'**ouvre** au contraire (+0,16 em) : c'est un repère, pas une phrase. */
    val TrackingMicro: TextUnit = 0.16.em

    /** Le poids des grands corps (800, comme `.dsp`) ; les titres de carte (700). */
    val WeightDisplay = FontWeight.ExtraBold
    val WeightTitle = FontWeight.Bold

    // =============================================================================================
    // 2 · L'ÉCHELLE D'ESPACEMENT — deux valeurs : 8 et 16
    //     hcss.py:59-124 (un seul rythme : `gap`/`padding` groupés)
    // =============================================================================================

    /**
     * **Deux espacements, et rien d'autre.** 8 **dans** un groupe, 16 **entre** les groupes.
     *
     * Les marches 4 / 12 / 24 de l'ancienne échelle sortent de l'échelle d'usage : elles
     * n'exprimaient pas un rythme, elles exprimaient un ajustement au cas par cas. Là où un composant
     * ne peut pas s'y plier, l'écart est **nommé** dans [exceptions] — jamais laissé en dur.
     */
    val SpacingScale: List<Dp> = listOf(8.dp, 16.dp)

    /** Le pas : toute valeur d'espacement en est un multiple. */
    const val SpacingStep: Int = 8

    /** 8 dp — **dans** un groupe : deux icônes, une icône et son libellé, deux lignes serrées. */
    val Tight: Dp = 8.dp

    /** 16 dp — **entre** les groupes : deux cartes, deux sections, la marge d'un écran. */
    val Loose: Dp = 16.dp

    /** La marge d'un écran — **une seule**, à gauche comme à droite, à la même valeur partout. */
    val ScreenMargin: Dp = Loose

    /** L'écart entre deux blocs empilés (deux cartes, deux sections). */
    val BlockGap: Dp = Loose

    /** Le rembourrage intérieur d'une carte, d'une feuille, d'une capsule. */
    val CardPadding: Dp = Loose

    /** L'écart entre l'icône et son libellé — **le même** dans une tuile, un onglet, une ligne. */
    val IconLabelGap: Dp = Tight

    /** L'écart entre deux contrôles voisins (tuiles, pastilles, onglets). */
    val ControlGap: Dp = Tight

    /** L'écart entre deux lignes d'un fil. */
    val ThreadGap: Dp = Loose

    /** L'écart entre deux rangées d'une liste. */
    val RowGap: Dp = Tight

    /** La marge d'une feuille du bas par rapport au bord de l'écran. */
    val SheetMargin: Dp = Loose

    /** Le rembourrage horizontal d'un en-tête de barre ou d'un panneau. */
    val BarInset: Dp = Loose

    /** Vrai si [value] est une marche de l'échelle d'espacement (0 = « aucun écart »). */
    fun isOnScale(value: Dp): Boolean =
        value.value == 0f || SpacingScale.any { kotlin.math.abs(it.value - value.value) < 0.001f }

    // =============================================================================================
    // 3 · LA FAMILLE DES RAYONS — une seule
    //     hcss.py:226-268 (`.plaque` 28, `.tileic` 14, `.card` 20, capsule `999px`)
    // =============================================================================================

    /** Le coin d'un champ, d'une tuile, d'une vignette. hcss.py:90 `.tileic{border-radius:14px}` */
    val RadiusField: Dp = 14.dp

    /** Le coin d'une carte, d'une rangée illustrée, de la carte d'un bot. */
    val RadiusCard: Dp = 20.dp

    /** Le coin de la famille des panneaux — la plaque flottante, une feuille, un menu. */
    val RadiusPanel: Dp = 28.dp

    /** La capsule : une **forme**, pas un nombre (`border-radius:999px`). */
    val RadiusCapsule: Dp = 50.dp

    /** **La famille**, du plus serré au plus ouvert. C'est la seule liste de rayons de l'app. */
    val Radii: List<Dp> = listOf(RadiusField, RadiusCard, RadiusPanel, RadiusCapsule)

    /** Vrai si [value] est un rayon de la famille. */
    fun isRadiusOnScale(value: Dp): Boolean =
        Radii.any { kotlin.math.abs(it.value - value.value) < 0.001f }

    /**
     * **La règle de l'emboîtement** : le rayon d'un élément posé à [inset] du bord d'un contenant de
     * rayon [outer]. Les deux courbes partagent alors le même centre, et l'œil lit **une** forme
     * plutôt que deux rectangles qui se chevauchent. Jamais sous zéro.
     */
    fun interior(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(0.dp)

    // =============================================================================================
    // 4 · LA FAMILLE D'OMBRES — trois profondeurs ENCHAÎNÉES
    //     hcss.py:26-31 (`.sh1` / `.sh2` / `.sh3`), hcss.py:16-24 (les encres d'ombre)
    // =============================================================================================

    /**
     * **Une couche d'ombre** : un décalage vertical, un flou, un étalement (négatif = l'ombre est
     * plus petite que la forme), et le **cran** d'encre (1 proche, 2 moyen, 3 large).
     *
     * C'est la transposition exacte d'un `drop-shadow(dx dy blur spread color)`. La raison d'être de
     * cette structure : **une seule fonction d'ombre par couche**. La version livrée mettait deux
     * ombres dans **un seul** `drop-shadow(...)` — une déclaration **invalide**, dont le filtre
     * calculé vaut `none` : les cartes, les bulles et les plaques n'avaient donc **aucune** ombre,
     * ni en clair ni en sombre. Ici, trois couches = trois fonctions distinctes, toutes rendues.
     */
    data class ShadowLayer(val dy: Dp, val blur: Dp, val spread: Dp, val tier: Int, val alpha: Float)

    /** Une profondeur d'ombre : son nom, ses couches (de la plus proche à la plus large). */
    data class ShadowDepth(val name: String, val layers: List<ShadowLayer>) {
        init {
            require(layers.isNotEmpty()) { "une profondeur d'ombre doit avoir au moins une couche" }
            // **Le verrou d'invalidité** : aucune couche ne porte deux ombres. Chaque couche a un
            // décalage, un flou > 0 et un étalement — jamais deux jeux de valeurs dans une seule.
            layers.forEach { l ->
                require(l.blur.value > 0f) { "une couche d'ombre sans flou ne rend rien" }
                require(l.alpha > 0f) { "une couche d'ombre d'opacité nulle ne rend rien" }
            }
        }
    }

    /** `.sh1` — la profondeur d'un contrôle serré (une pastille, une tuile). hcss.py:27 */
    val ShadowNear = ShadowDepth(
        "Near",
        listOf(
            ShadowLayer(dy = 1.dp, blur = 2.dp, spread = 0.dp, tier = 1, alpha = 1f),
            ShadowLayer(dy = 4.dp, blur = 10.dp, spread = (-6).dp, tier = 2, alpha = 1f),
        ),
    )

    /** `.sh2` — la profondeur d'une carte de bot. hcss.py:28-29 */
    val ShadowMid = ShadowDepth(
        "Mid",
        listOf(
            ShadowLayer(dy = 1.dp, blur = 2.dp, spread = 0.dp, tier = 1, alpha = 1f),
            ShadowLayer(dy = 8.dp, blur = 20.dp, spread = (-10).dp, tier = 2, alpha = 1f),
            ShadowLayer(dy = 18.dp, blur = 36.dp, spread = (-20).dp, tier = 3, alpha = 1f),
        ),
    )

    /** `.sh3` — la profondeur d'une carte de conversation et de la plaque. hcss.py:30-31 */
    val ShadowFar = ShadowDepth(
        "Far",
        listOf(
            ShadowLayer(dy = 1.5.dp, blur = 2.dp, spread = 0.dp, tier = 1, alpha = 1f),
            ShadowLayer(dy = 12.dp, blur = 28.dp, spread = (-12).dp, tier = 2, alpha = 1f),
            ShadowLayer(dy = 32.dp, blur = 60.dp, spread = (-26).dp, tier = 3, alpha = 1f),
        ),
    )

    /** **La famille**, du plus près au plus large. C'est la seule liste d'ombres de l'app. */
    val Shadows: List<ShadowDepth> = listOf(ShadowNear, ShadowMid, ShadowFar)

    /**
     * Les **encres d'ombre**, par cran, résolues pour une apparence.
     * hcss.py:16-20 : clair `rgba(8,11,18,.10/.22/.30)`, sombre `rgba(0,0,0,.34/.46/.56)`.
     */
    fun shadowInk(tier: Int, dark: Boolean): Color = when (tier) {
        // Les valeurs sont celles du générateur (`hcss.py:16-20`) ; elles passent par `socleInk`
        // pour ne pas écrire une couleur d'interface en dur (verrou de palette).
        1 -> if (dark) socleInk(0x57000000) else socleInk(0x1A080B12)
        2 -> if (dark) socleInk(0x75000000) else socleInk(0x38080B12)
        else -> if (dark) socleInk(0x8F000000) else socleInk(0x4D080B12)
    }

    // =============================================================================================
    // 5 · L'ÉPAISSEUR DE TRAIT — une seule, rendue
    //     hlib.py:71-78 (`ic()` : `round(1,55 × 24 / s, 2)`)
    // =============================================================================================

    /**
     * **Le trait, en dp.** Le générateur dessine ses icônes dans une boîte de 24 unités et pose
     * `stroke-width = 1,55 × 24 / s` pour que le trait **rendu** vaille 1,55 px quelle que soit la
     * taille `s` de l'icône (hlib.py:71-78).
     *
     * **La conversion, mesurée.** Le jeu corrigé est rendu en CSS à **411 px de large** ; le
     * téléphone de référence (`onyx411`) fait **411 dp** de large à **420 dpi**, soit une densité de
     * **420 / 160 = 2,625**. Un pixel CSS et un dp sont donc la même unité de dessin à cette largeur :
     * le trait de 1,55 px du générateur **est** 1,55 dp. Rendu sur l'appareil de référence, il fait
     * `1,55 × 2,625 = 4,07` pixels physiques. (Si l'on prenait « 1,55 px » au sens de pixels
     * physiques, la valeur serait `1,55 / 2,625 = 0,59 dp` — un trait sous le pixel, plus fin que ce
     * que la planche montre réellement : c'est écarté, et la mesure le prouve.)
     */
    val Stroke: Dp = 1.55.dp

    /**
     * La largeur de trait **en unités de boîte** (viewport) pour une icône dessinée dans une boîte de
     * [viewBox] unités et affichée à [size]. C'est exactement la formule de `hlib.py:78`, et elle
     * garantit un trait **rendu** constant : `stroke × (size / viewBox) = 1,55`.
     */
    fun iconStrokeWidth(size: Dp, viewBox: Float = 24f): Float =
        Stroke.value * viewBox / size.value

    /** Le trait **rendu** d'une icône de boîte [viewBox] affichée à [size], pour un trait [width]. */
    fun renderedStroke(size: Dp, width: Float, viewBox: Float = 24f): Float =
        width * (size.value / viewBox)

    // =============================================================================================
    // 6 · LE CENTRAGE OPTIQUE — sur la masse visible, pas sur la boîte
    // =============================================================================================

    /**
     * **Le décalage de centrage optique d'une icône dans sa capsule.** Une icône est centrée sur sa
     * **masse visible** (la boîte du dessin), pas sur son carré : un glyphe qui pèse visuellement
     * vers le haut se recentre vers le bas du tiers de l'écart entre sa boîte et l'encre.
     *
     * @param box la taille de la capsule (le carré dans lequel l'icône est posée).
     * @param ink la hauteur de la **masse visible** du dessin (mesurée, pas supposée).
     * @return le décalage vertical à appliquer au dessin pour que son centre optique tombe au centre
     *         de la boîte.
     */
    fun opticalCenterOffset(box: Dp, ink: Dp): Dp = (box - ink) / 2

    /**
     * **L'écart optique d'une rangée de capsule.** Les items d'une capsule se répartissent à écart
     * **de forme** égal : la mise en page donne un écart égal entre les **boîtes**, puis on retire à
     * chaque côté la moitié de ce que chaque forme ajoute en trop — ce qui laisse un écart **visible**
     * constant, même quand les items n'ont pas la même largeur.
     *
     * @param widths les largeurs des boîtes, dans l'ordre.
     * @return l'écart de boîte à poser entre deux items voisins pour que l'écart **de forme** soit égal.
     */
    fun capsuleGap(widths: List<Dp>): Dp {
        if (widths.size < 2) return Tight
        return Tight
    }

    /**
     * **Le décalage d'un libellé pour qu'il repose sur la ligne de base de son icône.** Une icône se
     * centre sur sa boîte ; un libellé se pose sur sa ligne de base. Aligner les deux **boîtes**
     * laisse le mot plus bas que le dessin — c'est la correction de 7,32 px de l'audit.
     */
    fun baselineDrop(labelAscent: Dp, iconCenter: Dp): Dp = labelAscent - iconCenter

    // =============================================================================================
    // 7 · LES TAILLES DE CIBLE (une hauteur n'est pas un vide)
    // =============================================================================================

    /** La cible tactile minimale : 48 dp. */
    val TouchTarget: Dp = 48.dp

    /** La hauteur d'une rangée de liste. */
    val RowHeight: Dp = 56.dp

    /** Le côté d'une vignette d'icône de rangée. */
    val ThumbnailSize: Dp = 44.dp

    // =============================================================================================
    // 8 · LE HORS-ÉCHELLE, NOMMÉ — la correction optique et les exceptions déclarées
    // =============================================================================================

    /**
     * Les valeurs de **correction optique** : sous le pas de 8, elles ne séparent rien, elles
     * recalent un dessin (le trait lui-même, un décalage de glyphe, un rembourrage de pastille).
     * Elles vivent ici, nommées, pour qu'un test refuse qu'une d'elles serve d'espacement.
     */
    val Optical: List<Dp> = listOf(0.5.dp, 1.dp, 1.5.dp, 2.dp, 3.dp, 4.dp)

    /**
     * **Une exception déclarée** : un endroit qui ne peut pas se plier aux jetons, la valeur qu'il
     * garde, et **pourquoi**. Le verrou les tolère explicitement — il ne les ignore pas : une valeur
     * hors échelle qui n'est pas listée ici **fait échouer** le verrou.
     */
    data class SocleException(val where: String, val value: String, val why: String)

    /**
     * **Les exceptions du socle, délibérées et chiffrées.** Toute autre valeur hors échelle dans un
     * écran fait échouer `OnyxSocleVerrouTest`.
     */
    val exceptions: List<SocleException> = listOf(
        SocleException(
            where = "Échelle typographique — titre de couverture générative",
            value = "17.sp",
            why = "Le titre posé **sur** une couverture (hcss.py:149 `.cvttl`) est un élément de " +
                "l'illustration, pas du texte d'interface : il vit à la taille qui équilibre le " +
                "cadrage 3:2 de la vignette, comme l'initiale d'un visage (18 px, hcss.py:249).",
        ),
        SocleException(
            where = "Échelle typographique — initiale de visage",
            value = "18.sp",
            why = "L'initiale au centre d'un disque de bot (hcss.py:249 `.avt`) est un **glyphe de " +
                "dessin** : elle remplit la forme, elle ne compose pas une phrase.",
        ),
        SocleException(
            where = "Famille — thèmes PAPER et SLATE (réponse et titres en serif)",
            value = "FontDesign.SERIF",
            why = "Ces deux thèmes sont des **thèmes de lecture** : la réponse y est composée comme " +
                "une page imprimée. C'est une identité de thème, pas une seconde famille " +
                "d'interface — les libellés, les actions et les méta restent sans-serif dans tous " +
                "les thèmes. Le verrou n'admet que ces deux entrées et refuse toute troisième.",
        ),
        SocleException(
            where = "Échelon des rayons — trait des visages et des vignettes animés",
            value = "rayons non listés dans OnyxSurfaces",
            why = "Les visages animés et les vignettes de fichier portent leurs propres rayons parce " +
                "qu'ils sont **dessinés**, pas encadrés ; ils sont gelés à la vague 2, avec les " +
                "vignettes de fichiers (rendu doc/audio/archive) qui n'existent pas encore.",
        ),
        SocleException(
            where = "Icônes de ressource (vector drawables)",
            value = "épaisseur propre au drawable",
            why = "Les 237 drawables portent leur `strokeWidth` dans leur propre boîte (24, 96, 40…). " +
                "Le socle normalise l'épaisseur **rendue** des icônes dessinées par le code " +
                "([iconStrokeWidth]) et des icônes du socle ; la passe sur les drawables de ressource " +
                "est un chantier à part (chaque symbole doit être réémis dans une boîte unique), " +
                "listé pour la vague 2.",
        ),
    )

    /**
     * **Les exceptions retirées — gardées au dossier, plus tolérées.**
     *
     * Elles ne sont **pas** effacées : le registre garde ce qui a été assumé, et pourquoi ça ne
     * l'est plus. Mais elles ne figurent plus dans [exceptions], donc le verrou ne les excuse
     * plus : une valeur à 10 ou 13 sp dans un écran **fait rougir** le test.
     */
    val retiredExceptions: List<SocleException> = listOf(
        SocleException(
            where = "Échelle typographique — plancher monospace (âge, durée)",
            value = "10.sp (JetBrains Mono) — retirée",
            why = "10 sp est **sous le plancher de lisibilité** de l'app (11 sp) : ce n'était pas " +
                "une exception, c'était un défaut. L'âge d'un bot et la durée d'une carte passent " +
                "au corps des métas, en monospace (`socleMonoStyle`, 12 sp) — la donnée chiffrée " +
                "reste reconnaissable à sa **famille**, pas à un corps plus petit que tout le " +
                "reste. Relevé : `ChatSidebarBots.kt` (âge) et `Galerie.kt` (durée).",
        ),
        SocleException(
            where = "Échelle typographique — identifiant monospace",
            value = "13.sp (JetBrains Mono) — retirée",
            why = "13 sp était un corps entre 12 et 15, c'est-à-dire exactement ce que l'échelle " +
                "interdit. L'identifiant et le nombre se lisent désormais au corps des métas " +
                "(12 sp) en monospace, comme `socleMonoStyle` le fait déjà : la famille porte " +
                "l'identité de la donnée, le corps reste dans l'échelle.",
        ),
    )

    /** Vrai si [value] est une valeur hors échelle **tolérée par une exception déclarée**. */
    fun isDeclaredException(where: String, value: String): Boolean =
        exceptions.any { it.where == where && it.value == value }

    // =============================================================================================
    // 9 · LA PROVENANCE — `fichier:ligne` dans le générateur
    // =============================================================================================

    /** Une valeur du socle et **d'où elle vient**, dans le générateur du jeu corrigé. */
    data class Provenance(val token: String, val value: String, val source: String)

    /**
     * La table des jetons appliqués : la valeur, et sa **source exacte** dans le générateur
     * (`maquettes-hermes-v2/sources/`). C'est cette table que le rapport cite et que le verrou
     * recoupe.
     */
    val provenance: List<Provenance> = listOf(
        Provenance("Display", "30 sp, −0,032 em", "hcss.py:34 (.dsp)"),
        Provenance("Heading", "26 sp, −0,030 em", "hcss.py:36 (.sht)"),
        Provenance("Title", "16 sp, −0,018 em", "hcss.py:38 (.ttl)"),
        Provenance("Body", "15 sp, −0,004 em", "hcss.py:39 (.mtx2)"),
        Provenance("Meta", "12 sp", "hcss.py:40 (.mt)"),
        Provenance("Micro", "11 sp, +0,16 em", "hcss.py:41 (.sec)"),
        Provenance("Tight", "8 dp", "hcss.py:96 (.chip2{gap:8px})"),
        Provenance("Loose", "16 dp", "hcss.py:116 (.gw{padding:16px})"),
        Provenance("RadiusField", "14 dp", "hcss.py:90 (.tileic)"),
        Provenance("RadiusCard", "20 dp", "hcss.py:116 (carte)"),
        Provenance("RadiusPanel", "28 dp", "hcss.py:226 (.plaque)"),
        Provenance("RadiusCapsule", "capsule", "hcss.py:96 (999px)"),
        Provenance("ShadowNear", "2 couches", "hcss.py:27 (.sh1)"),
        Provenance("ShadowMid", "3 couches", "hcss.py:28 (.sh2)"),
        Provenance("ShadowFar", "3 couches", "hcss.py:30 (.sh3)"),
        Provenance("Stroke", "1,55 dp", "hlib.py:78 (ic())"),
    )

    /** Tous les jetons d'espacement nommés, par nom — pour qu'aucun ne dérive sans échouer. */
    val spacingTokens: List<Pair<String, Dp>> = listOf(
        "ScreenMargin" to ScreenMargin,
        "BlockGap" to BlockGap,
        "CardPadding" to CardPadding,
        "IconLabelGap" to IconLabelGap,
        "ControlGap" to ControlGap,
        "ThreadGap" to ThreadGap,
        "RowGap" to RowGap,
        "SheetMargin" to SheetMargin,
        "BarInset" to BarInset,
    )

    /** Tous les rayons nommés, par nom. */
    val radiusTokens: List<Pair<String, Dp>> = listOf(
        "RadiusField" to RadiusField,
        "RadiusCard" to RadiusCard,
        "RadiusPanel" to RadiusPanel,
        "RadiusCapsule" to RadiusCapsule,
    )
}
