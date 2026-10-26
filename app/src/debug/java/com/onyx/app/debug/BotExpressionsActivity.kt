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
 * La planche des états d'un bot, **debug seulement**.
 *
 * Elle dessine, côte à côte et en grand, le même visage dans chacun des cinq états qu'un bot prend
 * pendant une conversation — ÉCOUTE, RÉFLEXION, PAROLE, SUCCÈS, ERREUR — pour que la recette
 * visuelle porte sur le **dessin** de chaque état, pas sur un parcours serveur qu'un émulateur nu ne
 * peut pas jouer. C'est un **état injecté**, et le rapport le dit: « PAROLE » passe par la vraie
 * [`VoiceFace`] (celle de la scène vocale, bouche ouverte par la pose), les quatre autres par
 * l'humeur ([BotMood]) que les écrans — la liste des bots, le fil d'une salle, la scène vocale —
 * passent réellement à `BotLook`.
 *
 * Rien de ceci n'existe dans l'APK signé: l'activité vit dans le jeu de sources `debug`.
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

    /** Un état: le visage du bot dans cet état, et son nom dessous. */
    @Composable
    private fun StateFace(label: String, mood: BotMood = BotMood.IDLE, speaking: Boolean = false) {
        val theme = LocalOnyxTheme.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                if (speaking) {
                    // La vraie scène vocale: son visage, sa pose de parole (bouche ouverte, un
                    // soupçon de sourcils et de respiration), au repos de phase.
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
         * Un visage **neutre** pour la planche (yeux ronds, sourire, sans marque): les cinq états
         * se distinguent alors par ce que l'humeur y ajoute, et non par les traits déjà présents.
         */
        const val QUILL_FACE = "1:blob:round:smile:none:4:0:0"
    }
}
