package com.onyx.app.voice

/**
 * Ce qu'une écoute doit dire d'elle-même quand rien n'y arrive.
 *
 * Le défaut rapporté (« rien ne se passe quand je parle », l'écran figé sur « À L'ÉCOUTE ») a une
 * cause précise côté plomberie : le seul signal d'alerte était un **seuil de niveau du micro**
 * ([ENDPOINT_MINIMUM_LEVEL]). Or un moteur de reconnaissance qui livre des mesures de niveau sans
 * jamais rendre un mot — le cas rapporté — garde ce niveau au-dessus du seuil : l'alerte ne
 * s'allumait donc **jamais**, l'écran restait à l'écoute indéfiniment, et rien n'était écrit au
 * journal. Et rien ne vérifiait non plus que « À L'ÉCOUTE » voulait dire « le moteur a démarré ».
 *
 * La règle est donc réécrite sur les **faits** que l'app possède déjà, jamais sur le niveau :
 *
 *  - le moteur a-t-il **démarré** (un rappel du moteur est arrivé) ? Sinon, au bout de
 *    [ENGINE_START_GRACE_MS], on le dit — jamais un écran qui prétend écouter un moteur mort ;
 *  - des **trames** sont-elles arrivées ? Aucune au bout de [NO_SOUND_MS] : le micro ne donne rien ;
 *  - des **mots** sont-ils sortis ? Aucun au bout de [NO_WORDS_MS] alors que le moteur tourne et
 *    reçoit des trames : le moteur ne rend rien (langue d'écoute, moteur en ligne injoignable…).
 *
 * Pur : ni micro, ni horloge, ni Android — donc éprouvable sur JVM, et c'est la décision qui est
 * testée plutôt que son câblage.
 */
enum class ListenTrouble(val message: String?) {
    NONE(null),

    /**
     * Le moteur n'a jamais démarré : aucun rappel du moteur depuis le début de l'écoute. C'est le
     * « l'écran peut rester muet alors que le moteur n'a jamais démarré » — désormais impossible.
     */
    ENGINE_NEVER_STARTED(
        "The device's speech recogniser never answered. Tap the orb to listen again.",
    ),

    /**
     * Le moteur tourne, mais **aucune trame du micro** : le micro ne donne rien. Le message est
     * celui, déjà connu, de l'alerte « on n'entend rien » (il vit dans l'écran) — cette valeur ne
     * porte donc pas de texte à elle.
     */
    NO_FRAMES(null),

    /**
     * Le moteur tourne et reçoit des trames, mais **aucun mot** n'en sort : ce n'est plus le micro
     * qui est en cause, c'est le moteur. C'était la zone muette du défaut rapporté.
     */
    NO_WORDS(
        "The recogniser is running but finding no words. Tap the orb and try again.",
    ),
}

/** Le moteur laisse ce temps à un moteur de reconnaissance avant qu'on dise qu'il ne répond pas. */
const val ENGINE_START_GRACE_MS = 1_500L

/**
 * Sans **aucune** trame du micro, on le dit au bout des cinq secondes du seuil d'alerte : c'est le
 * même repère que [NO_SOUND_MS], pour ne pas dire deux choses différentes du même silence.
 */
const val NO_FRAMES_MS = 5_000L

/**
 * Avec des trames mais **aucun mot**, il faut laisser plus de temps : le moteur a reçu du son, il
 * est en train de décoder, et une alerte trop vive couperait une phrase lente. Huit secondes :
 * au-delà, ce n'est plus de la lenteur, c'est un moteur qui ne rend rien.
 */
const val NO_WORDS_MS = 8_000L

/**
 * La décision, pure : que faut-il dire à la personne pendant cette écoute ?
 *
 * [engineStarted] est la **preuve** que le moteur a répondu (un rappel du moteur est arrivé) — pas
 * l'état optimiste « je crois écouter » posé au départ. [frames] est le nombre de mesures de niveau
 * reçues, [heard] ce qui a déjà été reconnu, et [listeningForMs] le temps d'écoute **d'une personne**
 * (il survit aux reprises automatiques, comme le repère du silence).
 */
fun listenTrouble(
    phase: VoicePhase,
    heard: String,
    frames: Int,
    engineStarted: Boolean,
    listeningForMs: Long,
    engineGraceMs: Long = ENGINE_START_GRACE_MS,
    noFramesMs: Long = NO_FRAMES_MS,
    noWordsMs: Long = NO_WORDS_MS,
): ListenTrouble = when {
    // L'alerte n'existe qu'à l'écoute : en réflexion ou en lecture, le micro n'est pas en jeu.
    phase != VoicePhase.LISTENING -> ListenTrouble.NONE
    // Rien n'a démarré : on le dit, quelle que soit la suite.
    !engineStarted && listeningForMs >= engineGraceMs -> ListenTrouble.ENGINE_NEVER_STARTED
    // Des mots sont arrivés : le micro et le moteur fonctionnent, il n'y a plus rien à signaler.
    heard.isNotBlank() -> ListenTrouble.NONE
    // Le moteur tourne : pas de trames = le micro ne donne rien ; des trames sans mots = le moteur
    // ne rend rien. Deux causes différentes, deux messages.
    frames <= 0 -> if (listeningForMs >= noFramesMs) ListenTrouble.NO_FRAMES else ListenTrouble.NONE
    else -> if (listeningForMs >= noWordsMs) ListenTrouble.NO_WORDS else ListenTrouble.NONE
}

/**
 * **La ligne à transmettre** quand le moteur tourne sans rien entendre.
 *
 * Elle est **courte** (le journal de l'appareil est plafonné à [VoiceDiagnostics.MAX_LINES] lignes,
 * une minute de boucle), **orange** (le niveau [com.onyx.app.ui.settings.voiceLogLevel] la colore
 * en avertissement) et **cherchable** : « on n'entend rien » se cherche tel quel dans la page
 * Journaux → onglet Voix, et se met en tête.
 *
 * [frames] : les trames reçues **en tout** (moteur + micro de l'app). Zéro veut dire qu'aucun des
 * deux n'a rien reçu — c'est le cas qu'on veut lire quand la personne dit « il ne capte pas ma
 * voix ».
 */
internal fun silentListeningLine(frames: Int, engineStarted: Boolean): String =
    "voice: on n'entend rien (${NO_SOUND_MS / 1000} s, trames=$frames, " +
        "moteur=${if (engineStarted) "oui" else "non"})"

/**
 * Pourquoi le moteur n'a pas démarré : ce qu'on écrit au journal, en clair et sans secret, pour
 * qu'un moteur muet se lise sans deviner (moteur absent, langue, intention, appareil).
 */
internal fun engineStartReason(
    recognitionAvailable: Boolean,
    preferOffline: Boolean,
    localeTag: String,
    frames: Int,
): String = buildString {
    append(if (recognitionAvailable) "moteur déclaré par l'appareil" else "aucun moteur déclaré")
    append(", langue=$localeTag")
    append(if (preferOffline) ", hors ligne" else ", en ligne")
    append(", trames reçues=$frames")
}

/**
 * Le sous-titre du journal pour une panne d'écoute : une ligne par cause, jamais un code brut seul.
 * (Fonction pure de mise en forme ; le journal, lui, est borné et expurgé par [VoiceDiagnostics].)
 */
internal fun listenTroubleLogLine(trouble: ListenTrouble, seconds: Long, frames: Int, started: Boolean): String? =
    when (trouble) {
        ListenTrouble.NONE -> null
        ListenTrouble.ENGINE_NEVER_STARTED ->
            "voice: le moteur n'a jamais démarré après ${seconds} s (trames reçues=$frames, démarré=$started)"
        ListenTrouble.NO_FRAMES ->
            "voice: aucune trame du micro depuis ${seconds} s (moteur démarré=$started)"
        ListenTrouble.NO_WORDS ->
            "voice: le moteur reçoit des trames mais aucun mot depuis ${seconds} s (trames reçues=$frames)"
    }
