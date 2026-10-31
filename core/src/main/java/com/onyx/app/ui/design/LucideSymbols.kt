package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import com.onyx.app.core.R

/**
 * **Le jeu d'icônes de la référence** — Lucide, repris **tel quel**.
 *
 * Le propriétaire a dénoncé deux choses sur les feuilles : des tuiles « blanches à glyphe noir
 * épais », et des **icônes qui ne sont pas celles de la référence**. Les deux se corrigent ensemble :
 * la source (`/home/john/herald`, Compose Multiplatform) emploie le jeu **Lucide**
 * (`com.composables.icons.lucide`), une grille de 24, **un trait unique de 2**, des **bouts et
 * jointures arrondis** — et ses feuilles prennent leurs icônes dans ce jeu :
 *
 *  - `shared/ui/.../chat/AttachmentViews.kt:176-178` : « Photos » → `Lucide.Image`,
 *    « Camera » → `Lucide.Camera`, « Files » → `Lucide.Paperclip` ;
 *  - `shared/designsystem/.../components/BottomSheet.kt:163-197` : la **rangée de feuille** porte son
 *    icône dans une tuile de 34 dp, à **18 dp** — trait fin, jamais un glyphe gras.
 *
 * Les vector drawables de [Lucide] ont donc été **générés depuis la géométrie Lucide elle-même**
 * (`lucide-react` v0.577.0, `dist/esm/icons/<nom>.js` — le même jeu, licences MIT/ISC), sans
 * redessiner une seule courbe : le script et la table « icône → source » sont dans le rapport.
 *
 * Ce qui change par rapport aux icônes SF de la maison (`[Symbols]`) : la **famille de traits**. Un
 * SF Symbol porte sa propre graisse et ses propres proportions ; un Lucide est un trait de 2 sur une
 * grille de 24, à bouts ronds. Les deux ne se mélangent pas dans une même rangée — c'est ce qui se
 * lit comme « les icônes ne sont pas celles de la référence ».
 *
 * **Ce qui manque, et qui est dit** : la référence n'a **pas** de rangée « Coller » (elle n'offre que
 * Photos, Camera, Files). Notre rangée garde donc `Lucide.Clipboard`, l'icône du jeu qui dit
 * exactement ce geste — aucune forme n'a été inventée.
 */
object Lucide {

    /** « Photos » de la référence — `image.js`, un cadre à coins ronds, un disque, une pente. */
    @DrawableRes val Image = R.drawable.ic_lucide_image

    /** « Camera ». */
    @DrawableRes val Camera = R.drawable.ic_lucide_camera

    /** « Files » de la référence — la **trombone**, pas un dossier. */
    @DrawableRes val Paperclip = R.drawable.ic_lucide_paperclip

    /** Notre rangée « Coller » — **aucun équivalent** dans la feuille de la référence. */
    @DrawableRes val Clipboard = R.drawable.ic_lucide_clipboard

    /** La langue (le globe) — icône de sujet de la rangée. */
    @DrawableRes val Globe = R.drawable.ic_lucide_globe

    /** Réglages. */
    @DrawableRes val Settings = R.drawable.ic_lucide_settings

    /** Actualiser — le cercle fléché de `ScheduledPage.kt:206`. */
    @DrawableRes val RefreshCw = R.drawable.ic_lucide_refresh_cw

    /** Réessayer la connexion — les ondes d'une tentative. */
    @DrawableRes val Radio = R.drawable.ic_lucide_radio

    /** Une action qui ne se rattrape pas. */
    @DrawableRes val CircleX = R.drawable.ic_lucide_circle_x

    /** Une passerelle. */
    @DrawableRes val Server = R.drawable.ic_lucide_server

    /** La coche d'un choix retenu (contrôle de droite d'une rangée). */
    @DrawableRes val Check = R.drawable.ic_lucide_check

    /** Le chevron d'une rangée qui ouvre une page. */
    @DrawableRes val ChevronRight = R.drawable.ic_lucide_chevron_right

    /** La flèche de retour — la seule icône de « retour » de la source (`SessionsSidebar.kt:1097`). */
    @DrawableRes val ArrowLeft = R.drawable.ic_lucide_arrow_left

    /** Créer. */
    @DrawableRes val Plus = R.drawable.ic_lucide_plus

    /** Écrire, modifier. */
    @DrawableRes val Pencil = R.drawable.ic_lucide_pencil

    /** Supprimer. */
    @DrawableRes val Trash2 = R.drawable.ic_lucide_trash_2

    /** L'exécution immédiate (« ⚡ Run now », `ScheduledPage.kt:378`). */
    @DrawableRes val Zap = R.drawable.ic_lucide_zap

    /** Reprendre. */
    @DrawableRes val Play = R.drawable.ic_lucide_play

    /** Mettre en pause. */
    @DrawableRes val Pause = R.drawable.ic_lucide_pause

    /** Effacer le contenu d'un champ. */
    @DrawableRes val X = R.drawable.ic_lucide_x

    /** Chercher — la loupe d'un champ de recherche. */
    @DrawableRes val Search = R.drawable.ic_lucide_search

    // ---- Les icônes des écrans de contenu -------------------------------------------------------

    /** Une compétence — l'étincelle de « ce que l'agent sait faire ». */
    @DrawableRes val Sparkles = R.drawable.ic_lucide_sparkles

    /** Un jeu d'outils — la clé à molette. */
    @DrawableRes val Wrench = R.drawable.ic_lucide_wrench

    /** Une source MCP — la prise. */
    @DrawableRes val Plug = R.drawable.ic_lucide_plug

    /** La branche d'une session de code. */
    @DrawableRes val GitBranch = R.drawable.ic_lucide_git_branch

    /** Le terminal. */
    @DrawableRes val Terminal = R.drawable.ic_lucide_terminal

    /** Un dossier. */
    @DrawableRes val Folder = R.drawable.ic_lucide_folder

    /** Un fichier. */
    @DrawableRes val File = R.drawable.ic_lucide_file

    /** Un tableau kanban. */
    @DrawableRes val SquareKanban = R.drawable.ic_lucide_square_kanban

    /** Le code. */
    @DrawableRes val Code = R.drawable.ic_lucide_code

    /** Un échec — le triangle d'alerte de `ScheduledPage.kt:315`. */
    @DrawableRes val TriangleAlert = R.drawable.ic_lucide_triangle_alert

    /** Un fait accompli. */
    @DrawableRes val CircleCheck = R.drawable.ic_lucide_circle_check

    /** L'échéancier — `ScheduledPage.kt:210`. */
    @DrawableRes val CalendarClock = R.drawable.ic_lucide_calendar_clock

    /** Une liste de tâches. */
    @DrawableRes val ListTodo = R.drawable.ic_lucide_list_todo

    /** Un identifiant, une étiquette. */
    @DrawableRes val Hash = R.drawable.ic_lucide_hash

    /** Une exécution — un fil, `ScheduledPage.kt:414`. */
    @DrawableRes val MessageSquare = R.drawable.ic_lucide_message_square

    /** Écrire — l'icône de la pilule « New chat » de la source (`SessionsSidebar.kt:1270`). */
    @DrawableRes val SquarePen = R.drawable.ic_lucide_square_pen

    /** Une conversation — la **bulle** qui dit « Chat » (l'onglet portait une icône de réception). */
    @DrawableRes val MessagesSquare = R.drawable.ic_lucide_messages_square

    /** Un flux — ce que dit l'onglet « Flux ». */
    @DrawableRes val Rss = R.drawable.ic_lucide_rss

    /** Une idée — l'ampoule, jamais une étoile décorative. */
    @DrawableRes val Lightbulb = R.drawable.ic_lucide_lightbulb

    /** Un objectif — la cible. */
    @DrawableRes val Target = R.drawable.ic_lucide_target

    /** Toutes les entrées, pour qu'un test puisse vérifier que chacune vient de la source. */
    val all: List<Pair<String, Int>> = listOf(
        "Image" to Image,
        "Camera" to Camera,
        "Paperclip" to Paperclip,
        "Clipboard" to Clipboard,
        "Globe" to Globe,
        "Settings" to Settings,
        "RefreshCw" to RefreshCw,
        "Radio" to Radio,
        "CircleX" to CircleX,
        "Server" to Server,
        "Check" to Check,
        "ChevronRight" to ChevronRight,
        "ArrowLeft" to ArrowLeft,
        "Plus" to Plus,
        "Pencil" to Pencil,
        "Trash2" to Trash2,
        "Zap" to Zap,
        "Play" to Play,
        "Pause" to Pause,
        "X" to X,
        "Search" to Search,
        "Sparkles" to Sparkles,
        "Wrench" to Wrench,
        "Plug" to Plug,
        "GitBranch" to GitBranch,
        "Terminal" to Terminal,
        "Folder" to Folder,
        "File" to File,
        "SquareKanban" to SquareKanban,
        "Code" to Code,
        "TriangleAlert" to TriangleAlert,
        "CircleCheck" to CircleCheck,
        "CalendarClock" to CalendarClock,
        "ListTodo" to ListTodo,
        "Hash" to Hash,
        "MessageSquare" to MessageSquare,
        "SquarePen" to SquarePen,
        "MessagesSquare" to MessagesSquare,
        "Rss" to Rss,
        "Lightbulb" to Lightbulb,
        "Target" to Target,
    )
}
