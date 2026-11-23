package com.onyx.app.ui.bots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.onyx.app.ui.design.OverlayHost
import com.onyx.app.ui.design.LocalOverlayHost
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.onyx.app.data.model.serve.BotProfile
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.GroupMember
import com.onyx.app.data.model.serve.GroupRoom
import com.onyx.app.ui.chat.components.CompactComposerDock
import com.onyx.app.ui.design.ChatTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CompactRoomUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val room = GroupRoom(
        id = "compact-room",
        name = "Release planning",
        members = listOf(
            GroupMember("hermes", "hermes", "hermes", "Hermes"),
            GroupMember("research", "research", "research", "Research"),
        ),
    )

    private fun show(width: Int = 320, scale: Float = 1f, desktop: Boolean = false) {
        lateinit var vm: GroupRoomViewModel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = GroupRoomViewModel(
                    compose.activity.application, ServerConfig(id = "room-ui-fixture"), room.copy(inDesktop = desktop),
                ) as T
            })[GroupRoomViewModel::class.java]
        }
        compose.setContent {
            val density = LocalDensity.current
            val host = remember { OverlayHost() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalOverlayHost provides host,
            ) {
                Box(Modifier.width(width.dp).fillMaxSize()) {
                    GroupRoomScreen(
                        viewModel = vm,
                        server = ServerConfig(id = "room-ui-fixture"),
                        onBack = {},
                        onEnded = {},
                        onOpenVoice = {},
                        onOpenFile = { _, _, _ -> },
                    )
                    host.layer?.invoke()
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun narrowRoomUsesCompactControlsAndPreservesDraftAcrossDestinations() {
        show()
        val placeholder = compose.onNodeWithText("Message Release planning")
        placeholder.assertIsDisplayed()
        compose.onNodeWithText("Message the group").assertDoesNotExist()
        val attach = compose.onNodeWithContentDescription("Add").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dictate = compose.onNodeWithContentDescription("Dictate").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(attach.width >= 48f && dictate.width >= 48f)
        assertTrue(kotlin.math.abs(attach.center.y - dictate.center.y) <= 1f)
        assertTrue(attach.right <= dictate.left)
        compose.onNode(hasSetTextAction()).performTextInput("Please review")
        compose.onNodeWithContentDescription("Send").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dictate").assertDoesNotExist()
        // Freeze the repeating room-face clock while the modal overlay is mounted.
        compose.mainClock.autoAdvance = false
        ChatTab.paged.filter { it != ChatTab.CHAT }.forEach { tab ->
            compose.onNodeWithContentDescription("More").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            compose.onNodeWithText(tab.label, useUnmergedTree = true).performScrollTo().performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithContentDescription("Conversations").performClick()
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            compose.onNodeWithText("Please review").assertIsDisplayed()
        }
    }

    @Config(qualifiers = "w840dp-h900dp")
    @Test fun wideLargeFontRoomKeepsRecipientAndStopAccessible() {
        var sent = ""
        var stopped by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(700.dp)) {
                    CompactComposerDock {
                        GroupComposer(
                            bots = room.composerBots(), roomName = room.name, compact = true,
                            isStreaming = !stopped,
                            onSend = { text, _ -> sent = text },
                            onStop = { stopped = true },
                        )
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("@res")
        compose.onNodeWithText("Research", useUnmergedTree = true).performClick()
        compose.onNodeWithText("@research ").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
        assertTrue(stopped)
        compose.onNodeWithContentDescription("Send").assertIsDisplayed().performClick()
        assertEquals("@research ", sent)
    }

    @Test fun compactRoomKeepsLiveVoiceAndAttachmentActionsInAddMenu() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Live voice", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add attachment", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun desktopRoomKeepsReadOnlyNoticeAndHeaderNavigation() {
        show(desktop = true)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Add").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Files", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Conversations").performClick()
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
    }

}
