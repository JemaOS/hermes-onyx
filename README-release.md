# Onyx 1.3.30 — copie de la release

Ce dossier est une **copie de travail** de la release publiée :

- `release/` — les trois paquets et leurs sommes, téléchargés depuis
  https://github.com/JemaOS/hermes-onyx/releases/tag/v1.3.30
- le reste — le dépôt `JemaOS/hermes-onyx`, cloné **sur le tag `v1.3.30`** (commit `97e5d55`),
  donc exactement l'état du code qui a produit ces paquets.

## Les trois paquets

| Paquet | Appareil |
|---|---|
| `onyx-1.3.30-release.apk` | téléphone (`com.onyx.app`) |
| `onyx-auto-1.3.30-release.apk` | Android Auto (`com.onyx.app.auto`) |
| `onyx-wear-1.3.30-release.apk` | Wear OS (`com.onyx.app.wear`) |

Tous en version `1.3.30`, code `1162`, signés par la même clé (`f58852a2…`) — ils s'installent
par-dessus une version précédente sans désinstallation.

Contrôle d'intégrité : `cd release && sha256sum -c SHA256SUMS-1.3.30.txt`.

## Rappel sur le clone

Un clone `--depth 1` sur un tag. Pour reprendre le travail avec l'historique complet :

```bash
git fetch --unshallow
git checkout main
```

Tests de référence de cette version : **4104**, zéro échec.
