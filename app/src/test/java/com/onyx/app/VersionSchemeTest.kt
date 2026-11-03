package com.onyx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le numéro de version : **une seule source de vérité**, et les trois APK d'une même publication.
 *
 * Le `versionCode` est l'entier que compare Android pour accepter une installation par-dessus
 * l'autre ; la version affichée (« 1.3.1 ») en est **dérivée**, à partir du code qui porte le
 * premier 1.3.1. Une seule valeur à changer à chaque publication, donc impossible d'en oublier
 * une — et impossible qu'un des trois APK porte un numéro différent des deux autres.
 */
class VersionSchemeTest {

    private fun properties(): Map<String, String> {
        val file = File("../gradle.properties")
        assertTrue("gradle.properties est introuvable depuis ${File(".").absolutePath}", file.exists())
        return file.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) null
                else trimmed.substringBefore('=') to trimmed.substringAfter('=')
            }
            .toMap()
    }

    @Test
    fun `la version affichee se derive du numero`() {
        val props = properties()
        val code = props["onyx.versionCode"]?.toIntOrNull()
        val base = props["onyx.versionBase"]?.toIntOrNull()
        assertTrue("onyx.versionCode est requis", code != null)
        assertTrue("onyx.versionBase est requis", base != null)

        // Le correctif : 1.3.1 pour le code de base, puis 1.3.2, 1.3.3…
        val patch = (code!! - base!!)
        assertTrue("le correctif doit commencer à 1 (code $code, base $base)", patch >= 1)
        assertEquals("1.3.$patch", versionNameOf(code, base))
    }

    @Test
    fun `les trois modules derivent la meme version`() {
        // Le même numéro pour le téléphone, Auto et Wear OS : un APK qui dérive se voit au contrôle
        // de signature, à l'installation — et sur la page de la release.
        val expression = "onyx.versionBase"
        val modules = listOf("build.gradle.kts", "../auto/build.gradle.kts", "../wear/build.gradle.kts")
        modules.forEach { path ->
            val file = File(path)
            assertTrue("$path est introuvable", file.exists())
            val source = file.readText()
            assertTrue("$path ne derive pas la version", source.contains(expression))
            assertTrue("$path ne lit pas le numero", source.contains("onyx.versionCode"))
            assertTrue(
                "$path garde l'ancienne forme « 1.0.0+ »",
                !source.contains("\"1.0.0+\" + versionCode"),
            )
        }
    }

    /** La même règle que les scripts de build : « 1.3. » + le correctif. */
    private fun versionNameOf(code: Int, base: Int): String = "1.3." + (code - base)
}
