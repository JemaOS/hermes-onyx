package com.onyx.app.ui.design

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * « Réduire les animations » — le Comme `@Environment(\.accessibilityReduceMotion)`
 *
 * Pourquoi ce fichier: , **chaque** vue animée lit ce drapeau et coupe son animation
 * d'un cran — les boucles `repeatForever` ne démarrent pas (`guard !reduceMotion else { return }`),
 * les `TimelineView(.animation)` sont mis en pause, les échelons se réduisent à zéro
 * (`delay(reduceMotion ? 0: 0.24)`) et les transitions deviennent des fondus
 * (`reduceMotion ? .opacity: .handoff`). L'app Android n'avait d'équivalent nulle part: les
 * orbes, les légendes et les arrivées de messages tournaient à pleine vitesse même pour qui a
 * demandé à son téléphone de ne plus bouger.
 *
 * **Le réglage système, et lui seul.** n'a qu'un réglage, l'utilisateur ne peut pas le
 * désactiver par app; le implémentation lit donc les trois échelles système que l'accessibilité Android
 * met à zéro (« Supprimer les animations », « Animation scale ») et ne propose **aucun** interrupteur
 * interne — un écran de réglages qui contourne un réglage d'accessibilité n'est pas un implémentation.
 *
 * **Pourquoi pas seulement Compose.** Compose applique déjà `ANIMATOR_DURATION_SCALE` aux
 * animations qu'il pilote: avec l'échelle à zéro, un `tween` saute à sa valeur finale. Mais cela
 * ne couvre **pas** ce que coupe explicitement: les `delay` et les `Task.sleep` des
 * échelons, les boucles infinies décoratives (qui, à l'échelle zéro, dégénèrent en une itération
 * par trame au lieu de ne pas tourner), et les poses au repos que garde quand il refuse
 * de démarrer une animation. C'est ce reliquat-là que ce fichier porte.
 *
 * Fil d'exécution: [ProvideReduceMotion] pose [LocalReduceMotion] à la racine de l'app et
 * s'abonne aux trois réglages; un changement dans les options de développement se voit à l'écran
 * sans redémarrer l'app, comme `accessibilityReduceMotion`.
 */
object ReduceMotion {

    /** Les trois échelles système que « Supprimer les animations » met à zéro. */
    val scopes: List<String> = listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
    )

    /**
     * La règle, pure et testable: les animations sont réduites dès qu'une des trois échelles est
     * à zéro. Android 12 met les trois à zéro pour « Supprimer les animations »; un utilisateur
     * qui ne coupe que les transitions ne veut pas non plus des boucles décoratives.
     */
    fun isReduced(
        animatorDurationScale: Float,
        transitionAnimationScale: Float,
        windowAnimationScale: Float,
    ): Boolean = animatorDurationScale == 0f ||
        transitionAnimationScale == 0f ||
        windowAnimationScale == 0f

    /** Une échelle du système, 1 par défaut (`Settings.Global.getFloat`). */
    fun scale(context: Context, key: String): Float =
        Settings.Global.getFloat(context.contentResolver, key, 1f)

    /** L'état courant du réglage sur cet appareil. */
    fun read(context: Context): Boolean = isReduced(
        animatorDurationScale = scale(context, Settings.Global.ANIMATOR_DURATION_SCALE),
        transitionAnimationScale = scale(context, Settings.Global.TRANSITION_ANIMATION_SCALE),
        windowAnimationScale = scale(context, Settings.Global.WINDOW_ANIMATION_SCALE),
    )
}

/**
 * Le drapeau, lisible partout où une vue animée en a besoin — l'équivalent de
 * `@Environment(\.accessibilityReduceMotion)`. Vaut `false` hors de [ProvideReduceMotion], pour
 * qu'une prévisualisation reste animée.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Pose [LocalReduceMotion] depuis les réglages système, et suit leurs changements. */
@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(ReduceMotion.read(context)) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                reduced = ReduceMotion.read(context)
            }
        }
        ReduceMotion.scopes.forEach { key ->
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    CompositionLocalProvider(LocalReduceMotion provides reduced, content = content)
}

/**
 * Sous « Réduire les animations », une transition devient instantanée. À n'utiliser que là où le
 * ne garde pas un fondu: quand il écrit `reduceMotion ? .opacity: .handoff`, le fondu se
 * porte à la main, pas ici.
 */
@Composable
fun <T> reducedSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spec

/**
 * Le `try? await Task.sleep(for: .milliseconds(reduceMotion ? 0: delay))` (StartScreen,
 * FollowUpChips): sous Reduce Motion, tout arrive d'un coup au lieu d'un échelon à la fois.
 */
@Composable
fun reducedStagger(delayMillis: Long): Long = if (LocalReduceMotion.current) 0L else delayMillis

/**
 * Le `guard !reduceMotion else { return }` devant une boucle décorative: la boucle ne
 * démarre pas et la vue reste à sa pose de repos ([rest]). Le laisse la boucle à son état
 * initial, donc c'est la valeur de repos qu'il faut rendre, pas la cible.
 *
 * La boucle est passée en lambda pour que `rememberInfiniteTransition.animateFloat(…)` ne soit
 * pas seulement ignorée mais **non composée**: une boucle infinie à l'échelle zéro dégénère en
 * une itération par trame, elle ne s'arrête pas d'elle-même.
 */
@Composable
fun ambientLoop(rest: Float, loop: @Composable () -> Float): Float =
    if (LocalReduceMotion.current) rest else loop()
