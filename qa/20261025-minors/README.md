# Deux captures de la liste `@`, et les écarts mineurs du dernier rapport

Recette faite sur `emulator-5592` (AVD `onyx-274`, Android 36.1, 1080×2400, densité 420), app en
français, contre le faux serveur `mock_hermes.py` (port 8790, `10.0.2.2`). APK release **275**
(`1.0.0+275`, sha256 `a66e399997c3174783481c31798a88d365e58225bbe39849d463d17b13d360b0`), installé
`-r -d` par-dessus pour garder la configuration. Captures par `uiautomator dump` + `screencap`, en
pilotant l'app **par ancres d'accessibilité** (jamais de coordonnées écrites à la main).

## 1. La liste `@` du composeur (les deux captures à refaire)

Le lot des mentions avait laissé `01-liste-ouverte.png` et `02-filtre-sc.png` **écrasées** par une
passe tardive : les fichiers montraient « Ask @ » / « Ask @sc » **sans** la liste, et le brouillon
finissait par un blanc parasite. Elles sont refaites ici, brouillon **vidé d'abord**, frappes
`input text` sur le champ **au repos** (le champ descend à `1375-1501` quand le clavier est ouvert :
taper ailleurs tombait sur le clavier et ajoutait des espaces).

| fichier | ce qu'on y voit |
|---|---|
| `01-liste-ouverte.png` | fil du bot **Onyx**, brouillon `@` (aucun blanc), liste `Bots à mentionner` ouverte : `@quill`, `@fix-it`, `@garage-project`, `@scout`, `@weekend-hike` ; clavier ouvert. |
| `02-filtre-sc.png` | brouillon `@sc`, la liste **filtrée** à `@scout` seul. |
| `03-mention-inseree.png` | tap sur la rangée `@scout` : brouillon `@scout ` (un seul blanc, à la fin). |

Les arbres `01-liste-ouverte.xml` / `02-filtre-sc.xml` / `03-mention-inseree.xml` portent la preuve
textuelle : `content-desc="Bots à mentionner"`, les libellés `@…`, et le `text` du nœud `EditText`.

## 2. Les cinq écarts mineurs — verdict, un par un

Chacun a été **vérifié contre le modèle** (`r132/onyx`, ) avant de toucher au code.

### 2.1 Grille d'icônes dépliée → **levé**

 : `@State private var expanded = false` (seul le hook de développement
`-echo.expandAppIcons` ouvre la grille d'emblée). Le port était à `mutableStateOf(true)`, avec un
commentaire qui lisait la capture « store » comme le défaut.

| avant | après |
|---|---|
| `11-avant-apparence.png` — la grille des 14 icônes est **peinte sous la rangée**, chevron vers le haut | `11-apres-apparence.png` — la rangée seule : « Icône de l'app · Visage Onyx · aperçu · ⌄ » |

### 2.2 « Require a passcode » resté en anglais → **conforme (déjà levé)**

`LockSettingsScreen` construit `"Require ${biometryLabel}"` ; les trois formes composées sont
traduites (`Require Fingerprint`, `Require Face unlock`, `Require Face or fingerprint`,
`Require a passcode`) et la capture le montre : la ligne dit **« Exiger le visage ou l'empreinte »**,
la section « VERROUILLAGE » et le pied de page aussi. Rien à changer — l'écart du rapport venait
d'un état antérieur au lot `onyx-lock-fix` (déjà ancêtre de HEAD).

### 2.3 L'avertissement du Terminal → **conforme (inventé par le rapport)**

`TerminalAccountForm` affiche, comme le modèle,
`Label("To connect, fill in \(missing.words).", systemImage: "exclamationmark.circle")` en orange,
sous les trois champs, avec les **mêmes mots** (`TerminalMissing.words`) et le même glyphe
(`Symbols.ExclamationCircle`, `theme.warning`). Le texte vu (« Pour vous connecter, renseignez le
nom d'hôte du serveur. ») est exact pour un hôte vide : le `10.0.2.2` affiché est le **placeholder**
gris de l'adresse suggérée, pas une valeur saisie.

### 2.4 Bouton rafraîchir du Kanban → **levé**

 : `if available { Image(systemName: live.isLive ? "dot.radiowaves.left.and.right"
: "arrow.clockwise") }` — la barre porte donc **toujours** un symbole de liaison quand le Dashboard
répond : flèche secondaire tant que le direct n'écoute pas, pastille verte quand il écoute, rien sans
Dashboard. Le port avait `null` hors direct (son commentaire affirmait que le modèle ne posait
rien — c'était faux).

| avant | après |
|---|---|
| `20-avant-kanban.png` — « ‹ Retour · Kanban · Nouvelle carte », **aucun** symbole | `20-apres-kanban.png` — la flèche ↻ secondaire à gauche de « Nouvelle carte » |

### 2.5 Pastille de visage → **levé**

 ne pose le visage dans la barre que pour le fil **d'un bot** :
`if let botFace { HStack { BotAvatar(face:name:size: 22); Text(title) } }`, et `botFace` vient de
`conversation.bot.map { … }`. Une conversation simple n'en porte aucun (cf. le modèle
« Nouvelle conversation » : la barre ne montre que le titre et « Onyx · hermes-kilo ⌄ »). Le port
passait `avatarName = threadSpeaker.faceName` sans condition, et `threadHeaderSpeaker(bot = null)`
retombait sur `faceName = OnyxProfile` — un visage de repli.

| avant | après |
|---|---|
| `50-conv-simple-avant.png` — petite pastille de visage bleue entre le bouton de gauche et « Nouvelle conversation » | `50-conv-simple-apres.png` — la barre ne porte plus que le titre |

Le fil d'un bot garde son visage (c'est le modèle) : `40-fil-onyx.png` montre le visage d'Onyx
à gauche du titre, dans la barre.

## Comment rejouer

`drive` = `uiautomator dump` + `input tap/text`, piloté par `adbm.py` (hors dépôt). Les deux
captures `@` s'obtiennent en : liste → rangée `Onyx` → tap sur le composeur → `input text "@"` →
`input text "sc"`. Ne **pas** taper ailleurs une fois le clavier ouvert.
