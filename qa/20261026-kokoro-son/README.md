# Kokoro: la voix ne sortait pas (bouton « en lecture », aucun son)

## Le défaut

Le serveur Kokoro répondait bien (`200`, `audio/pcm`, une phrase complète), l'app listait les voix
et le bouton passait au carré ⏹ — mais **aucun son ne sortait**, ni par le bouton ▶ d'une voix, ni
pour une réponse lue à voix haute.

Cause : dans `core/src/main/java/com/onyx/app/voice/SpeechAudioPlayer.kt`, le `AudioTrack` de flux
recevait un tampon **de la taille de la phrase entière** :

```
.setBufferSizeInBytes(maxOf(minimum, bytes.size.coerceAtMost(BUFFER_CAP)).coerceAtLeast(4_800))
```

`AudioTrack.write` en mode flux **copie** les octets dans le tampon du mélangeur, il ne les joue pas.
Le tampon pouvant contenir toute la phrase, la boucle d'écriture ne bloquait jamais : elle rendait la
main en une dizaine de millisecondes, et le lecteur enchaînait `stop()` puis `release()` — **avec la
tête de lecture encore à zéro**. Toute la phrase était jetée avant d'avoir sonné. Le bouton, lui,
affichait « en lecture » parce que cet état est posé dès l'appui, avant même le téléchargement.

## La preuve, avant/après (même appareil, même voix, même bouton)

`logcat/avant-bouton-Bella.txt` — APK 1.0.0+287 (défaut) :

```
I/OnyxVoiceAudio: build bytes=143874 minBuffer=4384 buffer=143874 ... bufferFrames=71937
I/OnyxVoiceAudio: play state=3 head=0
I/OnyxVoiceAudio: wrote=143874/143874 writes=15 in 10ms head=0 frames=71937 state=3
I/OnyxVoiceAudio: teardown head=0/71937 playState=3 t=11ms current=true
D/AudioTrack: stop(15): called with 71937 frames delivered
```

Le mélangeur n'a rien consommé, et sa trace le confirme (`logcat/audioflinger-pistes.txt`) :

```
02:26:25.171 AT::add    Id 55  Session 121  ...  Server FrmCnt 00000000  FrmRdy 71937
02:26:25.254 AT::remove Id 55  Session 121  ...  Server FrmCnt 00000000  FrmRdy 71937   <- 83 ms, 0 joué
```

`logcat/apres-bouton-Bella.txt` — APK corrigé :

```
I/OnyxVoiceAudio: pcm 143846o minBuffer=4384 buffer=9600 ...
I/OnyxVoiceAudio: write=9600 offset=0      head=0      state=3 t=15ms
I/OnyxVoiceAudio: write=9600 offset=9600   head=5440   state=3 t=177ms
...
I/OnyxVoiceAudio: played 143846o writes=15 in 3014ms head=71923/71923 frames state=3 musicActive=true
```

```
02:48:42.994 AT::add    Id 64  Session 225  ...  Server FrmCnt 00000000  FrmRdy 4800
02:48:46.116 AT::remove Id 64  Session 225  ...  Server FrmCnt 000118F3  FrmRdy 4800   <- 71923 joués
```

La réponse lue à voix haute suit le même chemin (`logcat/apres-reponse-lue.txt`) :
`played 794790o writes=83 in 16533ms head=397395/397395 frames` — la phrase entière.

## Écouter ce que l'app a reçu

`audio/recu-*.wav` : le WAV reconstitué **à partir des octets reçus par l'app** (pas du serveur), avec
`audio/ffprobe.txt`. C'est exactement ce que le haut-parleur aurait dû sortir.

| fichier | voix | durée |
|---|---|---|
| `audio/recu-avant-1.0.0+287.wav` | bouton ▶ de Bella, AVANT correction | 2,997 s |
| `audio/recu-bouton-Bella-apres.wav` | bouton ▶ de Bella, APRÈS correction | 2,997 s |
| `audio/recu-reponse-lue-apres.wav` | réponse lue à voix haute, APRÈS correction | 16,558 s |

Le PCM est du 24 kHz, 16 bits, mono — la sortie du serveur (`audio/pcm`), bit pour bit.

## Captures

- `captures/avant-bouton-Bella.png` — le bouton sur ⏹ alors que rien ne sonnait.
- `captures/apres-bouton-Bella-en-lecture.png` — Bella sur ■ pendant une lecture qui dure vraiment.
- `captures/apres-reponse-en-cours-de-lecture.png` — une réponse en cours de lecture (le
  haut-parleur de la rangée d'actions est remplacé par un carré d'arrêt).

## Ce que cet émulateur ne peut pas montrer

Le son **ne peut pas être entendu sur cette machine** : 13 des 14 émulateurs sont lancés avec
`-no-audio`, et les deux qui ne le sont pas échouent à ouvrir le pilote audio de l'hôte
(`Could not init 'pa' audio driver` — aucun serveur PulseAudio joignable depuis QEMU). La preuve
audible doit donc venir d'un appareil qui a une sortie : le téléphone du propriétaire. Ce qui est
prouvé ici, c'est que l'app donne au mélangeur **la phrase entière**, au bon format, et que le
mélangeur la **consomme en entier** (tête de lecture `71923/71923`, `musicActive=true`, compteur du
mélangeur à `0x118F3` = 71923 échantillons) au lieu de la jeter après 11 ms.

## Recette

`scripts/` reprend le pilotage des captures, rejouable tel quel :

- `drive.py` — pilote l'app par ancres d'accessibilité (texte / `content-desc`), jamais par
  coordonnées écrites : `python3 drive.py <serial> tap 'Réglages'`.
- `measure.py` — appuie sur un bouton d'écoute et relève `logcat`, la capture et le compteur
  d'images du mélangeur : `python3 measure.py <serial> <nom> <x> <y> [attente_s]`.
- `inject-kokoro.py` — pose l'adresse Kokoro, la voix et le moteur de lecture dans les réglages sans
  passer par les formulaires (`adb root`, `DataStore` protobuf) :
  `python3 inject-kokoro.py <serial> <url-kokoro> [<url-chat> <transport>]`.

Le journal détaillé de la lecture s'active à la demande, sans rebuild :
`adb shell setprop log.tag.OnyxVoiceAudio VERBOSE` (et `DEBUG` pour chaque écriture).
