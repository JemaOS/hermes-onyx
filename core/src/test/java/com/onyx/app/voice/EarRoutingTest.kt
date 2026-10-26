package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Hold-to-ear »: le capteur de proximité fait jouer la réponse dans l'écouteur.
 *
 * Le routeur prend son capteur et son application de sortie par injection — c'est justement ce
 * qui permet de l'exercer **entièrement** ici, sans téléphone et sans capteur: le chemin est réel,
 * seule la plomberie Android est remplacée.
 */
class EarRoutingTest {

    // MARK: - La décision

    @Test
    fun `téléphone à l oreille et réglage allumé sortent par l écouteur`() {
        assertEquals(EarOutput.EARPIECE, earOutputFor(externalRoute = false, atEar = true, enabled = true))
    }

    @Test
    fun `réglage éteint ou téléphone loin de l oreille sortent par le haut-parleur`() {
        assertEquals(EarOutput.SPEAKER, earOutputFor(externalRoute = false, atEar = false, enabled = true))
        assertEquals(EarOutput.SPEAKER, earOutputFor(externalRoute = false, atEar = true, enabled = false))
    }

    @Test
    fun `un casque l emporte sur le capteur`() {
        assertEquals(EarOutput.LEAVE_ALONE, earOutputFor(externalRoute = true, atEar = true, enabled = true))
        assertEquals(EarOutput.LEAVE_ALONE, earOutputFor(externalRoute = true, atEar = false, enabled = true))
    }

    @Test
    fun `posé face contre la table, ce n est pas une oreille`() {
        assertTrue(atEarFor(near = true, faceDown = false))
        assertFalse(atEarFor(near = true, faceDown = true))
        assertFalse(atEarFor(near = false, faceDown = false))
    }

    // MARK: - Le routeur, capteur et sortie injectés

    private class FakeWatcher(override val isAvailable: Boolean = true) : ProximityWatcher {
        var starts = 0
        var stops = 0
        override fun start() { starts += 1 }
        override fun stop() { stops += 1 }
    }

    private class FakeRoute : EarRouteApplier {
        val applied = mutableListOf<EarOutput>()
        var restores = 0
        override fun apply(output: EarOutput) { applied += output }
        override fun restore() { restores += 1 }
    }

    @Test
    fun `le capteur ne s ouvre que pendant la lecture`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route)

        assertFalse(router.isWatching)
        assertEquals(0, watcher.starts)

        router.setWanted(true)
        assertTrue("une réponse se lit : le capteur s'ouvre", router.isWatching)
        assertEquals(1, watcher.starts)
        // Le capteur ne rend compte que d'un changement: sans mesure, on reste au haut-parleur.
        assertEquals(EarOutput.SPEAKER, route.applied.last())

        router.setWanted(false)
        assertFalse("la réponse est finie : le capteur se referme", router.isWatching)
        assertEquals(1, watcher.stops)
        assertNull("au repos, Onyx n'impose plus de sortie", router.lastApplied)
        assertEquals("la sortie est rendue au téléphone", 1, route.restores)
    }

    @Test
    fun `le téléphone levé à l oreille fait passer la réponse dans l écouteur puis revient`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route)
        router.setWanted(true)

        router.proximityChanged(near = true)
        assertTrue("levé à l'oreille", router.isAtEar)
        assertEquals(EarOutput.EARPIECE, route.applied.last())

        router.proximityChanged(near = false)
        assertFalse("reposé", router.isAtEar)
        assertEquals(EarOutput.SPEAKER, route.applied.last())

        router.setWanted(false)
        assertEquals("une seule fois : on n'impose rien au repos", 1, route.restores)
    }

    @Test
    fun `la sortie est annoncée à celui qui lit, à chaque changement`() {
        val route = FakeRoute()
        val router = ProximityEarRouter(FakeWatcher(), route)
        val announced = mutableListOf<Boolean>()
        router.onAtEarChanged = { announced += it }

        router.setWanted(true)
        router.proximityChanged(near = true)
        router.proximityChanged(near = true)
        router.proximityChanged(near = false)
        router.setWanted(false)

        assertEquals(listOf(false, true, true, false), announced)
    }

    @Test
    fun `une mesure arrivée hors lecture ne route rien`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route)
        router.proximityChanged(near = true)
        assertFalse(router.isAtEar)
        assertTrue(route.applied.isEmpty())
    }

    @Test
    fun `posé face contre la table reste au haut-parleur`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route, faceDown = { true })
        router.setWanted(true)
        router.proximityChanged(near = true)
        assertFalse(router.isAtEar)
        assertEquals(EarOutput.SPEAKER, route.applied.last())
    }

    @Test
    fun `un casque branché n est pas bousculé`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route, externalRoute = { true })
        router.setWanted(true)
        router.proximityChanged(near = true)
        assertEquals(EarOutput.LEAVE_ALONE, route.applied.last())
        router.setWanted(false)
        assertEquals(EarOutput.LEAVE_ALONE, route.applied.last())
    }

    @Test
    fun `le réglage éteint n ouvre jamais le capteur`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route, enabled = { false })
        router.setWanted(true)
        assertFalse(router.isWatching)
        assertEquals(0, watcher.starts)
        // Réglage éteint: Onyx ne touche pas à la sortie du téléphone, ni pour l'imposer ni
        // pour la rendre — il ne l'a jamais prise.
        assertTrue(route.applied.isEmpty())
        assertEquals(0, route.restores)
        assertNull(router.lastApplied)
    }

    @Test
    fun `un appareil sans capteur reste au haut-parleur sans casser`() {
        val watcher = FakeWatcher(isAvailable = false)
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route)
        router.setWanted(true)
        assertFalse(router.isWatching)
        assertEquals(0, watcher.starts)
        assertEquals(0, watcher.stops)
        // Un capteur absent n'est jamais ouvert: rien n'est appliqué ni rendu, la sortie du
        // téléphone reste celle de la lecture.
        assertNull(router.lastApplied)
        assertEquals(0, route.restores)
        assertTrue(route.applied.isEmpty())
    }

    @Test
    fun `release referme le capteur et rend la sortie`() {
        val watcher = FakeWatcher()
        val route = FakeRoute()
        val router = ProximityEarRouter(watcher, route)
        router.setWanted(true)
        router.proximityChanged(near = true)
        router.release()
        assertFalse(router.isWatching)
        assertFalse(router.isAtEar)
        assertEquals(1, watcher.stops)
        assertEquals(1, route.restores)
    }
}
