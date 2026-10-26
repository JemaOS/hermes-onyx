package com.onyx.app.localization

import java.util.Locale

/**
 * Libellés de l'interface Onyx.
 *
 * Les clés sont les chaînes anglaises déjà présentes dans le code. La locale par défaut est lue à
 * chaque appel (et non mémorisée au démarrage): un changement de langue suivi d'une recomposition
 * ou d'une recréation d'activité est donc pris en compte. Une clé inconnue est toujours rendue
 * telle quelle, ce qui préserve les réponses du serveur et les identifiants.
 */
object OnyxLocalization {
    /** Anglais explicite pour les termes techniques qui n'ont pas besoin de traduction française. */
    private val english = mapOf(
        "API" to "API",
        "Onyx" to "Onyx",
        "Hermes" to "Hermes",
    )

    private val criticalFrench = linkedMapOf(
        // Écran de configuration.
        "Set up Onyx" to "Configurer Onyx",
        "Welcome to Onyx" to "Bienvenue dans Onyx",
        "Scan a setup code" to "Scanner un code de configuration",
        "Test connection" to "Tester la connexion",
        // Chat et conversations.
        "Conversations" to "Conversations",
        "New conversation" to "Nouvelle conversation",
        "Find in conversation" to "Rechercher dans la conversation",
        "Jump to latest" to "Aller au dernier message",
        // Deux panneaux (pliable déplié, grand écran): le contrôle `sidebar.left` de la barre.
        "Collapse list" to "Replier la liste",
        "Expand list" to "Déplier la liste",
        "Card menu" to "Menu de la carte",
        "Retry" to "Réessayer",
        "Dismiss" to "Ignorer",
        "Copy reply" to "Copier la réponse",
        "Read aloud" to "Lire à voix haute",
        // Bots et discussion de groupe.
        "Bots" to "Bots",
        "Bots to mention" to "Bots à mentionner",
        "Group chat" to "Discussion de groupe",
        "No bots yet" to "Aucun bot pour le moment",
        "Ask them together." to "Posez-leur une question ensemble.",
        "New Group Chat" to "Nouvelle discussion de groupe",
        "Pick two to six bots for the group." to "Choisissez deux à six bots pour la discussion.",
        "The server made no group." to "Le serveur n’a pas créé la discussion.",
        "Couldn't create the group: %1\$s" to "Impossible de créer la discussion : %1\$s",
        // Voix.
        "Voice" to "Voix",
        "Listening" to "À l’écoute",
        "Talk to the group" to "Parler au groupe",
        "Stop listening" to "Arrêter l’écoute",
        "Stop reading" to "Arrêter la lecture",
        "Voice settings" to "Réglages vocaux",
        // Réglages et états communs.
        "Settings" to "Réglages",
        "Appearance" to "Apparence",
        "Return key sends" to "La touche Retour envoie",
        "Suggest follow-up questions" to "Suggérer des questions de suivi",
        "Loading from Onyx…" to "Chargement depuis Onyx…",
        "Try Again" to "Réessayer",
        // Horaires.
        "Schedules" to "Horaires",
        "New Schedule" to "Nouvel horaire",
        "Pick a Time Instead" to "Choisir plutôt une heure",
        "Last run" to "Dernière exécution",
        "No runs yet" to "Aucune exécution pour le moment",
        // Actions communes.
        "Cancel" to "Annuler",
        "Delete" to "Supprimer",
        "Save" to "Enregistrer",
        "Done" to "Terminé",
    )

    // Les traductions spécifiques à Onyx priment sur les termes partagés, puis les ajouts locaux
    // peuvent adapter une formulation sans modifier le catalogue réutilisé. Les cartes de réponse
    // et les phrases de l'écran de verrouillage passent en dernier.
    private val french = onyxSharedFrench + criticalFrench + onyxAdditionalFrench + onyxSettingsFrench + onyxCommonFrench + onyxRemainingFrench + onyxSetupTerminalFrench + onyxCardsFrench + onyxLockFrench + onyxToolsFrench + onyxToolsPageFrench

    private val frenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = onyxSetupTerminalFrenchPatterns + onyxCardsFrenchPatterns + listOf(
        Regex("^(\\d+) selected$") to { match ->
            val count = match.groupValues[1]
            if (count == "1") "1 élément sélectionné" else "$count éléments sélectionnés"
        },
        Regex("^(\\d+) of (\\d+) chosen · Turn (\\d+)$") to { match ->
            "${match.groupValues[1]} sur ${match.groupValues[2]} choisis · Tour ${match.groupValues[3]}"
        },
        Regex("^(\\d+) of (\\d+) chosen$") to { match ->
            "${match.groupValues[1]} sur ${match.groupValues[2]} choisis"
        },
        Regex("^(\\d+) of (\\d+) answering$") to { match ->
            "${match.groupValues[1]} sur ${match.groupValues[2]} en train de répondre"
        },
        Regex("^(\\d+) of (\\d+)$") to { match ->
            "${match.groupValues[1]} sur ${match.groupValues[2]}"
        },
        Regex("^(\\d+) earlier messages$") to { match -> "${match.groupValues[1]} messages précédents" },
        Regex("^Show earlier messages \\((\\d+)\\)$") to { match ->
            "Afficher les ${match.groupValues[1]} messages précédents"
        },
        Regex("^(\\d+) messages$") to { match -> "${match.groupValues[1]} messages" },
        // La recherche d'un projet, dont le nom s'insère (`ProjectSessionsView`).
        Regex("^Search in (.+)$") to { match -> "Rechercher dans ${match.groupValues[1]}" },
        Regex("^(\\d+) messages waiting$") to { match -> "${match.groupValues[1]} messages en attente" },
        // La tuile qui déplie la grille des bots (`gridAction("\(n) more")`).
        Regex("^(\\d+) more$") to { match ->
            val count = match.groupValues[1]
            if (count == "1") "1 de plus" else "$count de plus"
        },
        // Ce que la tuile « Hidden » de la grille des bots dit à VoiceOver.
        Regex("^Shows the (\\d+) bots hidden from this list$") to { match ->
            "Affiche les ${match.groupValues[1]} bots masqués de cette liste"
        },
        Regex("^(\\d+) characters$") to { match -> "${match.groupValues[1]} caractères" },
        Regex("^(\\d+) attachments$") to { match -> "${match.groupValues[1]} pièces jointes" },
        Regex("^Waiting for connection · (.+)$") to { match -> "En attente de connexion · ${match.groupValues[1]}" },
        Regex("^Waiting · (.+)$") to { match -> "En attente · ${match.groupValues[1]}" },
        Regex("^Not sent · from (.+)$") to { match -> "Non envoyé · depuis ${match.groupValues[1]}" },
        Regex("^1 conversation · (.+)$") to { match -> "1 conversation · ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(\\d+) conversations · (.+)$") to { match -> "${match.groupValues[1]} conversations · ${translate(match.groupValues[2], Locale.FRANCE)}" },
        Regex("^1 pinned · (.+)$") to { match -> "1 épinglée · ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(\\d+) pinned · (.+)$") to { match -> "${match.groupValues[1]} épinglées · ${translate(match.groupValues[2], Locale.FRANCE)}" },
        Regex("^1 archived · (.+)$") to { match -> "1 archivée · ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(\\d+) archived · (.+)$") to { match -> "${match.groupValues[1]} archivées · ${translate(match.groupValues[2], Locale.FRANCE)}" },
        Regex("^(\\d+) conversations$") to { match -> "${match.groupValues[1]} conversations" },
        Regex("^(\\d+) members$") to { match -> "${match.groupValues[1]} membres" },
        Regex("^(\\d+) tools$") to { match -> "${match.groupValues[1]} outils" },
        Regex("^(\\d+) files$") to { match -> "${match.groupValues[1]} fichiers" },
        Regex("^(\\d+) comments$") to { match -> "${match.groupValues[1]} commentaires" },
        Regex("^(\\d+) pinned$") to { match -> "${match.groupValues[1]} épinglées" },
        Regex("^(\\d+) archived$") to { match -> "${match.groupValues[1]} archivées" },
        Regex("^(\\d+) answering$") to { match -> "${match.groupValues[1]} en train de répondre" },
        Regex("^(\\d+) turns$") to { match -> "${match.groupValues[1]} tours" },
        Regex("^(\\d+) more$") to { match -> "${match.groupValues[1]} de plus" },
        Regex("^Listening for (\\d+) s$") to { match -> "Écoute depuis ${match.groupValues[1]} s" },
        Regex("^Thought for (\\d+) s$") to { match -> "Réflexion pendant ${match.groupValues[1]} s" },
        Regex("^The recogniser stopped \\(code (\\d+)\\)\\.$") to { match ->
            "Le moteur de reconnaissance s’est arrêté (code ${match.groupValues[1]})."
        },
        Regex("^(\\d+) tools · (.+)$") to { match -> "${match.groupValues[1]} outils · ${match.groupValues[2]}" },
        // Un seul outil: la ligne de détail d'un sous-agent au singulier (« 1 tool · read_file »).
        Regex("^(\\d+) tool · (.+)$") to { match -> "${match.groupValues[1]} outil · ${match.groupValues[2]}" },
        Regex("^1 file · (.+)$") to { match -> "1 fichier · ${match.groupValues[1]}" },
        Regex("^(\\d+) files · (.+)$") to { match -> "${match.groupValues[1]} fichiers · ${match.groupValues[2]}" },
        Regex("^Working · (.+)$") to { match -> "En cours : ${translate(match.groupValues[1], Locale.FRANCE)}" },
        // Le détail d'un sous-agent fini, quand il n'a lancé aucun outil: la seule durée et le
        // résumé. Avec des outils, le motif « N tools · … » plus haut s'en charge.
        Regex("^(\\d+ s|\\d+\\.\\d+ min) · (.+)$") to { match ->
            "${match.groupValues[1]} · ${translate(match.groupValues[2], Locale.FRANCE)}"
        },
        Regex("^Used (.+)$") to { match -> "Utilisés : ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^View change · (.+)$") to { match -> "Voir la modification · ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^is working · (.+)$") to { match -> "travaille · ${match.groupValues[1]}" },
        Regex("^… (\\d+) more lines$") to { match -> "… ${match.groupValues[1]} lignes supplémentaires" },
        Regex("^(.+) · Replying…$") to { match -> "${match.groupValues[1]} · Réponse en cours…" },
        Regex("^Turn (\\d+)$") to { match -> "Tour ${match.groupValues[1]}" },
        Regex("^Photo (\\d+) of (\\d+)$") to { match -> "Photo ${match.groupValues[1]} sur ${match.groupValues[2]}" },
        // Le compte des pages du lecteur de PDF (`PdfViewer`).
        Regex("^Page (\\d+) of (\\d+)$") to { match -> "Page ${match.groupValues[1]} sur ${match.groupValues[2]}" },
        // Ce qu'un lecteur d'écran annonce pour la vignette d'une vidéo jointe (le nom, puis le
        // poids quand il est connu): le même gabarit que `VideoTile` du modèle.
        Regex("^Video: (.+)$") to { match -> "Vidéo : ${match.groupValues[1]}" },
        Regex("^Muted until tomorrow, (.+)$") to { match -> "Silencieux jusqu’à demain, ${match.groupValues[1]}" },
        Regex("^Muted until (.+)$") to { match -> "Silencieux jusqu’à ${match.groupValues[1]}" },
        Regex("^Deleting (.+)…$") to { match -> "Suppression de ${match.groupValues[1]}…" },
        Regex("^Uploading (.+)…$") to { match -> "Téléversement de ${match.groupValues[1]}…" },
        Regex("^(.+) · task (\\d+) of (\\d+)$") to { match ->
            "${translate(match.groupValues[1], Locale.FRANCE)} · tâche ${match.groupValues[2]} sur ${match.groupValues[3]}"
        },
        Regex("^Running · (.+)$") to { match -> "En cours · ${match.groupValues[1]}" },
        // Tour en cours (notification de progression): l'outil et la durée écoulée.
        Regex("^Running (.+) · (\\d+s|\\d+m \\d+s|\\d+h \\d+m)$") to { match ->
            "Exécution de ${match.groupValues[1]} · ${match.groupValues[2]}"
        },
        Regex("^Running (.+)$") to { match -> "Exécution de ${match.groupValues[1]}" },
        Regex("^(\\d+)% read · (\\d+s|\\d+m \\d+s|\\d+h \\d+m)$") to { match ->
            "${match.groupValues[1]} % lus · ${match.groupValues[2]}"
        },
        Regex("^(\\d+)% read$") to { match -> "${match.groupValues[1]} % lus" },
        Regex("^Answering… · (\\d+s|\\d+m \\d+s|\\d+h \\d+m)$") to { match ->
            "Réponse en cours… · ${match.groupValues[1]}"
        },
        // Les actions de notification et leurs états.
        Regex("^Approved$") to { _ -> "Approuvée" },
        Regex("^Denied$") to { _ -> "Refusée" },
        Regex("^Reply$") to { _ -> "Répondre" },
        Regex("^A password is needed$") to { _ -> "Un mot de passe est requis" },
        Regex("^A secret is needed$") to { _ -> "Un secret est requis" },
        Regex("^Couldn't answer$") to { _ -> "Impossible de répondre" },
        Regex("^Answered$") to { _ -> "Réponse envoyée" },
        Regex("^Open Onyx to answer from the conversation[.]$") to { _ ->
            "Ouvrez Onyx pour répondre depuis la conversation."
        },
        Regex("^Failed · (.+)$") to { match -> "Échec · ${match.groupValues[1]}" },
        Regex("^Finished · (.+)$") to { match -> "Terminé · ${match.groupValues[1]}" },
        Regex("^Show (\\d+)$") to { match -> "Afficher ${match.groupValues[1]}" },
        Regex("^Enabled · (\\d+)$") to { match -> "Activés · ${match.groupValues[1]}" },
        Regex("^Not enabled · (\\d+)$") to { match -> "Non activés · ${match.groupValues[1]}" },
        Regex("^(.+)'s Schedules$") to { match -> "Horaires de ${match.groupValues[1]}" },
        Regex("^Ask (.+)$") to { match -> "Demander à ${match.groupValues[1]}" },
        Regex("^(.+) is speaking$") to { match -> "${match.groupValues[1]} parle" },
        Regex("^(.+) is writing$") to { match -> "${match.groupValues[1]} écrit" },
        Regex("^(.+) is thinking$") to { match -> "${match.groupValues[1]} réfléchit" },
        // Les cartes d'interruption dans le fil d'un bot: « <bot> a une question / a besoin d'un
        // secret » — le nom du bot s'insère, là où « Onyx has a question » garde son entrée exacte.
        Regex("^(.+) has a few questions$") to { match -> "${match.groupValues[1]} a quelques questions" },
        Regex("^(.+) has a question$") to { match -> "${match.groupValues[1]} a une question" },
        Regex("^(.+) needs a secret$") to { match -> "${match.groupValues[1]} a besoin d’un secret" },
        Regex("^Same as (.+)$") to { match -> "Identique à ${match.groupValues[1]}" },
        // Le titre de la feuille de voix d'un bot (`BotVoicePicker`) et ses détails: le nom du
        // bot ou de l'agent principal s'insèrent, la phrase se traduit autour.
        Regex("^(.+)'s Voice$") to { match -> "Voix de ${match.groupValues[1]}" },
        Regex("^(.+)'s voice, for now: the server has no other voice in its language$") to { match ->
            "la voix de ${match.groupValues[1]}, pour l’instant : le serveur n’a pas d’autre voix dans sa langue"
        },
        Regex("^(.+)'s voice, for now: no other voice is installed for its language$") to { match ->
            "la voix de ${match.groupValues[1]}, pour l’instant : aucune autre voix n’est installée pour sa langue"
        },
        Regex("^(.+)'s voice$") to { match -> "la voix de ${match.groupValues[1]}" },
        // La qualité d'une voix de l'appareil, à côté de sa langue: « Améliorée · fr-FR ».
        Regex("^(Enhanced|Default|Premium) · (.+)$") to { match ->
            "${translate(match.groupValues[1], Locale.FRANCE)} · ${match.groupValues[2]}"
        },
        Regex("^The tools (.+) has\\.$") to { match -> "Les outils de ${match.groupValues[1]}." },
        Regex("^(.+) — Personality$") to { match -> "${match.groupValues[1]} — Personnalité" },
        Regex("^(.+) orb$") to { match -> "Orbe ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(.+), dark$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, sombre" },
        Regex("^(.+), light$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, clair" },
        Regex("^(.+), system$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, système" },
        // « Messages colors » / « Use Messages's own colors »: le nom du thème s'insère.
        Regex("^(.+) colors$") to { match -> "Couleurs du thème ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^Use (.+)'s own colors$") to { match -> "Utiliser les couleurs du thème ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^Remove (.+)$") to { match -> "Supprimer ${match.groupValues[1]}" },
        Regex("^Preview (.+)$") to { match -> "Écouter ${match.groupValues[1]}" },
        Regex("^Priority (.+)$") to { match -> "Priorité ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(.+)'s Skills tool is off \\(Edit Bot → Tools\\), so it reads none of these\\.$") to { match ->
            "L’outil Skills de ${match.groupValues[1]} est désactivé (Modifier le bot → Outils), donc il ne lit aucune de ces compétences."
        },
        // Barre de sélection multiple des conversations: « Archiver N » / « Supprimer N ».
        Regex("^Archive ([0-9]+)$") to { match -> "Archiver ${match.groupValues[1]}" },
        Regex("^Delete ([0-9]+)$") to { match -> "Supprimer ${match.groupValues[1]}" },
        // Le titre de la confirmation porte un « ? » dans l'écran (« Delete 4 conversations? »): sans
        // le motif, la question restait en anglais au-dessus d'un message français.
        Regex("^Delete ([0-9]+) conversations?\\??$") to { match ->
            val n = match.groupValues[1]
            if (n == "1") "Supprimer 1 conversation ?" else "Supprimer $n conversations ?"
        },
        Regex("^([0-9]+) conversation selected$") to { _ -> "1 conversation sélectionnée" },
        Regex("^([0-9]+) conversations selected$") to { match -> "${match.groupValues[1]} conversations sélectionnées" },
        // Pieds de page et états vides des horaires d'un bot, où le nom du bot s'insère.
        Regex("^Give (.+) something to do at a time: every weekday at 7, check the weather and your calendar and say what to wear[.] Its answer comes into its chat[.]$") to { match ->
            "Donnez à ${match.groupValues[1]} une tâche à une heure précise : en semaine à 7 h, consultez la météo et votre agenda et dites quoi porter. Sa réponse arrive dans son chat."
        },
        Regex("^Each run is (.+)'s, with its own personality and tools, and its answer comes into its chat, with a notification, while your phone is off[.] Times are your server's[.] Hermes 0[.]21[.]5 and later run a bot's schedules by themselves; earlier ones only with gateway[.]multiplex_profiles on[.]$") to { match ->
            "Chaque exécution est celle de ${match.groupValues[1]}, avec sa personnalité et ses outils ; sa réponse arrive dans son chat, avec une notification, même si votre téléphone est éteint. Les horaires suivent l’heure de votre serveur. Hermes 0.21.5 et versions ultérieures exécutent seuls les horaires d’un bot ; les versions antérieures seulement avec gateway.multiplex_profiles activé."
        },
    )

    /**
     * Traduit [key] pour [locale] (langue système par défaut). L'indexation `OnyxLocalization["To do"]`
     * suivait autrefois la seule langue système : un appelant qui passait `Locale.FRANCE` recevait
     * l'anglais dès que le système n'était pas en français. La langue est donc un paramètre ici aussi.
     */
    operator fun get(key: String, locale: Locale = Locale.getDefault()): String = translate(key, locale)

    /**
     * Traduit un texte fait de plusieurs lignes, chacune séparément.
     *
     * Les descriptions du serveur sont des paragraphes séparés par des retours à la ligne (la
     * fiche d'un greffon, par exemple): une clé unique ne peut pas les couvrir, alors que chaque
     * paragraphe, lui, est traduit. Une ligne vide est conservée telle quelle.
     */
    fun translateParagraphs(text: String, locale: Locale = Locale.getDefault()): String =
        text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) line else translate(line, locale)
        }

    /** Traduit un gabarit `String.format`, puis réinsère les arguments sans les localiser. */
    fun format(key: String, vararg args: Any?, locale: Locale = Locale.getDefault()): String =
        String.format(locale, translate(key, locale), *args)

    /**
     * Traduit [key] pour [locale]. Le français utilise le dictionnaire français, puis l'anglais;
     * les autres langues utilisent l'anglais, puis la clé d'origine sans la modifier.
     */
    fun translate(key: String, locale: Locale = Locale.getDefault()): String {
        if (!locale.language.equals("fr", ignoreCase = true)) return english[key] ?: onyxEnglishFromFrench[key] ?: key
        french[key]?.let { return it }
        english[key]?.let { return it }
        frenchPatterns.forEach { (pattern, replacement) ->
            pattern.matchEntire(key)?.let { return replacement(it) }
        }
        return english[key] ?: key
    }
}
