package com.onyx.app.ui.vault

import com.onyx.app.data.net.VaultSecret
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.TableDesRetours
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Les états et les libellés du Coffre**, sans Compose et sans réseau.
 *
 * Ce que ce fichier tient :
 *
 * 1. **les trois états** — chargement, erreur, vide — dans cet ordre, pour qu'une route absente ne
 *    se déguise jamais en écran vide muet ;
 * 2. **la ligne d'un secret** — empreinte, usage, date **lisible** : la chaîne ISO du serveur ne
 *    doit jamais apparaître telle quelle ;
 * 3. **les libellés** — chacun est une clé du dictionnaire central, sinon il ressortirait en anglais
 *    au milieu d'un écran français ;
 * 4. **la règle de la valeur** — le champ est masqué, la valeur est vidée après écriture, et le
 *    modèle de lecture n'a aucun champ pour elle ;
 * 5. **l'entrée conditionnelle** — la ligne « Secrets › » n'existe que si la sonde prouve la
 *    capacité, et la route a son retour déclaré.
 */
class VaultScreenTest {

    private val screen = File("src/main/java/com/onyx/app/ui/vault/VaultScreen.kt").readText()
    private val capabilities = File("src/main/java/com/onyx/app/ui/capabilities/CapabilitiesScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()

    private fun secret(
        name: String = "openai",
        fingerprint: String = "a1b2",
        updatedAt: String = "2026-11-03T12:00:00Z",
        usedBy: List<String> = listOf("recherche", "resume"),
    ) = VaultSecret(name = name, fingerprint = fingerprint, updatedAt = updatedAt, usedBy = usedBy)

    // MARK: - 1. Les trois états

    @Test
    fun `un chargement une erreur un vide et une liste sont quatre etats distincts`() {
        assertEquals(VaultView.LOADING, vaultView(secrets = null, problem = null))
        assertEquals(VaultView.EMPTY, vaultView(secrets = emptyList(), problem = null))
        assertEquals(VaultView.LIST, vaultView(secrets = listOf(secret()), problem = null))
    }

    @Test
    fun `une route absente est une erreur, jamais un ecran vide`() {
        // Le cas du serveur plus ancien : `list()` échoue en 404, `secrets` reste `null`. L'écran
        // doit dire l'échec — un `null` nu afficherait un squelette puis une page muette.
        assertEquals(
            "un échec prime sur une liste absente",
            VaultView.ERROR,
            vaultView(secrets = null, problem = "Cet Hermes ne sert pas la route du coffre de secrets."),
        )
        assertTrue(
            "le 404 est nommé par un message du dictionnaire, pas par un « null »",
            screen.contains("http.status == 404") && screen.contains("MISSING_ROUTE"),
        )
        assertTrue(
            "la route manquante a sa phrase française",
            OnyxLocalization.translate("This Hermes does not serve the secrets vault route.", Locale.FRANCE) !=
                "This Hermes does not serve the secrets vault route.",
        )
    }

    // MARK: - 2. La ligne d'un secret

    @Test
    fun `la ligne nomme l empreinte la date lisible et l usage`() {
        val line = vaultSecretDetail(secret(), Locale.FRANCE)

        assertTrue("l'empreinte est nommée : $line", line.contains("Empreinte a1b2"))
        assertTrue("l'usage est nommé : $line", line.contains("Utilisé par recherche, resume"))
        assertTrue("la date est lisible : $line", line.contains("nov. 2026"))
        assertFalse("la date ISO brute ne fuit pas : $line", line.contains("2026-11-03T"))
        // **La date passe devant l'usage** : la rangée n'a que deux lignes et la pastille
        // « Révoquer » mange la moitié de la largeur. En dernier, la date était coupée
        // (« 3 nov.... ») — c'est elle qu'on doit voir, pas l'usage.
        assertTrue(
            "la date précède l'usage : $line",
            line.indexOf("nov. 2026") < line.indexOf("Utilisé par"),
        )
    }

    @Test
    fun `un secret sans usage n ecrit pas une phrase pour ne rien dire`() {
        // « utilisé par aucune tâche » occupait 26 caractères et poussait la date hors de l'écran.
        assertEquals(
            "Empreinte **** · 3 nov. 2026",
            vaultSecretDetail(secret(fingerprint = "****", usedBy = emptyList()), Locale.FRANCE),
        )
    }

    @Test
    fun `une date illisible ne s ecrit pas`() {
        assertNull(vaultDateLabel("pas une date"))
        assertNull(vaultDateLabel(""))
        assertNull(vaultDateLabel(null))
        assertEquals("3 nov. 2026", vaultDateLabel("2026-11-03T12:00:00Z", Locale.FRANCE))
        assertEquals("3 Nov 2026", vaultDateLabel("2026-11-03T12:00:00Z", Locale.ENGLISH))
    }

    // MARK: - 3. Les libellés

    @Test
    fun `aucun libelle fixe du Coffre n echappe au dictionnaire central`() {
        val label = Regex(
            "(?:text|label|subtitle|title|header|footer|placeholder)\\s*=\\s*\"([^\"\\n\\\\]{2,160})\"",
        )
        var judged = 0
        label.findAll(screen).map { it.groupValues[1] }.toSet().forEach { word ->
            if (!word.contains(' ') || !word.first().isUpperCase()) return@forEach
            judged++
            assertTrue(
                "« $word » doit être une clé du dictionnaire central : sans elle, il ressort en " +
                    "anglais au milieu d'un écran français",
                OnyxLocalization.translate(word, Locale.FRANCE) != word,
            )
        }
        assertTrue("l'écran porte bien des libellés à contrôler ($judged)", judged >= 6)
    }

    @Test
    fun `la ligne d entree de Capacites est traduite`() {
        assertTrue("la ligne existe dans « Capacités »", capabilities.contains("title = \"Secrets ›\""))
        assertEquals(
            "Coffre de secrets ›",
            OnyxLocalization.translate("Secrets ›", Locale.FRANCE),
        )
    }

    // MARK: - 4. La valeur ne se montre jamais

    @Test
    fun `le champ de la valeur est masque et la valeur est videe apres ecriture`() {
        assertTrue("le champ de valeur est masqué", screen.contains("secret = true"))
        // Après un `put` accepté, le brouillon repart de zéro : la valeur ne revient pas à l'écran.
        assertTrue("la valeur est vidée après écriture", screen.contains("value = \"\""))
        assertFalse(
            "la valeur n'est jamais concaténée dans un message",
            screen.contains("value.toString()") || screen.contains("+ value"),
        )
        assertTrue(
            "la valeur ne part pas dans un journal",
            !screen.contains("Log.") && !screen.contains("println("),
        )
    }

    @Test
    fun `le modele de lecture n a aucun champ pour la valeur`() {
        assertFalse(
            "VaultSecret ne porte pas de `value`",
            "value" in VaultSecret::class.java.declaredFields.map { it.name },
        )
        val dump = secret().toString()
        assertFalse("le toString() ne peut pas porter une valeur", dump.contains("sk-"))
    }

    // MARK: - 5. L'entrée et la route

    @Test
    fun `la ligne Secrets n existe que si la sonde prouve la capacite`() {
        assertTrue(
            "la sonde interroge la capacité du coffre",
            capabilities.contains("probe?.allows(ServerCapability.VAULT) == true"),
        )
        assertTrue(
            "la ligne est dessinée sous la condition de la sonde",
            capabilities.contains("if (vaultReady && onOpenVault != null)"),
        )
        assertEquals(
            "aucune ligne « Secrets › » hors de cette condition (pas de tap mort)",
            1,
            Regex("title = \"Secrets ›\"").findAll(capabilities).count(),
        )
    }

    @Test
    fun `la route du Coffre est declaree, dessinee et a son retour`() {
        val routes = app.substringAfter("private enum class Route {").substringBefore("\n}")
            .lines().joinToString("\n") { it.substringBefore("//") }
        assertTrue("Route.VAULT est déclarée", Regex("\\bVAULT\\b").containsMatchIn(routes))
        assertTrue("le routeur plein page la dessine", app.contains("Route.VAULT -> VaultScreen("))
        assertTrue("le volet la dessine aussi", app.contains("Route.VAULT.name -> VaultScreen("))
        assertEquals(
            "le retour rend « Capacités », la page quittée",
            "CAPABILITIES",
            TableDesRetours.de("VAULT")?.repli,
        )
    }
}
