# Navigateur de l'agent en direct, et prise en main depuis le téléphone

**L'architecture de référence.** Ce document est normatif, pas une esquisse : il fixe les décisions,
le contrat filaire complet, les fichiers touchés des deux côtés, les règles de sécurité et les
critères qui disent « c'est fini ». Ce qui n'y est pas écrit n'est pas dans le périmètre.

État : `main` = `0d349bc` (release 1.3.47). Rien de ce plan n'est encore écrit.

---

## 0. Décisions à valider

| # | Question | Défaut recommandé | Pourquoi |
|---|---|---|---|
| D1 | **Où vit le serveur ?** | Un **plugin** `onyx-browser`, comme `onyx-vault` | Installable et versionné à part, sans patcher Hermes ; `display` est déjà dans `hermes serve`, on n'y ajoute pas une deuxième source |
| D2 | **Budget de latence ?** | 3 img/s au repos, **8 img/s** en conduite | 3 suffit pour lire un formulaire ; 8 rend le tap et la frappe utilisables |
| D3 | **Périmètre ?** | **Téléphone seulement** en v1 | L'app web de Dots n'a que la carte, pas l'écran ; on l'ajoutera si le besoin existe |

Ces trois défauts sont la **base de ce document** : tout ce qui suit est écrit pour eux. Un veto sur
l'une des lignes se répercute sur les sections concernées (§2 pour D1, §3 pour D2, §5 pour D3).

---

## 1. Objectif, et non-objectifs

**Objectif.** Quand l'agent bute sur une page qui exige un humain — connexion, mot de passe, 2FA,
captcha, mur de consentement — il **demande**, le téléphone affiche son navigateur en direct, tu
agis dessus (tap, glisser, frappe), tu rends la main, et il **continue dans le même tour**, sur la
même page, déjà connecté.

**Non-objectifs de la v1** (à écrire dans les notes de version, pour que personne ne les attende) :

- pas de sélecteur de fichier, pas de téléchargement, pas d'onglets, pas de pincer-zoomer ;
- pas de passkeys / WebAuthn (impossible par frappe) ;
- pas de flux dans l'app web ;
- pas de conduction automatique par l'agent : il *demande*, il ne prend pas.

---

## 2. Architecture

```
Onyx (téléphone)                          Hermes serve + plugin onyx-browser (le profil)
─────────────────                         ───────────────────────────────────────────────
                                          browser_request_takeover(reason)   ← outil bloquant
browser.observe        ── JSON-RPC ───▶   ticket à usage unique (30 s) + viewer_id
                       ◀── ticket ────
WS /api/browser/ws     ── trames ─────▶   JPEG 0.6 / q55, 3 img/s (8 en conduite)
  input {seq, action, x, y, dy, text} ──▶ CDP Input.*        (refusé si pas de bail)
  done  {id, note}      ────────────────▶ débloque l'outil, l'agent reprend
browser.lease.acquire  ── JSON-RPC ───▶   holder: human        ← « You're driving »
browser.lease.release  ── JSON-RPC ───▶   holder: agent        ← « Hand back »
                       ◀── display.lease ── fan-out vers tous les clients
```

**Pourquoi pas le schéma de Dots.** Dots refait à la main ce que Hermes a déjà :
`display.observe` mint un ticket à usage unique (`tui_gateway/methods_display.py:152`), un
WebSocket sert la vue (`hermes_cli/web_routers/display.py:75`), et un **bail** dit qui conduit
(`tui_gateway/contracts/display.py`, `DisplayLease(holder: agent|human, epoch)`). On reprend ces
formes et on n'invente que la **source** : au lieu du Xvnc d'un bot, le Chromium de l'agent.

Le `epoch` est la pièce qu'on garde absolument : il bouge à chaque transition réelle, donc une
saisie calculée sous un bail périmé se détecte au lieu de cliquer ailleurs. Dots n'a rien de tel —
chez lui, `takeover` n'est qu'un accélérateur de flux, et le serveur applique les saisies sans
vérifier qui conduit.

### 2.1 Le rendu : un viewport de téléphone, pas un bureau réduit

Dots rend la page à ~1280 px puis la **réduit** à la largeur du téléphone : le texte du formulaire
devient minuscule, les cases à cocher font trois pixels, et le tap demande de viser. On ne fait pas ça.

Au début de la prise en main — **avant** toute frappe — le serveur pose un viewport de téléphone sur
la page de l'agent :

```
Emulation.setDeviceMetricsOverride {
  width: 390, height: 844, deviceScaleFactor: 2, mobile: true
}
Emulation.setUserAgentOverride { userAgent: <UA mobile> }
```

Trois conséquences, toutes voulues :

- le site sert **sa version mobile** : texte lisible, cibles larges, un tap = un pouce ;
- la trame envoyée est **1:1** avec ce que tu vois : plus de décalage entre l'image et le doigt ;
- la trame est plus petite pour la même lisibilité → moins d'octets par seconde.

Précautions, dans l'ordre :

1. le changement **reflue la page** : il est posé au moment où la prise en main commence, jamais
   pendant une saisie ;
2. certains sites forcent leur version bureau (URL `?desktop=true`, cookie) : dans ce cas le viewport
   ne suffit pas et on retombe sur le rendu réduit, sans casser le flux ;
3. à la fin de la prise en main, le viewport d'origine est **rendu** (`Emulation.clearDeviceMetricsOverride`)
   pour que l'agent retrouve la page telle qu'il la connaissait.

---

## 3. Le contrat filaire

### Méthodes (JSON-RPC, sur `ServeRpc`)

| Méthode | Params | Résultat |
|---|---|---|
| `browser.observe` | `profile?` | `{ticket, path: "/api/browser/ws", viewer_id, expires_in: 30}` |
| `browser.lease.acquire` | `profile?` | `{holder: "human", viewer_hash, epoch}` |
| `browser.lease.release` | `profile?` | `{holder: "agent", epoch}` |
| `browser.status` | `profile?` | `{available: bool, browser_open: bool, lease, epoch}` |

Erreurs : `5301 BROWSER_UNAVAILABLE` (aucun navigateur d'agent ouvert), `5302 LEASE_HELD` (un autre
conduit déjà), `5303 STALE_FRAME` (voir §7).

### Trames du WebSocket

**Téléphone → serveur**

| `type` | Champs | Effet |
|---|---|---|
| `watch` | `on`, `takeover` | ouvre/ferme le flux ; `takeover` passe la cadence à 8 img/s |
| `input` | `seq`, `action` ∈ `click\|scroll\|type\|key\|fill`, `x`,`y` (fractions 0..1), `dy`, `text` (≤ 2000), `key`, `secret` | rejoué sur la page |
| `done` | `id`, `note` | rend la main, débloque l'outil |

**Serveur → téléphone**

| `type` | Champs |
|---|---|
| `frame` | `seq`, `data` (JPEG base64), `url`, `title`, `width`, `height`, `live`, `ts` |
| `idle` | — (aucun navigateur ouvert, le flux continue de sonder à 1,5 s) |
| `lease` | `holder`, `viewer_hash`, `epoch` |
| `refused` | `seq`, `reason` ∈ `stale\|no_lease\|no_page\|origin` |

Le `seq` est **monotone par connexion** : il sert à refuser une saisie calculée sur une image
périmée (§7).

---

## 4. Serveur — le plugin `onyx-browser`

Nouveau dossier, sur le modèle de `~/.hermes/plugins/onyx-vault/` :

```
plugins/onyx-browser/
  plugin.yaml              # métadonnées + requires (aucun secret requis)
  __init__.py              # register(ctx) : l'outil + la route
  adapter.py               # la logique : capture, input, bail
  dashboard/browser_api.py # /api/plugins/onyx-browser/{health,observe,lease}
```

### Ce qu'on réutilise tel quel

- `SUPERVISOR_REGISTRY.capture(task_id, timeout=…)` → `tools/browser_supervisor.py:518` (vérifié
  présent sur cette machine) ;
- `Target.getTargets` + `Target.attachToTarget(flatten)` pour viser la **vraie** page (le
  superviseur est épinglé au premier onglet, souvent vide) — l'heuristique de Dots
  (`adapter.py:1367`) est correcte, on la reprend ;
- `Page.getLayoutMetrics` → `cssVisualViewport` (la taille qu'on mappera) ;
- `Page.captureScreenshot(jpeg, quality=55, optimizeForSpeed, clip=viewport, scale=0.6)` ;
- `Page.getNavigationHistory` → `url` + `title` pour la barre d'adresse ;
- `Input.dispatchMouseEvent` / `insertText` / `dispatchKeyEvent` — **exactement** le mapping de
  Dots (`adapter.py:1400-1425`), qui est juste.

### Ce qu'on écrit

1. **L'outil** `browser_request_takeover(reason)` : outil bloquant sur le thread de l'agent
   (`threading.Event`), plafond **15 min**, rend `{status: done|timeout, note}`. Enregistré dans un
   jeu d'outils `onyx` (offert dans les conversations Onyx seulement).
   C'est le point non négociable : **Hermes ferme son navigateur à la fin du tour** ; sans outil
   bloquant, la prise en main n'a pas d'objet.
2. **La route WS** `/api/browser/ws` : ticket à usage unique (30 s), même forme que
   `display.observe` ; `watch`/`input`/`done` en entrée, `frame`/`idle`/`lease`/`refused` en sortie.
3. **Le bail** : `holder`, `viewer_hash`, `epoch`, et l'événement `browser.lease` diffusé à tous les
   clients (le modèle de `tui_gateway/contracts/display.py`).
4. **Le garde de saisie** (§7) : la seule règle que Dots n'a pas.
5. **Le viewport mobile** (§2.1) : posé au début de la prise en main, rendu à la fin.
6. **La détection du formulaire de connexion** (§6.2) : au moment du `done`, le serveur relève
   l'hôte de la page et la présence d'un champ `type=password` dans le dernier formulaire soumis, et
   rend ces deux faits dans la réponse de l'outil — la proposition d'enregistrement s'appuie dessus.

---

## 5. Onyx — les fichiers

### Nouveaux

| Fichier | Rôle |
|---|---|
| `core/…/data/net/BrowserScreenApi.kt` | `browser.observe` / `lease.*` / `status`, les modèles (`BrowserFrame`, `BrowserLease`, `ScreenInput`) |
| `core/…/data/net/BrowserScreenClient.kt` | le WS (OkHttp) : watch, input, done ; reconnexion ; rien d'autre |
| `app/…/ui/browser/BrowserScreenViewer.kt` | l'écran : image, barre d'adresse, commandes de prise en main, bouton « Rendre la main » |
| `app/…/ui/browser/BrowserScreenViewModel.kt` | l'état : dernière trame, `seq`, bail, erreurs — **hors** de la portée de composition du fil |
| `app/src/test/…/browser/BrowserScreenTest.kt` | les verrous (voir §8) |

### Réutilisés, sans les copier

- `ServeRpc` (`core/…/ServeRpc.kt:44`) pour les méthodes — le même canal que la feuille de contexte ;
- `ComputerStream.viewWidthFor(paneWidthPx, screenWidth)` : la largeur d'image demandée se **négocie**,
  on ne code pas 60 % en dur (Dots le fait en dur) ;
- `ServerProbe` / `ServerCapability` : `BROWSER("api/plugins/onyx-browser/health")`, sur le modèle de
  `VAULT` — la ligne, la tuile et le raccourci n'existent que si la sonde répond ;
- `BoundedImages` pour le décodage borné, si les trames arrivent en pleine taille ;
- les cartes d'outils du fil pour la carte `takeover` (pending / expired / handed back).

### Ce qu'on ne réutilise **pas**

`ComputerTouch` : c'est un **pointeur relatif**, excellent pour un bureau 3840×2160, mauvais pour une
page web. Ici le tap est **absolu** — on clique là où on voit le champ.

### Règles de performance (les mêmes que le lot 1.3.35)

- décodage **hors** du thread principal, un `Bitmap` réutilisé par trame, jamais un par image ;
- l'état des trames vit dans **sa propre portée** : une image à 3 img/s ne doit pas recomposer le
  fil — c'est le défaut qu'on a mis trois versions à corriger (`GlassScrollFreeze`,
  `WorkingPreviewLine`) ;
- `watch { on: false }` sur `onDispose` **et** sur `ON_STOP` : rien ne défile en arrière-plan ;
- la saisie est **étranglée** (~30/s max) et annulée si `seq` a changé.

---

## 6. Le coffre — remplir, et enregistrer au login

Le coffre n'est pas un accessoire : c'est ce qui fait passer la feature de « je tape mon mot de passe
sur mon téléphone » à « l'agent se connecte sans jamais le lire ». Deux gestes, deux directions.

### 6.1 Remplir (`fill`)

Si `ServerCapability.VAULT` est prouvée, la prise en main offre les secrets du profil
(`vault.list` : nom + empreinte `••••de`) :

```
input { action: "fill", secret: "reddit", seq: 412 }     ← aucun secret ne circule
serveur : vault.reveal("reddit") en mémoire → Input.insertText(...)
```

Le mot de passe ne touche ni le téléphone, ni l'IME, ni le presse-papiers, ni le WebSocket, ni le
modèle. C'est **strictement mieux** que la version Dots, où le champ de saisie est un
`BasicTextField` **en clair** (`TakeoverControls`) : pas de masquage, pas de clavier mot de passe.

Conditions, sinon on ne le fait pas :

1. le secret porte son **origine** (`used_by`) et l'injection est **refusée** si
   `Page.getNavigationHistory` ne rend pas cette origine — refus **côté serveur** ;
2. l'injection exige `holder == human` ;
3. le coffre ne rend **jamais** de valeur à l'app : la route de lecture n'existe pas, et on n'en
   ajoute pas.

### 6.2 Enregistrer au login (`capture`)

Quand la page soumise était un formulaire de connexion, la prise en main propose d'**enregistrer**
les identifiants dans le coffre — **jamais d'elle-même**.

**Pas d'écriture automatique.** Le serveur ne voit que des `type {text}` sur un champ focalisé : il
ne sait pas *ce qu'il écrit*. Une écriture automatique reviendrait à laisser **la page** décider de
ce qui entre dans le coffre, sous le nom qu'elle choisit — un coffre qui accepte des écritures
dictées par la page n'est plus un coffre.

Le chemin est donc explicite, et il ne demande **aucune route serveur nouvelle** :

```
app → POST vault.put { profile, name: "reddit", value, used_by: ["reddit.com"] }
     ← { fingerprint: "••••de", created: true }        (la valeur ne revient jamais)
```

`VaultApi.put(name, value, profile, usedBy)` existe déjà côté Onyx, avec `used_by`, sans valeur dans
le retour et sans journal. Le serveur n'a rien à apprendre.

**D'où vient la valeur** :

| Cas | Comportement |
|---|---|
| identifiants tapés sur le téléphone | l'app les garde dans le ViewModel jusqu'au `done`, puis les efface après l'écriture |
| identifiants injectés par `fill` | rien à enregistrer : ils viennent du coffre |

**Les cinq règles** (un verrou de test par règle, §8) :

1. **jamais sans geste** — aucune écriture déclenchée par la page, par l'agent ou par un timer ;
2. **lié à l'origine** — `used_by` porte l'hôte de la page soumise, et `fill` refusera ce secret hors
   de cette origine ;
3. **la valeur est effacée** du ViewModel dès l'écriture, et le champ est masqué pendant la frappe ;
4. **seulement sur un vrai formulaire de connexion** — un champ `type=password` dans le formulaire
   soumis ; sinon on ne devine pas, on ne propose rien ;
5. **aucun journal, aucun fichier** — `VaultApi.put` ne journalise pas la valeur, contrat déjà écrit.

### 6.3 Deux entrées par compte

Le coffre tient **une valeur par nom**, et une connexion en demande deux. Convention, pas un nouveau
format :

```
reddit        → le mot de passe    used_by: ["reddit.com"]
reddit-email  → l'adresse          used_by: ["reddit.com"]
```

`fill` les remplit **dans l'ordre** (adresse, puis mot de passe), et l'empreinte affichée à côté du
champ dit lequel vient de partir. Le nom est pré-rempli à partir de l'hôte (`reddit.com` → `reddit`),
et reste modifiable.

### 6.4 Ce que ça donne, en pratique

Reddit : l'agent demande, tu remplis depuis le coffre ou tu tapes une fois, tu enregistres d'un geste,
tu rends la main. La fois suivante, la même page se remplit **sans que tu tapes rien**, et l'agent
poste. Le mot de passe n'a jamais quitté le serveur, et il n'est jamais passé par le modèle.

---

## 7. Sécurité — les six règles

1. **Le jeton est la frontière.** Le WS exige le ticket à usage unique de `browser.observe` (30 s),
   jamais le jeton d'appairage en clair dans l'URL.
2. **Pas de saisie sans bail.** `input` refusé si `holder != human` → `refused {reason: "no_lease"}`.
   (Dots ne le fait pas : chez lui le drapeau ne change que la cadence.)
3. **Pas de saisie périmée.** `seq` plus vieux que la dernière trame émise, ou `epoch` changé →
   `refused {reason: "stale"}`. C'est le garde qui empêche de cliquer sur un champ qui a bougé.
4. **Un seul conducteur.** `browser.lease.acquire` quand un autre conduit → `5302 LEASE_HELD` ; les
   autres appareils reçoivent `lease {holder: "human", viewer_hash}` et restent en lecture.
5. **Rien dans le modèle.** `input` ne touche que CDP : aucun chemin de `screen_input` vers
   l'historique ou vers le prompt. À vérifier par un test, pas par une intention.
6. **L'origine est la seule clé des secrets.** Un `fill` ou un `capture` dont l'hôte ne correspond
   pas à `used_by` est refusé côté serveur (`refused {reason: "origin"}`) — jamais côté app, jamais
   « avec un avertissement ».

---

## 8. Tests et verrous (le style du dépôt)

| Verrou | Ce qu'il tient |
|---|---|
| `BrowserScreenTest` (pur) | le mapping fractions → viewport CSS, le refus `stale`, le refus `no_lease`, la cadence (3 / 8 img/s) |
| `BrowserCapabilityTest` | la tuile, la ligne et le raccourci n'apparaissent **que** si `ServerCapability.BROWSER` répond ; aucune entrée morte |
| `BrowserNoModelLeakTest` | aucun champ de `ScreenInput` n'atteint `ChatViewModel` / l'historique (garde par lecture de source, comme `VaultScreenTest`) |
| `BrowserThreadPerfTest` | `BrowserScreenViewer` ne lit pas l'état du fil ; une trame ne recompose que l'écran |
| `VaultCaptureTest` | **aucune écriture** sans geste ; `used_by` porte l'hôte ; la valeur est effacée du ViewModel après l'écriture ; rien n'est proposé sans `type=password` |
| `VaultFillOriginTest` | un `fill` dont l'origine ne correspond pas à `used_by` est **refusé côté serveur** (`refused {reason: "origin"}`) |
| tests du plugin (côté serveur) | `browser.observe` rend un ticket à usage unique ; `input` sans bail est refusé ; l'outil rend `timeout` à 15 min |

Aucun test ne mesure de fps réel : pas d'appareil sur la machine de build. Le protocole de mesure de
`AUDIT-PERFORMANCE.md` §6 s'applique si tu veux des chiffres.

---

## 9. Les lots

| Lot | Contenu | Fini quand |
|---|---|---|
| **M1 — le tube** | plugin : outil bloquant + `browser.observe` + WS qui diffuse des trames JPEG + viewport mobile (§2.1), sans input | un test de bout en bout montre 3 img/s, une trame 390×844 et un `done` qui débloque l'outil |
| **M2 — la main** | `input` (click, scroll, type, key) + bail + refus `no_lease` / `stale` | on se connecte à un site de test depuis l'écran, et une saisie sans bail est refusée |
| **M3 — Onyx** | l'écran Compose, le ViewModel, la sonde `BROWSER`, la carte `takeover` dans le fil | tout M2 est faisable depuis le téléphone, sans recomposer le fil |
| **M4 — le coffre** | `fill` + `capture` (enregistrer au login) + liaison d'origine + empreinte | le mot de passe ne quitte jamais le serveur ; un `fill` sur une autre origine est refusé ; aucune écriture sans geste |

Chaque lot se termine par un commit + notes de version + les trois APK, comme d'habitude.

---

## 10. Risques, dits franchement

- **Le seam CDP est une API interne.** `SUPERVISOR_REGISTRY.capture` peut changer entre deux
  versions de Hermes. Le README de Dots l'écrit lui-même. Conséquence : le plugin peut casser à une
  mise à jour, et il doit le dire clairement quand il ne trouve plus le seam (message, pas silence).
- **3 img/s, ce n'est pas du partage d'écran.** Un captcha en grille d'images est pénible. Si ça
  devient bloquant, la parade est `Page.startScreencast` (vrai flux), pas une cadence plus haute.
- **Coût CPU serveur** : un JPEG plein cadre toutes les 300 ms (120 ms en conduite).
- **Profils fantômes.** Si deux onglets sont ouverts, « la dernière vraie page » peut être le mauvais.
- **Session et durée de vie du profil navigateur** : à mesurer avant de promettre « tu restes
  connecté » (voir la conversation du 5/11).

---

## 11. Ce que je ne fais pas en v1

- streaming audio/vidéo de la page (le JPEG suffit pour un formulaire) ;
- conduction simultanée à deux ;
- app web ;
- remplissage automatique sans geste de l'utilisateur ;
- mémorisation des logins dans le coffre (le coffre garde le **mot de passe**, pas la session : un
  cookie est un jeton porteur sans liaison d'origine).
