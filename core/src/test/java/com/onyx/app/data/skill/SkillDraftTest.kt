package com.onyx.app.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles de conversion, une par défaut qu'elles évitent.
 *
 * Les tests parlent du **texte rendu**, jamais d'un modèle intermédiaire : c'est le brouillon que
 * l'utilisateur lira, et c'est donc lui qui doit être juste. Chaque assertion nomme la ligne
 * attendue en entier quand elle le peut — un `contains` sur un fragment laisserait passer une ligne
 * qui dit la bonne chose au mauvais endroit.
 */
class SkillDraftTest {

    // --- Appuis -------------------------------------------------------------------------------

    @Test
    fun `un appui nomme se lit par son libelle, sans position`() {
        val md = draft(TracedAction.Tap(540f, 240f, "Nouvelle facture"))

        assertEquals(
            """
            # Démonstration enregistrée

            Reproduire l'étape enregistrée, dans l'ordre où elle a été faite.

            1. Tap "Nouvelle facture"
            """.trimIndent() + "\n",
            md,
        )
    }

    @Test
    fun `un appui muet donne des pourcentages et se marque a relire`() {
        val md = draft(TracedAction.Tap(453.6f, 432f), width = 1080, height = 2400)

        assertTrue(md.contains("1. Tap at 42% / 18% of the window — à relire : libellé absent"))
        assertTrue(md.contains("> À relire : l'étape 1."))
    }

    @Test
    fun `un libelle fait d'espaces vaut un libelle absent`() {
        val md = draft(TracedAction.Tap(540f, 1200f, "   \n\t "))

        assertTrue(md.contains("— à relire : libellé absent"))
        assertFalse(md.contains("Tap \"\""))
    }

    @Test
    fun `une fenetre inconnue ne fabrique pas de pourcentage`() {
        // Sans dimension, « 0% / 0% » désignerait le coin haut-gauche : c'est un mensonge.
        val md = draft(TracedAction.Tap(540f, 240f), width = 0, height = 0)

        assertTrue(md.contains("1. Tap at an unknown position — à relire : fenêtre de taille inconnue"))
        assertFalse(md.contains("%"))
    }

    @Test
    fun `un appui hors fenetre est borne a cent pour cent`() {
        val md = draft(TracedAction.Tap(4000f, -120f), width = 1080, height = 2400)

        assertTrue(md.contains("Tap at 100% / 0% of the window"))
    }

    @Test
    fun `un libelle multiligne ne casse pas la liste`() {
        val md = draft(
            TracedAction.Tap(1f, 1f, "Ouvrir\nle menu"),
            TracedAction.Tap(1f, 1f, "Fermer"),
        )

        // Le libellé est ramené à une ligne : deux pas numérotés, pas trois lignes dont une vide.
        assertEquals(listOf("1. Tap \"Ouvrir le menu\"", "2. Tap \"Fermer\""), numbered(md))
    }

    // --- Textes -------------------------------------------------------------------------------

    @Test
    fun `un texte normal est recopie tel quel`() {
        val md = draft(TracedAction.Type("1 240,00 €"))

        assertTrue(md.contains("1. Type \"1 240,00 €\""))
        // Sortie en UTF-8 : l'euro reste l'euro, il n'est pas transformé en entité.
        assertTrue(md.contains("€"))
    }

    @Test
    fun `guillemets et retours sont echappes plutot que supprimes`() {
        val md = draft(TracedAction.Type("il a dit \"non\"\npuis rien"))

        assertTrue(md.contains("1. Type \"il a dit \\\"non\\\"\\npuis rien\""))
        // Un seul pas : le retour à la ligne n'a pas coupé la liste.
        assertEquals(1, numbered(md).size)
    }

    @Test
    fun `un texte vide se signale au lieu de faire un pas muet`() {
        val md = draft(TracedAction.Type(""))

        assertTrue(md.contains("1. Type \"\" — à relire : texte vide"))
    }

    @Test
    fun `un texte secret ne laisse aucune trace du texte saisi`() {
        // Le test le plus important du fichier : si celui-ci tombe, un mot de passe est sorti de
        // l'appareil. On cherche le texte entier, puis chacun de ses fragments de quatre lettres —
        // un masquage partiel (« mot-•••• ») ne doit pas suffire à passer.
        val secret = "Correct-Horse-Battery-9"
        val md = draft(TracedAction.Type(secret, isSecret = true))

        assertFalse(md.contains(secret))
        for (start in 0..secret.length - 4) {
            assertFalse(
                "fragment du secret dans le brouillon : ${secret.substring(start, start + 4)}",
                md.contains(secret.substring(start, start + 4)),
            )
        }
        assertTrue(md.contains("1. Type •••• — secret, non enregistré"))
    }

    @Test
    fun `un secret ne dit pas non plus sa longueur`() {
        val court = draft(TracedAction.Type("1234", isSecret = true))
        val long = draft(TracedAction.Type("1".repeat(64), isSecret = true))

        assertEquals(court, long)
        assertEquals(4, court.count { it == '•' })
    }

    @Test
    fun `le secret ne se marque pas a relire, il est complet`() {
        val md = draft(TracedAction.Type("hunter2", isSecret = true))

        assertFalse(md.contains("à relire"))
    }

    // --- Attentes -----------------------------------------------------------------------------

    @Test
    fun `les attentes courtes consecutives ne font qu'un pas`() {
        val md = draft(
            TracedAction.Wait(120),
            TracedAction.Wait(180),
            TracedAction.Wait(200),
            TracedAction.Tap(1f, 1f, "Suivant"),
        )

        assertEquals(listOf("1. Wait 500 ms", "2. Tap \"Suivant\""), numbered(md))
    }

    @Test
    fun `une attente longue reste son propre pas et coupe la serie`() {
        val md = draft(
            TracedAction.Wait(100),
            TracedAction.Wait(100),
            TracedAction.Wait(2500),
            TracedAction.Wait(100),
        )

        assertEquals(listOf("1. Wait 200 ms", "2. Wait 2500 ms", "3. Wait 100 ms"), numbered(md))
    }

    @Test
    fun `une attente nulle n'est pas une instruction`() {
        val md = draft(TracedAction.Wait(0), TracedAction.Tap(1f, 1f, "Valider"))

        assertEquals(listOf("1. Tap \"Valider\""), numbered(md))
    }

    @Test
    fun `une attente ronde se lit en secondes, sinon en millisecondes`() {
        val md = draft(TracedAction.Wait(2000), TracedAction.Wait(1500))

        // Deux attentes longues ne fusionnent pas : chacune est un choix de l'utilisateur.
        assertEquals(listOf("1. Wait 2 s", "2. Wait 1500 ms"), numbered(md))
    }

    // --- Défilements --------------------------------------------------------------------------

    @Test
    fun `les defilements consecutifs de meme sens se cumulent`() {
        val md = draft(
            TracedAction.Scroll(ScrollDirection.DOWN, 540),
            TracedAction.Scroll(ScrollDirection.DOWN, 220),
            TracedAction.Scroll(ScrollDirection.DOWN, 40),
        )

        assertEquals(listOf("1. Scroll down 800 px"), numbered(md))
    }

    @Test
    fun `un defilement de sens contraire coupe la serie`() {
        val md = draft(
            TracedAction.Scroll(ScrollDirection.DOWN, 100),
            TracedAction.Scroll(ScrollDirection.UP, 50),
            TracedAction.Scroll(ScrollDirection.DOWN, 10),
        )

        assertEquals(
            listOf("1. Scroll down 100 px", "2. Scroll up 50 px", "3. Scroll down 10 px"),
            numbered(md),
        )
    }

    @Test
    fun `une attente entre deux defilements les separe`() {
        // Fusion sur les seuls voisins : la pause dit qu'on a regardé entre les deux.
        val md = draft(
            TracedAction.Scroll(ScrollDirection.DOWN, 100),
            TracedAction.Wait(200),
            TracedAction.Scroll(ScrollDirection.DOWN, 100),
        )

        assertEquals(
            listOf("1. Scroll down 100 px", "2. Wait 200 ms", "3. Scroll down 100 px"),
            numbered(md),
        )
    }

    @Test
    fun `un defilement sans deplacement disparait et un montant negatif est une longueur`() {
        val md = draft(
            TracedAction.Scroll(ScrollDirection.DOWN, 0),
            TracedAction.Scroll(ScrollDirection.LEFT, -75),
        )

        assertEquals(listOf("1. Scroll left 75 px"), numbered(md))
    }

    // --- Navigation et touches ----------------------------------------------------------------

    @Test
    fun `une navigation porte son adresse`() {
        val md = draft(TracedAction.Navigate("https://onyx.example/notes"))

        assertEquals(listOf("1. Navigate to https://onyx.example/notes"), numbered(md))
    }

    @Test
    fun `une navigation sans adresse se signale`() {
        val md = draft(TracedAction.Navigate("  "))

        assertEquals(
            listOf("1. Navigate to an unknown address — à relire : adresse absente"),
            numbered(md),
        )
    }

    @Test
    fun `les touches systeme se lisent en clair`() {
        val md = draft(
            TracedAction.Key(NamedKey.BACK),
            TracedAction.Key(NamedKey.HOME),
            TracedAction.Key(NamedKey.ENTER),
        )

        assertEquals(listOf("1. Press Back", "2. Press Home", "3. Press Enter"), numbered(md))
    }

    // --- Journal vide -------------------------------------------------------------------------

    @Test
    fun `un journal vide le dit au lieu de produire un skill vide`() {
        val md = skillDraftMarkdown(ActionJournal(windowWidth = 1080, windowHeight = 2400))

        assertEquals(
            """
            # Démonstration vide

            Aucune action n'a été enregistrée : il n'y a rien à rejouer, donc rien à transformer en compétence.

            _Aucune étape._
            """.trimIndent() + "\n",
            md,
        )
        assertTrue(numbered(md).isEmpty())
    }

    @Test
    fun `un journal qui ne contient que des actions sans effet est vide aussi`() {
        val md = draft(TracedAction.Wait(0), TracedAction.Wait(-5))

        assertTrue(md.contains("Aucune action n'a été enregistrée"))
        assertTrue(numbered(md).isEmpty())
    }

    // --- Titre, ordre, déterminisme -----------------------------------------------------------

    @Test
    fun `le titre fourni est ramene a une ligne et debarrasse de ses dieses`() {
        val md = skillDraftMarkdown(
            ActionJournal(
                actions = listOf(TracedAction.Key(NamedKey.BACK)),
                title = "  ## Créer\nune facture  ",
            ),
        )

        assertTrue(md.startsWith("# Créer une facture\n\n"))
        assertFalse(md.contains("##"))
    }

    @Test
    fun `un titre fait d'espaces retombe sur le titre par defaut`() {
        val md = skillDraftMarkdown(
            ActionJournal(actions = listOf(TracedAction.Key(NamedKey.BACK)), title = "   "),
        )

        assertTrue(md.startsWith("# $SKILL_DRAFT_DEFAULT_TITLE\n\n"))
    }

    @Test
    fun `la numerotation suit le journal, sans trou apres les fusions`() {
        val md = draft(
            TracedAction.Navigate("https://onyx.example"),
            TracedAction.Wait(100),
            TracedAction.Wait(100),
            TracedAction.Scroll(ScrollDirection.DOWN, 10),
            TracedAction.Scroll(ScrollDirection.DOWN, 10),
            TracedAction.Key(NamedKey.BACK),
        )

        assertEquals(
            listOf(
                "1. Navigate to https://onyx.example",
                "2. Wait 200 ms",
                "3. Scroll down 20 px",
                "4. Press Back",
            ),
            numbered(md),
        )
    }

    @Test
    fun `deux rendus du meme journal sont identiques a l'octet`() {
        val actions = listOf(
            TracedAction.Tap(0.5f, 0.25f, "Enregistrer"),
            TracedAction.Type("été", isSecret = false),
            TracedAction.Scroll(ScrollDirection.RIGHT, 33),
        )

        val premier = skillDraftMarkdown(ActionJournal(actions, 1080, 2400, "T"))
        val second = skillDraftMarkdown(ActionJournal(actions.toList(), 1080, 2400, "T"))

        assertEquals(premier, second)
        // Une fin de ligne, une seule : le brouillon est un fichier texte, pas un bloc de texte.
        assertTrue(premier.endsWith("\n"))
        assertFalse(premier.endsWith("\n\n"))
    }

    @Test
    fun `plusieurs pas a relire sont tous listes dans la note finale`() {
        val md = draft(
            TracedAction.Tap(1f, 1f, "Ouvrir"),
            TracedAction.Tap(540f, 1200f),
            TracedAction.Key(NamedKey.BACK),
            TracedAction.Tap(108f, 240f),
        )

        assertTrue(md.contains("> À relire : les étapes 2 et 4."))
    }

    // --- Outils -------------------------------------------------------------------------------

    /** Un brouillon dont les actions sont celles données, dans une fenêtre de téléphone courante. */
    private fun draft(
        vararg actions: TracedAction,
        width: Int = 1080,
        height: Int = 2400,
        title: String? = null,
    ): String = skillDraftMarkdown(ActionJournal(actions.toList(), width, height, title))

    /** Les lignes numérotées du brouillon, dans l'ordre — les seules qui portent une étape. */
    private fun numbered(markdown: String): List<String> =
        markdown.lines().filter { line -> NUMBERED.containsMatchIn(line) }

    private companion object {
        val NUMBERED = Regex("^\\d+\\. ")
    }
}
