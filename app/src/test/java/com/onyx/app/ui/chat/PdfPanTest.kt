package com.onyx.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le déplacement d'une page PDF agrandie : les bornes, et leur comportement aux extrêmes.
 *
 * La règle est simple mais se casse sans bruit : à un facteur `z`, une page qui remplit la fenêtre
 * dépasse de `(z − 1) / 2` de chaque côté — c'est exactement ce qu'on peut parcourir, ni plus (on
 * verrait du vide) ni moins (le bord de la feuille resterait inatteignable).
 */
class PdfPanTest {

    @Test
    fun `sans zoom il n y a rien a deplacer`() {
        assertEquals(0f, PdfPan.limit(1000, 1f), 0.001f)
        assertEquals(0f, PdfPan.clamp(120f, 1000, 1f), 0.001f)
        assertEquals(0f, PdfPan.clamp(-120f, 1000, 1f), 0.001f)
    }

    @Test
    fun `la marge de deplacement suit le facteur`() {
        assertEquals(500f, PdfPan.limit(1000, 2f), 0.001f)
        assertEquals(1500f, PdfPan.limit(1000, 4f), 0.001f)
        // Un zoom sous 1 ne se produit pas, mais s'il arrivait il ne donnerait pas de marge négative.
        assertEquals(0f, PdfPan.limit(1000, 0.4f), 0.001f)
        assertEquals(0f, PdfPan.limit(0, 3f), 0.001f)
    }

    @Test
    fun `le deplacement reste dans la fenetre`() {
        assertEquals(500f, PdfPan.clamp(900f, 1000, 2f), 0.001f)
        assertEquals(-500f, PdfPan.clamp(-900f, 1000, 2f), 0.001f)
        assertEquals(120f, PdfPan.clamp(120f, 1000, 2f), 0.001f)
    }

    @Test
    fun `revenir a Fit ramene la page a son coin`() {
        // C'est ce que fait le lecteur en rétrécissant : la marge tombe à zéro, donc le déplacement aussi.
        val enGrand = PdfPan.clamp(700f, 1000, 2f)
        assertTrue(enGrand > 0f)
        assertEquals(0f, PdfPan.clamp(enGrand, 1000, 1f), 0.001f)
    }
}
