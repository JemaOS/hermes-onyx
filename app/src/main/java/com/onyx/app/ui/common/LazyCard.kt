package com.onyx.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxInkOn
import com.onyx.app.ui.design.OnyxContrast
import com.onyx.app.ui.design.OnyxSpace
import com.onyx.app.ui.localization.BasicText
import com.onyx.app.ui.theme.LocalOnyxTheme

/**
 * **La forme de carte de `OnyxFormSection`, mais paresseuse.**
 *
 * Le relevé de l'audit (T9) a trouvé la même faute sur sept écrans : leur liste vit dans un
 * `Column(verticalScroll)` posé dans une `OnyxFormSection`, donc **chaque** rangée est composée — y
 * compris les 480 qu'on ne voit pas d'un dossier de 500 fichiers. La correction n'est pas de
 * réécrire la carte (elle est bien) mais de la **découper en trois** pour qu'une `LazyColumn`
 * puisse la porter :
 *
 * - [onyxLazyCardHeader] — le titre de section, ses petites capitales et son retrait, exactement
 *   comme `OnyxFormSection` le dessine ;
 * - [OnyxCardRow] — la surface de la carte (`formSurfaceColor`, coins `CardCorner`), posée **rangée
 *   par rangée**, les coins arrondis restant aux deux extrémités du groupe. Deux rangées voisines
 *   se touchent (aucun écart), si bien que l'ensemble se lit comme **une** carte, pas comme cinq ;
 * - [onyxLazyCardFooter] — la phrase du pied, dans l'encre mesurée de la maison.
 *
 * Aucune valeur n'est réinventée ici : les marges, le coin, la surface et l'encre viennent des
 * mêmes jetons que `OnyxFormSection` (`OnyxSpace.ScreenMargin`, `OnyxSpace.CardCorner`,
 * `theme.formSurfaceColor`, `OnyxInkOn(…, OnyxContrast.TEXT_MIN)`). Une carte paresseuse ne peut donc
 * pas dériver d'une carte ordinaire.
 */

/** La page elle-même : une `LazyColumn` avec les marges de la maison. */
@Composable
fun OnyxLazyFormPage(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    /**
     * La réserve sous le dernier élément. 40 dp par défaut, comme avant ; l'appelant qui sait ce
     * qui se pose en bas de son écran — la plaque du fil, le pied du volet — passe la sienne, pour
     * que sa dernière rangée ne soit pas enterrée dessous.
     */
    bottomPadding: Dp = 40.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(bottom = bottomPadding),
        content = content,
    )
}

/** Le titre de section d'une carte paresseuse — les mêmes petites capitales que `OnyxFormSection`. */
fun LazyListScope.onyxLazyCardHeader(text: String) {
    item(key = "card-header:$text") {
        OnyxCardHeader(text = text)
    }
}

@Composable
private fun OnyxCardHeader(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = OnyxLocalization[text].uppercase(),
        localize = false,
        style = theme.footnote.copy(
            color = OnyxInkOn(theme.secondaryText, theme.formPageColor, OnyxContrast.TEXT_MIN),
        ),
        modifier = Modifier.padding(
            start = OnyxSpace.BarInset,
            top = OnyxSpace.SectionGap,
            bottom = OnyxSpace.Half,
        ),
    )
}

/** Le pied d'une carte paresseuse — la phrase qui explique la section, sous elle. */
fun LazyListScope.onyxLazyCardFooter(text: String) {
    item(key = "card-footer:$text") {
        OnyxCardFooter(text = text)
    }
}

@Composable
private fun OnyxCardFooter(text: String) {
    val theme = LocalOnyxTheme.current
    BasicText(
        text = text,
        style = theme.footnote.copy(
            color = OnyxInkOn(theme.secondaryText, theme.formPageColor, OnyxContrast.TEXT_MIN),
        ),
        modifier = Modifier.padding(
            start = OnyxSpace.BarInset,
            end = OnyxSpace.BarInset,
            top = OnyxSpace.Half,
        ),
    )
}

/**
 * **Une rangée de la carte paresseuse** : la surface, le retrait, et les coins seulement aux
 * extrémités. [first] et [last] portent le groupe : au milieu, la surface est un simple bandeau qui
 * se soude à ses voisines.
 */
@Composable
fun LazyItemScope.OnyxCardRow(
    first: Boolean,
    last: Boolean,
    content: @Composable () -> Unit,
) {
    val theme = LocalOnyxTheme.current
    val corner = OnyxSpace.CardCorner
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OnyxSpace.ScreenMargin)
            .background(theme.formSurfaceColor, shape),
    ) {
        content()
    }
}
