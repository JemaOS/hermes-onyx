package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des blocs de conversation — une plaque de verre, avec le visage de son bot.**
 *
 * La direction du propriétaire (capture à l'appui, les cartes encadrées en rouge) remplace celle des
 * couvertures générées : « pour les blocs de conversation tu vas pas mettre d'images, ça va être
 * très simple : une plaque de verre avec liquid glass, le visage du bot toujours en rapport avec le
 * nom et synchro si je change son visage, le visage doit être animé ».
 *
 * Les quatre dents, et ce que chacune mord :
 *
 *  1. **plus aucune image générée sur une carte** — la carte ne peint ni le fluide, ni la couverture
 *     déterministe, ni le voile de pied qui allait avec ; le matériau de la planche reste déclaré
 *     (nommé, documenté), mais **plus peint par les cartes** ;
 *  2. **le visage suit l'identité VIVANTE du bot** — la même clé que partout (`botFace`) : changer le
 *     visage d'un bot change le visage de sa carte. La dent mord aussi sur une **copie figée** : sa
 *     valeur est nommée, donc un lot qui figerait le visage à la création serait refusé ;
 *  3. **l'animation est déclarée ET respecte le figement** — les pouls de repos sont dans
 *     l'inventaire de mouvement ([MotionTable]), le visage bouge vraiment (deux instants diffèrent),
 *     et « Réduire les animations » le fige (aucune horloge montée) ;
 *  4. **l'encre du titre et de l'heure tient 4,5:1**, dans les deux apparences, sur **la pire
 *     plaque** (blanc pur et noir pur derrière la vitre, au voile le plus dense des deux chemins de
 *     peinture).
 *
 * Chaque dent qui mesure imprime aussi le **témoin négatif** de sa mesure (l'ancienne encre blanche,
 * la copie figée, la pose de repos) : un seuil qui ne mord nulle part ne prouve rien.
 */
class BlocsVerreVerrouTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /** Le fichier des blocs du langage « Galerie » : c'est lui qui porte la carte. */
    private val carte = "core/src/main/java/com/onyx/app/ui/design/Galerie.kt"

    private fun source(rel: String): String = File(root, rel).readText()

    private fun theme(dark: Boolean): ResolvedTheme =
        ResolvedTheme.resolve(OnyxTheme.STANDARD, dark = dark, fonts = OnyxFonts.System)

    private fun name(color: Color): String = "#%02X%02X%02X".format(
        (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(),
    )

    // =============================================================================================
    // 1 · Plus aucune image générée sur une carte — et le matériau de la planche reste déclaré
    // =============================================================================================

    @Test
    fun `la carte ne peint AUCUNE image generee, et le materiau de la planche reste declare`() {
        val src = source(carte)
        // Ce qu'une carte ne doit PLUS appeler : les deux couvertures génératives, le composable du
        // fluide, et le voile de pied qui n'existait que pour la lisibilité sur une image.
        val interdits = listOf(
            "FluideConversationCover(",
            "ConversationCover(",
            "ConversationCoverCard(",
            "RuntimeShader",
            "Brush.verticalGradient",
        )
        val fautes = interdits.filter { src.contains(it) }
        println("carte sans image : ${interdits.size} motifs surveillés, fautes=$fautes")
        assertTrue(
            "la carte de conversation peint encore une image générée : $fautes — la direction du " +
                "propriétaire est « pas d'images, une plaque de verre ». Fichier : $carte",
            fautes.isEmpty(),
        )
        // **Le matériau n'est pas supprimé en silence** : il reste nommé et déclaré comme matériau de
        // la planche, inutilisé par les cartes.
        val fluide = source("core/src/main/java/com/onyx/app/ui/design/FluideCover.kt")
        assertTrue(
            "le composable du fluide a disparu : il doit rester déclaré (matériau de la planche, " +
                "inutilisé par les cartes) — FluideCover.kt",
            fluide.contains("fun FluideConversationCover("),
        )
        assertTrue(
            "FluideCover.kt ne dit pas que le fluide n'est plus peint par les cartes : le prochain " +
                "lot le remettra sur une carte sans savoir que c'est interdit",
            fluide.contains("plus peinte sur les cartes") ||
                fluide.contains("n'est plus peinte par une carte") ||
                fluide.contains("AUCUNE carte"),
        )
        assertTrue(
            "la couverture déterministe a disparu : elle doit rester déclarée (matériau de la planche)",
            File(root, "core/src/main/java/com/onyx/app/ui/design/ConversationCover.kt").isFile,
        )
        assertTrue(
            "le choix de couverture a disparu : les trois compositions restent déclarées",
            source("core/src/main/java/com/onyx/app/ui/design/FluideChoix.kt").contains("VARIANTE_TRAME"),
        )
        assertTrue(
            "la carte doit être une PLAQUE DE VERRE (l'interrupteur du fluide ne la concerne plus)",
            source(carte).contains("liquidGlass(GlassSurface.CONVERSATION_CARD"),
        )
    }

    // =============================================================================================
    // 2 · Le visage suit l'identité VIVANTE du bot — la dent mord sur une copie figée
    // =============================================================================================

    @Test
    fun `le visage de la carte suit l identite vivante du bot, et une copie figee serait vue`() {
        val profil = "scout"
        val autre = "quill"
        // Des codes de visage **valides** (la grammaire de `BotFace.parse` : forme-yeux-bouche-
        // marque-couleur) : un code invalide retombe sur le visage du nom et ne mesurerait rien.
        val visageA = "bubble-happy-smile-antenna-1"
        val visageB = "egg-wide-grin-blush-4"
        // **Une seule source** : le visage de la carte est EXACTEMENT `botFace`, la clé de la maison.
        assertEquals(
            "le visage de la carte n'est pas résolu par `botFace` — deux visages pour un bot",
            botFace(profil, visageA),
            BlocConversation.visage(profil, visageA),
        )
        // Le visage enregistré l'emporte : changer le visage change le visage de la carte.
        val avant = BlocConversation.visage(profil, visageA)
        val apres = BlocConversation.visage(profil, visageB)
        assertNotEquals(
            "changer le visage d'un bot ne change pas le visage de sa carte : le visage est figé",
            avant, apres,
        )
        // Sans visage enregistré, c'est le NOM du profil qui décide — donc la carte est bien « en
        // rapport avec le nom ».
        assertNotEquals(
            "deux profils sans visage enregistré montrent le même visage : la carte ne dépend pas du nom",
            BlocConversation.visage(profil, null),
            BlocConversation.visage(autre, null),
        )
        // **LE TÉMOIN NÉGATIF, NOMMÉ** : la copie figée à la création ne montre pas ce que montre
        // l'identité vivante — c'est la valeur que la dent ci-dessus refuse.
        val copieFigee = BlocConversation.visage(profil, visageA)
        val vivant = BlocConversation.visage(profil, visageB)
        assertNotEquals(
            "une copie figée au moment de la création montre ce que montre le visage vivant : la " +
                "dent de synchro ne mesure rien",
            copieFigee, vivant,
        )
        println(
            "synchro du visage : $profil + $visageA -> ${copieFigee.colour} ${copieFigee.shape}-" +
                "${copieFigee.eyes}-${copieFigee.mouth} · $profil + $visageB -> ${vivant.colour} " +
                "${vivant.shape}-${vivant.eyes}-${vivant.mouth} · $profil sans visage -> " +
                "${BlocConversation.visage(profil, null).colour} · $autre sans visage -> " +
                "${BlocConversation.visage(autre, null).colour}",
        )
        // Et le site d'appel passe bien l'identité VIVANTE de la ligne : le nom de profil et le
        // visage que la liste recalcule à chaque composition.
        val sidebar = source("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
        for (motif in listOf("identity = row.identity", "faceCode = row.identityFace", "BlocConversation(")) {
            assertTrue(
                "le volet ne passe pas « $motif » à la carte : le visage ne peut pas suivre le bot",
                sidebar.contains(motif),
            )
        }
    }

    // =============================================================================================
    // 3 · L'animation est déclarée, elle bouge, et le figement la coupe
    // =============================================================================================

    @Test
    fun `l animation du visage est declaree dans l inventaire de mouvement`() {
        val declaree = MotionTable.entries.filter { entry ->
            entry.androidFile == carte && entry.android.contains("BotIdle.seedFor")
        }
        assertTrue(
            "aucune ligne de l'inventaire de mouvement ne déclare l'animation du visage de la carte : " +
                "une animation non déclarée est une animation qu'on ne peut pas couper",
            declaree.size == 1,
        )
        val ligne = declaree.single()
        assertEquals(
            "l'animation du visage doit être déclarée comme COUPÉE par « Réduire les animations »",
            MotionTable.MotionStatus.REDUIT,
            ligne.status,
        )
        assertTrue(
            "la ligne doit s'ancrer sur l'horloge du visage de la référence (1/30, `paused: reduceMotion`)",
            ligne.sourceSpec.contains("TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion))"),
        )
        // Les pouls déclarés sont ceux de la maison, et il y en a.
        assertEquals(
            "les animations du visage doivent être les pouls de repos de la maison (BotIdleCue)",
            BotIdleCue.entries.toList(),
            BlocConversation.Animations,
        )
        assertTrue("aucun pouls déclaré", BlocConversation.Animations.isNotEmpty())
    }

    @Test
    fun `le visage bouge vraiment, et deux instants differents donnent deux dessins`() {
        val seed = BotIdle.seedFor("scout", "bubble-happy-smile-antenna-1")
        val tilt = botFace("scout", "bubble-happy-smile-antenna-1").tilt.toFloat()
        var pire = 0f
        var mesures = 0
        // **Un pouls DE CHAQUE TYPE, construit pour la mesure** (la graine de la carte, l'animation
        // visée). Deux instants du pouls doivent donner deux dessins : on compare donc **tous** les
        // couples d'instants (0, un quart, la moitié, trois quarts, la fin) et l'on exige que le plus
        // grand écart soit franc. Un seul couple fixe ne suffirait pas : un clignement est
        // **symétrique** (le quart et les trois quarts donnent la même fermeture), et la respiration
        // est **nulle** à mi-course — comparer deux instants choisis d'avance peut ne rien mesurer.
        BotIdleCue.entries.forEachIndexed { index, cue ->
            val pulse = BotIdlePulse(
                index = index,
                cue = cue,
                gapMillis = BotIdle.gapMillis(seed, index),
                durationMillis = cue.durationMillis,
            )
            fun signature(progress: Float): List<Float> {
                val frame = botIdleFrame(pulse, progress, tilt)
                return listOf(
                    frame.pose.blink,
                    frame.pose.breath,
                    frame.pose.look?.x ?: 0f,
                    frame.pose.look?.y ?: 0f,
                    frame.pose.tilt ?: tilt,
                    frame.pose.brow,
                    frame.pose.squint,
                    if (frame.mood == BotMood.IDLE) 0f else 1f,
                )
            }
            val instants = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { it to signature(it) }
            var ecart = 0f
            for (a in instants.indices) {
                for (b in a + 1 until instants.size) {
                    val d = instants[a].second.zip(instants[b].second) { x, y -> kotlin.math.abs(x - y) }.sum()
                    ecart = maxOf(ecart, d)
                }
            }
            // L'écart au REPOS compte aussi : c'est lui qui dit que le pouls se voit.
            val repos = signature(1f)
            val quart = signature(0.25f)
            val visible = quart.zip(repos) { x, y -> kotlin.math.abs(x - y) }.sum()
            println(
                "  pouls ${cue.frLabel} (durée ${cue.durationMillis} ms) : plus grand écart entre deux " +
                    "instants %.3f · écart d'un quart au repos %.3f".format(ecart, visible),
            )
            assertTrue(
                "le pouls ${cue.frLabel} ne change rien au dessin : deux instants identiques",
                ecart > 0.1f,
            )
            assertTrue(
                "le pouls ${cue.frLabel} ne se voit pas depuis le repos",
                maxOf(visible, quart.zip(signature(0f)) { x, y -> kotlin.math.abs(x - y) }.sum()) > 0.1f,
            )
            pire = maxOf(pire, ecart)
            mesures++
        }
        assertEquals("les cinq pouls de la maison doivent être mesurés", BotIdleCue.entries.size, mesures)
        assertEquals("la table des animations de la carte doit être celle de la maison",
            BotIdleCue.entries.size, BlocConversation.Animations.size)
        assertTrue("aucun pouls ne bouge (pire écart $pire)", pire > 0.1f)
    }

    @Test
    fun `sous reduire les animations le visage est fige et aucune horloge n est montee`() {
        val src = source(carte)
        // La règle de figement, dans le composable du visage : rien n'est monté, la boucle sort.
        assertTrue(
            "le visage de la carte ne lit pas « Réduire les animations » : il bougera pour qui " +
                "a demandé que rien ne bouge — $carte",
            src.contains("if (reduceMotion) return@LaunchedEffect"),
        )
        assertTrue(
            "le visage doit lire le réglage système (LocalReduceMotion), pas un réglage à lui",
            src.contains("val reduceMotion = LocalReduceMotion.current"),
        )
        // Un seul clignement dans la carte : c'est le pouls qui donne l'instant du visage, jamais le
        // clignement de BotAvatar (sinon deux animations se superposeraient).
        assertTrue(
            "le visage de la carte doit recevoir son instant d'une seule source (blinks = false)",
            src.contains("blinks = false"),
        )
        // Le figement est un fait pur : les échelles système à zéro le déclarent.
        assertTrue(ReduceMotion.isReduced(0f, 1f, 1f))
        assertTrue(ReduceMotion.isReduced(1f, 1f, 0f))
        // Et la boîte du visage est CONSTANTE : l'animation ne pousse pas le titre.
        assertEquals(
            "la taille du visage est une constante de la maison (rien ne grandit à l'écran)",
            OnyxSocle.ThumbnailSize,
            BlocConversation.FaceSize,
        )
    }

    // =============================================================================================
    // 4 · L'encre du titre et de l'heure tient 4,5:1, dans les deux apparences, sur la pire plaque
    // =============================================================================================

    /** Les encreurs d'une carte, dans l'ordre de la hiérarchie : titre, seconde ligne, méta. */
    private fun encreurs(theme: ResolvedTheme): List<Triple<String, Color, Color>> = listOf(
        Triple("titre", BlocConversation.titleInk(theme), socleTitleInk(theme)),
        Triple("seconde ligne", BlocConversation.secondInk(theme), theme.secondaryText),
        Triple("heure / méta", BlocConversation.thirdInk(theme), theme.tertiaryText),
    )

    /** Le pire rapport d'un encreur : **tous** les fonds que la carte peut peindre. */
    private fun pireRapport(theme: ResolvedTheme, ink: Color): Float =
        BlocConversation.grounds(theme).minOf { under ->
            com.onyx.app.ui.design.OnyxContrast.ratio(ink, under)
        }

    @Test
    fun `l encre du titre et de l heure tient 4,5 sur la pire plaque, dans les deux apparences`() {
        println("== BLOCS DE CONVERSATION — encre sur la plaque (seuil 4,50:1) ==")
        val rapport = StringBuilder()
        var fautes = 0
        var pire = Float.MAX_VALUE
        OnyxTheme.entries.forEach { nom ->
            listOf(false, true).forEach { dark ->
                val theme = ResolvedTheme(theme = nom, palette = nom.palette, dark = dark, fonts = OnyxFonts.System)
                val voiles = BlocConversation.veils(theme)
                val fonds = BlocConversation.grounds(theme)
                rapport.append(
                    "  %-10s %-7s voiles %s · fonds %s%n".format(
                        nom.name.take(10), if (dark) "sombre" else "clair",
                        voiles.joinToString("/") { "%.3f".format(it.alpha) },
                        fonds.joinToString("/") { name(it) },
                    ),
                )
                encreurs(theme).forEach { (role, ink, prefere) ->
                    val mesures = fonds.map { com.onyx.app.ui.design.OnyxContrast.ratio(ink, it) }
                    val r = mesures.min()
                    pire = minOf(pire, r)
                    rapport.append(
                        "  %-10s %-7s %-13s encre %s = %s → pire %.2f:1%s%n".format(
                            nom.name.take(10), if (dark) "sombre" else "clair", role, name(ink),
                            mesures.joinToString("/") { "%.2f".format(it) }, r,
                            if (r < OnyxContrast.TEXT_MIN) "  ÉCHEC" else "",
                        ),
                    )
                    if (r < OnyxContrast.TEXT_MIN) fautes++
                    // L'encre n'est pas une couleur écrite à la main : c'est la règle de la maison
                    // (`readableInk`) appliquée jusqu'au point fixe sur **tous** les fonds.
                    assertEquals(
                        "$role/${nom.name}/${if (dark) "sombre" else "clair"} : l'encre doit être la " +
                            "règle de la maison (readableInk sur les fonds de la plaque), pas une " +
                            "couleur choisie sur place",
                        BlocConversation.ink(theme, prefere),
                        ink,
                    )
                }
                // Le **verre peint** de la plaque reste une vitre (le repli, lui, peut être opaque :
                // c'est le repli de la maison, et les trois plaques existantes le partagent).
                val verre = glassPaintTintAlpha(BlocConversation.Surface, dark, glassInks(BlocConversation.Surface, theme))
                assertTrue(
                    "le verre peint de la plaque doit rester strictement < 1 (une vitre, pas un " +
                        "aplat) — ${nom.name}/${if (dark) "sombre" else "clair"} : $verre",
                    verre < 1f,
                )
                // **La zone d'encre protégée n'est JAMAIS opaque** : c'est un voile local sous le
                // texte, jamais la plaque qu'on referme. Son opacité est celle que la mesure retient.
                val zone = BlocConversation.inkZoneAlpha(theme)
                assertTrue(
                    "la zone d'encre protégée doit rester strictement < 1 (un voile local, pas une " +
                        "plaque opaque) — ${nom.name}/${if (dark) "sombre" else "clair"} : $zone",
                    zone < 1f,
                )
            }
        }
        print(rapport)
        // **Le témoin négatif, nommé** : l'ancienne encre de la carte — le blanc pur, posé sur une
        // couverture — ne tient PAS le seuil sur la plaque. C'est ce que la plaque interdit, et le
        // chiffre est là pour que la dent ne soit pas un seuil complaisant.
        val blancSurPlaque = pireRapport(theme(false), Color.White)
        println("  témoin négatif : l'encre blanche de l'ancienne carte = %.2f:1 sur la plaque".format(blancSurPlaque))
        assertTrue(
            "l'ancien blanc pur tient le seuil sur la plaque : la dent ne mord pas, sa mesure ne vaut rien",
            blancSurPlaque < OnyxContrast.TEXT_MIN,
        )
        println("  pire encre mesurée : %.2f:1".format(pire))
        assertTrue(
            "une encre passe sous 4,50:1 sur la plaque :\n" + rapport,
            fautes == 0,
        )
    }

    @Test
    fun `la plaque est celle de la maison, et elle est sans grain`() {
        val surface = GlassSurface.CONVERSATION_CARD
        assertTrue(
            "la carte doit porter la même arête que les autres plaques",
            glassPlateEdgeUsed(surface),
        )
        assertEquals(
            "l'arête peinte est celle d'OnyxEdge (le jeton de la maison)",
            OnyxEdge.TopEdgeLightAlpha,
            glassPlateEdgeAlpha(surface, dark = false),
        )
        assertEquals(
            "l'arête peinte est celle d'OnyxEdge (le jeton de la maison)",
            OnyxEdge.TopEdgeDarkAlpha,
            glassPlateEdgeAlpha(surface, dark = true),
        )
        assertTrue(
            "le flou d'une plaque doit rester dans la fourchette de la maison (36–50 dp) : ${surface.blurDp}",
            surface.blurDp >= GlassBlurMinDp && surface.blurDp <= GlassBlurMaxDp,
        )
        assertEquals(
            "LE GRAIN DE LA PLAQUE DOIT ÊTRE NUL (le propriétaire l'a refusé deux fois)",
            0f,
            glassPlateGrainUsed(surface),
            0f,
        )
        // La teinte franche est celle du verre des plaques (VerreTeinte.kt), en apparence claire.
        assertEquals(
            "la plaque en clair doit porter la teinte franche des plaques (VerreTeinte.kt)",
            GlassPlateTintLight,
            glassTintColour(surface, dark = false),
        )
        // Et la carte passe bien par la vitre de la maison, avec sa forme.
        assertTrue(
            "la carte doit être posée par `liquidGlass` (la vitre de la maison) : $carte",
            source(carte).contains("liquidGlass(GlassSurface.CONVERSATION_CARD"),
        )
        assertTrue(
            "la forme de la carte doit venir d'une constante de la maison, pas d'un nombre écrit sur place",
            BlocConversation.Corner == GlassBarCorner,
        )
    }

    // =============================================================================================
    // 5 · LA ZONE D'ENCRE N'A PLUS D'ARÊTE — et le texte reste sur son plateau
    // =============================================================================================

    /**
     * **Le bord de la zone d'encre ne se voit plus — et la garantie reste.**
     *
     * Le défaut du propriétaire, capture à l'appui : *« tu as un encadré dans le verre, ça fait deux
     * couches pour rien »*. La zone d'encre du lot précédent était un **rectangle à coins arrondis**
     * posé dans la plaque : sa couleur (la page de l'apparence, à `inkZoneAlpha`) diffère de celle de
     * la plaque, donc **son bord se voyait** — mesuré au pixel sur la liste, apparence claire
     * (`qa/visages-synchro/shots/liste-clair-repos.png`) : une marche de **+12** de luminance à
     * `y = 1715` (bord haut) et **−12** à `y = 1807` (bord bas), sur toute la largeur.
     *
     * Ce que la dent exige, et qui est **mesuré sur la fonction que le dessin appelle**
     * ([BlocConversation.inkZoneAlphaAt]) :
     *  - **l'opacité au bord est nulle** → l'écart de luminance au bord est **exactement 0** (aucune
     *    arête, ni en haut, ni en bas, ni sur les côtés — la zone prend toute la largeur, recadrée par
     *    la forme de la plaque) ;
     *  - **la plus grande marche** de la bande d'effacement reste **sous 1 niveau sur 255** ;
     *  - **le texte est sur le plateau** : la zone atteint son opacité pleine avant le rembourrage de
     *    la carte ([OnyxSocle.CardPadding], 16 dp), donc avant le premier glyphe — la garantie du lot
     *    précédent (4,5:1) porte sur la même pire zone, **sans qu'aucun seuil ne bouge** ;
     *  - et **la source le dit** : la zone se peint par `drawRect` (aucune forme, aucun dégradé) —
     *    le verrou « plus aucune image générée sur une carte » n'est pas désarmé.
     */
    @Test
    fun `la zone d encre n a plus d arete et le texte reste sur son plateau`() {
        println("== ZONE D'ENCRE — arête nulle, texte sur le plateau (seuil 4,50:1) ==")
        listOf(false, true).forEach { dark ->
            val theme = theme(dark)
            val apparence = if (dark) "sombre" else "clair"
            val page = BlocConversation.inkZoneColour(theme)
            val full = BlocConversation.inkZoneAlpha(theme)
            val step = BlocConversation.inkZoneAlphaAt(
                theme,
                BlocConversation.InkZoneFade * (1f / BlocConversation.InkZoneStrips),
            )
            println(
                "  %-6s zone plein %.3f · 1re marche %.4f · dégradé %d marches sur %s".format(
                    apparence, full, step, BlocConversation.InkZoneStrips, BlocConversation.InkZoneFade,
                ),
            )

            // (a) L'OPACITÉ AU BORD EST NULLE. C'est la mesure directe : la zone disparaît sur le
            // bord, donc elle n'a pas d'arête.
            assertEquals(
                "$apparence : la zone doit être transparente à son bord (l'encadré visible du " +
                    "propriétaire venait de ce bord non nul)",
                0f, BlocConversation.inkZoneAlphaAt(theme, 0.dp), 0.0001f,
            )

            // L'écart de luminance AU BORD, sur tous les fonds que la plaque peut peindre : la zone à
            // l'opacité du bord par-dessus la plaque, moins la plaque. Nul si l'opacité du bord l'est.
            var pireBord = 0f
            var pireMarche = 0f
            // Le seuil n'est PAS écrit : c'est l'écart de luminance d'**un niveau sur 255**, mesuré
            // sur chaque plaque (la plus sombre est la plus raide) — la marche ne doit pas le
            // dépasser.
            var unNiveau = 0f
            BlocConversation.veils(theme).forEach { veil ->
                listOf(Color.White, Color.Black).forEach { under ->
                    val plaque = compositedBackdrop(veil, under)
                    val auBord = compositedBackdrop(
                        page.copy(alpha = BlocConversation.inkZoneAlphaAt(theme, 0.dp)),
                        plaque,
                    )
                    pireBord = maxOf(pireBord, kotlin.math.abs(OnyxContrast.luminance(auBord) - OnyxContrast.luminance(plaque)))
                    unNiveau = maxOf(
                        unNiveau,
                        kotlin.math.abs(
                            OnyxContrast.luminance(
                                Color(plaque.red + 1f / 255f, plaque.green + 1f / 255f, plaque.blue + 1f / 255f, 1f),
                            ) - OnyxContrast.luminance(plaque),
                        ),
                    )
                    // La plus grande marche visible de la bande : l'écart entre deux marches voisines.
                    var precedent = 0f
                    for (i in 0..BlocConversation.InkZoneStrips) {
                        val a = BlocConversation.inkZoneAlphaAt(
                            theme,
                            BlocConversation.InkZoneFade * (i.toFloat() / BlocConversation.InkZoneStrips),
                        )
                        if (i > 0) {
                            val pas = compositedBackdrop(page.copy(alpha = a), plaque)
                            val avant = compositedBackdrop(page.copy(alpha = precedent), plaque)
                            pireMarche = maxOf(
                                pireMarche,
                                kotlin.math.abs(OnyxContrast.luminance(pas) - OnyxContrast.luminance(avant)),
                            )
                        }
                        precedent = a
                    }
                }
            }
            println(
                "  %-6s écart au bord %.5f · plus grande marche %.5f · un niveau 255 = %.5f".format(
                    apparence, pireBord, pireMarche, unNiveau,
                ),
            )
            assertTrue(
                "$apparence : la zone a une arête — écart de luminance au bord $pireBord (> 0)",
                pireBord <= 0.0001f,
            )
            assertTrue(
                "$apparence : la bande d'effacement saute de ${"%.5f".format(pireMarche)} pour un " +
                    "niveau de 255 valant ${"%.5f".format(unNiveau)} — la marche se voit sur le bord " +
                    "de la bande",
                pireMarche <= unNiveau * 1.02f,
            )

            // (b) LE TEXTE EST SUR LE PLATEAU : la zone atteint son opacité pleine **avant** le
            // rembourrage de la carte, donc avant le premier glyphe.
            assertEquals(
                "$apparence : la zone n'a pas atteint sa pleine opacité au rembourrage de la carte " +
                    "(${BlocConversation.InkZoneFade}) — le texte reposerait sur une zone plus faible " +
                    "que celle que l'encre a mesurée",
                full, BlocConversation.inkZoneAlphaAt(theme, OnyxSocle.CardPadding), 0.0001f,
            )
            assertTrue(
                "$apparence : la bande d'effacement doit tenir DANS le rembourrage de la carte " +
                    "(${BlocConversation.InkZoneFade} < ${OnyxSocle.CardPadding})",
                BlocConversation.InkZoneFade <= OnyxSocle.CardPadding,
            )
            // Et la garantie reste : l'encre de la maison tient 4,5:1 sur la pire zone, la zone
            // comprise — c'est la mesure du lot précédent, reprise ici telle quelle.
            listOf(
                "titre" to BlocConversation.titleInk(theme),
                "heure" to BlocConversation.thirdInk(theme),
            ).forEach { (role, ink) ->
                val pire = BlocConversation.grounds(theme).minOf { OnyxContrast.ratio(ink, it) }
                assertTrue(
                    "$apparence : le $role passe sous 4,50:1 sur la zone d'encre ($pire)",
                    pire >= OnyxContrast.TEXT_MIN,
                )
            }
        }

        // (c) LA SOURCE LE DIT : la zone se peint par `drawRect` — aucune forme (donc pas d'arête
        // interne), aucun dégradé (`Brush.` reste interdit sur une carte par le verrou précédent).
        val zone = source(carte).substringAfter("private fun Modifier.blocConversationInkZone(")
            .substringBefore("\n}\n")
        assertTrue("la zone doit se peindre par `drawRect` : $carte", zone.contains("drawRect("))
        assertTrue("la zone doit lire le profil mesuré (`inkZoneAlphaAt`) : $carte", zone.contains("inkZoneAlphaAt("))
        assertFalse("la zone ne doit plus poser de forme (un bord se verrait) : $carte", zone.contains("RoundedCornerShape"))
        assertFalse("la zone ne doit pas peindre de dégradé (le verrou de la carte l'interdit) : $carte", zone.contains("Brush."))
    }
}
