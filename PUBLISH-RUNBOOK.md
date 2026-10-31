# Runbook de publication — Hermes Onyx

Tout est préparé pour que la publication ne demande que l'APK final. Ce fichier est le mode
d'emploi ; il peut être retiré avant le commit s'il ne doit pas être public.

## Ce qui est déjà en place (dans ce clone)

| Fichier | Rôle |
|---|---|
| `release.sh` | publie : signature, empreintes, `SHA256SUMS-<version>`, release GitHub **Latest**, re-téléchargement et re-vérification. |
| `scripts/check-forbidden-words.sh` | garde-fou : échoue si une marque de plateforme ou le nom de l'autre application **apparaît en nouveau**. |
| `releases/NOTES-__VERSION__.md` | notes de version (français), rendues en `releases/NOTES-<version>.md` par `release.sh`. |
| `README.md` | README à jour ; les jetons `__VERSION__`, `__CODE__`, `__SIDECAR__`, `__TESTS__` sont remplacés par `release.sh`. |
| `docs/screenshots/README.md` | conventions de nommage + liste des captures attendues (sombre et clair). |

## Commande exacte au moment de publier

```bash
cd ~/onyx-publish-finale

# 1. (optionnel mais recommandé) rafraîchir la galerie sur le build livré — lots de captures

# 2. vérifier le garde-fou avant tout
bash scripts/check-forbidden-words.sh .

# 3. repérer les trois APK livrés
find /home/john -maxdepth 6 -name 'Hermes-Onyx-<VERSION>*'

# 4. publier (remplacer les chemins)
#    `gh` n'est PAS authentifie sur cette machine : ne lance pas `gh auth login`.
#    Le jeton du proprietaire est dans `~/.git-credentials` (compte johnkryptochain) : il sert le
#    `git push` seul, et se passe en en-tete a l'API GitHub (`Authorization: token …`) pour creer
#    la release et envoyer les actifs. `release.sh` suit ce chemin.
./release.sh \
  --phone /chemin/Hermes-Onyx-<VERSION>-telephone.apk \
  --auto  /chemin/Hermes-Onyx-<VERSION>-auto.apk \
  --wear  /chemin/Hermes-Onyx-<VERSION>-wear.apk \
  --tests <nombre-de-tests> --sidecar <version-des-compagnons>

# 5. committer les textes rendus
git add README.md releases docs/screenshots scripts release.sh
git commit -m "Release <VERSION> : README, notes et script de publication"
git push
```

`release.sh --dry-run` fait tout **sauf** `gh release create` et le re-téléchargement, et n'écrit
ni le README ni les notes : c'est le mode à utiliser pour vérifier avant de publier.

## Ce que `release.sh` refuse de faire

- publier un paquet **non signé** ou signé par un **certificat différent** des autres ;
- publier si `scripts/check-forbidden-words.sh` trouve une occurrence **nouvelle** ;
- déclarer une réussite sans avoir **re-téléchargé** chaque paquet et comparé son empreinte.

## Après la publication

- Vérifier sur la page de la release que les noms d'assets portent bien le `+` (le script relit les
  noms d'assets via `gh api`).
- L'ancienne version **reste en place** ; la nouvelle release est marquée **Latest**.
