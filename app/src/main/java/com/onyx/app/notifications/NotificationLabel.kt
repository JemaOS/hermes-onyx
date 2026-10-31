package com.onyx.app.notifications

import android.content.Context
import com.onyx.app.localization.OnyxLocalization

/**
 * Le libellé d'une notification, dans la langue de **l'app**.
 *
 * `OnyxLocalization[clé]` suit `Locale.getDefault()`, c'est-à-dire la langue du téléphone. Elle
 * ne suffit pas pour une notification : l'app peut être réglée en français sur un système en
 * anglais (réglage « Langue de l'app » d'Android), et la notification sortait alors en anglais
 * sous une interface française — exactement le défaut relevé en recette. Les ressources de l'app,
 * elles, portent sa langue : c'est donc là qu'on la lit.
 */
internal fun Context.notificationLabel(key: String): String =
    OnyxLocalization.translate(key, resources.configuration.locales[0])
