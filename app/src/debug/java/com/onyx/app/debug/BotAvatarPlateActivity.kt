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
 * **La planche des avatars de bot** — debug seulement.
 *
 * Elle rend, côte à côte et sans parcours serveur, tout ce que la recette doit juger :
 *
 *  - **les quatre tailles** (20, 28, 44, 56 dp) dans les **deux apparences**, sur deux identités —
 *    c'est là que se lit que la **finesse** (le dégradé, le reflet, l'étincelle) tient à petite
 *    taille, sans aplatir le dessin ;
 *  - **les six bots de la capture du propriétaire** et les six du jeu de démonstration ;
 *  - la **reconnaissance à 20 dp** : le même dessin aux quatre tailles, pour juger la **forme** et
 *    le **contraste** plutôt qu'un tracé réduit à un bit ;
 *  - le **vocabulaire d'expression** : les humeurs sur le même dessin (repos, écoute, réflexion,
 *    travail, content) — la forme porte la couleur, le visage change ;
 *  - les **formes** du visage, chacune dans sa couleur.
 *
 * Le tracé est **coloré et mat** : c'est un **visage**, pas un pictogramme d'interface. Aucune
 * silhouette noir-sur-blanc n'est rendue ici — le test à un bit n'est plus un critère d'acceptation.
 *
 * Rien de ceci n'existe dans l'APK signé : l'activité vit dans le jeu de sources `debug`.
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

/** Les six bots de la capture du propriétaire (ses conversations réelles). */
private val BOTS_DU_PROPRIETAIRE = listOf("Onyx", "coder", "kilo", "scout", "kilo (copie)", "QA-tester")

/** Le jeu de démonstration servi par le double du serveur. */
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

/** Le bloc d'une apparence (clair ou sombre) : la même planche, sous le thème demandé. */
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

/** Les quatre tailles d'un même bot, sous le thème courant. */
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

/** Une rangée de bots à une taille donnée. */
@Composable
private fun BotRow(names: List<String>, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        names.forEach { name ->
            Labelled(name) { BotLook(name = name, faceCode = null, size = size, blinks = false) }
        }
    }
}

/** Un visage et son libellé, sans mesure : la planche se relit à l'œil. */
@Composable
private fun Labelled(label: String, face: @Composable () -> Unit) {
    val theme = LocalOnyxTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) { face() }
        BasicText(text = label, style = theme.caption2.copy(color = theme.textColor))
    }
}
