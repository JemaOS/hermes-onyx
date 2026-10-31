package com.onyx.app.ui.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verre répond au doigt — mais il ne s'éclaire plus.**
 *
 * Le lot précédent avait rendu la vitre vivante au doigt, et il l'avait fait avec de la **lumière** :
 * une tache spéculaire au point de contact et un contour illuminé. Le propriétaire a tranché — « la
 * lumière au toucher, supprime ça de partout, c'est moche » — donc la lumière a été **retirée
 * partout**. Ce que ce test tient, ligne par ligne, c'est ce **qui reste** :
 *
 *  1. **l'appui** — `GlassPressState` dit seulement si le doigt est posé (le point de contact a
 *     disparu avec la lumière : il n'existait que pour y poser la tache) ;
 *  2. **le seuil** — [glassPressStrength] est la **seule** décision : un réglage éteint, un Reduce
 *     Motion, un budget dépassé ou un défilement, et c'est zéro. Quatre conditions, un seul chiffre,
 *     donc aucune ne peut être oubliée à un endroit et appliquée à un autre ;
 *  3. **l'appui non lumineux** — l'enfoncement de 1,5 % (petites surfaces) et l'assombrissement
 *     (grandes) : deux signes qu'on **sent**, jamais une lumière ;
 *  4. **la parallaxe** — proportionnelle au déplacement, bornée, nulle à l'arrêt ;
 *  5. **le budget** — branché sur la mesure qui existe déjà ([GlassFluidity]) : sous une charge
 *     lourde, [GlassFluidity.pressAllowed] tombe à faux, et remonte au calme ;
 *  6. **le câblage** — la clé du réglage existe dans le dépôt et l'application pose
 *     [LocalGlassHalo], sinon l'interrupteur « Halo » des Réglages ne ferait rien.
 *
 * Et l'**honnêteté** : [GlassRefractionAvailable] est **vrai** depuis Haze 2.0.1, et le test le fige
 * — `haze-glass` déplace réellement le contenu échantillonné. Mais un drapeau ne dit pas *où* : la
 * capacité de la **bibliothèque** et celle de la **plateforme** sont deux choses, et c'est
 * [glassTier] qui les sépare. Le test éprouve les trois paliers, un par un, sans appareil.
 *
 * Le retour de la lumière, lui, est gardé par `GlassTouchLumiereVerrouTest`.
 */
class GlassTouchTest {

    // — 1. L'appui : le doigt est posé, ou il ne l'est pas —

    @Test
    fun `l appui se pose au contact et se retire au lacher`() {
        val press = GlassPressState()
        assertFalse("avant tout contact, aucun appui", press.pressed)

        press.press()
        assertTrue("le contact marque l'appui", press.pressed)

        press.release()
        assertFalse("le lâcher retire l'appui", press.pressed)
    }

    // — 2. Le seuil : quatre conditions, un seul chiffre —

    @Test
    fun `la force de l appui est nulle des qu une des conditions le coupe`() {
        // Au contact, plein doigt : la force suit l'entrée.
        assertEquals(1f, glassPressStrength(true, false, true, 1f), 0f)
        assertEquals(0.4f, glassPressStrength(true, false, true, 0.4f), 0f)

        // Le réglage « Halo » éteint : la surface est inerte au doigt.
        assertEquals(0f, glassPressStrength(enabled = false, reduceMotion = false, fluidityAllows = true, pressed = 1f), 0f)
        // Reduce Motion : le verre reste, l'appui se fige.
        assertEquals(0f, glassPressStrength(enabled = true, reduceMotion = true, fluidityAllows = true, pressed = 1f), 0f)
        // Budget dépassé : l'appui tombe en premier, pas le flou.
        assertEquals(0f, glassPressStrength(enabled = true, reduceMotion = false, fluidityAllows = false, pressed = 1f), 0f)

        // Et l'entrée est bornée : une force hors [0,1] ne fabrique pas un appui plus fort.
        assertEquals(1f, glassPressStrength(true, false, true, 2f), 0f)
        assertEquals(0f, glassPressStrength(true, false, true, -1f), 0f)
    }

    @Test
    fun `au repos la force est nulle, meme avec toutes les conditions ouvertes`() {
        assertEquals(0f, glassPressStrength(true, false, true, 0f), 0f)
    }

    // — 3. L'appui non lumineux : 1,5 % d'enfoncement, 6 % d'ombre, jamais un éclat —

    @Test
    fun `l appui enfonce la vitre de un et demi pour cent`() {
        assertTrue("l'appui enfonce", GlassPressScale < 1f)
        assertTrue("assez pour se sentir", GlassPressScale <= 0.990f)
        assertTrue("pas assez pour sauter", GlassPressScale >= 0.980f)
        assertEquals("l'appui est de 1,5 %", 0.985f, GlassPressScale, 0f)
    }

    @Test
    fun `les grandes surfaces s assombrissent au lieu de s eclairer`() {
        assertTrue("l'appui des grandes surfaces assombrit", GlassPressShade > 0f)
        assertTrue("mais très légèrement : une ombre, pas un voile", GlassPressShade < 0.10f)
        assertEquals("l'ombre est de 6 %", 0.06f, GlassPressShade, 0f)
    }

    // — 4. La parallaxe : proportionnelle, bornée, nulle à l'arrêt —

    @Test
    fun `la parallaxe est proportionnelle, bornee et nulle a l arret`() {
        assertEquals("au repos, aucun décalage", 0f, glassParallaxDp(travelPx = 0f, density = 2f), 0.0001f)
        // 20 px à densité 2 = 10 dp de déplacement : 12 % en sens inverse.
        assertEquals(-1.2f, glassParallaxDp(travelPx = 20f, density = 2f), 0.0001f)
        // Le sens inverse donne le décalage inverse.
        assertEquals(1.2f, glassParallaxDp(travelPx = -20f, density = 2f), 0.0001f)
        // Elle ne dépasse jamais la hauteur déclarée (6 dp), quelle que soit la course.
        assertEquals(GlassParallaxDp.value, -glassParallaxDp(travelPx = 800f, density = 2f), 0.0001f)
        assertEquals(GlassParallaxDp.value, glassParallaxDp(travelPx = -800f, density = 2f), 0.0001f)
        assertEquals(6f, GlassParallaxDp.value, 0f)
        // Une densité absurde ne fabrique pas un décalage infini.
        assertEquals(0f, glassParallaxDp(travelPx = 100f, density = 0f), 0f)
        assertEquals(0f, glassParallaxDp(travelPx = 100f, density = -1f), 0f)
    }

    // — 5. Le budget : l'appui tombe quand la cadence tombe —

    @Test
    fun `le budget coupe l appui sous une charge lourde et le rend au calme`() {
        val glass = GlassFluidity(GlassLevel.FULL)
        glass.acquire()
        var frame = 1_000_000L
        glass.sample(frame) // amorce la mesure

        // Deux fenêtres à 30 ms (charge ~1,8× la période) : le budget coupe l'appui.
        repeat(GlassFluidity.WindowFrames * 2) {
            frame += 30_000_000L
            glass.sample(frame)
        }
        assertFalse(
            "sous une charge lourde, l'appui doit tomber : c'est la pièce la plus dispensable",
            glass.pressAllowed,
        )

        // Le retour au calme : la fenêtre glissante est vidée de ses trames chargées, l'appui revient.
        repeat(GlassFluidity.WindowFrames * 40) {
            frame += 6_000_000L
            glass.sample(frame)
        }
        assertTrue("au calme, l'appui doit revenir", glass.pressAllowed)

        // Plus aucune vitre à l'écran : tout repart à zéro, l'appui compris.
        glass.release()
        assertEquals("plus de vitre, plus de mesure", 0, glass.demand)
        assertTrue("et l'appui repart ouvert", glass.pressAllowed)
    }

    // — 6. Le câblage du réglage : la clé, sa lecture, et sa pose —

    @Test
    fun `le reglage Halo existe dans le depot et l application le pose`() {
        val repo = read("app/src/main/java/com/onyx/app/data/repo/SettingsRepository.kt")
        assertTrue(
            "SettingsRepository doit déclarer la clé du halo",
            repo.contains("booleanPreferencesKey(\"glassHalo\")"),
        )
        assertTrue("il doit l'exposer en flux", repo.contains("val glassHalo: StateFlow<Boolean>"))
        assertTrue("il doit l'écrire", repo.contains("suspend fun setGlassHalo"))
        assertTrue("il doit la relire au chargement", repo.contains("Keys.GLASS_HALO"))

        val app = read("app/src/main/java/com/onyx/app/ui/OnyxApp.kt")
        assertTrue(
            "l'application doit fournir `LocalGlassHalo` depuis le réglage, sinon l'interrupteur ne fait rien",
            app.contains("LocalGlassHalo provides"),
        )
        assertTrue(
            "et elle doit lire le flux du dépôt",
            app.contains("settingsRepo.glassHalo"),
        )

        val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "la vitre doit lire `LocalGlassHalo` pour s'éteindre",
            glass.contains("LocalGlassHalo.current"),
        )
    }

    // — L'honnêteté : ce que la bibliothèque permet, et ce que la plateforme permet —

    @Test
    fun `la refraction est disponible dans la bibliotheque`() {
        assertTrue(
            "haze 2.0.1 déplace le contenu échantillonné : la réfraction est là",
            GlassRefractionAvailable,
        )
        assertTrue(
            "l'effet d'arête par flou progressif reste, comme repli des plateformes sans shader",
            GlassEdgeLens,
        )
        assertTrue("le bord floute plus que le centre", GlassEdgeLensStrength > 1f)
        assertTrue(
            "la force de réfraction reste dans la plage admise par la bibliothèque (0..1)",
            GlassRefractionStrength in 0f..1f,
        )
        assertTrue(
            "la bande de réfraction a une largeur réelle",
            GlassRefractionEdgeDp > 0f,
        )
    }

    @Test
    fun `les trois paliers separent la bibliotheque de la plateforme`() {
        // Le palier complet : la plateforme sait déplacer le contenu (processeur d'ombrage à
        // l'exécution) **et** le flou fonctionne.
        assertEquals(GlassTier.REFRACTION, glassTier(sdkInt = 33, blurEnabledByDefault = true))
        assertEquals(GlassTier.REFRACTION, glassTier(sdkInt = 36, blurEnabledByDefault = true))

        // Le flou seul : la plateforme accepte le flou, mais pas le déplacement.
        assertEquals(GlassTier.BLUR, glassTier(sdkInt = 31, blurEnabledByDefault = true))
        assertEquals(GlassTier.BLUR, glassTier(sdkInt = 32, blurEnabledByDefault = true))

        // Le voile : la plateforme refuse le flou. Aucun plantage, jamais un trou.
        assertEquals(GlassTier.VEIL, glassTier(sdkInt = 30, blurEnabledByDefault = false))
        assertEquals(GlassTier.VEIL, glassTier(sdkInt = 26, blurEnabledByDefault = false))

        // Et le verdict de la plateforme **prime** : une version récente qui refuse le flou ne
        // reçoit pas le verre complet non plus — on ne peint pas une vitre qui ne se rafraîchit pas.
        assertEquals(GlassTier.VEIL, glassTier(sdkInt = 34, blurEnabledByDefault = false))
    }

    @Test
    fun `le plafond du verre suit le meme seuil que la plateforme`() {
        assertEquals(
            "sous le seuil du flou, le verre démarre en repli",
            GlassLevel.SCRIM,
            glassCeiling(sdkInt = 30, lowRam = false),
        )
        assertEquals(GlassLevel.LITE, glassCeiling(sdkInt = 31, lowRam = true))
        assertEquals(GlassLevel.FULL, glassCeiling(sdkInt = 31, lowRam = false))
        assertEquals(GlassLevel.FULL, glassCeiling(sdkInt = 36, lowRam = false))
    }

    @Test
    fun `la vitre sait si une source est posee`() {
        val presence = GlassSourcePresence()
        assertEquals("au repos, aucune source", 0, presence.count)
        presence.enter()
        assertEquals(1, presence.count)
        presence.enter()
        assertEquals(2, presence.count)
        presence.leave()
        assertEquals(1, presence.count)
        presence.leave()
        presence.leave()
        assertEquals("le compte ne descend jamais sous zéro", 0, presence.count)
    }

    @Test
    fun `l appui ne peint aucune lumiere de contact`() {
        val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "les grandes surfaces posent une ombre, sous l'encre",
            glass.contains("Color.Black.copy(alpha = pressShade)"),
        )
        assertTrue(
            "et la teinte de la vitre n'est plus éclaircie par l'appui",
            !glass.contains("nominalTintAlpha +"),
        )
        val touch = read("core/src/main/java/com/onyx/app/ui/design/GlassTouch.kt")
        assertTrue("le suivi ne consomme jamais l'événement", touch.contains("requireUnconsumed = false"))
        assertTrue("il lit au passage Initial, sans rien prendre", touch.contains("PointerEventPass.Initial"))
        assertTrue("l'appui est bien le seul suivi du doigt", touch.contains("fun Modifier.glassPress("))
    }

    @Test
    fun `la vitre passe par les trois chemins de Haze 2`() {
        val glass = read("core/src/main/java/com/onyx/app/ui/design/LiquidGlass.kt")
        assertTrue(
            "le palier complet doit passer par le verre de Haze 2 (réfraction)",
            glass.contains("hazeGlass("),
        )
        assertTrue(
            "le palier intermédiaire doit passer par le flou seul",
            glass.contains("hazeBlur("),
        )
        assertTrue(
            "les deux lisent la source de l'écran par la même entrée",
            glass.contains("HazeInput.Sources(state)"),
        )
        assertTrue(
            "la vitre doit lire le compte des sources, pas une liste refermée par la bibliothèque",
            glass.contains("LocalGlassSourcePresence.current?.count"),
        )
        assertTrue(
            "et le liseré d'arête doit rester peint par nous, sous l'encre",
            glass.contains("drawGlassFinish("),
        )
    }

    private fun read(path: String): String =
        listOf(File(path), File("../$path")).firstOrNull { it.isFile }?.readText()
            ?: error("fichier introuvable : $path")
}
