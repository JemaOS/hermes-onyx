package com.onyx.app.data.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'onglet **Fichiers** : la famille d'un fichier, ses filtres, ses quatre ordres de tri, et la
 * destination d'un appui.
 *
 * Tout se vérifie ici sans appareil, parce que ce sont des fonctions pures : c'est le contrat que
 * l'écran dessine, et le seul endroit où « aucun appui ne reste sans effet » peut être prouvé
 * famille par famille.
 */
class ConversationFilesTest {

    private fun file(name: String, mime: String = "", at: Long = 0L, size: Long? = null) =
        ConversationFile(name = name, path = "/tmp/$name", mime = mime, appearedAtMillis = at, sizeBytes = size)

    // — La famille, par le type MIME **et** par l'extension —

    @Test
    fun `une image se reconnait a son nom, meme sans type annonce`() {
        // Le fichier du défaut signalé : un simple nom, aucun type.
        assertEquals(FileFamily.IMAGE, FileFamily.of("IMG-20261026-WA0001.jpg"))
        assertEquals(FileFamily.IMAGE, FileFamily.of("capture.JPEG"))
        assertEquals(FileFamily.IMAGE, FileFamily.of("icone.svg"))
        // Et par le type, quand le nom ne dit rien.
        assertEquals(FileFamily.IMAGE, FileFamily.of("photo", "image/jpeg"))
        assertEquals(FileFamily.IMAGE, FileFamily.of("sans-extension", "image/webp"))
    }

    @Test
    fun `une video, un son, un document et une archive se reconnaissent des deux facons`() {
        assertEquals(FileFamily.VIDEO, FileFamily.of("clip.mp4"))
        assertEquals(FileFamily.VIDEO, FileFamily.of("enregistrement", "video/quicktime"))
        assertEquals(FileFamily.AUDIO, FileFamily.of("note.m4a"))
        assertEquals(FileFamily.AUDIO, FileFamily.of("memo", "audio/mpeg"))
        assertEquals(FileFamily.DOCUMENT, FileFamily.of("contrat.pdf"))
        assertEquals(FileFamily.DOCUMENT, FileFamily.of("sans-nom", "application/pdf"))
        assertEquals(FileFamily.DOCUMENT, FileFamily.of("notes.txt"))
        assertEquals(FileFamily.DOCUMENT, FileFamily.of("feuille", "text/csv"))
        assertEquals(FileFamily.ARCHIVE, FileFamily.of("lot.zip"))
        assertEquals(FileFamily.ARCHIVE, FileFamily.of("paquet", "application/zip"))
        assertEquals(FileFamily.ARCHIVE, FileFamily.of("sauvegarde.tar.gz"))
    }

    @Test
    fun `ce qui n est ni l un ni l autre tombe dans Autre, et jamais dans un filtre`() {
        assertEquals(FileFamily.OTHER, FileFamily.of("data.bin", "application/octet-stream"))
        assertEquals(FileFamily.OTHER, FileFamily.of("sans-nom"))
        assertFalse("Autre n'a pas de puce", FileFamily.OTHER.isFilter)
        assertTrue("Tout accepte Autre", FileFamily.ALL.accepts(FileFamily.OTHER))
        assertFalse("Images n'accepte pas Autre", FileFamily.IMAGE.accepts(FileFamily.OTHER))
    }

    // — Les filtres et leurs compteurs —

    @Test
    fun `chaque filtre compte ce qu il montre, et Tout les compte tous`() {
        val files = listOf(
            file("a.jpg"),
            file("b.png", "image/png"),
            file("film.mp4"),
            file("son.mp3"),
            file("contrat.pdf"),
            file("notes.txt"),
            file("lot.zip"),
            file("truc.bin"),
        )
        val counts = fileFamilyCounts(files)
        assertEquals(8, counts[FileFamily.ALL])
        assertEquals(2, counts[FileFamily.IMAGE])
        assertEquals(1, counts[FileFamily.VIDEO])
        assertEquals(1, counts[FileFamily.AUDIO])
        assertEquals(2, counts[FileFamily.DOCUMENT])
        assertEquals(1, counts[FileFamily.ARCHIVE])
        // « Autre » est visible dans « Tout » seulement : aucun compteur ne le reprend.
        assertEquals(1, files.count { it.family == FileFamily.OTHER })
        // Les cinq familles et « Autre » font exactement « Tout » : rien n'est perdu en route.
        val familySum = listOf(
            FileFamily.IMAGE, FileFamily.VIDEO, FileFamily.AUDIO,
            FileFamily.DOCUMENT, FileFamily.ARCHIVE, FileFamily.OTHER,
        ).sumOf { counts[it] ?: 0 }
        assertEquals(counts[FileFamily.ALL], familySum)
    }

    @Test
    fun `le filtre ne retient que sa famille`() {
        val files = listOf(file("a.jpg"), file("film.mp4"), file("lot.zip"))
        assertEquals(listOf("a.jpg"), visibleConversationFiles(files, FileFamily.IMAGE, FileSort.NAME).map { it.name })
        assertEquals(emptyList<String>(), visibleConversationFiles(files, FileFamily.AUDIO, FileSort.NAME).map { it.name })
        assertEquals(3, visibleConversationFiles(files, FileFamily.ALL, FileSort.NAME).size)
    }

    // — Les quatre tris —

    @Test
    fun `la date trie dans les deux sens`() {
        val files = listOf(file("vieille.jpg", at = 100L), file("neuve.jpg", at = 300L), file("milieu.jpg", at = 200L))
        assertEquals(
            listOf("neuve.jpg", "milieu.jpg", "vieille.jpg"),
            visibleConversationFiles(files, FileFamily.ALL, FileSort.RECENT).map { it.name },
        )
        assertEquals(
            listOf("vieille.jpg", "milieu.jpg", "neuve.jpg"),
            visibleConversationFiles(files, FileFamily.ALL, FileSort.OLDEST).map { it.name },
        )
    }

    @Test
    fun `le nom trie sans tenir compte de la casse`() {
        val files = listOf(file("Zebre.txt"), file("abricot.txt"), file("Banane.txt"))
        assertEquals(
            listOf("abricot.txt", "Banane.txt", "Zebre.txt"),
            visibleConversationFiles(files, FileFamily.ALL, FileSort.NAME).map { it.name },
        )
    }

    @Test
    fun `la taille met les plus lourds en tete, et les poids inconnus en dernier`() {
        val files = listOf(
            file("petit.jpg", size = 1_000L),
            file("inconnu.jpg", size = null),
            file("gros.jpg", size = 9_000_000L),
            file("moyen.jpg", size = 40_000L),
        )
        assertEquals(
            listOf("gros.jpg", "moyen.jpg", "petit.jpg", "inconnu.jpg"),
            visibleConversationFiles(files, FileFamily.ALL, FileSort.SIZE).map { it.name },
        )
    }

    @Test
    fun `le type groupe les familles dans l ordre des filtres`() {
        val files = listOf(file("lot.zip"), file("a.jpg"), file("film.mp4"), file("b.jpg"))
        val sorted = visibleConversationFiles(files, FileFamily.ALL, FileSort.TYPE)
        assertEquals(listOf("a.jpg", "b.jpg", "film.mp4", "lot.zip"), sorted.map { it.name })
        assertEquals(
            listOf(FileFamily.IMAGE, FileFamily.IMAGE, FileFamily.VIDEO, FileFamily.ARCHIVE),
            sorted.map { it.family },
        )
    }

    // — Aucun appui sans effet —

    @Test
    fun `chaque famille a une destination, jamais rien`() {
        assertEquals(FileOpenRoute.PHOTO, fileOpenRoute("a.jpg"))
        assertEquals(FileOpenRoute.PHOTO, fileOpenRoute("photo", "image/jpeg"))
        assertEquals(FileOpenRoute.VIDEO, fileOpenRoute("film.mp4"))
        assertEquals(FileOpenRoute.AUDIO, fileOpenRoute("son.mp3"))
        assertEquals(FileOpenRoute.AUDIO, fileOpenRoute("memo", "audio/ogg"))
        assertEquals(FileOpenRoute.PDF, fileOpenRoute("contrat.pdf"))
        assertEquals(FileOpenRoute.TEXT, fileOpenRoute("notes.txt"))
        assertEquals(FileOpenRoute.TEXT, fileOpenRoute("releve", "text/csv"))
        assertEquals(FileOpenRoute.ARCHIVE, fileOpenRoute("lot.zip"))
        // Un nom sans extension n'est pas un texte : on ne devine pas, on passe la main au système.
        assertEquals(FileOpenRoute.EXTERNAL, fileOpenRoute("readme"))
        // Un document bureautique n'a pas de lecteur ici : il part au système, ce qui est une
        // destination — et l'onglet le dit (« Ouvrir avec »).
        assertEquals(FileOpenRoute.EXTERNAL, fileOpenRoute("rapport.docx"))
        assertEquals(FileOpenRoute.EXTERNAL, fileOpenRoute("data.bin", "application/octet-stream"))
        // Aucun échantillon ne rend « rien » : c'est la garantie du défaut signalé.
        listOf(
            "a.jpg", "a.mp4", "a.mp3", "a.pdf", "a.txt", "a.zip", "a.7z", "a.docx", "a.bin", "sans-nom",
        ).forEach { name ->
            assertNotNull("destination pour $name", fileOpenRoute(name))
        }
    }

    @Test
    fun `seule une archive d alignement de fichiers se liste sur place`() {
        assertTrue(FileFamily.isListableArchive("lot.zip"))
        assertTrue(FileFamily.isListableArchive("paquet", "application/zip"))
        assertFalse("un tar n'est pas lisible ici", FileFamily.isListableArchive("sauvegarde.tar.gz"))
        assertFalse(FileFamily.isListableArchive("documents.7z"))
        assertFalse(FileFamily.isListableArchive("lettre.pdf"))
    }

    // — Ce que la ligne montre —

    @Test
    fun `le sous-titre porte le poids et la date, et jamais un separateur orphelin`() {
        assertEquals("12,4 Mo · 26/10/2026", fileDetailLine("12,4 Mo", date(2026, 10, 26), Locale.FRANCE))
        assertEquals("26/10/2026", fileDetailLine("", date(2026, 10, 26), Locale.FRANCE))
        assertEquals("12,4 Mo", fileDetailLine("12,4 Mo", 0L, Locale.FRANCE))
        assertEquals("", fileDetailLine("", 0L, Locale.FRANCE))
        // La date se forme avec sa langue : la forme courte reste courte, et c'est la locale
        // passée en argument qui décide — jamais celle de la machine.
        assertTrue(
            "date française courte",
            formatFileDate(date(2026, 10, 26), Locale.FRANCE).matches(Regex("""\d{2}/\d{2}/\d{4}""")),
        )
        assertTrue(
            "date anglaise courte",
            formatFileDate(date(2026, 10, 26), Locale.US).matches(Regex("""\d{1,2}/\d{1,2}/\d{2,4}""")),
        )
        assertTrue(
            "jour avant mois en français",
            formatFileDate(date(2026, 10, 26), Locale.FRANCE).startsWith("26/10/"),
        )
        assertTrue(
            "mois avant jour en anglais",
            formatFileDate(date(2026, 10, 26), Locale.US).startsWith("10/26/"),
        )
    }

    @Test
    fun `une date absente ne s ecrit pas`() {
        assertEquals("", formatFileDate(0L, Locale.FRANCE))
        assertEquals("", formatFileDate(-1L, Locale.FRANCE))
    }

    /** Un instant à midi, pour que le fuseau du poste ne change pas le jour. */
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
}
