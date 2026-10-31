package com.onyx.app.ui.design

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/**
 * **Harnais de rendu — hors verrou.** Rend la carte de conversation (379 × 192, le cadre de la
 * maquette) depuis [Fluide.sample], la copie Kotlin du programme AGSL, et l'écrit en PNG pour que
 * les mesures se fassent sur l'image et non sur des estimations.
 *
 * Le dossier de sortie vient de la variable d'environnement `FLUIDE_HARNAIS` : sans elle, le test
 * ne fait rien — il n'écrit jamais dans l'arbre sans qu'on le lui demande.
 */
class FluideHarnaisTest {

    private val W = 379
    private val H = 192

    @Test
    fun `rend la carte du cycle pour la mesure`() {
        val dir = System.getenv("FLUIDE_HARNAIS") ?: return
        val dossier = File(dir)
        dossier.mkdirs()
        for (dark in listOf(true, false)) {
            val app = if (dark) "sombre" else "clair"
            val plan = Fluide.plan("Réunion de chantier", "conv-1", dark = dark, choix = ChoixCouverture.DEFAUT)
            for (k in 0..4) {
                val phase = k / 4f
                rend(plan, phase, File(dossier, "harnais-$app-t$k.png"))
            }
            println("harnais : cartes écrites dans ${dossier.path} ($app)")
        }
    }

    /** **Le courant** : les images d'un cycle complet, pour la vidéo (48 images, pas de 1/48). */
    @Test
    fun `rend le cycle complet du courant pour la video`() {
        val dir = System.getenv("FLUIDE_HARNAIS") ?: return
        val n = System.getenv("FLUIDE_VIDEO_FRAMES")?.toIntOrNull() ?: 48
        for (dark in listOf(true, false)) {
            val app = if (dark) "sombre" else "clair"
            val cadre = File(dir, "frames-$app").apply { mkdirs() }
            val plan = Fluide.plan("Réunion de chantier", "conv-1", dark = dark, choix = ChoixCouverture.DEFAUT)
            for (k in 0 until n) {
                rend(plan, k.toFloat() / n, File(cadre, "f%03d.png".format(k)))
            }
            println("harnais : $n images du courant écrites dans ${cadre.path} ($app)")
        }
    }

    private fun rend(plan: Fluide.Plan, phase: Float, sortie: File) {
        val bmp = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val c = Fluide.sample(
                    plan, (x + 0.5f) / W, (y + 0.5f) / H,
                    Fluide.souffleDuCycle(phase), phase = phase,
                )
                val r = (c[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val g = (c[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val b = (c[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                bmp.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        ImageIO.write(bmp, "png", sortie)
    }
}
