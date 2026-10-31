package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le geste « Scanner un code de configuration » : la caméra réelle, le respect de `rowStride`
 * (sans quoi aucun code ne décode jamais, alors que l'aperçu s'affiche), le retour visible quand
 * rien n'est lu, et le bouton « Coller le lien » dans la feuille.
 */
class SetupScannerParityTest {
    private val scanner = File("src/main/java/com/onyx/app/ui/setup/SetupCodeScanner.kt").readText()
    private val decoder = File("src/main/java/com/onyx/app/ui/setup/QrCodeDecoder.kt").readText()

    @Test
    fun `la bande Y de la camera est recomposee en respectant rowStride`() {
        // Le piège : lire `width` octets par ligne sans sauter le rembourrage incline l'image.
        assertTrue(decoder.contains("fun packLuminance("))
        assertTrue(decoder.contains("val position = row * rowStride"))
        assertTrue(scanner.contains("QrCodeDecoder.packLuminance(plane.buffer, width, height, plane.rowStride)"))
    }

    @Test
    fun `la feuille montre une zone de visee et dit quand elle ne voit rien`() {
        assertTrue(scanner.contains("ScannerViewfinder"))
        assertTrue(scanner.contains("slowToRead"))
        assertTrue(scanner.contains("delay(6_000)"))
        assertTrue(scanner.contains("\"I can't see a code yet — bring it closer, and avoid the glare.\""))
    }

    /**
     * Le scanner doit dire **ce qu'il ne parvient pas à faire**, et les trois échecs ne se
     * ressemblent pas : rien dans le champ, un code dans le champ mais illisible, ou la caméra
     * refusée. « Approchez-le, évitez le reflet » était une devinette pour les trois.
     */
    @Test
    fun `les trois echecs du scan se distinguent au lieu de deviner`() {
        // 1. aucune permission / pas de caméra : déjà dit.
        assertTrue(scanner.contains("\"No camera access\""))
        assertTrue(scanner.contains("\"Camera unavailable\""))
        // 2. un code repéré mais illisible : c'est un cas à part, et il vient de ZXing lui-même.
        assertTrue(decoder.contains("ChecksumException"))
        assertTrue(decoder.contains("FormatException"))
        assertTrue(decoder.contains("ScanOutcome.Unreadable"))
        assertTrue(decoder.contains("ScanOutcome.Nothing"))
        assertTrue(scanner.contains("seenButUnreadable"))
        assertTrue(scanner.contains("is ScanOutcome.Unreadable"))
        assertTrue(
            scanner.contains(
                "\"A code is in view, but I can't read it — move closer, and avoid the glare.\"",
            ),
        )
        // 3. rien dans le champ : le message d'origine reste, mais il n'est plus le seul.
        assertTrue(scanner.contains("slowToRead -> \"I can't see a code yet"))
    }

    /** Au bout de quelques secondes — ou dès qu'un code est repéré sans être lu — la voie de
     *  secours est **nommée**, pas seulement dessinée à côté. */
    @Test
    fun `la voie de secours est proposee quand le scan ne donne rien`() {
        assertTrue(scanner.contains("\"Paste the link instead.\""))
        assertTrue(scanner.contains("wrongCode || seenButUnreadable || slowToRead"))
    }

    @Test
    fun `la feuille offre de coller le lien a cote du scan`() {
        assertTrue(scanner.contains("pasteLabel"))
        assertTrue(scanner.contains("\"Paste the link\""))
        assertTrue(scanner.contains("fun pasteFromClipboard()"))
        assertTrue(scanner.contains("clipboard.getClipEntry()"))
    }

    @Test
    fun `la permission et les refus se disent en francais par le dictionnaire`() {
        // Les littéraux restent les clés anglaises : le dictionnaire les traduit (BasicText).
        assertTrue(scanner.contains("\"Point the camera at a Onyx setup code.\""))
        assertTrue(scanner.contains("\"No camera access\""))
        assertTrue(scanner.contains("\"Camera unavailable\""))
        assertTrue(scanner.contains("\"Open Settings\""))
        assertTrue(scanner.contains("ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(scanner.contains("Manifest.permission.CAMERA"))
    }
}
