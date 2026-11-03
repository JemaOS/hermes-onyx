package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La **mise à jour de l'app** : ce que GitHub dit, et ce que l'app en retient.
 *
 * Deux pièges, tous deux réels :
 *
 * - Les trois paquets d'une release portent des noms voisins — `onyx-1129-release.apk`,
 *   `onyx-auto-1129-release.apk`, `onyx-wear-1129-release.apk`. Proposer l'APK d'Auto à qui
 *   n'a pas de montre serait absurde, et proposer le Wear à un téléphone est une erreur.
 * - L'ordre des versions est le **numéro** (`versionCode`), jamais le nom : deux versions
 *   peuvent porter le même `1.0.0` avec des numéros différents, et c'est le numéro que
 *   l'Android compare pour accepter une installation par-dessus l'autre.
 */
class AppUpdateTest {

    private fun release(
        tag: String = "v1.0.0+1129",
        assets: String = """
            [
              {"name": "onyx-1129-release.apk", "browser_download_url": "https://ex/onyx-1129-release.apk"},
              {"name": "onyx-auto-1129-release.apk", "browser_download_url": "https://ex/onyx-auto-1129-release.apk"},
              {"name": "onyx-wear-1129-release.apk", "browser_download_url": "https://ex/onyx-wear-1129-release.apk"},
              {"name": "SHA256SUMS-1129.txt", "browser_download_url": "https://ex/SHA256SUMS-1129.txt"}
            ]
        """.trimIndent(),
    ): String = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/JemaOS/hermes-onyx/releases/tag/v1.0.0%2B1129",
          "assets": $assets
        }
    """.trimIndent()

    @Test
    fun `la release publiee est lue`() {
        val update = parseLatestRelease(release())
        assertEquals(1129, update?.versionCode)
        assertEquals("1.0.0", update?.versionName)
        assertEquals("https://ex/onyx-1129-release.apk", update?.downloadUrl)
        assertTrue("la page de la release est gardée", update?.releaseUrl?.contains("releases/tag") == true)
    }

    @Test
    fun `c est l APK du telephone et pas celui d auto ou de wear`() {
        val update = parseLatestRelease(release())
        assertEquals(
            "le paquet Auto ne doit jamais être proposé comme mise à jour du téléphone",
            "https://ex/onyx-1129-release.apk",
            update?.downloadUrl,
        )
    }

    @Test
    fun `une release sans APK de telephone ne propose rien`() {
        val assets = """[{"name": "onyx-wear-1130-release.apk", "browser_download_url": "https://ex/wear.apk"}]"""
        assertNull(parseLatestRelease(release(tag = "v1.0.0+1130", assets = assets)))
    }

    @Test
    fun `la version affichee ne porte pas le numero`() {
        // Le tag est `v1.3.1+1133` : le **numero** décide de l'ordre (c'est lui qu'Android
        // compare pour accepter l'installation par-dessus l'autre), le nom lisible est ce que la
        // carte affiche — « 1.3.1 », pas « 1.3.1+1133 ».
        val update = parseLatestRelease(release(tag = "v1.3.1+1133"))

        assertEquals(1133, update?.versionCode)
        assertEquals("1.3.1", update?.versionName)
    }

    @Test
    fun `un tag sans numero ne propose rien`() {
        // Sans numéro, l'app ne peut pas dire si la version est plus récente.
        assertNull(parseLatestRelease(release(tag = "v1.0.0")))
        assertNull(parseLatestRelease(release(tag = "nightly")))
    }

    @Test
    fun `une reponse illisible ne propose rien`() {
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("pas du json"))
        assertNull(parseLatestRelease("{}"))
    }

    @Test
    fun `la comparaison se fait sur le numero`() {
        assertTrue("1129 est plus récent que 1128", isUpdateAvailable(installedCode = 1128, latestCode = 1129))
        assertFalse("la même version ne se propose pas", isUpdateAvailable(installedCode = 1129, latestCode = 1129))
        assertFalse("une version plus ancienne ne se propose pas", isUpdateAvailable(installedCode = 1130, latestCode = 1129))
    }
}
