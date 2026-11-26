# Screenshots — Onyx app (Android)

Real screenshots of the **signed release** APK `onyx-1115-release.apk`
(`versionName 1.0.0+1115`, `versionCode 1115`, package `com.onyx.app`), taken on an
Android 16 emulator (AVD `onyxclair`, Pixel 7, 1080×2400, density 420, x86_64, Google
APIs image) with host GPU rendering
(`GLES: … NVIDIA GeForce RTX 4070 SUPER … OpenGL ES 3.1`).

Every screen exists in both **light** and **dark** mode, obtained with `adb shell cmd uimode night
no|yes` on the same device, in the same session, without restarting the app.

**Screenshot language: English** (see “Language” below). **No first name is
legible** in the fourteen images (see “Privacy”).

| # | Screen actually displayed | Light | Dark |
|---|---|---|---|
| 01 | Chat room: question sent + agent reply, Chat/Feed/Files/Onyx tab bar | `01-salle-chat-clair.png` (2,623 KB) | `01-salle-chat-sombre.png` (2,563 KB) |
| 02 | Feed tab of the same conversation (reply card, thumbs, Discuss) | `02-salle-flux-clair.png` (2,261 KB) | `02-salle-flux-sombre.png` (2,213 KB) |
| 03 | Composer “Add to message” sheet (**+** button): Photo Library, Camera, Files, Paste | `03-composer-piece-jointe-clair.png` (1,558 KB) | `03-composer-piece-jointe-sombre.png` (1,481 KB) |
| 04 | Conversation list (slide-out panel): search, Chats/Bots tabs, shortcuts, filters, 1 conversation | `04-liste-conversations-clair.png` (298 KB) | `04-liste-conversations-sombre.png` (460 KB) |
| 05 | Settings › Notifications (Android channels: Approvals, Silent approvals, Answers, Calls, Reachable) | `05-reglages-notifications-clair.png` (176 KB) | `05-reglages-notifications-sombre.png` (179 KB) |
| 06 | Settings › Privacy › Lock (locking enabled, recovery code) | `06-reglages-verrouillage-clair.png` (179 KB) | `06-reglages-verrouillage-sombre.png` (183 KB) |
| 07 | App lock screen (“Onyx is locked”) | `07-verrouillage-clair.png` (848 KB) | `07-verrouillage-sombre.png` (710 KB) |

All images are 1080×2400 PNGs (status bar included, no cropping).

## Language

**Can the app display in English? Yes.** The Onyx interface is in English in all
fourteen screenshots.

Onyx ships its own language setting, independent of Android's:
**Settings › Appearance › Language**, with three choices — *System*, *Français*, *English*.
The choice lives in the app (`app_language`, in the DataStore `onyx_settings.preferences_pb`)
and **never touches Android's language**: the phone can stay in its own language, while
Onyx's screens, sheets, menus and notifications switch to English in one step.

For this series, the setting was verified on screen: **Language → English**. (The device
was also set to `en-US`, so both paths lead to English.) The *Voice* setting
(speech-synthesis voice) remains on `fr-FR`: it is a separate option, it translates no
interface text and does not appear on any of the captured screens.

## Screenshot context

- The test Hermes gateway listens on `127.0.0.1:8642`; the app reaches it via `10.0.2.2:8642`
  (so the address shown in the conversation list is the emulator's, not a
  real address). The **Check** button on the *Settings* screen returns **“1 model”** (green
  dot), and the displayed model is `hermes-agent`, routed by `api_server.model_routes` to
  `deepseek/deepseek-v4.1-flash-1017` (provider `kilocode`).
- The visible conversation is a **real round trip** with the gateway, not injected
  data: question “Which models are you on?”, agent reply
  (“deepseek/deepseek-v4.1-flash-1017, served through kilocode — that's the api_server's
  model_routes mapping for the "hermes-agent" model…”), 5.1 s, 29.5 k tokens ↓.
- The conversation list shows only one entry (“Which models are you on?”): the
  demo discussions used for the previous series have been removed.

## Privacy

- **No image contains the legible first name “John”.** Verified twice: an OCR pass
  (Tesseract) over the fourteen PNGs — zero occurrences — and a visual review of each image.
- The demo conversation is deliberately neutral and contains no proper name; the agent does
  not address the person by their first name in the retained reply.
- **No blurred areas**: there is nothing to blur, muting was not
  necessary.
- The status bar contains only the time, the network and the battery — no person's name.

## Known deviations

- The two files `06-reglages-verrouillage-*.png` are the raw screenshots
  (`adb exec-out screencap -p`), **re-encoded losslessly** (pixels strictly identical,
  verified by SHA-256 hash of the RGBA buffer): the screen is almost uniformly light,
  and default PNG compression brought it down to ~105 KB, below the expected 150 KB threshold. The
  re-encoding simply uses a lower compression level (deflate `Z_FIXED`),
  without touching a single pixel. All other files are the raw screenshots as
  is.
- The lock screen (07) shows its two real warnings — no biometrics are
  enrolled on this emulator — and therefore offers the **recovery code** instead of the
  face/fingerprint button. This is the state produced by a new device with no biometrics enrolled; it
  is not a capture error. The code itself does not appear anywhere on screen.
- On screen 06, locking is **enabled** and a recovery code is set; the previous
  series showed locking disabled and no code. The current state is that of the
  capture machine, not a staged setup.
- The Android `POST_NOTIFICATIONS` permission was granted on the emulator so that
  screen 05 shows “Notification settings — **On**”, as in the previous series; without
  it, Android blocks Onyx notifications and the screen shows “Off”.

## Restarting the emulator

```bash
export JAVA_HOME=/home/john/android-toolchain/jdk-21
export ANDROID_HOME=/home/john/android-toolchain/sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

$ANDROID_HOME/emulator/emulator -avd onyxclair -no-snapshot -no-audio -no-boot-anim -gpu host &

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb install -r /home/john/.hermes/profiles/kilo/cache/scratch/onyx-1115-release.apk

adb shell cmd uimode night no   # or: night yes
adb exec-out screencap -p > docs/captures/<nom>-clair.png
```

The gateway must be running on `127.0.0.1:8642`: the app reaches it via `10.0.2.2:8642`
(*Settings › Connection* setting, **Check** button → “1 model”).
