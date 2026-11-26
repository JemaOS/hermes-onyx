package com.onyx.app.ui.setup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du mode « Simple » — et il mord.**
 *
 * Le propriétaire était bloqué sur « Configurer Onyx », mode « Simple » choisi : aucun champ de clé,
 * aucune proposition, et rien à appuyer. Le mode « Simple » a pour définition même « Adresse et clé.
 * Onyx trouve le reste. » — c'est le chemin d'installation principal de l'app, et il ne menait nulle
 * part.
 *
 * Ce fichier tient les six contrats de la correction, **sur la source** : si un morceau disparaît,
 * un de ces cas passe au rouge (la preuve de morsure est faite en retirant un fragment et en
 * relançant la suite). Il ne teste pas l'aspect : la recette de l'écran, ce sont les captures.
 */
class SetupSimpleLockTest {

    private val screen = File("src/main/java/com/onyx/app/ui/setup/SetupScreen.kt").readText()
    private val anatomy = File("src/main/java/com/onyx/app/ui/setup/SetupAnatomy.kt").readText()
    private val suggestions = File("src/main/java/com/onyx/app/ui/setup/SetupSuggestions.kt").readText()

    @Test
    fun `le mode Simple affiche un champ de cle, et il est bien le meme que celui d'Avance`() {
        // 1. Le champ de clé existe, et il est conditionné au mode « Simple ».
        assertTrue(anatomy.contains("internal fun SetupKeyField("))
        assertTrue(screen.contains("if (!advanced) {"))
        assertTrue(screen.contains("SetupKeyField("))
        assertTrue(screen.contains("label = \"Key\","))
        // 2. Il écrit dans **la même mémoire** que le champ « API key » d'Avancé : `config.apiKey`.
        //    Simple → Avancé sans perte, parce qu'il n'y a qu'un seul endroit où la clé vit.
        assertTrue(screen.contains("onValueChange = { value -> onChange { it.copy(apiKey = value) } }"))
        assertTrue(screen.contains("value = config.apiKey,"))
    }

    @Test
    fun `le champ de cle est entierement masque, copiable, et sans icone oeil`() {
        val key = anatomy.substringAfter("internal fun SetupKeyField(").substringBefore("/** Une capsule")
        assertTrue("la valeur est masquée", key.contains("secret = true,"))
        assertTrue("la copie est offerte", key.contains("Symbols.DocOnDoc"))
        assertTrue("le presse-papiers se pose", key.contains("Symbols.DocOnClipboard"))
        assertFalse("un secret ne se révèle jamais", key.contains("Symbols.Eye"))
        assertFalse(anatomy.contains("Symbols.Eye"))
        assertFalse(screen.contains("Symbols.Eye"))
    }

    @Test
    fun `l'adresse devient un secret des qu'elle porte le jeton de voix`() {
        assertTrue(screen.contains("secret = SetupSuggestions.carriesSecret(transportAddress(config))"))
        assertTrue(anatomy.contains("secret = secret,"))
        assertTrue(suggestions.contains("fun carriesSecret(raw: String): Boolean"))
    }

    @Test
    fun `le mode Simple propose — presse-papiers, adresses connues, scan du QR`() {
        // Le bouton « Coller » **dans le champ**, qui pose ce que le presse-papiers porte **et teste**.
        assertTrue(anatomy.contains("onPaste: (() -> Unit)? = null,"))
        assertTrue(anatomy.contains("label = \"Paste\","))
        // La capsule est **toujours** offerte : elle ne dépend pas d'une lecture du presse-papiers
        // au bon instant (le système la refuse à une appli sans focus).
        assertTrue(screen.contains("onPaste = {"))
        assertTrue(screen.contains("val pasted = SetupSuggestions.addressFromClipboard("))
        assertTrue("le collage lance le test", screen.contains("runTest()"))
        // Un collage sans adresse **le dit** — et il le dit mieux qu'avant : le lot de
        // l'adresse-verrou a remplacé le booléen par une phrase (`SetupPaste.problemFor`) qui nomme
        // ce qui a été collé et où cela va. L'intention de la chaîne est tenue, la forme a changé :
        // le verrou s'aligne sur ce qui est peint, il ne s'affaiblit pas.
        assertTrue("un collage sans adresse le dit", screen.contains("pasteProblem = SetupPaste.problemFor("))
        // Les adresses déjà connues : une source nommée pour chacune.
        assertTrue(screen.contains("suggestions: List<AddressSuggestion> = emptyList(),"))
        assertTrue(screen.contains("SetupSuggestionRow("))
        assertTrue(screen.contains("suggestionLabel(suggestion.source)"))
        assertTrue(screen.contains("SuggestionSource.LOCAL -> \"This phone's network\""))
        // Le scan du QR est toujours là, à la même place.
        assertTrue(screen.contains("text = \"Scan a setup code\""))
    }

    @Test
    fun `le bouton Tester est DANS le champ et remplit le reste apres un succes`() {
        // L'anatomie de première ouverture validée par le propriétaire : la pilule de test dans le
        // champ, à côté du globe.
        assertTrue(anatomy.contains("OnyxIcon(Symbols.Globe, tint = theme.tertiaryText, size = 17.dp)"))
        assertTrue(anatomy.contains("OnyxIcon(Symbols.Bolt, tint = theme.accent, size = 13.dp)"))
        assertTrue(anatomy.contains("text = if (testing) \"Testing…\" else \"Test\""))
        // Un test réussi lance la découverte : le modèle, les voix, le pont et la langue arrivent.
        assertTrue(screen.contains("if (outcome.ok) discover()"))
        assertTrue(screen.contains("if (found.model.isNotEmpty()) onChange { it.copy(model = found.model) }"))
        assertTrue(screen.contains("onApplyDiscovery(found)"))
        // **Et s'il trouve moins que prévu, il le dit** au lieu de rester muet.
        assertTrue(
            screen.contains(
                "\"Connected. The server announced nothing else — set the model and the voices by hand under Advanced.\"",
            ),
        )
    }

    @Test
    fun `une adresse longue se relit et l'echec porte le corps du serveur`() {
        // Lisibilité : le champ d'adresse se replie au lieu de cacher son début. La borne est
        // **au moins deux lignes** — le lot des polices du champ en a mesuré trois à police 1,3×,
        // et sa mesure est gardée : le verrou tient l'intention (le repli), pas un nombre figé.
        assertTrue(anatomy.contains("singleLine = false,"))
        assertTrue(anatomy.contains("maxLines = SetupAddressLines,"))
        assertTrue(anatomy.contains("wrapLines = SetupAddressLines,"))
        assertTrue(SetupAddressLines >= 2)
        // Le verdict du test n'est plus rogné à trois lignes : le corps renvoyé par la passerelle
        // (« HTTP 401 … clé d'installation absente ou invalide ») nomme la cause, il s'affiche.
        // La borne est **nommée** — apport du lot du champ d'adresse — et garde les douze lignes
        // de la chaîne : les deux intentions sont satisfaites ensemble.
        val verdict = anatomy.substringAfter("internal fun SetupVerdict(").substringBefore("internal fun SetupDisclosure(")
        assertTrue(verdict.contains("maxLines = SetupVerdictLines,"))
        assertTrue(SetupVerdictLines >= 12)
        assertFalse(verdict.contains("maxLines = 3,"))
    }

    @Test
    fun `la premiere rangee ne passe plus sous la barre de titre, et le bouton Copier vit dans la barre`() {
        // Le corps prend la place qui RESTE : il ne peut plus recouvrir la barre de titre.
        assertTrue(screen.contains(".fillMaxWidth()\n                .weight(1f)\n                .verticalScroll(rememberScrollState())"))
        assertTrue(screen.contains(".padding(top = OnyxSpace.Base, bottom = 40.dp),"))
        // Le bouton « Copier » est **dans la barre**, pas dans le corps défilant.
        assertTrue(screen.contains("trailing = if (setupLink.isNotEmpty())"))
        assertTrue(anatomy.contains("internal fun SetupLinkCopyButton("))
        assertTrue(anatomy.contains("text = \"Copy\","))
        assertTrue(screen.contains("setupLink: String = \"\","))
        // Et le lien ne porte pas les secrets : c'est le même contrat que le cas unitaire.
        assertTrue(screen.contains("internal fun setupLinkFor(config: ServerConfig): String"))
    }

    @Test
    fun `les libelles neufs du mode Simple sont traduits`() {
        val fr = java.util.Locale.FRANCE
        mapOf(
            "Key" to "Clé",
            "Gateway key" to "Clé de la passerelle",
            "This server's address" to "L’adresse de ce serveur",
            "This phone's network" to "Le réseau de ce téléphone",
            "The gateway you used last" to "La passerelle que vous utilisiez",
            "One of your saved servers" to "Un de vos serveurs enregistrés",
            "Setup link copied." to "Lien de configuration copié.",
            "Connected. The server announced nothing else — set the model and the voices by hand under Advanced." to
                "Connecté. Le serveur n’a rien annoncé d’autre — réglez le modèle et les voix à la main dans Avancé.",
        ).forEach { (english, french) ->
            org.junit.Assert.assertEquals(
                "français : $english",
                french,
                com.onyx.app.localization.OnyxLocalization.translate(english, fr),
            )
            org.junit.Assert.assertEquals(
                "anglais : $english",
                english,
                com.onyx.app.localization.OnyxLocalization.translate(english, java.util.Locale.ENGLISH),
            )
        }
    }
}
