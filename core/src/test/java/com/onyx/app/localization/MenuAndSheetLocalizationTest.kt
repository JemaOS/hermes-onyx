package com.onyx.app.localization

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les libellés **déclarés** dans un menu, une feuille d'action, une alerte ou une invite passent
 * tous par le dictionnaire français.
 *
 * Le défaut d'origine : le menu d'une carte **Kanban** affichait « Modifier » et « Supprimer » en
 * français, mais « **Move to Archived** » en anglais — le libellé se compose (« Move to » + le nom
 * de la colonne), donc aucune entrée exacte ne le couvrait. Le test ferme la récidive de deux
 * façons :
 *
 *  1. il **parcourt le source** des menus et des feuilles (les `MenuItem(…)` et `AlertAction(…)`
 *     déclarés dans l'arbre de l'UI) et refuse tout libellé qui n'a pas de traduction française ;
 *  2. il vérifie l'autre sens du contrat : chaque entrée du dictionnaire des menus
 *     ([onyxMenuSheetFrench]) correspond à un libellé bien présent dans le source — pas d'entrée
 *     morte.
 *
 * Le mécanisme reprend celui de [SetupScreenLocalizationTest] / [CodeScreenLocalizationTest]
 * (`translate` comparé à la clé), et lit le source comme le fait déjà le test des libellés
 * d'écran (`ScreenLabelsLocalizationTest`).
 */
class MenuAndSheetLocalizationTest {

    private val fr = Locale.FRANCE

    /** La racine du dépôt, remontée depuis le dossier de travail du test (le module `core`). */
    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("racine du dépôt introuvable depuis ${File("").absolutePath}")

    /** Tous les fichiers Kotlin de l'UI : c'est là que les menus et les feuilles se déclarent. */
    private val uiSources: List<File> =
        listOf("app", "core", "auto", "wear")
            .map { File(repoRoot, "$it/src/main/java") }
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            // Le dictionnaire lui-même n'affiche rien : seuls les écrans comptent.
            .filterNot { it.path.contains("/localization/") }

    /**
     * Les libellés écrits en clair dans un menu, une feuille d'action ou une ligne de la feuille de
     * pièce jointe : le premier argument d'un `MenuItem(…)` / `AlertAction(…)` / `AttachmentAction(…)`,
     * qu'il soit passé nu ou par `label =`, y compris les deux branches d'un `if (…) "…" else "…"`.
     */
    private fun declaredMenuLabels(): Set<String> {
        val labels = linkedSetOf<String>()
        val call = Regex("""(?:MenuItem|AlertAction|AttachmentAction)\s*\(""")
        val literal = Regex(""""((?:\\.|[^"\\])*)"""")
        uiSources.forEach { file ->
            val text = file.readText()
            call.findAll(text).forEach { match ->
                // `match.range.last` est la parenthèse ouvrante de l'appel.
                argumentList(text, match.range.last).let { body ->
                    literal.findAll(body).forEach { found -> labels += found.groupValues[1].unescape() }
                }
            }
        }
        return labels
    }

    /**
     * L'argument d'un appel : de sa parenthèse ouvrante à sa fermante. Les littéraux sont sautés
     * (une parenthèse dans une chaîne ne compte pas), et la lecture **s'arrête à la première
     * accolade ouvrante** — le corps d'une lambda (posée hors des parenthèses, en Kotlin) ou d'une
     * lambda nommée (`onSelect = { … }`) n'est pas un libellé : c'est là que vit un texte comme
     * `ClipData.newPlainText("LaTeX", …)`, qui n'a rien à voir avec le menu.
     */
    private fun argumentList(text: String, open: Int): String {
        var depth = 0
        var index = open
        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    index++
                    while (index < text.length && text[index] != '"') {
                        if (text[index] == '\\') index++
                        index++
                    }
                }
                '{' -> return text.substring(open + 1, index)
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(open + 1, index)
                }
            }
            index++
        }
        // Appel non refermé (fichier tronqué) : on ne devine pas, la fenêtre s'arrête là.
        return text.substring(open + 1)
    }

    private fun String.unescape(): String =
        replace("\\\"", "\"").replace("\\$", "$")

    @Test
    fun `aucun libelle de menu ni d action de feuille ne reste en anglais`() {
        val declared = declaredMenuLabels()
        assertTrue("le balayage ne lit aucun libellé (chemins faux ?)", declared.size > 40)
        val untranslated = declared
            // Un libellé composé n'est pas une clé : il est couvert par un gabarit (le Kanban, plus bas).
            .filterNot { it.contains("$") }
            .filterNot { it in identicalInFrench }
            .filter { OnyxLocalization.translate(it, fr) == it }
            .sorted()
        assertTrue("libellés de menu encore en anglais : $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `le menu d une carte Kanban nomme la colonne en francais`() {
        // Le défaut signalé : deux entrées françaises, la troisième anglaise.
        assertEquals("Modifier", OnyxLocalization.translate("Edit", fr))
        assertEquals("Supprimer", OnyxLocalization.translate("Delete", fr))
        assertNotEquals("Move to Archived", OnyxLocalization.translate("Move to Archived", fr))
        assertEquals("Déplacer vers « Archivé »", OnyxLocalization.translate("Move to Archived", fr))
        // La question de la confirmation, et une autre colonne : le nom vient de la capsule du haut.
        assertEquals("Déplacer vers « Archivé » ?", OnyxLocalization.translate("Move to Archived?", fr))
        assertEquals("Archivé", OnyxLocalization.translate("Archived", fr))
        assertEquals("Déplacer vers « À faire »", OnyxLocalization.translate("Move to To do", fr))
        // Une entrée exacte reste prioritaire sur le gabarit.
        assertEquals("Déplacer vers un projet", OnyxLocalization.translate("Move to Project", fr))
        // En anglais, rien ne bouge.
        assertEquals("Move to Archived", OnyxLocalization.translate("Move to Archived", Locale.ENGLISH))
        assertEquals("Move to Archived?", OnyxLocalization.translate("Move to Archived?", Locale.ENGLISH))
    }

    @Test
    fun `les feuilles d action et les alertes sont traduites`() {
        val cases = linkedMapOf(
            "Math" to "Maths",
            "Copy LaTeX" to "Copier le LaTeX",
            "DIAGRAM" to "DIAGRAMME",
            "Diagram" to "Diagramme",
            "Mermaid diagram" to "Diagramme Mermaid",
            "Open diagram full screen" to "Ouvrir le diagramme en plein écran",
            "Couldn't do it" to "Impossible de le faire",
        )
        cases.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `le dictionnaire des menus ne porte aucune entree inutilisee`() {
        val haystack = uiSources.joinToString("\n") { it.readText() }
        val unused = onyxMenuSheetFrench.keys
            // Un gabarit de `String.format` (« %1$s is %2$s… ») s'écrit dans le source avec son
            // dollar échappé : la clé telle quelle, et sa forme écrite, comptent toutes les deux.
            .filterNot { key -> haystack.contains("\"$key\"") || haystack.contains("\"${key.replace("$", "\\$")}\"") }
            .sorted()
        assertTrue("entrées de menu sans libellé correspondant dans le source : $unused", unused.isEmpty())
    }

    @Test
    fun `les libelles identiques en francais restent ecrits ainsi`() {
        // Mots qui s'écrivent pareil dans les deux langues : ils ne doivent ni bouger ni réclamer
        // une traduction inventée (« OK », « Terminal », « Pause »).
        identicalInFrench.forEach { key ->
            assertEquals("« $key » ne doit pas changer", key, OnyxLocalization.translate(key, fr))
        }
    }

    private companion object {
        /** Les libellés de menu qui s'écrivent déjà en français à l'identique. */
        val identicalInFrench = setOf("Terminal", "OK", "Pause")
    }
}
