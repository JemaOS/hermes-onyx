# Patch lot 5 — `groups.lead` et `groups.handoff` (proposition)

**Statut : proposition non appliquée.** Ce document décrit un diff unifié
(`PATCH-GROUPS-LEAD-HANDOFF.patch`, 1055 lignes, 7 fichiers, +805/−11) destiné à
`~/.hermes/hermes-agent`. Il n'a **jamais** été appliqué à ce dépôt, le serveur n'a pas été
redémarré, et aucune suite de tests du dépôt n'a tourné (voir §7).

Ce qui a réellement été exécuté : `git apply --check` (lecture seule, n'écrit rien) sur le dépôt
réel — il passe sur les 7 fichiers — et un test de fumée écrit à la main dans une **copie jetable**
du dépôt (`cache/scratch/patch5/gen`), sur une base SQLite temporaire (§6.3). Ce test a trouvé un
vrai bug (`OperationalError: closed database` dans `room_state`), corrigé dans le patch.

Référence : `docs/SPEC-GROUPS-HANDOFF.md` (le contrat) et `docs/ARCHITECTURE-TOP5.md` §8.2/§8.3
(les deux tests obligatoires). Ancrage : `hermes-agent` à `5b147ab036`. Le dépôt avait quatre
fichiers modifiés non commités (`agent/error_classifier.py`, `gateway/platforms/api_server*.py`,
`tests/agent/test_error_classifier.py`) : **aucun n'est touché par ce patch**.

---

## 1. Ce que le patch ajoute

| Route | Ce qu'elle écrit | Réponse |
|---|---|---|
| `groups.lead` (`room_id`, `member_id` présent) | un `room.lead_changed` — rien d'autre | `room_id`, `lead`, `previous_member_id`, `seq` |
| `groups.handoff` (`room_id`, `from_member_id`, `to_member_id`, `task_id?`, `summary?`) | un `bot.handoff` — et l'arrêt du tour partant s'il est nommé | `room_id`, `seq`, `from_member_id`, `to_member_id`, `cancelled_task_id` |

Les deux méthodes sont annoncées dans `groups.capabilities.methods` (`_METHODS` dans
`tui_gateway/methods_groups.py`) et dans `LONG_HANDLERS` : le repli « route absente » de la spec
§7.1 se fait donc tout seul côté client, sans route de sonde.

**Le meneur est un pointeur dérivé du journal, pas une colonne.** `lead_member_id` est lu à chaque
fois dans le dernier événement qui nomme un meneur (`room.lead_changed` ou `bot.handoff`), via
l'index `idx_hosted_room_events_lead`. Trois raisons, toutes vérifiées dans le code :

1. **D3 tient.** Le passage de main écrit **un** événement, et le meneur change quand même : c'est
   `bot.handoff` qui déplace le pointeur, vers `to_member_id`. Sans cela il faudrait un second
   `room.lead_changed` pour le même geste, ce que D3 interdit.
2. **§6 tient.** « Le destinataire échoue après le passage → la main reste au destinataire » est
   vrai par construction : plus aucun événement n'est écrit, le pointeur ne bouge plus.
3. **La réplication suit.** `groups.replicate` transporte les événements ; un replica promu
   (`gateway/hosted_room_replicas.py::promote_replica`) recopie le journal, donc le meneur. Une
   colonne dans `hosted_rooms` aurait été perdue au passage.

Le prix : une requête indexée par lecture de `room_state`. Pas de migration de schéma, pas de
colonne, pas de table.

---

## 2. Fichier par fichier

### `gateway/hosted_rooms.py` (le magasin) — +191

- `MAX_HANDOFF_SUMMARY_CHARS = 280`, `LEAD_CHANGE_REASONS` (`named`, `cleared`, `member_left`).
- `_EVENT_KINDS_BY_ACTOR` : `room.lead_changed` autorisé pour les acteurs `user` **et** `system`,
  `bot.handoff` pour `member`. Sans cette table, `_validate_actor` refuse l'écriture.
- `RoomLeadError(HostedRoomError)` avec un attribut `reason` (`room_not_found`,
  `member_not_in_room`, `already_lead`, `room_stopping`, `profile_not_found`, `same_member`,
  `task_not_yours`, `summary_too_long`, `bad_request`). C'est le seul mécanisme de refus dont
  dispose l'enveloppe `_room_method` : elle rend `{code, message, data: {reason}}`.
- `_SELECT_LEAD` + `_lead_from_conn` + `lead_member_id()` : le meneur, lu dans le journal.
- `set_lead()` : une transaction, un événement, `already_lead` **sans** écriture, charge utile
  `{lead_member_id, previous_member_id, reason}` — aucune clé `member_id` (D4). Idempotent par
  `event_id` comme `rename_room`.
- `handoff()` : une transaction, un `bot.handoff`, acteur `{kind: member, id: from_member_id,
  profile}` et charge utile `{from_member_id, to_member_id, task_id, summary}` — **quatre clés,
  jamais `member_id`**. Refuse `same_member`, `summary_too_long`, un caractère de contrôle, un
  membre hors roster.
- `room_state()` rend désormais `lead_member_id` (c'est par là que `groups.state` et le Desktop
  voient le meneur). La valeur est lue **dans** la transaction : la première version la lisait après
  la fermeture de la connexion, ce que le test de fumée a attrapé.
- `_initialize_schema()` : index `idx_hosted_room_events_lead (room_id, kind, seq)`.

### `gateway/hosted_room_discussion.py` (le planificateur) — +94

- `DiscussionRoom.lead_member_id` (défaut `None`, donc aucun appelant positionnel ne casse).
  `validate_room` le lit et **le retombe à `None`** si le membre n'est plus au roster : un pointeur
  périmé ne doit pas faire échouer le rejeu d'une salle entière.
- `resolve_mentions(..., lead_member_id=None)` + `_lead_first()` : quand personne n'est mentionné,
  le meneur passe en tête ; tri stable, personne n'est filtré, `@nom` inchangé. C'est tout le
  « destinataire par défaut, pas un chef » de D2.
- `plan_next_task` : le tour 0 passe `room.lead_member_id`. Les tours suivants
  (`_unaddressed_member_mentions`) ne changent pas : une relance par mention reste explicite.
- Deux validateurs d'événement : `_validate_handoff_event`, `_validate_lead_changed_event`.
  **C'est là que le piège 1 est tenu côté lecture** : `_exact_fields` refuse toute clé inconnue,
  donc une charge utile `bot.handoff` qui porterait `member_id` est rejetée
  (`bot.handoff payload has unknown fields: member_id`) au lieu de voler l'auteur de la ligne.
  `room.lead_changed` de raison `member_left` ne résout **pas** `previous_member_id` au roster :
  ce membre est parti, c'est justement le cas.
  Les deux genres ne sont **pas** ajoutés à `_EPOCH_STAMPED_KINDS` : après une promotion de replica,
  les anciens événements portent l'epoch précédent, et une vérification d'epoch les ferait échouer.

### `tui_gateway/hosted_room_service.py` (l'orchestration) — +120

- `_HANDOFF_CANCEL_PREFIX = "handoff:"` et `_publication_result(task, status)` : une tâche annulée
  n'a pas de `result`, le driver ne garde la cause que dans `cancel_id`. Cette fonction la traduit
  en `{"reason": "handed_over"}` au moment où le terminal est publié. **C'est le piège 2 côté
  serveur** : le journal reçoit `turn.cancelled` avec exactement `handed_over`, jamais
  « member turn cancelled ».
- `set_lead()` : vérifie `profile_not_found` (profil non servi et membre non-pair),
  `member_not_in_room`, `room_stopping` (une tâche en `stopping`), puis délègue au magasin avec un
  `event_id` déterministe `lead:<membre|none>:<latest_seq>`.
- `handoff()` : vérifie les deux membres, `room_stopping`, résout la tâche vive du partant
  (`task_not_yours` sinon), appelle `runtime.cancel(identity, cancel_id="handoff:<task_id>")`,
  puis écrit `bot.handoff`. Le `cancel_id` est ce qui permet à `_publication_result` de retrouver la
  cause plus tard, sans colonne ni paramètre nouveau dans le driver.
- `_retire_lead_outside_roster()` appelé au début de `prepare_room` : si le meneur n'est plus dans
  `members_json`, **une** écriture `system` / `member_left` / `lead_member_id: null`, puis plus
  rien (le pointeur dérivé vaut `None`).

### `tui_gateway/methods_groups.py` (les routes) — +51

- `_METHODS` : `groups.lead`, `groups.handoff` (annoncées, donc `LONG_HANDLERS`).
- Deux handlers `@_room_method(..., code=5124/5125, room_code=4124/4125, service_code=4123)`.
  `groups.lead` refuse `member_id` **absent** (`bad_request`) et accepte `null` (retrait) : la
  présence se lit dans le dict brut, `params.get()` ne la distingue pas.
  `groups.handoff` refuse un champ obligatoire vide, un `task_id` non-chaîne, un `summary` non-chaîne.

### `tui_gateway/contracts/groups_bot_relay.py` (le contrat filaire) — +49

- `Room.lead_member_id` (sinon `Result` en `extra=forbid` rejette le `groups.state` enrichi).
- `GroupsLeadParams`/`Result`/`RoomLead`, `GroupsHandoffParams`/`Result` + deux `method(...)`.
  Le test `tests/gateway/test_canonical_wire_contract.py` exige un contrat par méthode servie :
  sans ces déclarations, il échoue.

### `apps/shared/src/gateway-contract.generated.ts` et `.openrpc.json` — +41 / +270

Régénérés avec `python3 scripts/gen_gateway_contracts.py` **dans une copie** du dépôt. La
régénération du dépôt non patché rend des fichiers identiques au bit près aux fichiers commités :
le diff ne contient donc que l'ajout des deux méthodes et du champ `Room.lead_member_id` (vérifié).
Sans ces deux fichiers, `tests/tui_gateway/contracts/test_generated.py` échoue.

---

## 3. Les refus, traduits

La spec écrit `404`/`409` ; son §1 dit elle-même que ce sont des codes applicatifs, pas des statuts
HTTP. Le patch suit la convention du canal : un code numérique d'enveloppe et un jeton stable dans
`data.reason`.

| Situation | `code` | `data.reason` |
|---|---|---|
| salle inconnue ou terminée | 4124 / 4125 | `room_not_found` |
| membre hors roster | 4124 / 4125 | `member_not_in_room` |
| profil absent du serveur | 4124 | `profile_not_found` |
| déjà meneur (ou retrait sans meneur) | 4124 | `already_lead` |
| bots encore en arrêt | 4124 / 4125 | `room_stopping` |
| `from == to` | 4125 | `same_member` |
| `task_id` pas une tâche vive du partant | 4125 | `task_not_yours` |
| `summary` > 280 | 4125 | `summary_too_long` |
| champ obligatoire vide / `member_id` absent | 4124 / 4125 | `bad_request` |
| worker de salle indisponible | 4123 | — |
| méthode inconnue (serveur ancien) | −32601 | — |

Aucun refus n'écrit d'événement : toutes les vérifications précèdent l'insertion, dans la même
transaction que l'écriture pour celles qui dépendent de l'état.

`room_not_found` arrive en **4124**, pas en 404 : le client ne peut donc pas le confondre avec une
route absente (`looksLikeMissingRoute` ne reconnaît que `−32601`/`404`).

---

## 4. Les deux pièges de §8.3

**Piège 1 — `payload.member_id` vole l'auteur.** Vérifié dans
`core/src/main/java/com/onyx/app/data/model/serve/GroupThread.kt` : `actorID` lit
`payload.firstString("member_id")` **avant** `actor.id`. Le patch le tient des deux côtés :

- écriture : `hosted_rooms.handoff()` construit la charge utile en littéral, quatre clés ;
- lecture : `_validate_handoff_event` refuse la clé (`unknown fields: member_id`) — testé.

**Piège 2 — `handed_over` doit rendre `null`.** Le serveur ne peut pas changer `note()` : elle est
en Kotlin, dans le dépôt Onyx. Ce que le patch garantit, c'est que le journal reçoit
`turn.cancelled` avec `reason: "handed_over"` (et non « member turn cancelled »). Le changement
client reste **à faire**, et il est petit :

```kotlin
// core/src/main/java/com/onyx/app/data/model/serve/GroupThread.kt, note()
"turn.cancelled" ->
    if (payload.strOrNull("reason") in setOf("superseded_by_newer_user_event", "handed_over")) null
    else OnyxLocalization.format("%1\$s was stopped", who)
```

Tant qu'il n'est pas fait, le journal affichera « Writer was stopped » **et** le passage de main
pour un seul geste — c'est-à-dire exactement le défaut que la spec décrit. Ce patch ne le corrige
pas et ne prétend pas le corriger.

---

## 5. Écarts assumés par rapport à la spec

| # | Écart | Pourquoi, code à l'appui |
|---|---|---|
| D-1 | **Ordre des deux événements.** La spec veut `turn.cancelled` puis `bot.handoff`. Le patch annule d'abord le tour, mais écrit `bot.handoff` tout de suite : le `turn.cancelled` est publié plus tard par `_publish_terminal_tasks` (poll du driver). | Écrire `turn.cancelled` depuis la RPC donnerait **deux** terminaux pour une tâche ; `_derive_member_watermarks` lève alors `task '<id>' has more than one terminal room event` et la salle devient illisible. L'ordre visible est identique puisque `handed_over` ne rend aucune ligne. |
| D-2 | **« Le tour du destinataire commence » n'est pas implémenté.** | `plan_next_task` ne crée une tâche que depuis un `message.user` en attente. Un `bot.handoff` seul ne crée pas de discussion : le meneur change, et le prochain message sans `@nom` part chez lui. Inventer un tour proactif demanderait un événement synthétique, hors du contrat additif. |
| D-3 | **`member_left` n'est écrit que si la ligne de roster disparaît**, pas si le profil disparaît du serveur. | `validate_roster` (`hosted_room_discussion.py:226`) lève déjà « profile is not local to this gateway » avant tout plan : une salle dont le profil du meneur a disparu n'atteint jamais `prepare_room`. Traiter ce cas demande de rouvrir cette règle fail-closed — voir §8. |
| D-4 | La **seconde surface** `gateway/session_group_controls.py` (`GROUP_METHODS`, capacités `session:control`) n'est pas étendue. | C'est le chemin « runtime store » par profil, distinct du canal des salles décrit par la spec §1 (`methods_groups.py`). L'étendre demande d'y mirrorer les deux handlers **et** leurs `_FIELDS` ; laissé hors périmètre, et listé en §8. |
| D-5 | `groups.replica_state` n'expose pas le meneur. | Surface de couverture d'un replica, en lecture seule. Le meneur reste dérivable du journal repliqué dès la promotion. |
| D-6 | `member_id` « présent mais `null` » ne s'exprime pas dans le modèle pydantic. | `validate_params` ne refuse que les clés inconnues ; le handler lit `params` brut. Le TS généré rend `member_id?: string \| null` : le client doit **toujours** envoyer la clé. |

Note d'usage : un `groups.lead` rejoué après une réponse perdue rend `409 already_lead`, pas un
succès. C'est cohérent (`already_lead` = « il l'est déjà ») mais le client doit relire (`look()`)
plutôt qu'afficher une erreur.

---

## 6. Comment tester à la main

### 6.1 Le patch s'applique (sans l'appliquer)

```bash
cd ~/.hermes/hermes-agent
git apply --check docs/PATCH-GROUPS-LEAD-HANDOFF.patch   # déjà fait : passe sur 7 fichiers
git apply --stat  docs/PATCH-GROUPS-LEAD-HANDOFF.patch
```

### 6.2 Les routes, au canal

Après application **et** redémarrage du gateway (non fait ici) :

```jsonc
{"jsonrpc":"2.0","id":1,"method":"groups.capabilities","params":{}}
// → "methods" contient "groups.lead" et "groups.handoff"

{"jsonrpc":"2.0","id":2,"method":"groups.lead",
 "params":{"room_id":"onyx-9f2c1a4e","member_id":"coder"}}
// → {"room_id":"onyx-9f2c1a4e",
//    "lead":{"member_id":"coder","profile":"coder","label":"Coder"},
//    "previous_member_id":null,"seq":1237}

{"jsonrpc":"2.0","id":3,"method":"groups.lead",
 "params":{"room_id":"onyx-9f2c1a4e","member_id":"coder"}}
// → erreur 4124, data.reason = "already_lead", aucun événement écrit

{"jsonrpc":"2.0","id":4,"method":"groups.lead","params":{"room_id":"onyx-9f2c1a4e"}}
// → erreur 4124, data.reason = "bad_request"   (member_id absent ≠ null)

{"jsonrpc":"2.0","id":5,"method":"groups.handoff",
 "params":{"room_id":"onyx-9f2c1a4e","from_member_id":"writer","to_member_id":"coder",
           "task_id":"dtask:…","summary":"Plan écrit ; reste le code."}}
// → {"room_id":"onyx-9f2c1a4e","seq":1240,"from_member_id":"writer",
//    "to_member_id":"coder","cancelled_task_id":"dtask:…"}
```

Contrôle du journal, sans passer par l'écran :

```bash
sqlite3 ~/.hermes/state.db \
  "select seq, kind, json_extract(payload_json,'$.member_id') as stolen, payload_json
     from hosted_room_events where room_id='onyx-9f2c1a4e' order by seq desc limit 3"
```

Attendu, dans cet ordre décroissant : `turn.cancelled` avec
`$.reason = "handed_over"` et `stolen = NULL`, puis `bot.handoff` avec `stolen = NULL` (la clé
n'existe pas), acteur `member/writer`.

### 6.3 Ce qui a été exécuté (copie jetable, base temporaire)

Script `cache/scratch/patch5/smoke.py` sur la copie patchée : création d'une salle à deux membres,
`set_lead`, `handoff`, puis relecture. Résultat réel :

```
fresh lead: None
set_lead: {"lead": {"member_id": "coder", ...}, "previous_member_id": null, "seq": 1, "reason": "named"}
already_lead -> already_lead
member_not_in_room -> member_not_in_room
handoff kind/actor: bot.handoff {'kind': 'member', 'id': 'writer', 'profile': 'writer'}
handoff payload keys: ['from_member_id', 'summary', 'task_id', 'to_member_id']
same_member -> same_member
summary_too_long -> summary_too_long
DiscussionRoom lead: coder
handoff validates ok: True
member_id payload rejected: bot.handoff payload has unknown fields: member_id
default recipient order: ['coder', 'writer']
mention order: ['writer']
_publication_result({'cancel_id': 'handoff:task_7f3a'}, 'cancelled') -> {'reason': 'handed_over'}
```

Ce que cela prouve : la forme des deux événements, la dérivation du meneur, les refus, le piège 1
côté lecture, l'ordre des destinataires par défaut, et la traduction `handoff:` → `handed_over`.
Ce que cela **ne** prouve pas : le chemin RPC complet, `prepare_room`, le driver, la publication
d'un vrai `turn.cancelled`, la réplication, ni la compatibilité avec un journal existant.

### 6.4 Les tests à écrire (spec §10)

1. **Journal, pas écran** : 40 événements + `bot.handoff` → `rows` rend 41 lignes, même ordre.
2. **Pas de fuite dans le Flux** : `roomThreadMessages` ignore la ligne de passage.
3. **La discussion continue** : `discussing` reste vrai après un `bot.handoff` seul
   (`endsDiscussion` ne le connaît pas — vérifié, `GroupThread.kt:46`).
4. **Identité** : `bot.handoff` sans `member_id` → `actorID == "writer"` ; avec la clé → le
   serveur refuse (déjà couvert par `_validate_handoff_event`).
5. **Pas de doublon** : `turn.cancelled` de raison `handed_over` → `note()` rend `null`.
6. **Absence** : sans les deux noms dans `groups.capabilities.methods`, aucune entrée n'est dessinée.
7. **Localisation** : quatre libellés de menu + cinq phrases de §4.3 au catalogue.
8. **Compatibilité** : `GroupSupport(supported, writable)` compile encore.

---

## 7. Ce qui reste à faire (par ordre)

1. **Côté Onyx** : la branche `handed_over` de `note()` (§4), les deux nouvelles branches
   `bot.handoff` / `room.lead_changed`, les libellés et leurs clés EN, les deux entrées de menu
   conditionnelles. Rien de tout cela n'est dans ce patch.
2. **Suite de tests du serveur** : aucune n'a tourné. `pytest` n'est installé dans aucun des
   interpréteurs disponibles sur cette machine (`python3 -m pytest` → *No module named pytest*).
   À lancer au minimum : `tests/tui_gateway/test_groups_methods.py`,
   `tests/gateway/test_hosted_room_discussion.py`, `tests/gateway/test_canonical_wire_contract.py`,
   `tests/tui_gateway/contracts/test_generated.py`, `tests/tui_gateway/test_hosted_room_service.py`.
3. **`gateway/session_group_controls.py`** : mirrorer les deux méthodes si le Desktop doit les
   appeler par le chemin runtime-store (D-4).
4. **Driver** : un vrai `cancel_id` typé, ou un paramètre `reason` sur `runtime.cancel`, rendrait
   `_publication_result` inutile ; la traduction par préfixe est un pis-aller assumé.
5. **Ordre strict** `turn.cancelled` → `bot.handoff` : demande que la publication terminale soit
   synchrone dans la RPC, donc un remaniement du pipeline (`_publish_terminal_tasks`), pas un
   ajout.

## 8. Ce qui bloque encore, précisément

- **Meneur dont le profil disparaît (§6, ligne 4).** `validate_roster` refuse une salle dont un
  profil local n'est plus servi (`hosted_room_discussion.py:226`,
  « profile '{p}' is not local to this gateway »). Le plan de la salle échoue avant d'atteindre
  `prepare_room`, donc l'événement `member_left` ne peut pas être écrit par ce chemin. Le patch
  couvre le cas « la ligne de roster a disparu » et **pas** celui-là. Le trancher demande de
  décider si une salle doit continuer à vivre avec un membre dont le profil a disparu — une
  décision de produit, pas de code.
- **`groups.lead` exige le service vivant** (`service_code=4123`) : sans worker, la route rend
  « Group Chat worker is unavailable ». La spec ne dit pas ce qu'elle attend dans ce cas ; le
  patch choisit de refuser plutôt que d'écrire un meneur dans une salle que personne ne conduit.

## 9. Confiance

**Moyenne.** Ce qui est solide : le diff s'applique (`git apply --check` sur le dépôt réel), il est
ancré sur des symboles lus et non supposés, les artefacts générés sont régénérés et vérifiés
identiques au bit près sur la base non patchée, et le test de fumée montre les formes d'événements,
les refus, le piège 1 et l'ordre des destinataires. Ce qui manque : aucune suite de tests, aucun
passage par la RPC ni par le driver, et les écarts D-1/D-2/D-3 sont des choix documentés, pas des
preuves. Le patch ne doit pas être annoncé comme vérifié.