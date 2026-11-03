# Spécification serveur — démonstration → skill

**Lot 3 de `ARCHITECTURE-TOP5.md`** (§2 « Lot 3 — Teach a task », §3 « Ce qui reste côté Hermes »,
ADR-1 §7). Ce document ne remplace pas le plan : il en fixe le contrat des trois routes et le format
du journal. Aucune ligne d'implémentation n'y figure.

Les renvois `ARCHITECTURE-TOP5.md:<ligne>` désignent ce plan tel qu'il est sur `main` (`e8c195a`) :
il n'est pas dans la branche de ce document, et ses numéros de ligne sont ceux de cette version.

Ce que le plan décide et que ce document **ne rediscute pas** : le journal est fait d'**actions**
seulement (ADR-1, `ARCHITECTURE-TOP5.md:258-277`), le plafond est de 10 minutes, la fonction est
derrière `ServerProbe` et le réglage `top5.recording` éteint par défaut (`ARCHITECTURE-TOP5.md:121`).

---

## 0. Les cinq décisions que ce document tranche

| # | Décision | Pourquoi |
|---|---|---|
| D1 | Les trois routes vivent dans un **second plugin**, `onyx-record`, pas dans `onyx-vault`. | Le coffre a pour garde unique « une valeur n'est jamais relue » (`ARCHITECTURE-TOP5.md:54-56`). Un journal doit être relu pour être converti : le mettre dans le même composant affaiblirait la garde du coffre au nom d'un voisinage de lot. |
| D2 | Le journal vit **sur le téléphone** pendant l'enregistrement ; il monte au serveur **une fois**, dans l'appel `stop`. | Le pont Computer est un socket entre le téléphone et le PC (`ComputerBridgeClient.kt:61`) : le serveur ne voit aucune action. Rien ne peut donc être observé côté serveur, et rien ne quitte l'appareil avant que l'utilisateur demande la fin (ADR-1). |
| D3 | `stop` rend le journal **canonique** : ce que le serveur a validé et borné, pas ce que le téléphone a envoyé. | Le téléphone et le serveur peuvent diverger sur les bornes (version, taille, secret). Une seule version fait foi, sinon le test « 40 événements → un markdown stable » (`ARCHITECTURE-TOP5.md:120`) dépend de qui a raison. |
| D4 | `skills.draft` est **sans état**. Un bail expiré ne coûte jamais le brouillon. | Le journal reste sur le téléphone. Une expiration ne doit pas punir l'utilisateur d'avoir appuyé 30 secondes trop tard. |
| D5 | `skills.draft` **n'écrit rien**. Enregistrer un skill reste `POST api/skills` (`ServeSkillsApi.kt:78-88`). | Une seule route écrit un skill, et elle existe déjà. Un brouillon qu'on n'a pas relu ne doit pas atterrir dans `~/.hermes/skills`. |

---

## 1. Où vivent les routes

Plugin `onyx-record`, monté sous `/api/plugins/onyx-record/` par le même mécanisme que `onyx-vault`
(`ARCHITECTURE-TOP5.md:163-181`, montage prouvé `:183-218`). Le client les appelle par
`ServeClient.request(path, method, query, body, timeoutSeconds)` (`ServeClient.kt:128`), le chemin
relatif à l'adresse du Dashboard (`ServeClient.kt:191`).

| Nom de la route | Méthode | Chemin | Lot |
|---|---|---|---|
| `skills.record.start` | `POST` | `api/plugins/onyx-record/skills/record/start` | 3 |
| `skills.record.stop` | `POST` | `api/plugins/onyx-record/skills/record/stop` | 3 |
| `skills.draft` | `POST` | `api/plugins/onyx-record/skills/draft` | 3 |

Trois conséquences du montage, à écrire dans le lot :

- **Le plugin doit être activé.** Présent mais absent de `plugins.enabled`, son code n'est jamais
  importé et aucune route ne monte (`ARCHITECTURE-TOP5.md:201-211`).
- **L'authentification du Dashboard est devant.** Sans clé ni session : `401`
  (`ARCHITECTURE-TOP5.md:195`). Onyx passe la même identité que pour le reste.
- **Pas de nouvelle famille de client.** Un fichier `ServeRecordApi.kt`, miroir de
  `ServeSkillsApi.kt`, rien d'autre (§0 règle 3, `ARCHITECTURE-TOP5.md:20-22`).

Forme d'un refus, sur les trois routes :

```json
{"detail": {"code": "already_recording", "message": "A recording is already open.", "session_id": "rec_01J9Z3K4Q7V8"}}
```

`code` est **la première clé** du corps : `ServeClient` ne garde que les 300 premiers caractères
d'une réponse en erreur (`ServeClient.kt:222`). Un code placé après un message long serait coupé.
`message` est en anglais, pour le journal du serveur ; **l'écran n'affiche jamais `message`**, il
affiche sa propre phrase (section 8).

---

## 2. Le journal d'actions — format exact

C'est le contrat entre les trois routes. Un journal qui n'y ressemble pas est refusé
(`422 bad_journal`), jamais réparé.

### 2.1 Objet racine

| Champ | Type | Obligatoire | Règle |
|---|---|---|---|
| `version` | entier | oui | `1`. Toute autre valeur : `422 version_unsupported`. |
| `session_id` | chaîne | oui | Le bail rendu par `start`. Un identifiant inconnu : `409 unknown_session`. |
| `started_at` | décimal | oui | Secondes Unix, trois décimales. Posé par le téléphone à l'ouverture du bail. |
| `duration_ms` | entier | oui | Durée mesurée par le téléphone, plafonnée à `600000`. Le serveur la recalcule et garde **la sienne**. |
| `end_reason` | chaîne | oui | `stopped` ou `expired`. Un client ne peut pas déclarer autre chose. |
| `screen` | objet | oui | `width_px`, `height_px` (entiers > 0), `scale` (décimal > 0). Sert à écrire « 42 % / 18 % de la fenêtre ». |
| `bridge` | objet | oui | `endpoint` (hôte:port, sans schéma, sans identifiant) et `session` (l'identifiant de session du pont, tel que `ComputerHello` le donne). |
| `events` | tableau | oui | 0 à 2000 entrées, `seq` strictement croissant. |

### 2.2 Un événement

| Champ | Type | Présent pour | Règle |
|---|---|---|---|
| `seq` | entier | tous | ≥ 1, strictement croissant. Les trous sont permis (`dropped` les explique). |
| `at_ms` | entier | tous | Millisecondes depuis `started_at`, croissantes. Un `at_ms` qui recule : `422 bad_journal`. |
| `kind` | chaîne | tous | `focus`, `tap`, `type`, `scroll`, `navigate`, `wait`, `key`. Rien d'autre. |
| `target` | objet | tous sauf `wait` | `label` (chaîne ≤ 200 ou absente), `role` (chaîne ≤ 40 ou absente), `window` (titre de la fenêtre au premier plan, ≤ 120 ou absent), `x_pct` / `y_pct` (décimaux 0..1, seulement pour `tap`). |
| `text` | chaîne | `type` non secret | ≤ 2000 caractères. **Absent** dès que `secret` est vrai. |
| `secret` | booléen | `type` | Vrai : le champ est masqué côté PC. |
| `chars` | entier | `type` | Longueur du texte saisi, même quand `secret` est vrai. |
| `direction` | chaîne | `scroll` | `up` ou `down`. |
| `amount` | entier | `scroll` | Crans, 1 à 100. |
| `url` | chaîne | `navigate` | ≤ 2048, schéma `http` ou `https` **seulement**. |
| `key` | chaîne | `key` | `back`, `home`, `enter`, `tab`, `escape`. |
| `ms` | entier | `wait` | 100 à 30000. |

Les sept `kind` sont ceux d'ADR-1 (`ARCHITECTURE-TOP5.md:260-262`) : `focus` s'ajoute parce qu'une
procédure sans « quelle fenêtre était devant » n'est pas rejouable, et il ne porte aucun contenu.
Le **glisser** n'est pas un `kind` : ADR-1 accepte de perdre les gestes purement visuels
(`ARCHITECTURE-TOP5.md:268-270`), et le brouillon ne prétend pas le contraire.

### 2.3 Un journal complet

```json
{
  "version": 1,
  "session_id": "rec_01J9Z3K4Q7V8",
  "started_at": 1730650000.123,
  "duration_ms": 12000,
  "end_reason": "stopped",
  "screen": {"width_px": 1440, "height_px": 900, "scale": 1.0},
  "bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"},
  "events": [
    {"seq": 1, "at_ms": 0, "kind": "focus",
     "target": {"label": "Finder", "role": "window", "window": "Finder"}},
    {"seq": 2, "at_ms": 1240, "kind": "tap",
     "target": {"label": "Nouveau dossier", "role": "button", "window": "Finder",
                "x_pct": 0.421, "y_pct": 0.183}},
    {"seq": 3, "at_ms": 2600, "kind": "type",
     "target": {"label": "Nom", "role": "text field", "window": "Finder"},
     "text": "Rapport 2026", "chars": 12},
    {"seq": 4, "at_ms": 3900, "kind": "type",
     "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
     "secret": true, "chars": 9},
    {"seq": 5, "at_ms": 5200, "kind": "key",
     "target": {"label": "Mot de passe", "role": "secure text field", "window": "Réglages"},
     "key": "enter"},
    {"seq": 6, "at_ms": 6100, "kind": "scroll",
     "target": {"label": "Liste des comptes", "role": "list", "window": "Réglages"},
     "direction": "down", "amount": 3},
    {"seq": 7, "at_ms": 7400, "kind": "navigate",
     "target": {"label": "Rapport 2026", "role": "link", "window": "Navigateur"},
     "url": "https://exemple.fr/rapport"},
    {"seq": 8, "at_ms": 9600, "kind": "wait", "ms": 1800},
    {"seq": 9, "at_ms": 12000, "kind": "tap",
     "target": {"window": "Aperçu", "x_pct": 0.512, "y_pct": 0.077}}
  ]
}
```

L'événement 9 n'a ni `label` ni `role` : c'est exactement le cas d'ADR-1 — il devient une étape
« toucher 51 % / 8 % de la fenêtre », **marquée à relire** (`ARCHITECTURE-TOP5.md:272-273`), jamais
une étape silencieuse.

### 2.4 Les bornes

| Borne | Valeur | Conséquence du dépassement |
|---|---|---|
| Durée d'un enregistrement | 600 s | L'enregistreur s'arrête seul, `end_reason = expired`. |
| Événements | 2000 | Les plus anciens sont abandonnés, `truncated = true`, `dropped` les compte. |
| Taille sérialisée | 262144 octets | Même règle que les événements. |
| `text` d'un `type` | 2000 caractères | `422 bad_journal`. |
| `label` d'une cible | 200 caractères | Tronqué à 200, `warnings` le dit. |
| Délai de collecte après la fin | 120 s | `410 session_expired` sur `stop`. |

---

## 3. `skills.record.start`

Ouvre un **bail** d'enregistrement. N'observe rien : le serveur ne voit ni écran ni action (D2).

**Qui l'ouvre.** Uniquement `ComputerScreen`, par un geste explicite de l'utilisateur sur l'entrée
« Montre-moi une fois » du menu de l'écran (`ComputerScreen.kt:418-479`). Jamais au chargement :
l'écran ouvre la session du pont tout seul (`ComputerScreen.kt:185`), et l'enregistrement ne doit
pas suivre ce geste-là. Ni depuis le fil, ni depuis les Compétences, ni depuis Wear ou Android Auto :
sans cet écran, il n'y a rien à journaliser.

**Requête**

```json
{
  "bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"},
  "discard_pending": false
}
```

| Champ | Règle |
|---|---|
| `bridge.endpoint` | Obligatoire, non vide, sans schéma ni identifiant. |
| `bridge.session` | Obligatoire, non vide : l'identifiant de session du pont. C'est **le seul sens possible** de « pas de pont Computer » côté serveur — la route ne peut pas constater l'absence d'un pont, elle peut seulement refuser un bail qui ne nomme aucun pont. |
| `discard_pending` | Facultatif, `false` par défaut. `true` : le journal en attente est jeté et le bail s'ouvre. |
| `profile` | **Interdit.** Présent → `400 profile_not_supported`. |

Pourquoi `profile` est refusé et non ignoré : `ServeSkillsApi.create` n'a pas de paramètre de profil
(`ServeSkillsApi.kt:78-88`). Accepter un profil à l'enregistrement promettrait un rangement que
l'enregistrement du skill ne sait pas tenir. Le profil se choisit là où il est encore possible :
pas dans ce lot.

Pourquoi `bridge.session` et pas un booléen : un bail attaché à une session de pont nommée rend le
mélange de deux PC impossible ; un journal livré sous une autre session est refusé
(`409 bridge_mismatch`), au lieu d'être accepté et attribué au mauvais poste.

**Réponse `200`**

```json
{
  "session_id": "rec_01J9Z3K4Q7V8",
  "started_at": 1730650000.123,
  "deadline_at": 1730650600.123,
  "max_seconds": 600,
  "grace_seconds": 120
}
```

`deadline_at` est l'heure **du serveur**. Le téléphone s'aligne dessus et ne tient pas son propre
plafond : deux horloges qui divergent produisent deux fins différentes.

**Refus**

| Statut | `code` | Quand | Ce que le client fait |
|---|---|---|---|
| 400 | `bad_bridge` | `bridge` absent, vide, ou `endpoint` avec schéma | Affiche « Le pont de l'écran n'est pas joignable. » |
| 400 | `profile_not_supported` | `profile` présent | N'arrive pas si le client respecte le contrat ; à corriger côté app. |
| 400 | `bad_request` | Corps illisible | Affiche `humanFailure` (défaut d'Onyx, pas de cette fonction). |
| 401 | — | Dashboard non authentifié | Le chemin normal de reconnexion (`ServeClient.kt:135-145`). |
| 404 | — | Route absente | **Section 8**, phrase exacte. |
| 409 | `already_recording` | Un bail est ouvert | Adopte le `session_id` et le `deadline_at` rendus, bascule en `recording`. Pas de second bail. |
| 409 | `journal_pending` | Un journal non converti est encore là | Propose de le convertir ou de le jeter ; `discard_pending: true` si l'utilisateur le jette. |
| 503 | `recorder_unavailable` | Le plugin n'a pas de place mémoire | « L'enregistrement n'est pas disponible sur ce serveur. » |

---

## 4. `skills.record.stop`

Ferme le bail, **reçoit** le journal, le valide, le borne, le garde 120 s et rend sa forme canonique.

**Requête**

```json
{"session_id": "rec_01J9Z3K4Q7V8", "journal": { "version": 1, "…": "…" }}
```

`journal: null` (ou absent) = **abandon** : le bail se ferme, rien n'est gardé, rien n'est rendu.
C'est le seul chemin d'annulation ; il n'y a pas de quatrième route.

**Réponse `200`**

```json
{
  "session_id": "rec_01J9Z3K4Q7V8",
  "stopped_at": 1730650012.5,
  "duration_ms": 12377,
  "actions": 9,
  "bytes": 2196,
  "truncated": false,
  "dropped": 0,
  "end_reason": "stopped",
  "journal": { "version": 1, "…": "…" }
}
```

| Champ | Sens |
|---|---|
| `actions` | Nombre d'événements **retenus**. |
| `bytes` | Taille UTF-8 du `journal` rendu, tel qu'il est écrit. |
| `duration_ms` | `stopped_at − started_at`, plafonné à 600000. C'est la durée du serveur, pas celle du client. |
| `truncated` / `dropped` | Vrai et compte non nul quand une borne de §2.4 a mordu. |
| `end_reason` | `stopped`, `expired` ou `discarded`. Quand le bail a expiré avant l'appel, c'est `expired` — **le client ne peut pas le déclarer autrement**. |
| `journal` | La forme canonique : mêmes événements, même ordre, champs inconnus retirés, bornes appliquées. Absent si `end_reason = discarded`. |

**Si la session a expiré.** Trois moments, trois comportements :

1. **Le bail est expiré, on est dans les 120 s de collecte.** `200`, `end_reason: "expired"`,
   `truncated: true` si des événements sont arrivés après 600 s (le serveur les jette et les compte
   dans `dropped`). Le journal est accepté : un plafond qui jette dix minutes parce qu'on a appuyé
   tard n'est pas un plafond, c'est une punition.
2. **Le bail est expiré depuis plus de 120 s.** `410 session_expired`, sans corps de journal. Le
   téléphone garde le sien : `skills.draft` en ligne fonctionne encore (D4).
3. **Le bail n'a jamais existé** (`session_id` inconnu). `409 unknown_session`. Le téléphone peut
   tout de même convertir son journal en ligne.

`stop` est **idempotent** dans la fenêtre de collecte : un second appel rend le même `journal` et le
même `bytes`, à l'octet. C'est ce qui permet de récupérer un journal après la mort de l'écran.

**Refus**

| Statut | `code` | Quand |
|---|---|---|
| 400 | `bad_request` | Corps illisible, ou `journal` sans `session_id`. |
| 404 | — | Route absente (section 8). |
| 409 | `not_recording` | Aucun bail ouvert : rien à arrêter. |
| 409 | `bridge_mismatch` | Le `journal.bridge.session` n'est pas celui du bail. |
| 409 | `unknown_session` | `session_id` inconnu de ce serveur. |
| 410 | `session_expired` | Bail expiré depuis plus de 120 s. |
| 413 | `journal_too_large` | Journal > 262144 octets **avant** bornage. |
| 422 | `bad_journal` | §2 non respectée. |
| 422 | `version_unsupported` | `version` ≠ 1. |
| 422 | `secret_text_present` | Un événement `secret: true` porte un `text`. Voir section 6. |

---

## 5. `skills.draft`

Reçoit un journal, rend un brouillon Markdown et la liste des étapes à relire. **Sans état** : deux
appels avec le même journal rendent le même octet. Aucune écriture, aucun fichier, aucun profil.

**Requête**

```json
{
  "journal": { "version": 1, "…": "…" },
  "journal_id": null,
  "name_hint": "creer-un-dossier-dans-finder"
}
```

| Champ | Règle |
|---|---|
| `journal` | Le journal complet, même forme qu'en §2. |
| `journal_id` | Facultatif, exclusif avec `journal`. Le `session_id` d'un journal encore en collecte. Les deux ensemble : `400 two_journals`. |
| `name_hint` | Facultatif. Sinon, le nom est dérivé du premier `focus` et de la première cible nommée, en minuscules à traits d'union. |

**Réponse `200`**

```json
{
  "name": "creer-un-dossier-dans-finder",
  "title": "Créer un dossier dans Finder",
  "steps": 7,
  "markdown": "---\nname: creer-un-dossier-dans-finder\ndescription: Créer un dossier dans Finder, puis ouvrir un lien.\n---\n\n## Quand l'utiliser\n\nQuand un dossier doit être créé dans Finder à partir d'un nom donné.\n\n## Procédure\n\n1. Ouvrir **Finder**.\n2. Toucher **Nouveau dossier**.\n3. Saisir `Rapport 2026` dans **Nom**.\n4. Saisir le mot de passe dans **Mot de passe** (champ masqué, non enregistré).\n5. Valider avec **Entrée**.\n6. Faire défiler **Liste des comptes** vers le bas (3 crans).\n7. Ouvrir le lien **Rapport 2026** (https://exemple.fr/rapport).\n8. Toucher 51 % / 8 % de la fenêtre **Aperçu** (à relire : aucun libellé).\n\n## Vérification\n\nLe dossier est visible dans Finder et le lien s'ouvre dans le navigateur.\n",
  "review": [
    {"seq": 9, "step": 8, "reason": "no_label", "line": 21},
    {"seq": 4, "step": 4, "reason": "secret_field", "line": 17}
  ],
  "warnings": ["label_truncated"],
  "journal_bytes": 2196
}
```

| Champ | Sens |
|---|---|
| `name` | Le nom de dossier du skill, minuscules à traits d'union, ≤ 64 caractères. C'est `ServeSkillsApi.create(name, …)` qui l'écrira. |
| `title` | Le titre lisible, première ligne du corps. |
| `steps` | Nombre d'étapes numérotées dans `markdown`. |
| `review` | Les étapes à relire, dans l'ordre du brouillon. Vide = rien à relire. |
| `review[].step` | Le numéro de l'étape (1 = première ligne de « Procédure »). |
| `review[].line` | Ligne du `markdown`, à partir de 1. **Indicatif** : l'éditeur la borne à la longueur réelle et ne s'y fie pas pour placer le curseur. |
| `review[].reason` | `no_label`, `secret_field`, `no_window`, `long_wait`, `unsupported_kind`. Liste fermée. |
| `warnings` | Ce qui a été **modifié** pour tenir : `label_truncated`, `text_truncated`, `url_dropped`, `wait_clamped`, `events_dropped`. |
| `journal_bytes` | La taille du journal reçu, pour que l'écran puisse dire ce qu'il a envoyé. |

**Le brouillon ne devine pas.** Chaque événement devient une étape, dans l'ordre. Un `wait` de moins
de 500 ms ne devient pas une étape (il n'apprend rien) mais compte dans `warnings` s'il a été
raccourci. Un `kind` inconnu de cette version du serveur devient une étape « à relire » de raison
`unsupported_kind` : c'est la seule façon de ne pas perdre une étape en silence.

**Aucune donnée de session dans le Markdown.** Pas de date, pas de `session_id`, pas de nom d'hôte,
pas d'adresse de pont. Sinon le même journal produirait deux brouillons différents, et le test du
convertisseur (`ARCHITECTURE-TOP5.md:120`) ne pourrait pas être stable.

**La forme du Markdown suit ce que l'éditeur annonce déjà.** L'écran d'édition promet « Front matter
(name, description, version, metadata) then When to Use, Procedure, Pitfalls, Verification »
(`SkillEditorScreen.kt:146`). Le brouillon écrit exactement ces sections : front matter, « Quand
l'utiliser », « Procédure », « Vérification ». Un brouillon d'une autre forme aurait l'air étranger
dans son propre éditeur.

**Refus**

| Statut | `code` | Quand |
|---|---|---|
| 400 | `bad_request` | Corps illisible, ni `journal` ni `journal_id`. |
| 400 | `two_journals` | Les deux champs sont présents. |
| 404 | — | Route absente (section 8). |
| 409 | `still_recording` | `journal_id` désigne un bail encore ouvert. |
| 409 | `no_journal` | `journal_id` inconnu, ou bail abandonné. |
| 413 | `journal_too_large` | Journal > 262144 octets. |
| 422 | `bad_journal` | §2 non respectée. |
| 422 | `version_unsupported` | `version` ≠ 1. |
| 422 | `secret_text_present` | Un `secret: true` porte un `text`. |

---

## 6. Ce qui est interdit d'enregistrer

Interdictions du journal, vérifiées par le serveur et pas seulement par le téléphone.

| Interdit | Comment c'est tenu | Refus si la règle est violée |
|---|---|---|
| **Pixels** — capture, vignette, recadrage, OCR de l'écran | Aucun champ ne porte d'image. La liste des `kind` est fermée (§2.2). | `422 bad_journal` (un champ `image`, `png`, `screenshot` à n'importe quelle profondeur). |
| **Audio** — microphone, son du système, transcription | Aucun `kind` audio. La scène vocale n'est pas dans ce chemin. | `422 bad_journal`. |
| **Presse-papiers** | Le presse-papiers n'est jamais lu. Un **collage** est enregistré comme un `type` avec `secret: true` et `chars` seulement : la sensibilité d'un contenu de presse-papiers est inconnue au moment du collage, donc il est traité comme secret par défaut. | Un événement `type` portant `from_clipboard: true` et un `text` : `422 bad_journal`. |
| **Champ marqué secret** | `text` absent, `chars` seul. Le brouillon écrit « champ masqué, non enregistré ». | `422 secret_text_present` **si** un `text` accompagne `secret: true`. Le serveur refuse, il ne nettoie pas en silence — même règle que le lien de template contenant `apiKey` (`ARCHITECTURE-TOP5.md:133`). |
| **Chemin local d'un document** | `url` n'accepte que `http` et `https`. Un fichier ouvert devient un `label` portant son **nom**, jamais son chemin. | `422 bad_journal` (schéma `file:` ou `onyx:`). |
| **Identifiant de pont complet** | `bridge.endpoint` est un hôte:port ; un identifiant, un jeton ou un mot de passe dans l'adresse : `400 bad_bridge`. | `400 bad_bridge`. |

Le téléphone applique les mêmes règles **avant** l'envoi : le serveur est le second verrou, pas le
premier. Un journal qui n'a pas passé le premier ne doit pas exister.

---

## 7. Machine à états

Quatre états côté téléphone, deux côté serveur. Le serveur ne connaît pas `drafted` : `skills.draft`
est sans état (D4).

```
        start(200)              stop(journal)
 idle ────────────► recording ──────────────► stopped ─── draft(200) ───► drafted
   ▲                    │                        │                          │
   │   stop(null)       │ 600 s (serveur)        │ start(discard_pending)   │ start(200)
   └────────────────────┘                        └──────────────────────────┴──► recording
```

### 7.1 Ce que tient le serveur à chaque étape

| État du téléphone | État du serveur | Détail |
|---|---|---|
| `idle` | rien | Aucun bail. |
| `recording` | `recording` | Bail ouvert, `deadline_at` connu des deux. |
| `stopped` | `journal_pending` | Journal canonique gardé 120 s. |
| `drafted` | `journal_pending` | Le serveur n'a rien vu du brouillon ; le journal reste jusqu'à la fin des 120 s. |

### 7.2 Les transitions invalides

| Depuis | Geste | Réponse | Ce que le client affiche |
|---|---|---|---|
| `recording` | `start` | `409 already_recording` | Le bouton n'existe pas dans cet état. Si l'appel part quand même (vue périmée), le client **adopte** le `session_id` rendu et reste en `recording`. |
| `idle` | `stop` | `409 not_recording` | « Aucun enregistrement n'est ouvert. » Le bouton n'existe pas. |
| `recording` | `draft` | `409 still_recording` | Le bouton de conversion n'existe pas avant l'arrêt. |
| `idle` | `draft` | `409 no_journal` | « Rien à convertir : la démonstration n'a pas été enregistrée. » |
| `stopped` | `start` | `409 journal_pending` | « Une démonstration attend d'être convertie. » Deux issues offertes : convertir, ou `discard_pending: true`. |
| `stopped` / `drafted` | `stop` | `200`, journal identique | Rien à signaler : l'appel est idempotent. |
| `drafted` | `draft` | `200`, même octet | Rien à signaler : convertir deux fois le même journal est permis. |

Aucune de ces transitions n'est une panne : le client ne doit **jamais** afficher « Réessayez » pour
l'une d'elles. C'est la distinction que `looksLikeMissingRoute` fait déjà pour les routes absentes
(`PageStates.kt:113-123`) ; ici, la même règle s'applique aux conflits d'état.

### 7.3 Ce que l'écran montre, état par état

| État | Où | Ce qui est affiché |
|---|---|---|
| `idle` | `ComputerScreen`, menu | Entrée « Montre-moi une fois », active. |
| `idle`, pas de pont configuré | `ComputerScreen`, menu | **Aucune entrée.** `ComputerBridgeStore.configured` est faux (`ComputerBridgeStore.kt:63`) : il n'y a rien à montrer. |
| `idle`, pont configuré, phase ≠ `LIVE` | `ComputerScreen`, menu | Entrée **grisée**, avec « L'écran du PC n'est pas encore ouvert. » État transitoire, il se résout seul. |
| `recording` | bandeau en haut de l'aperçu | « Enregistrement — arrêt automatique dans 9 min 12 s. » plus le bouton « Arrêter ». |
| `stopped` | feuille de résultat | « Démonstration terminée : 9 actions, 12 s. » puis « Convertir en skill » / « Jeter ». |
| `drafted` | `SkillEditorScreen` | Le Markdown pré-rempli, et une ligne par étape à relire (`review`). |
| `drafted`, refus d'enregistrement | `SkillEditorScreen` | `humanFailure` de `POST api/skills` — le refus existant, pas une nouvelle phrase. |

Le réglage `top5.recording` éteint par défaut (`ARCHITECTURE-TOP5.md:121`) supprime l'entrée du menu,
exactement comme l'absence de pont. Il ne grise rien : une fonction qu'on a éteinte n'est pas une
fonction en panne.

---

## 8. Replis — et la phrase exacte quand la route n'existe pas

### 8.1 Serveur sans la route

Détection par `looksLikeMissingRoute` : `404` HTTP ou `-32601` (`PageStates.kt:113-123`). Le plugin
absent, non activé, ou un Dashboard plus ancien donnent tous les deux la même réponse — c'est
pourquoi la phrase ne parle pas du serveur mais de la fonction.

| Route absente | Phrase exacte affichée (FR) | Clé EN |
|---|---|---|
| `skills.record.start` | « Ce serveur ne sait pas enregistrer une démonstration (route absente). » | `This server can't record a demonstration (route missing).` |
| `skills.record.stop` | « Ce serveur ne sait pas arrêter un enregistrement (route absente). » | `This server can't close a recording (route missing).` |
| `skills.draft` | « Ce serveur ne sait pas transformer une démonstration en skill (route absente). Le journal reste sur cet appareil. » | `This server can't turn a demonstration into a skill (route missing). The journal stays on this device.` |

Trois règles autour de ces phrases :

- **`humanFailure` ne convient pas.** Sur un `404`, il dit « Endpoint introuvable (404). Vérifiez
  l'URL de base. » (`TransportError.kt:141`) : c'est faux, l'adresse est bonne et la route manque.
  Sur un `-32601`, il dit « Le serveur a refusé : … » (`TransportError.kt:160`) : c'est vrai mais
  inutile. Les trois phrases ci-dessus remplacent ces deux-là.
- **Elles vivent dans le catalogue**, pas dans un écran : un fichier `OnyxFrenchTop5.kt` ajouté à la
  concaténation de `OnyxLocalization.kt:221` (`ARCHITECTURE-TOP5.md:241-242`). Un libellé écrit en
  clair dans un `MenuItem(…)` sans entrée française fait échouer `MenuAndSheetLocalizationTest`
  (`MenuAndSheetLocalizationTest.kt:56-73`).
- **La phrase ne promet rien.** Elle ne dit ni « bientôt », ni « mettez à jour le serveur » : le
  téléphone ne connaît pas la version du Dashboard.

### 8.2 Hors ligne

`TransportError.Unreachable` : la phrase est celle d'Onyx (« Serveur injoignable… »,
`TransportError.kt:216-227`), pas une des trois ci-dessus. Un serveur injoignable n'est pas un
serveur sans la route ; confondre les deux ferait chercher une mise à jour inutile.

### 8.3 Ce qui reste vrai dans tous les cas

- Un enregistrement en cours **survit** à une coupure réseau : le journal est sur le téléphone (D2).
  Le bail, lui, court toujours ; `stop` peut arriver 60 s plus tard.
- `stop` perdu (réponse jamais reçue) : le client retente avec le **même** `session_id` ; l'appel est
  idempotent, il ne double pas le journal.
- Le brouillon ne dépend d'aucune route déjà montée sauf `POST api/skills` pour l'enregistrement
  final (`ServeSkillsApi.kt:78-88`), qui existe depuis toujours.

---

## 9. Accroches dans le code existant

| Affirmation de ce document | Fichier réel |
|---|---|
| Tout passe par un seul transport, `request(path, method, query, body, timeoutSeconds)` | `core/.../data/net/ServeClient.kt:128` |
| Le corps d'une erreur est coupé à 300 caractères | `core/.../data/net/ServeClient.kt:222` |
| Le chemin est relatif à l'adresse du Dashboard | `core/.../data/net/ServeClient.kt:191` |
| Un cookie mort est rattrapé une fois | `core/.../data/net/ServeClient.kt:135-145` |
| Le miroir d'une famille de routes est un fichier `*Api.kt` | `core/.../data/net/ServeSkillsApi.kt:49-52` |
| Créer un skill ne prend **pas** de profil | `core/.../data/net/ServeSkillsApi.kt:78-88` |
| Une route absente rend un objet « désactivé » plutôt qu'une exception | `core/.../data/net/ServeChatActions.kt:516-526` |
| Le refus « ce serveur n'offre pas cette lecture » existe déjà | `core/.../data/net/ServeChatActions.kt:400-419` |
| L'état d'une lecture est `LoadState` : `Loading`, `Ready`, `Failed(reason)` | `app/.../ui/chat/components/ChatActionSheet.kt:808-812` |
| Un `404` ou un `-32601` se reconnaît comme une route absente | `app/.../ui/common/PageStates.kt:113-123` |
| `humanFailure` dit « vérifiez l'URL » sur un 404 | `core/.../data/net/TransportError.kt:141` |
| Le pont Computer expose déjà tap, saisie, défilement, touche, commande | `core/.../data/net/ComputerBridgeClient.kt:571-685` |
| La phase du pont est `IDLE` / `CONNECTING` / `LIVE` / `RECONNECTING` / `FAILED` | `core/.../data/net/ComputerBridgeClient.kt:71-79` |
| « Pas de pont » se lit sur `configured` | `app/.../data/local/ComputerBridgeStore.kt:63` |
| L'écran Computer ouvre sa session au chargement — l'enregistrement ne doit pas suivre | `app/.../ui/computer/ComputerScreen.kt:185` |
| Le menu de l'écran Computer est une liste d'items conditionnels | `app/.../ui/computer/ComputerScreen.kt:418-478` |
| L'éditeur de skill annonce les sections du Markdown | `app/.../ui/capabilities/SkillEditorScreen.kt:146` |
| Sa signature accepte un paramètre par défaut sans casser un appelant | `app/.../ui/capabilities/SkillEditorScreen.kt:54-61` |
| Les réglages sont des clés de `onyx_settings` | `app/.../data/local/OnyxDataStore.kt:53-56` |
| Le catalogue français se concatène en un point unique | `core/.../localization/OnyxLocalization.kt:221` |
| Un libellé de menu sans traduction fait échouer un test | `core/.../test/.../MenuAndSheetLocalizationTest.kt:56-73` |

---

## 10. Accroche du lot dans l'app

Aucune destination nouvelle : `Route` (`OnyxApp.kt:234`) et `RouteRetours.kt` ne bougent pas.

- L'entrée du menu : `ComputerScreen`, une ligne de plus dans la liste conditionnelle
  (`ComputerScreen.kt:418-479`).
- Le brouillon : `SkillEditorScreen` reçoit `draft` en paramètre **avec valeur par défaut**, comme
  `BotEditorScreen` a reçu ses lambdas nulles (`ARCHITECTURE-TOP5.md:13-16`). Sans brouillon,
  l'écran est celui d'aujourd'hui, à l'identique.
- La routine : le lien existant vers `CronScreen` (`CronScreen.kt:98-107`). Ce lot n'ajoute aucune
  route de cron.

---

## 11. Ce que ce lot ne fait pas

- Il n'enregistre pas les gestes visuels (glisser, dessiner, choisir une couleur). ADR-1 l'accepte
  (`ARCHITECTURE-TOP5.md:268-270`).
- Il n'écrit aucun skill. `skills.draft` rend du texte ; `POST api/skills` l'écrit.
- Il ne crée pas de routine. Le bouton emprunte le chemin existant de `CronScreen`.
- Il ne journalise rien en arrière-plan : pas d'enregistrement hors de `ComputerScreen`, pas de
  service de premier plan.
- Il ne touche ni `Computer`, ni la scène vocale, ni les rooms (§0 règle 5,
  `ARCHITECTURE-TOP5.md:26-28`).

---

## 12. Preuves attendues

1. **Convertisseur pur** : 40 événements en entrée, un Markdown identique en sortie, deux appels
   donnent le même octet (`ARCHITECTURE-TOP5.md:120`).
2. **Refus du secret** : un journal dont un `secret: true` porte un `text` rend `422
   secret_text_present`, et le corps de la réponse ne contient **pas** ce texte.
3. **Bornes** : 2001 événements → `truncated: true`, `dropped: 1`, `actions: 2000`.
4. **Expiration** : un bail dépassé de 100 s rend `200 end_reason: expired` ; dépassé de 200 s rend
   `410`, et `skills.draft` en ligne rend quand même un brouillon.
5. **Idempotence** : deux `stop` dans la fenêtre rendent le même `bytes`.
6. **Absence** : sans le plugin, l'entrée du menu n'est pas dessinée, et aucune des trois phrases de
   §8.1 ne laisse place à « Réessayez ».
7. **Localisation** : les trois phrases de §8.1 et les libellés de §7.3 sont dans le catalogue
   français ; `MenuAndSheetLocalizationTest` passe.

---

## 13. Décisions ouvertes

| # | Question | Ce que je propose | Ce qu'il faut pour trancher |
|---|---|---|---|
| O1 | Second plugin `onyx-record`, ou routes ajoutées à `onyx-vault` ? | Second plugin (D1). | L'avis de qui installera : deux dossiers à activer, ou un seul composant qui fait deux choses. |
| O2 | Le journal monte-t-il à `stop` (D2), ou continue-t-il à vivre uniquement sur le téléphone avec un `skills.draft` en ligne ? | Il monte à `stop`, parce que la tâche demande que `stop` rende le journal. | Rien de technique : c'est un choix de produit sur ce qui quitte l'appareil. |
| O3 | Faut-il un `POST …/record/discard` plutôt que `stop` avec `journal: null` ? | Non : trois routes suffisent, et `stop` sans journal est déjà l'annulation. | Une revue d'API. |
| O4 | Le nom du skill dérivé automatiquement est-il acceptable, ou faut-il toujours le demander avant la conversion ? | Dérivé, avec `name_hint` pour le corriger. L'éditeur permet de le changer de toute façon (`SkillEditorScreen.kt:124-129`). | —

## 14. Points du plan qui m'ont semblé incohérents

1. **« Pas de pont Computer → bouton absent » (`ARCHITECTURE-TOP5.md:119`).** Le serveur ne peut pas
   constater l'absence d'un pont : il ne le voit pas (D2). La règle est donc entièrement côté client
   (`ComputerBridgeStore.kt:63`), et la route se contente d'exiger un `bridge.session` non vide. Le
   plan laisse croire que le serveur refuse ; il ne refuse qu'un bail anonyme.
2. **« Pont sans route de capture → bouton grisé avec la phrase du serveur »** (même ligne). Il n'y a
   pas de phrase du serveur quand la route est absente : il n'y a personne pour la dire. Et §0 règle
   2 (`:17-19`) demande qu'une entrée **s'efface** quand le serveur ne sait pas la servir. J'ai suivi
   §0 : absent, pas grisé. Grisé est réservé à un état **transitoire** du pont (phase ≠ `LIVE`), où
   une phrase à nous suffit.
3. **« B3 : mémoire seulement… puis `buf` compressé » (`:64`).** « Mémoire » de qui n'est pas dit. J'ai
   tranché : la mémoire est celle du téléphone, le serveur ne reçoit qu'un journal complet (D2).
4. **`skills.record.start/stop` « ou 100 % local si tu préfères » (`:117`).** Les deux ne sont pas
   équivalents : une version locale n'a pas de bail, donc pas de `deadline_at` partagé, donc pas de
   `410`. Ce document décrit la version serveur, celle des trois routes.
5. **Le lot 3 est annoncé à « 1,5 semaine, dont la moitié serveur » (`:111`) pour trois routes.** Ce
   document en ajoute une quatrième mécanique non prévue : la validation et le bornage du journal
   côté serveur (§2.4, §6). C'est une demi-journée de plus, pas une semaine — mais mieux vaut
   l'écrire que le découvrir.
