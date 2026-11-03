# Installation — plugin Hermes `onyx-vault`

Coffre de secrets **par profil**, chiffré au repos (Fernet), servi par le Dashboard
Hermes sous `/api/plugins/onyx-vault/`. Aucune UI, aucun redémarrage nécessaire.

Tous les chemins ci-dessous sont ceux de la machine de John (`/home/john`).

---

## 0. Ce qui est installé, et où

| Élément | Emplacement |
|---|---|
| Code du plugin | `~/.hermes/plugins/onyx-vault/dashboard/` |
| Activation | `plugins.enabled` dans `~/.hermes/config.yaml` |
| Base chiffrée | `~/.hermes/vault/onyx-vault.db` (0600, hors du dépôt Hermes) |
| Clé Fernet | `~/.hermes/vault/onyx-vault.key` (0600) |

Le coffre vit sous `~/.hermes/vault/`, **pas** sous `~/.hermes/hermes-agent/` : un
`hermes update` ne peut ni l'écraser ni le perdre.

---

## 1. Copier le plugin

```bash
mkdir -p ~/.hermes/plugins/onyx-vault
cp -r /home/john/Bureau/onyx-vault-plugin/dashboard \
      ~/.hermes/plugins/onyx-vault/dashboard
chmod -R go-w ~/.hermes/plugins/onyx-vault
```

Résultat attendu :

```
~/.hermes/plugins/onyx-vault/dashboard/
├── manifest.json      # déclaration du plugin (name, tab masqué, api)
├── plugin_api.py      # router = APIRouter() + routes montées
├── onyx_vault.py      # cœur : Fernet, SQLite, empreintes
└── dist/index.js      # module vide (aucune UI)
```

`INSTALL.md` et `tests/` n'ont pas besoin d'être copiés — ce sont des fichiers de
travail. Seul le dossier `dashboard/` est lu par Hermes (il cherche
`<plugins>/*/dashboard/manifest.json`).

---

## 2. Générer la clé de chiffrement

```bash
mkdir -p ~/.hermes/vault && chmod 700 ~/.hermes/vault

# 32 octets aléatoires en base64 urlsafe = exactement ce qu'attend Fernet.
# Aucune dépendance : n'importe quel python3 de la machine convient.
python3 -c 'import base64, os; print(base64.urlsafe_b64encode(os.urandom(32)).decode())' \
  > ~/.hermes/vault/onyx-vault.key

chmod 600 ~/.hermes/vault/onyx-vault.key
ls -l ~/.hermes/vault/onyx-vault.key     # doit afficher -rw------- (1, john)
```

Variante si vous préférez passer par `cryptography` (le venv Hermes l'a, le python
du système non) :

```bash
PY=$(ls -d ~/.hermes/installs/*/environments/*/venv/bin/python3 | head -1)
"$PY" -c 'from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())' \
  > ~/.hermes/vault/onyx-vault.key
```

Contrôle : la clé doit faire 44 caractères et finir par `=`.

Variante sans fichier : mettre la même valeur dans `ONYX_VAULT_KEY` (par exemple via
un `EnvironmentFile` systemd en 0600). C'est moins bon : une variable
d'environnement est lisible dans `/proc/<pid>/environ` par le même utilisateur.

> **Sauvegardez la clé ailleurs** (gestionnaire de mots de passe). Sans elle, les
> secrets sont définitivement illisibles : il n'existe aucune clé de secours.
> Ne la commitez jamais, ne la mettez pas dans `~/.hermes/config.yaml`.

---

## 3. Activer le plugin

Ouvrir `~/.hermes/config.yaml` et ajouter **exactement cette ligne** (4 espaces
d'indentation, sous `plugins.enabled`) :

```yaml
    - onyx-vault
```

Bloc complet si `plugins.enabled` n'existe pas encore :

```yaml
plugins:
  enabled:
    - onyx-vault
```

Si `plugins:` existe déjà avec d'autres clés, ajouter seulement la ligne `- onyx-vault`
dans la liste `enabled:` existante, même indentation.

> ⚠️ `hermes plugins enable onyx-vault` **ne fonctionne pas** pour ce plugin :
> la CLI ne connaît que les plugins qui portent un `plugin.yaml` (avec du code
> Python `register(ctx)`). Un plugin « dashboard-only » — comme `kanban` ou
> `hermes-achievements`, livrés avec Hermes — s'active uniquement par cette ligne
> dans `config.yaml`. C'est aussi la garde de sécurité du Dashboard : sans elle,
> le `plugin_api.py` d'un plugin utilisateur n'est jamais importé
> (`_plugin_api_mount_skip_reason`, GHSA-mcfc-hp25-cjv7).

Aucun redémarrage n'est nécessaire : la première requête sur
`/api/plugins/onyx-vault/...` déclenche le montage à chaud
(`_PluginApiHotMount`). Si le Dashboard tourne, il prend la nouvelle config dès la
requête suivante.

---

## 4. Vérifier

```bash
PORT=9119   # port du Dashboard Hermes
TOKEN=$(curl -s "http://127.0.0.1:$PORT/" \
        | sed -n 's/.*__HERMES_SESSION_TOKEN__="\([^"]*\)".*/\1/p' | head -1)
B="http://127.0.0.1:$PORT/api/plugins/onyx-vault"
H="X-Hermes-Session-Token: $TOKEN"

# 1. le coffre est-il prêt ? (aucun secret dans cette réponse)
curl -s "$B/health"; echo

# 2. écrire un secret (la réponse ne contient que l'empreinte)
curl -s -X POST "$B/vault.put" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY","value":"sk-test-1234","used_by":["onyx-android"]}'; echo

# 3. lister (empreinte = 4 derniers caractères, jamais la valeur)
curl -s "$B/vault.list?profile=onyx" -H "$H"; echo

# 4. révoquer
curl -s -X POST "$B/vault.revoke" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY"}'; echo
```

Attendu : `health` → `"ok":true`, `put` → `"fingerprint":"1234"`,
`list` → `{"count":1,...}`, `revoke` → `"revoked":true`.

Sans jeton, toute requête répond `401 {"detail":"Unauthorized"}` : c'est
l'authentification du Dashboard, que le plugin ne contourne pas.

Si le Dashboard est derrière la page de connexion (`dashboard_auth`), le jeton
d'en-tête est remplacé par le cookie de session : utilisez l'onglet Réseau du
navigateur pour rejouer les requêtes, ou testez depuis l'UI.

---

## 5. Désinstaller

```bash
# 1. retirer le plugin
rm -rf ~/.hermes/plugins/onyx-vault

# 2. retirer la ligne "    - onyx-vault" de ~/.hermes/config.yaml
```

Sans l'étape 2, le Dashboard journalise un plugin activé mais introuvable.

Supprimer les données (⚠️ irréversible, les secrets sont perdus) :

```bash
rm -f ~/.hermes/vault/onyx-vault.db ~/.hermes/vault/onyx-vault.key
rmdir ~/.hermes/vault 2>/dev/null || true
```

Pour tout effacer proprement côté SQLite avant de supprimer le fichier :

```bash
sqlite3 ~/.hermes/vault/onyx-vault.db 'VACUUM;'
```

(`DELETE` laisse le chiffré dans des pages libres ; `VACUUM` les réécrit. Le clair
n'est jamais dans le fichier, seulement le chiffré.)

---

## 6. Réglages (variables d'environnement)

| Variable | Défaut | Rôle |
|---|---|---|
| `ONYX_VAULT_DIR` | `$HERMES_HOME/vault` sinon `~/.hermes/vault` | Dossier de la base et de la clé par défaut |
| `ONYX_VAULT_KEY` | — | Clé Fernet en clair (prioritaire sur le fichier) |
| `ONYX_VAULT_KEY_FILE` | — | Chemin d'une clé Fernet (prioritaire sur le défaut) |
| `ONYX_VAULT_MOUNT` | `app` | `router` force le montage sur le routeur du plugin (tests) |

Ces variables doivent être présentes dans l'environnement du **serveur Dashboard**
(`hermes dashboard` / gateway), pas dans le shell du client.

---

## 7. Dépannage

| Symptôme | Cause | Correctif |
|---|---|---|
| `401 {"detail":"Unauthorized"}` | pas de jeton de session | en-tête `X-Hermes-Session-Token` (voir §4) |
| `404 {"detail":"Plugin not found"}` | plugin absent de `plugins.enabled`, ou désactivé à chaud | ajouter la ligne §3, vérifier `plugins.disabled` |
| `404 {"detail":"No such API endpoint: …"}` | plugin activé mais `api`/`manifest.json` introuvable ou chemin refusé | vérifier `~/.hermes/plugins/onyx-vault/dashboard/manifest.json` ; consulter le log du Dashboard (`Plugin …: refusing unsafe api path`, `declares api=… but file not found`) |
| `503 {"code":"vault_unavailable",…}` | clé absente, vide ou invalide | §2 ; la réponse contient la commande exacte à rejouer |
| `health` → `key_permissions_secure:false` | clé lisible par le groupe/tous | `chmod 600 ~/.hermes/vault/onyx-vault.key` |
| `400 profile invalide` | nom de profil hors `[a-z0-9._-]` | utiliser p. ex. `onyx` |
| `503` persistant après création de la clé | coffre déjà instancié sans clé dans un autre process | la clé est relue à chaque nouvelle instance ; sinon redémarrer le Dashboard (hors périmètre de ce plugin) |

---

## 8. Limites de sécurité assumées

1. **Chiffrement au repos seulement.** Le process du serveur détient la clé : qui
   peut lire `~/.hermes/vault/onyx-vault.key` (ou la mémoire du process) peut tout
   déchiffrer. Les droits 0600/0700 sont la seule barrière locale.
2. **L'empreinte est une donnée en clair** : les 4 derniers caractères de chaque
   valeur sont stockés et renvoyés (exigence du contrat). En dessous de 8
   caractères, la valeur est entièrement masquée (`****`) pour que l'empreinte ne
   soit pas le secret lui-même.
3. **`used_by` est déclaratif.** C'est une métadonnée d'intention
   (`["onyx-android"]`), pas une ACL : elle n'empêche aucun appelant de lire les
   métadonnées d'un autre profil. Le seul contrôle d'accès est le jeton du
   Dashboard — donc le même niveau de confiance que le Dashboard lui-même.
4. **Pas de rotation de clé intégrée, pas d'audit des lectures.** Rotation =
   réécrire chaque secret avec `vault.put` après changement de clé (le coffre ne
   migre pas tout seul) ; les lectures ne sont pas journalisées (seuls les
   `put`/`revoke` le sont, sans valeur).
5. **Pas de suppression douce.** `revoke` supprime la ligne ; le chiffré peut
   subsister dans des pages libres SQLite jusqu'à `VACUUM` (jamais le clair).
6. **Le contournement de portée de profil est documenté et borné** : les trois
   routes du coffre sont enregistrées sur l'application Dashboard pour échapper à
   la dépendance `_plugin_route_secret_scope`, qui lit `?profile=` comme un profil
   **Hermes** et répondrait `404 Profile 'onyx' does not exist` (comportement
   mesuré, cf. `tests/test_dashboard_mount.py`). L'authentification du Dashboard,
   la porte de consentement par requête (`_plugin_api_runtime_gate`) et les
   limites de corps restent actives : elles sont montées en middleware.
7. **Mode `plugins.isolation: host`** : le plugin est alors exécuté dans le process
   hôte du profil, où l'application Dashboard n'est pas présente ; les routes
   retombent sur le routeur du plugin et `?profile=` y subit la lecture « profil
   Hermes ». Utiliser le mode in-process (défaut) pour ce plugin.
8. **Taille maximale d'une valeur : 16 Kio**, corps HTTP limité à 64 Kio.
