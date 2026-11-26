package com.onyx.app.ui.chat

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuedMessageRowThemeTest {
    @Test
    fun `le contour pointille rentre le coin bas droit comme la bulle`() {
        val radius = CornerRadius(12f)
        val tucked = CornerRadius(5f)
        val shape = userBubbleRoundRect(Rect(0f, 0f, 100f, 40f), radius, tucked)

        assertEquals(radius, shape.topLeftCornerRadius)
        assertEquals(radius, shape.topRightCornerRadius)
        assertEquals(tucked, shape.bottomRightCornerRadius)
        assertEquals(radius, shape.bottomLeftCornerRadius)
    }
}
