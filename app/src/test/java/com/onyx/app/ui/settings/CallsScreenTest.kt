package com.onyx.app.ui.settings

import com.onyx.app.calls.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le libellé et le repli des heures de tranquillité, **tels que l'écran Appels les montre**.
 *
 * Ce qui est éprouvé : la plage annoncée (« Nothing rings from 22:00 to 07:00 »), le cas de la
 * plage vide (début = fin), et le repli d'une heure qui sort de la journée (les flèches de l'écran
 * ajoutent et retirent sans jamais déborder).
 */
class CallsScreenTest {

    @Test
    fun `eteintes, rien n'est annonce comme silencieux`() {
        assertEquals("Nothing is quiet yet.", quietHoursDetail(enabled = false, from = 1320, until = 420))
    }

    @Test
    fun `une plage vide le dit`() {
        assertEquals(
            "They start and end at 22:00, so nothing is quiet: pick another time.",
            quietHoursDetail(enabled = true, from = 1320, until = 1320),
        )
    }

    @Test
    fun `une plage ordinaire s'ecrit en heures`() {
        assertEquals(
            "Nothing rings from 22:00 to 07:00.",
            quietHoursDetail(enabled = true, from = 1320, until = 420),
        )
    }

    @Test
    fun `l'heure se replie dans la journee`() {
        assertEquals(0, wrapDay(QuietHours.MINUTES_A_DAY))
        assertEquals(QuietHours.MINUTES_A_DAY - 30, wrapDay(-30))
        assertEquals(0, wrapDay(QuietHours.MINUTES_A_DAY + 0))
        assertEquals(1320, wrapDay(1320))
        assertEquals(30, wrapDay(QuietHours.MINUTES_A_DAY + 30))
    }
}
