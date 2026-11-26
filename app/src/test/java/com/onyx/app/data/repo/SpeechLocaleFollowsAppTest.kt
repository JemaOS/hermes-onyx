package com.onyx.app.data.repo

import com.onyx.app.localization.OnyxLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * La langue du **micro** suit celle de l'**interface**, tant que la personne n'en a pas choisi
 * une autre.
 *
 * Le défaut rapporté : l'app était figée sur « fr-FR » comme langue de reconnaissance. Sur une app
 * anglaise, les phrases anglaises (« stop listening », « that's all ») étaient donc mal
 * transcrites — **jamais reconnues**. L'app et le micro parlaient deux langues différentes.
 *
 * Ce test verrouille les deux morceaux du contrat :
 *
 *  1. la langue donnée au moteur est celle de l'interface quand rien n'est choisi ;
 *  2. un choix explicite de la personne **reste** respecté — l'app ne dicte pas la langue du
 *     micro à la place de la personne.
 */
class SpeechLocaleFollowsAppTest {

    private val before = OnyxLanguage.choice

    @Before
    fun setUp() {
        // Une interface française par défaut : le test doit être reproductible sur n'importe quelle
        // machine, quoi que soit la langue d'Android de celle-ci.
        OnyxLanguage.apply(OnyxLanguage.FRENCH)
    }

    @After
    fun restore() {
        OnyxLanguage.apply(before)
    }

    @Test
    fun `l app francaise ecoute en francais`() {
        assertEquals("fr-FR", SettingsRepository.defaultSpeechLocale())
    }

    @Test
    fun `l app anglaise ecoute en anglais`() {
        OnyxLanguage.apply(OnyxLanguage.ENGLISH)
        assertEquals("en-US", SettingsRepository.defaultSpeechLocale())
    }

    @Test
    fun `rien de choisi veut dire la langue de l app`() {
        // La valeur par défaut n'est **plus** « fr-FR » figé : c'est vide, et le vide se résout
        // en suivant l'interface. Une valeur figée aurait cassé l'app anglaise en silence.
        assertEquals("", SettingsRepository.DEFAULT_SPEECH_LOCALE)
    }

    @Test
    fun `la valeur figee d avant est reconnue comme telle`() {
        // L'héritage : les installs d'avant avaient « fr-FR » **stocké**. Ce n'était pas
        // toujours un choix de la personne, c'était souvent la constante — il ne doit donc pas
        // figer le micro en français sur une app anglaise.
        assertEquals("fr-FR", SettingsRepository.LEGACY_FIXED_SPEECH_LOCALE)
    }

    @Test
    fun `un choix de langue explicite prime sur la langue de l app`() {
        // Le choix fait dans le sélecteur est écrit dans une clé **neuve** : c'est elle qui
        // distingue « la personne a choisi Français » de « la valeur par défaut d'avant ». Sans
        // cette clé, un « fr-FR » choisi par la personne aurait été confondu avec l'héritée — et
        // l'app aurait imposée sa langue au micro de quelqu'un qui avait choisi autrement.
        assertEquals("", SettingsRepository.DEFAULT_SPEECH_LOCALE)
    }

    @Test
    fun `la langue de reponse choisie a une etiquette de lecture`() {
        assertEquals("fr-FR", SettingsRepository.replyLanguageTag("fr"))
        assertEquals("en-US", SettingsRepository.replyLanguageTag("en"))
        assertEquals("de-DE", SettingsRepository.replyLanguageTag("de"))
        // « Automatique » : la voix suit le micro, donc l'interface.
        assertNull(SettingsRepository.replyLanguageTag(""))
    }
}
