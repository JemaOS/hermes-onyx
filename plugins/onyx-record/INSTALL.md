# Installation — plugin Hermes `onyx-record`

Serveur de la **démonstration → skill** (lot 3) : bail d'enregistrement, journal
d'actions canonique, brouillon Markdown. Servi par le Dashboard Hermes sous
`/api/plugins/onyx-record/`. Aucune UI, aucun onglet, aucun redémarrage.

Tous les chemins ci-dessous sont ceux de la machine de John (`/home/john`).

---

## 0. Ce qui est installé, et où

| Élément | Emplacement |
|---|---|
| Code du plugin | `~/.hermes/plugins/onyx-record/dashboard/` |
| Activation | `plugins.enabled` dans `~/.hermes/config.yaml` |
| Données | **aucune** — tout vit en mémoire, 120 s au plus |

Le plugin n'écrit rien : ni base, ni fichier, ni journal. Un journal envoyé à
`skills.draft` n'est pas conservé après la réponse, et un journal reçu par
`skills.record.stop` disparaît au bout de 120 s (fenêtre de collecte de la spec).
Un redémarrage du Dashboard perd donc, au pire, un journal qui allait expirer.

---

## 1. Copier le plugin

```bash
mkdir -p ~/.hermes/plugins/onyx-record
cp -r /home/john/Bureau/onyx-record-plugin/dashboard \
      ~/.hermes/plugins/onyx-record/dashboard
chmod -R go-w ~/.hermes/plugins/onyx-record
```

Résultat attendu :

```
~/.hermes/plugins/onyx-record/dashboard/
├── manifest.json      # déclaration (name, tab masqué, api)
├── plugin_api.py      # router = APIRouter() — les cinq routes
├── onyx_record.py     # cœur : validation, bornes, brouillon Markdown
└── dist/index.js      # module vide (aucune UI)
```

`INSTALL.md`, `ECARTS.md`, `README.md` et `tests/` n'ont pas besoin d'être
copiés : Hermes ne lit que `dashboard/manifest.json`. Le plugin peut cohabiter
avec `onyx-vault` — ce sont deux dossiers, deux lignes dans `plugins.enabled`,
et deux modules Python distincts.

---

## 2. Activer le plugin

Ouvrir `~/.hermes/config.yaml` et ajouter **exactement cette ligne** (4 espaces
d'indentation, sous `plugins.enabled`) :

```yaml
    - onyx-record
```

Bloc complet si `plugins.enabled` n'existe pas encore :

```yaml
plugins:
  enabled:
    - onyx-record
```

Si `plugins:` existe déjà, ajouter seulement la ligne `- onyx-record` dans la
liste `enabled:` existante, à la même indentation.

> ⚠️ `hermes plugins enable onyx-record` **ne fonctionne pas** : la CLI ne
> connaît que les plugins qui portent un `plugin.yaml` avec du code `register(ctx)`.
> Un plugin « dashboard-only » s'active uniquement par cette ligne. C'est aussi la
> garde de sécurité du Dashboard : sans elle, `plugin_api.py` n'est jamais importé
> (`_plugin_api_mount_skip_reason`, GHSA-mcfc-hp25-cjv7).

Aucun redémarrage : le Dashboard découvre le plugin à la première requête
(`_PluginApiHotMount` → `_try_hot_mount_plugin_api`). **Précision mesurée** : à
chaud, Hermes insère les routes juste avant son repli ; un montage forcé à la
main après l'import les laisserait *derrière* ce repli et rendrait
`404 {"detail":"Not Found"}`. C'est pourquoi un plugin activé au démarrage est le
cas nominal, et pourquoi le test de montage démarre un vrai serveur.

---

## 3. Vérifier

```bash
PORT=9119   # port du Dashboard Hermes
TOKEN=$(curl -s "http://127.0.0.1:$PORT/" \
        | sed -n 's/.*__HERMES_SESSION_TOKEN__="\([^"]*\)".*/\1/p' | head -1)
B="http://127.0.0.1:$PORT/api/plugins/onyx-record"
H="X-Hermes-Session-Token: $TOKEN"

# 1. le serveur répond-il ? (aucun journal, aucun identifiant de bail)
curl -s "$B/health"; echo

# 2. ouvrir un bail
curl -s -X POST "$B/skills/record/start" -H "$H" -H 'Content-Type: application/json' \
  -d '{"bridge":{"endpoint":"192.168.1.24:8765","session":"pc-7f21"}}'; echo
# -> {"session_id":"rec_…","started_at":…,"deadline_at":…,"max_seconds":600,"grace_seconds":120}

# 3. fermer le bail sans journal (abandon)
curl -s -X POST "$B/skills/record/stop" -H "$H" -H 'Content-Type: application/json' \
  -d '{"session_id":"rec_…","journal":null}'; echo
# -> {"…","end_reason":"discarded"}

# 4. convertir un journal (sans état, aucun bail nécessaire)
curl -s -X POST "$B/skills/draft" -H "$H" -H 'Content-Type: application/json' \
  -d @journal.json; echo
# -> {"name":…,"title":…,"steps":…,"markdown":…,"review":[…],"warnings":[…],"journal_bytes":…}
```

Attendu : `health` → `"ok":true` ; `start` → `200` avec `max_seconds: 600` ;
un second `start` sans `stop` → `409 {"detail":{"code":"already_recording",…}}` ;
`stop` → `200`, et un second `stop` rend **le même `bytes`** ; `draft` → `200`
avec un `markdown` identique au golden de l'app.

Sans jeton, toute requête répond `401 {"detail":"Unauthorized"}` : c'est
l'authentification du Dashboard, que le plugin ne contourne pas.

Si le Dashboard est derrière la page de connexion (`dashboard_auth`), le jeton
d'en-tête est remplacé par le cookie de session : utilisez l'onglet Réseau du
navigateur, ou testez depuis l'app.

---

## 4. Lancer les tests

```bash
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)

# toute la suite, un seul bloc, un seul processus (85 tests)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover \
    -s /home/john/Bureau/onyx-record-plugin/tests \
    -t /home/john/Bureau/onyx-record-plugin/tests -v
```

ou, depuis le dossier du plugin :

```bash
cd /home/john/Bureau/onyx-record-plugin
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" -m unittest discover -s tests -v
```

Le test de montage démarre le vrai Dashboard dans un `HERMES_HOME` jetable
(deux processus enfants, aucun accès à `~/.hermes`) : il ne faut donc ni
redémarrer ni arrêter le gateway en cours.

---

## 5. Désinstaller

```bash
# 1. retirer le plugin
rm -rf ~/.hermes/plugins/onyx-record

# 2. retirer la ligne "    - onyx-record" de ~/.hermes/config.yaml
```

Sans l'étape 2, le Dashboard journalise un plugin activé mais introuvable. Rien
d'autre à nettoyer : le plugin n'écrit aucun fichier.

---

## 6. Réglages

Aucun. Pas de variable d'environnement, pas de clé, pas de dossier de données :

| Constante | Valeur | Où |
|---|---|---|
| durée d'un enregistrement | 600 s | `onyx_record.MAX_SECONDS` |
| fenêtre de collecte après la fin | 120 s | `onyx_record.GRACE_SECONDS` |
| événements par journal | 2000 | `onyx_record.MAX_EVENTS` |
| taille d'un journal | 262144 octets | `onyx_record.MAX_JOURNAL_BYTES` |

Ces bornes sont celles de la spec ; les changer demande de changer le test.

---

## 7. Dépannage

| Symptôme | Cause | Correctif |
|---|---|---|
| `401 {"detail":"Unauthorized"}` | pas de jeton de session | en-tête `X-Hermes-Session-Token` (§3) |
| `404 {"detail":"Plugin not found"}` | absent de `plugins.enabled`, ou désactivé à chaud | ligne §2 ; vérifier aussi `plugins.disabled` |
| `404 {"detail":"No such API endpoint: …"}` | plugin activé mais `manifest.json`/`api` introuvable | vérifier `~/.hermes/plugins/onyx-record/dashboard/manifest.json` |
| `404 {"detail":"Not Found"}` sur une route précise | montage à chaud en course, ou plugin monté à la main derrière le repli | redémarrer le Dashboard une fois (hors périmètre du plugin), puis re-vérifier §3 |
| `409 already_recording` | un bail est ouvert | le client adopte le `session_id` rendu ; sinon `stop` avec `journal: null` |
| `409 journal_pending` | un journal attend d'être converti (120 s) | convertir, puis `start` avec `"discard_pending": true` |
| `410 session_expired` | bail expiré depuis plus de 120 s | le journal reste sur le téléphone : `skills.draft` en ligne fonctionne encore |
| `422 secret_text_present` | un événement `secret: true` porte un `text` | défaut côté app : le texte ne doit pas partir |
| `503 recorder_unavailable` | exception interne inattendue | consulter le log du Dashboard (le plugin ne journalise jamais un contenu) |

---

## 8. Limites de sécurité assumées

1. **Aucun secret dans une réponse ni dans un log.** Un `text` accompagné de
   `secret: true` est refusé (`422`), jamais nettoyé : le serveur ne décide pas
   de jeter en silence un texte qu'on lui a envoyé. Les logs ne portent que
   `session_id`, compteurs et `end_reason`.
2. **Rien sur disque.** Ni journal, ni brouillon, ni état : un brouillon non relu
   n'atterrit nulle part. L'écriture d'un skill reste `POST api/skills`.
3. **Le journal ne contient ni pixel ni audio.** Les champs `image`, `png`,
   `screenshot`, `audio`, `transcript`… sont refusés à n'importe quelle
   profondeur, et les sept `kind` sont fermés.
4. **`bridge.endpoint` est un hôte:port nu** : ni schéma, ni identifiant. Un
   journal livré sous une autre session de pont est refusé (`409 bridge_mismatch`).
5. **Le seul contrôle d'accès est le jeton du Dashboard** — le même niveau de
   confiance que le Dashboard lui-même. Le plugin ne lit aucun secret Hermes et
   n'ouvre aucun profil : il refuse même un `profile` explicite.
6. **Un bail par serveur.** Le profil est celui du Dashboard ; la route ne peut
   pas en désigner un autre (`400 profile_not_supported`), donc « un
   enregistrement déjà en cours pour ce profil » se lit « un bail est ouvert ».
7. **Mémoire seulement** : le journal en collecte (120 s) et le bail vivent dans
   le processus du Dashboard. Un redémarrage les perd — sans conséquence, le
   journal de référence est sur le téléphone.
