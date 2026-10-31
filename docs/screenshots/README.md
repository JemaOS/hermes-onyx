# Galerie de captures — conventions

Ce dossier porte les images du README. Les **emplacements sont préparés** : les fichiers listés
ci-dessous sont **attendus** pour la version courante. Aucune capture périmée ne doit être laissée
ici : une image d'une version antérieure montre une interface qui n'existe plus et se lit comme
l'état courant.

## Convention de nommage (déjà en place dans le dépôt)

- **Pas de suffixe = thème sombre** (le thème par défaut). Ex. `conversation-chat.png`.
- **Suffixe `-light` = thème clair**, même écran. Ex. `conversation-chat-light.png`.
- Casse : minuscules, mots séparés par un tiret.
- Un écran, deux fichiers : `<écran>.png` et `<écran>-light.png`.

## Règles de prise de vue

- Capture prise sur un **émulateur**, contre un serveur de test à données de démonstration —
  jamais sur l'appareil de l'auteur.
- **Barre d'état retirée** de chaque image ; l'**adresse** du serveur de démo est **floutée**.
- Aucune adresse réelle, aucun jeton, aucune conversation réelle, aucun nom de compte local à
  l'écran. Le volet et les Réglages impriment une adresse : vérifier l'arbre d'accessibilité de
  l'écran avant de garder une capture.
- La galerie montre le **build livré**. La rafraîchir est la **dernière** étape, sur l'APK publié.

## Captures attendues pour la version courante

À produire **en sombre puis en clair** (le second fichier porte `-light`) :

| # | Écran | Fichier sombre | Fichier clair |
|---|---|---|---|
| 1 | Accueil | `start.png` | `start-light.png` |
| 2 | Le volet, sur le fil (téléphone) | `conversations.png` | `conversations-light.png` |
| 3 | Le volet, posé à côté (grand écran) | `conversation-sidebar.png` | `conversation-sidebar-light.png` |
| 4 | Le fil, avec outils | `conversation-chat.png` | `conversation-chat-light.png` |
| 5 | Le fil, avec sous-agents | `conversation-subagents.png` | `conversation-subagents-light.png` |
| 6 | Réglages | `settings-connection.png` | `settings-connection-light.png` |
| 7 | Capacités | `capabilities.png` | `capabilities-light.png` |
| 8 | Fichiers | `conversation-files.png` | `conversation-files-light.png` |
| 9 | Fichiers — filtres | `conversation-files-filters.png` | `conversation-files-filters-light.png` |
| 10 | Planifiés | `scheduled.png` | `scheduled-light.png` |
| 11 | Kanban | `kanban.png` | `kanban-light.png` |
| 12 | Code | `code-sessions.png` | `code-sessions-light.png` |
| 13 | L'appel entrant | `incoming-call.png` | `incoming-call-light.png` |
| 14 | La feuille de modèle | `model-sheet.png` | `model-sheet-light.png` |

Écrans déjà capturés et conservés s'ils n'ont pas changé (à re-vérifier avant de garder) :
`conversation-feed.png`, `conversation-ideas.png`, `conversation-goals.png`, `file-viewer.png`,
`voice-listening.png`, `voice-ready.png`, `computer.png`, `terminal.png`,
`conversation-actions-sheet.png`, `settings-voice-notifications.png`, `settings-about.png`,
`models.png`, `archive.png`.

## Provenance

Quand une capture ne peut pas être re-prise, sa provenance se **note dans le README** (de quel
build vient l'image) plutôt que de la laisser non marquée : une image périmée non étiquetée est une
affirmation fausse, une image étiquetée est un écart connu.
