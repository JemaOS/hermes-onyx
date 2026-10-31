package com.onyx.app.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le **choix du modèle** : la recherche, l'ordre (favoris, récents, le reste), la puce du
 * compositeur et les **crans de réflexion**.
 *
 * Ce fichier vérifie la règle du propriétaire, chiffres à l'appui : **aucun prix n'est inventé**,
 * et les **crans de la référence absents du serveur ne sont jamais dessinés** — ils sont refusés
 * proprement et dits à part.
 */
class ModelSelectionTest {

    private fun choice(id: String, context: Int? = null, inPrice: Double? = null, outPrice: Double? = null) =
        ModelChoice(id = id, contextLength = context, promptPricePerMTok = inPrice, completionPricePerMTok = outPrice)

    // — Le fournisseur, le nom, le monogramme —

    @Test
    fun `le fournisseur et le nom se lisent dans l identifiant`() {
        assertEquals("openai", ModelChoice.providerOf("openai/gpt-5"))
        assertEquals("gpt-5", ModelChoice.labelOf("openai/gpt-5"))
        assertEquals("local", ModelChoice.providerOf("gpt-5"))
        assertEquals("gpt-5", ModelChoice.labelOf("gpt-5"))
    }

    @Test
    fun `le monogramme est la premiere lettre, jamais un carre vide`() {
        assertEquals("G", choice("openai/gpt-5").monogram)
        assertEquals("S", choice("sage-1.5").monogram)
        assertEquals("?", ModelChoice.monogramOf(""))
    }

    // — Aucun prix n'est inventé —

    @Test
    fun `sans tarif, aucun prix ne se peint`() {
        val bare = choice("openai/gpt-5")
        assertFalse(bare.hasPrice)
        assertNull("la ligne de prix doit rester muette", bare.priceLine())
        // Le sous-titre montre ce qui existe : le fournisseur seul.
        assertEquals("openai", bare.subtitle())
    }

    @Test
    fun `avec un tarif donne par le serveur, la ligne le dit`() {
        val priced = choice("openai/gpt-5", inPrice = 3.0, outPrice = 15.0)
        assertTrue(priced.hasPrice)
        assertEquals("\$3 in · \$15 out", priced.priceLine())
        assertEquals("openai · \$3 in · \$15 out", priced.subtitle())
    }

    @Test
    fun `sans prix mais avec une fenetre de contexte, la ligne montre la fenetre`() {
        val withContext = choice("anthropic/claude-4", context = 200_000)
        assertNull(withContext.priceLine())
        assertEquals("anthropic · 200k", withContext.subtitle())
    }

    // — La recherche : par nom, ou par fournisseur —

    @Test
    fun `la recherche retient par nom de modele ou de fournisseur`() {
        val all = listOf(
            choice("openai/gpt-5"),
            choice("anthropic/claude-4"),
            choice("local/sage-1.5"),
        )
        assertEquals(listOf("openai/gpt-5"), ModelSelection.filter(all, "gpt-5").map { it.id })
        assertEquals(listOf("anthropic/claude-4"), ModelSelection.filter(all, "anthropic").map { it.id })
        // La casse ne compte pas, et le préfixe du fournisseur se cherche aussi dans l'identifiant.
        assertEquals(3, ModelSelection.filter(all, "  ").size)
        assertEquals(listOf("local/sage-1.5"), ModelSelection.filter(all, "SAGE").map { it.id })
    }

    // — L'ordre : favoris, récents, le reste —

    @Test
    fun `les favoris passent en tete puis les recents, et rien ne se dedouble`() {
        val all = listOf(
            choice("z/model-z"),
            choice("a/model-a"),
            choice("b/model-b"),
            choice("c/model-c"),
        )
        val sections = ModelSelection.sections(
            choices = all,
            favourites = setOf("c/model-c"),
            recents = listOf("b/model-b", "c/model-c"),
        )
        assertEquals(listOf("c/model-c"), sections.favourites.map { it.id })
        assertEquals("un favori récent n'est pas recopié dans les récents", listOf("b/model-b"), sections.recents.map { it.id })
        // Le reste garde l'ordre de la source (le catalogue d'un profil écrit son modèle d'abord).
        assertEquals(listOf("z/model-z", "a/model-a"), sections.rest.map { it.id })

        val ordered = ModelSelection.ordered(all, setOf("c/model-c"), listOf("b/model-b", "c/model-c"))
        assertEquals(listOf("c/model-c", "b/model-b", "z/model-z", "a/model-a"), ordered.map { it.id })
    }

    @Test
    fun `un identifiant garde mais disparu de la liste est ignore`() {
        val sections = ModelSelection.sections(
            choices = listOf(choice("a/model-a")),
            favourites = setOf("gone/model"),
            recents = listOf("gone/model"),
        )
        assertTrue(sections.favourites.isEmpty())
        assertTrue(sections.recents.isEmpty())
        assertEquals(listOf("a/model-a"), sections.rest.map { it.id })
    }

    // — Les crans de réflexion : ceux du serveur, pas ceux de la référence —

    @Test
    fun `la reference montre sept crans et le serveur en connait six nommes plus Default et None`() {
        assertEquals(7, ReasoningLevels.referenced.size)
        val plan = ReasoningLevels.plan()
        // Les puces sont **exactement** la liste du serveur : rien n'est ajouté.
        assertEquals(ReasoningLevels.server, plan.chips)
        // « Ultra » de la référence n'existe pas côté serveur : il est déclaré manquant, jamais dessiné.
        assertEquals(listOf("Ultra"), plan.missing)
        assertFalse("un cran absent n'est jamais accepté", ReasoningLevels.accepts("ultra"))
    }

    @Test
    fun `les crans absents sont refuses proprement et la liste disponible est proposee`() {
        // Un serveur qui ne connaît que quatre crans : les trois autres ne sont pas inventés.
        val reduced = listOf(
            "minimal" to "Minimal",
            "low" to "Low",
            "medium" to "Medium",
            "high" to "High",
        )
        val plan = ReasoningLevels.plan(reduced)
        assertEquals(reduced, plan.chips)
        assertEquals(listOf("Extra high", "Max", "Ultra"), plan.missing)
        assertTrue(ReasoningLevels.accepts("high", reduced))
        assertFalse(ReasoningLevels.accepts("xhigh", reduced))
        assertFalse(ReasoningLevels.accepts("max", reduced))
    }

    @Test
    fun `le cran de la reference correspond a celui du serveur`() {
        assertEquals("xhigh", ReasoningLevels.mapping["Extra high"])
        assertEquals("X-High", ReasoningLevels.label("xhigh"))
        // Une valeur inconnue se rend telle quelle : l'app ne l'invente pas non plus.
        assertEquals("ultra", ReasoningLevels.label("ultra"))
    }

    // — La puce du compositeur : le libellé, et le débordement —

    @Test
    fun `le libelle de la puce joint le modele et la reflexion`() {
        assertEquals("Sage A1.5 | Medium", ModelChip.label("Sage A1.5", "Medium"))
        // Un niveau « Default » ne s'écrit pas : la puce dit le seul modèle.
        assertEquals("Sage A1.5", ModelChip.label("Sage A1.5", "Default"))
        assertEquals("Sage A1.5", ModelChip.label("Sage A1.5", ""))
        assertEquals("model | Medium", ModelChip.label(null, "Medium"))
    }

    /** Le modèle de mesure du texte : la largeur croît avec la taille, comme un vrai rendu. */
    private fun widthOf(text: String, size: Float): Float = text.length * size * 0.62f

    @Test
    fun `la puce ne deborde jamais, a 1,0 comme a 1,3 fois`() {
        val label = ModelChip.label("anthropic/claude-4-sonnet", "X-High")
        // Les deux échelles que la garde du propriétaire exige.
        for (fontScale in listOf(1.0f, 1.3f)) {
            for (available in listOf(320f, 220f, 170f, 130f)) {
                val base = 12f * fontScale
                val floor = (ChipRowFit.FLOOR_SP / fontScale).coerceAtMost(base)
                val plan = ModelChip.plan(
                    label = label,
                    availableDp = available,
                    chipInsetDp = 36f,
                    gapDp = 0f,
                    floorSp = floor,
                ) { text, size -> widthOf(text, size) }
                assertTrue(
                    "la puce ne tient pas : fontScale=$fontScale available=$available",
                    plan.fits,
                )
                val kept = plan.labels.first()
                // La puce tient dans la place qui reste…
                assertTrue(
                    "déborde : fontScale=$fontScale available=$available kept=« $kept »",
                    widthOf(kept, plan.fontSizeSp) + 36f <= available + 0.001f,
                )
                // …et un mot n'est jamais coupé au milieu : le libellé reste une suite de mots
                // entiers, éventuellement précédée de l'ellipse d'abrègement.
                val words = kept.removePrefix("…").trim().split(' ')
                assertTrue("un mot est coupé : « $kept »", words.all { word -> label.contains(word) })
            }
        }
    }

    @Test
    fun `meme a l etroit, la puce ne coupe jamais un mot`() {
        val label = ModelChip.label("anthropic/claude-4-sonnet", "X-High")
        val plan = ModelChip.plan(label, availableDp = 80f, chipInsetDp = 36f, gapDp = 0f) { text, size ->
            widthOf(text, size)
        }
        val kept = plan.labels.first()
        val words = kept.removePrefix("…").trim().split(' ')
        assertTrue("un mot est coupé : « $kept »", words.all { word -> label.contains(word) })
        assertFalse("plus rien ne se coupe du tout : « $kept »", kept.contains("  "))
    }

    @Test
    fun `la puce entiere reste entiere quand il y a la place`() {
        val label = "Sage A1.5 | Medium"
        val plan = ModelChip.plan(
            label = label,
            availableDp = 600f,
            chipInsetDp = 36f,
            gapDp = 0f,
        ) { text, size -> widthOf(text, size) }
        assertEquals(label, plan.labels.first())
    }

    // — Les profils : le regroupement, la clé de liste, l'état —

    @Test
    fun `les modeles se regroupent par profil, le meme identifiant reste distinct`() {
        val rows = listOf(
            ModelChoice(id = "sage-a1.5", provider = "lmstudio", profile = "default"),
            ModelChoice(id = "deepseek/deepseek-v4.1-flash", provider = "kilocode", profile = "kilo"),
            ModelChoice(id = "deepseek/deepseek-v4.1-flash", provider = "kilocode", profile = "scout"),
        )
        val groups = ModelSelection.groups(rows)
        assertEquals(listOf("default", "kilo", "scout"), groups.map { it.profile })
        assertEquals(listOf("sage-a1.5"), groups.first().choices.map { it.id })
        // Le même identifiant sous deux profils : deux clés de liste, aucune collision.
        val keys = rows.map { it.listKey }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("kilo/deepseek/deepseek-v4.1-flash", rows[1].listKey)

        // La feuille écrit le profil **sur chaque rangée**.
        assertEquals("default · lmstudio", rows[0].rowSubtitle())
        assertEquals("kilo · kilocode", rows[1].rowSubtitle())
    }

    @Test
    fun `la recherche retient aussi par profil`() {
        val rows = listOf(
            ModelChoice(id = "sage-a1.5", provider = "lmstudio", profile = "default"),
            ModelChoice(id = "gpt-5", provider = "kilocode", profile = "kilo"),
        )
        assertEquals(listOf("sage-a1.5"), ModelSelection.filter(rows, "default").map { it.id })
        assertEquals(listOf("gpt-5"), ModelSelection.filter(rows, "kilo").map { it.id })
    }

    @Test
    fun `un profil injoignable dit son etat, un profil normal ne dit rien`() {
        assertNull(ModelSelection.profileSubtitle(ProfileView("kilo", ProfileState.READY)))
        assertEquals(
            "unreachable",
            ModelSelection.profileSubtitle(ProfileView("ghost", ProfileState.UNREACHABLE)),
        )
        assertEquals(
            "model list unavailable",
            ModelSelection.profileSubtitle(ProfileView("kilo", ProfileState.PARTIAL)),
        )
    }
}
