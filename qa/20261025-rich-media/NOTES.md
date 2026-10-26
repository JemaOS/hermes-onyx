# Blocs riches: capture Android du 25/10/2026

Un message **semé** dans le fil (fichier `conversations.json` du cache de l'app, mêmes champs que
ceux qu'écrit `ConversationSerializer`), affiché par Onyx sur un émulateur `sdk_gphone64_x86_64`
(`onyx-rows`, port 5590 — `emulator-5558` n'a pas été touché).

- APK installé : variante **debug**, `sha256 ae871482d9e0ed97ce4e34dd3fdcf5f4c3900c37970a37874617b6c16b66f97d`
  (la variante debug seule porte le captureur d'écran `CaptureProvider` ; le code est le même).
- Modèle  : `…/native-inputs/e3079d51c22783c7ee65a0255eee6777f2ac3f7802b96973024b063ff729e36a/img_c5a7abe1f4d1.jpg`.

## Ce que la capture montre

| Élément |  2.0 | Android (ici) |
|:--|:--|:--|
| En-tête du bloc | `DIAGRAM` \| expansion, `Source`, `Copy` | identique (`android-final-thread.png`) |
| Rendu Mermaid | hors ligne, coupé à droite du bloc | hors ligne, coupé à droite du bloc |
| Plein écran | pincement | lecteur au pincement (`android-final-diagram-fullscreen.png`) : diagramme entier |
| Formule LaTeX | une ligne, `cost = p_in (…) + …` | identique, une ligne (`android-final-thread.png`) |
| Menu d'une formule | `Copy LaTeX` | identique (`android-final-math-sheet.png`) |

## Ce qui reste imparfait, et pourquoi

1. **Diagramme large dans le fil** — le bloc dessine le diagramme à sa taille naturelle et n'en
   montre qu'une partie : la hauteur mesurée par la page (48 px CSS) ne suit pas la taille du
   `<svg>` dessiné par mermaid. Le lecteur plein écran, lui, montre le diagramme entier.
   C'est le même comportement *visible* que  (le dessin y est coupé à droite), mais moins
   généreux :  laisse voir le premier nœud et le losange, Android en montre moins.
2. **Taille de la formule** — la formule est ramenée à la largeur du bloc (`fitMath`), donc plus
   petite que , qui la dessine à sa taille naturelle.
3. **`CaptureProvider`** — répond `no window` sur cet émulateur (`WindowManagerGlobal` par
   réflexion) ; les captures viennent de `adb exec-out screencap`, qui fonctionne ici.
