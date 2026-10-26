# Scène vocale d'une salle: ce qui restait, mesuré à l'écran

Le propriétaire, devant une salle de groupe : *« ça ne va pas du tout, ce n'est pas du tout comme
les captures 2 et 3 »*. Sa capture montre **deux visages** dans l'orbe, la légende **« À L'ÉCOUTE »**
et **aucun fil** — alors que le lot précédent (`480ae0e`) annonçait le grand visage, le fil réparé
et le ⏸ au milieu.

Méthode : **reproduire d'abord**, au build courant, en clair **et** en sombre, puis comparer
composant par composant avec les deux images du modèle. Le commit ne prouve rien ; l'écran si.

- Worktree : `/home/john/onyx-group-voice-4` (branche `group-voice-4`), base `9afe954`, le HEAD de
  `onyx-integration2` (330 commits, `1.0.0+330`). `onyx-integration2` n'a pas été touché.
- Spécification : `/home/john/r132/onyx` —  (`groupSpeaker`, `OrbFace`,
  `GroupVoiceFaces`, `phaseTitle`, `phaseColor`, `Halos`), ,
  , .
- Émulateur : `emulator-5610` (AVD `onyx-gv4`, 1080×2400, 420 dpi, français). **`emulator-5558`
  n'a pas été utilisé.** Émulateur éteint à la fin.

## 1. Ce qui a été reproduit, et à quel build

Le harnais de la recette est celui du lot précédent (`qa/group-voice-bigface/`), réutilisé tel quel :
un serveur de rejeu (`serve.py`) tient la salle (routes REST du Dashboard, canal JSON-RPC
`groups.*`, `POST /push` qui fait arriver la réplique du bot suivant). **Rien n'est injecté** : c'est
la scène vocale qui décide du visage et de la légende, en lisant ce que la salle a écrit.

État au build courant, avant correctif :

| Composant | Modèle | Build courant (`9afe954`) | Verdict |
|:--|:--|:--|:--|
| En-tête | visages superposés + nom de la salle | idem (`GroupVoiceHeader`) | conforme |
| Orbe au repos | **les visages de tous les bots** | idem (`GroupVoiceFaces`) | conforme |
| Orbe pendant la lecture | **un seul** grand visage, celui qui parle | idem | conforme |
| Aura | lueur colorée autour du visage | présente mais **éteinte en sombre** (couleur profonde du bot sur du noir) et **délavée en clair** | **défaut** |
| Anneaux | deux anneaux fins, hors du visage | idem (`VoiceHalos`, 0,45 → 0, transparence au bord) | conforme |
| Légende | nom du bot, capitales espacées, **centrée** | nom du bot, capitales espacées… **collée à la marge gauche** (`x[63-1017]`, texte dessiné depuis 63) | **défaut** |
| Fil | bulle de la question + **vignette + nom par réponse** | idem (`GroupVoiceLineRow` → `MineRow` / `RoomMemberRow`) | conforme |
| Phrase d'aide + trois boutons | présents | présents (clavier, ⏸, ■) | conforme |

**Le fil se remplit.** `02-parle.xml` porte `Scout` puis `Quill`, chacun avec sa ligne; `serve.py
--flip 0` tient une salle où **l'identifiant du membre diffère de son profil** (le cas qui cassait la
lecture avant `480ae0e`), et les deux répliques sont bien lues, nommées et dessinées. Le vide de la
capture du propriétaire n'est donc plus là.

## 2. Les deux causes qui restaient

### La légende n'était pas centrée

`VoiceScreen.kt` posait le mot d'état dans un `AnimatedContent` **pleine largeur**
(`.fillMaxWidth()`), avec `contentAlignment = Center` — mais l'alignement du conteneur centre une
**boîte qui occupe toute la largeur**, donc le texte se dessinait depuis son propre bord gauche. Le
dump le mesure : `text="SCOUT PARLE" … bounds="[63,975][1017,1028]"`, le texte commençant à x=63,
c'est-à-dire à la marge, à hauteur du fil. Le modèle le pose **au milieu sous l'orbe**
(`VoiceView` : un `Text` dans un `VStack` centré, `.tracking(1.2)`).

Correctif : `textAlign = TextAlign.Center` dans le style du mot d'état.

### L'aura n'existait vraiment que sur une page

`botFaceColour` rendait toujours **le ton profond** du dégradé du bot. Sur une page sombre, un violet
profond à 45 % ne fait qu'un halo à peine plus clair que le noir ; sur une page claire, ce même ton
se délaie en blanc bleuté — exactement l'« orbe délavé sans aura » de la capture du propriétaire.

Correctif : `botFaceAuraColour` garde **la couleur du visage qui parle** et choisit seulement sa
**teinte** selon la page — le clair du dégradé sur une page sombre, le profond sur une page claire.

## 3. Fichiers

| Fichier | Changement |
|:--|:--|
| `app/src/main/java/com/onyx/app/ui/voice/VoiceScreen.kt` | mot d'état centré (`textAlign`) ; l'aura du grand visage prend `botFaceAuraColour` |
| `core/src/main/java/com/onyx/app/ui/design/BotLook.kt` | `botFaceAuraColour` : la teinte de l'aura suit la page (`theme.dark`), la couleur reste celle du bot |
| `app/src/test/java/com/onyx/app/ui/voice/GroupVoiceScene2Test.kt` | l'épreuve de l'aura réalignée sur le nouveau nom, **deux épreuves de plus** (teinte par page, légende centrée) |
| `qa/group-voice-bigface/seed.py` | `--theme clair\\|sombre` : la recette peut semer l'un ou l'autre |

## 4. Recette

```
JAVA_HOME=/home/john/android-toolchain/jdk-21 ANDROID_HOME=/home/john/android-toolchain/sdk \
/home/john/android-toolchain/gradle/bin/gradle :core:testDebugUnitTest :app:testDebugUnitTest \
  :app:assembleRelease --no-daemon --console=plain \
  -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2
```

`BUILD SUCCESSFUL in 2m 12s`. Compté dans les XML : **1171 épreuves, 0 échec, 0 erreur** —
`app` 642, `core` 529. Le plancher de l'arbre est de **1169** (mesuré sur `9afe954`), plus les deux
épreuves ajoutées. *(Le brief annonçait 1420 : ce chiffre ne correspond pas à cet arbre — il n'y a
que `:app` et `:core` qui portent des épreuves, `:wear` et `:auto` n'en ont aucune. Le plancher
retenu est celui mesuré, il n'a pas baissé.)*

- APK release : `app/build/outputs/apk/release/app-release.apk`, `versionName 1.0.0+331`,
  `versionCode 331` (= nombre de commits).
- `sha256 007c290636ca32473ea9e76c64d28e647da1b1186f270fa19d98a460f7de9214`.
- **Installé et vérifié sur `emulator-5610`** : `adb shell sha256sum $(pm path …)` donne la **même**
  empreinte, `dumpsys package` relit `1.0.0+331`.

## 5. Les captures

| Capture | Ce qu'elle montre |
|:--|:--|
| `01-repos-clair.png`, `01-repos-sombre.png` | la scène à l'ouverture : les visages de tous les bots dans l'orbe, la légende **centrée**, la bulle de la question |
| `02-repos-apres-round-clair.png`, `…-sombre.png` | au repos **après** le round : les trois visages, la légende centrée, et le fil avec **Scout** puis **Quill**, vignette + nom chacun |
| `03-scout-parle-clair.png`, `…-sombre.png` | **pendant la lecture de Scout** : un seul grand visage, son aura, les anneaux, « **SCOUT PARLE** » centré en capitales espacées |
| `04-quill-parle-clair.png`, `…-sombre.png` | **pendant la lecture de Quill** : le bot suivant, « **QUILL PARLE** », le fil qui porte déjà la réplique de Scout |
| `10-comparaison-scout-sombre.png`, `-quill-sombre`, `-scout-clair`, `-quill-clair` | le modèle (l'écran du téléphone cadré) à gauche, la scène à droite, **même largeur** |
| `11-repos-clair-et-sombre.png` | le repos dans les deux thèmes, côte à côte |

Les `*.xml` de `qa/group-voice-bigface/` (dumps d'accessibilité) donnent la légende telle quelle
(`SCOUT PARLE`, `QUILL PARLE`) et les lignes du fil : c'est la preuve que c'est **le bot qui parle**
qui est nommé, et que chaque réponse porte son nom.

## 6. Écarts assumés et restes

- **Les deux visages au repos ne sont pas un défaut.** Le modèle dessine les visages de **tous**
  les bots dans l'orbe tant que personne ne parle ( : *« All its bots' faces while
  none is speaking »*, et `GroupVoiceFaces` sous la condition `replyFace == nil`), et le grand visage
  seul pendant la lecture. Le implémentation fait exactement cela. Le brief demandait de vérifier : c'est
  vérifié, et rien n'a été changé là.
- **Le vert du modèle n'est pas la couleur du bot.** Dans les deux captures, l'aura et la
  légende sont **vertes** alors que les visages sont violet et turquoise : côté modèle, l'aura
  prend `phaseColor`, qui vaut `.green` pendant la lecture. Le brief demande, lui, l'aura **de la
  couleur du locuteur** : c'est ce que fait l'app, et c'est une différence assumée — sur un visage
  violet, une aura verte se lit comme un décalage.
- **Le nom du bot est en couleur d'accent** dans le fil, là où le modèle le pose en gris clair :
  c'est `RoomMemberRow`, la ligne du fil de la salle, partagée avec le chat. La changer ici la
  changerait partout.
- **La bulle de la question** est celle du thème de l'app (bleu vif, pleine largeur) ; le modèle a
  un ruban pâle plus étroit. C'est la bulle du chat, pas celle de la scène.
- **Le bandeau orange « Onyx a besoin du micro »** vient de l'émulateur (ni microphone ni moteur de
  reconnaissance) : il n'est pas dans le modèle, et il n'apparaît pas sur un appareil pourvu d'un
  micro.
- **La salle créée sur le téléphone** n'est toujours prouvée que par les épreuves : sans micro,
  l'émulateur ne peut pas poser la question qui ouvre le round. La scène de la salle du serveur, elle,
  est capturée au chemin normal (salle ouverte, lecture en cours).
- **Au repos après un round**, la liste suit le dernier message : la bulle de la question est coupée
  en haut quand le round dépasse la hauteur disponible. C'est le suivi du bas, pas une casse de mise
  en page — mais cela se voit, et mérite peut-être un `contentPadding` en haut.
