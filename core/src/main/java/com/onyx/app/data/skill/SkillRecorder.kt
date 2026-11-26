package com.onyx.app.data.skill

/**
 * **L'enregistreur d'une démonstration** : ce que l'utilisateur fait, horodaté, prêt pour le journal.
 *
 * Il ne connaît ni Compose, ni le pont, ni le réseau : l'écran « Ordinateur » lui passe les gestes
 * qu'il a déjà envoyés au PC, et ce fichier les range dans l'ordre. C'est ce qui permet de vérifier
 * la capture sans téléphone — la même raison qui a fait de [skillDraftMarkdown] une fonction pure.
 *
 * ### Ce qu'il n'enregistre pas, par construction
 *
 * - **Aucun pixel, aucun son.** Il n'a pas de champ pour une image ni pour un flux audio ; les
 *   gestes visuels purs (glisser, dessiner) ne sont pas des actions du journal (spec §11).
 * - **Aucun texte secret.** Un texte marqué secret est rangé comme
 *   `Type(text = "", isSecret = true)` : l'enregistreur ne garde pas la chaîne, pas même sa
 *   longueur. Le convertisseur en fait quatre puces, et le serveur reçoit `chars = 0` — voir
 *   [SkillJournal] pour la raison de ce zéro.
 * - **Aucun libellé inventé.** Le pont ne remonte pas le nom d'accessibilité de la cible : les
 *   appuis partent avec `label = null`, et le brouillon les marque « à relire : libellé absent ».
 *   C'est la règle du modèle ([TracedAction.Tap]) : mieux vaut une étape à relire qu'un nom
 *   plausible qui ferait cliquer à côté.
 *
 * ### Le temps
 *
 * Chaque action porte son `atMs`, mesuré depuis [start] avec l'horloge injectée. Deux gestes dans
 * la même milliseconde sont acceptés (le serveur ne refuse qu'un `at_ms` qui **recule**), donc
 * l'horodatage est borné vers le haut à la dernière valeur écrite : il ne peut pas reculer, même
 * avec une horloge qui saute.
 */
class SkillRecorder(private val clock: () -> Long = System::currentTimeMillis) {

    /** Une action et son instant, depuis l'ouverture de l'enregistrement. */
    data class Entry(val atMs: Long, val action: TracedAction)

    private val entries = mutableListOf<Entry>()

    private var startedAtMillis = 0L
    private var lastAtMs = 0L
    private var open = false

    /** Vrai entre [start] et [stop] : l'écran n'enregistre que dans cette fenêtre. */
    val recording: Boolean get() = open

    /** Le nombre d'actions retenues — ce que l'écran montre pendant la capture. */
    val count: Int get() = entries.size

    /** L'instant de l'ouverture, en millisecondes d'horloge murale ; 0 tant qu'on n'a pas commencé. */
    fun startedAt(): Long = startedAtMillis

    /**
     * Ouvre un enregistrement neuf et jette le précédent.
     *
     * Un journal qui traînerait d'une démonstration précédente ferait un skill qui mélange deux
     * gestes : on repart de zéro à chaque « Montrer une fois ».
     */
    fun start() {
        entries.clear()
        startedAtMillis = clock()
        lastAtMs = 0L
        open = true
    }

    /** Ferme l'enregistrement et rend sa durée en millisecondes. */
    fun stop(): Long {
        val duration = if (startedAtMillis == 0L) 0L else (clock() - startedAtMillis).coerceAtLeast(0L)
        open = false
        return duration
    }

    /** L'instant courant depuis l'ouverture, borné à la durée maximale d'un journal (600 s). */
    fun elapsedMs(): Long =
        if (startedAtMillis == 0L) 0L
        else (clock() - startedAtMillis).coerceIn(0L, MAX_DURATION_MS)

    /** Un appui, en pixels de l'écran du PC. [label] reste nul : le pont n'en donne pas. */
    fun tap(x: Float, y: Float, label: String? = null) {
        add(TracedAction.Tap(x = x, y = y, label = label))
    }

    /**
     * Une saisie. Un texte secret **n'est pas gardé** : seule l'existence de la saisie reste.
     *
     * Le drapeau vient de l'écran, jamais du texte : c'est l'utilisateur qui dit « ceci est un
     * mot de passe », et l'enregistreur ne décide pas à sa place.
     */
    fun type(text: String, secret: Boolean = false) {
        if (secret) add(TracedAction.Type(text = "", isSecret = true))
        else add(TracedAction.Type(text = text, isSecret = false))
    }

    /**
     * Un défilement, en **crans du pont**.
     *
     * Le journal du modèle porte une longueur ; le pont, lui, compte des crans (`ComputerWheel`).
     * On garde le nombre que le pont a réellement reçu, sans le convertir en pixels : une
     * conversion inventerait une mesure que personne n'a prise. Le montant est rangé en valeur
     * absolue, le sens vivant dans [ScrollDirection] (c'est la règle du modèle).
     */
    fun scroll(direction: ScrollDirection, amount: Int) {
        val magnitude = if (amount == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(amount)
        if (magnitude == 0) return
        add(TracedAction.Scroll(direction = direction, amount = magnitude))
    }

    /** Une touche système réellement demandée par l'utilisateur. */
    fun key(key: NamedKey) {
        add(TracedAction.Key(key))
    }

    /** Une adresse ouverte depuis l'application (jamais un chemin de fichier, spec §6). */
    fun navigate(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        add(TracedAction.Navigate(trimmed))
    }

    /** Les actions retenues, dans l'ordre — une copie, pour que l'appelant ne les modifie pas. */
    fun entries(): List<Entry> = entries.toList()

    /**
     * Le journal tel que le modèle le porte, avec la fenêtre qui traduit un appui muet en
     * pourcentage. [windowWidth] et [windowHeight] viennent du `hello` du pont ; à zéro, le
     * brouillon dira « position inconnue » au lieu d'un pourcentage faux.
     */
    fun journal(
        windowWidth: Int = 0,
        windowHeight: Int = 0,
        title: String? = null,
    ): ActionJournal = ActionJournal(
        actions = entries.map { it.action },
        windowWidth = windowWidth.coerceAtLeast(0),
        windowHeight = windowHeight.coerceAtLeast(0),
        title = title,
    )

    private fun add(action: TracedAction) {
        if (!open) return
        val at = elapsedMs().coerceAtLeast(lastAtMs)
        lastAtMs = at
        entries += Entry(at, action)
    }

    companion object {
        /** Le plafond du lot : dix minutes (spec §0, `ARCHITECTURE-TOP5.md:121`). */
        const val MAX_DURATION_MS: Long = 10 * 60 * 1000L
    }
}
