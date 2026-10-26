# Pont « Ordinateur » — l'écran du PC en direct, et ses entrées

Ce dossier contient le **compagnon PC** de l'écran « Ordinateur » d'Onyx : le service qui capture
l'écran de la machine, l'envoie à l'app en quelques images par seconde, et prend en retour les
clics, les glissers et les frappes.

- `bridge.py` — le service (capture + entrées + prise de main + approbation).
- `check-bridge.py` — l'éprouve de bout en bout, sans l'app.

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
# Dépendances: une fois, dans un environnement à part
/usr/bin/python3 -m venv ~/.onyx-computer-bridge/venv
~/.onyx-computer-bridge/venv/bin/pip install python-xlib websockets

# L'éprouve: affichage, ffmpeg, XTest, et une image
python3 bridge.py --display :1 doctor

# Le service (local seulement)
python3 bridge.py --display :1 --port 8795 --fps 6
```

Au premier lancement, un **jeton** est créé dans `~/.onyx-computer-bridge/token` (droits `0600`) et
n'est jamais affiché. C'est celui qu'on colle dans l'app, écran « Ordinateur » → *More* → *Adresse
du pont*.

Dans l'app, l'adresse à saisir dépend d'où l'on vient :

| D'où | Adresse |
|---|---|
| Un émulateur Android sur le PC (le pont tourne sur l'hôte) | `http://10.0.2.2:8795` |
| Un téléphone sur le même réseau local | `http://<ip-du-pc>:8795` (voir « Réseau local ») |
| Depuis l'extérieur | voir « Exposer » plus bas — **à décider et à monter à la main** |

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

- **Écoute locale par défaut** (`127.0.0.1`). Rien n'est exposé sur l'Internet public par ce
  script.
- **Jeton obligatoire** sur chaque connexion (comparaison à temps constant), lu dans un fichier
  `0600`, jamais journalisé, jamais transmis à un client, et `GET /health` ne rend que
  `{"ok": true}` — ni image, ni jeton, ni nom de machine.
- **Le jeton reste dans le trousseau du téléphone** (Keystore Android) et s'affiche masqué en
  entier, avec un bouton *copier* : l'app ne le montre jamais en clair.
- **Ce que le pont ne fait pas** : il ne prend pas la main sur l'agent. Le `computer_use` de
  Hermes, lui, n'est pas au courant de la prise de main de l'app (sa vue est un bureau de bot, pas
  cet écran) : pendant qu'on pilote au doigt, il vaut mieux laisser l'agent tranquille.
- Toucher l'écran déplace **le vrai pointeur** et frappe de **vraies touches** (XTest) : c'est ce
  que veut dire « prendre la main ». Le temps de la prise, la souris de la machine est prise.

### Réseau local (téléphone sur le même Wi-Fi)

```bash
python3 bridge.py --display :1 --port 8795 --host 0.0.0.0
```

À ne faire que sur un réseau de confiance, et en gardant le jeton : c'est un accès au clavier et à
la souris de la machine.

### Exposer (tunnel) — **à la décision du propriétaire, pas fait ici**

Le Dashboard a déjà son tunnel Cloudflare ; ce pont **n'est pas** exposé avec lui. Si un jour
l'accès depuis l'extérieur est voulu, la même recette s'applique, en connaissance de cause :

```bash
# Détaché, sinon un redémarrage du gateway tue le tunnel (voir la skill « exposer Hermes »)
setsid nohup cloudflared tunnel --url http://127.0.0.1:8795 > ~/tunnel-computer.log 2>&1 &
```

puis, dans l'app, l'adresse `https://<…>.trycloudflare.com` (le pont en fait `wss://…/ws`). Trois
choses à savoir avant : l'URL change à chaque relance du tunnel ; **le jeton devient la seule
serrure** (mettre un mot de passe de tunnel, sinon quiconque a l'URL pilote la machine) ; et un
tunnel ouvert sur ce port donne le clavier et la souris, pas seulement l'image.

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
