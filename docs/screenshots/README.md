# Galerie de captures — conventions

Ce dossier porte les images du README. Les **emplacements sont préparés** : les fichiers listés
ci-dessous sont **attendus** pour la version courante. Aucune capture périmée ne doit être laissée
ici : une image d'une version antérieure montre une interface qui n'existe plus et se lit comme
l'état courant.

## Convention de nommage (déjà en place dans le dépôt)

- **Pas de suffixe = thème sombre** (le thème par défaut). Ex. `conversation-chat.png`.
- Casse : minuscules, mots séparés par un tiret.
- **Une seule apparence pour toute la galerie : la sombre.** Aucun fichier `-light`.

## Règles de prise de vue

- Capture prise sur un **émulateur**, contre un serveur de test à données de démonstration —
  jamais sur l'appareil de l'auteur.
- **Barre d'état retirée** de chaque image ; l'**adresse** du serveur de démo est **floutée**.
- Aucune adresse réelle, aucun jeton, aucune conversation réelle, aucun nom de compte local à
  l'écran. Le volet et les Réglages impriment une adresse : vérifier l'arbre d'accessibilité de
  l'écran avant de garder une capture.
- La galerie montre le **build livré**. La rafraîchir est la **dernière** étape, sur l'APK publié.

## Captures de la galerie (version courante)

**Apparence sombre uniquement.** Les fichiers réellement présents dans ce dossier, tous repris sur l'APK livré :

| # | Fichier |
|---|---|
| 1 | `archive.png` |
| 2 | `capabilities.png` |
| 3 | `code-sessions.png` |
| 4 | `computer.png` |
| 5 | `conversation-actions-sheet.png` |
| 6 | `conversation-chat.png` |
| 7 | `conversation-feed.png` |
| 8 | `conversation-files-filters.png` |
| 9 | `conversation-files.png` |
| 10 | `conversation-sidebar.png` |
| 11 | `conversations.png` |
| 12 | `file-viewer.png` |
| 13 | `kanban.png` |
| 14 | `models.png` |
| 15 | `settings-about.png` |
| 16 | `settings-connection.png` |
| 17 | `settings-voice-notifications.png` |
| 18 | `terminal.png` |
| 19 | `voice-listening.png` |
| 20 | `voice-ready.png` |

Écrans pas encore mis en image (à produire **en sombre**, jamais en clair) : l'accueil (`start.png`), le fil avec sous-agents (`conversation-subagents.png`), les planifiés (`scheduled.png`), l'appel entrant (`incoming-call.png`), la feuille de modèle (`model-sheet.png`).

Les onglets **Idées** et **Objectifs** ont été retirés de l'application : leurs captures (`conversation-ideas.png`, `conversation-goals.png`) ont été retirées avec eux.

## Provenance

Quand une capture ne peut pas être re-prise, sa provenance se **note dans le README** (de quel
build vient l'image) plutôt que de la laisser non marquée : une image périmée non étiquetée est une
affirmation fausse, une image étiquetée est un écart connu.
