package com.onyx.app.ui.files

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le fichier récupéré ne passe plus par la mémoire.**
 *
 * Le défaut d'origine : la rangée d'un binaire lisait tout le corps en `ByteArray`
 * (`ServeFilesApi.bytes`) avant de l'écrire. Un APK de 34 Mo tenait donc dans le tas de
 * l'application, et la taille du fichier décidait si « n'importe quoi » était récupérable. La
 * preuve est une lecture des sources : le chemin de récupération doit appeler
 * `ServeFileDownload.toFile` et **ne doit plus** appeler `bytes`.
 */
class FilesDownloadTest {

    private fun read(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            val fromApp = File(dir, "app/$relative")
            if (fromApp.isFile) return fromApp.readText()
            dir = dir.parentFile
        }
        error("introuvable : $relative")
    }

    @Test
    fun `le telechargement passe par le flux du serveur, pas par un ByteArray`() {
        val screen = read("src/main/java/com/onyx/app/ui/files/FilesScreen.kt")
        assertTrue(
            "l'écran appelle viewModel.download(entry, file)",
            screen.contains("viewModel.download(entry, file)"),
        )
        assertFalse(
            "l'écran ne lit plus les octets d'un binaire en mémoire",
            screen.contains("viewModel.bytes(entry)"),
        )
        assertFalse("l'ancien chemin en mémoire a disparu", screen.contains("downloadToStore("))
    }

    @Test
    fun `le modele passe par ServeFileDownload et publie son avancement`() {
        val model = read("src/main/java/com/onyx/app/ui/files/FilesViewModel.kt")
        assertTrue("écrit au fil de l'eau", model.contains("ServeFileDownload(config)"))
        assertTrue("avec la progression", model.contains("toFile(entry.path, destination)"))
        assertTrue("et un libellé pendant le transfert", model.contains("Downloading \${entry.name}"))
    }
}