package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ergonomie tactile de l'écran « Ordinateur », vérifiée **sur le source**.
 *
 * Le comportement vit dans des gestes Compose qu'une JVM ne rejoue pas : ce que ces assertions
 * gardent, ce sont les **règles** qui ne doivent plus se perdre — le pointeur relatif, le clic au
 * pointeur, le clic droit à l'appui long, le défilement à deux doigts avec élan, le pincement local,
 * le curseur dessiné et la ligne d'aide, plus le plafond de cadence qui borne les messages. La
 * **logique** de tout cela est, elle, calculée et vérifiée dans `ComputerTouchTest`.
 */
class ComputerTouchWiringTest {

    private fun read(path: String) = File(path).readText()

    private val screen = read("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt")
    private val cursor = read("src/main/java/com/onyx/app/ui/computer/ComputerCursor.kt")
    private val touch = read("../core/src/main/java/com/onyx/app/data/net/ComputerTouch.kt")
    private val wire = read("../core/src/main/java/com/onyx/app/data/net/ComputerWire.kt")
    private val client = read("../core/src/main/java/com/onyx/app/data/net/ComputerBridgeClient.kt")
    private val session = read("src/main/java/com/onyx/app/ui/computer/ComputerSession.kt")
    private val french = read("../core/src/main/java/com/onyx/app/localization/OnyxFrench.kt")
    private val bridge = read("../tools/onyx-computer-bridge/bridge.py")

    @Test
    fun `le pointeur est relatif, le doigt le deplace, il ne le designe plus`() {
        assertTrue(screen.contains("ComputerPointer.advance("))
        assertTrue(screen.contains("pointer.moveTo("))
        // Plus une seule mise à l'échelle du doigt : c'était le défaut (viser le pointeur).
        assertFalse(screen.contains("ComputerScale.toScreen("))
        // Il apparaît au centre du bureau, jamais sous le doigt : aucun saut au premier geste.
        assertTrue(screen.contains("pointer.placeAtCenter("))
    }

    @Test
    fun `taper clique au pointeur, l'appui long fait le clic droit`() {
        assertTrue(screen.contains("onPoint(pointer.point, 1)"))
        assertTrue(screen.contains("onPoint(pointer.point, 3)"))
        assertTrue(screen.contains("ComputerTaps.onRelease("))
        // L'annulation en éloignant le doigt : la progression repart à zéro.
        assertTrue(screen.contains("ComputerTaps.moving("))
        assertTrue(screen.contains("pressProgress.floatValue = 0f"))
    }

    @Test
    fun `le retour visuel de l'appui long est dessine par le curseur`() {
        assertTrue(cursor.contains("pressProgress"))
        assertTrue(cursor.contains("drawArc("))
    }

    @Test
    fun `deux doigts defilent, avec elan, et le pincement reste local`() {
        assertTrue(screen.contains("ComputerWheel.notches("))
        assertTrue(screen.contains("onWheel(pointer.point"))
        assertTrue(screen.contains("ComputerFling("))
        assertTrue(screen.contains("ComputerTwoFingerPolicy.decide("))
        assertTrue(screen.contains("ComputerViewport.pinchZoomBounded("))
    }

    @Test
    fun `la cadence des messages est bornee, un geste ne noie pas le pont`() {
        assertTrue(screen.contains("ComputerPacketBudget(ComputerPacketBudget.MOTION_MS)"))
        assertTrue(screen.contains("ComputerPacketBudget(ComputerPacketBudget.WHEEL_MS)"))
        assertTrue(screen.contains("motion.takeIfDue("))
        assertTrue(screen.contains("wheel.takeIfDue("))
    }

    @Test
    fun `le curseur se dessine, avec un halo lisible et une loupe de precision`() {
        assertTrue(screen.contains("ComputerCursorOverlay("))
        assertTrue(cursor.contains("drawCircle(color = Color.White"))
        assertTrue(cursor.contains("drawLoupe("))
        assertTrue(cursor.contains("precise"))
    }

    @Test
    fun `la visee se regle, un cran de precision et un cran rapide, en clair`() {
        // Le même bouton que la barre, avec un fond accentué quand « précision » est actif.
        assertTrue(screen.contains("highlight = pointer.gain == ComputerGain.PRECISION"))
        assertTrue(screen.contains("pointer.nextGain()"))
        assertTrue(screen.contains("label = pointer.gain.labelKey"))
        assertTrue(touch.contains("PRECISION(0.35f)"))
        assertTrue(touch.contains("FAST(2.2f)"))
    }

    @Test
    fun `la barre explique les gestes en une ligne, en francais`() {
        val key = "Drag to move · tap to click · long-press for right-click · two fingers to scroll"
        assertTrue("la ligne d'aide doit être dans la barre", screen.contains(key))
        assertTrue("et elle doit défiler au lieu d'être coupée",
            screen.contains(".horizontalScroll(rememberScrollState())"))
        assertTrue(
            "la ligne d'aide doit être traduite en français",
            french.contains(
                "Glisser pour déplacer · taper pour cliquer · appui long pour clic droit · " +
                    "deux doigts pour défiler",
            ),
        )
    }

    @Test
    fun `le pointeur sans clic est un message additif, avale par un pont plus ancien`() {
        assertTrue(wire.contains("fun motion("))
        assertTrue(client.contains("fun motion(x: Int, y: Int)"))
        // Une commodité : son refus ne s'affiche pas (le curseur dessiné bouge de toute façon).
        assertTrue(client.contains("ComputerWire.motion(id, x, y)"))
        assertTrue(session.contains("fun move(point: ComputerPoint) = client.motion(point.x, point.y)"))
        assertTrue("le pont accepte le déplacement additif", bridge.contains("if kind == \"motion\":"))
    }

    @Test
    fun `aucun mot de l'autre plateforme n'entre dans les fichiers neufs de cet ecran`() {
        // La consigne est absolue : rien de la plateforme d'origine, nulle part. Ces deux fichiers
        // sont neufs — ils n'ont aucune raison d'en porter un seul caractère. Le motif lui-même est
        // assemblé morceau par morceau : ainsi ce fichier ne porte pas davantage les mots interdits.
        val banned = listOf(
            "app" + "le",
            "i" + "os",
            "i" + "phone",
            "i" + "pad",
            "mac" + "os",
            "sir" + "i",
        ).joinToString("|")
        val forbidden = Regex(banned, RegexOption.IGNORE_CASE)
        assertFalse("ComputerTouch.kt", forbidden.containsMatchIn(touch))
        assertFalse("ComputerCursor.kt", forbidden.containsMatchIn(cursor))
        // Et la ligne d'aide, dans les deux langues, n'en porte aucun non plus.
        assertFalse(
            "la ligne d'aide",
            forbidden.containsMatchIn(
                "Drag to move · tap to click · long-press for right-click · two fingers to scroll",
            ),
        )
        assertFalse(
            "la ligne d'aide en français",
            forbidden.containsMatchIn(
                "Glisser pour déplacer · taper pour cliquer · appui long pour clic droit · " +
                    "deux doigts pour défiler",
            ),
        )
    }
}
