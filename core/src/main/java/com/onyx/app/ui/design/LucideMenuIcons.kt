package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import com.onyx.app.core.R

/**
 * **Les icônes du menu d'un bot** — le jeu de la référence (Lucide), repris **tel quel**.
 *
 * Le menu d'un bot de la référence (`shared/ui/.../bots/BotsRoster.kt:444-454`) prend ses neuf
 * icônes dans le jeu **Lucide** (`com.composables.icons.lucide`) : grille **24**, **un trait de 2**,
 * **bouts et jointures arrondis**. Les vector drawables de cet objet ont été **générés depuis la
 * géométrie Lucide elle-même** (`lucide-react` v0.577.0, `dist/esm/icons/<nom>.js` — le même jeu,
 * licences MIT/ISC), sans redessiner une courbe ; chaque fichier porte en tête la ligne de la
 * référence où l'icône sert, et le script de génération est dans le rapport du lot.
 *
 * **Pourquoi elles ne sont pas dans [Symbols].** Un symbole de la maison porte sa propre graisse et
 * ses propres proportions (famille dessinée à part). Un Lucide est un trait de 2 sur une grille de
 * 24 : les deux familles ne se mélangent pas dans une même rangée sans que ça se voie. Les neuf
 * entrées reprises de la référence forment donc **un bloc à la famille de la référence** ; les
 * entrées propres à Onyx, sous ce bloc, gardent leurs icônes de la maison.
 *
 * Les noms sont ceux de la référence (`Lucide.Pin`, `Lucide.PinOff`…), pour qu'un renvoi à la ligne
 * de la source se lise sans traduction.
 */
object LucideMenuIcons {

    /** « Épingler en haut » — `BotsRoster.kt:445`, la même icône que la marque de la carte (`:489`). */
    @DrawableRes val Pin = R.drawable.ic_lucide_pin

    /** « Désépingler » — `BotsRoster.kt:445` (`Lucide.PinOff`). */
    @DrawableRes val PinOff = R.drawable.ic_lucide_pin_off

    /** « Masquer » — `BotsRoster.kt:446` (`Lucide.EyeOff`). */
    @DrawableRes val EyeOff = R.drawable.ic_lucide_eye_off

    /** « Afficher » (bot déjà masqué) — `BotsRoster.kt:446` (`Lucide.Eye`). */
    @DrawableRes val Eye = R.drawable.ic_lucide_eye

    /** « Ouvrir la dernière session » — `BotsRoster.kt:447` (`Lucide.History`). */
    @DrawableRes val History = R.drawable.ic_lucide_history

    /** « Nouvelle conversation avec \<le nom\> » — `BotsRoster.kt:448` (`Lucide.MessageSquarePlus`). */
    @DrawableRes val MessageSquarePlus = R.drawable.ic_lucide_message_square_plus

    /** « Routines » — `BotsRoster.kt:449` (`Lucide.CalendarClock`). */
    @DrawableRes val CalendarClock = R.drawable.ic_lucide_calendar_clock

    /** « Repartir à zéro » — `BotsRoster.kt:450` (`Lucide.RotateCcw`). */
    @DrawableRes val RotateCcw = R.drawable.ic_lucide_rotate_ccw

    /** « Modifier » — `BotsRoster.kt:451` (`Lucide.Pencil`). */
    @DrawableRes val Pencil = R.drawable.ic_lucide_pencil

    /** « Dupliquer » — `BotsRoster.kt:452` (`Lucide.Copy`). */
    @DrawableRes val Copy = R.drawable.ic_lucide_copy

    /** « Supprimer » — `BotsRoster.kt:454` (`Lucide.Trash2`). */
    @DrawableRes val Trash2 = R.drawable.ic_lucide_trash_2
}
