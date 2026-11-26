package com.onyx.app.data.net

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le canal du Dashboard : **une seule** connexion, et elle revient toute seule.
 *
 * Deux défauts, tous deux visibles depuis le téléphone.
 *
 * **1. Deux sockets pour le même canal.** `connect()` est `suspend` et fait des allers-retours ;
 * rien ne l'empêchait de tourner à deux. Deux coroutines l'appelaient en même temps, ouvraient
 * **deux** WebSocket, et la seconde écrasait la première (`socket = webSocket`) : la socket
 * jetée restait ouverte côté serveur, et la première coroutine attendait un `opened` que plus
 * personne ne compléterait. Le journal du relais le montre : cinq `ws: tunnel ouvert` en deux
 * secondes. Le verrou rend l'ouverture unique.
 *
 * **2. Une coupure était définitive.** Le tunnel Cloudflare coupe les connexions longues quand
 * le réseau change ; le canal ne revenait qu'à la prochaine action manuelle. En pleine
 * conversation vocale, c'est le tour qui meurt. Le canal se rouvre maintenant tout seul, et
 * **borné** : une reprise qui ne marche pas veut dire que le serveur est down, et mieux vaut
 * une coupure franche qu'une boucle sans fin.
 */
class ServeRpcConnectionLockTest {

    private val source: String by lazy {
        val file = File("src/main/java/com/onyx/app/data/net/ServeRpc.kt")
        assertTrue("la source du canal est introuvable : " + file.absolutePath, file.exists())
        file.readText()
    }

    @Test
    fun `l ouverture du canal passe par un verrou`() {
        assertTrue(
            "le canal doit avoir un verrou d'ouverture : " +
                "sans lui, deux coroutines ouvrent deux sockets et l une des deux est perdue",
            Regex("""private val connectMutex\s*=\s*Mutex\(\)""").containsMatchIn(source),
        )
        assertTrue(
            "l'ouverture doit se faire sous le verrou",
            Regex("""connectMutex\.withLock\s*\{""").containsMatchIn(source),
        )
    }

    @Test
    fun `l ouverture est unique, on revit avant d ouvrir`() {
        // Sans ce second garde-fou, une coroutine qui attendait le verrou ouvrirait quand même
        // un socket alors que le premier vient d'ouvrir le sien.
        assertTrue(
            "le verrou doit revérifier l'etat du canal avant d'ouvrir : " +
                "sinon le second appelant ouvre un socket de trop",
            Regex("""connectMutex\.withLock\s*\{[^}]*?if\s*\(\s*isReady\s*\)\s*return""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source),
        )
    }

    @Test
    fun `une coupure declenche une reprise`() {
        assertTrue(
            "la coupure doit declencher la reprise du canal",
            Regex("""scope\.launch\s*\{\s*reconnect\(\)\s*\}""").containsMatchIn(source),
        )
    }

    @Test
    fun `la reprise est bornee`() {
        assertTrue(
            "la reprise doit etre bornee : sinon l'app boucle sur un serveur mort",
            Regex("""RECONNECT_ATTEMPTS\s*=\s*\d+""").containsMatchIn(source),
        )
        assertTrue(
            "la reprise doit attendre entre deux essais",
            Regex("""RECONNECT_DELAY_MS\s*=\s*\d+""").containsMatchIn(source),
        )
    }

    @Test
    fun `la reprise annonce la fin de la coupure`() {
        // `connection.lost` dit « ca va peut-etre revenir », `connection.restored` dit « ca y est ».
        // Sans le second, l'interface ne peut pas distinguer les deux.
        assertTrue(
            "la reprise doit annoncer le retour du canal",
            Regex(""""connection\.restored"""").containsMatchIn(source),
        )
    }
}
