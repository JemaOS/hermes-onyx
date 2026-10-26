package com.onyx.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L'identifiant de sous-agent lu dans les arguments d'un `tool.start`: c'est lui, et rien d'autre,
 * qui autorise steer et stop. Sans lui, la transcription reste en lecture seule.
 */
class ToolActivitySubagentIdTest {

    @Test
    fun `subagent_id est lu dans les arguments JSON`() {
        assertEquals("sa-42", ToolActivity.subagentIdOf("""{"subagent_id":"sa-42","model":"gpt"}"""))
    }

    @Test
    fun `un id generique suffit quand le gateway n'emploie pas subagent_id`() {
        assertEquals("child-7", ToolActivity.subagentIdOf("""{"id":"child-7"}"""))
    }

    @Test
    fun `subagent_id prime sur id`() {
        assertEquals("real", ToolActivity.subagentIdOf("""{"subagent_id":"real","id":"other"}"""))
    }

    @Test
    fun `des arguments absents ou illisibles ne donnent aucun identifiant`() {
        assertNull(ToolActivity.subagentIdOf(null))
        assertNull(ToolActivity.subagentIdOf(""))
        assertNull(ToolActivity.subagentIdOf("not json"))
        assertNull(ToolActivity.subagentIdOf("[1,2,3]"))
        assertNull(ToolActivity.subagentIdOf("""{"subagent_id":""}"""))
        assertNull(ToolActivity.subagentIdOf("""{"other":"x"}"""))
    }
}
