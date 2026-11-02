# Onyx 1.0.0+1123 — the anchored menu is real glass

The anchored menu read as a **grey rectangle** laid over the screen. That was not an over-strong tint: it was **four faults stacked**, none of them visible on screen.

**1. The menu lived in a `Dialog` — a different window.** It could see neither the Haze source (posed by the screen, in the main window), nor the lens page, nor the backdrop layer. A panel that lives there can therefore **sample nothing**: it can only paint a veil. It now goes through an **overlay of the main window** (`OverlayHost`), which fixes all **29 call sites** at once. Hardware back closes it — the `Dialog` no longer did.

**2. The lens path painted a tint but sampled nothing** (the recorded texture came back empty), so the panel let the text underneath through **unblurred**. The menu now runs on the **Haze** path, the only path measured as actually sampling.

**3. The group room's page material was posed outside the Haze source**, so the glass had nothing but a flat fill to eat. It is now inside the source, as in the 1:1 thread — the same fault the thread had, and the same fix.

**4. `masking = true`** raised the body to 0.92: the panel became the very **flat** block the fix was meant to remove. Dropped — legibility here is carried by the **blur**, not by opacity.

**Measured on the device** (the room, the menu over the thread):

| | before | after |
|---|---|---|
| correlation with the sharp capture (1.0 = text passes net) | 0.9927 | **0.2804** |
| standard deviation of the background under the panel | 1.3 (flat) | the real content, 0.6 → 94.7 |

The text under the panel is no longer sharp: it is **blurred**. The panel carries the bright edge and the menu text stays readable.

Surfaces that are not menus keep their path: the sidebar sheet stays deliberately opaque (locked by `DialogueSurfaceVerrouTest`), and `SHEET`/`ALERT` stay declared as own-window.

**Tests: 3755, 0 failures.** `GlassLuminanceTest` now says what the table says (the menu is in the window), and `VerreFondVerrouTest` has one more lock for the room's page material.
