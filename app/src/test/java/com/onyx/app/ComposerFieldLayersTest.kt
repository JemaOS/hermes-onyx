package com.onyx.app

import com.onyx.app.ui.design.ComposerFieldInvite
import com.onyx.app.ui.design.composerFieldInvite
import com.onyx.app.ui.design.composerFieldTexts
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du compositeur : une seule couche de texte, et rien qui se recouvre.**
 *
 * Le défaut relevé sur la capture du propriétaire — le seul angle mort du chantier : **aucun lot
 * n'avait jamais photographié le champ pendant l'attente du serveur**. Pendant l'attente, deux
 * textes étaient écrits au même endroit (« En attente du serveur » par-dessus « Écrire à Onyx… »),
 * la tuile d'activité de gauche mordait le début du texte, et le bouton d'envoi était devenu un
 * carré rouge.
 *
 * Ce fichier a **trois dents**, et chacune mord :
 *
 * 1. **La couche unique** ([composerFieldTexts]) : pour **toutes** les combinaisons d'état, le champ
 *    n'écrit jamais plus d'**un** libellé. La fonction qui compte est la même que celle du rendu —
 *    donc le chiffre du rapport et le chiffre vérifié sont le même.
 * 2. **La carte d'activité est RETIRÉE** (source) : elle dupliquait la réflexion du fil et son
 *    arrêt. La réserve du fil au-dessus du compositeur ne mesure donc plus que le champ, et une
 *    réintroduction de la carte est **refusée** — c'est la dent du verrou de source unique
 *    ([reflexionViolations]).
 * 3. **Le compte des libellés du champ** (source) : le champ ne dessine qu'**un** `BasicText` dans
 *    sa couche. La dent est éprouvée par un contrôle négatif : sur la source qui porte **le défaut**
 *    (l'invite *et* l'état écrits ensemble), le compte vaut 2 et le contrôle refuse.
 */
class ComposerFieldLayersTest {

    private fun read(path: String) = File(path).readText()

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")

    // =============================================================================================
    // 1. LA COUCHE UNIQUE DU CHAMP
    // =============================================================================================

    /** Les 2⁵ combinaisons d'état que le champ peut recevoir. */
    private fun allStates(): List<Triple<Boolean, Boolean, Boolean>> = buildList {
        listOf(false, true).forEach { dictating ->
            listOf(false, true).forEach { streaming ->
                listOf(false, true).forEach { canSteer ->
                    add(Triple(dictating, streaming, canSteer))
                }
            }
        }
    }

    @Test
    fun `le champ n ecrit jamais deux libelles, pour aucun etat`() {
        val report = StringBuilder("\nTABLE DES ÉTATS DU CHAMP — ce qui s'écrit, et ce qui cède\n")
        report.append("brouillon  dictee  reponse  pilotage  hors ligne  → invite            textes\n")
        var checked = 0
        listOf(false, true).forEach { draftBlank ->
            listOf(false, true).forEach { dictating ->
                listOf(false, true).forEach { streaming ->
                    listOf(false, true).forEach { canSteer ->
                        listOf(false, true).forEach { offline ->
                            val invite = composerFieldInvite(
                                draftBlank = draftBlank,
                                dictating = dictating,
                                streaming = streaming,
                                canSteer = canSteer,
                                offline = offline,
                                group = true,
                            )
                            // Le mot que le rendu écrirait pour cette invite (le seul qu'il reçoive).
                            val word = if (invite.writes) "« ${invite.name} »" else null
                            val texts = composerFieldTexts(
                                draft = if (draftBlank) "" else "bonjour",
                                invite = invite,
                                inviteText = word,
                            )
                            checked += 1
                            report.append(
                                "%-10s %-7s %-8s %-9s %-11s → %-17s %d\n".format(
                                    if (draftBlank) "vide" else "rempli", dictating, streaming,
                                    canSteer, offline, invite.name, texts.size,
                                ),
                            )
                            assertTrue(
                                "deux libellés dessinés dans le champ : $texts",
                                texts.size <= 1,
                            )
                            if (!draftBlank) {
                                assertEquals(
                                    "le brouillon écrit seul : aucune invite ne s'y ajoute",
                                    listOf("bonjour"),
                                    texts,
                                )
                                assertEquals(
                                    "un brouillon non vide fait céder l'invite",
                                    ComposerFieldInvite.NONE,
                                    invite,
                                )
                            }
                        }
                    }
                }
            }
        }
        assertEquals("les 32 combinaisons doivent être couvertes", 32, checked)
        print(report)
    }

    @Test
    fun `le controle mord — l invite et le brouillon dessines ensemble sont refuses`() {
        // Le défaut exact de la capture : l'invite **et** un état, écrits en même temps.
        val broken = composerFieldTexts(
            draft = "Écrire à Onyx…",
            invite = ComposerFieldInvite.ASK,
            inviteText = "En attente du serveur",
        )
        assertEquals("le défaut porte bien deux textes", 2, broken.size)
        assertTrue(
            "le contrôle doit REFUSER deux libellés : c'est sa morsure",
            broken.size > 1,
        )
    }

    @Test
    fun `les etats qui cedent la place ne dessinent aucun texte`() {
        listOf(ComposerFieldInvite.YIELDED, ComposerFieldInvite.OFFLINE, ComposerFieldInvite.NONE)
            .forEach { invite ->
                assertFalse("$invite ne doit rien écrire", invite.writes)
                assertTrue(
                    "$invite doit rendre un champ vide",
                    composerFieldTexts("", invite, "n'importe quoi").isEmpty(),
                )
            }
        listOf(ComposerFieldInvite.ASK, ComposerFieldInvite.GROUP,
            ComposerFieldInvite.DICTATION, ComposerFieldInvite.STEER).forEach { invite ->
            assertTrue("$invite écrit son mot", invite.writes)
        }
    }

    @Test
    fun `l ordre des priorites est celui de la table`() {
        // La dictée l'emporte sur l'attente ; l'attente pilotable l'emporte sur l'attente cédée ;
        // hors ligne, c'est l'attente cédée qui gagne ; et le brouillon l'emporte sur tout.
        assertEquals(ComposerFieldInvite.DICTATION, composerFieldInvite(true, true, true, true, true))
        assertEquals(ComposerFieldInvite.STEER, composerFieldInvite(true, false, true, true, true))
        assertEquals(ComposerFieldInvite.YIELDED, composerFieldInvite(true, false, true, false, true))
        assertEquals(ComposerFieldInvite.OFFLINE, composerFieldInvite(true, false, false, false, true))
        assertEquals(ComposerFieldInvite.GROUP, composerFieldInvite(true, false, false, false, false, group = true))
        assertEquals(ComposerFieldInvite.ASK, composerFieldInvite(true, false, false, false, false))
        assertEquals(
            ComposerFieldInvite.NONE,
            composerFieldInvite(false, true, true, true, true, group = true),
        )
    }

    // =============================================================================================
    // 2. L'EMPILEMENT — la carte d'activité réserve sa place, elle ne recouvre pas le champ
    // =============================================================================================

    @Test
    fun `la carte d activite a ete retiree et la reserve ne mesure plus que le champ`() {
        // Ordre du propriétaire : la carte dupliquait la réflexion du fil et son arrêt. Elle n'est
        // plus posée au-dessus du champ.
        assertFalse("la carte d'activité ne doit plus être posée", chat.contains("LiveActivityCard("))
        assertFalse("son titre ne doit plus exister", chat.contains("liveActivityTitle("))

        // Le champ garde sa boîte **de mesure** : c'est elle qui donne `composerHeightPx` et la
        // géométrie de la soudure. Et rien ne se glisse plus entre la plaque et lui.
        val box = chat.indexOf("onSizeChanged { composerHeightPx = it.height }")
        assertTrue("le champ doit garder sa boîte de mesure", box > 0)
        assertTrue(
            "le compositeur doit suivre sa boîte de mesure, sans carte entre les deux",
            chat.indexOf("OnyxComposer(", box) > box,
        )
        // La carte était le **seul** morceau, entre le fil et le champ : il a disparu avec elle.
        assertFalse(
            "la carte ne doit plus être empilée au-dessus du champ",
            chat.contains("state.live?.takeIf { it.isStreaming }?.let { phase ->"),
        )
    }

    @Test
    fun `le verrou de la carte d activite mord sur l arbre d avant`() {
        // Le contrôle négatif : une source qui **réintroduit** la carte d'activité au-dessus du champ
        // — la seconde réflexion et le second arrêt — doit être refusée par le verrou de source
        // unique. C'est la preuve que la dent coupe, et pas seulement qu'elle approuve l'état courant.
        val reintroduced = """
            state.live?.takeIf { it.isStreaming }?.let { phase ->
                LiveActivityCard(title = t, detail = d, onStop = onStop)
            }
        """.trimIndent()
        assertTrue(
            "l'arbre d'avant doit être REFUSÉ : c'est le doublon de la capture",
            reflexionViolations(
                chat = reintroduced,
                threadOrganisation = read("src/main/java/com/onyx/app/ui/chat/components/ThreadOrganisation.kt"),
                messageRow = read("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt"),
                composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt"),
            ).isNotEmpty(),
        )
    }

    // =============================================================================================
    // 3. LE CHAMP NE DESSINE QU'UN LIBELLE — et le contrôle est éprouvé sur le défaut
    // =============================================================================================

    /** **La couche du champ** : de sa décision à la vraie zone de texte — et rien d'autre. */
    private fun conversationField(): String = composer
        .substringAfter("// **La couche du champ")
        .substringBefore("BasicTextField(")

    /** Combien de libellés le champ dessine dans sa couche. */
    private fun labelCount(source: String): Int =
        Regex("""BasicText\(""").findAll(source).count()

    @Test
    fun `le champ d une conversation ne dessine qu un seul libelle`() {
        val field = conversationField()
        assertTrue("le corps du champ doit être trouvé", field.isNotBlank())
        assertEquals(
            "le champ doit n'avoir qu'UNE couche de texte (l'invite, ou rien)",
            1,
            labelCount(field),
        )
        // La couche est **décidée** par la règle pure, jamais par une condition locale.
        assertTrue(
            "le champ doit lire la règle du socle — et savoir que l'invite peut céder",
            composer.contains("ComposerFieldInvite.YIELDED, ComposerFieldInvite.OFFLINE, ComposerFieldInvite.NONE -> null"),
        )
        assertTrue(
            "l'invite doit pouvoir céder (rien n'est écrit quand elle cède)",
            field.contains("placeholder == null -> null") && field.contains("shownInvite != null"),
        )
        assertTrue(
            "et elle n'est dessinée que sur un champ vide",
            field.contains("if (draft.isEmpty() && shownInvite != null)"),
        )
        // **La dent, éprouvée sur le défaut** : le contrôle refuse deux libellés dans la couche.
        assertTrue("le contrôle doit refuser deux libellés", labelCount("""
            BasicText(text = "Écrire à Onyx…")
            BasicText(text = "En attente du serveur")
        """.trimIndent()) > 1)
    }

    @Test
    fun `le controle du nombre de libelles mord sur le defaut`() {
        // Le défaut : une invite **et** la ligne d'état, écrites dans la même couche.
        val broken = """
            if (draft.isEmpty()) {
                BasicText(text = "Écrire à Onyx…")
                BasicText(text = "En attente du serveur")
            }
        """.trimIndent()
        assertEquals("le défaut porte bien deux libellés", 2, labelCount(broken))
        assertTrue(
            "le contrôle doit REFUSER deux libellés dans la couche du champ",
            labelCount(broken) > 1,
        )
    }

    // =============================================================================================
    // 4. LA FORME DE L'ARRÊT — la famille de la maison, pas un carré rouge
    // =============================================================================================

    @Test
    fun `le bouton d arret suit la famille de la maison`() {
        val stop = composer.substringAfter("\"stop\" -> {")
            .substringBefore("\n\n        \"send\" ->")
        assertTrue("l'arrêt garde le glyphe de la maison", stop.contains("symbol = Symbols.Stop,"))
        assertTrue("l'arrêt garde le CERCLE de l'envoi", stop.contains("size = size,"))
        assertFalse(
            "l'arrêt ne prend plus le rouge de l'ÉCHEC : le rouge est le signal du défaut, pas d'un arrêt demandé",
            stop.contains("tint = theme.error") || stop.contains("ink(theme.error)"),
        )
        assertTrue(
            "l'arrêt prend le fond de contrôle de la famille de l'accent, **mesuré contre la plaque** " +
                "(`theme.fill` seul valait 1,10:1 en sombre : le cercle d'arrêt disparaissait)",
            stop.contains("val tint = ComposerCommands.disc(theme, theme.fill)") &&
                stop.contains("tint = tint,") &&
                stop.contains("contentTint = ComposerCommands.inkOnDisc(theme, tint, theme.textColor),"),
        )
        // La même forme que la carte d'activité dessine déjà pour le même geste : une seule
        // grammaire pour l'arrêt dans toute l'app.
        val card = read("src/main/java/com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
        assertTrue(
            "la carte d'activité doit dessiner l'arrêt de la même façon",
            card.contains("background(theme.fill, CircleShape)") &&
                card.contains("OnyxIcon(symbol = Symbols.Stop, tint = theme.textColor"),
        )
    }

    // =============================================================================================
    // 5. LES TEINTES DE L'ATTENTE — un seul accent, et le violet n'est qu'une IDENTITÉ
    // =============================================================================================

    @Test
    fun `les surfaces de l attente prennent l accent, jamais une teinte violette`() {
        // La pastille d'état de l'en-tête : l'accent pour un tour qui travaille.
        val thread = read("src/main/java/com/onyx/app/ui/chat/components/ThreadOrganisation.kt")
        assertTrue(
            "la pastille d'un tour qui travaille doit être l'accent",
            thread.contains("ThreadStatusTone.Busy -> theme.accent"),
        )
        // La puce d'une carte d'activité : l'accent pour « en cours » et « en attente ».
        val card = read("src/main/java/com/onyx/app/ui/chat/components/ContextCard.kt")
        assertTrue(
            "la puce vivante doit être l'accent",
            card.contains("RUNNING, WAITING -> ContextInk.ACCENT"),
        )
        assertTrue(
            "et le rôle d'accent doit résoudre sur le thème",
            card.contains("ContextInk.ACCENT -> theme.accent"),
        )
        // Le seul violet saturé de l'app est dans la **palette d'identité des visages** : c'est
        // l'identité d'un bot (comme sur la référence), pas une couleur d'état — et il est nommé
        // « Violet » dans le sélecteur, donc choisi par la personne.
        val face = read("../core/src/main/java/com/onyx/app/ui/design/BotAvatar.kt")
        assertTrue(
            "le violet doit rester une IDENTITÉ de visage",
            face.contains("Color(0.82f, 0.74f, 1.00f) to Color(0.54f, 0.40f, 0.94f)"),
        )
        assertTrue("et il doit être nommé", face.contains("\"Violet\""))
        // Aucune des surfaces de l'attente ne contient un violet en dur.
        listOf(thread, card).forEach { source ->
            assertFalse(
                "une surface d'attente porte une teinte violette en dur",
                source.contains("0xFF7C3AED") || source.contains("0xFFA78BFA"),
            )
        }
    }
}
