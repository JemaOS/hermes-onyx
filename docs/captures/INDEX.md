# Captures d'écran — app Onyx (Android)

Captures réelles de l'APK **release signé** `onyx-1115-release.apk`
(`versionName 1.0.0+1115`, `versionCode 1115`, package `com.onyx.app`), prises sur un
émulateur Android 16 (AVD `onyxclair`, Pixel 7, 1080×2400, densité 420, x86_64, image
Google APIs) avec rendu GPU de l'hôte
(`GLES: … NVIDIA GeForce RTX 4070 SUPER … OpenGL ES 3.1`).

Chaque écran existe en **clair** et en **sombre**, obtenus par `adb shell cmd uimode night
no|yes` sur le même appareil, dans la même session, sans redémarrage de l'app.

**Langue des captures : anglais** (voir « Langue » plus bas). **Aucun prénom n'est
lisible** dans les quatorze images (voir « Confidentialité »).

| # | Écran réellement affiché | Clair | Sombre |
|---|---|---|---|
| 01 | Salle de chat : question envoyée + réponse de l'agent, barre d'onglets Chat/Feed/Files/Onyx | `01-salle-chat-clair.png` (2 623 Ko) | `01-salle-chat-sombre.png` (2 563 Ko) |
| 02 | Onglet Feed de la même conversation (carte de réponse, pouces, Discuss) | `02-salle-flux-clair.png` (2 261 Ko) | `02-salle-flux-sombre.png` (2 213 Ko) |
| 03 | Feuille « Add to message » du compositeur (bouton **+**) : Photo Library, Camera, Files, Paste | `03-composer-piece-jointe-clair.png` (1 558 Ko) | `03-composer-piece-jointe-sombre.png` (1 481 Ko) |
| 04 | Liste des conversations (volet déroulant) : recherche, onglets Chats/Bots, raccourcis, filtres, 1 conversation | `04-liste-conversations-clair.png` (298 Ko) | `04-liste-conversations-sombre.png` (460 Ko) |
| 05 | Réglages › Notifications (canaux Android : Approvals, Silent approvals, Answers, Calls, Reachable) | `05-reglages-notifications-clair.png` (176 Ko) | `05-reglages-notifications-sombre.png` (179 Ko) |
| 06 | Réglages › Confidentialité › Lock (verrouillage activé, code de secours) | `06-reglages-verrouillage-clair.png` (179 Ko) | `06-reglages-verrouillage-sombre.png` (183 Ko) |
| 07 | Écran de verrouillage de l'app (« Onyx is locked ») | `07-verrouillage-clair.png` (848 Ko) | `07-verrouillage-sombre.png` (710 Ko) |

Toutes les images sont des PNG 1080×2400 (barre d'état comprise, aucun recadrage).

## Langue

**L'app sait-elle s'afficher en anglais ? Oui.** L'interface d'Onyx est en anglais sur les
quatorze captures.

Onyx embarque son propre réglage de langue, indépendant de celle d'Android :
**Réglages › Appearance › Language**, avec trois choix — *System*, *Français*, *English*.
Le choix vit dans l'app (`app_language`, dans le DataStore `onyx_settings.preferences_pb`)
et **ne touche jamais la langue d'Android** : le téléphone peut rester dans sa langue, les
écrans, les feuilles, les menus et les notifications d'Onyx passent en anglais d'un coup.

Pour cette série, le réglage a été vérifié sur l'écran : **Language → English**. (L'appareil
était de surcroît en `en-US`, donc les deux chemins mènent à l'anglais.) Le réglage *Voice*
(voix de synthèse) reste sur `fr-FR` : c'est une option distincte, elle ne traduit aucun
texte d'interface et n'apparaît sur aucun des écrans capturés.

## Contexte des captures

- Le gateway Hermes de test écoute sur `127.0.0.1:8642` ; l'app y accède par `10.0.2.2:8642`
  (l'adresse affichée dans la liste des conversations est donc celle de l'émulateur, pas une
  adresse réelle). Le bouton **Check** de l'écran *Settings* répond **« 1 model »** (point
  vert), et le modèle affiché est `hermes-agent`, routé par `api_server.model_routes` vers
  `deepseek/deepseek-v4.1-flash-1017` (fournisseur `kilocode`).
- La conversation visible est un **vrai aller-retour** avec le gateway, pas une donnée
  injectée : question « Which models are you on? », réponse de l'agent
  (« deepseek/deepseek-v4.1-flash-1017, served through kilocode — that's the api_server's
  model_routes mapping for the "hermes-agent" model… »), 5,1 s, 29,5 k tokens ↓.
- La liste des conversations ne montre qu'une entrée (« Which models are you on? ») : les
  discussions de démonstration qui avaient servi à la série précédente ont été retirées.

## Confidentialité

- **Aucune image ne contient le prénom « John » lisible.** Vérifié deux fois : passage OCR
  (Tesseract) sur les quatorze PNG — zéro occurrence — et relecture visuelle de chaque image.
- La conversation de démonstration est volontairement neutre et sans nom propre ; l'agent ne
  s'adresse pas à la personne par son prénom dans la réponse retenue.
- **Aucune zone floutée** : il n'y a rien à flouter, la mise en sourdine n'a pas été
  nécessaire.
- La barre d'état ne contient que l'heure, la réseau et la batterie — aucun nom de personne.

## Écarts connus

- Les deux fichiers `06-reglages-verrouillage-*.png` sont les captures brutes
  (`adb exec-out screencap -p`), **ré-encodées sans perte** (pixels strictement identiques,
  vérifié par empreinte SHA-256 du tampon RVBA) : l'écran est presque uniformément clair,
  la compression PNG par défaut le ramenait à ~105 Ko, sous le seuil de 150 Ko attendu. Le
  ré-encodage utilise simplement un niveau de compression plus faible (deflate `Z_FIXED`),
  sans toucher à un seul pixel. Tous les autres fichiers sont les captures brutes telles
  quelles.
- L'écran de verrouillage (07) affiche ses deux avertissements réels — aucune biométrie n'est
  enrôlée sur cet émulateur — et propose donc le **code de secours** à la place du bouton
  visage/empreinte. C'est l'état que produit un appareil neuf sans biométrie enrollée ; ce
  n'est pas une erreur de capture. Le code lui-même n'apparaît nulle part à l'écran.
- Sur l'écran 06, le verrouillage est **activé** et un code de secours est défini ; la série
  précédente montrait un verrouillage désactivé et aucun code. L'état actuel est celui du
  poste de capture, pas une mise en scène.
- L'autorisation Android `POST_NOTIFICATIONS` a été accordée sur l'émulateur pour que
  l'écran 05 affiche « Notification settings — **On** », comme sur la série précédente ; sans
  elle, Android bloque les notifications d'Onyx et l'écran affiche « Off ».

## Relancer l'émulateur

```bash
export JAVA_HOME=/home/john/android-toolchain/jdk-21
export ANDROID_HOME=/home/john/android-toolchain/sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

$ANDROID_HOME/emulator/emulator -avd onyxclair -no-snapshot -no-audio -no-boot-anim -gpu host &

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb install -r /home/john/.hermes/profiles/kilo/cache/scratch/onyx-1115-release.apk

adb shell cmd uimode night no   # ou : night yes
adb exec-out screencap -p > docs/captures/<nom>-clair.png
```

Le gateway doit tourner sur `127.0.0.1:8642` : l'app l'atteint par `10.0.2.2:8642`
(réglage *Settings › Connection*, bouton **Check** → « 1 model »).
