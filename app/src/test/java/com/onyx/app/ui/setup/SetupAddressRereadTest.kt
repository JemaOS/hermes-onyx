package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La saisie de l'adresse de passerelle, verrouillée par le source — les quatre correctifs.**
 *
 *  1. le mode « Simple » porte **l'adresse ET la clé**, la clé entièrement masquée (c'était le
 *     blocage réel : le serveur réclame la clé par son nom) ;
 *  2. une adresse longue reste **relisible** : le champ d'adresse **se replie** au lieu de ne
 *     montrer que sa fin (ce que le propriétaire a lu `an.trycloudflare.com` sur 49 caractères) ;
 *  3. l'échec **nomme l'adresse tentée et montre le corps de la réponse du serveur** ;
 *  4. les **deux rangées de collage** disent chacune ce qu'elles acceptent.
 *
 * Le mécanisme est celui de la maison : lire le source et y chercher une forme qui ne peut pas être
 * vraie par accident.
 */
class SetupAddressRereadTest {

    private val repoRoot: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("racine du dépôt introuvable depuis ${File("").absolutePath}")

    private fun src(rel: String): String = File(repoRoot, rel).readText()

    private val screen = src("app/src/main/java/com/onyx/app/ui/setup/SetupScreen.kt")
    private val anatomy = src("app/src/main/java/com/onyx/app/ui/setup/SetupAnatomy.kt")
    private val form = src("core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt")

    @Test
    fun `le mode Simple porte l adresse ET la cle, la cle entierement masquee`() {
        // La clé est rendue dans la branche « Simple » — donc visible d'emblée — juste sous l'adresse.
        val simpleBranch = screen.substringAfter("if (!advanced) {").substringBefore("SetupHelpLine")
        assertTrue("le champ de clé est dans la branche Simple", simpleBranch.contains("SetupKeyField("))
        assertTrue("il lit le même apiKey que l'avancé", screen.contains("value = config.apiKey,"))

        // Le champ de clé : masqué **entièrement**, copiable, et **aucune icône œil**.
        val keyField = anatomy.substringAfter("internal fun SetupKeyField(").take(3000)
        assertTrue("la valeur est masquée", keyField.contains("secret = true"))
        assertTrue("la copie est le seul chemin de relecture", keyField.contains("\"Copy\""))
        assertFalse("aucune icône œil sur le champ de clé", keyField.contains("Symbols.Eye"))
        assertFalse("aucune icône œil sur l'écran", screen.contains("Symbols.Eye"))
    }

    @Test
    fun `une adresse longue reste relisible en entier`() {
        // Le champ d'adresse se replie : son début reste sous les yeux au lieu de sa seule fin.
        assertTrue("le champ d'adresse se replie", anatomy.contains("wrapLines = SetupAddressLines"))
        assertTrue("au moins deux lignes", SetupAddressLines >= 2)
        // Et le repli existe pour de vrai dans le champ, pas seulement sur l'appel.
        assertTrue("la variante repliée est là", form.contains("OnyxWrappedEditableValue("))
        assertTrue("elle n'est pas sur une seule ligne", form.contains("singleLine = false,"))
        assertTrue("elle borne ses lignes", form.contains("maxLines = maxLines,"))
        // La garde d'écho du collage vaut aussi pour la variante repliée.
        assertTrue("la garde d'écho est partagée", form.contains("OnyxFieldSync.shouldAdopt(value, emitted, field.text)"))
    }

    @Test
    fun `l echec nomme l adresse tentee et montre le corps du serveur`() {
        val chat = src("core/src/main/java/com/onyx/app/data/net/ChatCompletionsClient.kt")
        assertTrue("la sonde passe par ProbeFailure", chat.contains("ProbeFailure.message("))
        assertTrue("l'adresse tentée est nommée", chat.contains("address = config.apiBaseUrl"))
        assertTrue("le corps du serveur est passé", chat.contains("rawBody = body"))
        // Le verdict a la place de lire un corps JSON entier.
        assertTrue("le verdict s'étend", anatomy.contains("maxLines = SetupVerdictLines"))
        assertTrue("au moins quatre lignes", SetupVerdictLines >= 4)
    }

    @Test
    fun `les deux rangees de collage annoncent chacune ce qu elle accepte`() {
        assertTrue("la rangée du lien annonce un lien complet", screen.contains("SetupPaste.LINK_ACCEPTS"))
        assertTrue("la rangée d'adresse annonce une adresse seule", screen.contains("SetupPaste.ADDRESS_ACCEPTS"))
        assertTrue(
            "l'échec de collage est une phrase, plus un booléen",
            screen.contains("var pasteProblem by remember { mutableStateOf<String?>(null) }"),
        )
        assertTrue(
            "la phrase vient de SetupPaste, qui nomme ce qui a été collé",
            screen.contains("SetupPaste.problemFor(text, onPasteCode(text))"),
        )
    }
}
