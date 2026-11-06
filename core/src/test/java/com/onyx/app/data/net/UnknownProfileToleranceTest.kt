package com.onyx.app.data.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Un profil mort ne vide pas la liste.**
 *
 * Le registre répond « ce serveur ne connaît pas le profil X » dès qu'un bot a été renommé ou
 * supprimé et que l'app le demande encore. L'exception remontait, l'import échouait **en entier**,
 * et le volet affichait un bandeau orange — alors que les autres profils avaient des dizaines de
 * sessions à rendre.
 */
class UnknownProfileToleranceTest {

    private val source = File("src/main/java/com/onyx/app/data/net/ServeSessionsApi.kt").readText()

    @Test
    fun `une lecture qui echoue n arrete pas les autres`() {
        assertTrue(
            "chaque profil est lu dans son propre runCatching",
            source.contains("val resultat = runCatching { allSummaries(pageSize, maxPages, profile) }"),
        )
        assertTrue("les lignes lues sont gardées", source.contains("resultat.onSuccess { lignes ->"))
        assertTrue("l'échec est mémorisé", source.contains("resultat.onFailure { erreur ->"))
    }

    @Test
    fun `l erreur ne se dit que si tout a echoue`() {
        assertTrue("un compteur de réussites", source.contains("var succes = 0"))
        assertTrue(
            "on ne lève que si aucune lecture n'a abouti",
            source.contains("if (succes == 0) premiereErreur?.let { throw it }"),
        )
        assertFalse(
            "plus de remontée immédiate qui vidait la liste",
            source.contains("getOrElse { return false }"),
        )
    }

    @Test
    fun `un profil inconnu est reconnu`() {
        assertTrue("le français du serveur", profilInconnu("Ce serveur ne connaît pas le profil `coder_mk86`."))
        assertTrue("l'anglais", profilInconnu("unknown profile: coder_mk86"))
        assertFalse("une vraie panne n'est pas un profil inconnu", profilInconnu("The server did not answer in time."))
    }
}
