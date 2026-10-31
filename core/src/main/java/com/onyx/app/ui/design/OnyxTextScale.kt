package com.onyx.app.ui.design

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * L'échelle de texte **choisie dans les Réglages**, pour toute l'app.
 *
 * Elle était appliquée au **seul fil de conversation** : les trois écrans de fil posaient
 * `LocalOnyxTheme provides theme.withFontScale(chatTextScale)`, et partout ailleurs le réglage ne
 * faisait rien. Le propriétaire lit donc « Grand » dans les Réglages, l'applique, ouvre les
 * Réglages, les Capacités ou le volet — et rien n'a changé : un réglage qui ne tient que sur un
 * écran est un réglage qui ment.
 *
 * Le sens est **inversé ici** : l'activité lit la préférence et la pose à la racine, le thème
 * ([com.onyx.app.ui.theme.OnyxAppTheme]) l'applique à **tout** l'arbre, et les écrans de fil ne la
 * reposent plus (elle y serait appliquée deux fois, soit 1,23 × 1,23 = 1,51).
 */
val LocalOnyxTextScale = staticCompositionLocalOf { 1f }
