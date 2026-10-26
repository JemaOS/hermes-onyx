package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le jeton tel qu'il est collé.
 *
 * Le champ de saisie est étroit, la valeur se copie depuis un terminal: elle arrive avec des
 * blancs, des guillemets, un `Bearer ` que la documentation montrait et que l'app ajoute elle
 * aussi. Refuser la connexion pour ces restes serait une panne inventée — et le pont, lui, les
 * tolère déjà.
 */
class ComputerTokenTest {

    private val token = "246d2yiWnd3RjeqSRZqV"

    @Test
    fun `un jeton colle garde ses restes et perd tout ce qui n'est pas lui`() {
        val cases = mapOf(
            token to token,
            "  $token  " to token,
            "\n$token\n" to token,
            "\"$token\"" to token,
            "'$token'" to token,
            "$token\u200b" to token,
            "Bearer $token" to token,
            "bearer $token" to token,
            "Bearer Bearer $token" to token,
            "token=$token" to token,
            "Bearer token=$token" to token,
        )
        cases.forEach { (raw, expected) -> assertEquals("pour $raw", expected, ComputerToken.clean(raw)) }
    }

    @Test
    fun `un blanc a l'interieur n'est jamais retire en douce`() {
        val malformed = "246d2yiWnd3 RjeqSRZqV"
        assertEquals(malformed, ComputerToken.clean(malformed))
        assertTrue(ComputerToken.looksMalformed(malformed))
        assertFalse(ComputerToken.looksMalformed(token))
        assertTrue(ComputerToken.looksMalformed(""))
    }

    @Test
    fun `un jeton manifestement tronque est vu avant l'envoi`() {
        assertTrue(ComputerToken.obviouslyTooShort("abc"))
        assertFalse(ComputerToken.obviouslyTooShort(token))
        // Le jeton du pont fait vingt caractères: un reste de collage de huit ne peut pas être lui.
        assertFalse(ComputerToken.obviouslyTooShort("12345678"))
    }
}
