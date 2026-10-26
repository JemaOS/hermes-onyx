package com.onyx.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le câblage des notifications, vérifié sur les fichiers: les récepteurs déclarés, les actions qui
 * répondent vraiment au gateway, et la rangée de code qui copie.
 *
 * Ces assertions lisent le source (comme les autres tests de mise en page du dépôt) parce que le
 * comportement vit dans des `BroadcastReceiver` et des `LaunchedEffect` que la JVM n'exécute pas.
 */
class NotificationActionsWiringTest {

    private fun read(path: String) = File(path).readText()

    @Test
    fun `les deux recepteurs de notification sont declares dans le manifeste`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains(".notifications.ApprovalActionReceiver"))
        assertTrue(manifest.contains(".notifications.QuestionActionReceiver"))
    }

    @Test
    fun `une approbation repondue depuis la notification remplace la demande par son resultat`() {
        val receiver = read("src/main/java/com/onyx/app/notifications/ApprovalActionReceiver.kt")
        assertTrue(receiver.contains("GatewayApprovalClient(server).respond(runId, requestId, choice)"))
        assertTrue(receiver.contains("ApprovalNotifier.notifyResolved("))
        // Une erreur n'est pas avalée: elle devient l'état affiché.
        assertTrue(receiver.contains("failed = true"))
        assertTrue(receiver.contains("approved = approved"))
    }

    @Test
    fun `la notification d'approbation porte ses deux actions et de quoi les reecrire`() {
        val notifier = read("src/main/java/com/onyx/app/notifications/ApprovalNotifier.kt")
        assertTrue(notifier.contains("NotificationCopy.APPROVE"))
        assertTrue(notifier.contains("NotificationCopy.DENY"))
        assertTrue(notifier.contains("EXTRA_COMMAND"))
        assertTrue(notifier.contains("EXTRA_TITLE"))
    }

    @Test
    fun `la question de l'agent se repond par RemoteInput et part vers le gateway`() {
        val notifier = read("src/main/java/com/onyx/app/notifications/QuestionNotifier.kt")
        assertTrue(notifier.contains("RemoteInput.Builder(REPLY_KEY)"))
        assertTrue(notifier.contains("addRemoteInput(remoteInput)"))
        assertTrue(notifier.contains("ApprovalNotifier.channelForSound(sound)"))

        val receiver = read("src/main/java/com/onyx/app/notifications/QuestionActionReceiver.kt")
        assertTrue(receiver.contains("RemoteInput.getResultsFromIntent(intent)"))
        // Le même gateway que la carte: question, sudo, secret.
        assertTrue(receiver.contains("client.clarify(requestId, questionId, answer)"))
        assertTrue(receiver.contains("client.sudo(sessionId.orEmpty(), requestId, answer)"))
        assertTrue(receiver.contains("client.secret(sessionId.orEmpty(), requestId, answer)"))
        assertTrue(receiver.contains("QuestionNotifier.notifyResolved("))
    }

    @Test
    fun `l'app notifie les questions en arriere-plan et non au premier plan`() {
        val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
        assertTrue(app.contains("pendingQuestionsOf(state.messages)"))
        assertTrue(app.contains("QuestionNotifier.notifyQuestion("))
        assertTrue(app.contains("canAnswerInline("))
        // Au premier plan, la carte suffit: la notification s'efface.
        assertTrue(app.contains("pendingQuestions.forEach { QuestionNotifier.clear(context, it.notificationId) }"))
    }

    @Test
    fun `la notification de progression suit l'outil et s'egrene`() {
        val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
        assertTrue(app.contains("NotificationCopy.turnPhase("))
        assertTrue(app.contains("TurnService.start("))
        assertTrue(app.contains("TurnService.phase(context, phase, percent)"))
        assertTrue(app.contains("delay(1_000)"))
    }

    @Test
    fun `la transcription du sous-agent ouvre steer et stop quand le gateway donne l'identifiant`() {
        val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
        // Les deux gestes ne sont proposés que si l'enfant a une session: sans elle, rien à
        // piloter, et l'écran ne montre ni Stop ni barre de consigne.
        assertTrue(app.contains("val controllable = agent.hasTranscript && !subagentDemoRun"))
        assertTrue(app.contains("onSteer = steerAction"))
        assertTrue(app.contains("onStop = stopAction"))
        assertTrue(app.contains("val steerAction: (suspend (String) -> Boolean)? ="))
        assertTrue(app.contains("val stopAction: (suspend () -> Boolean)? ="))

        val viewModel = read("src/main/java/com/onyx/app/ui/chat/ChatViewModel.kt")
        assertTrue(viewModel.contains("suspend fun steerSubagent("))
        assertTrue(viewModel.contains("suspend fun stopSubagent("))
        assertTrue(viewModel.contains("ServeSubagentRpc(channel).steer(parentSessionId(), id, text)"))
        assertTrue(viewModel.contains("ServeSubagentRpc(channel).interrupt(parentSessionId(), id)"))
        // Le canal est celui de la conversation: un socket neuf n'a pas l'autorité de pilotage.
        assertTrue(viewModel.contains("val channel = rpc ?: com.onyx.app.data.net.ServeRpc(server).also { rpc = it }"))
        assertTrue(viewModel.contains("private fun parentSessionId(): String = runtimeSessionId ?: serverSessionId.orEmpty()"))

        val screen = read("src/main/java/com/onyx/app/ui/chat/SubagentTranscriptScreen.kt")
        assertTrue(screen.contains("onStop: (suspend () -> Boolean)? = null"))
        assertTrue(screen.contains("action = if (onStop != null && running) \"Stop\" else null"))
        // La consigne refusée se dit, elle ne disparaît pas en silence.
        assertTrue(screen.contains("The subagent isn't accepting steering right now"))
    }

    @Test
    fun `le bloc de code copie vraiment et nhardcode pas de numeros de ligne`() {
        val block = read("../core/src/main/java/com/onyx/app/ui/rich/CodeBlock.kt")
        assertTrue(block.contains("clipboard.setClipEntry("))
        assertTrue(block.contains("codeBlockLabel(language)"))
        assertFalse(block.contains("lineCount"))
    }

    @Test
    fun `la ligne de sous-agent vient du vrai evenement du gateway`() {
        // Le vocabulaire `subagent.*` vit avec le modèle (qui le lit), et le transport s'y réfère
        // par type: une ligne ne peut plus naître d'un nom d'outil deviné.
        val model = read("../core/src/main/java/com/onyx/app/data/model/Subagent.kt")
        assertTrue(model.contains("\"subagent.start\""))
        assertTrue(model.contains("\"subagent.tool\""))
        assertTrue(model.contains("\"subagent.progress\""))
        assertTrue(model.contains("\"subagent.complete\""))
        assertTrue(model.contains("\"subagent_id\""))

        val client = read("../core/src/main/java/com/onyx/app/data/net/ChatCompletionsClient.kt")
        assertTrue(client.contains("SubagentUpdate.isSubagentEvent(event.event.orEmpty())"))
        assertTrue(client.contains("SubagentUpdate.parse(event, payload)"))
        assertTrue(client.contains("scope.send(TurnEvent.Subagent(update))"))
        // L'API de conversation ne nomme jamais l'enfant: le `delegate_task` et son libellé sont
        // tout ce qu'on y voit, et c'est ce qui devient la ligne « dispatchée ».
        assertTrue(client.contains("DELEGATE_TOOL = \"delegate_task\""))

        // Le socket du Dashboard, lui, porte vraiment les trames: c'est la route réparée.
        val serve = read("../core/src/main/java/com/onyx/app/data/net/HermesServeChat.kt")
        assertTrue(serve.contains("in SubagentUpdate.EVENT_TYPES ->"))
        assertTrue(serve.contains("?.let { ServeStep.Emit(listOf(TurnEvent.Subagent(it))) }"))
        assertTrue(serve.contains(") + SubagentUpdate.EVENT_TYPES"))
    }
}
