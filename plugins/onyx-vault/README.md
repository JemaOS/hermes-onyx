# onyx-vault — coffre de secrets par profil pour Onyx

Plugin Hermes « dashboard-only » : il ajoute des routes backend sous
`/api/plugins/onyx-vault/` et rien d'autre. Les secrets sont chiffrés au repos
(Fernet) dans SQLite, hors du dépôt Hermes, un jeu par profil (`onyx`, `staging`,
…). Aucune valeur n'est jamais renvoyée par HTTP, écrite en clair sur disque,
journalisée ou recopiée dans une exception.

Installation, activation, désinstallation : voir **[INSTALL.md](INSTALL.md)**.

## API

Toutes les routes exigent le jeton de session du Dashboard
(`X-Hermes-Session-Token`), comme n'importe quelle route `/api/`.

| Méthode | Chemin | Entrée | Sortie |
|---|---|---|---|
| `GET` | `/api/plugins/onyx-vault/vault.list` | `?profile=<nom>` | `{profile, count, secrets:[{name, fingerprint, updated_at, used_by}]}` |
| `POST` | `/api/plugins/onyx-vault/vault.put` | `{profile, name, value, used_by?}` | `{profile, name, fingerprint, updated_at, used_by, created}` |
| `POST` | `/api/plugins/onyx-vault/vault.revoke` | `{profile, name}` | `{profile, name, revoked}` |
| `GET` | `/api/plugins/onyx-vault/health` | — | état du coffre (jamais de secret) |
| `GET` | `/api/plugins/onyx-vault/info` | — | version, routes, cible de montage |

```bash
B=http://127.0.0.1:9119/api/plugins/onyx-vault
H="X-Hermes-Session-Token: $TOKEN"

curl -s -X POST "$B/vault.put" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY","value":"sk-…","used_by":["onyx-android"]}'
# -> {"profile":"onyx","name":"OPENAI_API_KEY","fingerprint":"…1234","updated_at":"…","used_by":["onyx-android"],"created":true}

curl -s "$B/vault.list?profile=onyx" -H "$H"
# -> {"profile":"onyx","count":1,"secrets":[{"name":"OPENAI_API_KEY","fingerprint":"…1234",…}]}

curl -s -X POST "$B/vault.revoke" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY"}'
# -> {"profile":"onyx","name":"OPENAI_API_KEY","revoked":true}
```

Codes : `200` ok · `400` entrée invalide · `401` pas de jeton · `404` plugin non
activé · `413` corps trop gros · `503` clé absente ou invalide (réponse avec la
commande de remédiation).

`fingerprint` = les 4 derniers caractères de la valeur, `****` si la valeur fait
moins de 8 caractères.

## Format sur disque

`~/.hermes/vault/onyx-vault.db` (0600) — table `secrets` :

| colonne | contenu |
|---|---|
| `profile`, `name` | identifiants, en clair |
| `ciphertext` | jeton Fernet d'une enveloppe `{v, p, n, s}` : la valeur **et** son `(profil, nom)` |
| `fingerprint` | 4 derniers caractères |
| `used_by` | liste JSON déclarative |
| `created_at`, `updated_at` | ISO 8601 UTC |

Lier le chiffré à son `(profil, nom)` détecte le déplacement d'une ligne à l'autre.
Une clé différente produit `400 "déchiffrement impossible"`, jamais une trace de la
valeur.

## Structure

```
onyx-vault-plugin/
├── INSTALL.md
├── README.md
├── dashboard/            # <- seul dossier à copier dans ~/.hermes/plugins/onyx-vault/
│   ├── manifest.json     # name, tab masqué, api: plugin_api.py
│   ├── plugin_api.py     # router = APIRouter() + montage des routes à ?profile=
│   ├── onyx_vault.py     # cœur testable seul : Fernet + SQLite + empreintes
│   └── dist/index.js     # module vide (aucune UI)
└── tests/
    ├── test_vault_core.py       # 17 tests : non-fuite, chiffrement, validation
    ├── test_plugin_api.py       # 12 tests : contrat HTTP (FastAPI nue)
    └── test_dashboard_mount.py  #  6 tests : vrai Dashboard, 401/montage/put/list/revoke
```

## Tests

```bash
cd /home/john/Bureau/onyx-vault-plugin
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)

# cœur + contrat HTTP (aucun besoin de Hermes)
"$PY" -m unittest tests.test_vault_core tests.test_plugin_api -v

# intégration au vrai Dashboard, dans un HERMES_HOME jetable (ne touche pas au serveur en cours)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" \
  /home/john/Bureau/onyx-vault-plugin/tests/test_dashboard_mount.py
```

## Deux décisions à connaître

**1. `?profile=` est un nom de profil de coffre, pas un profil Hermes.**
Le Dashboard monte chaque plugin avec une dépendance dont le paramètre `profile`
est lu dans la query string et désigne un profil **Hermes** ; avec
`?profile=onyx` elle répond `404 Profile 'onyx' does not exist` avant d'atteindre
le plugin. Les trois routes du coffre sont donc enregistrées sur l'application
Dashboard (ce qui n'écarte que cette dépendance de portée : authentification,
consentement par requête et limites de corps restent des middlewares actifs). Les
routes sans `?profile=` (`health`, `info`) passent par l'`APIRouter` standard.
Détail et mesures : `tests/test_dashboard_mount.py`, docstring de `plugin_api.py`,
§8 d'`INSTALL.md`.

**2. La clé absente ne casse rien.** `plugin_api.py` s'importe toujours : sans clé
il journalise un avertissement explicite et répond `503` avec la commande de
génération. Dès que la clé apparaît, la requête suivante fonctionne — pas de
redémarrage, pas de crash du Dashboard.
