# Écran de verrouillage — correctifs et preuves

Quatre retours d'usage, quatre correctifs, sur un arbre de travail neuf créé depuis `cc4b9d4`.

## Ce qui est corrigé

| Retour | Fichier:ligne | Correctif |
| --- | --- | --- |
| « Ça marche pas ni empreinte ni visage » | `app/src/main/java/com/onyx/app/security/AppLock.kt:74-96` et `:110-117` | `canAuthenticate(AUTHENTICATORS)` interroge le téléphone **avant** qu'un bouton soit offert ; `biometryLabel` (`:82`) nomme ce que l'appareil a vraiment (`Fingerprint`, `Face unlock`, sinon `Face or fingerprint`) et rend `null` s'il ne peut pas. Classes fortes **et** faibles (`BIOMETRIC_STRONG or BIOMETRIC_WEAK`). |
| idem | `app/src/main/java/com/onyx/app/ui/lock/LockScreen.kt:76-131` | `promptBiometry` : plus de `?: return` muet (journal + message à l'écran), `setConfirmationRequired(false)` (`:123`), pas d'identifiant d'appareil (`setNegativeButtonText` reste permis, `:122`), `onAuthenticationFailed` (`:95`) écrit un message et **laisse retenter**, `onAuthenticationError` (`:99`) écrit une phrase lisible (le bouton « Utiliser le code » guide — « Saisissez votre code. » — au lieu d'annoncer un échec, un balayage ne dit rien), et un prompt qui refuse de démarrer (`runCatching`, `:128`) se voit. |
| « Le logo cadenas est plein » | `core/src/main/res/drawable/ic_sym_lock.xml`, `Symbols.kt:94`, `LockScreen.kt:151` | `Symbols.LockOutline` = `lock` en contour (traits, aucun remplissage), 30 dp, accent — inchangés. Le plein (`lock.fill`) reste pour les cartes d'approbation. |
| « La phrase n'est pas traduite » | `core/src/main/java/com/onyx/app/localization/OnyxFrenchLock.kt` + `OnyxLocalization.kt:72` | Les phrases **composées** (« Unlock with Face or fingerprint », « Require Fingerprint ») ont leurs entrées françaises ; les erreurs de biométrie et l'explication d'une biométrie absente aussi (`LockScreenLocalizationTest`). |
| « Quand je rentre le code on voit plus rien » | `LockScreen.kt:128-146` | `BoxWithConstraints` + colonne `verticalScroll` + `heightIn(min = viewport)` : contenu centré quand il y a la place, défilant sinon, donc au-dessus du clavier. |
| (vu en passant) | `app/src/main/java/com/onyx/app/ui/settings/LockSettingsScreen.kt:65,127,142,148-165` | Supprimer ou poser le code ne se voyait pas : l'écran ne relisait `lock.hasPin` qu'à l'occasion d'une recomposition déclenchée par autre chose. Un état `hasPin` tient la ligne et le pied de page. |

Aucune chaîne visible en dur ne reste dans les deux écrans : `LockScreenLockTest` lit les littéraux des deux
fichiers et exige que chacun passe par la couche de traduction (seuls le tag du journal et ses messages en
sont dispensés).

## Preuves

- `proof-1-lock-screen.png` — cadenas **en contour**, « Onyx est verrouillé », « Déverrouiller avec
  l'empreinte », champ « Code » + « Déverrouiller ».
- `proof-2-lock-keyboard.png` — clavier ouvert : le champ « Code » **et son curseur** sont au-dessus du
  clavier, avec le bouton et le cadenas.
- `proof-3-wrong-code-error.png` — code faux : « Ce code est incorrect. » se lit à l'écran, au corps de
  texte, au-dessus du clavier.
- `proof-9-biometry-error-on-screen.png` — « Utiliser le code » sur la feuille : elle se ferme et l'écran
  affiche « Saisissez votre code. » (une consigne, pas un échec) au-dessus du champ.
- `biometric-unlock-proof.txt` — séquence empreinte (arbre d'accessibilité, pas de capture : la feuille
  biométrique du système est `FLAG_SECURE`, `screencap` rend du noir) : écran verrouillé → appui →
  feuille « Onyx est verrouillé » / « Déverrouillez pour voir vos conversations » / « Utiliser le code »
  → `adb emu finger touch 2` (doigt inconnu : la feuille reste, l'app reste verrouillée, on peut
  retenter) → nouvel appui → `adb emu finger touch 1` → l'application est déverrouillée.
- `proof-6-unlocked-by-fingerprint.png` — après l'empreinte, l'application est ouverte.
- `proof-7-settings-lock.png` — Réglages → Verrouillage, code posé : « Exiger l'empreinte »,
  « Verrouiller après / Immédiatement », « Modifier le code », « Supprimer le code ».
- `proof-8-settings-lock-no-pin.png` — après « Supprimer le code » : « Définir le code » et « Un code
  personnel, si la biométrie du téléphone n'est pas disponible. » (la suppression se voit tout de suite).

Émulateur : `emulator-5590` (AVD `onyx-rows`), empreinte **enrôlée** par Réglages Android +
`adb emu finger touch 1`, APK **release** `1.0.0+227`, sha256
`70f320ecfc9fbe5adfce8a03d9a6ca49e19a55b1417cbf061e7ca5230f4df004`, locale d'application `fr-FR`
(`cmd locale set-app-locales com.onyx.app --locales fr-FR`).

Aucun visage sur les images d'émulateur : le chemin « visage » n'est couvert que par le nom du bouton
(`Face unlock` → « Déverrouiller avec le visage ») et par les tests.
