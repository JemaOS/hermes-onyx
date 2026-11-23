package com.onyx.app.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onyx.app.ui.design.BotFace
import com.onyx.app.ui.design.BotLook
import com.onyx.app.ui.design.BotMood
import com.onyx.app.ui.design.FaceEyes
import com.onyx.app.ui.design.FaceMark
import com.onyx.app.ui.design.FaceMouth
import com.onyx.app.ui.design.FaceShape
import com.onyx.app.ui.design.LocalBotFaces
import com.onyx.app.ui.design.ProvideReduceMotion
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * **The bot avatar plate** — debug only.
 *
 * It renders, side by side and without any server path, everything the acceptance criteria must judge:
 *
 *  - **the four sizes** (20, 28, 44, 56 dp) in **both appearances**, across two identities —
 *    that is where it shows that the **finesse** (the gradient, the highlight, the sparkle) holds up at
 *    small size, without flattening the drawing;
 *  - **the owner's six captured bots** and the demo set's six;
 *  - **recognition at 20 dp**: the same drawing at all four sizes, to judge the **shape** and
 *    the **contrast** rather than a trace reduced to a bit;
 *  - the **expression vocabulary**: the moods on the same drawing (rest, listening, thinking,
 *    working, pleased) — the shape carries the color, the face changes;
 *  - the face **shapes**, each in its own color.
 *
 * The trace is **colored and matte**: it is a **face**, not a UI pictogram. No
 * black-on-white silhouette is rendered here — the one-bit test is no longer an acceptance criterion.
 *
 * None of this exists in the signed APK: the activity lives in the `debug` source set.
 */
class BotAvatarPlateActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ProvideReduceMotion {
                CompositionLocalProvider(LocalBotFaces provides true) {
                    Planche()
                }
            }
        }
    }
}

/** The owner's six captured bots (their real conversations). */
private val BOTS_DU_PROPRIETAIRE = listOf("Onyx", "coder", "kilo", "scout", "kilo (copie)", "QA-tester")

/** The demo set served by the server double. */
private val BOTS_DE_DEMO = listOf("default", "quill", "scout", "fix-it", "weekend hike", "garage project")

@Composable
private fun Planche() {
    val base = LocalOnyxTheme.current
    val light = ResolvedTheme(base.theme, base.palette, dark = false, fonts = base.fonts)
    val dark = ResolvedTheme(base.theme, base.palette, dark = true, fonts = base.fonts)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Section("Avatars · 20 / 28 / 44 / 56 dp · apparence claire", light) {
            ScaleRow("scout")
            ScaleRow("quill")
        }

        Section("Mêmes tailles · apparence sombre", dark) {
            ScaleRow("scout")
            ScaleRow("quill")
        }

        Section("Les six bots du propriétaire · 44 dp", light) {
            BotRow(BOTS_DU_PROPRIETAIRE, 44.dp)
        }

        Section("Les six bots de démonstration · 44 dp", light) {
            BotRow(BOTS_DE_DEMO, 44.dp)
        }

        Section("Reconnaissance à 20 dp (par la forme et le contraste)", light) {
            BotRow(BOTS_DU_PROPRIETAIRE, 20.dp)
        }

        Section("La couleur porte l'identité — même bot, deux fois", light) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("scout", "scout", "quill", "quill", "fix-it", "garage-project").forEach {
                    Labelled(it) { BotLook(name = it, faceCode = null, size = 44.dp, blinks = false) }
                }
            }
        }

        Section("Le vocabulaire d'expression — même dessin, visage qui change", light) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Labelled("repos") { BotLook(name = "scout", faceCode = null, size = 56.dp, blinks = false) }
                Labelled("écoute") { BotLook(name = "scout", faceCode = null, size = 56.dp, mood = BotMood.LISTENING, blinks = false) }
                Labelled("réflexion") { BotLook(name = "scout", faceCode = null, size = 56.dp, mood = BotMood.THINKING, blinks = false) }
                Labelled("travail") { BotLook(name = "scout", faceCode = null, size = 56.dp, mood = BotMood.WORKING, blinks = false) }
                Labelled("content") { BotLook(name = "scout", faceCode = null, size = 56.dp, mood = BotMood.PLEASED, blinks = false) }
            }
        }

        Section("Les formes du visage, chacune dans sa couleur", light) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FaceShape.given.forEach { shape ->
                    Labelled(shape.id) {
                        BotLook(
                            name = "scout",
                            faceCode = BotFace(shape = shape, eyes = FaceEyes.ROUND, mouth = FaceMouth.SMILE, mark = FaceMark.NONE).code(),
                            size = 44.dp,
                            blinks = false,
                        )
                    }
                }
            }
        }
    }
}

/** One appearance's block (light or dark): the same plate, under the requested theme. */
@Composable
private fun Section(title: String, theme: ResolvedTheme, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(theme.pageColor).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(text = title, style = theme.caption2.copy(color = theme.textColor))
        CompositionLocalProvider(LocalOnyxTheme provides theme) { content() }
    }
}

/** The four sizes of one same bot, under the current theme. */
@Composable
private fun ScaleRow(name: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        listOf(20, 28, 44, 56).forEach { dp ->
            Labelled("$dp dp") {
                BotLook(name = name, faceCode = null, size = dp.dp, blinks = false)
            }
        }
    }
}

/** A row of bots at a given size. */
@Composable
private fun BotRow(names: List<String>, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        names.forEach { name ->
            Labelled(name) { BotLook(name = name, faceCode = null, size = size, blinks = false) }
        }
    }
}

/** A face and its label, with no measurement: the plate is reviewed by eye. */
@Composable
private fun Labelled(label: String, face: @Composable () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) { face() }
        BasicText(text = label, style = theme.caption2.copy(color = theme.textColor))
    }
}
