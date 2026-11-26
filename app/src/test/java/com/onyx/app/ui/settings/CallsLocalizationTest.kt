package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'écran Appels ne laisse aucun texte en anglais.**
 *
 * Le défaut relevé sur la capture du propriétaire : les libellés étaient français (« Sonner pour
 * une décision », « Répondre en voix live », « Heures de tranquillité ») mais les **titres de
 * section** (« CALLS FOR AN ANSWER », « TRY IT ») et les **quatre phrases d'explication** restaient
 * dans la langue de la source — c'est-à-dire que les `header` et les `footer` d'`OnyxFormSection`
 * n'avaient pas d'entrée au dictionnaire, alors que les libellés, eux, en avaient une.
 *
 * Le verrou tient les deux moitiés : les clés fixes passent toutes par [OnyxLocalization] en
 * français, et les deux phrases que l'écran **compose** (les heures de tranquillité) sont couvertes
 * par un motif, pas par une égalité de chaîne.
 */
class CallsLocalizationTest {

    /** Les textes fixes de l'écran, tels qu'il les passe à `OnyxFormSection` / `OnyxPageHeader`. */
    private val fixedStrings = listOf(
        "Calls",
        "Calls for an answer",
        "When a turn is waiting for your decision — an approval or a question — the phone rings " +
            "like a call, full screen over the lock screen. Off, you get the usual notification " +
            "instead.",
        "Ring for a decision",
        "Answering",
        "On: answering puts Onyx on the line, listening — you say your decision out loud. Off: " +
            "answering opens the conversation, where the buttons wait.",
        "Answer with live voice",
        "Quiet hours",
        "Starts",
        "Ends",
        "Nothing is quiet yet.",
        "Try it",
        "Rings this phone now, exactly as a decision would. Declining leaves the usual " +
            "notification.",
        "Ring this phone",
    )

    @Test
    fun `chaque texte fixe de l ecran Appels a sa traduction francaise`() {
        val untranslated = fixedStrings.filter { key ->
            OnyxLocalization.translate(key, Locale.FRANCE) == key
        }
        assertTrue("sans traduction française : $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `le detail des heures de tranquillite est traduit, plage ou plage vide`() {
        val range = quietHoursDetail(enabled = true, from = 22 * 60, until = 7 * 60)
        val empty = quietHoursDetail(enabled = true, from = 8 * 60, until = 8 * 60)
        val off = quietHoursDetail(enabled = false, from = 22 * 60, until = 7 * 60)

        listOf(range, empty, off).forEach { detail ->
            val shown = OnyxLocalization.translate(detail, Locale.FRANCE)
            assertNotEquals("« $detail » reste en anglais", detail, shown)
        }
        // Le motif garde les deux heures telles que l'écran les a écrites.
        assertEquals(
            "Rien ne sonne de 22:00 à 07:00.",
            OnyxLocalization.translate(range, Locale.FRANCE),
        )
    }

    /**
     * **La source, pas seulement le dictionnaire.** Une clé traduite ne sert à rien si l'écran
     * l'écrit avec `localize = false` : on vérifie donc que les `header`/`footer` de l'écran sont
     * bien ceux du dictionnaire (aucun des deux n'est passé en texte verbatim).
     */
    @Test
    fun `l ecran passe bien ses titres et ses explications par le dictionnaire`() {
        val source = File("src/main/java/com/onyx/app/ui/settings/CallsScreen.kt").readText()

        assertTrue("le titre de la première section", source.contains("header = \"Calls for an answer\""))
        assertTrue("le titre de la démonstration", source.contains("header = \"Try it\""))
        assertTrue(
            "aucune section ne coupe la traduction de son en-tête ou de son pied",
            !source.contains("localizeHeader = false") && !source.contains("localizeFooter = false"),
        )
        // **Et les clés du dictionnaire sont bien celles que l'écran écrit.** Un dictionnaire qui
        // traduit une phrase légèrement différente de celle de l'écran ne traduit rien : chaque
        // explication doit se retrouver, mot pour mot, dans la source de l'écran.
        val footers = listOf(
            "When a turn is waiting for your decision — an approval or a",
            "On: answering puts Onyx on the line, listening — you say",
            "Rings this phone now, exactly as a decision would. Declining",
        )
        footers.forEach { head ->
            assertTrue("l'écran n'écrit pas « $head… »", source.contains(head))
        }
    }
}
