package com.onyx.app.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les trois vues d'une conversation — **la morsure anti-faux-chiffre**.
 *
 * Ces tests sont volontairement durs : ils **échouent** dès qu'une valeur manquante devient un zéro
 * plausible, dès qu'un diff vide est présenté comme une modification, ou dès qu'un compte absent est
 * peint comme un compte à 0 %. C'est la règle qui prime (« jamais un chiffre inventé ») transformée en
 * verrou : ce qui doit mordre, mord.
 */
class ThreeViewsReadTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // -- Points de restauration : le diff ------------------------------------------------------

    @Test
    fun `le diff se compte sur ses lignes, en francais, comme la reference`() {
        val answer = json.parseToJsonElement(
            """
            {"stat":"backup.sh | 4 +++-\n1 file changed, 3 insertions(+), 1 deletion(-)",
             "diff":"--- a/backup.sh\n+++ b/backup.sh\n@@ -1,2 +1,4 @@\n-a\n+b\n+c\n+d\n"}
            """.trimIndent(),
        )
        val diff = ServeChatActions.diffOf(answer)!!
        assertEquals(1, diff.filesChanged)
        assertEquals(3, diff.insertions)
        assertEquals(1, diff.deletions)
        assertEquals("1 fichier modifié, 3 insertions(+), 1 suppression(−)", diff.countLine())
    }

    @Test
    fun `les lignes du diff portent leur nature, une par une`() {
        val answer = json.parseToJsonElement(
            """{"stat":"x","diff":"--- a/x\n+++ b/x\n@@ -1 +1 @@\n-a\n+b\n"}""",
        )
        val kinds = ServeChatActions.diffOf(answer)!!.lines.map { it.kind }
        assertEquals(
            listOf(DiffLineKind.FILE, DiffLineKind.FILE, DiffLineKind.HUNK, DiffLineKind.REMOVE, DiffLineKind.ADD),
            kinds,
        )
    }

    @Test
    fun `un diff absent reste absent, il ne devient pas une modification vide`() {
        // `rollback.diff` qui ne donne rien : `null` — l'écran dira « pas de diff ».
        assertNull(ServeChatActions.diffOf(JsonNull))
        assertNull(ServeChatActions.diffOf(json.parseToJsonElement("""{"stat":"","diff":""}""")))
        // Et un diff vide n'a **pas** de ligne de compte (jamais « 0 insertion »).
        assertNull(ServeChatActions.diffOf(json.parseToJsonElement("""{"stat":"","diff":""}""")))
    }

    // -- Processus : pid, chemin, sortie -------------------------------------------------------

    @Test
    fun `un processus se lit avec son pid, son chemin et sa sortie`() {
        val answer = json.parseToJsonElement(
            """
            {"processes":[
              {"session_id":"proc_live","command":"python3 serve.py","status":"running","uptime_seconds":12,
               "pid":4242,"cwd":"/srv/app","output_tail":"listening on :8080\nready"}]}
            """.trimIndent(),
        )
        val process = ServeChatActions.processesOf(answer).single()
        assertEquals(4242, process.pid)
        assertEquals("/srv/app", process.cwd)
        assertEquals("listening on :8080\nready", process.output)
        assertEquals("12 s · pid 4242 · /srv/app", process.metaLine())
    }

    @Test
    fun `un pid absent ne s invente pas et la ligne reste honnete`() {
        val answer = json.parseToJsonElement(
            """{"processes":[{"session_id":"p","command":"sleep 1","status":"running","uptime_seconds":9}]}""",
        )
        val process = ServeChatActions.processesOf(answer).single()
        assertNull(process.pid)
        assertNull(process.cwd)
        assertNull(process.output)
        // La ligne ne porte que ce qui existe : ni « pid — », ni « 0 ».
        assertEquals("9 s", process.metaLine())
    }

    @Test
    fun `la sortie d un processus vient du serveur, jamais d une reconstruction`() {
        // `output_preview` quand il n'y a pas de `output_tail`.
        val preview = json.parseToJsonElement(
            """{"processes":[{"session_id":"p","command":"x","status":"exited","exit_code":0,"output_preview":"done"}]}""",
        )
        assertEquals("done", ServeChatActions.processesOf(preview).single().output)
        // Rien du tout : `null`, pas une chaîne vide.
        val none = json.parseToJsonElement("""{"processes":[{"session_id":"p","command":"x","status":"exited"}]}""")
        assertNull(ServeChatActions.processesOf(none).single().output)
    }

    // -- Compte : usage.bars -------------------------------------------------------------------

    @Test
    fun `le compte se lit quand le serveur le publie`() {
        val answer = json.parseToJsonElement(
            """
            {"ok":true,"available":true,"plan_name":"Pro","renews_display":"12 nov. 2026",
             "plan_bar":{"pct_used":42,"spent_display":"$4.20","total_display":"$10.00","remaining_display":"$5.80"}}
            """.trimIndent(),
        )
        val account = ServeChatActions.accountOf(answer)
        assertTrue(account.available)
        assertEquals("Pro", account.planName)
        assertEquals("12 nov. 2026", account.renewsDisplay)
        assertEquals(42, account.planPercent)
    }

    @Test
    fun `un compte indisponible reste indisponible, jamais a zero pour cent`() {
        assertFalse(ServeChatActions.accountOf(json.parseToJsonElement("""{"ok":true,"available":false}""")).available)
        assertFalse(ServeChatActions.accountOf(json.parseToJsonElement("""{"available":false}""")).available)
        assertFalse(ServeChatActions.accountOf(JsonNull).available)
        // Le pourcentage d'un compte absent n'est pas 0 : il est `null`.
        assertNull(ServeChatActions.accountOf(JsonNull).planPercent)
    }

    // -- Les formats : ce qui manque reste un tiret --------------------------------------------

    @Test
    fun `une taille de fichier se dit en francais, ou pas du tout`() {
        assertNull(ServeChatActions.fileSize(null))
        assertNull(ServeChatActions.fileSize(-1))
        assertEquals("663 octets", ServeChatActions.fileSize(663))
        assertEquals("2,0 ko", ServeChatActions.fileSize(2048))
        assertEquals("1,0 Mo", ServeChatActions.fileSize(1_048_576))
    }

    @Test
    fun `un fichier lu sans taille ne porte pas de zero invente`() {
        val breakdown = json.parseToJsonElement(
            """{"context_files":[{"label":"SOUL.md","path":"/x","est_tokens":167,"loaded":true}]}""",
        )
        val file = ServeChatActions.contextFilesOf(breakdown).single()
        assertEquals("SOUL.md", file.label)
        assertNull("la taille absente doit rester nulle", file.chars)
        assertNull(ServeChatActions.fileSize(file.chars))
    }

    @Test
    fun `le court identifiant d un point tient en six caracteres`() {
        val point = RestorePoint(hash = "a1b2c3d4e5f6", timestamp = "2026-10-27 09:12", message = "avant backup.sh")
        assertEquals("a1b2c3", point.shortHash)
    }

    @Test
    fun `un point sans nom garde son identifiant court`() {
        val list = ServeChatActions.restorePointsOf(
            json.parseToJsonElement(
                """{"enabled":true,"checkpoints":[{"hash":"deadbeefcafe","timestamp":"2026-10-27 09:12","message":""}]}""",
            ),
        )
        assertEquals(1, list.items.size)
        assertEquals("deadbe", list.items.single().shortHash)
        assertTrue(list.items.single().message.isBlank())
    }
}
