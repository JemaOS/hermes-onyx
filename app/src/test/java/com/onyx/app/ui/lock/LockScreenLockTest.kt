package com.onyx.app.ui.lock

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les quatre retours d'usage sur l'écran de verrouillage : un bouton de biométrie qui ne fait rien,
 * un cadenas plein, une phrase restée en anglais, et un champ de code caché par le clavier.
 */
class LockScreenLockTest {
    private val screen = File("src/main/java/com/onyx/app/ui/lock/LockScreen.kt").readText()
    private val settings = File("src/main/java/com/onyx/app/ui/settings/LockSettingsScreen.kt").readText()
    private val lock = File("src/main/java/com/onyx/app/security/AppLock.kt").readText()
    private val lockDrawable = File("../core/src/main/res/drawable/ic_sym_lock.xml")

    @Test
    fun `le cadenas de l ecran de verrouillage est un contour`() {
        assertTrue(screen.contains("OnyxIcon(Symbols.LockOutline, tint = theme.secondaryText, size = 30.dp)"))
        assertFalse("le cadenas plein ne doit plus servir ici", screen.contains("Symbols.Lock,"))
        assertTrue(
            File("../core/src/main/java/com/onyx/app/ui/design/Symbols.kt")
                .readText()
                .contains("val LockOutline = R.drawable.ic_sym_lock"),
        )
        assertTrue(lockDrawable.isFile)
        val xml = lockDrawable.readText()
        assertTrue("le contour est un trait", xml.contains("android:strokeColor=\"#FFFFFFFF\""))
        assertFalse("le contour ne remplit rien", xml.contains("android:fillColor=\"#FFFFFFFF\""))
    }

    @Test
    fun `le bouton de biometrie n est propose que si le telephone peut repondre`() {
        assertTrue(screen.contains("val biometry = remember(context, lock) { lock.biometryLabel(context) }"))
        assertTrue(screen.contains("if (biometry != null)"))
        assertTrue(lock.contains("fun canAuthenticate(context: Context): Boolean"))
        assertTrue(lock.contains("canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS"))
        assertTrue(lock.contains("PackageManager.FEATURE_FACE"))
        assertTrue(lock.contains("PackageManager.FEATURE_FINGERPRINT"))
        assertFalse("pas de vocabulaire le téléphone de référence ici", lock.contains("Touch ID"))
    }

    @Test
    fun `la demande accepte les classes fortes et faibles et ne se confirme pas`() {
        assertTrue(screen.contains("BiometricManager.Authenticators.BIOMETRIC_STRONG or"))
        assertTrue(screen.contains("BiometricManager.Authenticators.BIOMETRIC_WEAK,"))
        assertTrue(screen.contains(".setConfirmationRequired(false)"))
        assertFalse(
            "l'identifiant de l'appareil interdirait le bouton négatif",
            screen.contains("DEVICE_CREDENTIAL"),
        )
        assertTrue(screen.contains(".setNegativeButtonText(OnyxLocalization[\"Use the passcode\"])"))
    }

    @Test
    fun `aucune erreur ni aucun bouton muet`() {
        assertFalse("pas de retour silencieux", screen.contains("?: return"))
        assertTrue("le cas non-FragmentActivity est journalisé", screen.contains("Log.w(TAG,"))
        assertTrue(screen.contains("onAuthenticationFailed"))
        assertTrue(screen.contains("onAuthenticationError"))
        assertTrue("un prompt qui refuse de démarrer se voit", screen.contains("runCatching { prompt.authenticate(info) }"))
        assertTrue("les messages passent par la couche de traduction", screen.contains("error = OnyxLocalization["))
        assertTrue("l'erreur se lit au corps de texte", screen.contains("theme.subheadline.copy(color = theme.error"))
        // « Utiliser le code » n'est pas une panne : la feuille se ferme, on guide vers le champ.
        assertTrue(screen.contains("BiometricPrompt.ERROR_NEGATIVE_BUTTON -> OnyxLocalization[\"Enter your passcode.\"]"))
        assertTrue(screen.contains("BiometricPrompt.ERROR_USER_CANCELED -> null"))
    }

    @Test
    fun `le champ de code reste au dessus du clavier`() {
        assertTrue(screen.contains("BoxWithConstraints("))
        assertTrue(screen.contains("verticalScroll(rememberScrollState())"))
        assertTrue(screen.contains("heightIn(min = viewport)"))
        assertTrue(screen.contains("verticalArrangement = Arrangement.Center"))
        assertTrue(screen.contains("placeholder = \"Passcode\""))
    }

    @Test
    fun `les phrases des deux ecrans passent par la couche de traduction`() {
        // Le tag du journal et ses deux messages ne s'affichent jamais à l'écran.
        val journalOnly = setOf(
            "OnyxLock",
            "BiometricPrompt needs a FragmentActivity host; the passcode stays the way in.",
            "BiometricPrompt wouldn't start",
        )
        val literals = listOf(screen, settings)
            .flatMap { source -> Regex("\"([^\"\\n]*)\"").findAll(source).map { it.groupValues[1] }.toList() }
            // Les gabarits (« Unlock with $biometry ») et les fragments de gabarit ne sont pas des
            // phrases ; leurs formes composées sont vérifiées par LockScreenLocalizationTest.
            .filter { it.isNotBlank() && it.first().isLetter() && !it.contains('$') }
            .distinct()
        val untranslated = literals.filter { literal ->
            literal !in journalOnly && OnyxLocalization.translate(literal, Locale.FRANCE) == literal
        }
        assertTrue("Phrases encore en anglais à l'écran: $untranslated", untranslated.isEmpty())
    }

    @Test
    fun `la ligne Exiger des reglages nomme la biometrie du telephone`() {
        assertTrue(settings.contains("lock.biometryLabel(context)"))
        // Sans nom de biométrie (appareil sans visage ni empreinte), le libellé reste le
        // **générique de la référence**, « Face or fingerprint » — jamais « Require a passcode ».
        assertTrue(settings.contains("lock.biometryLabel(context) ?: \"Face or fingerprint\""))
        // Supprimer le code doit se voir tout de suite : l'écran tient son propre état.
        assertTrue(settings.contains("var hasPin by remember { mutableStateOf(lock.hasPin) }"))
        assertTrue(settings.contains("hasPin = lock.hasPin"))
        assertEquals("Exiger l’empreinte", OnyxLocalization.translate("Require Fingerprint", Locale.FRANCE))
        assertEquals("Exiger le visage", OnyxLocalization.translate("Require Face unlock", Locale.FRANCE))
        assertEquals(
            "Exiger le visage ou l’empreinte",
            OnyxLocalization.translate("Require Face or fingerprint", Locale.FRANCE),
        )
    }
}
