package com.onyx.app.ui.design

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.onyx.app.ui.localization.BasicText

/**
 * Du texte de conversation, que le doigt peut prendre.
 *
 * Un appui long pose la sélection sur un mot, deux poignées l'étendent, et c'est la **barre du
 * système** qui s'affiche (Couper / Copier / Coller / Tout sélectionner) : le mécanisme est celui
 * de Compose ([SelectionContainer]), jamais une barre dessinée par l'app. Les poignées et le
 * surlignage prennent la couleur de l'app par `LocalTextSelectionColors` (posé par le thème).
 *
 * **Pourquoi pas dans [BasicText] ?** Ce dernier habille toute l'app : l'activer globalement
 * donnerait une sélection dans les écrans de réglages, où elle n'a pas de sens, et volerait
 * l'appui long des lignes de liste, qui portent un menu ancré. La sélection se demande donc
 * explicitement, texte par texte, là où il y a quelque chose à lire et à reprendre : les messages,
 * le raisonnement déplié, les sorties d'outils, les blocs de code et les notes du fil.
 *
 * **Là où il ne faut PAS l'utiliser** : dans une ligne dont l'appui long ouvre déjà un menu
 * (`onyxTappable(onLongPress = …)` — liste des conversations, brouillons en file, pièces d'aperçu) ;
 * la sélection prendrait le geste. Là, la ligne reste un menu.
 */
@Composable
fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    localize: Boolean = true,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    SelectionContainer(modifier = modifier) {
        BasicText(
            text = text,
            localize = localize,
            style = style,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            overflow = overflow,
        )
    }
}

/** Le même, pour un texte déjà composé (bloc de code coloré, ligne d'invite à préfixe gras). */
@Composable
fun SelectableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    SelectionContainer(modifier = modifier) {
        BasicText(
            text = text,
            style = style,
            softWrap = softWrap,
            maxLines = maxLines,
        )
    }
}
