package com.onyx.app.ui.chat.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La règle qui allège le défilement : un texte sans balisage ne prend pas de vue Android.**
 *
 * Chaque prose du fil était rendue par Markwon dans un `TextView` posé par `AndroidView` — une vue
 * du système par message, mesurée et dessinée à travers Compose à chaque image. La plupart des
 * réponses ne portent aucun balisage : celles-là passent maintenant par le texte de Compose.
 *
 * La règle doit être **prudente dans un seul sens** : un faux positif (une phrase contenant un
 * astérisque) coûte une vue Android, ce qui est sans conséquence ; un faux négatif afficherait des
 * astérisques ou des dièses en clair à l'écran, ce qui est un défaut visible. Ces tests tiennent
 * cette asymétrie.
 */
class MarkdownPlainRuleTest {

    @Test
    fun `une reponse ordinaire ne prend pas de vue Android`() {
        assertFalse(hasMarkup("Le dépôt est propre et les tests passent."))
        assertFalse(hasMarkup("J'ai relancé la suite deux fois, même résultat."))
        assertFalse(hasMarkup("Version 1.3.28 — rien d'autre n'a bougé."))
    }

    @Test
    fun `le balisage en ligne garde Markwon`() {
        assertTrue("gras", hasMarkup("Il y a **trois** points."))
        assertTrue("code en ligne", hasMarkup("Regarde `ChatScreen.kt`."))
        assertTrue("lien", hasMarkup("Voir [la doc](https://example.com)."))
        assertTrue("barre", hasMarkup("C'est ~~faux~~ vrai."))
        assertTrue("italique", hasMarkup("C'est *presque* fini."))
    }

    @Test
    fun `les blocs gardent Markwon`() {
        assertTrue("titre", hasMarkup("## Résultat"))
        assertTrue("citation", hasMarkup("> Une mesure vaut mieux qu'une intuition."))
        assertTrue("liste à puces", hasMarkup("- premier\n- second"))
        assertTrue("liste numérotée", hasMarkup("1. premier\n2. second"))
        assertTrue("tableau", hasMarkup("| a | b |\n|---|---|"))
        assertTrue("bloc de code", hasMarkup("```kotlin\nval a = 1\n```"))
        assertTrue("case à cocher", hasMarkup("- [x] fait"))
    }

    @Test
    fun `un texte vide ne demande rien`() {
        assertFalse(hasMarkup(""))
        assertFalse(hasMarkup("   "))
    }
}
