## Highlights

- **From `schergr`**, seven merged contributions: Dashboard credential scoping and redirect hardening, proxy-intake and installer permission hardening, single-flight Dashboard sign-in, foldable overlays and vault gating, a content-first sidebar, the repaired group-send and opaque-menu regression gates, and a compact composer shared by chats and rooms (`CompactComposerDock`).
- **Networking**: a stream that ends without saying so is now reported as cut, the Dashboard channel pings so a dead socket is seen, the reply watcher reads a window instead of the whole thread, and a reply that misses the stream is read back from the session.
- **Chat**: a turn names its conversation (the gateway stopped refusing it), the take-over card is a line of the thread, and the thread opens on its last page with a working "earlier".
- **Browser screen**: the plugin answers health again, a refusal is remembered per page and conversation, and the page takes the shape of the screen.
- **Bots**: renaming one bot no longer renames the others, and a refusal is recorded.

## Notes

`1.3.99` is built on the same release key as `1.3.98` (certificate SHA-256 `1a1f8225f509167b4e01d4606b5ffa6eb17c07868d9b8190efc9f47c459a4402`), so updating from `1.3.98` works in place: no uninstall, no data loss. Verify the download against `SHA256SUMS-1.3.99.txt` if you want to be sure before installing.

## Important Notice

The tag was moved to the license-corrected tree in this cycle: `v1.3.98` and the removed `v1.3.96` no longer offer a source archive whose `LICENSE` says MIT. Onyx is licensed under FSL-1.1-MIT — see `LICENSE` and `NOTICE`.

## Verification

`:core` and `:app` unit tests: 4,417 passing, 1 skipped. All three packages carry `versionCode` 1231 / `versionName` 1.3.99 and are signed with the same certificate.

Thank you for your continued support.
