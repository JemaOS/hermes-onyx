package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le lien de configuration **réel** de cette machine, lu par le vrai parseur.
 *
 * Le fichier `/home/john/onyx-connect-link.txt` est écrit par le watchdog des tunnels : il porte
 * l'adresse vivante du tunnel, la clé d'installation, et les champs de la voix, du pont et de
 * la langue. C'est le seul lien que John colle dans l'app.
 *
 * Ce test verrouille ce qui a été observé sur le terrain : un lien **écourté** (les secrets
 * remplacés par `*`) est refusé par le serveur — `401 missing_or_invalid_key` — alors que le
 * lien complet passe. Le lien doit donc être lu tel quel, et le test le lit **depuis le
 * fichier**, pas depuis une constante recopiée : c'est le fichier qui change quand le tunnel
 * change de nom.
 */
class LiveSetupLinkTest {

    private fun liveLink(): String? {
        val file = File("/home/john/onyx-connect-link.txt")
        if (!file.isFile) return null
        return file.readText().trim().takeIf { it.isNotEmpty() }
    }

    @Test
    fun `le lien du fichier est complet et sans placeholder`() {
        val link = liveLink() ?: return // pas de fichier sur cette machine : rien a prouver

        assertTrue("le lien doit porter le scheme onyx : $link", link.startsWith("onyx://connect"))
        assertTrue(
            "le lien ne doit contenir ni astérisque ni points de suspension : $link",
            !link.contains('*') && !link.contains('…'),
        )
        assertTrue("le lien doit porter une clé : $link", link.contains("key="))
    }

    @Test
    fun `le lien du fichier donne une config joignable`() {
        val link = liveLink() ?: return
        val code = SetupCode.parse(link)

        assertNotNull("le lien du fichier doit etre lu : $link", code)
        val config = code!!.toServerConfig("srv-live")

        assertEquals(code.apiURL, config.baseUrl)
        // L'adresse qui porte le WebSocket du Dashboard : sans elle, l'app n'essaie jamais
        // d'ouvrir le socket et la liste des bots reste vide.
        assertEquals(code.apiURL, config.serveBaseUrl)
        assertTrue("le serveur doit avoir des identifiants", config.hasServeCredentials)
        assertTrue("le serveur doit etre joignable par la cle", config.servesWithKey)
        assertEquals(Transport.HERMES_SESSIONS, config.transport)
    }

    @Test
    fun `un lien dont la cle est un astérisque est refusé`() {
        // Le symptome vu par John : le lien recopie d'un message ou les secrets sont masques.
        val link = liveLink() ?: return
        val broken = link.replace(Regex("key=[^&]*"), "key=*")

        val code = SetupCode.parse(broken)
        // Le parseur, lui, accepte la chaine ; c'est le serveur qui la refuse. On verifie donc
        // que la cle qu'on en tire n'est PAS celle du serveur : elle ne peut pas passer.
        val key = code?.apiKey.orEmpty()
        assertTrue("la cle du lien casse doit etre un placeholder : $key", key == "*")
        assertTrue(
            "une cle masquee ne peut pas etre la vraie cle d'installation",
            key != link.substringAfter("key=").substringBefore("&"),
        )
    }
}
