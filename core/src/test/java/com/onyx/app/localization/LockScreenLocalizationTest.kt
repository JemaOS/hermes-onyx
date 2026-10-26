package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'écran de verrouillage en français.
 *
 * Le bouton et la ligne « Exiger … » s'écrivent à partir du nom que le téléphone donne à sa
 * biométrie: « Unlock with Face or fingerprint » restait en anglais parce que seule la partie fixe
 * était connue du catalogue.
 */
class LockScreenLocalizationTest {
    private val fr = Locale.FRANCE
    private val en = Locale.ENGLISH

    @Test
    fun `l ecran de verrouillage parle francais`() {
        linkedMapOf(
            "Onyx is locked" to "Onyx est verrouillé",
            "Unlock to see your conversations" to "Déverrouillez pour voir vos conversations",
            "Unlock" to "Déverrouiller",
            "Passcode" to "Code",
            "Use the passcode" to "Utiliser le code",
            "That passcode isn't right." to "Ce code est incorrect.",
            "Fingerprint" to "Empreinte digitale",
            "Face unlock" to "Déverrouillage par le visage",
            "Face or fingerprint" to "Visage ou empreinte digitale",
        ).forEach { (key, expected) ->
            assertEquals("Français de « $key »", expected, OnyxLocalization.translate(key, fr))
            assertEquals("Anglais de « $key »", key, OnyxLocalization.translate(key, en))
        }
    }

    @Test
    fun `le bouton et la ligne des reglages nomment la biometrie en francais`() {
        linkedMapOf(
            "Unlock with Fingerprint" to "Déverrouiller avec l’empreinte",
            "Unlock with Face unlock" to "Déverrouiller avec le visage",
            "Unlock with Face or fingerprint" to "Déverrouiller avec le visage ou l’empreinte",
            "Require Fingerprint" to "Exiger l’empreinte",
            "Require Face unlock" to "Exiger le visage",
            "Require Face or fingerprint" to "Exiger le visage ou l’empreinte",
            "Require a passcode" to "Exiger un code",
        ).forEach { (key, expected) ->
            assertEquals("Français de « $key »", expected, OnyxLocalization.translate(key, fr))
            assertEquals("Anglais de « $key »", key, OnyxLocalization.translate(key, en))
        }
    }

    @Test
    fun `les erreurs de biometrie se disent a l ecran en francais`() {
        linkedMapOf(
            "That didn't match. Try again, or use the passcode." to
                "Ça ne correspond pas. Réessayez, ou utilisez le code.",
            "Unlock didn't work. Try again, or use the passcode." to
                "Le déverrouillage n’a pas fonctionné. Réessayez, ou utilisez le code.",
            "Biometric unlock isn't available on this screen. Use the passcode." to
                "Le déverrouillage biométrique n’est pas disponible sur cet écran. Utilisez le code.",
            // « Utiliser le code » de la feuille: on guide, on n'annonce pas un échec.
            "Enter your passcode." to "Saisissez votre code.",
        ).forEach { (key, expected) ->
            assertEquals("Français de « $key »", expected, OnyxLocalization.translate(key, fr))
            assertEquals("Anglais de « $key »", key, OnyxLocalization.translate(key, en))
        }
    }

    @Test
    fun `une biometrie absente est expliquee en francais`() {
        linkedMapOf(
            "Face or fingerprint isn't set up on this phone. Use the passcode." to
                "Le visage ou l’empreinte n’est pas configuré sur ce téléphone. Utilisez le code.",
            "Face or fingerprint isn't set up on this phone. Add one in Settings › Lock." to
                "Le visage ou l’empreinte n’est pas configuré sur ce téléphone. Ajoutez-le dans Réglages › Verrouillage.",
        ).forEach { (key, expected) ->
            assertEquals("Français de « $key »", expected, OnyxLocalization.translate(key, fr))
            assertEquals("Anglais de « $key »", key, OnyxLocalization.translate(key, en))
        }
    }
}
