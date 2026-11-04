One surface, not two.

## The framed menu

The project menu was framed with a red box, and the complaint was that it did
not look clean — that it should read as one uniform surface.

The panel was carrying the **plate finish** — the head light (26% white along
its top edge) and the foot shade (16% black along its bottom edge) that make a
large sheet read as glass catching a room. On a sheet that is right. On a menu
of three rows it is a gradient, and a gradient inside a small floating panel
means the eye sees **two** surfaces: a white card sitting on a grey one.

Measured on the owner's screenshot, before: the panel body fell from **246 to
207** in luminance between its top and bottom edge, with a bright hairline
through the middle of it.

`GlassSurface.MENU` is now `lit = false`. No head light, no foot shade, no inner
hairline, no edge reflection. It keeps what actually separates it from the page:
its tint, its blur, and its drop shadow. It reads as one tone from the top edge
to the bottom — the same rule the bottom plate and the header already follow,
where a surface separates from the page by luminance alone.

The material lock test suite gains a case asserting the anchored menu is a
single surface: flat finish, sheen and shade at zero, shadow kept. `MENU` also
left the list of surfaces that are *required* to keep a finish — that list said
the opposite, and a lock that encodes a decision you just reversed is a lock
that lies.

## The build

Compiling this release caught a bug that had nothing to do with the menu, and
that would have blocked every release until it was found.

The Gradle build reads the voice token file into a `BuildConfig` constant. A
second key had been appended to that file, so that one connect link serves both
chat and speech — the voice proxy accepts either. The build read the file
whole, newline included, and the generated Java died on `unclosed string
literal`: no APK, at all. It now takes the first non-blank line, which is what a
multi-line token file means.

## Verified

- `:core:testDebugUnitTest` green, including the new case — 6/6 in the material
  lock suite.
- Three APKs built, signed with the same certificate as your installation, so
  they install over it without uninstalling.
- `versionCode 1163`, `versionName 1.3.31`.
- `SHA256SUMS-1.3.31.txt` recomputed and checked against the shipped files.

## Still not right

The rendering is verified against a **measurement of a screenshot**, not against
a running app: this machine has no emulator running, so no one has seen the new
panel with their own eyes yet. Install it and look — if the panel still reads as
two surfaces, the cause is somewhere other than the plate finish, and the next
screenshot gets the same pixel treatment.
