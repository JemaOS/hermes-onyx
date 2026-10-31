package com.onyx.app.ui.design

import com.onyx.app.data.model.FileFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La preuve de déterminisme des vignettes.**
 *
 * Le générateur n'a ni `Random`, ni horloge, ni état : tout dérive d'un `crc32` du couple
 * `(nom, chemin)` et, pour le son, des **premiers octets** du fichier. Ces tests le vérifient au
 * niveau de la **valeur** (le [FileThumbnail.Plan]), pas d'une capture : c'est rejouable, sans
 * appareil.
 *
 * **Trois propriétés** : (1) même fichier → même plan, **champ à champ** ; (2) deux fichiers
 * différents → deux plans différents ; (3) même contenu sonore → même forme d'onde, contenus
 * différents → formes différentes.
 */
class FileThumbnailTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    // — 1 · Même fichier, même vignette —

    @Test
    fun `le meme fichier donne toujours le meme plan`() {
        val a = FileThumbnail.plan("lagan.jpg", "/home/onyx/Images/lagan.jpg", "image/jpeg")
        val b = FileThumbnail.plan("lagan.jpg", "/home/onyx/Images/lagan.jpg", "image/jpeg")
        assertEquals("même nom, même chemin → même graine", a.seed, b.seed)
        assertEquals("même plan, champ à champ", a, b)
    }

    @Test
    fun `le meme son donne toujours la meme forme d onde`() {
        val sound = bytes(1, 40, 80, 120, -12, -60, -100, 7, 90, 33)
        val a = FileThumbnail.plan("note-vocale.m4a", "/x/note-vocale.m4a", "audio/mp4", sound)
        val b = FileThumbnail.plan("note-vocale.m4a", "/x/note-vocale.m4a", "audio/mp4", sound.copyOf())
        assertEquals("la forme d'onde est la même", a.wave, b.wave)
        assertEquals("et le plan entier aussi", a, b)
    }

    @Test
    fun `une image vide et une image avec octets donnent le meme plan`() {
        // Pour une image, les octets ne changent rien au plan (la vignette EST le bitmap décodé).
        val a = FileThumbnail.plan("capture.png", "/x/capture.png", "image/png")
        val b = FileThumbnail.plan("capture.png", "/x/capture.png", "image/png", bytes(9, 9, 9))
        assertEquals(a, b)
    }

    // — 2 · Deux fichiers, deux vignettes —

    @Test
    fun `deux fichiers differents donnent deux plans differents`() {
        val a = FileThumbnail.plan("lagan.jpg", "/home/onyx/Images/lagan.jpg", "image/jpeg")
        val b = FileThumbnail.plan("capture-39.png", "/home/onyx/Images/capture-39.png", "image/png")
        assertNotEquals("deux noms → deux graines", a.seed, b.seed)
        assertNotEquals("deux vignettes", a, b)
    }

    @Test
    fun `deux documents differents ont des encres ou des motifs differents a l echelle du lot`() {
        // Sur un lot réel, les encres ne tombent pas toutes sur la même valeur : la couleur « dérive
        // du nom ». On le prouve sur un échantillon large.
        val names = listOf("rapport.pdf", "releve.csv", "notes.md", "facture.docx", "plan.pdf", "log.txt")
        val inks = names.map { FileThumbnail.inkFor(FileThumbnail.seed(it, "/x/$it")) }.toSet()
        assertTrue("au moins trois encres distinctes sur six documents", inks.size >= 3)
    }

    // — 3 · La forme d'onde vient bien des octets —

    @Test
    fun `deux contenus sonores differents donnent deux formes d onde differentes`() {
        val soft = ByteArray(256) { 4 }
        val loud = ByteArray(256) { 100 }
        val a = FileThumbnail.waveform(soft, seed = 1L)
        val b = FileThumbnail.waveform(loud, seed = 1L)
        assertEquals("la barre attendue", FileThumbnail.WaveBars, a.size)
        assertNotEquals("un son doux et un son fort ne dessinent pas la même onde", a, b)
        assertTrue("le doux est plus court que le fort", a.average() < b.average())
    }

    @Test
    fun `la forme d onde sans octets est deterministe et bornee`() {
        val a = FileThumbnail.pseudoWave(seed = 12345L, bars = FileThumbnail.WaveBars)
        val b = FileThumbnail.pseudoWave(seed = 12345L, bars = FileThumbnail.WaveBars)
        assertEquals("même graine → même onde", a, b)
        assertTrue("dans la bande lisible", a.all { it in 0.12f..0.95f })
        val c = FileThumbnail.pseudoWave(seed = 99999L, bars = FileThumbnail.WaveBars)
        assertNotEquals("deux graines → deux ondes", a, c)
    }

    @Test
    fun `la forme d onde ne depend que des premiers octets`() {
        val base = ByteArray(FileThumbnail.WaveBytes + 400) { 10 }
        // Identique dans la fenêtre, différent au-delà : même onde.
        val beyond = base.copyOf().also { for (i in FileThumbnail.WaveBytes until it.size) it[i] = 99 }
        assertEquals(
            "au-delà de la fenêtre, l'octet ne change rien",
            FileThumbnail.waveform(base, seed = 5L),
            FileThumbnail.waveform(beyond, seed = 5L),
        )
        // Différent dans la fenêtre : onde différente.
        val inside = base.copyOf().also { it[0] = 100.toByte() }
        assertNotEquals(
            "dans la fenêtre, l'octet change l'onde",
            FileThumbnail.waveform(base, seed = 5L),
            FileThumbnail.waveform(inside, seed = 5L),
        )
    }

    // — 4 · Le type écrit —

    @Test
    fun `le type ecrit d un document est son extension en capitales`() {
        assertEquals("PDF", FileThumbnail.typeLabel("rapport-2026.pdf"))
        assertEquals("DOCX", FileThumbnail.typeLabel("facture.docx"))
        assertEquals("CSV", FileThumbnail.typeLabel("releve.csv"))
        assertEquals("MD", FileThumbnail.typeLabel("notes.md"))
    }

    @Test
    fun `un document sans extension porte ce que le type dit, sinon FICHIER`() {
        assertEquals("PDF", FileThumbnail.typeLabel("sans-extension", "application/pdf"))
        assertEquals("FICHIER", FileThumbnail.typeLabel("sans-extension", ""))
    }

    // — 5 · La famille est celle du modèle —

    @Test
    fun `le plan range chaque fichier dans sa famille`() {
        assertEquals(FileFamily.DOCUMENT, FileThumbnail.plan("a.pdf", "/a.pdf").family)
        assertEquals(FileFamily.AUDIO, FileThumbnail.plan("a.mp3", "/a.mp3").family)
        assertEquals(FileFamily.ARCHIVE, FileThumbnail.plan("a.zip", "/a.zip").family)
        assertEquals(FileFamily.IMAGE, FileThumbnail.plan("a.png", "/a.png").family)
    }

    @Test
    fun `seul un son porte une forme d onde`() {
        assertTrue("un son en porte une", FileThumbnail.plan("a.mp3", "/a.mp3").wave.isNotEmpty())
        assertTrue("un document non", FileThumbnail.plan("a.pdf", "/a.pdf").wave.isEmpty())
        assertTrue("une archive non", FileThumbnail.plan("a.zip", "/a.zip").wave.isEmpty())
    }

    // — 6 · La preuve imprimée : la table des plans, lue par le rapport —

    /**
     * **La preuve de déterminisme, imprimée.** Elle porte le même fichier **deux fois** (même plan,
     * deux fois) et **trois fichiers différents** (trois plans différents), puis écrit la table que
     * le rapport cite : la graine, l'encre et les huit premières barres de l'onde. Rien n'est
     * inventé : c'est ce que le générateur rend réellement.
     */
    @Test
    fun `preuve de determinisme, imprimee`() {
        val samples = listOf(
            "lagan.jpg" to "/home/onyx/Images/lagan.jpg",
            "rapport-2026.pdf" to "/home/onyx/Documents/rapport-2026.pdf",
            "note-vocale.m4a" to "/home/onyx/Audio/note-vocale.m4a",
            "archive-notes.zip" to "/home/onyx/Archives/archive-notes.zip",
        )
        val sounds = ByteArray(FileThumbnail.WaveBytes) { i -> ((i * 7) % 200 - 100).toByte() }
        println("PREUVE-DETERMINISME-VIGNETTES")
        println("fichier | famille | graine (crc32) | encre | onde(8 premières barres)")
        samples.forEach { (name, path) ->
            val bytes = if (name.endsWith(".m4a")) sounds else null
            val a = FileThumbnail.plan(name, path, "", bytes)
            val b = FileThumbnail.plan(name, path, "", bytes?.copyOf())
            assertEquals("même fichier → même plan ($name)", a, b)
            val wave = a.wave.take(8).joinToString(",") { String.format(java.util.Locale.ROOT, "%.3f", it) }
            println("$name | ${a.family.name} | ${a.seed} | %08X | $wave".format(a.ink))
        }
        // Deux fichiers → deux plans.
        val plans = samples.map { (n, p) -> FileThumbnail.plan(n, p) }
        assertEquals("quatre fichiers → quatre graines", 4, plans.map { it.seed }.toSet().size)
    }
}
