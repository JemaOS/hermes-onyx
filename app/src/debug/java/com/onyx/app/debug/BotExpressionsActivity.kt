package com.onyx.app.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.BotPose
import com.onyx.app.ui.design.LocalBotFaces
import com.onyx.app.ui.design.ProvideReduceMotion
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.voice.VoiceFace

/**
 * The plate of a bot's states, **debug only**.
 *
 * It draws, side by side and large, the same face in each of the five states a bot takes
 * during a conversation — LISTENING, THINKING, SPEAKING, SUCCESS, ERROR — so that the visual
 * acceptance focuses on the **drawing** of each state, not on a server path that a bare emulator
 * cannot play. It is an **injected state**, and the report says so: "SPEAKING" (French UI string "PAROLE") goes through the real
 * [`VoiceFace`] (the one from the voice scene, mouth open via the pose), the other four through
 * the mood ([BotMood]) that the screens — the bot list, a room's thread, the voice scene —
 * actually pass to `BotLook`.
 *
 * None of this exists in the signed APK: the activity lives in the `debug` source set.
 */
class BotExpressionsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ProvideReduceMotion {
                CompositionLocalProvider(LocalBotFaces provides true) {
                    val theme = LocalOnyxTheme.current
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(theme.pageColor)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BasicText(
                                text = "Les états d'un bot (maquette)",
                                style = theme.title3.copy(color = theme.textColor),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                StateFace("ÉCOUTE", mood = BotMood.LISTENING)
                                StateFace("RÉFLEXION", mood = BotMood.THINKING)
                                StateFace("PAROLE", speaking = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                StateFace("SUCCÈS", mood = BotMood.PLEASED)
                                StateFace("ERREUR", mood = BotMood.ERROR)
                            }
                        }
                    }
                }
            }
        }
    }

    /** A state: the bot's face in that state, and its name below. */
    @Composable
    private fun StateFace(label: String, mood: BotMood = BotMood.IDLE, speaking: Boolean = false) {
        val theme = LocalOnyxTheme.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                if (speaking) {
                    // The real voice scene: its face, its speaking pose (mouth open, a
                    // hint of eyebrows and breathing), at phase rest.
                    VoiceFace(
                        name = QUILL,
                        faceCode = QUILL_FACE,
                        phase = com.onyx.app.voice.VoicePhase.SPEAKING,
                        level = { 0.8f },
                        size = 132.dp,
                    )
                } else {
                    com.onyx.app.ui.design.BotLook(
                        name = QUILL,
                        faceCode = QUILL_FACE,
                        size = 132.dp,
                        mood = mood,
                        showsInitial = false,
                    )
                }
            }
            BasicText(text = label, style = theme.caption.copy(color = theme.textColor))
        }
    }

    private companion object {
        const val QUILL = "quill"

        /**
         * A **neutral** face for the plate (round eyes, smile, no mark): the five states
         * are then distinguished by what the mood adds to it, and not by traits already present.
         */
        const val QUILL_FACE = "1:blob:round:smile:none:4:0:0"
    }
}
