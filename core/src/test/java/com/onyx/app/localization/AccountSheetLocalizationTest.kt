package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Les libellés de la **feuille de compte du volet** passent tous par le dictionnaire central : son
 * titre d'état, sa ligne mesurée (latence et compte à rebours), ses cinq rangées et les phrases de
 * la déconnexion.
 *
 * C'est le trou que ce test ferme : la feuille construit ses lignes elle-même (elles ne sont pas
 * des `MenuItem(…)` de l'arbre), et rien ne rougirait si l'une d'elles restait en anglais. Le
 * contrôle du même genre existe déjà pour la feuille des actions d'une conversation
 * ([ChatActionSheetLocalizationTest]) ; celui-ci le refait pour celle-ci.
 */
class AccountSheetLocalizationTest {

    private val fr = Locale.FRANCE

    /** Le titre d'état, la ligne mesurée, et les cinq rangées. */
    private val labels = linkedMapOf(
        "Connected" to "Connecté",
        "Offline" to "Hors ligne",
        "Settings" to "Réglages",
        "Refresh conversations" to "Actualiser les conversations",
        "Refreshing the conversations…" to "Actualisation des conversations…",
        "Retry the connection now" to "Réessayer la connexion maintenant",
        "Disconnect" to "Se déconnecter",
        "Change or add a gateway" to "Changer ou ajouter une passerelle",
    )

    /** Les gabarits `String.format` de la ligne d'état et du retour d'actualisation. */
    private val templates = linkedMapOf(
        // LOT anatomie-des-contenus : la ligne d'état ne répète plus l'état — elle porte **la
        // mesure** seule, et le titre porte le mot. Les gabarits ont suivi.
        "new attempt in %1\$d s" to "nouvelle tentative dans %1\$d s",
        "%1\$d model" to "%1\$d modèle",
        "%1\$d models" to "%1\$d modèles",
        "Conversations refreshed · %1\$d" to "Conversations actualisées · %1\$d",
    )

    /** Ce que la déconnexion dit avant d'agir. */
    private val disconnect = linkedMapOf(
        "Disconnect from this gateway?" to "Se déconnecter de cette passerelle ?",
        "Onyx goes back to its setup screen. The address and keys stay in its settings, and nothing is removed from the server." to
            "Onyx revient à son écran de configuration. L'adresse et les clés restent dans ses réglages, et rien n'est supprimé du serveur.",
        "Couldn't refresh the conversations." to "Impossible d'actualiser les conversations.",
    )

    @Test
    fun `le titre d etat et les cinq rangees sont en francais, et restent en anglais`() {
        labels.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `la ligne d etat composee est en francais — le compte a rebours et la latence compris`() {
        templates.forEach { (english, french) ->
            assertNotEquals(
                "le gabarit « $english » ne doit pas rester en anglais",
                english,
                OnyxLocalization.translate(english, fr),
            )
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
        }
        // Composés pour de vrai, avec des chiffres réels : c'est ce que la feuille affiche.
        assertEquals(
            "42 ms",
            OnyxLocalization.format("%1\$d ms", 42L, fr),
        )
        assertEquals(
            "nouvelle tentative dans 28 s",
            OnyxLocalization.format("new attempt in %1\$d s", 28, fr),
        )
        assertEquals(
            "Conversations actualisées · 12",
            OnyxLocalization.format("Conversations refreshed · %1\$d", 12, fr),
        )
    }

    @Test
    fun `la deconnexion s annonce en francais avant d agir`() {
        disconnect.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
            assertEquals("anglais : $english", english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }
}
