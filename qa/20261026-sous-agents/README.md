# Sous-agents: la route réparée, la liste des tâches, la transcription et le pilotage

Les captures de ce dossier sont prises sur un **état injecté** — un émulateur n'a ni Dashboard ni
agent, donc aucun sous-agent ne peut naître pour de vrai :

```
adb shell am start -n com.onyx.app/com.onyx.app.MainActivity --ez onyx.subagentDemo true
```

Le drapeau pose un fil et un roster (`SubagentStandIn`), et il ne change **rien** quand il est
absent. Les **états** couverts sont ceux que l'app sait dessiner : une tâche qui travaille (avec un
outil en cours), une qui démarre, une **lancée à l'aveugle** (celle de l'API de conversation, sans
session d'enfant) et une finie.

Ce qui n'est pas injecté : la lecture des trames du gateway, la route des RPC de pilotage et la
machine à états. Elles sont éprouvées par rejeu des **vraies** frames du gateway dans les tests
(`HermesServeSubagentTest`, `SubagentModelTest`) : c'est là qu'est la preuve du transport, pas dans
la capture.

## Ce que chaque transport donne

| | Dashboard `hermes serve` (WebSocket) | API de conversation `/v1` (ce que l'app utilise) |
|---|---|---|
| la ligne existe | trames `subagent.start/.progress/.thinking/.tool/.complete` sur la session du **parent** | seulement l'appel `delegate_task`, dont le libellé porte le but |
| but, place dans la série, profondeur, modèle | oui | but seulement, dans le libellé |
| dernier outil, tour d'outils | oui (`subagent.tool`, `tool_count`) | non |
| temps écoulé | oui (`started_at` du roster / `duration_seconds`) | l'horloge de l'app, depuis que l'appel est vu |
| session de l'enfant | oui (`child_session_id`) | **non** |
| transcription | `subagent.tail` (16 ko vivants) | **non transportée** |
| orienter | `subagent.steer` → `queued` / `rejected` | non transporté |
| arrêter | `subagent.interrupt` → `found` | non transporté |
| état montré | En cours | **Lancé** (le but est connu, l'état réel ne l'est pas) |

Deux points de contrat, vérifiés dans la source du gateway
(`tui_gateway/methods_subagents.py`, `tui_gateway/server.py`) :

1. `subagent.list` / `.tail` / `.steer` / `.interrupt` exigent l'identifiant **runtime** de la
   session (`_current_session_steer_authority`), pas l'identifiant conservé côté app ;
2. l'autorité de pilotage tient au **transport attaché à la session** : un socket neuf, même avec
   la bonne session, est refusé en `4001`. Le tour et les commandes partagent donc le même socket
   (`HermesServeChat(..., ownsLink = false)`).

## Fichiers

- `inject.py` — pose un serveur Dashboard sans passer par les formulaires (release APK : `adb root`
  + écriture du fichier `preferences_pb`).
- `capture.py` — la passe de captures, pilotée par **ancres d'accessibilité** (jamais de
  coordonnées écrites) : conversation, liste des tâches, transcriptions, consigne, arrêt.
- `10-…` conversation (rangées + capsule), `20-/21-…` liste (à 8 s d'écart), `30-/31-…`
  transcription et consigne envoyée, `40-…` arrêt effectif.
