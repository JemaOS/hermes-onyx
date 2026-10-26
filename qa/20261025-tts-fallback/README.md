# Réglages → Voix: phrase de test — avant / après

Émulateur : `emulator-5554` (portrait, serveur `Dashboard Hermes · http://10.0.2.2:8650`).
Le serveur TTS d'Hermes est HS : `/api/audio/speak` répond **HTTP 400
`{"detail":"TTS chunk 1 failed (edge): No TTS provider available…"}`** — c'est exactement le
scénario rapporté.

| | build installé | sha256 de `base.apk` |
|---|---|---|
| avant | master `0b2034e` (`releases/app-v1.0.0_194-20261025-1104.apk`) | `853f4a9d198007670e50f2412e9deb3c4c64a0de1e2bd4c22f9d947778588fa6` |
| après | `onyx-tts-fallback` (`app/build/outputs/apk/release/app-release.apk`) | `162adc16ab277cae8d9c7902677dff6c199ffc34dacdc140ad83ec6baa0c9ef5` |

## Avant (`before-row-http400.png`, `before-zoom.png`)

La rangée **« Say a test sentence »** porte l'erreur brute dans sa **colonne de valeur** : texte rouge
sur 3 lignes, tronqué à « No TTS provider », rangée deux fois plus haute. Aucun pied de section, et
**rien ne parle**.

## Après (`after-01-voice-hermes.png` → `after-03-final-check-footnote.png`)

1. `after-01-voice-hermes.png` — Hermes sélectionné, rangée au repos.
2. `after-02-working.png` — valeur courte **« Working… »** (une ligne) pendant l'essai.
3. `after-03-final-check-footnote.png` / `after-03b-zoom.png` — valeur courte **✓** (hauteur de
   rangée normale) et le détail complet dans un **pied de section sous la carte** :

   > The Dashboard couldn't speak, so the on-device voice read it. The server returned HTTP error 400.
   > {"detail":"TTS chunk 1 failed (edge): No TTS provider available. Enable Edge TTS with: hermes pm
   > install --extra edge-tts or run 'hermes setup tts' and choose NeuTTS for local synthesis."}

Le ✓ dit que la **voix de l'appareil a bien prononcé la phrase** malgré l'échec du serveur.
Les hiérarchies UI des deux états sont dans `before-final-hierarchy.xml` / `after-final-hierarchy.xml`,
les journaux de pilotage dans `before-log.txt` / `after-log.txt`.

## Reste à faire

Le serveur TTS d'Hermes lui-même est toujours HS (edge-tts → 403 au handshake WSS). Le repli
appareil est désormais la voie de secours, mais « Hermes » ne parle pas par lui-même tant que
`tts` n'est pas réparé sur l'hôte (`hermes pm install --extra edge-tts` ou `hermes setup tts`).
