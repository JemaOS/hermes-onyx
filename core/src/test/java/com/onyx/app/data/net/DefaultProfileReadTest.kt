package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Le profil du serveur se lit, même — et surtout — quand il n'a pas de nom.**
 *
 * `named()` traduit `""` et `"default"` en « pas de paramètre de profil », ce qui veut dire « le
 * profil du serveur ». Mais la boucle de lecture filtrait les chaînes vides **avant** d'appeler
 * `named()` : une liste qui ne portait que le profil par défaut devenait vide, la boucle ne
 * tournait pas, et la lecture rendait zéro session — alors que le serveur répondait `200` avec
 * quinze lignes. C'est ce qui a maintenu la liste vide après trois correctifs : la requête partait,
 * la réponse arrivait, et personne ne la lisait.
 */
class DefaultProfileReadTest {

    private val api = ServeSessionsApi(ServeClient(com.onyx.app.data.model.ServerConfig.blank("test")))

    @Test
    fun `une liste vide demande le profil du serveur`() {
        assertEquals(listOf(""), api.profilsLus(emptyList()))
    }

    @Test
    fun `le profil par defaut n est pas filtre`() {
        assertEquals(listOf(""), api.profilsLus(listOf("")))
    }

    @Test
    fun `default est garde - c est named qui le traduit`() {
        assertEquals(listOf("default"), api.profilsLus(listOf("default")))
    }

    @Test
    fun `les profils des bots restent lus un par un sans doublon`() {
        assertEquals(
            listOf("", "coder", "writer"),
            api.profilsLus(listOf("", "coder", " coder ", "writer")),
        )
    }
}
