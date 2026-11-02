# Onyx 1.0.0+1125 — the model sheet is back to scale

The model sheet read **too big**: the close button came out at 31.7 dp, the star at 44.7 dp — the star overlapped the price and the model name — and the "Reasoning" chips at 54.8 dp, so eight of them spilled onto two rows.

**The cause was one, and it was general.** `OnyxIcon` applies its `size` **after** the caller's `modifier` (`modifier = modifier.size(size)`). A caller that put the touch target on the **same node** — `.size(48.dp)` — had its `size` **overwritten**, and the glyph inherited the size of the **area**: 48 dp instead of 15.

The text itself was normal (mention 9.4 dp, chip 10.1 dp, paragraph 10.1 dp). Only the icons were oversized.

**The fix** is the shape already used by the sidebar's search field and the top bar: a `Box` carrying the one-finger target, with the icon inside it at **its own** size.

- close button: **31.7 dp → 15 dp**
- star: **44.7 dp → 17 dp**
- search field's clear button: **48 dp → 16 dp**
- "Reasoning" chips: **54.8 dp → ~34 dp**

The touch target stays at 48 dp everywhere: what changed is the **displayed** size, not the tappable area. A short chip such as "Max" (46.8 dp) keeps its background tight to the text instead of stretching.

This release also carries the three packages — phone, Auto and Wear OS — under one signature.

**Tests: 3765, 0 failures.** `ModelSheetIconSizeTest` locks the contract: an icon no longer carries a touch target in its own `modifier`, the three keep their size, and each has its 48 dp area around it.
