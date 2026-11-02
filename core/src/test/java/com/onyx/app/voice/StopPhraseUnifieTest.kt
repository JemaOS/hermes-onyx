package com.onyx.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordre d'arrêt : **une seule règle**, du début à la fin.
 *
 * Avant, il y avait **deux** règles, et chacune ignorait ce que l'autre savait :
 *
 * - pendant l'**écoute**, le transcript **entier** était comparé à la liste : « arrête
 *   d'écouter » ne passait que si c'était la seule chose dite, et « arrête d'écouter arrête »
 *   ne passait jamais ;
 * - pendant la **lecture**, seule la liste de [StopWord] était lue : « stop » et les phrases
 *   de la personne, mais **jamais** « arrête d'écouter ».
 *
 * Résultat : un ordre d'arrêt en français ne marchait pas pendant que le bot parlait, et un
 * ordre d'arrêt un peu long ne marchait jamais du tout.
 *
 * La règle tient désormais : les phrases **longues** (deux mots ou plus) se repèrent n'importe
 * où dans ce qui a été dit, les phrases **courtes** (un mot) seulement si elles sont tout ce
 * qui a été dit — « l'auto s'arrête » ne doit pas arrêter l'écoute — et la négation protège des
 * deux côtés (« ne t'arrête pas », « arrête pas »).
 */
class StopPhraseUnifieTest {

    /** La règle de l'écoute : c'est la même que celle de la lecture. */
    private fun ecoute(texte: String, phrases: List<String> = emptyList()): Boolean =
        StopWord.heardIn(texte, phrases)

    @Test
    fun `l ordre d arret en francais marche pendant la lecture`() {
        // Avant : la liste intégrée n'était pas lue par ce chemin — rien ne passait.
        assertTrue("« arrête d'écouter » doit arrêter", ecoute("arrête d'écouter"))
        assertTrue("« arrête » seul doit arrêter", ecoute("arrête"))
        assertTrue("« ça suffit » doit arrêter", ecoute("ça suffit"))
        assertTrue("« c'est tout » doit arrêter", ecoute("c'est tout"))
    }

    @Test
    fun `l ordre d arret en anglais marche aussi`() {
        assertTrue(ecoute("stop"))
        assertTrue(ecoute("stop listening"))
        assertTrue(ecoute("that's all"))
        assertTrue(ecoute("goodbye"))
    }

    @Test
    fun `une phrase longue se repere n importe ou`() {
        assertTrue("« arrête d'écouter » dit dans une phrase", ecoute("arrête d'écouter maintenant"))
        assertTrue("avec une ponctuation", ecoute("Arrête d'écouter."))
        assertTrue("avec des politesses autour", ecoute("onyx arrête d'écouter"))
        assertTrue("avec des politesses après", ecoute("arrête d'écouter merci"))
    }

    @Test
    fun `une phrase courte ne vaut que seule`() {
        // « l'auto s'arrête » n'est pas un ordre d'arrêt : le mot seul ne suffit pas au milieu
        // d'une phrase.
        assertFalse("« l'auto s'arrête » n'est pas un ordre d'arrêt", ecoute("l'auto s'arrête"))
        assertFalse("« il s'arrête » non plus", ecoute("il s'arrête"))
        assertTrue("mais « arrête » seul l'est", ecoute("arrête"))
    }

    @Test
    fun `la negation protege des deux cotes`() {
        assertFalse("« ne t'arrête pas »", ecoute("ne t'arrête pas"))
        assertFalse("« n'arrête pas »", ecoute("n'arrête pas"))
        assertFalse("« arrête pas »", ecoute("arrête pas"))
        assertFalse("« don't stop »", ecoute("don't stop"))
        assertFalse("« ne dis pas stop »", ecoute("ne dis pas stop"))
    }

    @Test
    fun `les phrases de la personne comptent aussi`() {
        assertTrue(ecoute("coupe tout", listOf("coupe tout")))
        assertTrue("dites au milieu", ecoute("coupe tout maintenant", listOf("coupe tout")))
    }

    @Test
    fun `une phrase ordinaire ne declenche rien`() {
        assertFalse(ecoute("quelle est la météo demain"))
        assertFalse(ecoute("je voudrais un café"))
        assertFalse(ecoute("arrêt de la station"))
    }
}
