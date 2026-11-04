# Release notes — Hermes Onyx `1.3.35`

What this version brings, and **where it is provable**. Each point points back to a commit, to a file
in the sources, or to a number read from the tree. Nothing here is claimed that cannot be shown.

**Version:** `1.3.35` (`versionCode` = `1167`, written in `gradle.properties`).
**Packages:** phone `onyx-1.3.35-release.apk`; `onyx-auto-1.3.35-release.apk`;
`onyx-wear-1.3.35-release.apk`; checksums in `SHA256SUMS-1.3.35.txt`.
**Test suite:** `4 118 tests, 0 failures` — `:core:testDebugUnitTest` + `:app:testDebugUnitTest`,
counted in the XML of the build that produced these packages.

---

## 1. The thread, under load

The complaint was precise: « ça lag au défilement » on a big conversation, on a phone that should not
lag. The audit is static (no device attached while it ran), and it found the cost in three places.
Full report and measurement protocol: `AUDIT-PERFORMANCE.md` (commit `cb4d535`).

- **A reply in flight no longer recomposes the list.** `state.live` carries `lastEventAt`, so it
  changed at **every token** (the thread republishes 33 times per second). Handed to every row, it
  made every row's parameters differ at each publication: none could be skipped, and prose, folds,
  cards and tool chips all recomposed 33×/s. It now goes to the live row only
  (`app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt`).
- **The glass freeze no longer rides the token stream.** `LocalGlassScrollFreeze` was provided from
  the screen's own scope, and `isScrollInProgress` flips twice per `scrollToItem` — that is, twice
  per token while the thread re-anchors at the bottom. It is now provided by `GlassScrollFreeze()`
  (`core/src/main/java/com/onyx/app/ui/design/GlassTouch.kt`), which reads the flag in its own
  scope; the thread and the sidebar both use it.
- **An archived checkmark is drawn once.** `DrawnCheckmark` mounted a path animation for every
  finished tool step at first composition — scrolling back into a forty-step reply replayed forty
  tracings, each allocating a `Path` per frame. The tracing is now reserved to the live row
  (`app/src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt`).
- **Row work survives leaving the window.** `RowCaches` keeps the markdown split, the derived
  message facts and the first-link lookup across a `LazyColumn` disposal
  (`app/src/main/java/com/onyx/app/ui/chat/components/RowCaches.kt`).
- Smaller, all measured in the same pass: the approval count is an allocation-free loop over the
  thread, the link lookup / mention menu / tools chip are memoised on their real inputs, and the
  list items declare a `contentType`.

**What is not claimed:** no frame number. There is no device on the build machine and no emulator
binary, so a figure would have been invented. `AUDIT-PERFORMANCE.md` §6 gives the exact
`dumpsys gfxinfo` / Perfetto protocol to measure before and after, on the phone, in release.

## 2. The composer

- **The full-screen button says what it does, and keeps four corners** (commit `7c0c7ed`):
  `OnyxComposer.kt` and its French label in `OnyxFrench.kt`, locked by `ComposerLayoutTest`.

## 3. Bots and conversations

- **A renamed bot takes its new name everywhere** (commit `d12000d`): the name reaches the server
  and every conversation that already used it (`BotsViewModel.kt`, `ConversationRepository.kt`),
  held by `BotRenamePropagationTest`.

## 4. Publication

- **The phone package is named `onyx-1.3.35-release.apk`.** This is a contract, not a habit: the
  in-app updater only picks an asset matching `^onyx-([\d.]+)-release\.apk$`
  (`core/src/main/java/com/onyx/app/data/net/AppUpdate.kt`). The previous release shipped
  `onyx-app-1.3.32-release.apk`, which that expression does not match — so the updater saw no
  update at all. Fixed here by naming, and verified after upload by reading the asset list back.
