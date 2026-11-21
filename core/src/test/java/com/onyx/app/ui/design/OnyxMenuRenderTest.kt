package com.onyx.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.onyx.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnyxMenuRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyThemeUsesAnOpaqueForegroundSurface() {
        for (theme in OnyxTheme.entries) {
            for (dark in listOf(false, true)) {
                val resolved = ResolvedTheme.resolve(theme, dark, OnyxFonts.System)
                assertEquals("$theme dark=$dark", 1f, menuSurface(resolved).alpha, 0f)
            }
        }
    }

    @Test fun panelOccludesPageInLightAndDarkThemes() {
        val dark = mutableStateOf(false)
        val page = mutableStateOf(Color.Magenta)
        val theme = { ResolvedTheme.resolve(OnyxTheme.STANDARD, dark.value, OnyxFonts.System) }
        compose.setContent {
            CompositionLocalProvider(LocalOnyxTheme provides theme()) {
                Box(Modifier.fillMaxSize().background(page.value)) {
                    TestOverlayHost {
                        OnyxMenu(onDismiss = {}, items = listOf(MenuItem("Resume", onSelect = {})),
                            anchor = Rect(80f, 60f, 120f, 100f))
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) {
            for (color in listOf(Color.Magenta, Color.Green)) {
                compose.runOnIdle { dark.value = isDark; page.value = color }
                compose.waitForIdle()
                // Sample actual rendered pixels in the blank padding, not a source/token check.
                val pixels = compose.onNode(hasScrollAction()).captureToImage().toPixelMap()
                assertEquals(menuSurface(theme()), pixels[pixels.width / 2, 3])
            }
        }
    }

    @Test fun panelBlocksPageAndOutsideTapDismissesWithoutClickThrough() {
        val visible = mutableStateOf(true)
        var pageClicks = 0
        var actions = 0
        var dismissals = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("root")) {
                Box(Modifier.size(50.dp).testTag("page").clickable { pageClicks++ })
                TestOverlayHost {
                    if (visible.value) OnyxMenu( onDismiss = { dismissals++; visible.value = false },
                        items = listOf(MenuItem("Resume", onSelect = { actions++; dismissals++; visible.value = false })),
                        anchor = Rect(180f, 100f, 220f, 140f))
                }
            }
        }
        compose.onNode(hasScrollAction()).performTouchInput { click(Offset(width / 2f, 3f)) }
        compose.runOnIdle { assertEquals(0, dismissals); assertEquals(0, pageClicks) }
        compose.onNodeWithText("Resume").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(1, dismissals) }
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("root").performTouchInput { click(Offset(12f, 12f)) }
        compose.runOnIdle { assertEquals(0, pageClicks); assertEquals(2, dismissals) }
        compose.onNode(hasScrollAction()).assertDoesNotExist()
    }

    @Test fun longMenuRemainsBoundedAndLastActionIsReachable() {
        var selected = -1
        compose.setContent {
            Box(Modifier.size(240.dp, 180.dp).testTag("viewport")) {
                TestOverlayHost {
                    OnyxMenu(onDismiss = {}, items = (0..15).map { index ->
                        MenuItem("Action $index", onSelect = { selected = index })
                    }, anchor = Rect(1000f, 1000f, 1050f, 1050f))
                }
            }
        }
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(menu.left >= viewport.left && menu.top >= viewport.top)
        assertTrue(menu.right <= viewport.right && menu.bottom <= viewport.bottom)
        compose.onNodeWithText("Action 15").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(15, selected) }
    }
}

@Composable
private fun TestOverlayHost(content: @Composable () -> Unit) {
    val host = remember { OverlayHost() }
    CompositionLocalProvider(LocalOverlayHost provides host) {
        content()
        host.layer?.invoke()
    }
}
