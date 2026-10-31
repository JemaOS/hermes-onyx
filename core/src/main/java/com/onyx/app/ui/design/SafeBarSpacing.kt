package com.onyx.app.ui.design

/**
 * Les marges d'une barre posée dans une fenêtre **bord à bord** — **l'ancien nom, gardé en alias**.
 *
 * L'app est bord à bord (`enableEdgeToEdge`) : ses barres se dessinent donc sous la barre d'état et
 * sous la barre de navigation, et c'est à **elles** de se décaler. Une valeur écrite en dur ne le
 * fait pas : 24 dp suffisent sur un téléphone en navigation par gestes et pas sur un appareil en
 * navigation à trois boutons (48 dp), ni sur un pliable déplié dont la barre système change de
 * hauteur. La règle est donc **calculée à partir des encarts du système**.
 *
 * Deux lots de la même vague ont posé cette règle chacun de son côté : ici (`SafeBarSpacing`, en
 * dp) et dans [SystemMargins] — plus complet, parce qu'il calcule aussi en pixels et fournit les
 * deux extensions de `Modifier` des lecteurs plein écran. Deux sources pour la même marge, c'est
 * une de trop : le jour où la marge nette change, un seul fichier doit être corrigé.
 *
 * Le calcul vit donc **dans [SystemMargins]**, et cet objet n'est plus qu'un nom : chacune de ses
 * fonctions appelle la sienne, sans arithmétique propre. Les écrans qui l'utilisent (`ChatScreen`,
 * `GroupChatScreen`, `GroupRoomScreen`, `ComposerStacking`…) n'ont pas bougé, et
 * `SafeBarSpacingTest` continue de verrouiller le résultat — c'est la preuve que l'alias ne change
 * rien au comportement.
 *
 * - [topMarginDp] : une barre du haut commence **sous** la barre d'état, sans marge ajoutée
 *   ([SystemMargins.barTopDp]) ;
 * - [bottomMarginDp] : une barre du bas commence **au-dessus** de la zone système (navigation ou
 *   geste), **plus** l'espace visuel de la maison — celui qui empêche la barre de paraître collée
 *   ([SystemMargins.barBottomDp]) ;
 * - [clearsSystemBottom] : le verdict, pour qu'un test puisse casser une barre qui mordrait sur la
 *   zone ([SystemMargins.clearsBand]).
 */
object SafeBarSpacing {

    /**
     * L'espace visuel demandé entre une barre du bas et la zone système, en dp.
     *
     * Il est **en plus** de l'encart : la barre ne se pose pas sur le bord de la zone, elle s'en
     * écarte. C'est la marge nette de [SystemMargins.ClearanceDp] : une seule constante dans l'app.
     */
    const val GapDp: Int = SystemMargins.ClearanceDp

    /**
     * La marge du **bas** d'une barre, en dp, mesurée depuis le bas de la fenêtre.
     *
     * [systemBottomInsetDp] est la zone système (barre de navigation, ou zone de geste) ; un encart
     * négatif — qui n'existe pas — ne rabote pas la marge.
     */
    fun bottomMarginDp(systemBottomInsetDp: Int, gapDp: Int = GapDp): Int =
        SystemMargins.barBottomDp(systemBottomInsetDp, gapDp)

    /**
     * La marge du **haut** d'une barre, en dp, mesurée depuis le haut de la fenêtre.
     *
     * La barre d'état d'abord : la barre se pose dessous, sans espace ajouté — le vide de la barre
     * d'état est déjà le sien.
     */
    fun topMarginDp(systemTopInsetDp: Int): Int = SystemMargins.barTopDp(systemTopInsetDp)

    /**
     * Vrai si le bas d'une barre — [barBottomDp] dp **au-dessus** du bas de la fenêtre — ne mord pas
     * sur la zone système. C'est le verrou : une barre collée au bord (`0`) est fausse dès que la
     * zone existe.
     */
    fun clearsSystemBottom(barBottomDp: Int, systemBottomInsetDp: Int): Boolean =
        SystemMargins.clearsBand(barBottomDp, systemBottomInsetDp)

    /** Vrai si le haut d'une barre — [barTopDp] dp sous le haut de la fenêtre — ne mord pas sur l'état. */
    fun clearsSystemTop(barTopDp: Int, systemTopInsetDp: Int): Boolean =
        SystemMargins.clearsBand(barTopDp, systemTopInsetDp)
}
