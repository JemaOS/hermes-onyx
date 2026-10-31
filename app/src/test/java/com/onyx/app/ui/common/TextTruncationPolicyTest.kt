package com.onyx.app.ui.common

import com.onyx.app.ui.design.IdentifierFit
import com.onyx.app.ui.design.OnyxTextPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou des troncatures.** Une coupure de texte n'est plus une décision locale qu'on prend en
 * écrivant un `Text` : elle appartient à une **famille** ([OnyxTextPolicy.Kind]), et ce test refuse
 * les coupures qui n'appartiennent à aucune.
 *
 * Ce qui a été relevé, et ce que le verrou empêche de revenir :
 *
 * - un **libellé d'action**, un **état**, un nom de bot ou un titre de rangée se coupait à une
 *   ligne (« Mode… », « En cour… ») alors que la place manquait d'un coup à police 1,3× ;
 * - une **adresse de serveur**, un **chemin**, un **nom de modèle** ou une **cible MCP** se coupait
 *   **à la fin** — donc on ne lisait plus où l'adresse menait, ce qui est justement la partie utile.
 *
 * La règle qui reste : **seule une donnée libre** — un nom de fichier, un aperçu de message, un
 * extrait de code, une question transcrite — peut se couper sur une ligne. Tout le reste se replie
 * (`maxLines = 2`) ou s'abrège au milieu ([IdentifierFit]).
 *
 * Le test lit la **source**, il ne fige donc pas d'écrans : un écran ajouté demain est contrôlé
 * comme les autres.
 */
class TextTruncationPolicyTest {

    private val main = File("src/main/java")

    private data class Site(
        val file: String,
        val line: Int,
        val text: String,
        val maxLines: Int?,
        val ellipsis: Boolean,
    )

    /** Les sites de troncature par ellipse, avec leur expression de texte et leur `maxLines`. */
    private fun sites(): List<Site> {
        val out = mutableListOf<Site>()
        main.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { f ->
            val lines = f.readLines()
            lines.forEachIndexed { i, l ->
                if (!l.contains("TextOverflow.Ellipsis")) return@forEachIndexed
                var start = i
                var k = i
                while (k >= 0 && k > i - 45) {
                    if (Regex("""\b(Text|BasicText|SelectableText)\s*\(""").containsMatchIn(lines[k])) {
                        start = k
                        break
                    }
                    k--
                }
                val block = lines.subList(start, i + 1)
                val text = block.firstNotNullOfOrNull { b ->
                    Regex("""text\s*=\s*(.+?),?\s*$""").find(b)?.groupValues?.get(1)?.trim()
                } ?: Regex("""\(\s*([A-Za-z_][^,]*)""").find(block.first())
                    ?.groupValues?.get(1)?.trim().orEmpty()
                val maxLines = block.firstNotNullOfOrNull { b ->
                    Regex("""maxLines\s*=\s*([^,\s]+)""").find(b)?.groupValues?.get(1)?.toIntOrNull()
                }
                out += Site(
                    file = f.path.removePrefix(main.path + "/"),
                    line = i + 1,
                    text = text,
                    maxLines = maxLines,
                    ellipsis = true,
                )
            }
        }
        return out
    }

    /**
     * Le **vocabulaire de la donnée** : ce qui a le droit de se couper sur une ligne, parce que
     * c'est écrit par la personne ou reçu du serveur, et que le déplier ferait sauter la hauteur
     * des rangées. Tout ce qui n'est pas là est un libellé, un état ou un identifiant.
     */
    private val dataVocabulary = Regex(
        """(\.name\b|file\.|entry\.|source\.|attachment\.|\btext\b|\bline\b|\bpreview\b|\bmessage\b|""" +
            """question|\bit\b|dayLabel|take\(|toString\(\)|Onyx will use this)""",
    )

    /**
     * Le **plafond des coupures restantes** : **21**.
     *
     * Dix-huit d'entre elles sont des **données** — un nom de fichier, un aperçu de message, un
     * extrait de code, une question transcrite : les déplier ferait sauter la hauteur des rangées
     * (le défaut « grille en escalier »), et elles ne portent aucun mot clé à perdre.
     *
     * Trois font exception, **et chacune a son verrou propre** :
     *
     * - les compteurs (`count.toString()`, `commentCount.toString()`) : un nombre ne se coupe pas, et
     *   la règle du volet exige une ellipse plutôt qu'une coupure sèche (`ResponsiveLayoutTest`) ;
     * - le badge « Onyx will use this » de la rangée d'adresse : sa règle **mesurée** est « entier
     *   sur une ligne, sinon rejeté *entier* à la ligne suivante » — le replier le ferait remonter
     *   sur le titre, le défaut d'origine (`SetupBadgeLayoutTest`).
     *
     * Le plafond ne peut descendre qu'en corrigeant des sites ; il ne remonte pas sans une raison
     * écrite ici.
     */
    private val dataCutsCeiling = 21

    @Test
    fun `aucun libelle ni identifiant n est coupe sur une seule ligne`() {
        val oneLine = sites().filter { it.maxLines == 1 }
        val offenders = oneLine.filterNot { dataVocabulary.containsMatchIn(it.text) }
        assertTrue(
            "Ces textes ne sont pas des données : ils se replient (maxLines = 2) ou s'abrègent au " +
                "milieu (IdentifierText), jamais sur une ligne.\n" +
                offenders.joinToString("\n") { "  ${it.file}:${it.line} → ${it.text}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `le budget des coupures de donnees ne remonte pas`() {
        val oneLine = sites().filter { it.maxLines == 1 }
        assertTrue(
            "Il y a ${oneLine.size} coupures de données pour un plafond de $dataCutsCeiling. " +
                "Une coupure de plus est un libellé, un état ou un identifiant déguisé : corriger " +
                "le site, ou écrire ici la raison qui fait monter le plafond.\n" +
                oneLine.joinToString("\n") { "  ${it.file}:${it.line} → ${it.text}" },
            oneLine.size <= dataCutsCeiling,
        )
    }

    @Test
    fun `les identifiants ne passent plus par l ellipse`() {
        // Les expressions qui DOIVENT rester exactes pour être utiles.
        val identifier = Regex(
            """(address|\.path\b|target|\.model\b|\bmodel\b|host\b|url|endpoint|\bsite\b)""",
            RegexOption.IGNORE_CASE,
        )
        val offenders = sites().filter { identifier.containsMatchIn(it.text) && (it.maxLines ?: 1) < 2 }
        assertTrue(
            "Une adresse, un chemin, un modèle ou une cible ne se coupe pas : ils passent par " +
                "IdentifierText, qui garde le début ET la fin.\n" +
                offenders.joinToString("\n") { "  ${it.file}:${it.line} → ${it.text}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `la politique se replie ou s abrege, jamais ne coupe un identifiant`() {
        // La table de la politique : elle est le contrat que le verrou ci-dessus fait respecter.
        assertEquals(2, OnyxTextPolicy.maxLines(OnyxTextPolicy.Kind.LABEL))
        assertEquals(2, OnyxTextPolicy.maxLines(OnyxTextPolicy.Kind.TITLE))
        assertEquals(1, OnyxTextPolicy.maxLines(OnyxTextPolicy.Kind.IDENTIFIER))
        assertFalse(OnyxTextPolicy.allowsEllipsis(OnyxTextPolicy.Kind.IDENTIFIER))
        assertTrue(OnyxTextPolicy.allowsEllipsis(OnyxTextPolicy.Kind.DATA))
        assertTrue(OnyxTextPolicy.isAnonymousCut(OnyxTextPolicy.Kind.LABEL, maxLines = 1))
        assertFalse(OnyxTextPolicy.isAnonymousCut(OnyxTextPolicy.Kind.LABEL, maxLines = 2))
    }

    // — L'abrègement au milieu : une fonction pure, donc éprouvable sans écran —————————

    /** Une mesure de test : une largeur fixe par caractère, comme une police à chasse fixe. */
    private fun measure(perChar: Int): (String) -> Int = { it.length * perChar }

    @Test
    fun `un texte qui tient n est pas touche`() {
        val text = "https://exemple.com/v1"
        assertEquals(text, IdentifierFit.middleEllipsis(text, 10_000, measure(10)))
    }

    @Test
    fun `un texte trop long garde son debut ET sa fin`() {
        val text = "https://api.exemple.com/onyx/v1/endpoints"
        val shown = IdentifierFit.middleEllipsis(text, 200, measure(10))
        assertTrue("l'ellipse manque : $shown", shown.contains(IdentifierFit.ELLIPSIS))
        assertTrue("le début est perdu : $shown", shown.startsWith("http"))
        assertTrue("la fin est perdue : $shown", shown.endsWith("points"))
        assertTrue("le résultat dépasse la place : $shown", measure(10)(shown) <= 200)
    }

    @Test
    fun `l abrevement tient dans la place offerte, quelle qu elle soit`() {
        val text = "wss://un-nom-de-tunnel-tres-long.trycloudflare.com/ordinateur"
        (20..400 step 10).forEach { width ->
            val shown = IdentifierFit.middleEllipsis(text, width, measure(10))
            assertTrue(
                "à $width px, « $shown » (${measure(10)(shown)} px) dépasse",
                measure(10)(shown) <= width || shown == IdentifierFit.ELLIPSIS,
            )
        }
    }

    @Test
    fun `l abrevement ne produit jamais un texte vide`() {
        val text = "C:/un/chemin/interminable/jusqu/au/fichier.txt"
        val shown = IdentifierFit.middleEllipsis(text, 8, measure(10))
        assertTrue("vide : « $shown »", shown.isNotEmpty())
    }
}
