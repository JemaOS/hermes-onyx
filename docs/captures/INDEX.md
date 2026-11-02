# Captures d'écran — app Onyx (Android)

Captures réelles de l'APK **release signé** `onyx-1113-release.apk` (`versionName 1.0.0+1113`,
`versionCode 1113`, package `com.onyx.app`), prises sur un émulateur Android 16 (AVD `onyxclair`,
Pixel 7, 1080×2400, densité 420) avec rendu GPU de l'hôte
(`GLES: … NVIDIA GeForce RTX 4070 SUPER … OpenGL ES 3.1`).

Chaque écran existe en **clair** et en **sombre**, obtenus par `adb shell cmd uimode night no|yes`
sur le même appareil, dans la même session, sans redémarrage de l'app.

| # | Écran montré | Clair | Sombre |
|---|---|---|---|
| 01 | Salle de chat : question envoyée + réponse de l'agent, barre d'onglets Chat/Feed/Files/Onyx | `01-salle-chat-clair.png` | `01-salle-chat-sombre.png` |
| 02 | Onglet Flux de la même conversation (carte de réponse, pouces, Discuter) | `02-salle-flux-clair.png` | `02-salle-flux-sombre.png` |
| 03 | Feuille « Add to message » du compositeur (bouton **+**) : Photo Library, Camera, Files, Paste | `03-composer-piece-jointe-clair.png` | `03-composer-piece-jointe-sombre.png` |
| 04 | Liste des conversations (volet déroulant) : recherche, onglets Chats/Bots, raccourcis, filtres | `04-liste-conversations-clair.png` | `04-liste-conversations-sombre.png` |
| 05 | Réglages › Notifications (canaux Android : Approvals, Silent approvals, Answers, Calls, Reachable) | `05-reglages-notifications-clair.png` | `05-reglages-notifications-sombre.png` |
| 06 | Réglages › Confidentialité › Verrou (verrou biométrique + code de secours) | `06-reglages-verrouillage-clair.png` | `06-reglages-verrouillage-sombre.png` |
| 07 | Écran de verrouillage de l'app (« Onyx is locked ») | `07-verrouillage-clair.png` | `07-verrouillage-sombre.png` |

Toutes les images sont des PNG 1080×2400, captures brutes `adb exec-out screencap -p` (barre d'état
comprise, aucune retouche, aucun recadrage).

## Contexte des captures

- Le gateway Hermes de test écoute sur `127.0.0.1:8642` ; l'app y accède par `10.0.2.2:8642`
  (l'adresse affichée dans la liste des conversations est donc celle de l'émulateur, pas une
  adresse réelle). Connexion vérifiée par le bouton **Test** de l'assistant : « Connected · 1 model ».
- La conversation visible vient d'un vrai aller-retour avec le gateway (prompt « Bonjour,
  presente-toi en deux phrases. »), pas d'une donnée de démonstration injectée.

## Écarts connus

- L'écran de verrouillage (07) affiche ses deux avertissements réels — aucune biométrie n'est
  enrôlée sur cet émulateur et aucun code de secours n'est défini — donc pas de bouton de
  déverrouillage. C'est l'état que produit un appareil neuf ; ce n'est pas une erreur de capture.
- Le nom `03-composer-piece-jointe-*` remplace `03-composer-onglets-*` : le bouton **+** du
  compositeur ouvre la feuille de pièces jointes, il n'y a pas de barre d'onglets dans le compositeur.
