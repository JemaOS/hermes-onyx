package com.onyx.app.ui.chat

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.onyx.app.data.model.ServerConfig
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Starts at the real conversation owner, not a directly mounted destination sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp")
class CompactChatNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun overflowReachesFeedFilesAndIdentityAndBackKeepsDraft() {
        val state = mutableStateOf(ChatUiState(server = ServerConfig(id = "compact-fixture"), conversationId = "compact-nav", input = "unsent draft"))
        var identity = 0
        var conversations = 0
        var models = 0
        val picked = mutableListOf<android.net.Uri>()
        compose.setContent {
            ChatScreen(stateFlow = state, startCards = emptyList(),
                onInputChange = { state.value = state.value.copy(input = it) },
                onSend = {}, onStop = {}, onRetry = {}, onClearError = {}, onNewConversation = {},
                onOpenConversations = { conversations++ }, onPickModel = { models++ },
                onOpenSettings = {}, onOpenBotIdentity = { identity++ }, onOpenVoice = {},
                onExpandDraft = {}, onOpenCard = {}, onAddAttachments = { picked.addAll(it) },
                onApproval = { _, _, _ -> }, onRemoveAttachment = {}, onOpenDelivered = {},
                onOpenLocalFile = { _, _, _ -> }, assistantName = "Ada")
        }
        compose.onNodeWithText("Feed").assertDoesNotExist()
        compose.onNodeWithText("Files").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Feed", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("No cards from Ada yet. Its answers appear here.").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains("unsent draft")
        compose.onNodeWithText("No cards from Ada yet. Its answers appear here.").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Files", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Nothing has been exchanged in this conversation yet.").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains("unsent draft")
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Onyx", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, identity); assertEquals("unsent draft", state.value.input) }
        compose.onNodeWithText("default model").performClick()
        compose.runOnIdle { assertEquals(1, models) }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Add attachment").performClick()
        compose.onNodeWithText("Files").performClick()
        val request = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        assertEquals("*/*", request.intent.type)
        assertTrue(request.intent.getBooleanExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false))
        val uri = android.net.Uri.parse("content://compact-fixture/document/1")
        compose.runOnIdle {
            compose.activity.activityResultRegistry.dispatchResult(request.requestCode,
                android.app.Activity.RESULT_OK, android.content.Intent().setData(uri))
        }
        compose.runOnIdle { assertEquals(listOf(uri), picked); assertEquals("unsent draft", state.value.input) }
        compose.onNodeWithContentDescription("Conversations").performClick()
        compose.runOnIdle { assertEquals(1, conversations) }
    }
}
