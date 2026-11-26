package com.onyx.app.localization

import java.util.Locale

/**
 * La langue de l'interface : celle d'Android (« Système », le défaut et le comportement d'avant),
 * ou celle que la personne a choisie dans Réglages.
 *
 * Le choix vit **ici**, en mémoire du processus, et ne touche jamais `Locale.getDefault()` : la
 * langue d'Android — donc celle du micro, de la lecture, des nombres du système et des autres
 * applications — reste intacte. Tous les points de l'app qui affichent du texte ou forment une date
 * lisent [preferred]. Quand le choix est « Système », [preferred] vaut exactement
 * `Locale.getDefault()`, si bien que ce chemin ne change pas d'un iota.
 */
object OnyxLanguage {

    /** Suit la langue d'Android. Le défaut, et le comportement d'avant ce réglage. */
    const val SYSTEM = "system"

    /** Français, quelle que soit la langue d'Android. */
    const val FRENCH = "fr"

    /** Anglais, quelle que soit la langue d'Android. */
    const val ENGLISH = "en"

    /** Les trois choix offerts, dans l'ordre où la feuille les montre. */
    val choices = listOf(SYSTEM, FRENCH, ENGLISH)

    /** Le choix enregistré ; « Système » tant qu'aucun autre n'a été fait. */
    @Volatile
    var choice: String = SYSTEM
        private set

    /** Un choix absent ou inconnu retombe sur « Système » : jamais de langue tirée au hasard. */
    fun normalize(raw: String?): String = if (raw != null && raw in choices) raw else SYSTEM

    /**
     * La langue effective : le choix explicite, sinon celle d'Android — relue **à chaque appel**
     * (et non mémorisée au démarrage), comme `OnyxLocalization` le fait déjà.
     */
    val preferred: Locale
        get() = when (choice) {
            FRENCH -> Locale.FRENCH
            ENGLISH -> Locale.ENGLISH
            else -> Locale.getDefault()
        }

    /**
     * Ce que la rangée de Réglages et la feuille montrent pour un choix.
     *
     * Les deux langues s'écrivent **dans leur propre langue** (un endonyme reste reconnaissable
     * quelle que soit celle de l'interface : on retrouve toujours la sienne) ; « System » passe par
     * le dictionnaire, donc « Système » en français.
     */
    fun label(choice: String = this.choice): String = when (normalize(choice)) {
        FRENCH -> "Français"
        ENGLISH -> "English"
        else -> "System"
    }

    /** Applique un choix au processus. Sans effet sur la langue d'Android ni sur les autres apps. */
    fun apply(raw: String?) {
        choice = normalize(raw)
    }
}
