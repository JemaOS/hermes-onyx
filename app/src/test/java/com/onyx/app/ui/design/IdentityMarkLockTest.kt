package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import com.onyx.app.ui.design.sidebarPaneColor
import com.onyx.app.ui.sidebar.SidebarRowTone
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.OnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des marques d'identité — un seul composant, une seule géométrie, une seule teinte.**
 *
 * Il tient trois choses, et chacune est mesurée plutôt que jugée :
 *
 *  1. **la marque du coin n'est plus peinte sur les cartes de conversation**, et l'identité d'une
 *     ligne se résout **par conversation** — sur le **nom de profil de son bot**, la clé unique de la
 *     maison (`BotLook` / `conversationProfile`). Deux défauts sont nommés ici : la marque refusée par
 *     le propriétaire (« supprime la petite partie avec le cercle »), et l'identité résolue par
 *     **serveur** — qui donnait le **même** visage à trois conversations de bots différents d'un même
 *     serveur (« trois cartes, un seul visage ») ;
 *  2. **le contraste ne tombe pas sous le seuil**, dans **les deux apparences** : le glyphe sur son
 *     disque (4,5:1 — c'est du texte), le point de présence sur le disque (3:1 — c'est un trait) ;
 *  3. **aucune géométrie ni teinte n'est écrite à la main** sur un site d'appel : les valeurs vivent
 *     dans les constantes de `IdentityMark` ([IdentityMark.Diameter], [IdentityMark.Corner],
 *     [IdentityMark.StrokeWidth], [IdentityMark.Glyph], [IdentityMark.Inset]), et
 *     [IdentityMark.fitsGeometry] dit si une marque est de la maison.
 *
 * **La séparation de la couverture** est prouvée à part ([IdentityMark.coverageProof]) : les deux
 * lisérés d'appui garantissent le seuil sur **toute** luminance de couverture — donc sur une
 * couverture claire, sombre **et** chargée, sans rien connaître d'elle.
 */
class IdentityMarkLockTest {

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    private val mark = read("core/src/main/java/com/onyx/app/ui/design/IdentityMark.kt")
    private val sidebar = read("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
    private val model = read("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarModel.kt")
    private val host = read("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarHost.kt")
    private val botsSource = read("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt")
    private val app = read("app/src/main/java/com/onyx/app/ui/OnyxApp.kt")

    private fun theme(theme: OnyxTheme, dark: Boolean) =
        ResolvedTheme.resolve(theme, dark = dark, fonts = OnyxFonts.System)

    /** Les identités de l'épreuve : les deux bots de la capture, le bot principal, et deux libellés. */
    private val identities = listOf("QA-tester", "coder", "default", "Onyx", "qa", "code")

    /** `fichier:ligne` d'une aiguille dans un source, ou `null` — pour nommer la faute. */
    private fun where(source: String, needle: String): String? =
        source.lines().indexOfFirst { it.contains(needle) }
            .takeIf { it >= 0 }
            ?.let { it + 1 }
            ?.toString()

    /** Le corps d'une fonction privée d'un source, de sa signature à l'accolade fermante de colonne 0. */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}\n", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }

    /**
     * **Ce que la pastille du coin peint quand elle teinte par l'ÉTAT** — la valeur d'avant, telle
     * qu'elle était écrite à la main sur le site d'appel : la teinte de l'état composée sur le
     * panneau à 18 %. C'est l'une des deux valeurs divergentes que le message nomme.
     */
    private fun etatPeint(tone: SidebarRowTone, theme: ResolvedTheme): Color {
        val tint = when (tone) {
            SidebarRowTone.WAITING -> theme.warning
            else -> theme.accent
        }
        return OnyxContrast.over(tint.copy(alpha = 0.18f), sidebarPaneColor(theme))
    }

    private fun rgb(color: Color): String =
        "(%.3f, %.3f, %.3f)".format(color.red, color.green, color.blue)

    /**
     * **Le ton d'une ligne, tel que la marque le porte** — recopié ici pour que l'épreuve du
     * contraste tourne **aussi sur le source d'avant** : un test qui ne compilerait que sur le
     * nouveau source ne pourrait pas mordre, et la morsure est la preuve du verrou.
     */
    private fun presenceOf(tone: SidebarRowTone): IdentityMark.Presence? = when (tone) {
        SidebarRowTone.RUNNING -> IdentityMark.Presence.RUNNING
        SidebarRowTone.WAITING -> IdentityMark.Presence.WAITING
        SidebarRowTone.UNREAD -> IdentityMark.Presence.UNREAD
        SidebarRowTone.IDLE -> null
    }

    // =============================================================================================
    // 1. La même identité montre la même marque
    // =============================================================================================

    @Test
    fun `la marque du coin N EST PLUS peinte sur les cartes, et l identite se resout par conversation`() {
        val disc = bodyOf(sidebar, "private fun ToneDisc(")
        assertTrue(
            "la pastille du coin d'une carte de conversation a disparu du volet",
            disc.isNotEmpty(),
        )

        // Les deux valeurs divergentes, calculées : celle de l'état (la carte d'avant) et celle de
        // l'identité (la liste des bots, la fiche, l'en-tête, le volet — un seul visage).
        val clair = theme(OnyxTheme.STANDARD, dark = false)
        val sombre = theme(OnyxTheme.STANDARD, dark = true)
        val divergence = buildString {
            for ((tone, quoi) in listOf(
                SidebarRowTone.WAITING to "une question qui attend",
                SidebarRowTone.RUNNING to "un tour en cours",
            )) {
                append(
                    "  · la carte de la liste (« QA-tester », $quoi) teinte la pastille par l'ÉTAT : " +
                        "${rgb(etatPeint(tone, clair))} au clair, ${rgb(etatPeint(tone, sombre))} en sombre\n",
                )
            }
            append(
                "  · partout ailleurs, la même identité montre le disque de son bot : " +
                    "${rgb(IdentityMark.discFor("QA-tester", null, clair))} au clair, " +
                    "${rgb(IdentityMark.discFor("QA-tester", null, sombre))} en sombre\n",
            )
        }

        // La marque passe par le composant unique — jamais par une teinte d'état écrite ici.
        val faute = listOf(
            "SidebarRowTone.WAITING -> theme.warning",
            "SidebarRowTone.RUNNING -> theme.accent",
            "SidebarRowTone.UNREAD -> theme.accent",
            "theme.tertiaryText to Symbols.TextBubble",
        ).firstOrNull { disc.contains(it) }
        assertTrue(
            "la même identité montre DEUX marques différentes : la carte de la liste teinte sa " +
                "pastille par l'état de la conversation, alors que la liste des bots, la fiche, " +
                "l'en-tête et le volet montrent la marque de l'identité.\n" +
                "  Fautif : app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt" +
                (where(sidebar, faute.orEmpty())?.let { ":$it" } ?: "") +
                " — `$faute`\n" + divergence,
            faute == null,
        )
        // **Le composant reste déclaré** : la marque n'est pas supprimée en silence (le volet porte
        // encore son site `ToneDisc`, qui peint `IdentityMark`) — mais il n'est plus **peint sur une
        // carte**.
        assertTrue(
            "la marque du coin a disparu du composant unique (`IdentityMark`) : géométrie et teinte " +
                "seraient recopiées au site d'appel",
            disc.contains("IdentityMark("),
        )
        // **Ce que le propriétaire a demandé** : la retirer des cartes. La dent exige donc maintenant
        // le contraire de ce qu'elle exigeait — la marque **n'est plus peinte** au coin d'une carte.
        assertFalse(
            "la marque du coin est ENCORE peinte sur une carte de conversation : le propriétaire l'a " +
                "refusée (« supprime la petite partie avec le cercle, le visage c'est moche »). " +
                "Fautif : app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt" +
                (where(sidebar, "ToneDisc(row.tone, row.identity")?.let { ":$it" } ?: "") +
                " — `ToneDisc(row.tone, row.identity…)`",
            sidebar.contains("state = { ToneDisc(") || sidebar.contains("ToneDisc(row.tone, row.identity"),
        )
        assertTrue(
            "le site de la marque ne dit pas qu'elle n'est plus peinte sur les cartes : le prochain " +
                "lot la remettrait sans savoir que c'est interdit",
            sidebar.contains("PLUS PEINTE SUR LES CARTES"),
        )
        // L'identité d'une ligne est le **nom de profil** de son bot, résolue **par conversation**.
        assertTrue(
            "la ligne du volet ne porte plus l'identité de son bot",
            model.contains("val identity: String = \"\","),
        )
        assertTrue(
            "l'identité d'une ligne n'est plus résolue PAR CONVERSATION — c'est le défaut de synchro " +
                "« trois cartes, un seul visage » (une identité par serveur donne le même visage à " +
                "trois bots du même serveur). Fautif : ChatSidebarModel.kt" +
                (where(model, "identityOf(conversation.serverId)")?.let { ":$it" } ?: ""),
            model.contains("identityOf(conversation)"),
        )
        assertFalse(
            "l'identité d'une ligne est encore résolue par serveur (`conversation.serverId`) : les " +
                "cartes d'un même serveur partagent leur visage. Fautif : ChatSidebarModel.kt" +
                (where(model, "identityOf(conversation.serverId)")?.let { ":$it" } ?: ""),
            model.contains("identityOf(conversation.serverId)"),
        )
        // Le visage vient de l'**identité** — `faceCodeOf` — et, quand le fil a le sien, de lui
        // d'abord ([Conversation.faceCode], `ThreadFaceTest`). Jamais de l'état de la carte.
        assertTrue(
            "le visage de la ligne est résolu par l'identité, avec le visage du fil en premier",
            model.contains("identityFace = conversation.faceCode ?: faceCodeOf(identity),"),
        )
        assertTrue(
            "et il ne se relit pas au même endroit que la marque",
            model.contains("faceCodeOf(identity)"),
        )
        assertTrue(
            "le volet ne passe plus l'identité de chaque conversation",
            host.contains("identityOf = identityOf,"),
        )
        // Le profil, jamais le libellé, et **par conversation** : `conversationProfile` est le calcul
        // de la maison (conversation → bot → profil du serveur → `default`).
        assertTrue(
            "l'identité d'une conversation n'est plus résolue sur le profil de SON bot " +
                "(`conversationProfile`, le calcul de l'en-tête et des onglets). Fautif : OnyxApp.kt",
            app.contains("conversationProfile("),
        )
        assertFalse(
            "l'identité d'une conversation est ENCORE résolue par serveur (`identityByServer`) : un " +
                "seul profil pour toutes les conversations du serveur. Fautif : OnyxApp.kt" +
                (where(app, "identityByServer")?.let { ":$it" } ?: ""),
            app.contains("identityByServer"),
        )
    }

    @Test
    fun `le bot de la liste des bots montre bien le visage de son profil`() {
        // La carte de bot montre son visage : la même clé (nom de profil + code enregistré) que la
        // marque d'identité, donc la même teinte — jamais deux identités pour un bot.
        assertTrue(
            "la carte de bot ne résout plus son visage sur son nom de profil",
            botsSource.contains("BotAvatar(name = bot.name, faceCode = faceCode,"),
        )
    }

    // =============================================================================================
    // 2. Le contraste — dans les deux apparences
    // =============================================================================================

    @Test
    fun `le contraste des marques tient le seuil de la maison dans les deux apparences`() {
        val rapport = StringBuilder()
        var fautes = 0
        for (dark in listOf(false, true)) {
            val theme = theme(OnyxTheme.STANDARD, dark = dark)
            for (identity in identities) {
                val disc = IdentityMark.discFor(identity, null, theme)
                val encre = IdentityMark.markInkContrast(disc)
                val (corpsClair, corpsProfond) = IdentityMark.faceBodyFor(identity, null, theme)
                val visage = IdentityMark.faceContrast(
                    disc, corpsClair, corpsProfond, IdentityMark.featuresFor(corpsProfond),
                )
                rapport.append(
                    ("%-10s apparence=%-5s disque=%s encre=%s %.2f:1 (min %.1f) · " +
                        "visage %.2f:1 (min %.1f, corps↔disque %.2f:1)\n").format(
                        identity, if (dark) "sombre" else "clair", rgb(disc), rgb(IdentityMark.inkFor(disc)),
                        encre, IdentityMark.GlyphMin, visage, IdentityMark.EdgeMin,
                        IdentityMark.bodyOnDisc(corpsProfond, disc),
                    ),
                )
                if (encre < IdentityMark.GlyphMin - 0.01f) fautes++
                if (visage < IdentityMark.EdgeMin - 0.01f) fautes++
                for (tone in SidebarRowTone.entries) {
                    val presence = presenceOf(tone) ?: continue
                    val point = IdentityMark.presenceInk(presence, disc, theme) ?: continue
                    val contraste = OnyxContrast.ratio(point, disc)
                    rapport.append(
                        "             presence %-8s %s %.2f:1 (min %.1f)\n".format(
                            presence, rgb(point), contraste, IdentityMark.EdgeMin,
                        ),
                    )
                    if (contraste < IdentityMark.EdgeMin - 0.01f) fautes++
                }
            }
        }
        println(rapport)
        assertEquals(
            "des marques d'identité passent sous le seuil de la maison :\n" +
                rapport.lineSequence().filter { it.contains(":1") }
                    .filter { line ->
                        val value = Regex("(\\d+\\.\\d+):1").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                        value != null && value < IdentityMark.EdgeMin - 0.01f
                    }
                    .joinToString("\n"),
            0,
            fautes,
        )
    }

    @Test
    fun `la teinte d une identite ne depend pas de l apparence pour une couleur semee`() {
        val clair = theme(OnyxTheme.STANDARD, dark = false)
        val sombre = theme(OnyxTheme.STANDARD, dark = true)
        for (identity in listOf("QA-tester", "coder", "qa", "code")) {
            assertEquals(
                "la marque de $identity change de teinte avec l'apparence : ce n'est plus une identité",
                IdentityMark.tintFor(identity, null, clair),
                IdentityMark.tintFor(identity, null, sombre),
            )
        }
    }

    // =============================================================================================
    // 3. La géométrie et les teintes — les constantes de la maison, jamais recopiées
    // =============================================================================================

    @Test
    fun `la geometrie est celle de la maison et rien n est ecrit a la main sur un site d appel`() {
        assertTrue("le diamètre n'est plus une constante de la maison", mark.contains("val Diameter: Dp = 32.dp"))
        assertTrue("l'épaisseur du trait n'est plus une constante", mark.contains("val StrokeWidth: Dp = 1.dp"))
        assertTrue("le diamètre du visage n'est plus dérivé du disque et du trait", mark.contains("val Face: Dp get() = Diameter - StrokeWidth * 4"))
        assertTrue("l'écart au bord n'est plus la marche de la maison", mark.contains("val Inset: Dp = OnyxBase.Tight"))
        assertTrue("le rayon de coin n'est plus dérivé du diamètre", mark.contains("fun CornerOf(diameter: Dp): Dp = diameter / 2"))
        assertTrue(
            "la géométrie de la maison ne tient plus : disque, rayon, trait ou glyphe hors des bornes",
            IdentityMark.fitsGeometry(),
        )
        assertTrue(
            "le diamètre de la maison n'est plus une taille admise",
            IdentityMark.Sizes.contains(IdentityMark.Diameter),
        )
        // La géométrie d'avant — le disque de 26 dp et son voile noir écrit à la main — n'est plus là.
        val disc = bodyOf(sidebar, "private fun ToneDisc(")
        assertFalse(
            "une dimension est écrite à la main sur le site d'appel de la marque (ChatSidebar.kt, " +
                "`private fun ToneDisc`) : elle doit venir des constantes d'IdentityMark",
            disc.contains("size(26.dp)") || disc.contains("26.dp") || disc.contains("13.dp"),
        )
        assertFalse(
            "une teinte est écrite à la main sur le site d'appel de la marque (le voile noir de 30 % " +
                "qui rendait l'encre annoncée différente de l'encre peinte)",
            disc.contains("Color.Black.copy(alpha = 0.30f)"),
        )
        assertTrue(
            "le site d'appel ne pose plus la marque d'identité du tout",
            disc.contains("IdentityMark("),
        )
    }

    @Test
    fun `la marque se detache d une couverture claire, sombre ou chargee`() {
        val pire = IdentityMark.coverageProof()
        println("séparation minimale sur toute luminance de couverture : %.3f:1 (seuil %.1f)".format(pire, IdentityMark.EdgeMin))
        assertTrue(
            "la marque peut se fondre dans une couverture : le pire rapport de séparation est " +
                "%.2f:1, sous le seuil de %.1f:1".format(pire, IdentityMark.EdgeMin),
            pire >= IdentityMark.EdgeMin - 0.01f,
        )
        // Pour une couverture claire, une couverture sombre et une couverture chargée, la mesure.
        val clair = Color.White
        val sombre = Color.Black
        val chargee = Color(0.42f, 0.30f, 0.62f)
        for (disc in listOf(Color(0.96f, 0.68f, 0.22f), Color(0.36f, 0.42f, 0.94f), Color(0.13f, 0.69f, 0.60f))) {
            for ((nom, cover) in listOf("claire" to clair, "sombre" to sombre, "chargée" to chargee)) {
                val separe = IdentityMark.separation(disc, cover)
                println("disque %s sur couverture %-8s : %.2f:1".format(rgb(disc), nom, separe))
                assertTrue(
                    "une marque se fond dans une couverture $nom (%.2f:1)".format(separe),
                    separe >= IdentityMark.EdgeMin - 0.01f,
                )
            }
        }
    }

    @Test
    fun `la marque porte le VISAGE du bot, la meme resolution que partout ailleurs`() {
        // **Ce que le propriétaire a demandé** : « le petit logo en haut à droite, mets plutôt le
        // visage du bot en question ». La marque ne peint donc plus de glyphe monochrome : elle
        // résout le visage à la **même clé** que la fiche, l'en-tête et le volet (`botFace` sur le
        // nom de profil et le code enregistré), et c'est ce visage qui est posé dans le disque.
        for ((nom, code) in listOf("default" to null, "coder" to null, "QA-tester" to null)) {
            assertEquals(
                "la marque de $nom ne montre pas le visage de son profil",
                botFace(nom, code),
                IdentityMark.faceFor(nom, code),
            )
        }
        // Le visage est peint par la MÊME pièce que partout (aucune seconde version du dessin).
        assertTrue(
            "la marque ne peint plus le visage par le composant unique (`BotLook`)",
            mark.contains("BotLook("),
        )
        assertTrue(
            "le glyphe monochrome (initiale ou rune) est encore peint par la marque",
            !mark.contains("drawText(") && !mark.contains("OnyxRune.placed("),
        )
    }

    @Test
    fun `le visage se detache de son disque — la dent MORD sans le lisere, et passe avec`() {
        val rapport = StringBuilder()
        var pire = Float.MAX_VALUE
        var pireSansLisere = Float.MAX_VALUE
        for (dark in listOf(false, true)) {
            val theme = theme(OnyxTheme.STANDARD, dark = dark)
            for (colour in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, BotFace.themed)) {
                val (corpsClair, corpsProfond) = BotFace.gradient(colour, theme)
                val disc = OnyxContrast.filled(corpsProfond, IdentityMark.GlyphMin)
                val sansLisere = IdentityMark.bodyOnDisc(corpsProfond, disc)
                val avec = IdentityMark.faceContrast(
                    disc, corpsClair, corpsProfond, IdentityMark.featuresFor(corpsProfond),
                )
                pireSansLisere = minOf(pireSansLisere, sansLisere)
                pire = minOf(pire, avec)
                rapport.append(
                    ("  %-6s couleur %2d · corps↔disque SANS liséré %.2f:1 · AVEC liséré %.2f:1%n")
                        .format(if (dark) "sombre" else "clair", colour, sansLisere, avec),
                )
            }
        }
        println("lisibilité du visage dans son disque :\n$rapport")
        // **LA MORSure ROUGE** : le visage SANS liséré ne se détache pas de son disque (il est peint
        // dans la même couleur) — c'est le défaut que le liséré intérieur corrige, et il est mesuré.
        assertTrue(
            ("un visage SANS liséré devrait se confondre avec son disque : mesuré %.2f:1, " +
                "attendu sous %.1f:1 — la dent ne mord donc pas").format(pireSansLisere, IdentityMark.EdgeMin),
            pireSansLisere < IdentityMark.EdgeMin,
        )
        // **VERT APRÈS** : avec le liséré, toutes les couleurs passent.
        assertTrue(
            "le visage ne se détache pas de son disque : pire rapport %.2f:1, seuil %.1f:1\n%s"
                .format(pire, IdentityMark.EdgeMin, rapport),
            pire >= IdentityMark.EdgeMin - 0.01f,
        )
        // Et la preuve de couverture reste celle de la maison (le disque contre la couverture).
        assertTrue(
            "le disque ne se détache plus de la couverture : %.2f:1".format(IdentityMark.coverageProof()),
            IdentityMark.coverageProof() >= IdentityMark.EdgeMin - 0.01f,
        )
    }
}
