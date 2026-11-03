package com.onyx.app.data.skill

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La conversion d'une démonstration en brouillon de compétence : **une fonction pure**, sans réseau,
 * sans écran, sans horloge.
 *
 * Elle rend un Markdown **stable** : la même entrée donne la même sortie à l'octet près. Rien ici ne
 * dépend de la locale (aucun `String.format`, aucun nombre décimal composé par la machine), ni de la
 * date, ni de l'ordre d'un `Map`. C'est ce qui permet de comparer un brouillon à un fichier
 * attendu — et de voir, dans un diff, la seule ligne qui a changé quand une règle change.
 *
 * ### Ce que le brouillon ne fait pas
 *
 * Il ne devine pas. Pas de titre inventé à partir du premier bouton, pas de nom deviné pour une
 * icône muette, pas de texte secret recopié « pour la forme ». Chaque fois qu'il manque une
 * information, le pas est écrit quand même (jamais une étape muette, jamais une étape en moins) et
 * marqué **à relire**, avec la raison.
 *
 * ### Les cinq règles, et pourquoi elles sont là
 *
 * 1. Un appui nommé se lit par son nom ; un appui muet se lit par sa position, en pourcentage de la
 *    fenêtre, et se marque à relire.
 * 2. Un texte saisi se recopie entre guillemets, échappé pour tenir sur une ligne. Un texte secret
 *    devient quatre puces — **toujours quatre**, quelle que soit sa longueur : la longueur d'un mot
 *    de passe est déjà une information.
 * 3. Les attentes courtes consécutives sont fondues en une seule (seuil : [SHORT_WAIT_MS]). Un
 *    enregistrement réel contient des dizaines d'attentes de 100 à 300 ms ; sans fusion, la
 *    compétence serait quinze lignes de « wait », et la consigne utile serait noyée.
 * 4. Les défilements consécutifs de même sens se cumulent : un geste d'inertie arrive en morceaux,
 *    l'utilisateur, lui, a fait **un** défilement.
 * 5. Un journal vide — ou qui ne contient que des actions sans effet — produit un brouillon qui le
 *    dit, jamais un skill vide qu'on croirait généré.
 */

/**
 * Le seuil, en millisecondes, sous lequel une attente est « courte » et se fond dans ses voisines.
 *
 * Un demi-seconde est le temps qu'un écran met à se peindre ou qu'une requête met à revenir : en
 * dessous, l'attente décrit la machine, pas l'intention, et une suite de telles attentes n'est
 * qu'un seul « laisse charger ». Au-dessus, l'attente est un choix (attendre un envoi de courriel,
 * un décompte) et mérite sa propre étape.
 */
const val SHORT_WAIT_MS: Long = 500L

/** Le titre d'un brouillon dont la démonstration n'en portait pas. */
const val SKILL_DRAFT_DEFAULT_TITLE: String = "Démonstration enregistrée"

/** Le titre d'un brouillon dont le journal ne contient aucune action. */
const val SKILL_DRAFT_EMPTY_TITLE: String = "Démonstration vide"

/**
 * Ce qui remplace un texte secret : quatre puces, toujours quatre.
 *
 * Une puce par caractère dirait la longueur du mot de passe ; aucune puce ne dirait qu'il y avait
 * une saisie. Quatre est la seule valeur qui ne raconte que « il y avait un secret ici ».
 */
const val SKILL_DRAFT_SECRET: String = "••••"

/**
 * Le Markdown du brouillon : un titre, une phrase d'intention, un pas numéroté par action, et une
 * note finale pour les pas à relire.
 *
 * Le texte rendu se termine par **une** fin de ligne, comme un fichier texte : deux rendus du même
 * journal sont comparables octet pour octet, y compris au `diff`.
 */
fun skillDraftMarkdown(journal: ActionJournal): String {
    val steps = draftSteps(journal)
    val title = draftTitle(journal.title)
        ?: if (steps.isEmpty()) SKILL_DRAFT_EMPTY_TITLE else SKILL_DRAFT_DEFAULT_TITLE

    val markdown = StringBuilder()
    markdown.append("# ").append(title).append("\n\n")

    if (steps.isEmpty()) {
        markdown.append(
            "Aucune action n'a été enregistrée : il n'y a rien à rejouer, donc rien à transformer " +
                "en compétence.\n\n",
        )
        markdown.append("_Aucune étape._\n")
        return markdown.toString()
    }

    markdown.append(intention(steps.size)).append("\n\n")
    steps.forEachIndexed { index, step ->
        markdown.append(index + 1).append(". ").append(step.markdown()).append('\n')
    }

    val review = reviewLine(steps)
    if (review != null) markdown.append('\n').append(review).append('\n')
    return markdown.toString()
}

/** Un pas du brouillon : sa ligne, et la raison de le relire quand il y en a une. */
private data class DraftStep(val line: String, val review: String? = null) {

    /** La ligne telle qu'elle s'écrit dans la liste : la raison suit, sur la même ligne. */
    fun markdown(): String = if (review == null) line else "$line — à relire : $review"
}

/** Les pas du brouillon, une fois les attentes et les défilements fondus. */
private fun draftSteps(journal: ActionJournal): List<DraftStep> =
    mergeScrolls(mergeWaits(journal.actions)).map { action -> render(action, journal) }

/**
 * Fond les attentes courtes consécutives en une seule, et jette les attentes nulles.
 *
 * Une attente nulle n'est pas une instruction (« attends zéro milliseconde ») : c'est un artefact
 * d'enregistrement, et il n'a rien à faire dans une compétence.
 */
private fun mergeWaits(actions: List<TracedAction>): List<TracedAction> {
    val out = ArrayList<TracedAction>(actions.size)
    var shortMs = 0L
    var shortCount = 0

    fun flush() {
        if (shortCount > 0) out += TracedAction.Wait(shortMs)
        shortMs = 0L
        shortCount = 0
    }

    for (action in actions) {
        if (action !is TracedAction.Wait) {
            flush()
            out += action
            continue
        }
        val ms = action.ms.coerceAtLeast(0L)
        when {
            ms == 0L -> Unit
            ms <= SHORT_WAIT_MS -> {
                shortMs += ms
                shortCount++
            }
            else -> {
                flush()
                out += TracedAction.Wait(ms)
            }
        }
    }
    flush()
    return out
}

/**
 * Cumule les défilements **adjacents** de même sens, et jette ceux qui n'ont rien fait défiler.
 *
 * Adjacents au sens strict : une attente ou un appui entre deux défilements coupe la série. C'est
 * volontaire — la pause dit justement qu'on a regardé entre les deux, et la fondre effacerait une
 * intention que l'utilisateur a eue.
 */
private fun mergeScrolls(actions: List<TracedAction>): List<TracedAction> {
    val out = ArrayList<TracedAction>(actions.size)
    for (action in actions) {
        if (action is TracedAction.Scroll && magnitude(action.amount) == 0L) continue
        val scroll = action as? TracedAction.Scroll
        val previous = out.lastOrNull()
        if (scroll != null && previous is TracedAction.Scroll && previous.direction == scroll.direction) {
            val total = (magnitude(previous.amount) + magnitude(scroll.amount))
                .coerceAtMost(Int.MAX_VALUE.toLong())
            out[out.lastIndex] = TracedAction.Scroll(previous.direction, total.toInt())
        } else {
            out += action
        }
    }
    return out
}

/** La ligne d'un pas, et son éventuelle marque « à relire ». */
private fun render(action: TracedAction, journal: ActionJournal): DraftStep = when (action) {
    is TracedAction.Tap -> renderTap(action, journal)
    is TracedAction.Type -> renderType(action)
    is TracedAction.Scroll ->
        DraftStep("Scroll ${action.direction.word} ${magnitude(action.amount)} px")
    is TracedAction.Navigate -> renderNavigate(action)
    is TracedAction.Wait -> DraftStep("Wait ${duration(action.ms)}")
    is TracedAction.Key -> DraftStep("Press ${action.key.word}")
}

/** Un appui nommé se lit par son nom ; un appui muet par sa position, et il se relit. */
private fun renderTap(tap: TracedAction.Tap, journal: ActionJournal): DraftStep {
    val label = singleLine(tap.label)
    if (label.isNotEmpty()) return DraftStep("Tap \"${escape(label)}\"")

    val x = percentOf(tap.x, journal.windowWidth)
    val y = percentOf(tap.y, journal.windowHeight)
    if (x == null || y == null) {
        // Pas de fenêtre connue : aucun pourcentage ne serait honnête, pas même zéro.
        return DraftStep("Tap at an unknown position", "fenêtre de taille inconnue")
    }
    return DraftStep("Tap at $x% / $y% of the window", "libellé absent")
}

/** Un texte normal se recopie ; un texte secret ne laisse rien, pas même sa longueur. */
private fun renderType(type: TracedAction.Type): DraftStep {
    if (type.isSecret) return DraftStep("Type $SKILL_DRAFT_SECRET — secret, non enregistré")
    if (type.text.isEmpty()) return DraftStep("Type \"\"", "texte vide")
    return DraftStep("Type \"${escape(type.text)}\"")
}

/** Une navigation porte son adresse, ou avoue qu'elle n'en a pas. */
private fun renderNavigate(navigate: TracedAction.Navigate): DraftStep {
    val url = singleLine(navigate.url)
    if (url.isEmpty()) return DraftStep("Navigate to an unknown address", "adresse absente")
    return DraftStep("Navigate to $url")
}

/**
 * La durée d'une attente, lisible : en secondes quand elles sont rondes, en millisecondes sinon.
 *
 * « Wait 2000 ms » se lit mal ; « Wait 2 s » se lit. Mais arrondir 2500 ms à « 2,5 s » obligerait à
 * composer un décimal, donc à dépendre de la locale — d'où le retour aux millisecondes dès que la
 * seconde n'est pas entière.
 */
private fun duration(ms: Long): String =
    if (ms >= 1000L && ms % 1000L == 0L) "${ms / 1000L} s" else "$ms ms"

/** La phrase d'intention : ce que la compétence fera, sans promettre plus que le journal. */
private fun intention(count: Int): String = if (count == 1) {
    "Reproduire l'étape enregistrée, dans l'ordre où elle a été faite."
} else {
    "Reproduire les $count étapes enregistrées, dans l'ordre où elles ont été faites."
}

/**
 * La note finale : quels pas relire, dans l'ordre du brouillon.
 *
 * Elle existe pour qu'on puisse relire un brouillon **sans** le parcourir ligne à ligne : un
 * enregistrement de trente pas peut n'avoir qu'un appui muet, perdu au milieu.
 */
private fun reviewLine(steps: List<DraftStep>): String? {
    val numbers = steps.mapIndexedNotNull { index, step -> if (step.review == null) null else index + 1 }
    if (numbers.isEmpty()) return null
    val listed = if (numbers.size == 1) {
        "l'étape ${numbers.first()}"
    } else {
        "les étapes ${joinFrench(numbers)}"
    }
    return "> À relire : $listed."
}

/** « 3 », « 3 et 5 », « 3, 5 et 7 » : la conjonction française, sans virgule d'Oxford. */
private fun joinFrench(numbers: List<Int>): String =
    if (numbers.size <= 1) numbers.joinToString("") else {
        numbers.dropLast(1).joinToString(", ") + " et " + numbers.last()
    }

/** Le titre détouré : une seule ligne, sans dièse d'entrée qui doublerait celui du niveau 1. */
private fun draftTitle(title: String?): String? =
    singleLine(title).trimStart('#', ' ').takeIf { it.isNotEmpty() }

/**
 * La position en pourcentage de la fenêtre, ou `null` si elle ne peut pas être calculée.
 *
 * L'arrondi est celui de [roundToInt] (demi vers le haut) et le résultat est **borné à 0…100** : un
 * doigt qui sort de la fenêtre au bord ne doit pas produire « 103% », qui ne désigne rien.
 */
private fun percentOf(position: Float, span: Int): Int? {
    if (span <= 0) return null
    val ratio = position.toDouble() / span * 100.0
    if (!ratio.isFinite()) return null
    return ratio.roundToInt().coerceIn(0, 100)
}

/** La longueur d'un défilement, jamais un signe : le sens est déjà dit par la direction. */
private fun magnitude(amount: Int): Long = abs(amount.toLong())

/**
 * Le texte tel qu'il peut tenir sur **une** ligne d'un pas numéroté.
 *
 * Un libellé d'accessibilité peut contenir un retour à la ligne, un guillemet, une barre oblique
 * inverse. Recopiés tels quels, ils feraient une ligne vide au milieu de la liste — le pas suivant
 * perdrait son numéro — ou une chaîne qu'on ne peut plus relire. On les échappe plutôt que de les
 * supprimer : le texte reste celui qui a été saisi.
 */
private fun escape(text: String): String = buildString(text.length) {
    for (ch in text) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

/**
 * Le texte ramené à une ligne : toute suite d'espaces devient une espace, les bords sont détourés.
 *
 * `null` et un texte fait d'espaces donnent la même chose — la chaîne vide — parce que
 * l'accessibilité rend parfois `" "` là où elle n'a rien à dire, et que ce n'est pas un libellé.
 */
private fun singleLine(text: String?): String {
    if (text == null) return ""
    val builder = StringBuilder(text.length)
    var pendingSpace = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            pendingSpace = true
            continue
        }
        if (pendingSpace && builder.isNotEmpty()) builder.append(' ')
        pendingSpace = false
        builder.append(ch)
    }
    return builder.toString()
}

/** Le mot anglais d'un sens de défilement, tel qu'il s'écrit dans un pas. */
private val ScrollDirection.word: String
    get() = when (this) {
        ScrollDirection.UP -> "up"
        ScrollDirection.DOWN -> "down"
        ScrollDirection.LEFT -> "left"
        ScrollDirection.RIGHT -> "right"
    }

/** Le nom d'une touche système, tel qu'il s'écrit dans un pas. */
private val NamedKey.word: String
    get() = when (this) {
        NamedKey.BACK -> "Back"
        NamedKey.HOME -> "Home"
        NamedKey.ENTER -> "Enter"
    }
