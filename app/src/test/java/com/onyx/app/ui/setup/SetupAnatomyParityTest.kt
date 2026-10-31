package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **L'écran de mise en route refait sur l'anatomie de la référence — et prouvé par le source.**
 *
 * Le propriétaire a montré la page de première ouverture de la référence et demandé « c'est propre,
 * je veux pareil adapté à mon app — il faut simplifier ce que j'ai déjà en gardant les meilleures
 * fonctions ». La simplification demandée est un **rangement**, pas une suppression : ces tests
 * verrouillent donc les deux moitiés de la phrase — ce qui est **rangé** (l'ordre de la page, le
 * test dans le champ, les repliables, les trois moyens d'entrer l'adresse au même endroit), et ce
 * qui est **gardé** (la sonde réelle, le QR, le collage, la liste des transports, les secrets
 * masqués).
 */
class SetupAnatomyParityTest {

    private val source = File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt").readText()
    private val anatomy = File("src/main/java/com/onyx/app/ui/setup/SetupAnatomy.kt").readText()

    @Test
    fun `la page ouvre sur l'icone, le grand titre en gras et la phrase qui rassure`() {
        // 1. l'icône en haut à gauche, 2. le très grand titre en gras, 3. la réassurance.
        assertTrue(source.contains("modifier = Modifier\n                        .size(OnyxMarkSize)"))
        assertTrue(source.contains("style = theme.largeTitle.copy("))
        assertTrue(source.contains("fontWeight = FontWeight.Bold,"))
        assertTrue(
            source.contains("\"Connect to your Hermes gateway. Nothing is sent until you connect.\""),
        )
        // Le titre n'est plus celui d'avant, et la promesse d'envoi est **dans l'intro**, pas
        // reléguée en note : c'est la phrase qui rassure de la référence.
        assertFalse(source.contains("\"Talk to your own AI agent by voice or chat.\""))
        // Tout est aligné à gauche, y compris l'en-tête.
        assertTrue(source.contains("horizontalAlignment = Alignment.Start"))
    }

    @Test
    fun `le champ porte le libelle, le globe et l'action de test DANS le champ`() {
        // Libellé au-dessus du champ, globe à gauche, pilule « Tester » à droite **dans** le champ.
        assertTrue(anatomy.contains("internal fun SetupAddressField("))
        assertTrue(anatomy.contains("OnyxIcon(Symbols.Globe, tint = theme.tertiaryText, size = 17.dp)"))
        assertTrue(anatomy.contains("OnyxIcon(Symbols.Bolt, tint = theme.accent, size = 13.dp)"))
        assertTrue(anatomy.contains("text = if (testing) \"Testing…\" else \"Test\""))
        // Le libellé et la ligne d'aide sont passés par le dictionnaire (clés anglaises).
        assertTrue(source.contains("label = \"Gateway address\""))
        assertTrue(source.contains("\"Where your dashboard runs: LAN, Tailscale or HTTPS.\""))
        // Le champ est unique : une seule source d'adresse pour tout l'écran.
        assertTrue(source.contains("value = transportAddress(config)"))
        assertFalse(source.contains("label = \"Address\","))
    }

    @Test
    fun `les trois moyens d'entrer l'adresse sont dans la meme section`() {
        val card = source.substringAfter("OnyxFormSection(\n                footer = \"One link sets up everything").substringBefore("// **3.**")
        assertTrue("la saisie est dans la carte", card.contains("SetupAddressField("))
        assertTrue("le QR est dans la même carte", card.contains("text = \"Scan a setup code\""))
        assertTrue("le collage est dans la même carte", card.contains("text = \"Paste a setup link\""))
        assertTrue("la capsule de collage reste", card.contains("SetupPasteButton("))
        // Le message de collage est désormais une **phrase** qui nomme ce qui a été collé et où le
        // mettre ([SetupPaste.problemFor]) : la carte la rend telle quelle, et le refus générique
        // — muet sur la suite à donner — a disparu.
        assertTrue("l'erreur de collage est rendue", card.contains("text = problem"))
        assertFalse("le refus générique est révolu", card.contains("\"That isn't a Onyx setup link.\""))
        // Le QR n'est plus un écran isolé : il n'a plus de section « Have a setup code? » à lui.
        assertFalse(source.contains("header = \"Have a setup code?\""))
    }

    @Test
    fun `les cinq repliables sont fermes a l'ouverture`() {
        listOf(
            "addressHelpOpen", "accessOpen", "transportOpen", "discoverOpen", "featuresOpen",
        ).forEach { flag ->
            assertTrue("$flag doit être un repliable fermé", source.contains("var $flag by remember { mutableStateOf(false) }"))
        }
        listOf(
            "\"Which address do I use?\"",
            "\"Behind Cloudflare Access?\"",
            "\"Connect another way\"",
            "\"What else is on the server?\"",
            "\"What does Onyx do?\"",
        ).forEach { title ->
            assertTrue("le repliable $title manque", source.contains("title = $title"))
        }
        // Le résumé du repliable est celui de la maison : titre à gauche, chevron à droite.
        assertTrue(anatomy.contains("OnyxDisclosureSummary(title = title, expanded = expanded, onClick = onToggle)"))
        // L'ouverture suit une famille de mouvement de la maison, pas une durée choisie sur place.
        assertTrue(anatomy.contains("fadeIn(animationSpec = MotionFamily.Row.animated())"))
        assertTrue(anatomy.contains("expandVertically(animationSpec = MotionFamily.Row.animated())"))
    }

    @Test
    fun `les explications d'adressage portent ses trois cas et ses adresses`() {
        assertTrue(source.contains("title = \"Same Wi-Fi / local network\""))
        assertTrue(source.contains("example = \"http://192.168.1.20:9119\""))
        assertTrue(source.contains("--host 0.0.0.0"))
        assertTrue(source.contains("title = \"Tailscale\""))
        assertTrue(source.contains("example = \"http://100.64.0.1:9119\""))
        assertTrue(source.contains("machine.tailnet-name.ts.net"))
        assertTrue(source.contains("title = \"HTTPS\""))
        assertTrue(source.contains("example = \"https://your-gateway.example.com\""))
        assertTrue(source.contains("/api/ws"))
    }

    @Test
    fun `aucun secret n'est jamais relu en clair`() {
        // Le champ du secret de Cloudflare Access est masqué, et **aucun œil** ne le révèle : la
        // règle corrigée de la maison (copie seule) gagne sur le bouton de révélation.
        val access = source.substringAfter("\"Behind Cloudflare Access?\"").substringBefore("// **5.**")
        assertTrue(access.contains("label = \"Client ID\""))
        assertTrue(access.contains("label = \"Client secret\""))
        assertTrue(access.contains("secret = true,"))
        assertFalse("un secret ne se révèle jamais", access.contains("Symbols.Eye"))
        assertFalse(source.contains("PasswordVisualTransformation.None"))
        assertFalse(anatomy.contains("Symbols.Eye"))
        // Et la note de chiffrement de la référence est là, adaptée à Onyx.
        assertTrue(source.contains("\"Stored encrypted on this phone, and sent only to this address.\""))
    }

    @Test
    fun `aucun secret ne part dans un journal`() {
        // Le verrou de la maison : les deux fichiers de l'écran de mise en route ne journalisent
        // rien du tout — donc aucun secret ne peut y passer, ni en clair ni tronqué.
        listOf(source, anatomy).forEach { text ->
            assertFalse("un journal a été ajouté à l'écran de mise en route", text.contains("Log.d("))
            assertFalse(text.contains("Log.i("))
            assertFalse(text.contains("Log.w("))
            assertFalse(text.contains("Log.e("))
            assertFalse(text.contains("println("))
        }
    }

    @Test
    fun `les meilleures fonctions d'Onyx sont gardees`() {
        // La liste des transports et sa coche.
        assertTrue(source.contains("BackendTransports.forEachIndexed"))
        assertTrue(source.contains("header = \"How does Onyx reach it?\""))
        // Les trois identifiants de transport restent là, avec leurs textes.
        assertTrue(source.contains("header = \"Hermes API server\""))
        assertTrue(source.contains("header = \"Hermes Dashboard\""))
        assertTrue(source.contains("header = \"OpenAI-compatible endpoint\""))
        // La vérification réelle, avec sa latence.
        assertTrue(anatomy.contains("\"${'$'}{result.latencyMs} ms\""))
        // La découverte du reste, et ce qu'elle a trouvé.
        assertTrue(source.contains("text = if (discovering) \"Finding the rest…\" else \"Find the rest\""))
        assertTrue(source.contains("SetupLabeledRow(\"Model\", found.model)"))
        assertTrue(source.contains("SetupLabeledRow(\"Voices\", found.voices)"))
        assertTrue(source.contains("SetupLabeledRow(\"Computer\", found.bridge + note)"))
        assertTrue(source.contains("SetupLabeledRow(\"Language\", found.language)"))
        // Les trois promesses de la référence.
        assertTrue(source.contains("\"Your own server\""))
        assertTrue(source.contains("\"Private by design\""))
        assertTrue(source.contains("\"Speech stays on device\""))
    }

    @Test
    fun `la sortie ne reste jamais muette et le test n'est jamais aveugle`() {
        // « Done » dit pourquoi, ou conclut ; « Skip » sort toujours.
        assertTrue(source.contains("val refusal = doneRefusal(config, testing, probe)"))
        assertTrue(source.contains("if (refusal == null) onDone()"))
        assertTrue(source.contains("onBack = onSkip"))
        // Une adresse mal formée ne laisse plus le bouton estompé pour toujours : la sonde est
        // enveloppée et l'échec devient un verdict avec sa raison.
        assertTrue(source.contains("catch (error: Exception) {"))
        assertTrue(source.contains("message = error.message ?: OnyxLocalization[\"Couldn't connect.\"]"))
    }
}
