# « Hermes nu » — ce qu'Onyx voit d'un serveur sans compagnon

L'objectif du client est simple à énoncer : **quelqu'un installe Onyx, branche le Hermes de son PC,
et tout ce qui s'affiche fonctionne.** Ce document dit comment on le vérifie, et ce qui a été
mesuré.

## La chaîne à prouver

```
serveur sans le plugin  →  route absente  →  sonde : NO  →  l'entrée n'est pas dessinée
```

Chaque maillon est vérifiable séparément, et c'est ce qui rend la règle tenable.

## Ce qui a été mesuré

**1. Un `HERMES_HOME` vierge ne monte aucune route de compagnon.**
Sur un home neuf avec `plugins.enabled: []`, l'application Hermes ne monte que les plugins
embarqués : `hermes-achievements` et `kanban`. Aucune route `/api/plugins/onyx-vault/*` n'apparaît
dans la table de routage.

**2. Un plugin désactivé répond 404, pas 500 ni une page vide.**
C'est écrit dans les tests du coffre eux-mêmes (`plugins/onyx-vault/tests/test_dashboard_mount.py`,
cas `[6]`) :

```
plugin désactivé -> 404 {"detail":"Plugin not found"}
```

**3. Une requête non authentifiée répond 401 avant tout routage.**
Mesuré sur le dashboard en marche : `/api/plugins/onyx-vault/health` sans session → `401`. C'est
important pour la sonde, et c'est pour ça qu'elle classe :

| Statut | Classe | Conséquence |
|---|---|---|
| `2xx` | `YES` | l'entrée est dessinée |
| `404` / `405` / `501` | `NO` | l'entrée **n'existe pas** |
| `401` / `403` / `5xx` | `UNKNOWN` | « Not confirmed » — jamais un oui |

Un `401` n'est pas une absence : c'est « je n'ai pas pu demander ». La sonde ne le grave pas en
cache, et l'écran écrit « Not confirmed » au lieu de promettre ou de cacher.

**4. Côté client, `NO` ferme la porte.**
`ServerProbeTest` fige : `404 → Support.NO`, `UNKNOWN` traité comme `NO` par `allows()`, et
`CapabilityLabelTest` fige ce que l'écran écrit dans chaque cas.

## Ce que ça donne pour l'utilisateur

| Fonction | Hermes nu | Avec le compagnon |
|---|---|---|
| Chat, bots, rooms, voix, Computer, Kanban, Cron, Code, fichiers | marche | marche |
| Skills par `/`, partage de modèle, capacités du serveur | marche | marche |
| Meneur de room, passage de main (v1 client) | marche | marche |
| Coffre de secrets | **entrée absente** | marche |
| Démonstration → skill | **entrée absente** | marche |

## Ce qui reste à faire pour que ce soit une recette, pas une intention

- Un test **instrumenté** sur appareil : brancher l'app sur un Hermes nu et vérifier qu'aucune
  entrée de coffre n'apparaît. Aujourd'hui la chaîne est prouvée par ses deux extrémités (serveur
  et unités Kotlin), pas par un parcours d'écran.
- Le même parcours sur un Hermes **avec** le compagnon, pour vérifier que l'entrée apparaît *après*
  la sonde, sans redémarrage de l'app.
