package com.onyx.app.ui.chat.components

import com.onyx.app.data.model.ApprovalRequest
import com.onyx.app.data.model.SubagentStatus
import com.onyx.app.data.model.ToolStatus
import com.onyx.app.ui.design.FlowState
import com.onyx.app.ui.design.OnyxSpace
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **La carte de contexte — un composant, plusieurs contenus.**
 *
 * Le défaut que ce test ferme : un appel d'outil, un sous-agent, une approbation, une page et un
 * diff étaient cinq objets différents — quatre rayons, trois filets, deux façons de replier, aucune
 * règle sur ce qui se met à gauche ou à droite. Ce qu'on vérifie ici :
 *
 *  1. **les mêmes règles, une seule fois** — le rayon ([ContextCardCorner]), le filet
 *     ([contextCardSurface]) et l'en-tête (état à gauche, action à droite) sont ceux de
 *     [ContextCard], et les **cinq** contenus y passent (verrou de source) ;
 *  2. **l'état est vrai** — chaque contenu dérive son état de sa donnée réelle
 *     ([toolCardState], [subagentCardState], [approvalCardState], [linkCardState],
 *     [patchCardState]) ;
 *  3. **aucun état d'alarme en couleur** — les deux états qui attendent quelque chose (travail,
 *     décision) partagent **le même accent** : c'est le mouvement qui les distingue ;
 *  4. **48 dp** — la hauteur d'en-tête est la cible tactile de la maison, et aucune action n'est
 *     dessinée quand il n'y a rien à faire (pas de bouton mort).
 */
class ContextCardTest {

    private val previousLocale = Locale.getDefault()

    @Before
    fun frenchInterface() {
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private fun source(path: String): String = File("src/main/java/com/onyx/app/$path").readText()

    @Test
    fun `quatre etats de carte, et le vrai libelle de chacun`() {
        assertEquals(
            listOf(
                ContextCardState.RUNNING,
                ContextCardState.DONE,
                ContextCardState.FAILED,
                ContextCardState.WAITING,
            ),
            ContextCardState.entries.toList(),
        )
        assertEquals("En cours", ContextCardState.RUNNING.label)
        assertEquals("Terminé", ContextCardState.DONE.label)
        assertEquals(
            "le mot de l'échec est celui de la maison (« Échec », comme l'écran des tâches) — " +
                "un état de carte ne se traduit pas à sa façon",
            "Échec",
            ContextCardState.FAILED.label,
        )
        assertEquals("En attente", ContextCardState.WAITING.label)
    }

    @Test
    fun `l etat d une carte est celui de sa donnee, jamais un ornement`() {
        // Un appel d'outil : son statut, tel que le transport l'a donné.
        assertEquals(ContextCardState.RUNNING, toolCardState(ToolStatus.RUNNING))
        assertEquals(ContextCardState.DONE, toolCardState(ToolStatus.COMPLETED))
        assertEquals(ContextCardState.FAILED, toolCardState(ToolStatus.FAILED))

        // Un enfant : vivant (il tourne ou il vient d'être lancé), fini, ou en échec.
        assertEquals(ContextCardState.RUNNING, subagentCardState(SubagentStatus.RUNNING))
        assertEquals(ContextCardState.RUNNING, subagentCardState(SubagentStatus.DISPATCHED))
        assertEquals(ContextCardState.DONE, subagentCardState(SubagentStatus.COMPLETED))
        assertEquals(ContextCardState.FAILED, subagentCardState(SubagentStatus.FAILED))

        // Une approbation : la seule carte qui puisse porter l'attente.
        assertEquals(ContextCardState.WAITING, approvalCardState(ApprovalRequest(id = "a1")))
        assertEquals(
            ContextCardState.DONE,
            approvalCardState(ApprovalRequest(id = "a1", decision = ApprovalRequest.CHOICE_DENY)),
        )

        // Une page : en cours tant qu'elle n'a pas été lue.
        assertEquals(ContextCardState.RUNNING, linkCardState(loaded = false))
        assertEquals(ContextCardState.DONE, linkCardState(loaded = true))

        // Un diff : il est là, complet, dès qu'il s'affiche.
        assertEquals(ContextCardState.DONE, patchCardState(files = 2))
        assertEquals(ContextCardState.FAILED, patchCardState(files = 0))
    }

    @Test
    fun `un travail et une attente partagent le meme accent, le mouvement les distingue`() {
        assertEquals(
            "aucun état n'a de couleur d'alarme : le travail et l'attente portent le même accent",
            ContextInk.ACCENT,
            ContextCardState.RUNNING.ink,
        )
        assertEquals(ContextInk.ACCENT, ContextCardState.WAITING.ink)
        assertEquals("un travail fini est un signal de la maison", ContextInk.SUCCESS, ContextCardState.DONE.ink)
        assertEquals("un échec est un signal de la maison", ContextInk.ERROR, ContextCardState.FAILED.ink)
    }

    @Test
    fun `seuls les etats vivants portent un mouvement, et ce sont ceux de la grammaire`() {
        assertEquals(
            "un travail en cours prend le mouvement du travail",
            FlowState.WORKING,
            ContextCardState.RUNNING.motion,
        )
        assertEquals(
            "une décision attendue prend le mouvement de l'attente",
            FlowState.DECIDING,
            ContextCardState.WAITING.motion,
        )
        assertNull("un travail fini ne bouge plus", ContextCardState.DONE.motion)
        assertNull("un travail échoué ne bouge plus", ContextCardState.FAILED.motion)
    }

    @Test
    fun `l en-tete d une carte est la cible tactile de la maison`() {
        assertEquals(
            "48 dp : la cible de la maison, et de quoi poser l'état **et** l'action",
            OnyxSpace.TouchTarget,
            ContextCardRowHeight,
        )
        assertTrue("au moins 48 dp", ContextCardRowHeight.value >= 48f)
    }

    @Test
    fun `les regles sont ecrites une seule fois, dans le composant`() {
        val card = source("ui/chat/components/ContextCard.kt")
        assertTrue(
            "le rayon de la maison vient du jeton, pas d'un littéral",
            card.contains("val ContextCardCorner: Dp = OnyxSpace.CardCorner"),
        )
        assertTrue(
            "le filet est celui de la maison, et il vire à l'accent quand la carte vit",
            card.contains("width = OnyxSpace.Stroke,") && card.contains("theme.accent.copy(alpha = 0.35f)"),
        )
        assertTrue("l'état est posé **à gauche**", card.contains("// 1. L'ÉTAT, À GAUCHE."))
        assertTrue("l'action est posée **à droite**", card.contains("// 4. L'ACTION, À DROITE"))
        assertTrue("le repli est celui de la maison", card.contains("OnyxMotion.Sites.MessageFold"))
        assertTrue(
            "aucune action ne se dessine quand il n'y en a pas : pas de bouton mort",
            card.contains("if (leading != null) leading() else ContextStateDot"),
        )
    }

    /**
     * **Les cinq contenus passent par le même composant.** C'est le cœur du concept : « pas un
     * nouvel objet par type : un composant, plusieurs contenus ». Ce verrou lit la source des cinq
     * sites — s'il en manque un, la phrase du rapport devient fausse.
     */
    @Test
    fun `les cinq contenus passent par le composant de la maison`() {
        val messageRow = source("ui/chat/components/MessageRow.kt")
        assertTrue(
            "l'appel d'outil est une carte de contexte",
            messageRow.contains("state = toolCardState(tool.status)"),
        )
        assertTrue(
            "le diff est une carte de contexte",
            messageRow.contains("state = patchCardState("),
        )

        val subagents = source("ui/chat/components/SubagentRows.kt")
        assertTrue(
            "le sous-agent est une carte de contexte",
            subagents.contains("state = subagentCardState(agent.status)"),
        )

        val approval = source("ui/chat/components/ApprovalCard.kt")
        assertTrue(
            "l'approbation est une carte de contexte",
            approval.contains("state = approvalCardState(request)"),
        )

        val link = source("ui/chat/components/LinkPreviewCard.kt")
        assertTrue(
            "la page prend le rayon et le filet de la carte de contexte",
            link.contains("contextCardSurface(linkCardState("),
        )
        assertTrue("et le rayon de la maison", link.contains("RoundedCornerShape(ContextCardCorner)"))

        val activity = source("ui/chat/components/ThreadOrganisation.kt")
        assertTrue(
            "la carte d'activité du tour est une carte de contexte",
            activity.contains("ContextCard("),
        )
    }

    /**
     * **L'en-tête et les cartes partagent la même signature.** Le point de l'en-tête et la puce
     * d'une carte passent par la **même** fonction de dessin : les deux battent ensemble, et sous
     * « Réduire les animations » les deux se figent au même endroit.
     */
    @Test
    fun `l en-tete et les cartes dessinent la meme signature d etat`() {
        val thread = source("ui/chat/components/ThreadOrganisation.kt")
        assertTrue(
            "la pastille de l'en-tête passe par la signature commune",
            thread.contains("StateSignatureDot("),
        )
        assertTrue(
            "elle prend l'état réel et le signal de lecture",
            thread.contains("flow = state,") && thread.contains("readingUp = readingUp,"),
        )
        assertTrue(
            "le point n'est plus une couleur d'état : c'est le mouvement qui les distingue",
            thread.contains("**Aucune couleur d'état.**"),
        )

        val card = source("ui/chat/components/ContextCard.kt")
        assertTrue(
            "la puce d'une carte passe par la même signature",
            card.contains("fun ContextStateDot(state: ContextCardState") &&
                card.contains("val pose = rememberFlowPose(flow, readingUp)"),
        )
    }

    /**
     * **Le changement d'état entre par une transition, jamais par un saut** — et sous « Réduire
     * les animations », `snap()` : les états sont posés d'un coup, sans une image intermédiaire.
     */
    @Test
    fun `le passage d un etat a l autre est anime, et fige sous Reduce Motion`() {
        val thread = source("ui/chat/components/ThreadOrganisation.kt")
        assertTrue(
            "le changement d'état passe par une transition",
            thread.contains("AnimatedContent(") && thread.contains("label = \"flowShift\""),
        )
        assertTrue("un fondu court et une échelle", thread.contains("fadeIn(animationSpec = shift)") && thread.contains("scaleIn("))
        assertTrue(
            "la transition est la famille de la maison, donc figée sous Reduce Motion",
            thread.contains("val shift = MotionFamily.FlowShift.spec<Float>(reduceMotion)"),
        )
        assertTrue(
            "l'échelle d'entrée est courte et nommée",
            thread.contains("const val FlowSignatureEntryScale: Float = 0.92f"),
        )
    }

    @Test
    fun `aucune couleur saturee ajoutee par le lot dans les cartes`() {
        val card = source("ui/chat/components/ContextCard.kt")
        assertFalse(
            "une carte ne déclare aucune couleur littérale",
            Regex("Color\\(0x").containsMatchIn(card),
        )
        val approval = source("ui/chat/components/ApprovalCard.kt")
        assertFalse(
            "l'aplat orange de la fiche a disparu : une seule couleur d'état, et c'est l'accent",
            approval.contains("theme.warning"),
        )
    }
}
