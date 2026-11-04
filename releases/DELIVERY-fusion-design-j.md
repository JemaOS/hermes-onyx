# LIVRAISON — volet design-j (fusion des deux vagues du nouveau design)

**Base** : `7af82776` (« LOT voix-jeton », `versionCode` 656) — la même base pour les deux vagues.
**Branche de fusion** : `fusion/design-j`, arbre `/home/john/onyx-fusion-j`.

| vague | branche | tête fusionnée |
|---|---|---|
| 1 — socle, fil, volet Discussions, volet Bots | `lot/socle-v2-j` | `525e55f2` |
| 2 — vignettes + Fichiers, en-têtes Réglages/Capacités/Modèle | `vague2/design-jeton` | `7c0abd54` |

Fusion `--no-ff`, une vague par vague, **union** (jamais `--ours`/`--theirs` en bloc).

- commit de fusion 1 : `8fa74e4f`
- commit de fusion 2 (tête) : **`005c0cc9`**

## Les trois fichiers homonymes

| fichier | socle (vague 1) | vague 2 | résolution |
|---|---|---|---|
| `OnyxSocle.kt` | 441 l. | 443 l. | **union** : forme de la vague 2 (`socleInk` en indirection, valeurs identiques) **+** la liste `Optical` à **six** valeurs de la vague 1 (`1.5.dp` ré-ajouté) |
| `SocleShadow.kt` | 58 l. | 58 l. | **identiques octet pour octet** — pris tel quel |
| `ConversationCover.kt` | 221 l. | 221 l. | **identiques octet pour octet** — pris tel quel |

`tools/socle-confrontation.py` : **21 jetons, 21 ACCORD, verdict global IDENTIQUES**.

## Version livrée

`versionName` **1.0.0+660** (`versionCode` 660 > 657), signée `ONYX_RELEASE_KEYSTORE`, sur
téléphone + Auto + Wear.

## CE QUI RESTE (à dire, pas à masquer)

**Le chantier B de la vague 2 est partiel.** Les **en-têtes** de Réglages, Capacités et de la
feuille de modèle sont passés aux jetons du socle ; **leur intérieur ne l'est pas** — **13
espacements hors pas** subsistent (`SettingsScreen` 7/14/40, `CapabilitiesScreen` 10/14/40,
`ModelSheet` 6/7/10/11/12/14/18). Les corps et les rayons, eux, sont à zéro partout.

Autres restes assumés : les **en-têtes de Ressources** (`OnyxSocle.exceptions`) et la conversion des
**écarts nommés** (`padding(start = …, end = …)`) sur les trois écrans — le verrou ne couvre
aujourd'hui que les quatre **formes d'écriture** littérales. **Aucune suppression n'a été faite.**

## Marqueurs dans le dex (`mapping.txt` R8, après `assembleRelease`)

`OnyxSocle` 48 · `OnyxSocle$ShadowDepth` 1 · `OnyxSocle$ShadowLayer` 2 · `ConversationCover` 324 ·
`FileThumbnail` 510 · `FileThumbnailView` 198 · `SocleKit` 204 · `ChatTabBar` 551.

`SocleShadow.kt` (`Modifier.socleShadow`, `cardShadow`) est **dessiné par le code mais encore sans
appelant** : R8 retire la classe (0 dans `mapping.txt`). La **famille** d'ombres reste dans le dex
via `OnyxSocle$ShadowDepth` / `$ShadowLayer`.
