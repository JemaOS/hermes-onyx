> **Pending.** This repository is under FSL-1.1-MIT (derived from Redde) and not under a free
> license in the FSF sense. F-Droid and IzzyOnDroid therefore cannot list it for now.
> Do not send the requests below before the MIT date of each Redde version;
> see `NOTICE`. This file remains the procedure for that moment.
# Publishing Onyx on free repositories — without a paid developer account

This file is the procedure for the repositories that require **neither fees nor a paid account**:
IzzyOnDroid, F-Droid, and the GitHub releases page (already in place). Only two steps remain to be
done by hand, because they require a free account — Codeberg for IzzyOnDroid, GitLab for
F-Droid — that only the owner can open.

## What is already in the repository

| File | What it is for |
|---|---|
| `fastlane/metadata/android/en-US/` and `fr-FR/` | title, short summary, long description, release notes (`changelogs/1229.txt`), six screenshots and the feature graphic. This is the format read **by both** F-Droid and IzzyOnDroid, from the app's repository. |
| `fastlane/metadata/android/en-US/images/featureGraphic.png` | 1024 × 500, the feature graphic for the listings. |
| `fastlane/fdroiddata-draft/com.onyx.app.yml` | the ready-to-copy `fdroiddata` entry, with the `versionCode` read from `gradle.properties`. |
| `README.md` → *Install* | the Obtainium button, which installs and updates the app from the GitHub releases. |

None of this changes the app: these are text files and images.

## Step 1 — IzzyOnDroid (fastest, a few days)

IzzyOnDroid reuses **your** signed APKs, as they are: the update installs over the existing
installation, with no uninstall. It is the repository to target first.

1. Create an account on **Codeberg** (free, email only): https://codeberg.org/user/sign_up
   — their request tool has moved there; the old GitLab tracker is no longer the entry point.
2. Open the request: https://codeberg.org/IzzyOnDroid/repodata/issues/new
   (no account: an email to `team@izzyondroid.org`, their address for anything that does not fit
   in a form)
3. Title: `Inclusion request: Hermes Onyx (com.onyx.app)`
4. Message body (in English), paste as is:

```
App name: Hermes Onyx
App id: com.onyx.app
Repository: https://github.com/JemaOS/hermes-onyx
License: FSL-1.1-MIT
Category: Internet
Latest release: https://github.com/JemaOS/hermes-onyx/releases/latest
APK to use: onyx-app-<version>-release.apk — signed with the project's release key,
SHA-256 of the certificate: 1a1f8225f509167b4e01d4606b5ffa6eb17c07868d9b8190efc9f47c459a4402
(The same key signs the two companions, onyx-auto- and onyx-wear-.)

Fastlane metadata is already in the repository (fastlane/metadata/android/en-US and fr-FR):
title, short and full description, per-version changelogs, six phone screenshots at 1080x1920,
a 1024x500 feature graphic and a 512x512 icon.

What it is: the Android client for a self-hosted Hermes agent. No account, no telemetry,
no Firebase, no Play Services. It talks to one host only: the Hermes Dashboard the user runs
themselves. Streaming chat, bots and group rooms, hands-free voice, and a live view of the
user's computer.

Dependencies: every library is free software (AndroidX, OkHttp, kotlinx, Markwon, sshj, ZXing).
There is no billing library, no advertising SDK and no crash reporter — a Play Billing
dependency was removed in 1.3.98, so nothing non-free is left.

Cleartext traffic: the app ships a Network Security Configuration
(app/src/main/res/xml/network_security_config.xml) and permits cleartext, because a self-hosted
Hermes gateway usually runs on plain http:// on the local network. This is the "local
webserver" case your policy allows; the file documents the reason in place.

Anti-features: none. The app needs a self-hosted server to be useful, which the description says.

Upstream also publishes the same signed packages from its own F-Droid repository,
https://jemaos.github.io/hermes-onyx/repo — it does not replace yours, it just shows the
packages are maintained and consistently signed.
```

Izzy then checks the APK, the signature and the metadata, then adds the app to its repository.
The badge to add to the README afterwards:

```markdown
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/index/apk/com.onyx.app/badge.json)](https://apt.izzysoft.de/fdroid/index/apk/com.onyx.app)
```

## Step 2 — official F-Droid (slower, several weeks to a few months)

F-Droid **rebuilds** the app from source, then signs it with **its own** key. Two
consequences to know:

- the F-Droid APK cannot replace a GitHub APK through an update: different signature,
  you must uninstall then reinstall (the user must pick **one** source and stick with it);
- your `applicationId` and your `versionCode` must stay stable, which they are.

The request is made on the requests repository. A GitLab account (free) is required: it is the
only prerequisite, and it can only be created by hand.

1. https://gitlab.com/fdroid/rfp/-/issues/new — the text below is ready to paste.
2. The maintainer will then open a merge request on `fdroiddata` with the
   `fastlane/fdroiddata-draft/com.onyx.app.yml` entry (to be copied as `metadata/com.onyx.app.yml`).

Request text:

```
App: Hermes Onyx
Package: com.onyx.app
Source: https://github.com/JemaOS/hermes-onyx
License: FSL-1.1-MIT
Category: Internet

Build: gradle, `assembleRelease` from the repository root (`subdir: .`).
No NDK, no submodules, no prebuilt binaries except gradle/wrapper/gradle-wrapper.jar.
minSdk 26, targetSdk 36, compileSdk 37. No Firebase, no Play Services, no analytics.

Toolchain, in case the buildserver needs pinning:
  AGP 9.1.0, Kotlin 2.4.20, Gradle 9.8.2 (wrapper), JDK 21.

Dependencies: all free software (AndroidX, OkHttp, kotlinx, Markwon, sshj, ZXing).
No billing library, no proprietary component, no advertising SDK.

Version numbers live in gradle.properties (`onyx.versionCode`), not in build.gradle.kts, and are
declared explicitly per release; nothing is derived from git at build time.
Release notes: https://github.com/JemaOS/hermes-onyx/releases
```

### The two risks, to state up front to the maintainer

1. **The toolchain is very recent** — AGP 9.1.0, Gradle 9.8.2, `compileSdk 37`. If the buildserver
   does not have these versions, the first build will fail and it will be necessary to pin lower; this is mechanical,
   not a refusal in principle. Better to flag it in the request than to discover it after
   three weeks.
2. **The release pace.** F-Droid freezes one version at a time and its queue is measured
   in weeks. With several versions per day, the listing will always be behind. Hence the value
   of the self-hosted repository: it absorbs the pace, F-Droid brings the visibility.


### What was blocking F-Droid, and is now fixed

`com.android.billingclient` 7.1.1 (Play Billing) was the dependency of the tip screen
(`ui/common/TipJarScreen.kt`): the only non-free library in the project, and F-Droid refuses an
app that depends on it, even if the screen is optional.

**Fixed in 1.3.98**: the tip jar and the library were removed, not worked around. No build
variant is needed — the published APK no longer contains a single billing class, and the
`com.android.vending.BILLING` permission that the library injected into the manifest disappeared with
it. Verification:

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c billing   # 0
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk | grep -c BILLING   # 0
```

The `READ_CALENDAR`, `WRITE_CALENDAR` and `READ_CONTACTS` permissions declared in the manifest come
from the "phone tools" screen (`ui/settings/PhoneToolsScreen.kt`): justify them in the request,
F-Droid accepts them when the use is real.

## Step 3 — repositories that require an account, but no money

Do this only when you want to expand; they are all free to sign up.

| Repository | What it requires | What it brings |
|---|---|---|
| Amazon Appstore | free developer account, no annual fee | Fire tablets, an audience that does not install APKs by hand |
| Samsung Galaxy Store | free account, APK accepted | Samsung devices, widely installed in Europe |
| Huawei AppGallery | free account, own signature and SDK | Chinese market and Huawei devices |
| Aptoide / APKPure | direct upload, no fees | visibility, light review |

Google Play remains the only paid route ($25 once), and it is not necessary to exist.

## With every release

Both repositories read the listing **from this repository**, not from a web console. A new release
therefore requires one more file, and nothing else:

1. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — and its `fr-FR` twin, **500
   characters at most** each (beyond that, F-Droid truncates);
2. the screenshots, only if the interface has changed visibly;
3. once F-Droid has accepted, `CurrentVersion` and `CurrentVersionCode` update by themselves
   (`UpdateCheckMode: Tags`), nothing to push elsewhere.

IzzyOnDroid, for its part, follows the releases page: an APK published by `release.sh` is picked up
on their robot's next pass.

## To check before every submission

```bash
bash scripts/check-forbidden-words.sh .
```

The repository's guard rejects any platform trademark in the content **and** in the file
names: new metadata files are subject to it like the others.
