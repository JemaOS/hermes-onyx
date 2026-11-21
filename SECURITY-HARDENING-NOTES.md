# Final Security Hardening Notes

## Scope and limits

This source review builds on the attachment credential fix and proxy/installer security fixes. It is not an APK trust certification. No production configuration, key rotation, APK deployment, or native Windows/Linux service-manager integration is included.

## Recommendations, in priority order

1. Release a verified patched Android artifact. Check package identity, signing lineage and SHA-256; exercise login, chat, downloads and redirects on-device. A fork cannot update an upstream-signed installation without the appropriate signing key. Preserve settings before any uninstall.
2. Rotate gateway credentials after migration. Earlier HTTP usage and provisioning copies warrant retiring old credentials; no compromise was established. Prefer independently revocable, narrowly scoped per-device credentials over unrestricted shared gateway authority.
3. Encrypt every network hop, including reverse proxy to gateway. Preserve explicit HTTP support only for deliberate trusted/encrypted-network deployments, with a conspicuous warning. Do not silently disable certificate validation.
4. Pin installer tool/dependency versions and verify reviewed per-platform digests before executing them. Cloudflared still uses the mutable GitHub latest download endpoint; safe archive extraction does not establish artifact authenticity. Obtain and maintain trusted hashes rather than fetching a hash from the same untrusted response.
5. Validate update URLs as HTTPS and verify APK package identity and signing lineage before presenting installation. Android enforces update signatures, but application-side verification can reject unexpected artifacts earlier. Test wrong-package, wrong-signer and downgrade paths.
6. Add CI security gates: core credential/redirect tests and Python proxy/installer regressions on every change; native Windows ACL and Linux service-manager tests; dependency and artifact scanning; reproducible APK/signature/hash evidence for releases.
7. Keep browser/desktop-control plugins disabled unless required. Require separate credentials, loopback/private listeners, explicit host-side approval for privileged actions, and audit trails that never record secrets.
8. Harden WebViews further where compatible: disable file access when assets permit, test blocked network/frame navigation and bridge rejection, and retain restrictive CSP/sanitization.

## Explicit residual risks

- Foreground pairing links/QRs intentionally contain credentials; treat screenshots and exported provisioning files as secrets.
- Per-process worker and request limits reduce unauthenticated exhaustion but do not replace edge rate limits or upstream concurrency controls.
- Local root, the same OS account, and a compromised gateway remain outside file-permission and Android credential-origin isolation guarantees.
- Static absence of a demonstrated exploit is not proof that all application/plugin attack surfaces are safe.
