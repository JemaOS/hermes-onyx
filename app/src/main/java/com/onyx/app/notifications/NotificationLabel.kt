package com.onyx.app.notifications

import android.content.Context
import com.onyx.app.localization.OnyxLocalization

/**
 * The label of a notification, in the language of **the app**.
 *
 * `OnyxLocalization[key]` follows `Locale.getDefault()`, that is, the phone's language. That is not
 * enough for a notification: the app can be set to French on a system in English (Android's
 * "App language" setting), and the notification would then come out in English
 * under a French interface — exactly the defect found in review. The app's resources,
 * however, carry its language: so that is where we read it.
 */
internal fun Context.notificationLabel(key: String): String =
    OnyxLocalization.translate(key, resources.configuration.locales[0])
