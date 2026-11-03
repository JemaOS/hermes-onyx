# Spécification serveur — bot principal et passage de main

**Lot 5 de `ARCHITECTURE-TOP5.md`** (§2 « Lot 5 — Bot principal et passage de main », §3 « Ce qui
reste côté Hermes »). Ce document fixe le contrat de deux routes et la **forme exacte** de
l'événement de passage, pour que l'interface n'ait rien à deviner. Aucune ligne d'implémentation n'y
figure.

Les renvois `ARCHITECTURE-TOP5.md:<ligne>` désignent ce plan tel qu'il est sur `main` (`e8c195a`) :
il n'est pas dans la branche de ce document, et ses numéros de ligne sont ceux de cette version.

Ce que le plan décide et que ce document ne rediscute pas : les routes sont **dans le core Hermes**,
pas dans un plugin, parce qu'un meneur est une notion de room (`ARCHITECTURE-TOP5.md:159`) ; l'ajout
est **additif**, aucune destination de navigation ne bouge (`:26-28`) ; le journal reste rendu par la
même liste (`:142`).

---

## 0. Les quatre décisions que ce document tranche

| # | Décision | Pourquoi |
|---|---|---|
| D1 | Les deux routes sont des méthodes **RPC** (`groups.*`), pas des routes HTTP. | Les rooms passent déjà par le canal RPC du Dashboard (`ServeGroupsRpc.kt:31`, `RpcChannel.kt:13`). Une route HTTP pour la même notion ferait deux transports pour une seule salle. |
| D2 | Le meneur est un **destinataire par défaut**, pas un chef. | `@nom` adresse déjà un membre directement (`GroupRoomScreen.kt:956-957`). Un meneur qui filtrerait les autres le casserait. |
| D3 | Le passage de main écrit **un** événement, `bot.handoff`, et rien d'autre. | Un journal qui écrit deux lignes pour un seul geste se lit deux fois. Le tour annulé, lui, a déjà sa ligne (`turn.cancelled`). |
| D4 | Les deux événements ne portent **aucune clé `member_id`** dans leur charge utile. | `GroupEvent.from` lit `payload.member_id` **avant** l'acteur (`GroupThread.kt:83`). Une telle clé volerait silencieusement l'identité de l'auteur de la ligne. |

---

## 1. Où vivent les routes

Deux méthodes du core Hermes, sur le canal des salles, à côté de `groups.send` et `groups.stop`
(`ServeGroupsRpc.kt:113-166`). Elles suivent la même forme d'appel : `rpc.call(method, params,
timeoutMillis)` (`RpcChannel.kt:13`), avec le délai des appels de salle (`CALL_TIMEOUT`, 60 s,
`ServeGroupsRpc.kt:286`).

| Nom | Genre | Paramètres |
|---|---|---|
| `groups.lead` | RPC | `room_id`, `member_id` (ou `null`) |
| `groups.handoff` | RPC | `room_id`, `from_member_id`, `to_member_id`, `task_id` (facultatif), `summary` (facultatif) |

**Découverte.** Les deux méthodes s'annoncent dans `groups.capabilities.methods`, que le client lit
déjà (`ServeGroupsRpc.kt:297-303`). `GroupSupport` gagne deux champs booléens avec valeur par défaut
`false` : l'ajout ne casse aucun appelant, y compris le test qui construit l'objet par position
(`ServeGroupsRpcTest.kt:52`). C'est le mécanisme d'absence prévu par le plan
(`ARCHITECTURE-TOP5.md:143`), et il ne demande aucune route de sonde.

**Forme d'un refus.** Une erreur applicative du serveur arrive en `TransportError.Rpc(code, detail)`
(`TransportError.kt:49`). Les codes ci-dessous sont donc des **codes RPC négatifs ou applicatifs**,
pas des statuts HTTP.

---

## 2. `groups.lead`

Désigne le meneur d'une salle, ou le retire.

**Requête**

```json
{"room_id": "onyx-9f2c1a4e", "member_id": "coder"}
```

| Champ | Règle |
|---|---|
| `room_id` | Obligatoire. Salle inconnue : `404 room_not_found`. |
| `member_id` | Obligatoire **présent**. Une chaîne désigne le meneur ; `null` le retire. Absent ≠ `null` : absent rend `400 bad_request`, pour qu'aucun client ne retire un meneur par omission. |

**Réponse**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "lead": {"member_id": "coder", "profile": "coder", "label": "Coder"},
  "previous_member_id": "researcher",
  "seq": 1237
}
```

| Champ | Sens |
|---|---|
| `lead` | Le meneur après l'appel, ou `null` s'il n'y en a plus. `label` est le nom de salle du membre, celui que le roster donne déjà (`GroupRoom.kt:34-35`). |
| `previous_member_id` | Le meneur d'avant, ou `null`. Permet à l'écran d'annuler sans relire. |
| `seq` | Le numéro du `room.lead_changed` écrit. L'écran ne dessine **pas** de ligne optimiste : il relit (`look()`), et cette valeur lui dit ce qu'il va trouver. |

**Ce que l'appel écrit.** Un événement `room.lead_changed`, à la fin du journal, avec son propre
`seq`. Rien d'autre ne bouge : ni le nom de la salle, ni ses membres, ni son `latest_seq` d'avant.

**Refus**

| Code | `code` | Quand |
|---|---|---|
| 404 | `room_not_found` | Salle inconnue, ou terminée. |
| 404 | `profile_not_found` | Le profil du membre n'existe plus sur ce serveur. |
| 409 | `member_not_in_room` | `member_id` n'est pas un membre de cette salle. |
| 409 | `already_lead` | Ce membre est déjà le meneur. Aucun événement n'est écrit. |
| 409 | `room_stopping` | Des bots s'arrêtent encore. À réessayer : c'est le motif déjà employé pour `disband` (`ServeGroupsRpc.kt:185-196`). |
| 400 | `bad_request` | `room_id` vide, ou `member_id` absent. |
| −32601 | — | Le serveur ne connaît pas la méthode. Voir §7. |

---

## 3. `groups.handoff`

Passe la main d'un membre à un autre, avec le contexte utile.

**Requête**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "from_member_id": "writer",
  "to_member_id": "coder",
  "task_id": "task_7f3a",
  "summary": "Plan écrit ; reste le code."
}
```

| Champ | Règle |
|---|---|
| `room_id` | Obligatoire. |
| `from_member_id` | Obligatoire. C'est le membre qui lâche la main — en pratique le meneur, ou un bot qui termine son tour. Il doit être dans la salle. |
| `to_member_id` | Obligatoire. Différent de `from_member_id` : même membre → `409 same_member`. |
| `task_id` | Facultatif. Présent : c'est le tour en cours de `from_member_id`, et il est **annulé** avant que l'autre commence. Absent : rien n'est annulé, la main passe pour le tour suivant. |
| `summary` | Facultatif. Texte brut, ≤ 280 caractères, sans caractère de contrôle. Une chaîne vide vaut absente. |

`summary` est **le** contexte utile : ce qui est fait, ce qui reste. Il n'est pas obligatoire parce
que le journal de la salle **est** déjà le contexte — un destinataire peut le relire. Un résumé
obligatoire pousserait à écrire « suite » pour passer.

**Réponse**

```json
{
  "room_id": "onyx-9f2c1a4e",
  "seq": 1240,
  "from_member_id": "writer",
  "to_member_id": "coder",
  "cancelled_task_id": "task_7f3a"
}
```

`cancelled_task_id` vaut `null` quand la requête n'a pas donné de `task_id`. L'écran relit le journal
juste après (`look()`), comme il le fait déjà après `stop` et `retry` (`GroupRoomFeed.kt:524-531`) :
la ligne apparaît au tick suivant, qui est de 500 ms quand la salle travaille
(`GroupRoomFeed.kt:581-582`).

**Ce que l'appel écrit, dans cet ordre**

1. `turn.cancelled` avec `reason: "handed_over"` — **seulement** si `task_id` était présent. Le tour
   du partant s'arrête là.
2. `bot.handoff` — un seul événement, forme exacte en §4.
3. Le tour du destinataire commence, s'il y a quelque chose à faire.

**Refus**

| Code | `code` | Quand |
|---|---|---|
| 404 | `room_not_found` | Salle inconnue. |
| 409 | `member_not_in_room` | L'un des deux membres n'est pas dans la salle. |
| 409 | `same_member` | `from_member_id` = `to_member_id`. |
| 409 | `task_not_yours` | `task_id` n'est pas une tâche en cours de `from_member_id`. |
| 409 | `room_stopping` | Des bots s'arrêtent encore. |
| 422 | `summary_too_long` | `summary` > 280 caractères. |
| 400 | `bad_request` | Champ obligatoire vide. |
| −32601 | — | Le serveur ne connaît pas la méthode. Voir §7. |

Aucun refus n'écrit d'événement. Un passage de main refusé ne laisse pas de trace à moitié faite :
c'est la règle qui rend l'état lisible après une erreur.

---

## 4. La forme exacte de l'événement

C'est la partie du contrat que l'interface lit. Elle ne doit pas changer sans changer ce document.

### 4.1 `bot.handoff`

```json
{
  "seq": 1240,
  "kind": "bot.handoff",
  "actor": {"kind": "member", "id": "writer"},
  "payload": {
    "from_member_id": "writer",
    "to_member_id": "coder",
    "task_id": "task_7f3a",
    "summary": "Plan écrit ; reste le code."
  },
  "created_at": 1730650042.5
}
```

| Champ | Règle | Ce que l'interface en fait |
|---|---|---|
| `seq` | Entier, à la fin du journal. | L'ordre de la ligne. Rien n'est réordonné : `rows` est un filtre, pas un tri (`GroupRoomFeed.kt:98`). |
| `kind` | Exactement `bot.handoff`. | Le seul discriminant. |
| `actor.kind` | `member`. | — |
| `actor.id` | Le partant, **égal** à `from_member_id`. | Devient `actorID` : `GroupEvent.from` lit `actor.id` quand `actor.kind` vaut `member` et que la charge utile n'a pas de `member_id` (`GroupThread.kt:83-84`). |
| `payload.from_member_id` | Chaîne, membre de la salle. | Le nom affiché à gauche. |
| `payload.to_member_id` | Chaîne, membre de la salle. | Le nom affiché à droite. |
| `payload.task_id` | Chaîne ou `null`. | Non affiché. |
| `payload.summary` | Chaîne ≤ 280 ou `null`. | Affiché **tel quel**, jamais reformulé. |
| `created_at` | Secondes Unix décimales. | `GroupEvent.from` convertit (`GroupThread.kt:86`). |

**Deux pièges, à tenir par un test chacun.**

1. **Pas de clé `member_id` dans `payload`.** `GroupEvent.from` lit `payload.firstString("member_id")`
   avant l'acteur (`GroupThread.kt:83`). Une telle clé ferait de `actorID` le destinataire au lieu du
   partant, et la phrase nommerait deux fois la même personne. Le serveur n'en écrit pas ; le test
   doit refuser un journal qui en porte une.
2. **`bot.handoff` ne termine pas la discussion.** `endsDiscussion` ne reconnaît que
   `room.activity` et `room.stop_requested` (`GroupThread.kt:46`). Un `bot.handoff` glissé dans cette
   liste ferait croire à `discussing` que le tour est fini (`GroupRoomFeed.kt:73-78`), et la salle
   cesserait d'attendre la réponse qu'elle vient de demander.

### 4.2 `room.lead_changed`

```json
{
  "seq": 1237,
  "kind": "room.lead_changed",
  "actor": {"kind": "user"},
  "payload": {
    "lead_member_id": "coder",
    "previous_member_id": "researcher",
    "reason": "named"
  },
  "created_at": 1730650001.0
}
```

| Champ | Règle |
|---|---|
| `payload.lead_member_id` | Le nouveau meneur, ou `null` s'il n'y en a plus. |
| `payload.previous_member_id` | L'ancien, ou `null`. |
| `payload.reason` | `named` (désigné), `cleared` (retiré), `member_left` (son profil a disparu). Liste fermée. |
| `actor` | `user` pour un geste humain, `system` pour `member_left`. |

Là encore : **aucune clé `member_id`** (D4).

### 4.3 Les lignes que l'interface doit savoir composer

Le journal ne rend une ligne que si `GroupEvent.note(room)` rend un texte (`GroupRoomFeed.kt:98`) ;
`RoomEventRow` l'affiche alors centré, en note de bas de fil, sans nouvelle composition
(`GroupRoomScreen.kt:1009-1016`). Les deux nouveaux cas s'ajoutent donc à `note()`
(`GroupThread.kt:49-69`), sur le modèle de `turn.settled` (`:52`) et `room.renamed` (`:66`) : l'id
vient de la charge utile, le nom vient du roster.

| Cas | Texte exact (FR) | Clé EN | Repli quand le membre est inconnu |
|---|---|---|---|
| `bot.handoff` | « Writer passe la main à Coder. » | `%1$s passed the lead to %2$s.` | Le nom inconnu devient « un bot » (`OnyxLocalization["A bot"]`, déjà employé en `GroupThread.kt:50`). |
| `bot.handoff` avec `summary` | La même phrase, puis un saut de ligne, puis le résumé **mot pour mot**. | idem | idem |
| `room.lead_changed`, `reason` = `named` | « Coder mène la salle. » | `%1$s leads the room.` | « Un bot mène la salle. » |
| `room.lead_changed`, `reason` = `cleared` | « Plus de meneur dans la salle. » | `This room has no lead.` | — |
| `room.lead_changed`, `reason` = `member_left` | « Coder a quitté la salle : plus de meneur. » | `%1$s left the room: no lead.` | idem |

**Un troisième cas, à ne pas oublier.** `turn.cancelled` avec `reason: "handed_over"` ne doit
**rien** afficher. La branche existante rend « %1$s was stopped » pour toute raison inconnue
(`GroupThread.kt:56-58`) ; sans une exception explicite pour `handed_over`, le journal montrerait
« Writer was stopped » **et** « Writer passe la main à Coder » pour un seul geste. La branche
existante écarte déjà une raison (`superseded_by_newer_user_event`) : `handed_over` s'ajoute à la
même condition.

---

## 5. Ce que le meneur change, et ce qu'il ne change pas

### 5.1 Ce qu'il change

| | Sans meneur (aujourd'hui) | Avec un meneur |
|---|---|---|
| Qui reçoit une demande sans `@nom` | Le driver du serveur choisit. Onyx ne le voit que par `driver_status` (`GroupThread.kt:114-127`) et par l'indice de l'écran vide (`GroupRoomScreen.kt:956-957`). | Le meneur, en premier. |
| Qui distribue | Personne d'identifiable : la répartition est interne au driver. | Le meneur, et sa décision **s'écrit** : chaque répartition est un `bot.handoff` dans le journal. |
| Ce que l'écran peut montrer | Rien de la répartition. | La ligne du passage, et le meneur courant. |
| Ordre des tours | Implicite. | Explicite dans le journal, donc relisible et testable. |

Le gain n'est pas « un chef » : c'est **un ordre écrit**. Aujourd'hui, une salle qui répond dans un
ordre inattendu ne laisse aucune trace de la décision.

### 5.2 Ce qu'il ne change pas

| Chose | Pourquoi elle ne bouge pas |
|---|---|
| `@nom` adresse toujours un membre directement | L'indice de la salle le promet déjà (`GroupRoomScreen.kt:956-957`). Le meneur est un défaut, pas un péage (D2). |
| `groups.stop` arrête tout le monde, meneur compris | Il ne prend qu'un `room_id` (`ServeGroupsRpc.kt:157-166`). Rien à changer. |
| `groups.approve` garde son identité par membre | La requête porte `member_id` (`ServeGroupsRpc.kt:211-224`). Le meneur ne peut pas approuver à la place d'un autre : ce serait approuver une commande qu'il n'a pas demandée. |
| Le roster de 2 à 6 membres | Fixé à la création (`GroupRoom.kt:116-118`). `groups.lead` n'ajoute et ne retire personne. |
| L'ordre et la numérotation du journal | Un événement de plus, à la fin. Aucune ligne existante n'est déplacée. |
| La scène vocale | Elle ne lit que les messages et les brouillons (`GroupRoomFeed.kt:110-161`). |
| L'onglet Flux et l'onglet Fichiers | `roomThreadMessages` ne garde que `isMessage` (`GroupRoomJournal.kt:30`). Un passage de main n'est pas un message : il ne s'y invite pas. C'est **voulu** — le Flux est une conversation, pas un journal d'exploitation. |
| Le nom, l'identifiant, la date de la salle | `groups.lead` n'y touche pas. |

---

## 6. Si le meneur meurt en cours de route

Cinq cas. Chacun a une issue précise, et aucune n'est « on verra ».

| Cas | Ce que le serveur fait | Ce que l'écran montre |
|---|---|---|
| Le tour du destinataire échoue après le passage | `turn.failed` pour le destinataire (`GroupThread.kt:53-55`). **La main reste au destinataire.** Aucun retour automatique. | « Coder n'a pas pu répondre : … », et la salle garde Coder comme meneur. |
| Le partant meurt **avant** que `groups.handoff` soit écrit | Rien n'est écrit. La main ne bouge pas. Le tour est perdu comme aujourd'hui (`turn.failed`). | Aucune ligne de passage. C'est correct : il n'y a pas eu de passage. |
| Le meneur meurt pendant son tour, sans `groups.handoff` | `turn.failed` ou `turn.cancelled`, selon la cause. Le meneur **reste** désigné. | La salle reste avec ce meneur ; la personne peut en désigner un autre. |
| Le profil du meneur disparaît du serveur | Le serveur retire le meneur et écrit `room.lead_changed` avec `lead_member_id: null`, `reason: "member_left"`. Une **seule** écriture, pas deux. | « Coder a quitté la salle : plus de meneur. » La salle se comporte exactement comme avant le lot. |
| Le serveur redémarre en plein tour | Le journal est sur disque : le passage, s'il était écrit, reste. `groups.state` rend `blocked` si Hermes a perdu le tour de vue (`GroupThread.kt:96-100`). | Le fil garde la ligne de passage ; rien n'est inventé au redémarrage. |

Pourquoi aucun retour automatique : un renvoi à chaque échec produirait un ping-pong entre deux bots
qui échouent. Le retour est **un geste**, pas une conséquence — `groups.handoff` dans l'autre sens.

---

## 7. Les replis

### 7.1 Serveur sans la route

Deux chemins, un seul verdict.

1. **Annoncé** : `groups.capabilities.methods` ne liste pas `groups.lead` / `groups.handoff`. Le client
   le lit déjà pour `supported` (`ServeGroupsRpc.kt:297-303`) ; les deux champs ajoutés à
   `GroupSupport` restent `false`, et l'entrée de menu n'est **pas dessinée**.
2. **Découvert à l'usage** : un `-32601` (méthode inconnue). `looksLikeMissingRoute` le reconnaît déjà
   (`PageStates.kt:113-123`) ; l'entrée disparaît au retour suivant, et la phrase de §7.4 s'affiche
   une fois.

Le motif d'entrée conditionnelle existe déjà dans la salle : « Bot Models » n'est dessiné que si la
lambda est fournie et que la salle a des membres (`GroupRoomScreen.kt:723-737`). Une entrée de plus,
avec la même garde, ne demande aucun mécanisme neuf. Elle demande en revanche son libellé au
catalogue : `MenuAndSheetLocalizationTest` refuse un `MenuItem(…)` écrit en clair sans traduction
française (`MenuAndSheetLocalizationTest.kt:56-73`).

| Route absente | Phrase exacte affichée (FR) | Clé EN |
|---|---|---|
| `groups.lead` | « Ce serveur ne sait pas désigner un meneur (route absente). » | `This server can't name a lead (route missing).` |
| `groups.handoff` | « Ce serveur ne sait pas passer la main (route absente). » | `This server can't hand over (route missing).` |

`humanFailure` ne convient pas ici : sur un `-32601` il dit « Le serveur a refusé : … »
(`TransportError.kt:160`), ce qui est vrai mais ne dit pas que la fonction n'existe pas.

### 7.2 Salle sans meneur

C'est l'état d'aujourd'hui, à l'identique : aucune ligne, aucune entrée « Meneur » cochée, et
`groups.send` sans `@nom` se comporte comme avant. `groups.handoff` reste appelable — n'importe quel
membre peut passer la main — mais l'entrée « Passer la main à… » du menu humain n'est **pas** dessinée :
sans meneur, il n'y a pas de main à passer, et cette entrée ne doit pas promettre un geste qui n'a pas
d'objet.

`groups.lead` avec `member_id: null` sur une salle qui n'a pas de meneur → `409 already_lead`, aucun
événement. Un retrait qui ne retire rien n'est pas une information.

### 7.3 Meneur retiré de la salle

Onyx **n'a aucune route pour retirer un membre** d'une salle : `GroupRoomApi` n'en porte pas
(`ServeGroupsRpc.kt:326-339`), et le roster est fixé à la création. Ce cas arrive donc par le serveur,
quand un profil disparaît — et c'est au serveur de le traiter (§6, quatrième ligne). Côté téléphone :

- Le roster se rafraîchit par `groups.state` : `look()` remplace les membres quand ils ont changé
  (`GroupRoomFeed.kt:264-266`).
- Tant que le roster local est en retard, la salle peut afficher un meneur qui n'existe plus. Le
  prochain `groups.lead` ou `groups.handoff` qui le nomme rend `409 member_not_in_room`, et l'écran
  relit le journal au lieu d'inventer une correction.
- La ligne « Coder a quitté la salle : plus de meneur » vient du serveur, jamais d'une déduction de
  l'app : c'est la seule façon de distinguer un retrait d'une simple panne de lecture.

### 7.4 Ce qui reste vrai dans tous les cas

- Aucun repli ne casse une salle existante : sans les deux routes, la salle est celle d'aujourd'hui,
  au pixel près (`ARCHITECTURE-TOP5.md:143`).
- Un `409` n'est jamais une panne : l'écran le dit avec ses mots (§5 des refus), pas avec
  « Réessayez ».
- Une entrée absente ne laisse pas de tap mort : c'est la règle 2 du contrat de non-régression
  (`ARCHITECTURE-TOP5.md:17-19`).

---

## 8. L'accroche dans l'écran

Aucune destination nouvelle : `Route` (`OnyxApp.kt:234`) et `RouteRetours.kt` ne bougent pas.

| Geste | Où | Route |
|---|---|---|
| « Meneur de la salle » → sous-menu des membres | menu « … » de la salle, une entrée conditionnelle de plus (`GroupRoomScreen.kt:713-752`) | `groups.lead` |
| « Passer la main à… » → sous-menu des membres, dessiné seulement s'il y a un meneur | même menu | `groups.handoff`, avec `from_member_id` = le meneur et `task_id: null` |
| La ligne du passage | le fil, par `note()` (`GroupThread.kt:49-69`) | lecture seule |

Le sous-menu par membre existe déjà pour « Bot Models » (`GroupRoomScreen.kt:723-737`) : on réutilise
sa forme, pas son code. Les deux nouvelles entrées portent `localize = true` (défaut) et leurs
libellés vont au catalogue.

Ce qui n'est **pas** dans ce lot : un écran de meneur, un badge permanent dans l'en-tête, une
commande `/lead`. Le plan ne les demande pas et l'en-tête est déjà chargé (trois boutons,
`GroupRoomScreen.kt:892-911`).

---

## 9. Accroches dans le code existant

| Affirmation de ce document | Fichier réel |
|---|---|
| Les rooms passent par un canal RPC, pas par HTTP | `core/.../data/net/ServeGroupsRpc.kt:31` ; `core/.../data/net/RpcChannel.kt:13` |
| Le délai d'un appel de salle est de 60 s | `core/.../data/net/ServeGroupsRpc.kt:286` |
| Les méthodes connues du serveur sont annoncées dans `groups.capabilities` | `core/.../data/net/ServeGroupsRpc.kt:297-303` |
| `GroupSupport` n'a que deux champs aujourd'hui, et un test le construit par position | `core/.../data/net/ServeGroupsRpc.kt:342` ; `core/.../test/.../ServeGroupsRpcTest.kt:52` |
| `groups.send` écrit un `message.user` et laisse le driver choisir qui répond | `core/.../data/net/ServeGroupsRpc.kt:113-129` |
| `groups.stop` n'arrête qu'une salle, sans distinguer de meneur | `core/.../data/net/ServeGroupsRpc.kt:157-166` |
| `groups.approve` porte le `member_id` de celui qui a demandé | `core/.../data/net/ServeGroupsRpc.kt:211-224` |
| Un `disband` pendant l'arrêt se retente | `core/.../data/net/ServeGroupsRpc.kt:185-196` |
| `GroupEvent` : `seq`, `kind`, `actor`, `payload`, `created_at` | `core/.../data/model/serve/GroupThread.kt:27-39` |
| `actorID` lit `payload.member_id` **avant** `actor.id` | `core/.../data/model/serve/GroupThread.kt:83-84` |
| `endsDiscussion` ne connaît que deux genres | `core/.../data/model/serve/GroupThread.kt:46` |
| `note()` est le seul chemin d'une ligne de journal non-message | `core/.../data/model/serve/GroupThread.kt:49-69` |
| `turn.settled` compose son libellé localement depuis le roster | `core/.../data/model/serve/GroupThread.kt:52` |
| `turn.cancelled` écarte déjà une raison pour ne rien afficher | `core/.../data/model/serve/GroupThread.kt:56-58` |
| `driver_status` est la seule vue actuelle de la répartition | `core/.../data/model/serve/GroupThread.kt:114-127` |
| `rows` est un filtre, l'ordre du journal est conservé | `app/.../ui/bots/GroupRoomFeed.kt:98` |
| `discussing` dépend de `endsDiscussion` | `app/.../ui/bots/GroupRoomFeed.kt:73-78` |
| Le roster se rafraîchit par `groups.state` | `app/.../ui/bots/GroupRoomFeed.kt:264-266` |
| L'écran relit après un geste (`stop`, `retry`) | `app/.../ui/bots/GroupRoomFeed.kt:524-531` |
| Le tick est de 500 ms quand la salle travaille | `app/.../ui/bots/GroupRoomFeed.kt:580-583` |
| Une note s'affiche centrée, sans nouvelle composition | `app/.../ui/bots/GroupRoomScreen.kt:1009-1016` |
| Un sous-menu par membre est déjà dessiné | `app/.../ui/bots/GroupRoomScreen.kt:723-737` |
| L'indice de la salle promet `@nom` | `app/.../ui/bots/GroupRoomScreen.kt:956-957` |
| Le roster va de 2 à 6 membres | `core/.../data/model/serve/GroupRoom.kt:116-118` |
| `label` est le nom de salle, sinon la poignée | `core/.../data/model/serve/GroupRoom.kt:34-35` |
| Le Flux ne lit que les messages | `app/.../ui/bots/GroupRoomJournal.kt:30` |
| La voix ne lit que les messages et les brouillons | `app/.../ui/bots/GroupRoomFeed.kt:110-161` |
| `-32601` et `404` se reconnaissent comme une route absente | `app/.../ui/common/PageStates.kt:113-123` |
| Un `-32601` se dit aujourd'hui « Le serveur a refusé : … » | `core/.../data/net/TransportError.kt:160` |
| Un libellé de menu sans traduction fait échouer un test | `core/.../test/.../MenuAndSheetLocalizationTest.kt:56-73` |

---

## 10. Preuves attendues

1. **Le journal, pas l'écran** : 40 événements + un `bot.handoff` → les mêmes rangées dans le même
   ordre, plus une (`ARCHITECTURE-TOP5.md:144`). Test pur sur `rows`.
2. **Pas de fuite dans le Flux** : `roomThreadMessages` sur ce même journal rend les mêmes messages
   qu'avant, sans la ligne de passage (`GroupRoomJournal.kt:30`).
3. **La discussion continue** : `discussing` reste vrai après un `bot.handoff` seul, et devient faux
   après un `room.activity`.
4. **Le piège d'identité** : un `bot.handoff` dont la charge utile porte `member_id` change
   `actorID` — le test doit l'attraper, et le serveur ne doit jamais l'écrire.
5. **Pas de doublon** : un `turn.cancelled` de raison `handed_over` ne produit aucune ligne.
6. **Absence** : sans les deux méthodes dans `groups.capabilities`, aucune des deux entrées n'est
   dessinée, et aucune phrase « Réessayez » n'apparaît.
7. **Localisation** : les quatre libellés du menu et les cinq phrases de §4.3 sont au catalogue ;
   `MenuAndSheetLocalizationTest` passe.
8. **Compatibilité** : `GroupSupport(supported, writable)` compile encore, et la salle sans meneur se
   comporte exactement comme avant.

---

## 11. Ce que ce lot ne fait pas

- Il n'ajoute ni ne retire de membre : aucune route ne le permet aujourd'hui.
- Il ne donne au meneur aucun droit d'approbation, d'arrêt ou d'exclusion.
- Il n'affiche pas le meneur en permanence : la ligne de journal suffit, et l'en-tête est plein.
- Il ne touche ni au fil, ni à la scène vocale, ni aux cinq onglets (`ARCHITECTURE-TOP5.md:26-28`).
- Il ne change aucune route existante : `groups.lead` et `groups.handoff` sont deux méthodes de plus.

---

## 12. Décisions ouvertes

| # | Question | Ce que je propose | Ce qu'il faut pour trancher |
|---|---|---|---|
| O1 | Le meneur est-il **aussi** celui qui ouvre le tour après un message de la personne, ou seulement le premier destinataire ? | Premier destinataire : il peut rester muet et laisser un autre répondre. | Une observation d'usage : un meneur muet est-il un bug ou un choix ? |
| O2 | `summary` obligatoire ? | Facultatif : le journal est déjà le contexte. | — |
| O3 | Un `groups.lead` doit-il écrire un événement quand la valeur ne change pas ? | Non : `409 already_lead`, aucun événement. | — |
| O4 | Le retrait du meneur sur disparition de profil : événement `system` ou `user` ? | `system` : personne n'a cliqué. | La convention du core Hermes pour les événements qu'il écrit seul. |
| O5 | Les deux entrées de menu, ou une seule avec deux gestes ? | Deux entrées distinctes, chacune conditionnelle à sa route. | Une maquette : deux entrées dans un menu de six, c'est beaucoup. |

## 13. Points du plan qui m'ont semblé incohérents

1. **« une ligne de menu, comme `onOpenBotModels` » (`ARCHITECTURE-TOP5.md:142`).** Ce nom n'existe
   pas dans le dépôt. Le motif réel est `onPickBotModel` (`GroupRoomScreen.kt:723-737`), une entrée
   conditionnelle dans le `buildList` du `OnyxMenu` de la salle. Le plan cite un symbole inventé ; la
   spec s'ancre sur le vrai.
2. **« dans le fil, l'événement de passage (“Talk hands to Coder”) » (`:139`).** La formulation
   anglaise n'existe nulle part, et « Talk hands » ne veut rien dire. La phrase retenue est
   « Writer passe la main à Coder. », composée localement à partir des identifiants (§4.3), comme
   `turn.settled` le fait déjà.
3. **« Repli : serveur sans handoff → l'entrée de menu n'existe pas » (`:143`).** Il y a **deux**
   routes et donc **deux** entrées à éteindre séparément : un serveur peut connaître `groups.lead`
   sans connaître `groups.handoff` (un core plus ancien d'une version). Le plan traite les deux comme
   un seul drapeau ; la spec les sépare (§7.1), sinon une entrée resterait dessinée pour une route
   absente.
4. **« surtout serveur » pour une semaine (`:135`).** Les deux méthodes sont petites ; ce qui prend du
   temps, c'est de décider ce que le meneur **ne** fait pas (§5.2) et de tenir les deux pièges de
   §4.1. Le plan sous-estime la partie *interface* de ce lot, pas la partie serveur.
5. **Le plan ne dit pas ce qui arrive si le meneur meurt** (`:135-144`). C'est la question qui décide
   de la sémantique du passage : sans elle, on ne sait pas si `groups.handoff` déplace un pointeur ou
   transfère un tour. La spec tranche : les deux, selon que `task_id` est présent (§3, §6).
