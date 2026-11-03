package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La **mise à jour de l'app** : ce que GitHub dit, et ce que l'app en retient.
 *
 * **Une version, un nom.** Le tag se lit `v1.3.3` et le paquet `onyx-1.3.3-release.apk` : le
 * numéro interne n'apparaît plus dans ce qui est publié. La comparaison se fait donc sur la
 * version **lisible**, dérivée du numéro interne — les deux ne peuvent pas diverger.
 *
 * Deux pièges, tous deux réels :
 *
 * - Les trois paquets d'une release portent des noms voisins — `onyx-1.3.3-release.apk`,
 *   `onyx-auto-1.3.3-release.apk`, `onyx-wear-1.3.3-release.apk`. Proposer l'APK d'Auto à qui
 *   n'a pas de montre serait absurde, et proposer le Wear à un téléphone est une erreur.
 * - Les releases **anciennes** portent encore leur numéro (`v1.3.1+1133`,
 *   `onyx-1133-release.apk`) : elles doivent rester lisibles, et la comparaison y est exacte.
 */
class AppUpdateTest {

    private fun release(
        tag: String = "v1.3.3",
        assets: String = """
            [
              {"name": "onyx-1.3.3-release.apk", "browser_download_url": "https://ex/onyx-1.3.3-release.apk"},
              {"name": "onyx-auto-1.3.3-release.apk", "browser_download_url": "https://ex/onyx-auto-1.3.3-release.apk"},
              {"name": "onyx-wear-1.3.3-release.apk", "browser_download_url": "https://ex/onyx-wear-1.3.3-release.apk"},
              {"name": "SHA256SUMS-1.3.3.txt", "browser_download_url": "https://ex/SHA256SUMS-1.3.3.txt"}
            ]
        """.trimIndent(),
    ): String = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/JemaOS/hermes-onyx/releases/tag/v1.3.3",
          "assets": $assets
        }
    """.trimIndent()

    @Test
    fun `la release publiee est lue`() {
        val update = parseLatestRelease(release())
        assertEquals("1.3.3", update?.versionName)
        assertNull("un tag sans numéro ne porte pas de numéro", update?.versionCode)
        assertEquals("https://ex/onyx-1.3.3-release.apk", update?.downloadUrl)
        assertTrue("la page de la release est gardée", update?.releaseUrl?.contains("releases/tag") == true)
    }

    @Test
    fun `c est l APK du telephone et pas celui d auto ou de wear`() {
        val update = parseLatestRelease(release())
        assertEquals(
            "le paquet Auto ne doit jamais être proposé comme mise à jour du téléphone",
            "https://ex/onyx-1.3.3-release.apk",
            update?.downloadUrl,
        )
    }

    @Test
    fun `une release sans APK de telephone ne propose rien`() {
        val assets = """[{"name": "onyx-wear-1.3.4-release.apk", "browser_download_url": "https://ex/wear.apk"}]"""
        assertNull(parseLatestRelease(release(tag = "v1.3.4", assets = assets)))
    }

    @Test
    fun `une release ancienne, avec son numero, reste lisible`() {
        // Les releases déjà publiées portent le numéro après le `+` : elles doivent rester
        // lisibles, et la comparaison y est exacte.
        val assets = """[{"name": "onyx-1133-release.apk", "browser_download_url": "https://ex/onyx-1133-release.apk"}]"""
        val update = parseLatestRelease(release(tag = "v1.3.1+1133", assets = assets))

        assertEquals("1.3.1", update?.versionName)
        assertEquals(1133, update?.versionCode)
        assertEquals("https://ex/onyx-1133-release.apk", update?.downloadUrl)
    }

    @Test
    fun `un tag illisible ne propose rien`() {
        assertNull(parseLatestRelease(release(tag = "nightly")))
        assertNull(parseLatestRelease(release(tag = "v1.3")))
    }

    @Test
    fun `une reponse illisible ne propose rien`() {
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("pas du json"))
        assertNull(parseLatestRelease("{}"))
    }

    @Test
    fun `la comparaison se fait sur le numero quand la release le porte`() {
        val old = AppUpdate("1.3.1", 1133, "https://ex/a.apk", "https://ex/r")
        assertTrue("1133 est plus récent que 1131", isUpdateAvailable(1131, "1.3.1", old))
        assertFalse("la même version ne se propose pas", isUpdateAvailable(1133, "1.3.1", old))
        assertFalse("une version plus ancienne ne se propose pas", isUpdateAvailable(1134, "1.3.2", old))
    }

    @Test
    fun `la comparaison se fait sur la version lisible quand la release ne porte pas de numero`() {
        val latest = AppUpdate("1.3.3", null, "https://ex/a.apk", "https://ex/r")
        assertTrue("1.3.3 est plus récent que 1.3.2", isUpdateAvailable(1134, "1.3.2", latest))
        assertFalse("la même version ne se propose pas", isUpdateAvailable(1135, "1.3.3", latest))
        assertFalse("une version plus ancienne ne se propose pas", isUpdateAvailable(1136, "1.3.4", latest))
    }
}
