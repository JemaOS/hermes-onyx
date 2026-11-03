# Onyx 1.3.3 — hands-free speaks your language, and the sheets reach the bottom

Two defects, one release. Both were about the app and its interface disagreeing with each other.

## Hands-free speaks the language of the app

The hands-free screen quoted the stop phrases — but in the wrong language. An English app said
`Say “stop listening” or “that's all” to end hands-free.` over a French pair of phrases, and a
French app quoted the French pair under an English sentence. The quote and the interface could
diverge, in both directions, and they did.

Now the phrases, their quotes and their separator all follow the language of the app:

- English: `Say “stop listening” or “that's all” to end hands-free.`
- French: `Dites « arrête d'écouter » ou « c'est tout » pour quitter le mode mains libres.`

The quoted pair now comes from **one place** (`HandsFreeStops`, in `core`), so the screen and the
"Always heard" list in Settings → Voice → Stop phrases cannot drift apart again.

### The microphone follows the app too

The deeper half of the bug was not the text. The recognition locale was **pinned to `fr-FR`**,
whatever the app's language: on an English app, *stop listening* was transcribed in French, badly,
and therefore **never recognised**. The stop phrases could not work in English — not because they
were mistranslated, but because the microphone was listening in another language.

The microphone now follows the interface language when nothing is chosen, and an explicit choice
is still respected: it is written to its own key, which is what distinguishes a person who picked
French from the old constant that happened to be French.

### And the voice that reads the answer

The "Reply language" setting only reached the model, never the voice: a French reply on an English
app was read with an English voice. The reading voice now speaks the chosen reply language, and
follows the microphone — hence the interface — when the setting is "Automatic".

## Bottom sheets reach the bottom of the screen

Opening the gateway sheet left a **band at the bottom of the screen** where the app showed through,
undimmed and cut in half — the send button and the microphone pill, sliced.

The cause was the sheet's dialog window: it stopped **above** the navigation bar, so the scrim
never covered that band. The bottom sheets now open their window over the whole screen
(`decorFitsSystemWindows = false`), like the more recent sheets already did. Measured on the same
screen before and after: the bottom-left pixel went from the app's grey (`190,199,199`) to the
sheet's white (`255,255,255`).

## One version, one name

A release used to carry an internal counter in every name it published: the tag
`v1.3.1+1133`, the files `onyx-1133-release.apk`, the sums `SHA256SUMS-1133.txt`. The number
said nothing to a reader — it was the machine's business, not theirs.

The published names now carry the **version** and nothing else:

- the tag is `v1.3.3`;
- the packages are `onyx-1.3.3-release.apk`, `onyx-auto-1.3.3-release.apk` and
  `onyx-wear-1.3.3-release.apk`;
- the sums file is `SHA256SUMS-1.3.3.txt`.

The app's update card reads both spellings — a release published the old way is still read exactly,
and one published the new way is compared on its **readable** version, which is derived from the
internal counter, so the two can never disagree.

---

**Tests: 3816, 0 failures** (`:core` 1808, `:app` 2008; one skipped). New locks: the quoted
phrases follow the interface language (`HandsFreeStopsTest`), the microphone locale follows the
interface and an explicit choice survives a restart (`SpeechLocaleFollowsAppTest`), every bottom
sheet's window covers the system bars (`FenetreFeuilleTest`), and a release is read whether its
tag and its packages carry the number or not (`AppUpdateTest`).

**Verify a download:**

```bash
curl -LO https://github.com/JemaOS/hermes-onyx/releases/download/v1.3.3/SHA256SUMS-1.3.3.txt
sha256sum -c SHA256SUMS-1.3.3.txt
```

The three packages — phone (`com.onyx.app`), Android Auto (`com.onyx.app.auto`) and Wear OS
(`com.onyx.app.wear`) — carry the same version and are signed with the **same certificate**, so
they install side by side.
