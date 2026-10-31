package com.onyx.app.ui.chat.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le câblage du **visage vivant de l'en-tête** : ce que le code doit tenir, relu dans les sources.
 *
 * Ce qui se vérifie ailleurs : le **rythme** (intervalles, poids, graine) par `BotIdleFaceTest`
 * dans `:core`, et le **rendu** par la capture d'émulateur — c'est elle qui juge, pas ce fichier.
 * Ici on tient les trois promesses qui ne se voient pas sur une image fixe : la **boîte de
 * l'avatar ne dépend d'aucun instant de l'animation**, la boucle **s'arrête hors écran**, et
 * **rien ne tourne sous « Réduire les animations »**.
 */
class TopBarBotFaceTest {

    private val topBar = File("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt").readText()
    private val live = File("src/main/java/com/onyx/app/ui/chat/components/LiveBotFace.kt").readText()
    private val engine = File("../core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt").readText()

    @Test
    fun `la barre passe le visage vivant, sans lui donner de taille`() {
        assertTrue(
            "la barre du haut montre le visage vivant",
            topBar.contains("LiveBotFace(name = name, faceCode = faceCode)"),
        )
        assertTrue("la taille de la face est une constante", topBar.contains("internal val TopBarAvatarSize = 30.dp"))
        assertTrue("celle de sa boîte aussi", topBar.contains("internal val TopBarAvatarBoxSize = 38.dp"))
    }

    @Test
    fun `la boite de l avatar ne depend d aucun instant de l animation`() {
        // La boîte est posée **une fois**, sur la constante ; aucune autre taille n'est écrite là.
        assertTrue("une seule taille, la constante", live.contains(".size(boxSize)"))
        assertFalse(
            "aucune taille calculée depuis un instant de l'animation",
            Regex("""size\([^)]*(pose|frame|idle|reaction|progress)""").containsMatchIn(live),
        )
        // Et le moteur dessine dans un canevas **de la taille de sa boîte** : ce qui bouge est
        // dessiné, jamais mesuré. (Le moteur lui-même n'est pas touché par cette scène.)
        assertTrue(
            "le visage est dessiné dans un canevas à la taille de la boîte",
            engine.contains("Canvas(Modifier.matchParentSize())"),
        )
    }

    @Test
    fun `rien ne tourne hors de la composition ni en arriere plan`() {
        assertTrue("la boucle vit dans un effet annulé hors composition", live.contains("LaunchedEffect(seed, onScreen, reduceMotion)"))
        assertTrue("elle ne démarre pas si l'écran n'est pas visible", live.contains("if (reduceMotion || !onScreen) return@LaunchedEffect"))
        assertTrue("l'état de l'écran suit le cycle de vie", live.contains("Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> onScreen = false"))
        assertTrue("et repart à la reprise", live.contains("Lifecycle.Event.ON_RESUME -> onScreen = true"))
        assertTrue("l'observateur est retiré", live.contains("lifecycle.removeObserver(observer)"))
    }

    @Test
    fun `sous Reduce Motion, rien ne bouge tout seul`() {
        // Aucun pouls automatique : la boucle s'arrête avant de prendre le premier.
        assertTrue("aucun pouls automatique", live.contains("if (reduceMotion || !onScreen) return@LaunchedEffect"))
        assertTrue("le visage est reposé en entrant", live.contains("idle = null"))
        // La réaction reste, mais sans rebond ni retour haptique.
        assertTrue("la réaction ne rebondit pas", live.contains("if (!taken.bounce)"))
        assertTrue(
            "et aucun retour haptique",
            live.contains("if (!reduceMotion) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)"),
        )
        assertTrue(
            "le visage est remis au repos exactement, sans pose tenue",
            live.contains("else BotPose(tilt = tilt)"),
        )
    }

    @Test
    fun `la scene ne dessine rien elle-meme`() {
        // Aucune couleur, aucune forme, aucun trait : les poses du moteur, et rien d'autre.
        listOf("Color(", "Path(", "Brush", "drawCircle", "drawPath", "drawRect", "Paint(").forEach { forbidden ->
            assertFalse("la scène n'emprunte pas $forbidden au moteur", live.contains(forbidden))
        }
        assertTrue("le visage est le dessin partagé", live.contains("BotLook("))
        assertTrue("avec une pose du moteur", live.contains("pose = shown"))
        assertTrue("et une humeur du moteur", live.contains("mood = mood"))
        assertTrue("le clignement du moteur est éteint pour ne pas se superposer", live.contains("blinks = false"))
    }

    @Test
    fun `le visage vivant est reutilisable pour les autres petites faces`() {
        // Rien de propre à la barre du haut : la taille, la boîte et l'initiale sont des paramètres,
        // et aucune dépendance à l'en-tête n'est tirée.
        assertTrue(live.contains("faceSize: Dp = TopBarAvatarSize"))
        assertTrue(live.contains("boxSize: Dp = TopBarAvatarBoxSize"))
        assertTrue(live.contains("showsInitial: Boolean = false"))
        assertFalse("aucune référence au titre de la barre", live.contains("ThreadSpeaker"))
    }
}
