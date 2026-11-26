package com.onyx.app.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **nom du modèle** tel qu'il s'écrit : ce que le serveur sert, jamais l'alias de la passerelle.
 *
 * Le défaut d'origine, vu par la personne : la puce du compositeur disait `hermes-agent` — le nom du
 * tuyau que l'app appelle, pas celui du modèle qui répond. La règle est celle de l'app depuis le
 * début (le nom vient du serveur, `session.info` → champ `model`) ; la puce et la barre du haut
 * doivent la suivre, et **aucune** des deux ne peut peindre l'alias.
 */
class ModelNameTest {

    /** L'alias que l'app envoie. Il ne s'affiche jamais. */
    private val alias = "hermes-agent"

    @Test
    fun `le nom du serveur prime`() {
        assertTrue(
            "le nom servi doit s'écrire",
            ModelName.display("claude-sonnet-4.5", alias) == "claude-sonnet-4.5",
        )
    }

    @Test
    fun `l alias de passerelle ne s ecrit jamais`() {
        // C'est le cas de la capture : le serveur n'a rien dit encore, et le modèle configuré
        // est l'alias. La puce doit dire « Default model », pas `hermes-agent`.
        assertTrue(
            "l'alias ne doit pas s'afficher",
            ModelName.display(null, alias) == null,
        )
        // Et même si le serveur le renvoyait tel quel, c'est le même mensonge.
        assertTrue(
            "un alias servi ne s'affiche pas non plus",
            ModelName.display(alias, alias) == null,
        )
    }

    @Test
    fun `toutes les graphies de l alias sont reconnues`() {
        listOf("hermes-agent", " HERMES-AGENT ", "Hermes-Agent", "hermes", "gateway", "default", "")
            .forEach { value ->
                assertTrue("« $value » doit être vu comme un alias", ModelName.isAlias(value))
                assertTrue(
                    "« $value » ne doit pas s'afficher",
                    ModelName.display(null, value) == null,
                )
            }
    }

    @Test
    fun `un vrai nom configure s affiche quand le serveur se tait`() {
        assertTrue(
            "un modèle configuré qui n'est pas un alias s'écrit",
            ModelName.display(null, "gpt-5-codex") == "gpt-5-codex",
        )
    }

    @Test
    fun `un nom d alias n est jamais un nom de modele`() {
        // La phrase du test, en clair : quel que soit le chemin, la valeur rendue n'est pas l'alias.
        listOf(
            ModelName.display(null, alias),
            ModelName.display(alias, null),
            ModelName.display(alias, alias),
            ModelName.display("", alias),
        ).forEach { shown ->
            assertTrue("la valeur rendue ne peut pas être l'alias : $shown", shown != alias)
        }
    }
}
