package com.onyx.app.ui.settings

import com.onyx.app.localization.OnyxLocalization
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La section CONNEXION et la section RÉDACTION des Réglages, telles que la source de référence les tient :
 * un serveur d'usine (donc une seule ligne à la première installation), et les deux interrupteurs
 * de la rédaction dans le **sens** de la référence — « Return key sends » allumé, « Suggest follow-up
 * questions » éteint —, le pouce à droite et la piste verte quand c'est allumé.
 */
class SettingsWritingParityTest {
    private val repo = File("src/main/java/com/onyx/app/data/repo/SettingsRepository.kt").readText()
    private val servers = File("src/main/java/com/onyx/app/data/repo/ServerRepository.kt").readText()
    private val screen = File("src/main/java/com/onyx/app/ui/settings/SettingsScreen.kt").readText()
    private val form = File("../core/src/main/java/com/onyx/app/ui/design/OnyxForm.kt").readText()
    private val writing = screen.substringAfter("header = \"Chat\"").substringBefore("header = \"Voice\"")

    @Test
    fun `une installation neuve n a qu un seul serveur`() {
        // La liste retombe sur l'unique serveur d'usine (`DEFAULT_SERVER`), comme `Settings.servers`
        // sur la référence : la section CONNEXION ne montre donc qu'une ligne, puis « Add a server ».
        assertTrue(servers.contains("stored.first.ifEmpty { listOf(DEFAULT_SERVER) }"))
        assertTrue(servers.contains("val DEFAULT_SERVER = ServerConfig("))
        assertTrue(screen.contains("servers.forEachIndexed { index, config ->"))
        assertTrue(screen.contains("text = \"Add a server\""))
    }

    @Test
    fun `les deux interrupteurs de la redaction suivent le sens d référence`() {
        val returnRow = writing.indexOf("label = \"Return key sends\"")
        val suggestRow = writing.indexOf("label = \"Suggest follow-up questions\"")
        assertTrue(returnRow > 0)
        assertTrue(suggestRow > returnRow)
        assertTrue(writing.contains("checked = returnSends"))
        assertTrue(writing.contains("onCheckedChange = viewModel::setReturnSends"))
        assertTrue(writing.contains("checked = suggestFollowUps"))
        assertTrue(writing.contains("onCheckedChange = viewModel::setSuggestFollowUps"))
    }

    @Test
    fun `les valeurs par defaut sont celles de la référence`() {
        // `Settings.returnSends` est allumé, `Settings.suggestFollowUps` éteint.
        assertTrue(repo.contains("private val _returnSends = MutableStateFlow(true)"))
        assertTrue(repo.contains("_returnSends.value = preferences[Keys.RETURN_SENDS] ?: true"))
        assertTrue(repo.contains("private val _suggestFollowUps = MutableStateFlow(false)"))
        assertTrue(repo.contains("_suggestFollowUps.value = preferences[Keys.SUGGEST_FOLLOW_UPS] ?: false"))
        // Chaque réglage garde sa propre clé : rien n'est croisé en chemin.
        assertTrue(repo.contains("val RETURN_SENDS = booleanPreferencesKey(\"return_sends\")"))
        assertTrue(repo.contains("val SUGGEST_FOLLOW_UPS = booleanPreferencesKey(\"suggest_follow_ups\")"))
    }

    @Test
    fun `l interrupteur met le pouce a droite et la piste a l accent quand il est allume`() {
        assertTrue(form.contains("contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart"))
        // La piste allumée est **l'accent du thème** — le bleu de la référence, et non plus le vert
        // du système : c'est le contrôle que la référence montre dans ses rangées. Elle est écartée
        // de la page juste assez pour tenir 3:1 (`OnyxContrast.distinctOn`) ; le seuil est mesuré
        // dans `InsightsContrastTest`.
        assertTrue(form.contains("val track = if (checked) OnyxContrast.distinctOn(theme.accent, theme.formPageColor) else theme.fill"))
        assertTrue(form.contains(".background(track, RoundedCornerShape(50))"))
        assertTrue(form.contains("fun OnyxToggleRow("))
        assertTrue(form.contains("onCheckedChange(!checked)"))
        // Les dimensions de la référence : une piste de 46×28, un pouce de 22.
        assertTrue(form.contains(".size(width = 46.dp, height = 28.dp)"))
        assertTrue(form.contains(".size(22.dp)"))
    }

    @Test
    fun `les libelles de la redaction sont dits en francais`() {
        assertEquals("La touche Retour envoie", OnyxLocalization.translate("Return key sends", Locale.FRENCH))
        assertEquals(
            "Suggérer des questions de suivi",
            OnyxLocalization.translate("Suggest follow-up questions", Locale.FRENCH),
        )
        assertEquals("Ajouter un serveur", OnyxLocalization.translate("Add a server", Locale.FRENCH))
    }
}
