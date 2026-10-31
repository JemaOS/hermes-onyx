package com.onyx.app.notifications

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.onyx.app.di.AppContainer
import com.onyx.app.ui.calls.IncomingCallScreen
import com.onyx.app.ui.theme.OnyxAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * L'écran **plein** d'un appel entrant : sur l'écran verrouillé, avec l'écran éteint, un tour qui
 * attend une décision prend tout l'écran — comme un vrai appel.
 *
 * Reprend `IncomingCallActivity` de la référence (`androidApp/.../notify/IncomingCallActivity.kt`) :
 * fermé dès que l'appel disparaît (décroché ailleurs, retiré, sonnerie finie), **Décrocher** demande
 * le déverrouillage d'abord — sur un téléphone verrouillé, une approbation serait sinon prise par
 * qui tient l'appareil — puis ouvre le mode vocal (ou l'app). **Refuser** retire l'appel et laisse
 * la notification habituelle prendre sa place.
 *
 * L'écran est dans le thème d'Onyx : clair ou sombre selon le réglage, police mise à l'échelle,
 * aucun composant Material.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        val callId = intent?.getStringExtra(CallRinger.EXTRA_CALL_ID).orEmpty()
        val call = CallRinger.current(callId)
        if (call == null) {
            finish()
            return
        }
        setContent {
            val settings = AppContainer.settings
            val theme by settings.theme.collectAsStateWithLifecycle()
            val appearance by settings.appearance.collectAsStateWithLifecycle()
            val themeColors by settings.themeColors.collectAsStateWithLifecycle()
            val pureBlack by settings.pureBlack.collectAsStateWithLifecycle()
            val halo by settings.halo.collectAsStateWithLifecycle()
            OnyxAppTheme(
                theme = theme,
                appearance = appearance,
                colors = themeColors[theme.id],
                pureBlack = pureBlack,
                halo = halo,
            ) {
                IncomingCallScreen(
                    caller = call.title?.takeIf { it.isNotBlank() } ?: "Hermes",
                    why = call.why,
                    onAnswer = { answer(call) },
                    onDecline = {
                        CallRinger.decline(this@IncomingCallActivity, call.id)
                        finish()
                    },
                )
            }
        }
        // L'appel a disparu (décroché ailleurs, retiré, sonnerie finie) : l'écran s'efface avec lui.
        lifecycleScope.launch {
            CallRinger.ringing.first { call.id !in it }
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    /** Un second appel pendant celui-ci : le plus récent prend l'écran. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (CallRinger.current(intent.getStringExtra(CallRinger.EXTRA_CALL_ID).orEmpty()) != null) recreate()
    }

    /** Décrocher : on déverrouille d'abord (l'approbation vaut pour qui tient le téléphone). */
    private fun answer(call: CallRinger.Ringing) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            talk(call)
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = talk(call)
                // Refusé de déverrouiller : l'appel continue de sonner et finira en notification.
            },
        )
    }

    /** Hermes en ligne : l'appel se retire (sans repli), puis le mode vocal s'ouvre. */
    private fun talk(call: CallRinger.Ringing) {
        if (isFinishing || isDestroyed) return
        CallRinger.answered(this, call.id)
        startActivity(CallRinger.answerIntent(this, call))
        finish()
    }

    /** Plein écran sur l'écran verrouillé, et l'écran s'allume pour montrer qui appelle. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
