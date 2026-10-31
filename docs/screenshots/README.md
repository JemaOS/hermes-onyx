# Galerie de captures — conventions

Ce dossier porte les images du README. Les **fichiers listés ci-dessous sont ceux réellement
présents** dans ce dossier, tous repris sur le build livré. Aucune capture périmée ne doit être
laissée ici : une image d'une version antérieure montre une interface qui n'existe plus et se lit
comme l'état courant.

## Convention de nommage

- **Pas de suffixe = apparence SOMBRE** (l'apparence par défaut du build). Ex. `conversation-chat.png`.
- **Suffixe `-light` = apparence CLAIRE**, même nom d'écran, même cadrage. Ex. `conversation-chat-light.png`.
- Casse : minuscules, mots séparés par un tiret.
- **La galerie porte les DEUX apparences** : chaque écran sombre a sa jumelle claire, prise sur le
  même build, écran pour écran.

## Règles de prise de vue

- Capture prise sur un **émulateur**, contre un serveur de test à données de démonstration —
  jamais sur l'appareil de l'auteur.
- **Barre d'état retirée** de chaque image ; l'**adresse** du serveur de démo est **floutée**
  (bande pleine largeur), et l'en-tête du volet — qui l'imprime — est **recadré**.
- Aucune adresse réelle, aucun jeton, aucune conversation réelle, aucun nom de compte local à
  l'écran. Le volet, l'écran Ordinateur, le Terminal et les Réglages impriment une adresse :
  l'arbre d'accessibilité est relevé à côté de **chaque** image et sert de revue de texte
  (`grep -oE 'text="[^"]+"' <arbre>.xml`).
- La galerie montre le **build livré**. La rafraîchir est la **dernière** étape, sur l'APK publié.

## Captures de la galerie (build `1.0.0+976`, `versionCode 976`)

**Deux apparences.** Les fichiers réellement présents dans ce dossier, tous repris sur l'APK livré :

| # | Sombre (défaut) | Claire (`-light`) |
|---|---|---|
| 1 | `archive.png` | `archive-light.png` |
| 2 | `capabilities.png` | `capabilities-light.png` |
| 3 | `code-sessions.png` | `code-sessions-light.png` |
| 4 | `computer.png` | `computer-light.png` |
| 5 | `conversation-actions-sheet.png` | `conversation-actions-sheet-light.png` |
| 6 | `conversation-chat.png` | `conversation-chat-light.png` |
| 7 | `conversation-feed.png` | `conversation-feed-light.png` |
| 8 | `conversation-files-filters.png` | `conversation-files-filters-light.png` |
| 9 | `conversation-files.png` | `conversation-files-light.png` |
| 10 | `conversation-sidebar.png` | `conversation-sidebar-light.png` |
| 11 | `conversations.png` | `conversations-light.png` |
| 12 | `file-viewer.png` | `file-viewer-light.png` |
| 13 | `kanban.png` | `kanban-light.png` |
| 14 | `models.png` | `models-light.png` |
| 15 | `settings-about.png` | `settings-about-light.png` |
| 16 | `settings-connection.png` | `settings-connection-light.png` |
| 17 | `settings-voice-notifications.png` | `settings-voice-notifications-light.png` |
| 18 | `terminal.png` | `terminal-light.png` |
| 19 | `voice-listening.png` | `voice-listening-light.png` |
| 20 | `voice-ready.png` | `voice-ready-light.png` |

**40 fichiers : 20 sombres + 20 clairs.**

Deux écrans **ne suivent pas l'apparence** et sont donc identiques dans les deux jeux, par choix de
l'app : le **lecteur de fichiers** (`file-viewer`) garde un fond noir, et la **feuille d'actions**
(`conversation-actions-sheet`) assombrit la page derrière elle (voile de scrim). Ce n'est pas un
défaut de thème.

Écrans pas encore mis en image : l'accueil de l'app (`start.png`), le fil avec sous-agents
(`conversation-subagents.png`), les planifiés (`scheduled.png`), l'appel entrant
(`incoming-call.png`), la feuille de modèle (`model-sheet.png`).

Les onglets **Idées** et **Objectifs** ont été retirés de l'application : leurs captures
(`conversation-ideas.png`, `conversation-goals.png`) ont été retirées avec eux.

## Provenance

Quand une capture ne peut pas être re-prise, sa provenance se **note dans le README** (de quel
build vient l'image) plutôt que de la laisser non marquée : une image périmée non étiquetée est une
affirmation fausse, une image étiquetée est un écart connu.

Les 40 fichiers de ce dossier viennent du **même** build (`1.0.0+976`, `versionCode 976`) ; le
`sha256` du `base.apk` relu **sur l'appareil** vaut
`3ea97ce2985017a3cce24f36d82e5f6ee70fca8f5bd1aae451749cf8e1314d5d`.
