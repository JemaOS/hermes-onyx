package com.onyx.app.ui.design

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Un seul jeu de valeurs par défaut, et le source le porte — sur TOUS les écrans.**
 *
 * Le défaut que le propriétaire a nommé n'est pas un écran raté : c'est que **chaque écran a été
 * habillé par un lot différent**, d'où des rayons, des en-têtes et des densités qui ne se
 * ressemblent pas. La cause n'est pas l'ignorance, c'est l'absence de **verrou** : la famille de
 * rayons existe ([OnyxSpace.radii]) mais rien n'empêchait un écran d'en inventer un sixième.
 *
 * Trois verrous, sur **tous** les écrans trouvés dans `app/…/ui/` :
 *  1. **aucun rayon en dur** — tout `RoundedCornerShape(<nombre>.dp)` est refusé ; un rayon vient
 *     d'un jeton de [OnyxSpace.radii] (ou de la capsule `50`, qui est une forme, pas un nombre) ;
 *  2. **une en-tête décidée** — chaque écran appelle `OnyxPageHeader` (le gabarit unique : retour +
 *     titre + action à droite) ou figure dans [HeaderException] avec sa raison ;
 *  3. **un fond décidé** — chaque écran pose la matière (`onyxPage`) ou figure dans
 *     [FondException] avec sa raison (les écrans de formulaire natif, les lecteurs plein écran).
 *
 * Le tableau d'audit est **imprimé** : `écran → rayons cités → en-tête → fond → conforme`. Un écran
 * absent du tableau serait un écran non audité, donc un écran qui divergera — c'est pour ça que le
 * test vérifie aussi le **nombre** d'écrans parcourus.
 */
class ScreenDefaultsTest {

    /**
     * Le gabarit d'en-tête unique, et sa raison d'être : retour + titre + action à droite.
     *
     * Deux formes d'une **même** famille : la barre de page ([OnyxPageHeader]) et sa variante à grand
     * titre ([OnyxLargePageHeader]) — la seconde n'est pas un gabarit rival, c'est la première avec
     * le titre détaché sous la barre (l'anatomie des Réglages, revue par le lot des Réglages).
     */
    private val headerTokens = listOf(
        "OnyxPageHeader(",
        "OnyxLargePageHeader(",
        "ContentPageHeader(",
        // Vague 2 : l'en-tête **à grand titre du socle** (30 sp, −0,032 em, ExtraBold) — la même
        // famille que les trois autres, avec le titre du langage « J ».
        "SocleLargeHeader(",
    )

    /** Les écrans qui portent leur **propre** barre, avec la raison — la liste est courte à dessein. */
    private val headerException: Map<String, String> = mapOf(
        "ui/chat/ChatScreen.kt" to "Le fil porte la barre flottante du haut (OnyxTopBar), pas une barre de page.",
        "ui/bots/GroupChatScreen.kt" to "Salle de groupe : barre propre (participants + direct), pas de retour de page.",
        "ui/bots/GroupRoomScreen.kt" to "Salle de groupe : barre propre (RoomHeader).",
        "ui/voice/VoiceScreen.kt" to "Scène vocale : en-tête de scène (VoiceHeader), plein écran.",
        "ui/lock/LockScreen.kt" to "Écran de verrouillage : aucun chrome de navigation, par définition.",
        "ui/chat/components/StartScreen.kt" to "État vide **dans** le fil : il n'a pas de barre, celle du fil est au-dessus.",
        "ui/common/WhatsNewScreen.kt" to "Page de nouveautés (onboarding) : un grand titre dans le contenu, pas de retour.",
        "ui/chat/CameraScreen.kt" to "Viseur appareil photo plein écran.",
        "ui/chat/PhotoViewerScreen.kt" to "Visionneuse d'image plein écran.",
        "ui/chat/VideoPlayerScreen.kt" to "Lecteur vidéo plein écran.",
        "ui/chat/AudioPlayerScreen.kt" to "Lecteur audio plein écran.",
        "ui/chat/PdfViewerScreen.kt" to "Visionneuse PDF plein écran.",
        "ui/chat/ComposerEditorScreen.kt" to "Éditeur du compositeur : barre à trois positions (Annuler / titre / Enregistrer), hors gabarit de page.",
        "ui/calls/IncomingCallScreen.kt" to "Appel entrant : surface de scène plein écran (sonnerie + répondre), pas une page.",
    )

    /** Les écrans dont le fond n'est pas une page, avec la raison — une seule règle par famille. */
    private val fondException: Map<String, String> = mapOf(
        "ui/chat/CameraScreen.kt" to "Viseur : fond du capteur, plein écran, pas une page.",
        "ui/chat/PhotoViewerScreen.kt" to "Visionneuse d'image : fond de scène, plein écran.",
        "ui/chat/VideoPlayerScreen.kt" to "Lecteur vidéo : fond de scène, plein écran.",
        "ui/chat/components/StartScreen.kt" to "État vide **dans** le fil : il n'a pas de page, celle du fil est derrière.",
        "ui/computer/ComputerScreen.kt" to "Espace de travail : la barre de commandes et le flux ont chacun leur fond, l'écran n'a pas de page.",
        "ui/settings/LogsScreen.kt" to "Journal : un défilement de texte brut, sur le fond système (épinglé par le verrou de parité des formulaires).",
        "ui/calls/IncomingCallScreen.kt" to "Appel entrant : surface de scène plein écran, le fond du fil derrière, pas une page.",
    )

    /**
     * Les fonds de page de l'app : la matière du socle (`onyxPage`) des écrans de verre, sa variante
     * **travaillée** (`matierePage` — le fond de page, la matière en quatre couches, puis le halo)
     * pour les deux écrans où la matière porte la lecture, et le fond de formulaire natif
     * (`formPageColor`) des listes groupées. Un écran qui n'a ni l'un ni l'autre (lecteurs, scènes)
     * le dit dans [fondException].
     */
    private val fondTokens = listOf(
        ".onyxPage(theme)",
        ".matierePage(theme)",
        ".background(theme.formPageColor)",
    )

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText() ?: ""

    /** Le dossier des écrans, quel que soit le répertoire de travail du test (module ou racine). */
    private fun uiDir(): File =
        listOf(File("app/src/main/java/com/onyx/app/ui"), File("../app/src/main/java/com/onyx/app/ui"))
            .first { it.isDirectory }

    /** Tous les fichiers d'écran de l'app, en chemins `ui/…` — la base de l'audit. */
    private fun screenFiles(): List<String> {
        val root = uiDir()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> Regex("fun\\s+\\w*Screen\\w*\\s*\\(").containsMatchIn(f.readText()) }
            .map { "ui/" + it.relativeTo(root).path }
            .sorted()
            .toList()
    }

    /** Le source d'un écran, depuis son chemin `ui/…`. */
    private fun readScreen(uiRel: String): String = read("app/src/main/java/com/onyx/app/$uiRel")

    private val literalRadius = Regex("""RoundedCornerShape\(\s*\d+(?:\.\d+)?\.dp""")

    @Test
    fun `aucun ecran ne definit un rayon hors du jeu partage`() {
        val offenders = mutableListOf<String>()
        screenFiles().forEach { path ->
            val source = readScreen(path)
            literalRadius.findAll(source).forEach { m ->
                offenders.add("$path : ${m.value}")
            }
        }
        assertTrue(
            "des rayons en dur subsistent (ils doivent venir d'OnyxSpace.radii) :\n" +
                offenders.take(20).joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `le jeu de rayons est declare, borne, et tient en une famille plus deux exceptions`() {
        // La famille (quatre marches + le coin de vignette) et **deux** exceptions nommées, pas plus.
        val names = OnyxSpace.radii.map { it.first }
        assertEquals("la famille des rayons a cinq marches", 5, names.take(5).size)
        assertTrue(OnyxSpace.radii.contains("ThumbnailCorner" to OnyxSpace.ThumbnailCorner))
        // Les exceptions sont nommées et documentées — et il n'y en a que deux.
        assertEquals("FormCorner" to OnyxSpace.FormCorner, OnyxSpace.radii.last { it.first == "FormCorner" })
        assertEquals("ControlCorner" to OnyxSpace.ControlCorner, OnyxSpace.radii.last { it.first == "ControlCorner" })
        assertEquals("la famille tient en 7 rayons nommés", 7, OnyxSpace.radii.size)
        // Aucun rayon autorisé n'est un doublon, et la capsule (50) est admise.
        assertEquals(OnyxSpace.radii.map { it.second }.distinct().size, OnyxSpace.radii.size)
        assertTrue("la capsule reste admise", OnyxSpace.allowedRadii.contains(50.dp))
        assertEquals(
            "les rayons autorisés = les 7 rayons nommés + la capsule",
            8,
            OnyxSpace.allowedRadii.size,
        )
    }

    @Test
    fun `tableau d audit de tous les ecrans et porte par le source`() {
        val files = screenFiles()
        val report = StringBuilder(
            "\nAUDIT D'UNIFORMITÉ — écran → rayons → en-tête → fond → conforme\n" +
                "%-58s %-34s %-10s %-8s %s\n".format("écran", "rayons cités (jetons)", "en-tête", "fond", "conforme"),
        )
        var conform = 0
        val nonConform = mutableListOf<String>()
        files.forEach { path ->
            val source = readScreen(path)
            val radii = Regex("""RoundedCornerShape\((OnyxSpace\.\w+|50)\)""")
                .findAll(source).map { it.groupValues[1] }.distinct().sorted().toList()
            val hasHeader = headerTokens.any { source.contains(it) }
            val headerOk = hasHeader || headerException.containsKey(path)
            val hasFond = fondTokens.any { source.contains(it) }
            val fondOk = hasFond || fondException.containsKey(path)
            val noLiteral = !literalRadius.containsMatchIn(source)
            val ok = headerOk && fondOk && noLiteral
            if (ok) conform++ else nonConform.add(path)
            report.append(
                "%-58s %-34s %-10s %-8s %s\n".format(
                    path,
                    if (radii.isEmpty()) "—" else radii.joinToString(",").replace("OnyxSpace.", ""),
                    if (hasHeader) "OnyxPageHeader" else if (headerOk) "propre" else "AUCUNE",
                    if (source.contains(".onyxPage(theme)")) "matière"
                    else if (source.contains(".matierePage(theme)")) "matière+"
                    else if (source.contains(".background(theme.formPageColor)")) "formulaire"
                    else if (fondOk) "propre" else "AUCUN",
                    if (ok) "oui" else "NON",
                ),
            )
        }
        report.append("écrans audités : ${files.size} — conformes : $conform\n")
        report.append("exceptions d'en-tête déclarées : ${headerException.size}, de fond : ${fondException.size}\n")
        print(report)
        assertTrue("l'audit doit couvrir l'app (>= 55 écrans), il en a ${files.size}", files.size >= 55)
        assertTrue(
            "des écrans divergent :\n" + nonConform.joinToString("\n"),
            nonConform.isEmpty(),
        )
        // Un écran non audité est un écran qui divergera : chaque écran est dans le tableau.
        assertEquals(files.size, conform + nonConform.size)
    }

    @Test
    fun `toute exception d en-tete cite un fichier qui existe`() {
        // Une exception qui cite un fichier disparu est une exception qui ne protège plus rien.
        (headerException.keys + fondException.keys).distinct().forEach { path ->
            assertTrue("l'exception cite un fichier introuvable : $path", readScreen(path).isNotEmpty())
        }
    }
}
