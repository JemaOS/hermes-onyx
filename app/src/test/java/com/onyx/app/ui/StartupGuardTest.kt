package com.onyx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La boucle « fermeture / réouverture » ne peut pas se répéter.
 *
 * Ce que le propriétaire décrit : il joint une image, se balade dans les onglets du bas, l'app
 * meurt — et **recommence** à chaque fois, parce que le lancement suivant restaure le même
 * onglet, le même fil et la même pièce jointe, et refait donc le même dessin fatal.
 *
 * Le garde-fou tient en trois temps : le drapeau est **écrit avant** la restauration, **effacé**
 * après le premier dessin, et un démarrage qui le retrouve déjà posé revient sur **Chat** sans
 * la pièce jointe du brouillon (le fil garde la sienne).
 *
 * Le magasin est une doublure : la décision se vérifie sans appareil, ce qui est le seul moyen
 * d'éprouver un démarrage après plantage.
 */
class StartupGuardTest {

    /** L'état sauvegardé d'une session : le dernier onglet et la pièce jointe du brouillon. */
    private data class SavedSession(val lastTab: Int, val draftAttachment: String?)

    private class FakeStore(
        var flag: Boolean = false,
        var tab: Int = StartupGuard.CHAT_TAB,
    ) : StartupStore {
        /** Vrai au moment où le drapeau a été posé : il doit l'être **avant** toute restauration. */
        var wroteBeforeRestore = false
        private var began = false

        override fun interrupted(): Boolean = flag

        override fun markRestoring() {
            flag = true
            if (!began) {
                began = true
                wroteBeforeRestore = true
            }
        }

        override fun markSettled() {
            flag = false
        }

        override fun lastTab(): Int = tab

        override fun rememberTab(index: Int) {
            tab = index
        }
    }

    @Test
    fun `un plantage pendant la restauration ramene sur Chat sans la piece jointe`() {
        // Dernière session : l'onglet Fichiers (2) et une image de 8000×6000 dans le brouillon.
        val saved = SavedSession(lastTab = 2, draftAttachment = "content://media/picker/0/photo.jpg")
        val store = FakeStore(flag = false, tab = saved.lastTab)
        val guard = StartupGuard(store)

        // Premier lancement : il pose le drapeau, puis restaure l'onglet et la pièce jointe.
        val first = guard.begin()
        assertFalse("un premier lancement n'est pas un secours", first.interrupted)
        assertFalse("il ne jette pas la pièce jointe", first.dropDraftAttachments)
        assertTrue("le drapeau est écrit avant la restauration", store.wroteBeforeRestore)
        assertTrue("le drapeau est bien posé pendant la restauration", store.flag)
        assertEquals("l'onglet sauvegardé reste celui de la session", 2, store.lastTab())

        // Le processus meurt ici : `settled()` n'est jamais appelé.

        // Deuxième lancement : c'est un démarrage de secours.
        val second = guard.begin()
        assertTrue("le garde-fou voit le plantage", second.interrupted)
        assertTrue("il rouvre sur Chat", second.chatTab)
        assertTrue("il écarte la pièce jointe du brouillon", second.dropDraftAttachments)
        assertEquals("l'onglet fautif est oublié", StartupGuard.CHAT_TAB, store.lastTab())
        guard.settled()

        // Troisième lancement : normal, plus aucune trace du secours.
        val third = guard.begin()
        assertFalse("le secours ne dure pas", third.interrupted)
        assertFalse(third.chatTab)
        assertFalse(third.dropDraftAttachments)
    }

    @Test
    fun `un demarrage ordinaire garde l'onglet ou l'on etait`() {
        val store = FakeStore(flag = false, tab = 3)
        val guard = StartupGuard(store)
        val plan = guard.begin()
        guard.settled()

        assertFalse(plan.interrupted)
        assertEquals("l'onglet habituel est respecté", 3, store.lastTab())
        assertFalse("le drapeau est effacé après le premier dessin", store.flag)
    }

    @Test
    fun `l'onglet note par l'app est celui du prochain demarrage`() {
        val store = FakeStore()
        val guard = StartupGuard(store)
        guard.begin()
        guard.noteTab(4)
        guard.settled()
        assertEquals(4, guard.lastTab())
    }
}
