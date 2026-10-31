package com.onyx.app.ui.sidebar

import com.onyx.app.ui.design.SectionAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des actions de section** — l'écart sous l'en-tête, et une seule forme.
 *
 * Le défaut relevé par le propriétaire : le « + » de **BOTS** touchait la première rangée de cartes
 * et celui de **SALONS** recouvrait la première ligne de salon (« 2965w » juste sous le bouton) ;
 * les deux actions ne se ressemblaient pas non plus — 44 dp d'un côté, 48 dp de l'autre, et deux
 * tailles de glyphe.
 *
 * Le verrou a deux étages, et il mord sur le premier :
 *
 *  1. **la source, mesurée.** Chaque en-tête de section de l'app est lu dans son fichier : la hauteur
 *     minimale de la ligne, le rembourrage bas, la taille de l'action. L'écart qui en résulte est
 *     calculé (l'action est centrée) et comparé au plancher de la maison, [SectionAction.MinGap].
 *     Sur la source d'avant, l'en-tête de BOTS ne portait aucun rembourrage bas : le calcul rend
 *     **0 dp**, et le message nomme la section — « BOTS : écart 0 dp… ».
 *  2. **le geste, mesuré sur l'appareil.** Les relevés de `qa/2026-10-30-actions-section/apres`
 *     (arbre d'accessibilité + capture, deux apparences) portent les écarts réels en dp ; ils sont
 *     relus ici, et le verrou tombe si l'un repasse sous le plancher ou si deux actions n'ont plus
 *     la même taille ni la même forme.
 */
class SectionActionGeometryTest {

    /** La racine du dépôt : les tests tournent depuis `app/`. */
    private val root: File = File("..").canonicalFile

    private fun source(path: String): String = File(root, path).readText()

    /** Le bloc d'une fonction ou d'un appel : d'une ancre jusqu'à la fin de sa portée. */
    private fun block(text: String, anchor: String, length: Int = 900): String {
        val at = text.indexOf(anchor)
        assertTrue("ancre absente de la source : $anchor", at >= 0)
        return text.substring(at, minOf(text.length, at + length))
    }

    /** La valeur en dp d'un jeton d'espacement — les noms de la maison, pas des nombres en l'air. */
    private fun dp(token: String): Float = when (token) {
        "SectionAction.Gap" -> SectionAction.Gap.value
        "SectionAction.Size" -> SectionAction.Size.value
        "MinTarget" -> 44f
        "OnyxSocle.TouchTarget" -> 48f
        else ->
            if (token.endsWith(".dp")) token.removeSuffix(".dp").toFloat()
            else error("valeur d'espacement inconnue : $token")
    }

    private fun token(block: String, key: String): String? =
        Regex("\\b$key\\s*=\\s*([A-Za-z0-9_.]+)").find(block)?.groupValues?.get(1)

    /** Un en-tête de section : sa ligne, son rembourrage bas, l'action qu'il porte. */
    private class Header(
        val fichier: String,
        val section: String,
        val minHeight: Float,
        val bottom: Float,
        val action: Float,
    ) {
        /** L'action est centrée dans la ligne : le bas du bouton laisse la moitié du reste. */
        val gap: Float get() = bottom + maxOf(0f, (minHeight - action) / 2f)
    }

    private fun header(
        fichier: String,
        section: String,
        definition: String,
        appel: String,
    ): Header {
        val def = definition
        val minHeight = token(def, "minHeight")?.let(::dp) ?: 0f
        val bottom = token(def, "bottom")?.let(::dp) ?: 0f
        // L'action : sa taille est **celle du composant** ; si un appel en impose une autre, c'est
        // déjà une divergence — elle est lue ici, et le verrou la nomme.
        val action = token(appel, "size")?.let(::dp) ?: SectionAction.Size.value
        return Header(fichier, section, minHeight, bottom, action)
    }

    private fun botsHeaders(): List<Header> {
        val src = source("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt")
        // Le bloc de la définition porte ses commentaires : il est large à dessein.
        val def = block(src, "private fun SectionHeader(", length = 2500)
        return listOf("Bots", "Rooms").map { title ->
            header(
                "ChatSidebarBots.kt",
                if (title == "Bots") "BOTS" else "SALONS",
                def,
                block(src, "SectionHeader(title = \"$title\""),
            )
        }
    }

    private fun chatsHeaders(): List<Header> {
        val src = source("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
        val titles = block(src, "private fun SidebarTileTitleRow(", length = 2500)
        return listOf(
            header(
                "ChatSidebar.kt",
                "RACCOURCIS",
                titles,
                block(src, "private fun SidebarTileTitleRow(", length = 2000),
            ),
        )
    }

    @Test
    fun `aucune action de section ne touche le contenu qui suit`() {
        // Toutes les sections fautives sont nommées d'un coup : un verrou qui s'arrête à la première
        // laisse croire que le reste est conforme.
        val fautives = (botsHeaders() + chatsHeaders())
            .filter { it.gap < SectionAction.MinGap.value }
            .map {
                "${it.section} : écart ${it.gap} dp — l'action de section touche le contenu qui suit " +
                    "(plancher ${SectionAction.MinGap.value} dp ; ligne ${it.minHeight} dp, " +
                    "action ${it.action} dp, rembourrage bas ${it.bottom} dp)"
            }
        assertTrue(fautives.joinToString("\n"), fautives.isEmpty())
    }

    @Test
    fun `les actions de section ont toutes la meme taille`() {
        val tailles = (botsHeaders() + chatsHeaders()).map { it.action }.distinct()
        assertEquals(
            "deux actions de section n'ont pas la même taille : $tailles — une seule taille, " +
                "${SectionAction.Size.value} dp, portée par SectionActionButton",
            1,
            tailles.size,
        )
        assertEquals(SectionAction.Size.value, tailles.single(), 0.01f)
    }

    @Test
    fun `un seul composant porte les actions de section`() {
        val bots = source("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebarBots.kt")
        val chats = source("app/src/main/java/com/onyx/app/ui/sidebar/ChatSidebar.kt")
        assertEquals(
            "l'en-tête de BOTS et celui de SALONS doivent passer par SectionActionButton",
            2,
            Regex("SectionActionButton\\(").findAll(bots).count(),
        )
        assertTrue(
            "un GlassCircleButton écrit à la main dans un en-tête rouvre la porte aux deux tailles",
            !bots.contains("GlassCircleButton("),
        )
        assertEquals(
            "le crayon de RACCOURCIS et le « + » de TOUS LES PROJETS passent par le même composant",
            2,
            Regex("SectionActionButton\\(").findAll(chats).count(),
        )
        val composant = source("core/src/main/java/com/onyx/app/ui/design/SectionAction.kt")
        assertTrue(
            "SectionActionButton doit citer GlassCircleButton et RoundButtonShape (le vrai cercle)",
            composant.contains("GlassCircleButton(") &&
                source("core/src/main/java/com/onyx/app/ui/design/OnyxControls.kt")
                    .contains("glassSurface(RoundButtonShape)"),
        )
    }

    @Test
    fun `la geometrie de la maison se calcule comme elle se dessine`() {
        // Ligne de 44 dp, action de 44 dp, rembourrage bas de 8 dp : l'écart est 8 dp, pas 0.
        assertEquals(8.0f, SectionAction.gap(44.dp(), SectionAction.Size, SectionAction.Gap), 0.01f)
        // Sans rembourrage bas — la source d'avant —, l'écart tombe à zéro : contact.
        assertEquals(0.0f, SectionAction.gap(44.dp(), SectionAction.Size, 0.dp()), 0.01f)
        // Une ligne plus haute centre l'action : la moitié du reste s'ajoute sous elle.
        assertEquals(10.0f, SectionAction.gap(48.dp(), SectionAction.Size, SectionAction.Gap), 0.01f)
        assertTrue(SectionAction.MinGap.value >= 8f)
        // Recouvrement : la boîte du bouton descend sous le haut de la ligne suivante.
        assertEquals(4.0f, SectionAction.overlap(actionBottomDp = 104f, contentTopDp = 100f), 0.01f)
        assertEquals(0.0f, SectionAction.overlap(actionBottomDp = 96f, contentTopDp = 100f), 0.01f)
    }

    @Test
    fun `les mesures sur l'appareil ne repassent pas sous le plancher`() {
        val mesures = File(root, "qa/2026-10-30-actions-section/apres/mesures.json")
        assertTrue("les mesures de l'appareil manquent : ${mesures.path}", mesures.isFile)
        val texte = mesures.readText()
        // Chaque section mesurée est un objet du JSON : on le découpe sur sa clé « section », puis
        // on relit ses champs un par un (l'ordre des clés n'est pas un contrat).
        val entrees = texte.split("\"section\":").drop(1)
        assertTrue("aucune mesure lisible dans ${mesures.path}", entrees.size >= 4)
        val tailles = mutableSetOf<String>()
        var lues = 0
        for (bloc in entrees) {
            fun champ(cle: String): String? =
                Regex("\"$cle\": \"?([^\",]+)\"?").find(bloc)?.groupValues?.get(1)
            val section = Regex("^\\s*\"([^\"]+)\"").find(bloc)?.groupValues?.get(1) ?: continue
            val ecart = champ("ecart_dp")?.trim()?.toFloatOrNull() ?: continue
            val cote = champ("cote_dp")?.trim() ?: continue
            lues++
            assertTrue(
                "$section : écart $ecart dp — sous le plancher de " +
                    "${SectionAction.MinGap.value} dp, l'action touche la ligne suivante",
                ecart >= SectionAction.MinGap.value,
            )
            tailles += cote
        }
        assertTrue("aucune section mesurée n'a pu être relue", lues >= 4)
        assertEquals(
            "deux actions de section mesurées n'ont pas la même taille : $tailles",
            1,
            tailles.size,
        )
    }
}

/** Un nombre de dp, pour les tests d'arithmétique. */
private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
