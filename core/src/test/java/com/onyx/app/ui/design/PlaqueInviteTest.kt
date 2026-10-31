package com.onyx.app.ui.design

import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le libellé du champ de la plaque : celui de la planche, et jamais une coupure.**
 *
 * La planche écrit « Écrire à Onyx… » (`fil-clair.html:479`). L'app montrait sa propre clé traduite.
 * Le libellé de la planche est donc ajouté au dictionnaire central — mais la plaque porte **quatre**
 * commandes dans la rangée du champ, si bien qu'à **1,3× de police** le champ ne garde plus assez de
 * place pour la phrase entière.
 *
 * Le brief a prévu ce cas, mot pour mot : « si ça ne tient réellement pas à 1,3× de police, réduis la
 * longueur du texte d'invite plutôt que de supprimer un bouton, en le disant ». C'est donc **la
 * longueur du texte** qui cède — et `PlaqueInvite.key` le décide **par la mesure**, avec la largeur
 * réellement offerte et la largeur réellement rendue. Ce test est ce qui empêche la décision de
 * devenir un chiffre écrit à la main quelque part.
 */
class PlaqueInviteTest {

    /** Les largeurs rendues, mesurées sur l'appareil à 1,3× : la pleine, puis la courte. */
    private val measured: Map<String, Float> = mapOf(
        PlaqueInvite.FULL to 130.2f,
        PlaqueInvite.SHORT to 116.7f,
    )

    private val width: (String) -> Float = { text -> measured.getValue(text) }

    @Test
    fun `le libelle de la planche est ecrit mot pour mot, et traduit`() {
        assertEquals("la planche", "Write to Onyx…", PlaqueInvite.FULL)
        assertEquals("Écrire à Onyx…", OnyxLocalization.translate(PlaqueInvite.FULL, Locale.FRANCE))
        assertEquals("Écrire à Onyx", OnyxLocalization.translate(PlaqueInvite.SHORT, Locale.FRANCE))
    }

    @Test
    fun `la version courte est la meme phrase sans l ellipse`() {
        assertEquals(
            "l'ellipse seule tombe : les mots de la planche restent",
            PlaqueInvite.FULL.removeSuffix("…"),
            PlaqueInvite.SHORT,
        )
        assertTrue("et elle est réellement plus courte", width(PlaqueInvite.SHORT) < width(PlaqueInvite.FULL))
    }

    @Test
    fun `quand la place porte la phrase entiere, c est elle qui est posee`() {
        // À 1,0× de police, la rangée du champ laisse ≈ 170 dp : rien à raccourcir.
        assertEquals(PlaqueInvite.FULL, PlaqueInvite.key(roomDp = 170f, width = width))
    }

    @Test
    fun `a 1,3x la place manque et l ellipse tombe`() {
        // La plaque à quatre commandes laisse 127,4 dp : c'est la mesure de l'appareil, pas une
        // estimation, et c'est exactement le cas que le brief avait prévu.
        assertEquals(PlaqueInvite.SHORT, PlaqueInvite.key(roomDp = 127.43f, width = width))
    }

    @Test
    fun `une place nulle ne fait pas disparaitre le libelle`() {
        assertEquals(PlaqueInvite.SHORT, PlaqueInvite.key(roomDp = 0f, width = width))
    }

    @Test
    fun `le libelle ne depend pas d un chiffre ecrit dans la table`() {
        // La règle est une fonction **pure** : elle reçoit la place et la mesure. Deux largeurs
        // différentes pour la même place donnent donc deux réponses différentes — c'est ce qui
        // prouve que c'est la mesure qui décide.
        assertEquals(
            PlaqueInvite.FULL,
            PlaqueInvite.key(roomDp = 127.43f) { if (it == PlaqueInvite.FULL) 120f else 100f },
        )
    }

    @Test
    fun `en anglais les deux cles se lisent telles quelles`() {
        // Les clés **sont** l'anglais : la plaque ne montre jamais une phrase française sous un
        // appareil anglais, et le repli verbatim du dictionnaire est ce qui le garantit.
        assertEquals("Write to Onyx…", OnyxLocalization.translate(PlaqueInvite.FULL, Locale.ENGLISH))
        assertEquals("Write to Onyx", OnyxLocalization.translate(PlaqueInvite.SHORT, Locale.ENGLISH))
    }

    @Test
    fun `la version courte est la pleine sans l ellipse, dans les deux langues`() {
        // C'est la **seule** différence entre les deux clés, et elle vaut dans les deux langues :
        // rien d'autre ne change, donc rien d'autre ne peut dériver d'une langue à l'autre.
        listOf(Locale.FRANCE, Locale.ENGLISH).forEach { locale ->
            val full = OnyxLocalization.translate(PlaqueInvite.FULL, locale)
            val short = OnyxLocalization.translate(PlaqueInvite.SHORT, locale)
            assertEquals("en $locale, la clé courte est la pleine sans l'ellipse", full.removeSuffix("…"), short)
        }
    }
}
