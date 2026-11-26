package com.onyx.app.data.skill

/**
 * Le journal d'une démonstration : les gestes de l'utilisateur, réduits à ce qui se raconte.
 *
 * Ce fichier ne sait rien d'Android : ni `MotionEvent`, ni `Context`, ni vue. Le journal est ce
 * qu'un enregistreur d'écran peut remplir, et ce qu'un test peut écrire à la main — c'est la seule
 * raison pour laquelle la conversion en compétence se vérifie sans téléphone.
 *
 * Deux choix de forme méritent d'être dits :
 *
 * - **Un libellé est optionnel, jamais inventé.** L'accessibilité ne donne pas toujours un nom à ce
 *   qu'on touche (une icône sans description, un canevas, une zone d'image). Le journal dit alors
 *   `null`, et le brouillon le signalera — au lieu de fabriquer un nom plausible, qui ferait une
 *   compétence qui *paraît* juste et qui rejoue à côté.
 * - **Le secret accompagne le texte, il ne le remplace pas.** `isSecret` est un drapeau, pas une
 *   chaîne déjà masquée : l'enregistreur n'a pas à décider quoi jeter, et le convertisseur reste le
 *   seul endroit qui sait que ce texte ne doit jamais sortir.
 */

/** Une action de la démonstration, telle que le journal la retient. */
sealed interface TracedAction {

    /**
     * Un appui, en pixels de la fenêtre.
     *
     * [label] vient de l'accessibilité ; `null` quand la cible n'en a pas. Un libellé fait
     * d'espaces vaut un libellé absent — c'est le convertisseur qui tranche, ici on ne fait que
     * transporter ce que l'enregistreur a lu.
     */
    data class Tap(val x: Float, val y: Float, val label: String? = null) : TracedAction

    /**
     * Du texte saisi dans un champ.
     *
     * [isSecret] est vrai pour un mot de passe, un code à usage unique, un numéro de carte : le
     * brouillon n'en gardera **rien**, pas même la longueur.
     */
    data class Type(val text: String, val isSecret: Boolean = false) : TracedAction

    /**
     * Un défilement de [amount] pixels, dans [direction].
     *
     * [amount] est une **longueur**, pas un vecteur : le sens est porté par [direction], et un
     * montant négatif est lu comme sa valeur absolue. Un montant nul n'a rien fait défiler et
     * disparaît du brouillon.
     */
    data class Scroll(val direction: ScrollDirection, val amount: Int) : TracedAction

    /** Une adresse ouverte dans le navigateur de l'application. */
    data class Navigate(val url: String) : TracedAction

    /** Une attente, en millisecondes — le temps qu'un écran met à répondre. */
    data class Wait(val ms: Long) : TracedAction

    /** Une touche système : retour, accueil, entrée. */
    data class Key(val key: NamedKey) : TracedAction
}

/** Le sens d'un défilement. */
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

/** Les touches système qu'un journal peut contenir. */
enum class NamedKey { BACK, HOME, ENTER, ESCAPE }

/**
 * La démonstration entière : ses actions, la fenêtre où elles ont eu lieu, et le nom qu'on lui donne.
 *
 * [windowWidth] et [windowHeight] ne servent qu'à traduire un appui **sans libellé** en pourcentage
 * de la fenêtre. À zéro (taille inconnue), le brouillon ne calcule aucun pourcentage : mieux vaut
 * avouer « position inconnue » qu'écrire « 0% / 0% », qui désignerait le coin haut-gauche et ferait
 * cliquer à côté en silence.
 *
 * [title] est le nom donné à la démonstration. Absent, le brouillon en porte un par défaut : une
 * compétence sans titre ne se retrouve pas dans une liste.
 */
data class ActionJournal(
    val actions: List<TracedAction> = emptyList(),
    val windowWidth: Int = 0,
    val windowHeight: Int = 0,
    val title: String? = null,
)
