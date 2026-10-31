package com.onyx.app.ui.common

import com.onyx.app.localization.OnyxLanguage
import android.content.Context
import androidx.annotation.DrawableRes
import com.onyx.app.ui.design.MenuItem
import com.onyx.app.ui.design.Symbols

/** Combien de temps on se tait (`MuteSpan`). */
enum class MuteSpan(val millis: Long, val title: String) {
    HOUR(60 * 60_000L, "For 1 Hour"),
    EIGHT_HOURS(8 * 60 * 60_000L, "For 8 Hours"),
    /** Demain à 8 h, comme `MuteSpan.untilTomorrow`. */
    TOMORROW(0L, "Until Tomorrow"),
    /** Jusqu'à ce qu'on le rallume : Hermes le note comme un silence sans fin. */
    FOREVER(0L, "Until I Turn It Back On"),
    ;

    /** Le moment où le silence s'arrête ; [now] sert à calculer « demain 8 h ». */
    fun until(now: Long = System.currentTimeMillis()): Long = when (this) {
        HOUR -> now + millis
        EIGHT_HOURS -> now + millis
        TOMORROW -> {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 8)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            calendar.timeInMillis
        }
        FOREVER -> Long.MAX_VALUE / 2
    }

    companion object {
        /** La durée d'un silence qui court jusqu'à [until], dans les mots de la référence. */
        fun describe(until: Long): String {
            if (until >= Long.MAX_VALUE / 2) return "Muted"
            val now = System.currentTimeMillis()
            if (until <= now) return "Unmuted"
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = until }
            val today = java.util.Calendar.getInstance()
            val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            val time = java.text.SimpleDateFormat("H:mm", OnyxLanguage.preferred)
                .format(java.util.Date(until))

            fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
                a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
                    a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

            return when {
                sameDay(today, calendar) -> "Muted until $time"
                sameDay(tomorrow, calendar) -> "Muted until tomorrow, $time"
                else -> "Muted until " + java.text.SimpleDateFormat("MMM d, H:mm", OnyxLanguage.preferred)
                    .format(java.util.Date(until))
            }
        }
    }
}

class Mutes(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("onyx_mutes", Context.MODE_PRIVATE)

    fun until(item: String): Long? {
        val value = prefs.getLong(item, 0L)
        if (value == 0L) return null
        if (value <= System.currentTimeMillis()) {
            prefs.edit().remove(item).apply()
            return null
        }
        return value
    }

    fun isMuted(item: String): Boolean = until(item) != null

    fun mute(item: String, span: MuteSpan) {
        prefs.edit().putLong(item, span.until()).apply()
    }

    fun unmute(item: String) {
        prefs.edit().remove(item).apply()
    }
}

/** Ce qu'une ligne du sous-menu du silence est : « Unmute », l'échéance, ou une des durées. */
sealed interface MuteRow {
    /** « Unmute » : on le rallume. */
    data object Unmute : MuteRow

    /** La ligne qui dit jusqu'à quand il est tu (« Silencieux jusqu'à 21:30 »). */
    data class Until(val text: String) : MuteRow

    /** Une des quatre durées, la première portant le filet du groupe des durées. */
    data class Span(val span: MuteSpan, val groupBreak: Boolean) : MuteRow
}

/**
 * Les lignes du sous-menu du silence (« Mute › ») à partir de l'échéance [until] (`null` quand rien
 * n'est tu) : « Unmute » et l'échéance quand il est déjà tu, puis **les quatre durées**. Jamais
 * vide : aucune durée ne disparaît du sous-menu.
 */
fun muteSubmenuRows(until: Long?): List<MuteRow> = buildList {
    if (until != null) {
        add(MuteRow.Unmute)
        add(MuteRow.Until(MuteSpan.describe(until)))
    }
    MuteSpan.entries.forEachIndexed { index, span ->
        add(MuteRow.Span(span = span, groupBreak = index == 0 && until != null))
    }
}

/**
 * Les entrées du sous-menu du silence (`MuteMenu`, ouvert par « Mute › ») : « Unmute » et son
 * échéance quand il est tu, puis les quatre durées — chacune son action.
 */
fun muteSubmenuItems(mutes: Mutes, item: String): List<MenuItem> =
    muteSubmenuRows(mutes.until(item)).map { row ->
        when (row) {
            MuteRow.Unmute -> MenuItem(label = "Unmute", symbol = Symbols.Bell) { mutes.unmute(item) }
            is MuteRow.Until -> MenuItem(label = row.text) { }
            is MuteRow.Span -> MenuItem(
                label = row.span.title,
                symbol = Symbols.BellSlash,
                groupBreak = row.groupBreak,
            ) {
                mutes.mute(item, row.span)
            }
        }
    }

/**
 * Les entrées « Mute » / « Unmute » d'un menu (`MuteMenu`), avec l'échéance quand il est tu.
 */
fun muteMenuItems(mutes: Mutes, item: String): List<MenuItem> {
    val until = mutes.until(item)
    return if (until != null) {
        // Déjà tu : « Unmute », puis la ligne qui dit jusqu'à quand.
        listOf(
            MenuItem(label = "Unmute", symbol = Symbols.Bell) { mutes.unmute(item) },
            MenuItem(label = MuteSpan.describe(until), symbol = null) { },
        )
    } else {
        MuteSpan.entries.map { span ->
            MenuItem(label = span.title, symbol = Symbols.BellSlash) { mutes.mute(item, span) }
        }
    }
}

/** Le symbole d'une conversation tue, à côté de son nom. */
@DrawableRes
val mutedMark: Int = Symbols.BellSlashFill
