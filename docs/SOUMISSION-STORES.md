# Publier Onyx sur les dépôts libres — sans compte développeur payant

Ce fichier est la marche à suivre pour les dépôts qui n'exigent **ni frais, ni compte payant** :
IzzyOnDroid, F-Droid, et la page des versions GitHub (déjà en place). Il ne reste que deux gestes à
faire à la main, parce qu'ils demandent un compte GitLab (gratuit) que seul le propriétaire peut ouvrir.

## Ce qui est déjà dans le dépôt

| Fichier | À quoi il sert |
|---|---|
| `fastlane/metadata/android/en-US/` et `fr-FR/` | titre, résumé, description longue, notes de version (`changelogs/1229.txt`), six captures et l'image de tête. C'est le format lu **à la fois** par F-Droid et par IzzyOnDroid, depuis le dépôt de l'application. |
| `fastlane/metadata/android/en-US/images/featureGraphic.png` | 1024 × 500, l'image de tête des fiches. |
| `fastlane/fdroiddata-draft/com.onyx.app.yml` | la fiche `fdroiddata` prête à copier, avec le `versionCode` lu dans `gradle.properties`. |
| `README.md` → *Install* | le bouton Obtainium, qui installe et met à jour l'application depuis les versions GitHub. |

Rien de tout cela ne change l'application : ce sont des fichiers de texte et des images.

## Étape 1 — IzzyOnDroid (le plus rapide, quelques jours)

IzzyOnDroid reprend **tes** APK signés, tels quels : la mise à jour se fait par-dessus l'installation
existante, sans désinstallation. C'est le dépôt à viser en premier.

1. Se connecter à GitLab (compte gratuit) : https://gitlab.com/users/sign_up
2. Ouvrir une demande d'ajout : https://gitlab.com/IzzyOnDroid/repo/-/issues/new
3. Titre : `Inclusion request: Hermes Onyx (com.onyx.app)`
4. Corps du message (en anglais) :

```
App name: Hermes Onyx
App id: com.onyx.app
Repository: https://github.com/JemaOS/hermes-onyx
License: MIT
Category: Internet
Latest release: https://github.com/JemaOS/hermes-onyx/releases/latest
APK to use: onyx-app-<version>-release.apk (signed with the project's release key,
SHA-256 of the certificate: 1a1f8225f509167b4e01d4606b5ffa6eb17c07868d9b8190efc9f47c459a4402)

Fastlane metadata is already in the repository (fastlane/metadata/android/en-US and fr-FR:
title, short and full description, changelogs, six phone screenshots, feature graphic).

What it is: the Android client for a self-hosted Hermes agent. No account, no telemetry,
no proprietary dependencies — no Firebase, no Play Services. It talks to one host only:
the Hermes Dashboard the user runs themselves. Chat with streaming, bots and rooms,
hands-free voice, and a live view of the user's computer.

Anti-features: none that I know of. The app requires a self-hosted server to be useful,
which I mention in the description.
```

Izzy vérifie ensuite l'APK, la signature et les métadonnées, puis ajoute l'application à son dépôt.
Le badge à mettre ensuite dans le README :

```markdown
[![IzzyOnDroid](https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png)](https://apt.izzysoft.de/fdroid/index/apk/com.onyx.app)
```

## Étape 2 — F-Droid officiel (plus lent, plusieurs semaines à quelques mois)

F-Droid **reconstruit** l'application depuis les sources, puis la signe avec **sa** clé. Deux
conséquences à connaître :

- l'APK F-Droid ne peut pas remplacer un APK GitHub par une mise à jour : signature différente,
  il faut désinstaller puis réinstaller (l'utilisateur doit choisir **une** source et s'y tenir) ;
- ton `applicationId` et ton `versionCode` doivent rester stables, ce qui est le cas.

La demande se fait sur le dépôt des requêtes :

1. https://gitlab.com/fdroid/rfp/-/issues/new
2. Titre : `Hermes Onyx (com.onyx.app) — self-hosted agent client`
3. Corps :

```
App: Hermes Onyx
Package: com.onyx.app
Source: https://github.com/JemaOS/hermes-onyx
License: MIT
Category: Internet
Build: gradle, `assembleRelease` from the repository root (`subdir: .`), no NDK, no submodules.
Min SDK 26, target SDK 36. No Firebase, no Play Services, no proprietary blobs.
Release notes: https://github.com/JemaOS/hermes-onyx/releases
```

Le mainteneur ouvre ensuite une merge request sur `fdroiddata` : `fastlane/fdroiddata-draft/com.onyx.app.yml`
est déjà la fiche, à copier sous `metadata/com.onyx.app.yml` dans ce dépôt-là. Elle déclare
`UpdateCheckData` pour lire `onyx.versionCode` dans `gradle.properties` — c'est là que vit le numéro
de version dans ce projet, pas dans `build.gradle.kts`.

## Étape 3 — les dépôts qui demandent un compte, mais pas d'argent

À faire seulement quand tu voudras élargir ; tous sont gratuits à l'inscription.

| Dépôt | Ce qu'il faut | Ce qu'il apporte |
|---|---|---|
| Amazon Appstore | compte développeur gratuit, aucun frais annuel | tablettes Fire, un public qui n'installe pas d'APK à la main |
| Samsung Galaxy Store | compte gratuit, APK accepté | appareils Samsung, très installés en Europe |
| Huawei AppGallery | compte gratuit, signature et SDK maison | marché chinois et appareils Huawei |
| Aptoide / APKPure | envoi direct, sans frais | visibilité, revue faible |

Google Play reste le seul passage payant (25 $ une fois), et il n'est pas nécessaire pour exister.

## À chaque version

Les deux dépôts lisent la fiche **depuis ce dépôt**, pas depuis une console web. Une version nouvelle
demande donc un fichier de plus, et rien d'autre :

1. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — et le jumeau `fr-FR`, **500
   caractères au plus** chacun (au-delà, F-Droid tronque) ;
2. les captures, seulement si l'interface a changé de façon visible ;
3. une fois F-Droid accepté, `CurrentVersion` et `CurrentVersionCode` se mettent à jour tout seuls
   (`UpdateCheckMode: Tags`), rien à pousser ailleurs.

IzzyOnDroid de son côté suit la page des versions : un APK publié par `release.sh` est repris à la
prochaine passe de leur robot.

## À vérifier avant chaque envoi

```bash
bash scripts/check-forbidden-words.sh .
```

Le garde-fou du dépôt refuse toute marque de plateforme dans le contenu **et** dans les noms de
fichiers : les nouveaux fichiers de métadonnées y sont soumis comme les autres.
