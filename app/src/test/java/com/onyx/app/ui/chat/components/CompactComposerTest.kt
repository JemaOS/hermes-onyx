package com.onyx.app.ui.chat.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Native Compose fixtures, not physical foldable or speech-engine evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w840dp-h900dp")
class CompactComposerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyDraftRunningAndDictationDispatchToDistinctOwners() {
        val draft = mutableStateOf("")
        val streaming = mutableStateOf(false)
        val dictating = mutableStateOf(false)
        var sent = 0
        var stopped = 0
        var dictated = 0
        var voice = 0
        compose.setContent {
            OnyxComposer(draft.value, { draft.value = it }, streaming.value, "Research bot",
                plate = true, compact = true, applyBottomInsets = false,
                dictating = dictating.value, onDictate = { dictated++ },
                onSend = { sent++ }, onStop = { stopped++ }, onTalk = { voice++ })
        }
        compose.onNodeWithText("Message Research bot").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dictate").performClick()
        compose.runOnIdle { assertEquals(1, dictated); draft.value = "draft" }
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { assertEquals(1, sent); streaming.value = true }
        compose.onNodeWithContentDescription("Stop").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Send").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stopped) }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Send after this reply").performClick()
        compose.runOnIdle { assertEquals(2, sent); streaming.value = false; dictating.value = true }
        compose.onNodeWithContentDescription("Stop dictating").performClick()
        compose.runOnIdle { assertEquals(2, dictated) }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Live voice").performClick()
        compose.runOnIdle { assertEquals(1, voice); assertEquals("draft", draft.value) }
    }

    @Test fun compactRestBoundedGrowthAndResizeKeepDraftAndTargets() {
        val width = mutableStateOf(320.dp)
        val scale = mutableStateOf(1f)
        val draft = mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                Box(Modifier.width(width.value)) {
                    OnyxComposer(draft.value, { draft.value = it }, false, "Ada",
                        plate = true, compact = true, applyBottomInsets = false,
                        onDictate = {}, onSend = {}, onStop = {}, onTalk = {})
                }
            }
        }
        val rest = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        assertTrue("empty input must not reserve three lines", rest < 72f)
        compose.onNode(hasSetTextAction()).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(center.x, height - 2f))
        }.assertIsFocused()
        compose.onNode(hasSetTextAction()).performTextInput((1..40).joinToString("\n") { "line $it" })
        val grown = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        assertTrue(grown > rest)
        assertTrue("long input must scroll rather than consume the window", grown < 400f)
        for (w in listOf(320.dp, 740.dp)) {
            compose.runOnIdle { width.value = w; scale.value = 1.5f }
            compose.onNode(hasSetTextAction()).assertTextContains("line 40", substring = true)
            for (label in listOf("Add", "Send")) {
                val control = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
                assertTrue(control.boundsInRoot.width >= 48f)
                assertTrue(control.boundsInRoot.height >= 48f)
                assertTrue(control.boundsInRoot.right <= w.value)
            }
        }
    }

    @Test fun attachmentOnlyCanSendOpenAndRemoveWithoutTyping() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "compact-note.txt").apply { writeText("fixture") }
        val pending = com.onyx.app.ui.chat.PendingAttachment(
            "fixture", android.net.Uri.fromFile(file), "compact-note.txt", "text/plain", false)
        val attachments = mutableStateOf(listOf(pending))
        var sends = 0
        var opens = 0
        compose.setContent {
            OnyxComposer("", {}, false, "Ada", plate = true, compact = true, applyBottomInsets = false,
                attachments = attachments.value, onOpenAttachment = { opens++ },
                onRemoveAttachment = { id -> attachments.value = attachments.value.filterNot { it.id == id } },
                onDictate = {}, onSend = { sends++ }, onStop = {}, onTalk = {})
        }
        compose.onNodeWithText("compact-note.txt").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Send").performClick()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(1, sends) }
        val remove = compose.onNodeWithContentDescription("Remove compact-note.txt").assertIsDisplayed()
        assertTrue(remove.fetchSemanticsNode().boundsInRoot.width >= 48f)
        remove.performClick()
        compose.onNodeWithText("compact-note.txt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dictate").assertIsDisplayed()
        compose.runOnIdle { assertTrue(attachments.value.isEmpty()); file.delete() }
    }

    @Test fun plusRetainsAttachmentAndEditorActionsAndVisibleErrors() {
        var attached = 0
        var expanded = 0
        compose.setContent {
            OnyxComposer("", {}, false, "Ada", plate = true, compact = true, applyBottomInsets = false,
                problem = "Upload failed", onAddAttachment = { attached++ }, onExpand = { expanded++ },
                onDictate = {}, onSend = {}, onStop = {}, onTalk = {})
        }
        compose.onNodeWithText("Upload failed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Add attachment").performClick()
        compose.runOnIdle { assertEquals(1, attached) }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Open the editor in full screen").performClick()
        compose.runOnIdle { assertEquals(1, expanded) }
    }
}
