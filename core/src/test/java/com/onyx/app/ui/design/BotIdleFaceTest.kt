package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La vie de repos d'un visage : sa **règle**, vérifiée sans écran.
 *
 * Ce qui se contrôle ici, ce sont les trois choses qu'un `Random` caché rendrait invérifiables :
 * les **intervalles** (dans la plage), les **poids** (le clignement plus fréquent qu'un hochement),
 * et la **reproductibilité** (même graine, même séquence). Le rendu, lui, se juge en capture.
 */
class BotIdleFaceTest {

    /** Quelques graines, dont celles de deux bots réels par leur nom. */
    private val seeds = listOf(
        BotIdle.seedFor("default"),
        BotIdle.seedFor("scout"),
        BotIdle.seedFor("quill", "1:egg:round:smile:none:4:2:1"),
        0x5EEDL,
        -1L,
    )

    @Test
    fun `les poids font cent, et le clignement passe avant tout`() {
        assertEquals("les poids se lisent comme des pourcentages", 100, BotIdleCue.totalWeight)
        val weights = BotIdleCue.entries.map { it.weight }
        assertEquals("du plus fréquent au plus rare", weights.sortedDescending(), weights)
        assertEquals(40, BotIdleCue.BLINK.weight)
        assertEquals(8, BotIdleCue.NOD.weight)
        assertTrue("le clignement est le plus fréquent", BotIdleCue.BLINK.weight > BotIdleCue.GLANCE.weight)
        assertTrue("le hochement est le plus rare", BotIdleCue.NOD.weight < BotIdleCue.SMILE.weight)
    }

    @Test
    fun `chaque animation a sa duree, et le clignement est le plus bref`() {
        val durations = BotIdleCue.entries.map { it.durationMillis }
        assertTrue("tout pouls est court", durations.all { it in 100L..3_000L })
        assertEquals(170L, BotIdleCue.BLINK.durationMillis)
        assertEquals("le clignement est le plus bref", durations.min(), BotIdleCue.BLINK.durationMillis)
        assertTrue("la respiration est la plus longue", durations.max() == BotIdleCue.BREATH.durationMillis)
    }

    @Test
    fun `le tirage couvre toute la plage des poids, sans trou ni debordement`() {
        // Une animation dont le poids est w doit sortir pour w tirages consécutifs exactement.
        var draw = 0
        for (cue in BotIdleCue.entries) {
            repeat(cue.weight) {
                assertEquals("tirage $draw", cue, BotIdleCue.forDraw(draw))
                draw += 1
            }
        }
        assertEquals(100, draw)
        // Le tirage boucle, et un négatif retombe sur la même animation que son équivalent positif.
        assertEquals(BotIdleCue.forDraw(0), BotIdleCue.forDraw(100))
        assertEquals(BotIdleCue.forDraw(7), BotIdleCue.forDraw(-93))
    }

    @Test
    fun `les intervalles tiennent dans la plage, bornes comprises`() {
        for (seed in seeds) {
            val gaps = BotIdle.schedule(seed, 2_000).map { it.gapMillis }
            assertTrue("graine $seed : rien sous 4 s", gaps.min() >= BotIdle.MIN_GAP_MS)
            assertTrue("graine $seed : rien au-dessus de 9 s", gaps.max() <= BotIdle.MAX_GAP_MS)
            assertTrue("graine $seed : les deux bornes sont atteintes", gaps.min() < 4_200L && gaps.max() > 8_800L)
        }
    }

    @Test
    fun `les pouls ne se chevauchent pas et laissent toujours une pause`() {
        for (seed in seeds) {
            val schedule = BotIdle.schedule(seed, 500)
            schedule.zipWithNext().forEach { (before, after) ->
                assertTrue("aucun chevauchement", after.startMillis >= before.endMillis)
                assertEquals("l'attente sépare bien les deux", after.gapMillis, after.startMillis - before.startMillis)
            }
            // Le visage est au repos la plus grande partie du temps : c'est ce qui le rend sobre.
            val busy = schedule.sumOf { it.durationMillis }.toFloat()
            val span = schedule.last().endMillis.toFloat()
            assertTrue("le visage bouge moins d'un sixième du temps (${busy / span})", busy / span < 0.15f)
        }
    }

    @Test
    fun `les poids sont respectes sur une longue suite`() {
        val count = 5_000
        for (seed in seeds.take(3)) {
            val schedule = BotIdle.schedule(seed, count)
            val counts = BotIdleCue.entries.associateWith { cue -> schedule.count { it.cue == cue } }
            for (cue in BotIdleCue.entries) {
                val expected = cue.weight * count / 100.0
                val got = counts.getValue(cue).toDouble()
                assertTrue(
                    "graine $seed, ${cue.frLabel} : $got pour un attendu de $expected",
                    Math.abs(got - expected) <= expected * 0.25,
                )
            }
            // L'ordre des fréquences suit l'ordre des poids.
            val ordered = BotIdleCue.entries.sortedByDescending { counts.getValue(it) }
            assertEquals("le clignement reste le plus fréquent", BotIdleCue.BLINK, ordered.first())
        }
    }

    @Test
    fun `une meme graine rejoue exactement la meme sequence`() {
        val seed = BotIdle.seedFor("scout")
        val once = BotIdle.schedule(seed, 40)
        val twice = BotIdle.schedule(seed, 40)
        assertEquals("rejoué à l'identique", once, twice)
        // Les valeurs elles-mêmes sont figées : une régression du tirage se voit ici.
        val expected = listOf(
            "0 SMILE 4470@4470..5670",
            "1 BLINK 4524@8994..9164",
            "2 GLANCE 7780@16774..17534",
            "3 GLANCE 7402@24176..24936",
            "4 SMILE 4833@29009..30209",
        )
        assertEquals(
            expected,
            once.take(5).map { "${it.index} ${it.cue.name} ${it.gapMillis}@${it.startMillis}..${it.endMillis}" },
        )
    }

    @Test
    fun `deux bots ne tirent pas la meme vie`() {
        val scout = BotIdle.schedule(BotIdle.seedFor("scout"), 20)
        val quill = BotIdle.schedule(BotIdle.seedFor("quill"), 20)
        assertNotEquals("les graines diffèrent", BotIdle.seedFor("scout"), BotIdle.seedFor("quill"))
        assertNotEquals("et les séquences aussi", scout, quill)
        // Le code du visage entre dans la graine : deux visages du même nom ne battent pas ensemble.
        assertNotEquals(
            BotIdle.seedFor("scout"),
            BotIdle.seedFor("scout", "1:round:round:smile:none:2:1:0"),
        )
        // Le même nom redonne la même graine, session après session.
        assertEquals(BotIdle.seedFor("scout"), BotIdle.seedFor("scout", null))
    }

    @Test
    fun `le pouls d un instant est exactement celui du plan`() {
        val seed = BotIdle.seedFor("default")
        val plan = BotIdle.schedule(seed, 100)
        // Avant le premier pouls, le visage est au repos.
        assertNull("rien au tout début", BotIdle.pulseAt(seed, 0L))
        assertNull("rien juste avant le premier pouls", BotIdle.pulseAt(seed, plan.first().startMillis - 1L))
        for (pulse in plan) {
            assertEquals("au premier instant du pouls", pulse, BotIdle.pulseAt(seed, pulse.startMillis))
            assertEquals("et encore au dernier", pulse, BotIdle.pulseAt(seed, pulse.endMillis - 1L))
            assertNull("après lui, le repos", BotIdle.pulseAt(seed, pulse.endMillis))
        }
    }

    @Test
    fun `un pouls part et revient exactement au repos`() {
        val rest = BotPose(tilt = -3f)
        for (seed in seeds.take(3)) {
            for (pulse in BotIdle.schedule(seed, 30)) {
                val start = botIdleFrame(pulse, 0f, tilt = -3f, base = BotMood.IDLE)
                val end = botIdleFrame(pulse, 1f, tilt = -3f, base = BotMood.IDLE)
                assertEquals("${pulse.cue} : le début est au repos", rest, start.pose)
                assertEquals("${pulse.cue} : la fin est au repos", rest, end.pose)
                assertEquals("le début garde l'inclinaison du visage", -3f, start.pose.tilt)
            }
        }
    }

    @Test
    fun `un clignement ferme les deux yeux et les rouvre`() {
        val blink = BotIdle.schedule(BotIdle.seedFor("scout"), 40).first { it.cue == BotIdleCue.BLINK }
        val middle = botIdleFrame(blink, 0.5f)
        assertEquals("les yeux sont fermés au milieu", 1f, middle.pose.blink, 0.0001f)
        assertEquals("rien d'autre ne bouge", 0f, middle.pose.squint, 0.0001f)
        assertEquals(0f, middle.pose.breath, 0.0001f)
        assertNull("le regard reste celui du visage", middle.pose.look)
        assertEquals("et l'humeur ne change pas", BotMood.IDLE, middle.mood)
    }

    @Test
    fun `le regard part de cote puis revient, et change d'oeil`() {
        val glances = BotIdle.schedule(BotIdle.seedFor("quill"), 300).filter { it.cue == BotIdleCue.GLANCE }
        // Le côté alterne d'un pouls à l'autre : le visage ne lorgne pas toujours du même œil.
        val sides = glances.take(20).map { if (it.index % 2 == 0) 1 else -1 }
        assertEquals("les deux côtés servent", 2, sides.toSet().size)
        assertTrue(
            "et il change de côté souvent",
            sides.zipWithNext().count { (a, b) -> a != b } >= 5,
        )
        // Le regard part tôt, et il est revenu avant la fin : la pose finale est le repos.
        val first = glances.first()
        assertEquals(BotIdle.GLANCE_REACH, Math.abs(botIdleFrame(first, 0.4f).pose.look!!.x), 0.0001f)
        assertTrue("il part vite", Math.abs(botIdleFrame(first, 0.11f).pose.look!!.x) > 0.6f)
        assertNull("et il est revenu à la fin", botIdleFrame(first, 1f).pose.look)
        // Le regard ne dépasse jamais l'échelle attendue par le moteur (−1 … 1).
        for (pulse in glances) {
            for (step in 0..40) {
                val look = botIdleFrame(pulse, step / 40f).pose.look ?: continue
                assertTrue("le regard reste borné", Math.abs(look.x) <= 1f && Math.abs(look.y) <= 1f)
            }
        }
    }

    @Test
    fun `la respiration monte puis redescend, et rien ne sort de l'echelle`() {
        val breath = BotIdle.schedule(BotIdle.seedFor("default"), 400).first { it.cue == BotIdleCue.BREATH }
        val up = botIdleFrame(breath, 0.25f).pose.breath
        val down = botIdleFrame(breath, 0.75f).pose.breath
        assertTrue("le corps monte d'abord", up > 0f)
        assertTrue("puis redescend sous son repos", down < 0f)
        assertEquals("l'amplitude reste celle de la scène", BotIdle.BREATH_AMPLITUDE, up, 0.0001f)
        for (pulse in BotIdle.schedule(BotIdle.seedFor("default"), 200)) {
            for (step in 0..30) {
                val pose = botIdleFrame(pulse, step / 30f).pose
                assertTrue("la respiration reste bornée", Math.abs(pose.breath) <= 1f)
                assertTrue("les paupières restent bornées", Math.abs(pose.squint) <= 1f)
                assertTrue("le clignement reste borné", Math.abs(pose.blink) <= 1f)
                assertTrue("les sourcils restent bornés", Math.abs(pose.brow) <= 1f)
            }
        }
    }

    @Test
    fun `le micro-sourire passe par la pose contente du moteur`() {
        val smile = BotIdle.schedule(BotIdle.seedFor("scout"), 200).first { it.cue == BotIdleCue.SMILE }
        val frame = botIdleFrame(smile, 0.5f, base = BotMood.IDLE)
        assertEquals("les yeux sourient", BotMood.PLEASED, frame.mood)
        // La pose elle-même ne touche à rien : c'est la pose « content » du moteur qui dessine.
        assertNull(frame.pose.look)
        assertEquals(0f, frame.pose.blink, 0.0001f)
        assertEquals(0f, frame.pose.breath, 0.0001f)
        // Et un bot qui n'est pas au repos garde son humeur : un sourire de repos ne l'écrase pas.
        assertEquals(BotMood.THINKING, botIdleFrame(smile, 0.5f, base = BotMood.THINKING).mood)
    }

    @Test
    fun `le hochement penche la tete depuis l'inclinaison du visage`() {
        val nod = BotIdle.schedule(BotIdle.seedFor("quill"), 400).first { it.cue == BotIdleCue.NOD }
        val middle = botIdleFrame(nod, 0.5f, tilt = 2.5f)
        assertEquals("la tête penche autour de l'inclinaison du visage", 2.5f + BotIdle.NOD_DEGREES, middle.pose.tilt!!, 0.0001f)
        assertTrue("un rien de sourcil", middle.pose.brow > 0f)
        assertEquals("la fin revient à l'inclinaison du visage", 2.5f, botIdleFrame(nod, 1f, tilt = 2.5f).pose.tilt!!, 0.0001f)
    }

    @Test
    fun `un appui court rend content, un appui long surprend`() {
        val tap = BotPress.reaction(long = false)
        assertEquals("l'appui court : content", BotMood.PLEASED, tap.mood)
        assertEquals(620L, tap.durationMillis)
        assertTrue("il rebondit", tap.bounce)
        val hold = BotPress.reaction(long = true)
        assertNotEquals("l'appui long donne une autre pose", tap.mood, hold.mood)
        assertEquals("la surprise est la pose d'écoute du moteur", BotMood.LISTENING, hold.mood)
        assertTrue("elle tient plus longtemps", hold.durationMillis > tap.durationMillis)
    }

    @Test
    fun `sous Reduce Motion la reaction ne rebondit pas`() {
        val tap = BotPress.reaction(long = false, reduceMotion = true)
        assertEquals(BotMood.PLEASED, tap.mood)
        assertTrue("aucun rebond : la bascule est nette", !tap.bounce)
        assertTrue("et l'appui long non plus", !BotPress.reaction(long = true, reduceMotion = true).bounce)
    }

    @Test
    fun `le rebond part du repos et y revient`() {
        assertEquals("rien au départ", BotPose(tilt = 0f), botPressPose(0f))
        assertEquals("rien à l'arrivée", BotPose(tilt = 0f), botPressPose(1f))
        assertTrue("il monte tôt", botPressPose(0.15f).breath > 0.5f)
        assertTrue("il monte haut", botPressPose(0.25f).breath > 0.9f)
        assertTrue("et retombe", botPressPose(0.8f).breath < botPressPose(0.25f).breath)
        assertEquals("l'inclinaison du visage est conservée", -3f, botPressPose(0.5f, tilt = -3f).tilt)
    }
}
