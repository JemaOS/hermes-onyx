package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le type MIME sous lequel un fichier récupéré du serveur est confié au système.
 *
 * Le cas qui compte est l'APK : annoncé en `application/octet-stream`, Android ne propose **jamais**
 * de l'installer. C'était le défaut réel — on ne pouvait pas récupérer une version d'Onyx depuis son
 * propre serveur, alors que le fichier était là et le téléchargement terminé.
 */
class DownloadMimeTest {

    @Test
    fun `un apk s'annonce comme un paquet android`() {
        assertEquals("application/vnd.android.package-archive", downloadMimeFor("onyx-1.0.0+1135-top5.apk"))
        assertEquals("la casse ne compte pas", "application/vnd.android.package-archive", downloadMimeFor("Onyx.APK"))
    }

    @Test
    fun `les familles connues gardent leur type`() {
        assertEquals("application/pdf", downloadMimeFor("rapport.pdf"))
        assertEquals("application/zip", downloadMimeFor("lot.zip"))
        assertEquals("image/png", downloadMimeFor("capture.png"))
        assertEquals("audio/mpeg", downloadMimeFor("voix.mp3"))
        assertEquals("video/mp4", downloadMimeFor("demo.mp4"))
        assertEquals("text/plain", downloadMimeFor("notes.md"))
    }

    @Test
    fun `un type inconnu ne s'invente pas`() {
        assertEquals("application/octet-stream", downloadMimeFor("donnees.xyz"))
        assertEquals("application/octet-stream", downloadMimeFor("sans-extension"))
        assertEquals("application/octet-stream", downloadMimeFor(""))
    }
}