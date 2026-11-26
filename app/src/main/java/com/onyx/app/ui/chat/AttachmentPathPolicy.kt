package com.onyx.app.ui.chat

/**
 * **Where an attachment lives** — the rule, pure, without Android.
 *
 * It exists to be exercised without a device (a JVM test reads it), and to be the only one to
 * decide what is "durable": an attachment is **never** referenced by a transient URI (a picker
 * `content://`, whose grant dies with the process) nor by a path under `cacheDir` (which the
 * system purges whenever it wants). It is referenced by the `file://` of a copy in the app's
 * private storage (`filesDir`).
 *
 * This is the mechanism of the measured defect: three captures written to `cacheDir`, a picker
 * that kept only its URI, and "Unreadable" bubbles after the app was closed.
 */
object AttachmentPathPolicy {

    /** True for a reference that **does not survive** the app closing or a cache cleanup. */
    fun isTransient(path: String): Boolean =
        path.startsWith("content://") ||
            path.startsWith("android.resource://") ||
            path.contains("/cache/") ||
            path.startsWith("cache/")

    /**
     * True for a `file://` of durable private storage (`filesDir`), the only form accepted for an
     * attachment: it survives `am force-stop`, a phone reboot, and the system's cache cleanup.
     */
    fun isDurable(path: String): Boolean =
        path.startsWith("file://") && path.contains("/files/") && !isTransient(path)
}
