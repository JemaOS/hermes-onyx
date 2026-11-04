package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Aucune explication d'un écran de Réglages ne reste en anglais.**
 *
 * Le défaut signalé sur l'écran Appels — « CALLS FOR AN ANSWER » et ses phrases d'explication en
 * anglais sous des libellés français — n'était pas un oubli isolé : quatre autres `footer` de
 * Réglages n'avaient pas d'entrée au dictionnaire non plus (Connexion ×3, Alertes ×1). Une phrase
 * longue est justement celle qu'on lit pour comprendre ce qu'on règle : elle ne peut pas rester
 * dans la langue de la source.
 *
 * Ce verrou lit **les écrans**, pas le dictionnaire — c'est la seule façon d'attraper le cas réel :
 * un `header`/`footer` écrit dans l'écran, que le dictionnaire ne connaît pas. Il recolle d'abord
 * les littéraux concaténés (`"a " + "b"` est une seule clé), puis exige, pour chaque phrase de
 * trois mots ou plus, que le rendu français diffère de la source. Les textes **composés** (une
 * interpolation `$…`) sont écartés : ils demandent un motif, pas une entrée — c'est le cas des
 * heures de tranquillité, tenues par `CallsLocalizationTest`.
 */
class SettingsFooterLocalizationTest {

    private val screensDir = File("src/main/java/com/onyx/app/ui/settings")

    /** `"a " + "b"` → `"a b"` : une clé est la phrase entière, pas son premier morceau. */
    private val concatenation = Regex("\"\\s*\\+\\s*\"")

    /** `header = "…"` ou `footer = "…"`, après recollement des littéraux. */
    private val attribute = Regex("\\b(header|footer)\\s*=\\s*\"([^\"]{5,})\"")

    @Test
    fun `toute explication d un ecran de Reglages a sa traduction francaise`() {
        val files = screensDir.listFiles { file -> file.extension == "kt" }?.sortedBy { it.name }.orEmpty()
        assertTrue("les écrans de Réglages sont introuvables : $screensDir", files.isNotEmpty())

        val gaps = mutableListOf<String>()
        files.forEach { file ->
            val source = file.readText().replace(concatenation, "")
            attribute.findAll(source).forEach { match ->
                val key = match.groupValues[2]
                // Un texte composé par l'écran n'est pas une clé : il lui faut un motif.
                if ('$' in key) return@forEach
                if (key.trim().split(' ').size < 3) return@forEach
                if (OnyxLocalization.translate(key, Locale.FRANCE) == key) {
                    gaps += "${file.name}: $key"
                }
            }
        }

        assertTrue(
            "explications affichées en anglais :\n" + gaps.joinToString("\n"),
            gaps.isEmpty(),
        )
    }
}
