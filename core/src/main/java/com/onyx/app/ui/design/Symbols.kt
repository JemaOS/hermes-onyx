package com.onyx.app.ui.design

import androidx.annotation.DrawableRes
import com.onyx.app.core.R

/**
 * Les vector drawables des icônes de l'app, plus ceux que
 * l'écran de chat, le composer et les réglages utilisent.
 *
 * Même vocabulaire que: `Symbols.ArrowDown` répond à `Image(systemName: "arrow.down")`.
 */
object Symbols {
    @DrawableRes val ArrowDown = R.drawable.ic_sym_arrow_down
    @DrawableRes val ArrowUp = R.drawable.ic_sym_arrow_up
    /** `arrow.up.arrow.down`: « Reorder Bots ». */
    @DrawableRes val ArrowUpArrowDown = R.drawable.ic_sym_arrow_up_arrow_down
    @DrawableRes val ArrowUpDoc = R.drawable.ic_sym_arrow_up_doc
    @DrawableRes val ArrowUpCircle = R.drawable.ic_sym_arrow_up_circle
    @DrawableRes val ArrowClockwise = R.drawable.ic_sym_arrow_clockwise
    @DrawableRes val DotRadiowavesLeftAndRight = R.drawable.ic_sym_dot_radiowaves_left_and_right
    @DrawableRes val ArrowCounterclockwise = R.drawable.ic_sym_arrow_counterclockwise
    @DrawableRes val Expand = R.drawable.ic_sym_arrow_up_left_and_arrow_down_right
    @DrawableRes val ArrowTurnDownRight = R.drawable.ic_sym_arrow_turn_down_right
    /** `arrow.triangle.turn.up.right.diamond.fill`: « Directions » de la carte d'une réponse. */
    @DrawableRes val ArrowTriangleTurnUpRightDiamond = R.drawable.ic_sym_arrow_triangle_turn_up_right_diamond_fill
    @DrawableRes val ArrowRightSquare = R.drawable.ic_sym_arrow_right_square
    @DrawableRes val Bell = R.drawable.ic_sym_bell
    @DrawableRes val BellSlash = R.drawable.ic_sym_bell_slash
    @DrawableRes val BellSlashFill = R.drawable.ic_sym_bell_slash_fill
    @DrawableRes val Bicycle = R.drawable.ic_sym_bicycle
    @DrawableRes val Binoculars = R.drawable.ic_sym_binoculars_fill
    @DrawableRes val Bolt = R.drawable.ic_sym_bolt_fill
    @DrawableRes val BoltOutline = R.drawable.ic_sym_bolt
    @DrawableRes val Brain = R.drawable.ic_sym_brain
    @DrawableRes val Bubbles = R.drawable.ic_sym_bubble_left_and_text_bubble_right
    @DrawableRes val Calendar = R.drawable.ic_sym_calendar
    @DrawableRes val Camera = R.drawable.ic_sym_camera
    @DrawableRes val CameraFill = R.drawable.ic_sym_camera_fill
    /** `car.fill`: une carte de réponse dont le chemin se fait en voiture. */
    @DrawableRes val CarFill = R.drawable.ic_sym_car_fill
    @DrawableRes val Checkmark = R.drawable.ic_sym_checkmark
    @DrawableRes val CheckmarkCircle = R.drawable.ic_sym_checkmark_circle_fill
    /** `chart.bar`: la ligne « Usage & cost » du menu d'une conversation. */
    @DrawableRes val ChartBar = R.drawable.ic_sym_chart_bar
    @DrawableRes val CheckmarkCircleOutline = R.drawable.ic_sym_checkmark_circle
    /** `checkmark.message`: « Mark as Read » du menu d'une conversation. */
    @DrawableRes val CheckmarkMessage = R.drawable.ic_sym_checkmark_message
    @DrawableRes val ChevronDown = R.drawable.ic_sym_chevron_down
    @DrawableRes val ChevronRight = R.drawable.ic_sym_chevron_right
    @DrawableRes val ChevronUp = R.drawable.ic_sym_chevron_up
    @DrawableRes val ChevronUpDown = R.drawable.ic_sym_chevron_up_chevron_down
    @DrawableRes val Circle = R.drawable.ic_sym_circle
    @DrawableRes val CircleFill = R.drawable.ic_sym_circle_fill
    @DrawableRes val Clock = R.drawable.ic_sym_clock
    @DrawableRes val ClockBadgeQuestionmark = R.drawable.ic_sym_clock_badge_questionmark
    /** `cloud.fill`: le temps qu'il fait sur une carte de réponse. */
    @DrawableRes val Cloud = R.drawable.ic_sym_cloud_fill
    @DrawableRes val CloudBoltRain = R.drawable.ic_sym_cloud_bolt_rain_fill
    @DrawableRes val CloudDrizzle = R.drawable.ic_sym_cloud_drizzle_fill
    @DrawableRes val CloudFog = R.drawable.ic_sym_cloud_fog_fill
    @DrawableRes val CloudHail = R.drawable.ic_sym_cloud_hail_fill
    @DrawableRes val CloudHeavyRain = R.drawable.ic_sym_cloud_heavyrain_fill
    @DrawableRes val CloudMoon = R.drawable.ic_sym_cloud_moon_fill
    @DrawableRes val CloudRain = R.drawable.ic_sym_cloud_rain_fill
    @DrawableRes val CloudSleet = R.drawable.ic_sym_cloud_sleet_fill
    @DrawableRes val CloudSnow = R.drawable.ic_sym_cloud_snow_fill
    @DrawableRes val CloudSun = R.drawable.ic_sym_cloud_sun_fill
    @DrawableRes val DesktopComputer = R.drawable.ic_sym_desktopcomputer
    @DrawableRes val Doc = R.drawable.ic_sym_doc
    @DrawableRes val DocOnDoc = R.drawable.ic_sym_doc_on_doc
    @DrawableRes val Ellipsis = R.drawable.ic_sym_ellipsis
    /** `envelope.open`: « Mark as Read » du menu d'une tuile de bot et d'une salle. */
    @DrawableRes val EnvelopeOpen = R.drawable.ic_sym_envelope_open
    /** `envelope.badge`: « Mark as Unread » du menu d'une tuile de bot et d'une salle. */
    @DrawableRes val EnvelopeBadge = R.drawable.ic_sym_envelope_badge
    @DrawableRes val ExclamationBubble = R.drawable.ic_sym_exclamationmark_bubble
    @DrawableRes val ExclamationCircle = R.drawable.ic_sym_exclamationmark_circle_fill
    @DrawableRes val ExclamationCircleOutline = R.drawable.ic_sym_exclamationmark_circle
    @DrawableRes val ExclamationTriangle = R.drawable.ic_sym_exclamationmark_triangle
    @DrawableRes val ExclamationTriangleFill = R.drawable.ic_sym_exclamationmark_triangle_fill
    @DrawableRes val Eye = R.drawable.ic_sym_eye
    @DrawableRes val EyeSlash = R.drawable.ic_sym_eye_slash
    /** `figure.walk`: une carte de réponse dont le chemin se fait à pied. */
    @DrawableRes val FigureWalk = R.drawable.ic_sym_figure_walk
    /** `flag.fill`: l'arrivée d'un chemin, sur la carte d'une réponse. */
    @DrawableRes val FlagFill = R.drawable.ic_sym_flag_fill
    /** `face.smiling`: « Change Face… » du menu d'une tuile de bot. */
    @DrawableRes val FaceSmiling = R.drawable.ic_sym_face_smiling
    @DrawableRes val HandRaised = R.drawable.ic_sym_hand_raised
    @DrawableRes val ArchiveBox = R.drawable.ic_sym_archivebox
    @DrawableRes val Folder = R.drawable.ic_sym_folder
    @DrawableRes val FolderBadgePlus = R.drawable.ic_sym_folder_badge_plus
    @DrawableRes val FolderFill = R.drawable.ic_sym_folder_fill
    @DrawableRes val Gearshape = R.drawable.ic_sym_gearshape_2
    /** `gearshape`: une seule roue — le bouton « Settings » de la liste des conversations. */
    @DrawableRes val GearshapeSingle = R.drawable.ic_sym_gearshape
    @DrawableRes val Globe = R.drawable.ic_sym_globe
    @DrawableRes val Heart = R.drawable.ic_sym_heart
    @DrawableRes val HeartFill = R.drawable.ic_sym_heart_fill
    /** `house.fill`: le dossier personnel d'un projet, en orange dans la liste. */
    @DrawableRes val HouseFill = R.drawable.ic_sym_house_fill
    @DrawableRes val Iphone = R.drawable.ic_sym_phone
    @DrawableRes val Key = R.drawable.ic_sym_key_fill

    /** `key` en contour: les rangées de Réglages, où un pictogramme plein faisait une tache. */
    @DrawableRes val KeyOutline = R.drawable.ic_sym_key
    @DrawableRes val Keyboard = R.drawable.ic_sym_keyboard
    @DrawableRes val LargeCircleFillCircle = R.drawable.ic_sym_largecircle_fill_circle
    @DrawableRes val Link = R.drawable.ic_sym_link
    @DrawableRes val ListBullet = R.drawable.ic_sym_list_bullet
    /** `line.3.horizontal`: la poignée d'une ligne qu'on glisse pour la mettre en ordre. */
    @DrawableRes val Line3Horizontal = R.drawable.ic_sym_line_3_horizontal
    @DrawableRes val Lock = R.drawable.ic_sym_lock_fill
    /**
     * `lock` en contour: l'écran de verrouillage dessine le cadenas au trait, , et non
     * plein (`lock.fill`).
     */
    @DrawableRes val LockOutline = R.drawable.ic_sym_lock
    @DrawableRes val LockShield = R.drawable.ic_sym_lock_shield
    @DrawableRes val MagnifyingGlass = R.drawable.ic_sym_magnifyingglass
    @DrawableRes val Mic = R.drawable.ic_sym_mic_fill
    @DrawableRes val MicOutline = R.drawable.ic_sym_mic
    @DrawableRes val MinusCircle = R.drawable.ic_sym_minus_circle_fill
    /** `message.badge`: « Mark as Unread » du menu d'une conversation. */
    @DrawableRes val MessageBadge = R.drawable.ic_sym_message_badge
    @DrawableRes val MoonFill = R.drawable.ic_sym_moon_fill
    @DrawableRes val Paintbrush = R.drawable.ic_sym_paintbrush_fill
    @DrawableRes val PaintbrushOutline = R.drawable.ic_sym_paintbrush
    @DrawableRes val Paperclip = R.drawable.ic_sym_paperclip
    @DrawableRes val Pause = R.drawable.ic_sym_pause_fill
    @DrawableRes val PauseOutline = R.drawable.ic_sym_pause
    @DrawableRes val PauseCircle = R.drawable.ic_sym_pause_circle
    @DrawableRes val Pencil = R.drawable.ic_sym_pencil
    @DrawableRes val Person2 = R.drawable.ic_sym_person_2_fill
    @DrawableRes val Person = R.drawable.ic_sym_person
    @DrawableRes val Person3 = R.drawable.ic_sym_person_3
    @DrawableRes val PersonCircle = R.drawable.ic_sym_person_crop_circle
    @DrawableRes val Photo = R.drawable.ic_sym_photo
    @DrawableRes val PhotoOnRectangle = R.drawable.ic_sym_photo_on_rectangle
    @DrawableRes val Pin = R.drawable.ic_sym_pin_fill
    @DrawableRes val Play = R.drawable.ic_sym_play_fill
    @DrawableRes val PlayOutline = R.drawable.ic_sym_play
    @DrawableRes val PlayCircle = R.drawable.ic_sym_play_circle
    @DrawableRes val Film = R.drawable.ic_sym_film
    @DrawableRes val Plus = R.drawable.ic_sym_plus
    @DrawableRes val PlusBubble = R.drawable.ic_sym_plus_bubble
    @DrawableRes val PlusCircle = R.drawable.ic_sym_plus_circle_fill
    @DrawableRes val QrCode = R.drawable.ic_sym_qrcode
    @DrawableRes val QrCodeViewfinder = R.drawable.ic_sym_qrcode_viewfinder
    @DrawableRes val QuoteBubble = R.drawable.ic_sym_quote_bubble
    @DrawableRes val RectangleSplit = R.drawable.ic_sym_rectangle_split_3x1
    /** `safari`: « Open » du menu d'une fiche de lien (une boussole). */
    @DrawableRes val Safari = R.drawable.ic_sym_safari
    @DrawableRes val ServerRack = R.drawable.ic_sym_server_rack
    /** `square.stack.3d.up`: « All Servers » de la liste des conversations. */
    @DrawableRes val SquareStack3dUp = R.drawable.ic_sym_square_stack_3d_up
    @DrawableRes val Share = R.drawable.ic_sym_share
    /** `sidebar.left`: replier ou déplier le volet des deux panneaux (barre d'téléphone). */
    @DrawableRes val SidebarLeft = R.drawable.ic_sym_sidebar_left
    @DrawableRes val Sparkles = R.drawable.ic_sym_sparkles
    @DrawableRes val SpeakerWave = R.drawable.ic_sym_speaker_wave_2
    @DrawableRes val SquareAndPencil = R.drawable.ic_sym_square_and_pencil
    @DrawableRes val Stop = R.drawable.ic_sym_stop_fill
    @DrawableRes val SunMax = R.drawable.ic_sym_sun_max_fill
    @DrawableRes val Terminal = R.drawable.ic_sym_terminal
    @DrawableRes val TextBubble = R.drawable.ic_sym_text_bubble
    @DrawableRes val TextCursor = R.drawable.ic_sym_text_cursor
    @DrawableRes val SendAfter = R.drawable.ic_sym_text_line_last_and_arrowtriangle_forward
    /** `slider.horizontal.3`: « Edit Bot… » du menu d'une tuile de bot. */
    @DrawableRes val SliderHorizontal3 = R.drawable.ic_sym_slider_horizontal_3
    @DrawableRes val Trash = R.drawable.ic_sym_trash
    @DrawableRes val Tray = R.drawable.ic_sym_tray
    @DrawableRes val Waveform = R.drawable.ic_sym_waveform
    @DrawableRes val WifiExclamation = R.drawable.ic_sym_wifi_exclamationmark
    @DrawableRes val WrenchAndScrewdriver = R.drawable.ic_sym_wrench_and_screwdriver
    @DrawableRes val Xmark = R.drawable.ic_sym_xmark
    @DrawableRes val XmarkCircle = R.drawable.ic_sym_xmark_circle_fill
    @DrawableRes val XmarkOctagonFill = R.drawable.ic_sym_xmark_octagon_fill

    // ---- Les cartes d'une réponse (`onyx_show`): le reste de leurs symboles ----

    /** `location.fill`: la punaise pleine d'un lieu, sur une carte. */
    @DrawableRes val LocationFill = R.drawable.ic_sym_location_fill

    /** `map`: « Open in Maps », sous la carte d'une réponse. */
    @DrawableRes val Map = R.drawable.ic_sym_map

    /** `phone.fill`: appeler un lieu trouvé. */
    @DrawableRes val PhoneFill = R.drawable.ic_sym_phone_fill

    /** `thermometer.medium`: la condition « inconnue », faute de mieux. */
    @DrawableRes val ThermometerMedium = R.drawable.ic_sym_thermometer_medium

    /** `tram.fill`: une carte de réponse dont le chemin se fait en transport. */
    @DrawableRes val TramFill = R.drawable.ic_sym_tram_fill

    /** `wind`: la condition « venteux ». */
    @DrawableRes val Wind = R.drawable.ic_sym_wind
}
