package com.onyx.app.ui.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **La croissance du champ du compositeur** — la règle qui fait qu'aucune ligne n'est jamais coupée.
 *
 * Le défaut de la capture du propriétaire : le champ était à **hauteur figée** (64 dp, la rangée
 * `.ask` de la planche) alors que la référence le fait **grandir** — le texte s'y affichait sur
 * trois lignes entières, puis les commandes (+, micro, envoi) passaient sur **leur propre rangée**
 * en dessous. Chez nous, le champ restait à une ligne de haut : au-delà, le texte débordait sous les
 * commandes et la **première ligne** se retrouvait coupée.
 *
 * La règle tient en quatre points, et chacun est **calculable**, donc éprouvable sans appareil :
 *
 *  1. **une ligne au repos** (la hauteur de la planche, 64 dp) ;
 *  2. **il grandit ligne par ligne**, de la hauteur d'une ligne rendue, sans jamais de coupure,
 *     jusqu'à [MaxLines] ;
 *  3. **au maximum, il défile en lui-même** ([scrollsInternally]) — la hauteur ne bouge plus ;
 *  4. **dès la troisième ligne, les commandes descendent** sur leur propre rangée
 *     ([stacksCommands], tolérance de [StackAfterLines] = 2) : le texte ne passe **jamais** sous
 *     elles, et la plaque ne change pas de forme pour dix caractères.
 *
 * **La valeur maximale retenue : [MaxLines] = 8.** La planche ne la donne pas — son `.ask` est à
 * hauteur fixe (`hcss.py:238`, `.ask{height:64px}`), elle ne montre que l'état au repos ; le champ
 * qui grandit est celui de la **référence du propriétaire**, qui n'est pas dans les sources. Le
 * maximum est donc **choisi par la hauteur d'écran**, et le voici chiffré : sur l'appareil de
 * référence (1080 × 2400 px à 420 dpi → densité 2,625 → **≈ 914 dp de haut**), huit lignes de corps
 * (`OnyxBase.Body` × 1,22 ≈ 18,3 dp de ligne) font ≈ 146 dp de texte, plus les 20 dp de rembourrage
 * vertical du champ → ≈ 166 dp ; la plaque étendue vaut alors 166 + 76 (rangée d'onglets) =
 * **≈ 242 dp, soit 26 % de la hauteur**. Il reste ≈ 672 dp au fil : **aucun message n'est caché en
 * bas**, et la pastille « aller au dernier message » garde sa voie au-dessus de la plaque
 * ([PlateGeometry.FloatLane]). Huit lignes est aussi le plafond courant d'un champ de chat — au-delà,
 * on n'écrit plus dans la barre, on ouvre l'éditeur plein écran (le bouton « agrandir » apparaît déjà
 * pour un brouillon long).
 *
 * À **1,3× de police**, la règle est **la même** : le plafond est un nombre de **lignes**, pas une
 * hauteur — la ligne rendue grandit, le champ grandit avec elle, et il s'arrête toujours à huit
 * lignes. Rien n'est figé en dp.
 *
 * Les fonctions sont **pures** : le chiffre annoncé et le chiffre vérifié sont le même.
 */
object ComposerGrowth {

    /**
     * **Le maximum : huit lignes.** Au-delà, le champ cesse de grandir et **défile en lui-même**
     * ([scrollsInternally]). Choisi par la hauteur d'écran (voir le commentaire de l'objet) : la
     * plaque étendue reste sous ~26 % de la hauteur sur l'appareil de référence, donc le fil garde
     * la place de ses derniers messages.
     */
    const val MaxLines: Int = 8

    /**
     * **Le nombre de lignes tolérées avant que les commandes descendent : deux.**
     *
     * Le défaut du propriétaire (« dès que je dépasse dix caractères, la barre s'agrandit et déplace
     * le clavier ») vient de la règle d'avant, qui descendait les commandes **dès la deuxième
     * ligne**. Sur la plaque, le champ ne garde que ~127 dp de texte à 1,3× de police : la dixième
     * lettre suffisait à le faire passer à deux lignes, donc à **doubler la hauteur de la plaque**
     * (+ le champ pleine largeur, + la rangée des commandes, + son écart) au moment précis où l'on
     * écrit le premier mot d'un message.
     *
     * Avec une tolérance de deux lignes, la plaque **ne bouge pas** tant que le brouillon tient dans
     * la hauteur de repos : deux lignes rendues font `2 × 18,3 + 20 = 56,6 dp` à 1,0×, sous les
     * 64 dp de la planche — croissance **nulle** ; à 1,3× elles font `2 × 23,8 + 20 = 67,6 dp`, soit
     * **3,6 dp** de croissance, invisible. Les commandes descendent à la **troisième** ligne, quand
     * le brouillon est vraiment long et que la place manque — c'est aussi le moment où le bouton
     * « agrandir » approche.
     *
     * L'oscillation reste impossible : la mesure se fait toujours à la largeur de l'état d'une ligne
     * (voir [singleLineFieldWidthDp]), donc [stacksCommands] est une fonction pure de
     * (texte, largeur) — descendre une fois ne change pas la mesure qui l'a fait descendre.
     */
    const val StackAfterLines: Int = 2

    /**
     * **La hauteur au repos : trois lignes de champ, dès la première frappe.**
     *
     * Le propriétaire l'a demandé en ces mots : « il faut mieux déjà en grand de base ». Le défaut
     * mesuré : à la deuxième ligne — dix-neuf caractères d'un mot continu — la plaque changeait de
     * hauteur et **déplaçait le clavier**. Ce n'est pas la disposition qui bougeait, c'est la
     * réserve : le champ ne réservait qu'une ligne au repos.
     *
     * Avec trois lignes réservées, écrire les trois premières lignes ne change **rien** : la plaque
     * garde sa hauteur, le clavier ne bouge pas. Elle ne grandit qu'au-delà, et c'est alors un
     * agrandissement qu'on a demandé en écrivant un vrai paragraphe.
     *
     * Trois, pas huit : à 1,3× de police, trois lignes font `3 × 23,8 + 20 = 91,4 dp` — la plaque
     * tient à 167,4 dp avec sa rangée d'onglets, sous le cinquième de l'écran de référence (914 dp).
     */
    const val RestLines: Int = 3

    /**
     * **Le rembourrage vertical du champ** (haut + bas) : `OnyxComposer` pose `10.dp` de chaque
     * côté autour du `BasicTextField`. La hauteur d'une ligne s'y ajoute pour donner la hauteur du
     * champ. Écrit ici pour que la règle de croissance et le rendu ne divergent pas.
     */
    const val VerticalPaddingDp: Float = 20f

    /**
     * **La largeur fixe de la rangée d'une ligne** : le « + » ([OnyxSpace.ThumbnailSize], 44), le
     * micro et le mode vocal (44 chacun), l'envoi ([PlateGeometry.SendSlot], 48) et les **quatre**
     * écarts de la planche ([PlateGeometry.FieldGap], 10) — soit 220 dp. C'est la place qui n'est
     * pas au champ quand les commandes tiennent sur sa rangée.
     *
     * Elle **exclut** le bouton « agrandir » : celui-ci n'apparaît que pour un brouillon long, donc
     * quand la disposition est **déjà** celle des commandes sur leur rangée ; la largeur de
     * référence sert à mesurer le **passage** d'une ligne à plusieurs, et elle doit être celle de
     * l'état d'une ligne (sans « agrandir »), sinon la mesure ne décrirait pas le cas où elle décide.
     */
    val RowFixedWidthDp: Float =
        3f * OnyxSpace.ThumbnailSize.value + PlateGeometry.SendSlot.value +
            4f * PlateGeometry.FieldGap.value

    /**
     * **La largeur laissée au champ quand les commandes tiennent sur sa rangée** : la largeur du
     * contenu de la rangée moins [RowFixedWidthDp]. C'est la largeur à laquelle on mesure le
     * brouillon pour décider s'il tient sur une ligne — et **toujours** la même, quelle que soit la
     * disposition en cours, ce qui **interdit l'aller-retour** (voir [stacksCommands]).
     */
    fun singleLineFieldWidthDp(rowContentWidthDp: Float): Float =
        (rowContentWidthDp - RowFixedWidthDp).coerceAtLeast(1f)

    /**
     * **Les commandes descendent-elles sur leur propre rangée ?** Oui dès que le texte occupe plus
     * de [StackAfterLines] lignes ([lineCount] mesuré **à la largeur d'une ligne** —
     * [singleLineFieldWidthDp], jamais à la largeur courante).
     *
     * C'est cette **largeur fixe de mesure** qui empêche l'aller-retour pendant la frappe : quand les
     * commandes sont sur la rangée, le champ est étroit ; une fois descendues, il est **plus large**.
     * Si on mesurait à la largeur courante, un texte qui passe à plusieurs lignes ferait descendre
     * les commandes, le champ s'élargirait, le texte repasserait à une ligne, les commandes
     * remonteraient… En mesurant **toujours** à la largeur de l'état d'une ligne (la plus étroite),
     * la décision est une **fonction pure** de (texte, largeur) : elle ne peut pas osciller.
     *
     * La **tolérance de deux lignes** est celle du correctif « la barre s'agrandit au dixième
     * caractère » : voir [StackAfterLines]. Une ligne de plus tient dans la hauteur de repos, donc
     * la plaque ne bouge pas ; c'est la troisième qui fait descendre les commandes.
     */
    fun stacksCommands(lineCount: Int): Boolean = lineCount > StackAfterLines

    /** Les lignes réellement dessinées : au moins une, jamais plus de [MaxLines]. */
    fun shownLines(lineCount: Int): Int = lineCount.coerceIn(1, MaxLines)

    /** **Vrai si le texte dépasse le maximum : le champ défile alors en lui-même.** */
    fun scrollsInternally(lineCount: Int): Boolean = lineCount > MaxLines

    /**
     * **La hauteur de la rangée du champ** pour [lineCount] lignes, à une hauteur de ligne donnée
     * (celle de la police **rendue**, donc 1,3× comprise) : jamais moins que la hauteur au repos
     * ([PlateGeometry.FieldRowHeight], 64 dp), et **jamais de coupure** — [shownLines] borne les
     * lignes, le texte au-delà défile.
     */
    fun fieldRowHeightDp(lineCount: Int, lineHeightDp: Float): Float =
        maxOf(
            PlateGeometry.FieldRowHeight.value,
            // **En grand de base** : le champ montre toujours [RestLines] lignes, même vide. Une
            // plaque qui grandit à la deuxième ligne déplace le clavier au moment où l'on écrit le
            // premier mot — c'est le défaut du propriétaire (« ça change la taille de l'entrée du
            // prompt et déplace le clavier »). En réservant la place dès le repos, le passage d'une
            // ligne à trois ne bouge plus rien.
            maxOf(shownLines(lineCount), RestLines) * lineHeightDp + VerticalPaddingDp,
        )

    /**
     * **La hauteur de la plaque** quand le champ a [fieldRowHeightDp] : le champ étendu **plus** la
     * rangée d'onglets ([PlateGeometry.Height]). Au repos elle vaut exactement 140 dp ; elle grandit
     * avec le champ, et la **réserve du fil suit** (elle se mesure, voir `threadBottomReserve`).
     */
    fun plaqueHeightDp(fieldRowHeightDp: Float): Float =
        fieldRowHeightDp + PlateGeometry.TabRowHeight.value

    /** Le dégagement au-dessus de la pastille « aller au dernier message », pour une plaque de [plaqueHeightDp]. */
    fun pillClearanceDp(plaqueHeightDp: Float, screenHeightDp: Float): Float =
        (screenHeightDp - plaqueHeightDp).coerceAtLeast(0f)
}
