package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Libellés corrigés par la QA fonctionnelle: la barre de sélection, l'état vide du kanban, les
 * résumés de serveur et les pieds de page des horaires d'un bot.
 */
class FunctionalQaLocalizationTest {
    private val fr = Locale.FRANCE

    @Test
    fun `la barre de selection multiple traduit ses compteurs`() {
        assertEquals("Archiver 0", OnyxLocalization.translate("Archive 0", fr))
        assertEquals("Supprimer 3", OnyxLocalization.translate("Delete 3", fr))
        assertEquals("Supprimer 1 conversation ?", OnyxLocalization.translate("Delete 1 conversation", fr))
        assertEquals("Supprimer 2 conversations ?", OnyxLocalization.translate("Delete 2 conversations", fr))
        assertEquals("1 conversation sélectionnée", OnyxLocalization.translate("1 conversation selected", fr))
        assertEquals("4 conversations sélectionnées", OnyxLocalization.translate("4 conversations selected", fr))
    }

    @Test
    fun `l'etat vide du kanban combine le nom traduit de la colonne`() {
        assertEquals("Rien dans À faire", OnyxLocalization.format("Nothing in %1\$s", OnyxLocalization["To do", fr], locale = fr))
        assertEquals("Rien dans Prêt", OnyxLocalization.format("Nothing in %1\$s", OnyxLocalization["Ready", fr], locale = fr))
        assertEquals("Rien dans Bloqué", OnyxLocalization.format("Nothing in %1\$s", OnyxLocalization["Blocked", fr], locale = fr))
    }

    @Test
    fun `les libelles de serveur et de verrouillage sont traduits`() {
        assertEquals("non configuré", OnyxLocalization.translate("not set up", fr))
        assertEquals("Dashboard Hermes", OnyxLocalization.translate("Hermes Dashboard", fr))
        assertEquals("API Hermes", OnyxLocalization.translate("Hermes API", fr))
        assertEquals("Exiger un code", OnyxLocalization.translate("Require a passcode", fr))
        assertEquals("Exiger le visage ou l’empreinte", OnyxLocalization.translate("Require Face or fingerprint", fr))
        assertEquals("requis, par exemple hermes", OnyxLocalization.translate("required, such as hermes", fr))
    }

    @Test
    fun `les pieds de page des horaires d'un bot sont traduits avec son nom`() {
        val empty = OnyxLocalization.translate(
            "Give kilo something to do at a time: every weekday at 7, check the weather and your calendar and say what to wear. Its answer comes into its chat.",
            fr,
        )
        assertEquals(
            "Donnez à kilo une tâche à une heure précise : en semaine à 7 h, consultez la météo et votre agenda et dites quoi porter. Sa réponse arrive dans son chat.",
            empty,
        )
        val footer = OnyxLocalization.translate(
            "Each run is kilo's, with its own personality and tools, and its answer comes into its chat, with a notification, while your phone is off. Times are your server's. Hermes 0.21.5 and later run a bot's schedules by themselves; earlier ones only with gateway.multiplex_profiles on.",
            fr,
        )
        assertEquals(true, footer.startsWith("Chaque exécution est celle de kilo"))
        assertEquals(false, footer.contains("gateway.multiplex_profiles on"))
    }

    @Test
    fun `le terminal ne laisse pas that user en anglais`() {
        val text = OnyxLocalization.translate(
            "Add it as one line to ~/.ssh/authorized_keys for that user on 10.0.2.2. The key was made on this phone and never leaves it.",
            fr,
        )
        assertEquals(true, text.contains("pour cet utilisateur sur 10.0.2.2"))
        assertEquals(false, text.contains("that user"))
    }

    @Test
    fun `en anglais les nouveaux libelles restent inchanges`() {
        assertEquals("Archive 0", OnyxLocalization.translate("Archive 0", Locale.US))
        assertEquals("Require a passcode", OnyxLocalization.translate("Require a passcode", Locale.US))
    }
}
