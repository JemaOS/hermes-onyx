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
 * The **full** screen of an incoming call: on the lock screen, with the screen off, a turn
 * waiting for a decision takes the whole screen — like a real call.
 *
 * Reproduces the reference `IncomingCallActivity` (`androidApp/.../notify/IncomingCallActivity.kt`):
 * closed as soon as the call disappears (answered elsewhere, removed, ringing finished), **Answer** asks
 * for the unlock first — on a locked phone, an approval would otherwise be taken by
 * whoever holds the device — then opens voice mode (or the app). **Decline** removes the call and lets
 * the usual notification take its place.
 *
 * The screen uses the Onyx theme: light or dark per the setting, scaled font,
 * no Material components.
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
        // The call has disappeared (answered elsewhere, removed, ringing finished): the screen fades with it.
        lifecycleScope.launch {
            CallRinger.ringing.first { call.id !in it }
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    /** A second call during this one: the most recent takes the screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (CallRinger.current(intent.getStringExtra(CallRinger.EXTRA_CALL_ID).orEmpty()) != null) recreate()
    }

    /** Answer: the unlock comes first (the approval is valid for whoever holds the phone). */
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
                // Unlock refused: the call keeps ringing and will end up as a notification.
            },
        )
    }

    /** Hermes online: the call is removed (without fallback), then voice mode opens. */
    private fun talk(call: CallRinger.Ringing) {
        if (isFinishing || isDestroyed) return
        CallRinger.answered(this, call.id)
        startActivity(CallRinger.answerIntent(this, call))
        finish()
    }

    /** Full screen on the lock screen, and the screen turns on to show who is calling. */
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
