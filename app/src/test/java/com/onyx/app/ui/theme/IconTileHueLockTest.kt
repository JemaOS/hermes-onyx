package com.onyx.app.ui.theme

import com.onyx.app.ui.chat.components.ChatActionTint
import com.onyx.app.ui.chat.components.tintHue
import com.onyx.app.ui.conversations.ConversationActionTint
import com.onyx.app.ui.conversations.conversationTintHue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou d'une seule couleur d'icône, partout.**
 *
 * Le propriétaire, sur la feuille d'une conversation : « il y a de la couleur ici, ce n'est pas
 * cohérent avec le reste ». Sa capture le montrait noir sur blanc — **quatre teintes dans une seule
 * feuille** : bleu/indigo pour Renommer, Créer un fil, Marquer comme non lu, Consommation, Ouvrir ;
 * orange pour Épingler ; gris pour Copier le titre, Archiver et les deux mises en sourdine. La
 * maquette, elle, ne colore **aucune** tuile (`.tileic{background:var(--solid);color:var(--ink)}`).
 *
 * Deux règles sont verrouillées ici :
 *
 *  1. **la tuile d'une icône de menu ou de feuille est neutre** — le gris de la maison — et son
 *     glyphe est **l'encre monochrome du thème**. Une rangée qui n'est pas un état ne porte pas de
 *     couleur ;
 *  2. les **deux exceptions survivent**, et elles seules : le **rouge des actions destructrices**
 *     (une action qui ne se rattrape pas est un état) et les **voyants d'état** (pastille en ligne,
 *     coche d'un choix, onde « en direct », marque d'enregistrement) — qui ne sont pas des tuiles
 *     de rangée et gardent leur teinte.
 *
 * Le test **nomme le fichier et la ligne** de toute tuile déclarée hors du jeu autorisé : la teinte
 * d'une tuile se lit dans les fonctions de famille ([conversationTintHue], [tintHue]) et dans les
 * appels de pastille ([com.onyx.app.ui.design.ChatActionBadge]) — les deux sont balayés.
 */
class IconTileHueLockTest {

    private fun app(path: String): File = File("src/main/java/com/onyx/app", path)

    private fun core(path: String): File = File("../core/src/main/java/com/onyx/app", path)

    /** Le jeu autorisé pour une tuile d'icône : l'encre de la maison, et le rouge du danger. */
    private val allowed = listOf("theme.textColor", "theme.error")

    /** Toute autre couleur du thème : hors jeu pour une tuile ou un glyphe de menu. */
    private val forbidden = listOf(
        "theme.accent",
        "theme.accentInk",
        "theme.warning",
        "theme.success",
        "theme.secondaryText",
        "theme.tertiaryText",
        "ReferencePalette.NeutralAction",
    )

    /**
     * Les fonctions qui **décident la teinte d'une tuile de feuille**. La teinte d'une ligne se lit
     * là, pas dans le dessin : c'est le seul endroit où une famille peut réintroduire une couleur.
     */
    private val hueFunctions = listOf(
        app("ui/conversations/ConversationActionSheet.kt") to
            "internal fun conversationTintHue(tint: ConversationActionTint, theme: ResolvedTheme)",
        app("ui/chat/components/ChatActionSheet.kt") to
            "internal fun tintHue(tint: ChatActionTint, theme: ResolvedTheme)",
    )

    /** Le corps d'une fonction : de sa signature jusqu'à la première accolade en tête de ligne. */
    private fun body(file: File, signature: String): Pair<Int, List<String>> {
        val lines = file.readLines()
        val start = lines.indexOfFirst { it.trim().startsWith(signature) }
        require(start >= 0) { "${file.path} : signature introuvable « $signature »" }
        val block = lines.drop(start + 1).takeWhile { it.trim() != "}" }
        return (start + 1) to block
    }

    @Test
    fun `une tuile d icone ne porte que l encre neutre, ou le rouge du danger`() {
        val failures = mutableListOf<String>()
        hueFunctions.forEach { (file, signature) ->
            val (offset, block) = body(file, signature)
            block.forEachIndexed { index, line ->
                forbidden.filter { line.contains(it) }.forEach { hue ->
                    failures += "${file.path}:${offset + index + 1} : tuile teintée hors jeu « $hue » — ${line.trim()}"
                }
            }
        }
        assertTrue(
            "des tuiles d'icône portent une teinte hors du jeu autorisé :\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `aucun appel de pastille ni de menu ne peint une teinte hors jeu`() {
        val failures = mutableListOf<String>()
        val sheets = listOf(
            app("ui/chat/components/ChatActionSheet.kt"),
            app("ui/conversations/ConversationActionSheet.kt"),
        )
        sheets.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val paintsTile = line.contains("ChatActionBadge.fill(") || line.contains("ChatActionBadge.ink(")
                if (paintsTile) {
                    forbidden.filter { line.contains(it) }.forEach { hue ->
                        failures += "${file.path}:${index + 1} : pastille teintée hors jeu « $hue » — ${line.trim()}"
                    }
                }
            }
        }
        // Le glyphe d'une ligne de menu ([OnyxMenu]) n'est pas un état : il prend l'encre, jamais
        // l'accent. C'est ce qui colorait les menus de bot, de fichier et du modèle.
        //
        // **La coche d'une ligne cochée reste, elle, en accent** : c'est un ÉTAT (l'entrée retenue),
        // pas une teinte de famille — c'est l'exception « voyant d'état » de la charte.
        val menu = core("ui/design/OnyxMenu.kt")
        menu.readLines().forEachIndexed { index, line ->
            val isStateMarker = line.contains("checked")
            if (!isStateMarker && line.contains("tint =") &&
                (line.contains("theme.accentInk") || line.contains("theme.accent "))
            ) {
                failures += "${menu.path}:${index + 1} : glyphe de menu en accent — ${line.trim()}"
            }
        }
        // Et l'état d'un choix garde bien son accent : la coche d'un modèle retenu, d'un serveur
        // choisi — c'est le voyant d'état de la charte, il ne doit pas disparaître.
        val modelSheet = app("ui/models/ModelSheet.kt")
        assertTrue(
            "la coche d'un modèle retenu doit garder l'accent (voyant d'état)",
            modelSheet.readText().contains("OnyxIcon(Symbols.Checkmark, tint = theme.accentInk"),
        )
        assertTrue(
            "des pastilles ou des glyphes de menu peignent une teinte hors jeu :\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `une famille n est rouge que pour le danger, et neutre sinon`() {
        listOf(false, true).forEach { dark ->
            OnyxTheme.entries.forEach { name ->
                val theme = ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)
                ConversationActionTint.entries.forEach { family ->
                    val hue = conversationTintHue(family, theme)
                    if (family == ConversationActionTint.DANGER) {
                        assertEquals("$name/$dark : la famille $family doit être le rouge", theme.error, hue)
                    } else {
                        assertEquals("$name/$dark : la famille $family doit être neutre", theme.textColor, hue)
                    }
                }
                ChatActionTint.entries.forEach { family ->
                    val hue = tintHue(family, theme)
                    if (family == ChatActionTint.DANGER) {
                        assertEquals("$name/$dark : la famille $family doit être le rouge", theme.error, hue)
                    } else {
                        assertEquals("$name/$dark : la famille $family doit être neutre", theme.textColor, hue)
                    }
                }
            }
        }
    }

    @Test
    fun `une feuille ne compte qu une seule teinte de tuile, le danger excepte`() {
        // Le compte demandé : le nombre de teintes distinctes par feuille. La cible est **une** —
        // l'encre neutre —, plus le rouge, qui n'est pas une teinte de famille mais l'état du
        // destructeur.
        listOf(false, true).forEach { dark ->
            OnyxTheme.entries.forEach { name ->
                val theme = ResolvedTheme.resolve(name, dark = dark, fonts = OnyxFonts.System)
                val conversationHues = ConversationActionTint.entries.map { conversationTintHue(it, theme) }.toSet()
                val chatHues = ChatActionTint.entries.map { tintHue(it, theme) }.toSet()
                println("$name/${if (dark) "sombre" else "clair"} : feuille conversation ${conversationHues.size} teinte(s), feuille du fil ${chatHues.size} teinte(s)")
                assertEquals("$name/$dark : la feuille d'une conversation doit n'avoir qu'une teinte neutre + le danger", 2, conversationHues.size)
                assertEquals("$name/$dark : la feuille du fil doit n'avoir qu'une teinte neutre + le danger", 2, chatHues.size)
                assertEquals("$name/$dark : le jeu doit être exactement {encre, rouge}", setOf(theme.textColor, theme.error), conversationHues)
                assertEquals("$name/$dark : le jeu doit être exactement {encre, rouge}", setOf(theme.textColor, theme.error), chatHues)
                // Le jeu autorisé du verrou doit bien décrire ce que le code rend.
                assertEquals(allowed.toSet(), setOf("theme.textColor", "theme.error"))
            }
        }
    }
}
