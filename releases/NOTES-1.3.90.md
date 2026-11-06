# Hermes Onyx 1.3.90

Quatre chemins chauffaient le processeur en même temps : le verre de la liste, la réflexion du
modèle, le volet des conversations et le changement de bot. Cette version les refroidit sans
changer l'interface.

## Une carte de conversation ne floute plus toute la fenêtre

Le volet et le fil vivent côte à côte sur un pliant. Chaque carte de conversation, chaque tuile et
chaque rangée lançait **son propre shader de lentille** (AGSL) ou ré-échantillonnait **toute la
fenêtre** à chaque trame : dix à quinze surfaces en parallèle, à 120 Hz. Le framerate s'effondrait
dès qu'une conversation s'écrivait.

- La lentille est désormais réservée aux surfaces **réellement flottantes** : barre du haut,
  onglets, compositeur, capsule d'en-tête, menu.
- Une vitre de liste garde sa **teinte mesurée**, son arête et sa forme, mais ne floute plus la
  fenêtre : une plaque de verre posée, pas un aplat gris. Le repli opaque reste réservé aux
  surfaces qui n'ont rien à montrer.
- La capture Haze n'est plus posée en double quand la page de la lentille est déjà enregistrée
  (Android 13 et plus).
- Les pouls de repos des visages repassent à 30 Hz, la cadence de la référence.

## La réflexion ne se recopie plus à chaque jeton

Chaque fragment de raisonnement recopiait tout le texte déjà reçu
(`reasoning + event.text`) et publiait l'état à chaque jeton. Une réflexion de plusieurs milliers de
caractères coûtait des allocations en O(n²) et un re-parsing du bloc à chaque image.

- Le raisonnement s'accumule dans un tampon et se publie au plus **une fois par trame** (30 ms),
  comme le texte de la réponse.
- Le bloc replié ne passe plus que ses premières lignes à `BasicText`, et non des milliers de
  caractères à `SelectionContainer` à chaque publication.
- La détection de langue est bornée aux 320 premiers caractères.
- Le palier d'écriture disque du texte en cours passe de 256 à 1 024 caractères.

## Le volet ne relit plus un fil qu'un tour n'a pas changé

`sidebarRows` reconstruisait toutes les lignes à chaque publication du fil : un aperçu passait sept
expressions régulières sur le dernier message de chaque conversation, et l'horloge invalidait `now`
à chaque mise à jour d'un tour en cours.

- `conversationPreview` garde son résultat par (conversation, message, longueur, empreinte) dans un
  cache borné, et ne nettoie que les 512 premiers caractères.
- `now` ne se relit que lorsque le nombre de conversations, de bots ou de salles change.
- Les effets de la racine (migration de profils, marques de lecture, cartes de départ) se
  déclenchent sur ce qui les concerne, et la migration s'exécute hors du fil principal.

## Un changement de bot : un seul socket, hors du fil principal

Changer de bot rouvrait deux WebSockets et un appel REST à chaque relecture du roster, puis
redessinait les vignettes des raccourcis sur le fil principal.

- Un seul `ServeRpc` sert les profils, les salles et l'activité d'un `reload` ; la route REST ne
  reste que comme repli.
- Les raccourcis sont publiés sur `Dispatchers.Default`, et seulement quand la signature du roster
  change.
- La progression d'un tour met à jour la notification par `NotificationManager` au lieu de
  relancer le service, et n'est plus republiée à l'identique.

## Vérifié

- `:core:testDebugUnitTest` + `:app:testDebugUnitTest` : **4 337 tests, 0 échec**, 1 ignoré,
  624 classes.
- `:app:assembleRelease`, `:wear:assembleRelease`, `:auto:assembleRelease` : trois APK signés.
- `apksigner verify` : même certificat que les versions précédentes
  (`f58852a2…`), `versionCode 1222`, `versionName 1.3.90`.

| Paquet | Module | Taille |
|---|---|---|
| `onyx-1.3.90-release.apk` | app | 12,1 Mo |
| `onyx-auto-1.3.90-release.apk` | auto | 12,3 Mo |
| `onyx-wear-1.3.90-release.apk` | wear | 34,3 Mo |

**Non vérifié** : aucune mesure de framerate n'a été refaite sur le Honor Magic V3 dans cette
version. Les gains sont prouvés par la structure (nombre de shaders, d'échantillonnages et de
publications par trame) et par les verrous de test, pas par un chronomètre.
