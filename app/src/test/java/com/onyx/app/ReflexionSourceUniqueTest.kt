package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LE VERROU DE LA RÉFLEXION — une seule source, un seul arrêt.**
 *
 * Ordre du propriétaire : « supprime la barre de réflexion, encadrée en rouge, ça fait doublon ».
 * La carte d'activité posée au-dessus du compositeur réaffichait ce que le fil dit déjà — la
 * réflexion et sa durée (la rangée « Réflexion » dépliable, en tête de la réponse) **et** un second
 * bouton d'arrêt pour le même geste que le carré du champ. Un autre lot avait relevé « deux boutons
 * d'arrêt pour le même geste » sans le corriger : c'était cette carte.
 *
 * Ce verrou a **deux dents**, et chacune mord :
 *
 * 1. **La source unique** : [reflexionViolations] rend **toujours vide** sur l'arbre réel, et il
 *    mord sur une source qui réintroduit la carte — c'est la preuve que la dent coupe.
 * 2. **Les deux gestes** : la rangée « Réflexion » du fil reste présente (elle porte la durée), et
 *    le compositeur reste le **seul** arrêt du tour.
 */
class ReflexionSourceUniqueTest {

    private fun read(path: String) = File(path).readText()

    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val threadOrganisation =
        read("src/main/java/com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
    private val messageRow = read("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt")
    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")

    @Test
    fun `une seule source pour la reflexion, un seul arret pour le tour`() {
        assertEquals(
            "la réflexion ne doit vivre qu'à UN endroit (le fil), et le tour n'avoir qu'UN arrêt",
            emptyList<String>(),
            reflexionViolations(chat, threadOrganisation, messageRow, composer),
        )
    }

    @Test
    fun `le verrou mord quand une seconde reflexion ou un second arret revient hors du fil`() {
        // 1. La carte d'activité réintroduite telle qu'elle était : seconde réflexion **et** second
        //    arrêt, exactement le doublon de la capture du propriétaire.
        val reappeared =
            "state.live?.takeIf { it.isStreaming }?.let { phase ->\n" +
                "    LiveActivityCard(title = liveActivityTitle(phase), onStop = onStop)\n" +
                "}"
        assertTrue(
            "la dent doit couper : la carte réintroduite doit être refusée",
            reflexionViolations(reappeared, threadOrganisation, messageRow, composer).isNotEmpty(),
        )

        // 2. Un second arrêt du tour **sans** la carte : rebranché dans l'écran, il ne doit pas
        //    passer non plus.
        val secondStop = "TurnStopButton(onClick = onStop)"
        assertTrue(
            "un second arrêt du tour dans l'écran doit être refusé",
            reflexionViolations(secondStop, threadOrganisation, messageRow, composer).isNotEmpty(),
        )

        // 3. La rangée « Réflexion » du fil effacée : la source unique serait perdue, le verrou le
        //    dit.
        assertTrue(
            "l'absence de la rangée « Réflexion » du fil doit être refusée",
            reflexionViolations(chat, threadOrganisation, "// plus de rangée", composer).isNotEmpty(),
        )

        // 4. Un compositeur qui perdrait son arrêt : refusé aussi (le seul arrêt a disparu).
        assertTrue(
            "un compositeur sans arrêt doit être refusé",
            reflexionViolations(chat, threadOrganisation, messageRow, "// plus d'arrêt").isNotEmpty(),
        )
    }
}

/**
 * **Les infractions à la source unique.** Rend la liste (vide = conforme) :
 *
 * - la carte d'activité — ou son composant — revient au-dessus du compositeur (seconde réflexion) ;
 * - la rangée « Réflexion » du fil disparaît, ou n'y montre plus sa durée ;
 * - un second arrêt du tour apparaît dans l'écran, ou le compositeur n'en porte plus un seul.
 *
 * **Fonction pure** : elle est éprouvée sur des sources qui portent le défaut (la morsure).
 */
internal fun reflexionViolations(
    chat: String,
    threadOrganisation: String,
    messageRow: String,
    composer: String,
): List<String> = buildList {
    // 1. La carte d'activité au-dessus du compositeur — seconde réflexion **et** second arrêt.
    if (chat.contains("LiveActivityCard(")) add("carte d'activité posée au-dessus du compositeur")
    if (threadOrganisation.contains("fun LiveActivityCard(")) add("le composant LiveActivityCard existe encore")
    if (chat.contains("liveActivityTitle(") || chat.contains("liveActivityDetail(")) {
        add("titre/détail de la carte d'activité branchés dans l'écran")
    }
    // 2. La rangée « Réflexion » du fil : la source **unique**, avec sa durée.
    if (!messageRow.contains("private fun ThinkingFold(")) {
        add("la rangée « Réflexion » du fil a disparu")
    }
    if (!messageRow.contains("reasoningSeconds(message)")) {
        add("la rangée « Réflexion » du fil ne montre plus la durée")
    }
    // 3. Un **seul** arrêt du tour : celui du compositeur.
    if (Regex("Symbols\\.Stop").findAll(composer).count() != 1) {
        add("le compositeur ne porte pas exactement un arrêt du tour")
    }
    if (chat.contains("TurnStopButton(")) {
        add("un second arrêt du tour est branché dans l'écran")
    }
}
