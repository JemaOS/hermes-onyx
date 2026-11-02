package com.onyx.app.ui.bots

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Change Face » du bot en creation doit ouvrir l'ecran de visage.**
 *
 * Le defaut : l'ecran « New Bot » est l'editeur avec `bot = null` — le bot n'existe pas encore
 * sur le serveur. Le geste « Change Face » y etait cable ainsi :
 *
 * ```
 * onFaceChosen = { if (bot != null) navTo(Route.BOT_FACE.name) }
 * ```
 *
 * donc, pour un bot neuf, le geste **ne faisait rien** : pas d'ecran de visage, pas de choix, et
 * le bot cree gardait le visage de son modele. Le meme defaut existait sur l'ecran de visage
 * lui-meme (`if (bot == null) { retour immediat }`), qui n'avait donc rien a montrer.
 *
 * Ce verrou tient les quatre bouts, parce qu'il suffit d'un seul pour perdre la correction :
 *
 * 1. le **geste** — « Change Face » ouvre l'ecran de visage meme quand le bot n'existe pas ;
 * 2. l'**ecran** — il sert le bot en creation (par son nom) au lieu de renvoyer ;
 * 3. le **retour** — le visage choisi revient a l'editeur, qui l'affiche et le pose a la creation ;
 * 4. la **morsure** — la regle est une fonction pure : rejouee sur l'ancien cablage, elle
 *    **echoue**. Il n'y a donc pas de version de ce test qui passerait avec le defaut.
 */
class ChangeFaceNouveauBotTest {

    private fun read(path: String): String {
        val file = listOf(path, "../$path").map(::File).firstOrNull { it.isFile }
            ?: error("fichier introuvable : $path")
        return file.readText()
    }

    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val editor = read("src/main/java/com/onyx/app/ui/bots/BotEditorScreen.kt")

    /**
     * **La regle, reduite a une fonction pure.** C'est elle le verrou : elle est vraie sur le code
     * corrige, fausse sur le defaut — et c'est la **meme** fonction qui est interrogee dans les
     * deux cas, donc le test ne peut pas etre « vrai par construction ».
     *
     * Elle exige que la branche « pas de bot » ne soit pas un simple `navTo` coupe court : il
     * faut qu'elle **pose un etat** (le nom du bot en creation) et qu'elle **ouvre** l'ecran.
     */
    private fun opensFaceForNewBot(source: String): Boolean {
        // La branche « pas de bot » doit poser l'etat (le nom du bot en creation) ET ouvrir
        // l'ecran de visage.
        val named = Regex(
            """onFaceChosenNamed\s*=\s*\{(.*?)\n\s{20}\},""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(source)?.groupValues?.get(1) ?: return false
        return named.contains("navTo(Route.BOT_FACE.name)") &&
            named.contains("newBotName = name")
    }

    // ---- 1. Le geste -------------------------------------------------------------------------

    @Test
    fun `le geste Change Face ouvre l ecran de visage meme pour un bot qui n existe pas encore`() {
        assertTrue(
            "le geste doit ouvrir l'ecran de visage pour un bot en creation",
            opensFaceForNewBot(app),
        )
        assertFalse(
            "et le defaut — une simple condition `if (bot != null)` qui coupe court — ne doit plus",
            Regex("""onFaceChosenNamed\s*=\s*\{\s*if\s*\(\s*bot\s*!=\s*null\s*\)\s*navTo\(Route\.BOT_FACE\.name\)\s*\}""")
                .containsMatchIn(app),
        )
        assertFalse(
            "et le defaut — le geste muet quand il n'y a pas de bot — ne doit plus",
            Regex("""onFaceChosenNamed\s*=\s*\{\s*if\s*\(\s*bot\s*!=\s*null\s*\)""")
                .containsMatchIn(app),
        )
    }

    // ---- 2. L'ecran de visage sert le bot en creation ------------------------------------------

    @Test
    fun `l ecran de visage sert le bot en creation au lieu de renvoyer`() {
        val branch = Regex(
            """Route\.BOT_FACE\s*->\s*\{(.*?)\n\s{12}\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).find(app)?.groupValues?.get(1)
        assertTrue("la route BOT_FACE doit etre cablee", branch != null)
        val text = branch!!
        assertTrue(
            "la route doit connaître le bot en creation (son nom)",
            text.contains("newBotName"),
        )
        assertTrue(
            "et la servir : le picker s'ouvre pour lui",
            text.contains("keptWithBot = true"),
        )
        assertFalse(
            "un bot absent NE REVOQUE PAS l'ecran quand un bot est en creation",
            Regex("""if\s*\(\s*bot\s*==\s*null\s*\)\s*\{\s*LaunchedEffect""").containsMatchIn(text) &&
                !text.contains("pendingName == null"),
        )
    }

    // ---- 3. Le retour : le visage choisi revient a l'editeur --------------------------------------

    @Test
    fun `le visage choisi revient a l editeur, qui l affiche et le pose a la creation`() {
        assertTrue(
            "l'etat du visage choisi doit exister",
            app.contains("var newBotFaceCode by rememberSaveable"),
        )
        assertTrue(
            "et l'editeur le recoit",
            app.contains("chosenFaceCode = if (bot == null) newBotFaceCode else null"),
        )
        assertTrue(
            "l'editeur l'applique a son apercu",
            editor.contains("if (chosenFace != null) pickedFace = chosenFace"),
        )
        assertTrue(
            "et le pose sur le bot cree",
            editor.contains("shown, pickedFace?.code()"),
        )
    }

    // ---- 4. La morsure : l'ancien cablage fait echouer la regle ----------------------------------

    @Test
    fun `remettre l ancien cablage fait echouer la regle`() {
        val old = app.replace(
            Regex(
                """onFaceChosenNamed = \{.*?\n\s{20}\},""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ),
            "onFaceChosenNamed = { _, _ -> navTo(Route.BOT_FACE.name) },",
        )
        assertFalse(
            "avec l'ancien cablage, la regle doit ECHOUER (le verrou mord)",
            opensFaceForNewBot(old),
        )
    }
}
