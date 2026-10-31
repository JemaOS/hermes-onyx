package com.onyx.app.ui.lock

import com.onyx.app.ui.design.OnyxSpace

import com.onyx.app.ui.design.onyxPage

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.onyx.app.ui.localization.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxTextField
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.security.AppLock

private const val TAG = "OnyxLock"

/**
 * L'écran de verrouillage.
 *
 * Miroir de `LockScreenView` : le cadenas au trait, « Onyx est verrouillé », et le bouton qui
 * demande la biométrie du téléphone. Le code est le secours d'Android quand le doigt ou le visage ne
 * répond pas — c'est le chemin qui marche toujours, il n'est jamais retiré.
 *
 * Trois règles, apprises à l'usage :
 *  - le bouton de biométrie n'apparaît que si le téléphone peut réellement l'évaluer
 *    (`BiometricManager.canAuthenticate`) ; sinon on dit à l'écran pourquoi, plutôt que d'offrir un
 *    bouton muet ;
 *  - toute erreur de la feuille biométrique s'écrit à l'écran, lisible, et une empreinte qui ne
 *    correspond pas laisse réessayer ;
 *  - le contenu défile et se cale au-dessus de l'inset du clavier : le champ « Code » et son bouton
 *    restent visibles.
 */
@Composable
fun LockScreen(
    lock: AppLock,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // `null` veut dire « ce téléphone ne peut pas » : rien à proposer, donc pas de bouton — et la
    // raison est dite plus bas. La question est posée une fois, comme référence résout `biometryName`.
    val biometry = remember(context, lock) { lock.biometryLabel(context) }

    fun promptBiometry() {
        // Un hôte qui n'est pas une `FragmentActivity` ne peut pas porter `BiometricPrompt` : sans
        // prompt il ne se passerait rien du tout, donc on le journalise **et** on le dit à l'écran.
        val activity = context as? FragmentActivity
        if (activity == null) {
            Log.w(TAG, "BiometricPrompt needs a FragmentActivity host; the passcode stays the way in.")
            error = OnyxLocalization["Biometric unlock isn't available on this screen. Use the passcode."]
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    error = null
                    onUnlocked()
                }

                /** Un doigt qui glisse n'interrompt pas la demande : on le dit et on laisse réessayer. */
                override fun onAuthenticationFailed() {
                    error = OnyxLocalization["That didn't match. Try again, or use the passcode."]
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.w(TAG, "BiometricPrompt error $errorCode: $errString")
                    // Le bouton négatif n'est pas une panne : c'est l'utilisateur qui demande le code,
                    // et la feuille se ferme d'elle-même. On le guide au lieu de lui dire que ça a raté.
                    // Un balayage de côté est un abandon : rien à dire non plus.
                    error = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> OnyxLocalization["Enter your passcode."]
                        BiometricPrompt.ERROR_USER_CANCELED -> null
                        else -> OnyxLocalization["Unlock didn't work. Try again, or use the passcode."]
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(OnyxLocalization["Onyx is locked"])
            .setSubtitle(OnyxLocalization["Unlock to see your conversations"])
            // Les classes **fortes et faibles** : sur beaucoup d'appareils (Honor, Xiaomi), seules
            // les fortes sont proposées, et l'ancienne demande (faible seule) restait sans réponse.
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
            )
            // L'identifiant de l'appareil n'est pas demandé — le code de Onyx est le secours, donc
            // le bouton négatif reste permis et mène au champ de code.
            .setNegativeButtonText(OnyxLocalization["Use the passcode"])
            .setConfirmationRequired(false)
            .build()
        error = null
        // Un prompt qui refuse de démarrer (une famille d'appareils le fait) doit se voir, pas
        // rester un bouton sans effet.
        runCatching { prompt.authenticate(info) }.onFailure { failure ->
            Log.w(TAG, "BiometricPrompt wouldn't start", failure)
            error = OnyxLocalization["Unlock didn't work. Try again, or use the passcode."]
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme)
            .statusBarsPadding(),
    ) {
        // Centré quand il y a la place, défilant quand il n'y en a pas : la colonne fait au moins la
        // hauteur visible, donc le champ « Code » et son bouton restent au-dessus du clavier au lieu
        // de passer dessous.
        val viewport: Dp = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .then(if (viewport == Dp.Infinity) Modifier else Modifier.heightIn(min = viewport))
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OnyxIcon(Symbols.LockOutline, tint = theme.secondaryText, size = 30.dp)

                BasicText(
                    text = "Onyx is locked",
                    style = theme.title3.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold),
                )

                if (biometry != null) {
                    Box(
                        modifier = Modifier
                            .background(theme.accent, RoundedCornerShape(50))
                            .onyxClickable { promptBiometry() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        BasicText(
                            text = "Unlock with $biometry",
                            style = theme.body.copy(color = theme.userText, fontWeight = FontWeight.Medium),
                        )
                    }
                } else {
                    // Pas de bouton muet : on explique ce qui manque, et où le régler.
                    BasicText(
                        text = if (lock.hasPin) {
                            "Face or fingerprint isn't set up on this phone. Use the passcode."
                        } else {
                            "Face or fingerprint isn't set up on this phone. Add one in Settings › Lock."
                        },
                        style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                    )
                }

                if (lock.hasPin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.surfaceColor, RoundedCornerShape(OnyxSpace.FormCorner))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OnyxTextField(
                            value = pin,
                            onValueChange = { value ->
                                pin = value.filter { it.isDigit() }.take(12)
                                error = null
                                if (pin.length >= 4 && lock.checkPin(pin)) onUnlocked()
                            },
                            placeholder = "Passcode",
                            style = theme.body,
                            keyboardType = KeyboardType.NumberPassword,
                            modifier = Modifier.weight(1f),
                        )
                        BasicText(
                            text = "Unlock",
                            style = theme.body.copy(
                                color = if (pin.length >= 4) theme.accent else theme.tertiaryText,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier.onyxClickable(enabled = pin.length >= 4) {
                                if (lock.checkPin(pin)) onUnlocked() else error = "That passcode isn't right."
                            },
                        )
                    }
                }

                // L'erreur se lit au corps de texte, au centre — pas en pied de page minuscule.
                error?.let {
                    BasicText(
                        text = it,
                        style = theme.subheadline.copy(color = theme.error, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}
