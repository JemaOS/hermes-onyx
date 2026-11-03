# Onyx 1.3.1 — the version is 1.3.1 now, and it increments from there

## The version is 1.3.1 now, and it increments from there

The release page used to read `Onyx 1.0.0+1132` — a name that changed shape every time and a
number that told nothing. From now on the app, the release page and the tag all read **1.3.1**,
and every release after it is **1.3.2**, **1.3.3**, and so on.

### One source of truth

The displayed version is **derived from the version code**, not written by hand:

- `onyx.versionCode` — the integer Android compares to accept an install over the current one;
- `onyx.versionBase` — the code that carries the first 1.3.1.

`versionCode - versionBase` is the patch number, so a publication is one number to change, and
forgetting one of two is impossible. The phone, Android Auto and Wear OS all read the same two
properties, so the three packages of a release can never disagree.

### The tag keeps the number

The release tag is `v1.3.1+1133`: the readable name first, the number after the `+`. The number is
what decides the order — it is the `versionCode` of the APK, and Android compares it to accept the
install — and the app's update card now shows the readable part alone: **1.3.1**, not
`1.3.1+1133`.

### What this release carries

The notifications are on by default — the background alerts, the reply watcher, the sound — and
the permission is asked for at the first launch instead of only when the switch is toggled.

And the foreground service is now **tied to the turn**: since Android 12 a foreground service
cannot be started from the background, so it was started exactly where Android refuses it, and the
work died when the app was closed. It now starts when the turn starts — while the app is visible,
the only moment Android allows — and lives until the turn ends.

---

**Tests: 3805, 0 failures.** New locks: the version scheme (the name derives from the code, and
the three modules cannot drift apart), and the update card reading `1.3.1` out of the tag
`v1.3.1+1133`.
