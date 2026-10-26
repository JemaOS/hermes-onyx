package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'écran Notifications garde la structure: les états système, les canaux (le pendant
 * Android de la liste des appairages), les trois choix et l'action de test — sans recopier le
 * relais , qui n'a pas d'équivalent ici.
 */
class PushSettingsTest {
    private val source = File("src/main/java/com/onyx/app/ui/settings/PushSettingsScreen.kt").readText()

    @Test
    fun `les trois sections  sont presentes`() {
        listOf("Android", "Channels", "Onyx").forEach { header ->
            assertTrue(header, source.contains("header = \"$header\""))
        }
    }

    @Test
    fun `la page garde le fond, l'en-tete et les insets`() {
        assertTrue(source.contains(".background(theme.formPageColor)"))
        assertTrue(source.contains("OnyxPageHeader(title = \"Notifications\""))
        assertTrue(source.contains("statusBarsPadding()"))
    }

    @Test
    fun `les trois choix et l'action de test sont presents`() {
        assertTrue(source.contains("\"Notify me in the background\""))
        assertTrue(source.contains("\"Notification sound\""))
        assertTrue(source.contains("\"Ongoing answer notification\""))
        assertTrue(source.contains("\"Send a test notification\""))
    }

    @Test
    fun `la liste des canaux suit  et se regle dans Android`() {
        assertTrue(source.contains("context.onyxChannels()"))
        assertTrue(source.contains("context.openChannelSettings(channel.id)"))
        assertTrue(source.contains("\"No channels yet.\""))
    }

    @Test
    fun `les textes du relais  sont adaptes, pas recopies`() {
        assertTrue(source.contains("Nothing passes through a relay"))
        assertFalse(source.contains("UIPasteboard"))
        assertFalse(source.contains("hermes plugins install"))
    }
}
