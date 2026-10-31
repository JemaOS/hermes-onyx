package com.onyx.app.data.local

import com.onyx.app.localization.OnyxLanguage
import android.content.Context

/** Une famille d'outils que l'agent peut demander à utiliser sur le téléphone. */
enum class PhoneArea(
    val id: String,
    val label: String,
    val blurb: String,
    val allowLabel: String,
) {
    CALENDAR("calendar", "Calendar", "Look up your events and add new ones.", "Allow reading"),
    REMINDERS("reminders", "Reminders", "Read your reminders, add one, mark one done.", "Allow reading"),
    CONTACTS("contacts", "Contacts", "Look someone up: names, numbers, addresses. It can't change them.", "Allow reading"),
    PHOTOS("photos", "Photos", "Find photos by date, kind and what they show.", "Allow reading"),
    FILES("files", "Files", "Read and save files in Onyx's folder in the Files app.", "Allow reading"),
    ALARMS("alarms", "Alarms", "Set an alarm or a timer, see the ones it set, and cancel one.", "Allow reading"),
    WIDGET("widget", "Widget", "Write a few lines on Onyx's “From Your Agent” widget.", "Allow"),
    PEOPLE("people", "Calls and Messages", "Start a call, and write a message for you to send.", "On"),
    ;

    companion object {
        fun fromId(raw: String?): PhoneArea? = entries.firstOrNull { it.id == raw }
    }
}

/** Ce que l'utilisateur autorise pour une famille d'outils. */
enum class PhonePolicy(val id: String, val label: String) {
    OFF("off", "Off"),
    ASK("ask", "Ask each time"),
    ALLOW("allow", "Allow"),
    ;

    companion object {
        fun fromId(raw: String?): PhonePolicy = entries.firstOrNull { it.id == raw } ?: OFF
    }
}

/**
 * Réglages → Outils téléphone.
 *
 * Miroir de `PhoneToolsSettingsView` : un interrupteur maître, puis une politique par
 * famille. Ce que l'agent peut faire — et ce qu'il doit demander avant — se règle ici.
 */
class PhoneToolsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_phone_tools", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun policy(area: PhoneArea): PhonePolicy =
        PhonePolicy.fromId(prefs.getString("policy_${area.id}", null))

    fun setPolicy(area: PhoneArea, policy: PhonePolicy) {
        prefs.edit().putString("policy_${area.id}", policy.id).apply()
    }

    /** Les lignes que l'agent a écrites sur le widget. */
    var agentWidgetText: String
        get() = prefs.getString(KEY_WIDGET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_WIDGET, value).apply()

    /** Ce que l'agent a demandé récemment, du plus récent au plus ancien. */
    fun recent(): List<String> =
        prefs.getString(KEY_LOG, "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList()

    fun note(title: String, detail: String, at: Long = System.currentTimeMillis()) {
        val stamp = java.text.SimpleDateFormat("HH:mm", OnyxLanguage.preferred).format(java.util.Date(at))
        val entry = "$stamp · $title${if (detail.isBlank()) "" else " — $detail"}"
        val lines = (listOf(entry) + recent()).take(MAX_LOG)
        prefs.edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
    }

    fun clearLog() {
        prefs.edit().remove(KEY_LOG).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_WIDGET = "agent_widget_text"
        const val KEY_LOG = "recent"
        const val MAX_LOG = 20
    }
}
