# Pont « Ordinateur » — l'écran du PC en direct, et ses entrées

Ce dossier contient le **compagnon PC** de l'écran « Ordinateur » d'Onyx : le service qui capture
l'écran de la machine, l'envoie à l'app en quelques images par seconde, et prend en retour les
clics, les glissers et les frappes.

- `bridge.py` — le service (capture + entrées + prise de main + approbation).
- `check-bridge.py` — l'éprouve de bout en bout, sans l'app, **en local**.
- `check-public.py` — la même éprouve **depuis l'adresse publique** (tunnel), jeton refusé compris.
- `check-token.py` — vérifie que le jeton est **accueilli tel qu'il arrive** (blancs, `Bearer ` en
  trop, `?token=`) et que chaque refus porte **son motif** (`bad-token:missing`, `bad-token:len`,
  `bad-token:mismatch`).

## Pourquoi un pont, et pas une route du serveur Hermes

L'enquête a été faite avant d'écrire : **le serveur Hermes n'expose rien qui capture l'écran du
poste ni qui y injecte des entrées.**

| Ce qui existe | Ce que ça donne | Pourquoi ça ne suffit pas |
|---|---|---|
| `computer_use` (cua-driver) | Pilote vraiment le bureau : captures, souris, clavier, X11 | C'est un **outil dans un tour d'agent**, pas un point d'accès : aucun client ne peut s'y abonner ni envoyer un clic |
| `GET /api/display/ws` du Dashboard | Relaie du **RFB** (VNC) en WebSocket, avec une prise de main (`lease`) et une éviction propre | Ne sert qu'un **bureau de bot** : il exige `<HERMES_HOME>/bot-desktop/rfb.sock` (Xvnc en bac à sable) et un **ticket** `bot-desktop` à usage unique émis pour le Desktop. Rien de tout cela n'est l'écran du PC |
| `/api/tools/computer-use/*` | L'état des permissions du pilote | Pas de capture, pas d'entrée |
| `terminal` / PTY du Dashboard | Un shell | Ce n'est pas l'écran |

Le pont reprend donc, côté app, **les idées** de ce qui existe déjà côté Hermes — la main
(`agent` / `human`), le refus d'entrée sans la main, une seule vérité d'état — mais sur **l'écran
réel** de la machine, et sur son propre canal. C'est le même choix que le serveur TTS compagnon :
un service local, que l'app joint à son adresse.

## Lancer

```bash
# Dépendances : une fois, dans un environnement à part
/usr/bin/python3 -m venv ~/.onyx-computer-bridge/venv
~/.onyx-computer-bridge/venv/bin/pip install python-xlib websockets

# L'éprouve : affichage, ffmpeg, XTest, et une image
python3 bridge.py --display :1 doctor

# Le service (local seulement)
python3 bridge.py --display :1 --port 8795 --fps 6 --turbo-fps 12
```

En service, maintenant : deux **unités utilisateur** systemd (pas de `sudo`), `enable`, qui
redémarrent toutes seules.

```bash
systemctl --user enable --now onyx-computer-bridge.service   # le pont (127.0.0.1:8795)
systemctl --user enable --now onyx-computer-tunnel.service    # le tunnel public cloudflared
```

| Unité | Ce qu'elle fait | Son journal |
|---|---|---|
| `onyx-computer-bridge.service` | le pont : capture + entrées, sur `127.0.0.1:8795` | `~/.onyx-computer-bridge/bridge.log` (et `journalctl --user -u onyx-computer-bridge`) |
| `onyx-computer-tunnel.service` | le tunnel `cloudflared` vers `http://127.0.0.1:8795` | `~/.onyx-computer-bridge/tunnel.log` |

<details>
<summary>Ouvrir l'unité du pont</summary>

```ini
# ~/.config/systemd/user/onyx-computer-bridge.service
[Service]
WorkingDirectory=~/onyx-computer/tools/onyx-computer-bridge
ExecStart=~/.onyx-computer-bridge/venv/bin/python …/bridge.py --display :1 --port 8795 --fps 6
Restart=always
Environment=DISPLAY=:1
Environment=XAUTHORITY=/run/user/1000/gdm/Xauthority
```
</details>

Au premier lancement, un **jeton** est créé dans `~/.onyx-computer-bridge/token` (droits `0600`) et
n'est jamais affiché. C'est celui qu'on colle dans l'app, écran « Ordinateur » → *More* → *Adresse
du pont*.

Dans l'app, l'adresse à saisir dépend d'où l'on vient :

| D'où | Adresse |
|---|---|
| Un émulateur Android sur le PC (le pont tourne sur l'hôte) | `http://10.0.2.2:8795` |
| Un téléphone sur le même réseau local | `http://<ip-du-pc>:8795` (voir « Réseau local ») |
| **Depuis un autre réseau** (téléphone ailleurs, 4G, autre ville) | `https://<…>.trycloudflare.com` — voir « Depuis un autre réseau » |

## Le protocole, en une page

Une seule WebSocket, `ws://<hôte>:<port>/ws`. Le jeton passe en en-tête
(`Authorization: Bearer …` ou `X-Onyx-Token`), jamais dans l'URL par défaut.

**Le pont → l'app**

| Trame | Contenu |
|---|---|
| binaire | une image JPEG de l'écran (l'écran entier, réduit ; le curseur est dedans) |
| `{"t":"hello"}` | `screen` (taille réelle du PC), `view` (taille de l'image), `fps`, `viewer`, `holder` |
| `{"t":"state"}` | `holder` (`agent`/`human`), `viewer`, `busy`, `last` (la dernière action) |
| `{"t":"ack"}` / `{"t":"denied"}` | le sort de la dernière demande, avec sa raison |
| `{"t":"run_result"}` | la sortie d'une commande approuvée |

**L'app → le pont**

| Demande | Effet |
|---|---|
| `take` / `release` | prendre la main, la rendre |
| `tap` `x`,`y`,`button` | un clic aux coordonnées de **l'écran du PC** |
| `drag` `from`,`to` | un glisser |
| `scroll` `x`,`y`,`amount` | la molette |
| `text` `text` | la frappe, accents compris, par la disposition réelle du clavier |
| `key` `keys`,`approved` | un raccourci (`ctrl+c`, `Return`, `alt+F4`…) |
| `run` `command`,`approved` | une commande — **refusée sans `approved: true`** |
| `stream` `width`,`quality` | la taille et la qualité de l'image **voulues** — ce n'est pas une entrée sur le PC, la main n'est donc pas requise |
| `rate` `moving` | l'app se déplace : cadence de geste, sinon cadence de repos |

**Ce que l'app demande, le pont le borne.** `stream` est ramené à `MIN_VIEW_WIDTH`…`--max-width`
(jamais plus que l'écran, toujours pair), le quantificateur à 2…31, et `rate` à `--fps`…`--max-fps` :
un client ne décide pas de brûler le processeur du PC. Après un changement de taille, le pont
**réannonce** `hello` — l'app doit placer ses clics dans une géométrie qui existe. Redemander la
même chose ne redémarre pas la capture ; quand plus personne ne regarde, la cadence retombe au
repos.

Le débit se règle donc des deux côtés : l'app annonce la largeur **réelle** de son volet (sur un
téléphone plié, ~1080 pixels — demander 1280 encodait, transmettait et décodait 200 pixels jetés),
et le pont, lui, ne descend jamais sous ce qui reste lisible ni ne monte au-delà de son plafond.

## La main, et l'approbation

- **Un seul détenteur.** Tant que la main est à l'agent, chaque entrée de l'app est refusée
  (`denied`, raison `not_holder`) : on peut regarder sans cliquer. Un second client qui demande la
  main pendant qu'un autre la détient est refusé aussi (`taken_by_other`).
- **Fermer la fenêtre rend la main.** Une connexion qui se termine rend la main de lui-même ; une
  session reprise par l'app (l'utilisateur quitte l'écran et revient) ne la rend pas.
- **L'approbation est dans l'app, le refus est dans le pont.** L'app demande avant d'envoyer ; le
  pont, lui, ne demande rien : il refuse ce qui n'est pas marqué approuvé. Une app modifiée ne peut
  donc pas contourner la question. Sont concernés les **commandes** (`run`) et les **raccourcis de
  session** (verrouillage, déconnexion, changement de console).
- **Quelques commandes sont refusées même approuvées** (formatage, écriture directe sur un disque,
  `rm -rf /`, arrêt de la machine) : le motif est gravé des deux côtés, dans l'app et dans le pont.

## Sécurité

- **Écoute locale par défaut** (`127.0.0.1`). Le pont lui-même n'ouvre rien ; pour l'extérieur,
  c'est le tunnel cloudflared qui publie, et lui seul.
- **Jeton obligatoire** sur chaque connexion, comparé **à temps constant** (`secrets.compare_digest`),
  lu dans un fichier `0600`, jamais journalisé — pas même dans le chemin d'une URL refusée — jamais
  transmis à un client, et `GET /health` ne rend que `{"ok": true}` (ni image, ni jeton, ni nom de
  machine).
- **Aucun jeton dans l'URL** côté app : il part dans un en-tête `Authorization: Bearer <jeton>`. Le
  pont **tolère** encore `?token=…` (compatibilité), mais ne l'utilisez pas : une URL traîne dans
  les journaux de tout le monde.
- **L'origine réelle est journalisée.** Derrière le tunnel, le pair TCP vaut toujours `127.0.0.1` :
  le pont lit `CF-Connecting-IP` (puis `X-Forwarded-For`) et note l'adresse de l'appelant sur
  chaque connexion, chaque refus et chaque action —
  `{"event":"action","kind":"click","x":1920,"y":1950,"origin":"2a01:cb1d:…"}`.
- **Les tentatives sont limitées.** 5 jetons faux en 60 s depuis une même origine → cette origine
  est refusée 5 min (`{"event":"token_blocked", "retry_in":…}`), **même avec le bon jeton** le temps
  du blocage. La limitation vit en mémoire : un `restart` du pont la remet à zéro.
- **Le jeton reste dans le trousseau du téléphone** (Keystore Android) et s'affiche masqué en
  entier, avec un bouton *copier* : l'app ne le montre jamais en clair.
- **Ce que le pont ne fait pas** : il ne prend pas la main sur l'agent. Le `computer_use` de
  Hermes, lui, n'est pas au courant de la prise de main de l'app (sa vue est un bureau de bot, pas
  cet écran) : pendant qu'on pilote au doigt, il vaut mieux laisser l'agent tranquille.
- Toucher l'écran déplace **le vrai pointeur** et frappe de **vraies touches** (XTest) : c'est ce
  que veut dire « prendre la main ». Le temps de la prise, la souris de la machine est prise.

### Le lien se juge au flux, pas à un ping de contrôle

Le pont sert **sans** keepalive WebSocket (`ping_interval=None`) : il envoie déjà six images par
seconde — le flux est le battement de cœur, et une socket morte se voit à l'envoi suivant. Un ping
de contrôle n'ajoutait qu'une façon de tuer une session saine : son pong fait la queue derrière des
dizaines de kilo-octets d'image, et un aller-retour de réseau mobile suffit à dépasser les 20 s du
délai. Côté app, un ping de contrôle toutes les 20 s produisait exactement ce texte à l'écran :
« sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs) » — le texte de
la bibliothèque cliente, en anglais et en millisecondes, sur une session qui avait bien été
établie. Le battement est donc **applicatif** : l'app envoie `{"t":"ping"}`, le pont répond
`{"t":"pong"}`, et la session n'est déclarée morte qu'après 45 s sans **aucune** trame.

Mesuré le 26/10 à travers le tunnel public, trois connexions simultanées tenues 380 s (2 283
trames chacune) :

- ping de contrôle **client → pont** : pong en **0 ms** ;
- ping de contrôle **pont → client** : reçu à **+20 s** — il traverse donc cloudflared ;
- un client qui ne répond **pas** aux pings est coupé à **+40 s** par le pont, code 1011, motif
  `keepalive ping timeout`.

Ce dernier point tranche le diagnostic : le pont ne dit pas « sent ping but didn't receive pong
within 20000ms ». Ce texte-là ne venait pas du pont mais du client, et il ne disait rien d'utile à
qui lisait l'écran.

### Ce qui reste possible pour qui aurait l'adresse **et** le jeton

À dire sans détour : **tout ce que l'écran et le clavier permettent.** Le jeton ne limite pas les
dégâts, il limite l'entrée.

- voir l'écran en continu (6 i/s) — y compris ce qui s'y affiche en clair ;
- cliquer, glisser, taper du texte, donc **ouvrir un terminal et y lancer des commandes** ;
- la route `run … approved: true` exécute une commande shell : c'est une **exécution de code à
  distance**, avec les droits de l'utilisateur `john` (dossiers, navigateur, trousseau déverrouillé,
  accès réseau interne) ;
- les seules bornes : les raccourcis de session et les commandes destructrices connues
  (`mkfs`, `dd of=/dev/…`, `rm -rf /`, `shutdown`…) sont refusées **même approuvées**, et la main ne
  peut être détenue que par un seul client à la fois.

Autrement dit : **l'adresse et le jeton, ensemble, valent le contrôle de la machine.** L'adresse
seule ne vaut rien (essai → `token_rejected` au journal).

### Changer le jeton

À faire **maintenant** : ce jeton a circulé dans des journaux de session.

```bash
# 1. un jeton neuf
python3 -c "import secrets; print(secrets.token_hex(24))" > ~/.onyx-computer-bridge/token
chmod 600 ~/.onyx-computer-bridge/token

# 2. le pont le relit
systemctl --user restart onyx-computer-bridge.service

# 3. le recopier dans l'app : Ordinateur → More → Adresse du pont → champ Token
cat ~/.onyx-computer-bridge/token
```

L'ancien jeton ne vaut plus rien dès l'étape 2 ; l'app se reconnecte toute seule avec le nouveau.
Le tunnel, lui, n'a pas besoin d'être redémarré — donc **l'adresse publique ne change pas**.

### Réseau local (téléphone sur le même Wi-Fi)

```bash
python3 bridge.py --display :1 --port 8795 --host 0.0.0.0
```

À ne faire que sur un réseau de confiance, et en gardant le jeton : c'est un accès au clavier et à
la souris de la machine.

### Depuis un autre réseau (tunnel public) — **en service**

C'est monté. Le pont n'écoute toujours que sur `127.0.0.1` ; c'est **cloudflared** qui publie une
adresse `https://…trycloudflare.com` et la rabat sur le pont. Rien n'est ouvert sur la box.

#### L'adresse à saisir dans l'app

Dans l'app : écran **Ordinateur** → **More** (en haut à droite) → **Adresse du pont**, puis collez
l'adresse dans le champ **Address** :

```
https://give-overnight-reveal-nano.trycloudflare.com
```

**Gardez le `https://`.** L'app en tire `wss://…/ws`. Sans schéma, elle prendrait `ws://…` (port 80,
en clair) — ça finit par marcher, mais c'est le trajet non chiffré. Le champ **Token** est juste
au-dessous : voir plus bas où le prendre.

Pour réafficher l'adresse du moment :

```bash
grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' ~/.onyx-computer-bridge/tunnel.log | tail -1
```

> **L'adresse change à chaque redémarrage du tunnel** (c'est une adresse « express », sans compte
> Cloudflare). Si l'app ne joint plus le pont après un redémarrage du PC, c'est presque toujours ça :
> relisez l'adresse ci-dessus et recollez-la dans l'app.

#### Le jeton

Il vit dans `~/.onyx-computer-bridge/token` (droits `0600`). L'app le garde dans le trousseau
Android et ne l'affiche jamais en entier.

```bash
cat ~/.onyx-computer-bridge/token      # à recopier dans l'app, une fois
```

#### Voir l'état, redémarrer

```bash
systemctl --user status  onyx-computer-bridge.service onyx-computer-tunnel.service
systemctl --user restart onyx-computer-bridge.service     # relit le jeton, réarme la limitation
systemctl --user restart onyx-computer-tunnel.service     # ⚠ change l'adresse publique
journalctl --user -u onyx-computer-bridge -n 40 --no-pager
tail -f ~/.onyx-computer-bridge/bridge.log                # qui s'est connecté, qui a cliqué
```

#### Tout fermer (plus aucun accès depuis l'extérieur)

```bash
systemctl --user stop onyx-computer-tunnel.service    # coupe l'accès public
systemctl --user stop onyx-computer-bridge.service    # coupe aussi le pont
systemctl --user disable onyx-computer-tunnel.service # pour que ça ne reparte pas au démarrage
```

Le simple `stop` du tunnel suffit : l'adresse publique cesse de répondre, le pont reste utilisable
en local.

#### Éprouver l'accès public, sans le téléphone

```bash
python3 check-public.py --url wss://give-overnight-reveal-nano.trycloudflare.com/ws --tap 1920,1950
```

(voir « Éprouver » plus bas pour ce que la sortie doit dire)

#### Ce que ça change, côté risque

Publier ce port, c'est publier **l'écran, la souris et le clavier** du PC. Le jeton devient la
**seule** serrure ; ce qui suit la protège (voir « Sécurité »), mais lisez la ligne « ce qui reste
possible » avant de laisser ça ouvert en permanence.

## Éprouver

```bash
python3 check-bridge.py --url ws://127.0.0.1:8795/ws --tap 320,180 --type "bonjour" \
    --refuse-first --command "uname -a"
```

Ce que le script doit rendre, et ce que le journal (`~/.onyx-computer-bridge/bridge.log`, une ligne
JSON par événement) doit montrer :

```
jeton faux refusé (ConnectionClosedError)      → {"event":"token_rejected"}
salut : écran [3840, 2160] → image [1280, 720], 6 i/s
état initial : main à agent
sans la main : denied / not_holder             → {"event":"denied","reason":"not_holder"}
prise de main : ack / taken                    → {"event":"takeover"}
commande sans approbation : denied / needs_approval   → {"event":"denied","reason":"needs_approval"}
commande approuvée : run_result / sortie 0     → {"event":"run_started"} … {"event":"run_finished"}
main rendue : ack / released                   → {"event":"handback"}
après le rendu : denied / not_holder
```

### Depuis l'adresse publique (ce que voit le propriétaire à 1000 km)

```bash
python3 check-public.py --url wss://give-overnight-reveal-nano.trycloudflare.com/ws --tap 1920,1950
```

Ce que la sortie rend (relevé réel du montage) :

```
sans aucun jeton → refusé à la session (code 4401)
avec un faux jeton → refusé à la session (code 4401)
salut : écran [3840, 2160] → image [1280, 720], 6 i/s, main à agent
image reçue : 95242 octets, JPEG 1280x720
prise de main : ack / taken
clic à 1920,1950 (écran du PC) : ack / clicked 1920,1950
pointeur relu sur le PC : 1920 1950 (attendu 1920 1950)      ← le clic est bien arrivé au PC
main rendue : ack / released

— limitation des tentatives —
  essai 1..5 : code 4401
  essai 6 : code 4429  ← mise au coin
bon jeton pendant la mise au coin → refusé à la session (code 4429)
```

et le journal du pont, **avec l'adresse d'origine** :

```
{"event":"token_rejected","origin":"2a01:cb1d:…","path":"/ws","reason":"len","token_len":48,
 "expected_len":20,"attempts_left":4}
{"event":"client_connected","viewer":"a85ac1b1","origin":"2a01:cb1d:…","clients":1}
{"event":"action","kind":"click","x":1920,"y":1950,"origin":"2a01:cb1d:…"}
{"event":"token_blocked","origin":"2a01:cb1d:…","retry_in":299.9}
```

`reason` et les deux longueurs sont la **preuve** d'un jeton refusé : `missing` (aucun jeton
présenté), `len` (le jeton reçu n'a pas la longueur de celui du pont — un collage tronqué, ou un
jeton d'avant le changement), `mismatch` (même longueur, autre valeur). Le jeton lui-même n'est
jamais écrit, et **on n'écrit pas non plus d'empreinte** : huit caractères hexadécimaux d'un
SHA-256 se forcent hors ligne en quelques secondes contre un jeton de vingt caractères, ce serait
publier le secret. La longueur, elle, ne dit rien du secret et suffit à trancher.

> L'éprouve met **votre propre origine** au coin pour 5 min. Pour réarmer tout de suite :
> `systemctl --user restart onyx-computer-bridge.service`.
