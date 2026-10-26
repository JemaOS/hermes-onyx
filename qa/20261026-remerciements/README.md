# Remerciements — inventaire réel et réécriture de la page

Version livrée : `1.0.0+319` — `app-release.apk` sha256
`607a975731c6b797f27b2a952b3f154f0ce4edd071d1b85c67ad68872a54ae16`, vérifié sur l'appareil
(`sha256sum $(pm path com.onyx.app)`).

## Ce qui a été retiré, et pourquoi

La page créditait **** et les paquets ** d'une autre plateforme** (, 
Crypto,  Atomics…) : aucune de ces bibliothèques n'est une dépendance de cette app
(`gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/build.gradle.kts` — rien de tel), et
leur seule présence dans la page était un reste du modèle d'origine. Les deux fichiers de licence
livrés avec elles sont supprimés des ressources :

- `core/src/main/assets/rich/LICENSES/-MIT.txt`
- `core/src/main/assets/rich/LICENSES/-packages-Apache.txt`

Les polices et les deux moteurs de rendu livrés (KaTeX, Mermaid) restent en place : eux sont bien
embarqués dans `core/src/main/assets/rich/`.

## Inventaire réel — chaque ligne vérifiée à sa source

### Sur ce téléphone (embarqué)

| Composant | Licence | Source vérifiée |
|---|---|---|
| Jetpack Compose | Apache-2.0, © The Android Open Source Project | `libs.versions.toml` → `androidx.compose.ui:ui` |
| AndroidX / Jetpack | Apache-2.0, © The Android Open Source Project | `androidx.core:core-ktx` (+ lifecycle, activity, DataStore, CameraX, biometric, Glance) |
| kotlinx.coroutines | Apache-2.0, © JetBrains s.r.o. | `org.jetbrains.kotlinx:kotlinx-coroutines-android` |
| kotlinx.serialization | Apache-2.0, © JetBrains s.r.o. | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| OkHttp | Apache-2.0, © Square, Inc. | `com.squareup.okhttp3:okhttp` |
| Markwon | Apache-2.0, © Dimitry Ivanov (noties) | `io.noties.markwon:core` |
| sshj | Apache-2.0, © Jeroen van Erp and contributors | `com.hierynomus:sshj` (POM du cache Gradle) |
| EdDSA-Java | CC0 1.0 Universal, © str4d | `net.i2p.crypto:eddsa` (POM : `CC0 1.0 Universal`) |
| ZXing | Apache-2.0, © ZXing authors / JourneyApps | `com.journeyapps:zxing-android-embedded` (POM) |
| KaTeX | MIT, © 2013-2020 Khan Academy and other contributors | `assets/rich/katex.min.js` + `LICENSES/KaTeX-MIT.txt` |
| Polices KaTeX | SIL OFL 1.1, © 2013-2020 Khan Academy | `assets/rich/fonts/KaTeX_*.woff2` + `LICENSES/KaTeX-fonts-OFL.txt` |
| Mermaid | MIT, © 2014-2022 Knut Sveidqvist | `assets/rich/mermaid.min.js` + `LICENSES/mermaid-MIT.txt` |
| Inter | SIL OFL 1.1, © 2016 The Inter Project Authors | `assets/fonts/Inter-*.ttf` + <https://github.com/rsms/inter> |
| Source Code Pro | SIL OFL 1.1, © 2023 Adobe (nom réservé « Source ») | `assets/fonts/SourceCodePro-*.ttf` + <https://github.com/adobe-fonts/source-code-pro> |
| Tuiles OpenStreetMap | ODbL 1.0, © OpenStreetMap contributors | <https://www.openstreetmap.org/copyright> ; attribution obligatoire dessinée sous chaque carte (`OsmTiles.attribution`, `OsmMapImage.kt`) |
| Terminal | **aucune licence tierce** : code du dépôt | `TerminalScreen.kt`, `TerminalBuffer.kt` — l'écran et le tampon sont écrits ici, la session SSH passe par sshj |

### Sur votre PC (services du poste)

| Composant | Licence | Source vérifiée |
|---|---|---|
| Hermes (votre serveur) | MIT, © 2025 Nous Research | `~/.hermes/hermes-agent/LICENSE` |
| Kokoro (poids Kokoro-82M) | Apache-2.0, © hexgrad | <https://huggingface.co/hexgrad/Kokoro-82M> |
| Kokoro-FastAPI | Apache-2.0, © remsky | `~/kokoro-tts/Kokoro-FastAPI/LICENSE` + <https://github.com/remsky/Kokoro-FastAPI> |
| Piper (`piper-tts 1.8.0`) | **GPL-3.0-or-later**, © les auteurs de Piper | `piper_tts-1.8.0.dist-info/METADATA` (`License: GPL-3.0-or-later`) + <https://github.com/OHF-Voice/piper1-gpl> |
| faster-whisper 1.2.1 | MIT, © 2023 SYSTRAN | `faster_whisper-1.2.1.dist-info/METADATA` + <https://github.com/SYSTRAN/faster-whisper> |
| FFmpeg | GPL v3 **sur cette compilation** (`--enable-gpl`, `ffmpeg -L`) ; LGPL sans les parties GPL | `/home/john/.hermes/tools/ffmpeg-9.0.1-linux-x64/LICENSE.txt` + <https://ffmpeg.org/legal.html> |
| cloudflared 2026.10.3 | Apache-2.0, © Cloudflare, Inc. | <https://github.com/cloudflare/cloudflared> |

## Cas à noter

- **Piper n'est pas MIT** : le paquet installé (`piper-tts`, dépôt `piper1-gpl`) est
  **GPL-3.0-or-later**. L'ancien binaire C++ `rhasspy/piper` était MIT, mais ce n'est pas ce qui
  tourne ici.
- **FFmpeg** : le binaire de ce poste est bâti avec `--enable-gpl` et s'annonce GPL v3 ; une
  compilation sans les parties GPL reste LGPL-2.1+. Les deux sont écrits dans la page.
- **Tuiles OpenStreetMap** : la page crédite « © OpenStreetMap contributors » (licence ODbL) ;
  l'app dessine « © OpenStreetMap » sous la carte. La forme longue est celle que demande l'OSMF,
  la forme courte est celle du code — écart mineur, signalé, pas corrigé (hors du périmètre de la
  page).
- **`material-icons-extended`** est déclaré dans `libs.versions.toml` mais n'est référencé par
  aucun module : il n'est **pas** crédité (composant fantôme), et le test de provenance refuserait
  de le voir apparaître.

## Tests ajoutés

- `app/src/test/java/com/onyx/app/ui/common/AcknowledgementsPageTest.kt` (9 tests) — aucun mot de
  l'autre plateforme dans la page ; chaque composant cité se retrouve dans le dépôt (alias Gradle
  présent **et** référencé, ressource livrée, fichier source, ou URL de licence pour un service du
  poste) ; chaque ligne nomme une licence ; le terminal est déclaré maison ; les fichiers de licence
  retirés ne sont plus livrés.
- `core/src/test/java/com/onyx/app/localization/VisibleTextHasNoForeignPlatformTest.kt` (3 tests) —
  **tous** les catalogues affichables (clés et valeurs, dans les deux langues) sont balayés: aucun
  ne cite l'autre plateforme.

## Captures

`qa/20261026-remerciements/shots/` — `avant-*` sur `1.0.0+318`, `apres-*` sur `1.0.0+319`,
`apres-fold-*` sur un écran large. Haut et bas, sombre et clair, police 1,0× et 1,3×.
