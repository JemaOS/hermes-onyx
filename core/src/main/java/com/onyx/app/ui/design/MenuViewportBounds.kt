package com.onyx.app.ui.design

/** Pixel bounds inside the already-inset current window, not the physical display. */
internal data class MenuViewportBounds(val left: Int, val top: Int, val right: Int, val bottom: Int, val width: Int) {
    val height: Int get() = bottom - top
}

internal fun menuViewportBounds(windowWidth: Int, windowHeight: Int, preferredWidth: Int, margin: Int): MenuViewportBounds {
    val width = windowWidth.coerceAtLeast(0)
    val height = windowHeight.coerceAtLeast(0)
    val horizontal = margin.coerceIn(0, width / 2)
    val vertical = margin.coerceIn(0, height / 2)
    return MenuViewportBounds(
        left = horizontal,
        top = vertical,
        right = width - horizontal,
        bottom = height - vertical,
        width = preferredWidth.coerceIn(0, width - 2 * horizontal),
    )
}
