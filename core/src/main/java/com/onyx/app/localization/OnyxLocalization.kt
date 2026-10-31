package com.onyx.app.localization

import java.util.IllegalFormatException
import java.util.Locale

/**
 * Libellés de l'interface Onyx.
 *
 * Les clés sont les chaînes anglaises déjà présentes dans le code. La locale par défaut est lue à
 * chaque appel (et non mémorisée au démarrage) : un changement de langue suivi d'une recomposition
 * ou d'une recréation d'activité est donc pris en compte. Une clé inconnue est toujours rendue
 * telle quelle, ce qui préserve les réponses du serveur et les identifiants.
 */
object OnyxLocalization {
    /** Anglais explicite pour les termes techniques qui n'ont pas besoin de traduction française. */
    private val english = mapOf(
        "API" to "API",
        "Onyx" to "Onyx",
        "Hermes" to "Hermes",
        // Le cran « Défaut » du curseur de taille de texte : la clé est le mot français, et
        // l'anglais le dit dans sa langue.
        "Défaut" to "Default",
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
        // Deux panneaux (pliable déplié, grand écran) : le contrôle `sidebar.left` de la barre.
        "Collapse list" to "Replier la liste",
        "Expand list" to "Déplier la liste",
        // Le **voile** du volet : l'appui dessus est le retour d'un cran, et il porte désormais son
        // nom (relevé d'arbre : ce nœud cliquable plein écran était muet).
        "Close list" to "Fermer la liste",
        // Le volet du Chat : sa tête, ses tuiles, ses puces de projets, ses filtres et ses bots.
        "Search chats" to "Rechercher des conversations",
        // Le **mode recherche** : sortir du champ et effacer son texte sont deux actions, et l'état
        // vide dit **ce que la recherche interroge vraiment** (le titre et le texte des messages).
        "Clear search" to "Effacer la recherche",
        "End search" to "Quitter la recherche",
        "No results" to "Aucun résultat",
        "Searches the titles of your conversations and the text of their messages." to "La recherche porte sur les titres des conversations et le texte de leurs messages.",
        "All projects" to "Tous les projets",
        "New project" to "Nouveau projet",
        "Waiting for you" to "Vous attend",
        "Running" to "En cours",
        "All" to "Tout",
        "Working" to "En cours",
        "Scheduled" to "Planifiés",
        // LOT anatomie-des-contenus : le sous-titre du grand titre de la liste des tâches planifiées.
        "%d job · each run opens as a chat" to "%d tâche · chaque exécution s’ouvre comme une conversation",
        "%d jobs · each run opens as a chat" to "%d tâches · chaque exécution s’ouvre comme une conversation",
        "Skills" to "Compétences",
        "Tools" to "Outils",
        "Files" to "Fichiers",
        // Le navigateur de fichiers du serveur (Réglages → Fichiers) : il ne porte pas le même nom
        // que l'onglet « Fichiers » de la conversation, qui montre les pièces échangées du fil.
        "Files browser" to "Fichiers du serveur",
        "Models" to "Modèles",
        "Code" to "Code",
        "Reachable" to "Joignable",
        "Unreachable" to "Injoignable",
        "Checking" to "Vérification",
        // La **ligne d'état hors ligne** du volet : une phrase, et un réessai discret. Le gabarit
        // `%d` est rempli par `OnyxLocalization.format`, donc l'âge reste un nombre.
        "Saved copy" to "Copie enregistrée",
        "Saved copy on this phone." to "Copie enregistrée sur ce téléphone.",
        "The server is unreachable for now." to "Le serveur est injoignable pour le moment.",
        "just now" to "à l'instant",
        "%d min ago" to "il y a %d min",
        "%d h ago" to "il y a %d h",
        "%d d ago" to "il y a %d d",
        "%d w ago" to "il y a %d sem",
        "Try again" to "Réessayer",
        "New bot" to "Nouveau bot",
        "New room" to "Nouveau salon",
        "Rooms" to "Salons",
        "No conversations yet." to "Aucune conversation pour le moment.",
        "No matches." to "Aucun résultat.",
        "No bots on this gateway yet." to "Aucun bot sur cette passerelle pour le moment.",
        "No rooms yet." to "Aucun salon pour le moment.",
        "Card menu" to "Menu de la carte",
        "Retry" to "Réessayer",
        "Dismiss" to "Ignorer",
        "Copy reply" to "Copier la réponse",
        "Read aloud" to "Lire à voix haute",
        // Bots et salons (ce qu'Onyx appelait « discussion de groupe » : le même objet, sous le nom
        // que la référence lui donne — un **salon**, tenu par la passerelle ou gardé ici).
        "Bots" to "Bots",
        "Bots to mention" to "Bots à mentionner",
        "Group chat" to "Salon",
        "No bots yet" to "Aucun bot pour le moment",
        "Ask them together." to "Posez-leur une question ensemble.",
        "New Group Chat" to "Nouveau salon",
        "Pick two to six bots for the group." to "Choisissez de deux à six bots pour le salon.",
        "The server made no group." to "Le serveur n’a pas créé le salon.",
        "Couldn't create the group: %1\$s" to "Impossible de créer le salon : %1\$s",
        // La boîte de création d'un salon (`RoomCreationDialog`), aux mots de la référence.
        "Room name" to "Nom du salon",
        "Release room" to "Salon des versions",
        "Bots · pick 2 to 6" to "BOTS · EN CHOISIR DE 2 À 6",
        "1 picked" to "1 choisi",
        "%1\$d picked" to "%1\$d choisis",
        "Name the room." to "Donnez un nom au salon.",
        "%1\$d more: a room needs at least %2\$d bots." to "Encore %1\$d bot : il en faut au moins %2\$d.",
        "Take %1\$d out: %2\$d bots at most." to "Enlevez-en %1\$d : %2\$d bots au maximum.",
        "Making the room…" to "Ouverture du salon…",
        "The bots you pick talk things over in the room. It runs on the gateway, so it keeps going with no app open, and the whole conversation is there when you come back." to
            "Les bots que vous choisissez discutent dans le salon. Il tourne sur la passerelle : il continue même application fermée, et toute la conversation est là quand vous revenez.",
        "The bots you pick talk things over in the room. This one is kept on this phone: it is still there after a restart, but its bots only answer while Onyx is open." to
            "Les bots que vous choisissez discutent dans le salon. Celui-ci est gardé sur ce téléphone : il reste là après une relance, mais ses bots ne répondent que pendant qu’Onyx est ouvert.",
        "The bots you pick talk things over in the room. The gateway isn't running rooms right now: opening one will be turned down, and nothing is kept if it is." to
            "Les bots que vous choisissez discutent dans le salon. La passerelle ne tient pas de salon pour le moment : l’ouverture sera refusée, et rien ne sera gardé si elle l’est.",
        // Voix.
        "Voice" to "Voix",
        "Listening" to "À l’écoute",
        "Talk to the salon" to "Parler au salon",
        "Talk to the group" to "Parler au salon",
        "Stop listening" to "Arrêter l’écoute",
        "Stop the task" to "Arrêter la tâche",
        "Stop this subagent" to "Arrêter ce sous-agent",
        "Take photo" to "Prendre la photo",
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
        // L'appel entrant (Réglages → Phone → Calls).
        "Calls" to "Appels",
        "Onyx call" to "Appel d’Onyx",
        "Needs your approval" to "A besoin de votre accord",
        "Has a question for you" to "A une question pour vous",
        "Ring for a decision" to "Sonner pour une décision",
        "Answer with live voice" to "Répondre en voix live",
        "Quiet hours" to "Heures de tranquillité",
        "Starts" to "Début",
        "Ends" to "Fin",
        "Ring this phone" to "Faire sonner ce téléphone",
        "Decline" to "Refuser",
        "Answer" to "Décrocher",
        // La veille d'appel (le service qui garde Onyx joignable en arrière-plan).
        "Reachable" to "Joignable",
        "Keeps Onyx able to ring while it's in the background." to "Garde Onyx capable de sonner pendant qu'il est en arrière-plan.",
        "Ready for a call" to "Prêt à recevoir un appel",
        "Hermes can reach this phone while Onyx isn't open." to "Hermes peut joindre ce téléphone même quand Onyx n’est pas ouvert.",

        // -------------------------------------------------------------------------------------
        // LES TÂCHES PLANIFIÉES (Réglages → Planifiés, et les horaires d'un bot).
        //
        // Le relevé de la capture du propriétaire (détail d'une tâche) a trouvé, **affichés tels
        // quels dans une appli française** : « Run now », « Pause », « next run », « scheduled »,
        // « origin », « every 60m », « RUNS ». Les entrées ci-dessous sont les leurs ; elles vivent
        // ici, dans **le** dictionnaire, et nulle part en dur dans un écran.
        // -------------------------------------------------------------------------------------

        // Le détail d'une tâche : ses deux pilules, ses deux gestes de tête, son menu.
        "Pause job" to "Mettre en pause",
        "Job options" to "Options de la tâche",
        "Edit job" to "Modifier la tâche",
        "Delete job" to "Supprimer la tâche",

        // La liste : son titre de section, son état vide, son résumé.
        "Jobs" to "Tâches",
        "Schedule Onyx to run a prompt every hour, every weekday at 9am, or once at a time you pick." to
            "Demandez à Onyx d’exécuter une consigne toutes les heures, chaque jour de semaine à 9 h, ou une seule fois à l’heure que vous choisissez.",

        // L'éditeur d'une tâche : ses cartes et leurs phrases.
        "Job" to "Tâche",
        "Prompt" to "Consigne",
        "What should Onyx do?" to "Que doit faire Onyx ?",
        "Local keeps the output on the gateway; a platform sends it where the job runs." to
            "« Local » garde le résultat sur la passerelle ; une plateforme l’envoie là où la tâche s’exécute.",
        "Saving…" to "Enregistrement…",

        // La colonne d'état d'une rangée, et les valeurs d'état du serveur.
        "next run" to "prochaine exécution",
        "last run" to "dernière exécution",
        "last run failed" to "échec de la dernière exécution",
        "done" to "terminée",
        "state" to "état",
        "run" to "exécution",
        "scheduled" to "planifiée",
        "completed" to "terminée",
        "error" to "en échec",
        // Un passage dont la valeur n'est pas un instant : la rangée **ne prend pas** le nombre brut
        // pour titre (l'identifiant technique descend en monospace, sous la ligne).
        "Undated run" to "Exécution non datée",

        // La liste des exécutions d'une tâche.
        "Runs" to "Exécutions",

        // Les horaires d'un bot : la pastille d'une routine partagée, et son pied.
        "Shared" to "Partagée",
    )

    // Les traductions spécifiques à Onyx priment sur les termes partagés, puis les ajouts locaux
    // peuvent adapter une formulation sans modifier le catalogue réutilisé. Les cartes de réponse
    // et les phrases de l'écran de verrouillage passent en dernier.
    private val french = onyxSharedFrench + criticalFrench + onyxAdditionalFrench + onyxSettingsFrench + onyxCommonFrench + onyxRemainingFrench + onyxSetupTerminalFrench + onyxCardsFrench + onyxLockFrench + onyxToolsFrench + onyxToolsPageFrench + onyxLanguageFrench + onyxCodeFrench + onyxTabsFrench + onyxMenuSheetFrench + onyxCapabilitiesFrench + onyxInsightsFrench + onyxOrganisationFrench + onyxLedgerFrench + onyxSidebarTilesFrench

    private val frenchPatterns: List<Pair<Regex, (MatchResult) -> String>> = onyxSetupTerminalFrenchPatterns + onyxCardsFrenchPatterns + onyxCodeFrenchPatterns + onyxMenuSheetFrenchPatterns + listOf(
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
        // Un seul outil : la ligne de détail d'un sous-agent au singulier (« 1 tool · read_file »).
        Regex("^(\\d+) tool · (.+)$") to { match -> "${match.groupValues[1]} outil · ${match.groupValues[2]}" },
        Regex("^1 file · (.+)$") to { match -> "1 fichier · ${match.groupValues[1]}" },
        Regex("^(\\d+) files · (.+)$") to { match -> "${match.groupValues[1]} fichiers · ${match.groupValues[2]}" },
        Regex("^Working · (.+)$") to { match -> "En cours : ${translate(match.groupValues[1], Locale.FRANCE)}" },
        // Le détail d'un sous-agent fini, quand il n'a lancé aucun outil : la seule durée et le
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
        // poids quand il est connu) : le même gabarit que `VideoTile` de la référence.
        Regex("^Video: (.+)$") to { match -> "Vidéo : ${match.groupValues[1]}" },
        Regex("^Muted until tomorrow, (.+)$") to { match -> "Silencieux jusqu’à demain, ${match.groupValues[1]}" },
        Regex("^Muted until (.+)$") to { match -> "Silencieux jusqu’à ${match.groupValues[1]}" },
        Regex("^Deleting (.+)…$") to { match -> "Suppression de ${match.groupValues[1]}…" },
        Regex("^Uploading (.+)…$") to { match -> "Téléversement de ${match.groupValues[1]}…" },
        Regex("^(.+) · task (\\d+) of (\\d+)$") to { match ->
            "${translate(match.groupValues[1], Locale.FRANCE)} · tâche ${match.groupValues[2]} sur ${match.groupValues[3]}"
        },
        Regex("^Running · (.+)$") to { match -> "En cours · ${match.groupValues[1]}" },
        // Tour en cours (notification de progression) : l'outil et la durée écoulée.
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
        // ---- Les horaires d'une tâche planifiée, **affichés** --------------------------------
        // Le champ du serveur porte la **syntaxe** (« every 60m », « weekdays at 9am »), et cette
        // syntaxe reste anglaise là où elle est une **valeur à saisir** (l'éditeur, ses exemples).
        // Mais là où l'écran la **raconte** — la rangée d'une tâche, son sous-titre, sa ligne
        // « Planification » —, « every 60m » au milieu d'une phrase française est exactement le
        // défaut relevé. Ces motifs ne couvrent que les formes que l'app cite elle-même ; toute
        // autre valeur passe telle quelle.
        Regex("^every (\\d+)m$", RegexOption.IGNORE_CASE) to { match ->
            "toutes les ${match.groupValues[1]} min"
        },
        Regex("^every (\\d+)h$", RegexOption.IGNORE_CASE) to { match ->
            "toutes les ${match.groupValues[1]} h"
        },
        Regex("^every (\\d+) ?minutes?$", RegexOption.IGNORE_CASE) to { match ->
            "toutes les ${match.groupValues[1]} min"
        },
        Regex("^every (\\d+) ?hours?$", RegexOption.IGNORE_CASE) to { match ->
            "toutes les ${match.groupValues[1]} h"
        },
        Regex("^every hour$", RegexOption.IGNORE_CASE) to { _ -> "toutes les heures" },
        Regex("^every day at (.+)$", RegexOption.IGNORE_CASE) to { match ->
            "chaque jour à ${frenchClock(match.groupValues[1])}"
        },
        Regex("^every day$", RegexOption.IGNORE_CASE) to { _ -> "chaque jour" },
        Regex("^weekdays at (.+)$", RegexOption.IGNORE_CASE) to { match ->
            "en semaine à ${frenchClock(match.groupValues[1])}"
        },
        Regex("^weekdays$", RegexOption.IGNORE_CASE) to { _ -> "en semaine" },
        Regex("^every (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s*(.*)$", RegexOption.IGNORE_CASE) to { match ->
            val day = translate(match.groupValues[1], Locale.FRANCE).lowercase(Locale.FRANCE)
            val at = frenchClock(match.groupValues[2])
            if (at.isBlank()) "chaque $day" else "chaque $day à $at"
        },
        Regex("^in (\\d+)m$", RegexOption.IGNORE_CASE) to { match -> "dans ${match.groupValues[1]} min" },
        Regex("^in (\\d+)h$", RegexOption.IGNORE_CASE) to { match -> "dans ${match.groupValues[1]} h" },
        Regex("^at (.+)$", RegexOption.IGNORE_CASE) to { match -> "à ${match.groupValues[1]}" },
        // Le titre de la confirmation de suppression d'une tâche : « Delete “X”? » — le nom de la
        // tâche entre dans la phrase, comme partout ailleurs dans la maison.
        Regex("^Delete “(.+)”\\?$") to { match -> "Supprimer « ${match.groupValues[1]} » ?" },
        // La phrase de « Done » sur l'écran de configuration : le message du test y est repris tel
        // quel (c'est la réponse du serveur, pas un libellé d'Onyx).
        Regex("^The test failed: (.+)$") to { match -> "Le test a échoué : ${match.groupValues[1]}" },
        Regex("^Ask (.+)$") to { match -> "Demander à ${match.groupValues[1]}" },
        Regex("^(.+) is speaking$") to { match -> "${match.groupValues[1]} parle" },
        Regex("^(.+) is writing$") to { match -> "${match.groupValues[1]} écrit" },
        Regex("^(.+) is thinking$") to { match -> "${match.groupValues[1]} réfléchit" },
        // Les cartes d'interruption dans le fil d'un bot : « <bot> a une question / a besoin d'un
        // secret » — le nom du bot s'insère, là où « Onyx has a question » garde son entrée exacte.
        Regex("^(.+) has a few questions$") to { match -> "${match.groupValues[1]} a quelques questions" },
        Regex("^(.+) has a question$") to { match -> "${match.groupValues[1]} a une question" },
        Regex("^(.+) needs a secret$") to { match -> "${match.groupValues[1]} a besoin d’un secret" },
        Regex("^Same as (.+)$") to { match -> "Identique à ${match.groupValues[1]}" },
        // Le titre de la feuille de voix d'un bot (`BotVoicePicker`) et ses détails : le nom du
        // bot ou de l'agent principal s'insèrent, la phrase se traduit autour.
        Regex("^(.+)'s Voice$") to { match -> "Voix de ${match.groupValues[1]}" },
        Regex("^(.+)'s voice, for now: the server has no other voice in its language$") to { match ->
            "la voix de ${match.groupValues[1]}, pour l’instant : le serveur n’a pas d’autre voix dans sa langue"
        },
        Regex("^(.+)'s voice, for now: no other voice is installed for its language$") to { match ->
            "la voix de ${match.groupValues[1]}, pour l’instant : aucune autre voix n’est installée pour sa langue"
        },
        Regex("^(.+)'s voice$") to { match -> "la voix de ${match.groupValues[1]}" },
        // La qualité d'une voix de l'appareil, à côté de sa langue : « Améliorée · fr-FR ».
        Regex("^(Enhanced|Default|Premium) · (.+)$") to { match ->
            "${translate(match.groupValues[1], Locale.FRANCE)} · ${match.groupValues[2]}"
        },
        Regex("^The tools (.+) has\\.$") to { match -> "Les outils de ${match.groupValues[1]}." },
        Regex("^(.+) — Personality$") to { match -> "${match.groupValues[1]} — Personnalité" },
        Regex("^(.+) orb$") to { match -> "Orbe ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(.+), dark$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, sombre" },
        Regex("^(.+), light$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, clair" },
        Regex("^(.+), system$") to { match -> "${translate(match.groupValues[1], Locale.FRANCE)}, système" },
        // « Messages colors » / « Use Messages's own colors » : le nom du thème s'insère.
        Regex("^(.+) colors$") to { match -> "Couleurs du thème ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^Use (.+)'s own colors$") to { match -> "Utiliser les couleurs du thème ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^Remove (.+)$") to { match -> "Supprimer ${match.groupValues[1]}" },
        Regex("^Preview (.+)$") to { match -> "Écouter ${match.groupValues[1]}" },
        Regex("^Priority (.+)$") to { match -> "Priorité ${translate(match.groupValues[1], Locale.FRANCE)}" },
        Regex("^(.+)'s Skills tool is off \\(Edit Bot → Tools\\), so it reads none of these\\.$") to { match ->
            "L’outil Skills de ${match.groupValues[1]} est désactivé (Modifier le bot → Outils), donc il ne lit aucune de ces compétences."
        },
        // Barre de sélection multiple des conversations : « Archiver N » / « Supprimer N ».
        Regex("^Archive ([0-9]+)$") to { match -> "Archiver ${match.groupValues[1]}" },
        Regex("^Delete ([0-9]+)$") to { match -> "Supprimer ${match.groupValues[1]}" },
        // La même barre dans un projet y ajoute le déplacement groupé vers un autre projet.
        Regex("^Move ([0-9]+)$") to { match -> "Déplacer ${match.groupValues[1]}" },
        // Le titre de la confirmation porte un « ? » dans l'écran (« Delete 4 conversations? ») : sans
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
     * L'heure d'un horaire, en français : « 9am » → « 9 h », « 6:30pm » → « 6:30 ». Le reste — une
     * expression cron, une heure déjà écrite — passe tel quel.
     */
    private fun frenchClock(text: String): String {
        val trimmed = text.trim()
        val match = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*([ap]m)$", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed) ?: return trimmed
        val hour = match.groupValues[1].toIntOrNull() ?: return trimmed
        val minutes = match.groupValues[2]
        val pm = match.groupValues[3].equals("pm", ignoreCase = true)
        val h24 = when {
            pm && hour < 12 -> hour + 12
            !pm && hour == 12 -> 0
            else -> hour
        }
        return if (minutes.isEmpty()) "$h24 h" else "$h24:${minutes.padStart(2, '0')}"
    }

    /** Traduit avec la langue système courante. */
    operator fun get(key: String): String = translate(key)

    /**
     * Vrai quand l'interface est en **français**.
     *
     * Sert aux textes que l'app compose **elle-même** sans passer par la table de traduction — la
     * ponctuation d'un montant (« 0,30 $ » plutôt que « $0.30 »), qu'aucune clé ne peut porter
     * puisqu'elle dépend du chiffre reçu du serveur.
     */
    fun isFrench(locale: Locale = OnyxLanguage.preferred): Boolean =
        locale.language.equals("fr", ignoreCase = true)

    /**
     * Traduit un texte fait de plusieurs lignes, chacune séparément.
     *
     * Les descriptions du serveur sont des paragraphes séparés par des retours à la ligne (la
     * fiche d'un greffon, par exemple) : une clé unique ne peut pas les couvrir, alors que chaque
     * paragraphe, lui, est traduit. Une ligne vide est conservée telle quelle.
     */
    fun translateParagraphs(text: String, locale: Locale = OnyxLanguage.preferred): String =
        text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) line else translate(line, locale)
        }

    /**
     * Traduit un gabarit `String.format`, puis réinsère les arguments sans les localiser.
     *
     * **Un gabarit et ses arguments qui ne s'accordent pas ne doivent jamais tuer l'app.** Un trou
     * `%3$s` sans troisième argument levait `MissingFormatArgumentException` — et comme l'appel
     * vivait dans l'effet de chargement d'un écran, l'exception partait au fil principal et le
     * processus mourait avant sa première image (défaut « Relevé de l'agent »). Le repli rend le
     * gabarit **tel quel** : un libellé visiblement non rempli, jamais un écran qui se ferme.
     */
    fun format(key: String, vararg args: Any?, locale: Locale = OnyxLanguage.preferred): String {
        val template = translate(key, locale)
        return try {
            String.format(locale, template, *args)
        } catch (unmatched: IllegalFormatException) {
            template
        }
    }

    /**
     * Traduit [key] pour [locale]. Le français utilise le dictionnaire français, puis l'anglais ;
     * les autres langues utilisent l'anglais, puis la clé d'origine sans la modifier.
     */
    fun translate(key: String, locale: Locale = OnyxLanguage.preferred): String {
        if (!locale.language.equals("fr", ignoreCase = true)) return english[key] ?: onyxEnglishFromFrench[key] ?: key
        french[key]?.let { return it }
        english[key]?.let { return it }
        frenchPatterns.forEach { (pattern, replacement) ->
            pattern.matchEntire(key)?.let { return replacement(it) }
        }
        return english[key] ?: key
    }
}
