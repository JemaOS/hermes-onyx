package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La déduction du schéma, et le jeton tel qu'on le colle.
 *
 * Le pont local vit sur un **port explicite** (`192.168.1.83:8795`) et parle en clair : `ws://`.
 * Un **nom de tunnel** (`xxx.trycloudflare.com`) n'a pas de port, et il est derrière un
 * terminateur TLS : `wss://`. Ramener un nom de tunnel à `ws://` envoie la poignée de main sur le
 * port 80, où l'edge répond par une redirection HTTP — l'app n'ouvre alors jamais sa session et
 * l'écran « Ordinateur » reste sur « le pont n'est pas connecté ». C'est le défaut que ces cas
 * verrouillent.
 */
class ComputerAddressTest {

    @Test
    fun `un nom de tunnel sans port se rejoint en TLS`() {
        assertEquals(
            "wss://give-overnight-reveal-nano.trycloudflare.com/ws",
            ComputerAddress.websocketUrl("give-overnight-reveal-nano.trycloudflare.com"),
        )
        // La casse d'un nom d'hôte ne change rien : le schéma déduit reste le même.
        assertEquals(
            "wss://GiVe-OvErNiGhT.TryCloudFlare.CoM/ws",
            ComputerAddress.websocketUrl("GiVe-OvErNiGhT.TryCloudFlare.CoM"),
        )
    }

    @Test
    fun `un hote avec un port explicite est le pont local, en clair`() {
        assertEquals("ws://192.168.1.83:8795/ws", ComputerAddress.websocketUrl("192.168.1.83:8795"))
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("10.0.2.2:8795"))
        assertEquals("ws://localhost:8795/ws", ComputerAddress.websocketUrl("localhost:8795"))
        // Un littéral IPv6 : le port est celui qui suit le crochet fermant.
        assertEquals("ws://[::1]:8795/ws", ComputerAddress.websocketUrl("[::1]:8795"))
        assertEquals("wss://[2001:db8::1]/ws", ComputerAddress.websocketUrl("[2001:db8::1]"))
    }

    @Test
    fun `un schema ecrit a la main est respecte`() {
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("http://10.0.2.2:8795"))
        assertEquals("wss://pc.exemple.fr/ws", ComputerAddress.websocketUrl("https://pc.exemple.fr"))
        assertEquals("ws://10.0.2.2:8795/ws", ComputerAddress.websocketUrl("ws://10.0.2.2:8795"))
        assertEquals("wss://pc.exemple.fr/ws", ComputerAddress.websocketUrl("wss://pc.exemple.fr"))
        // Un schéma explicite gagne sur la déduction : `https` reste du TLS même avec un port.
        assertEquals("wss://pc.exemple.fr:8443/ws", ComputerAddress.websocketUrl("https://pc.exemple.fr:8443"))
        assertEquals("ws://pc.exemple.fr:8080/ws", ComputerAddress.websocketUrl("ws://pc.exemple.fr:8080"))
    }

    @Test
    fun `un chemin, une barre finale et des espaces ne changent pas le schema`() {
        assertEquals(
            "wss://reveler.trycloudflare.com/ws",
            ComputerAddress.websocketUrl("reveler.trycloudflare.com/"),
        )
        assertEquals(
            "wss://reveler.trycloudflare.com/ordinateur/ws",
            ComputerAddress.websocketUrl("reveler.trycloudflare.com/ordinateur/"),
        )
        assertEquals(
            "wss://reveler.trycloudflare.com/ws",
            ComputerAddress.websocketUrl("  reveler.trycloudflare.com  "),
        )
        assertEquals(
            "wss://reveler.trycloudflare.com/ws",
            ComputerAddress.websocketUrl("reveler.trycloudflare.com/ws"),
        )
        assertEquals(
            "wss://pc.exemple.fr/ordinateur/ws",
            ComputerAddress.websocketUrl("https://pc.exemple.fr/ordinateur"),
        )
        // Une barre multiple ne doit pas fabriquer un chemin cassé.
        assertEquals(
            "wss://pc.exemple.fr/ordinateur/ws",
            ComputerAddress.websocketUrl("pc.exemple.fr//ordinateur"),
        )
    }

    @Test
    fun `une adresse vide ou absurde ne donne rien`() {
        assertEquals(null, ComputerAddress.websocketUrl(""))
        assertEquals(null, ComputerAddress.websocketUrl("   "))
        assertEquals(null, ComputerAddress.websocketUrl("http://"))
        assertEquals(null, ComputerAddress.websocketUrl("https://"))
        assertEquals(null, ComputerAddress.websocketUrl("wss://"))
        assertEquals(null, ComputerAddress.websocketUrl("http://un hote"))
        assertEquals(null, ComputerAddress.websocketUrl("/ws"))
    }

    @Test
    fun `un jeton colle perd les blancs autour et un Bearer en trop`() {
        assertEquals("abc123", ComputerToken.normalize("abc123"))
        assertEquals("abc123", ComputerToken.normalize("  abc123  "))
        // Le copier-coller colle souvent le saut de ligne du fichier, et parfois le préfixe.
        assertEquals("abc123", ComputerToken.normalize("abc123\n"))
        assertEquals("abc123", ComputerToken.normalize("\n  abc123 \r\n"))
        assertEquals("abc123", ComputerToken.normalize("Bearer abc123"))
        assertEquals("abc123", ComputerToken.normalize("bearer abc123"))
        assertEquals("abc123", ComputerToken.normalize("Bearer\tabc123"))
        assertEquals("abc123", ComputerToken.normalize("  Bearer abc123 \n"))
        // Un jeton qui n'est que des blancs ne vaut rien.
        assertEquals("", ComputerToken.normalize("   \n\t "))
        // `Bearer` tout seul n'est pas un préfixe : rien à retirer.
        assertEquals("Bearer", ComputerToken.normalize("Bearer"))
    }
}
