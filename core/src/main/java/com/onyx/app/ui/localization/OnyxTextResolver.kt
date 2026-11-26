package com.onyx.app.ui.localization

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale

/** Point unique testable du choix traduction/verbatim utilisé par le wrapper Compose. */
fun resolveOnyxText(
    text: String,
    localize: Boolean = true,
    locale: Locale = OnyxLanguage.preferred,
): String = if (localize) OnyxLocalization.translate(text, locale) else text
