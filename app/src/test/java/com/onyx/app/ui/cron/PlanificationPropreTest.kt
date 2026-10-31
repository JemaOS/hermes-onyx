package com.onyx.app.ui.cron

import com.onyx.app.data.model.serve.CronJob
import com.onyx.app.data.model.serve.cronIdentity
import com.onyx.app.data.model.serve.cronStamp
import com.onyx.app.data.model.serve.scheduleText
import com.onyx.app.localization.OnyxLocalization
import com.onyx.app.ui.design.OnyxSpace
import java.io.File
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou du détail d'une tâche planifiée** — il mord.
 *
 * La capture du propriétaire montrait cinq défauts distincts, et ce test tient sur chacun d'eux :
 *
 * 1. les deux boutons (« Run now », « Pause ») **rognés par le bord bas de leur carte** ;
 * 2. quatre **horodatages bruts** en millisecondes affichés à l'utilisateur
 *    (`1793292701521`, `1793299101521`, `179328282.757681`) ;
 * 3. sept **chaînes anglaises** dans une appli française (« Run now », « Pause », « next run »,
 *    « scheduled », « origin », « every 60m », « RUNS ») ;
 * 4. la **pastille « Nouvelle conversation »** et son rond posés sur la liste des exécutions ;
 * 5. des **espacements et des filets irréguliers** d'une carte et d'une rangée à l'autre.
 *
 * Chaque assertion ci-dessous **échoue** si le défaut revient : c'est la « morsure » demandée, et
 * elle porte autant sur le texte du source que sur la fonction qui formate (appelée pour de vrai,
 * avec les valeurs exactes de la capture).
 */
class PlanificationPropreTest {

    private val cron = File("src/main/java/com/onyx/app/ui/cron/CronScreen.kt").readText()
    private val bot = File("src/main/java/com/onyx/app/ui/cron/BotSchedulesScreen.kt").readText()
    private val anatomy = File("../core/src/main/java/com/onyx/app/ui/design/ContentAnatomy.kt").readText()
    private val lazy = File("src/main/java/com/onyx/app/ui/common/LazyCard.kt").readText()
    private val fr = Locale.FRANCE

    /** Une forme affichée ne peut pas contenir dix chiffres d'affilée. */
    private val brut = Regex("\\d{10,}")

    // ---------------------------------------------------------------------------------------
    // 2. Les horodatages bruts
    // ---------------------------------------------------------------------------------------

    @Test
    fun `aucun horodatage brut ne peut plus etre affiche`() {
        // Le source ne peut plus jeter la valeur du serveur dans un titre, une valeur de rangée ou
        // une colonne d'état : c'est exactement ce que faisaient ces cinq lignes.
        listOf(
            "title = run.at",
            "DetailRow(\"Next run\", it",
            "DetailRow(\"Last run\", it",
            "status = job.nextRun",
            "status = job.lastRun",
        ).forEach { fuite ->
            assertFalse("un horodatage brut est repassé à l'écran : $fuite", cron.contains(fuite))
        }
        // Et il passe par le formatage, partout : les deux horodatages du détail et ceux de la
        // colonne d'état, plus l'identifiant technique séparé de la date.
        assertTrue("les horodatages doivent passer par cronStamp", cron.contains("cronStamp("))
        assertTrue("l'identifiant technique doit être séparé de la date", cron.contains("cronIdentity("))
        assertTrue("les deux rangées d'horodatage du détail", cron.contains("DetailSpec(\"Next run\"") && cron.contains("DetailSpec(\"Last run\""))
        // Et un passage dont la valeur n'est pas un instant ne prend **pas** le nombre pour titre :
        // la rangée dit qu'elle n'est pas datée, l'identifiant descend en monospace dessous.
        assertTrue(cron.contains("title = stamp?.absolute ?: \"Undated run\""))
        assertTrue(cron.contains("subtitleMono = identity != null"))
        // Et la colonne de droite **ne coupe pas** le libellé : la date est la valeur, le relatif
        // une seconde ligne (« Prochain e exécut ion » était le libellé écrasé à 1,3×).
        assertTrue(cron.contains("note = it.relative"))
        assertTrue(cron.contains("verticalArrangement = Arrangement.spacedBy(OnyxSpace.GlyphGap),"))
    }

    @Test
    fun `les valeurs de la capture se lisent et ne sont plus des nombres`() {
        val now = 1_793_292_700_000L
        val paris = ZoneId.of("Europe/Paris")
        // Les deux horodatages en millisecondes : une date française, et un relatif.
        val next = cronStamp(raw = "1793292701521", nowMillis = now, locale = fr, zone = paris)
        val last = cronStamp(raw = "1793289101521", nowMillis = now, locale = fr, zone = paris)
        assertNotNull(next)
        assertNotNull(last)
        assertEquals("29/10/2026 17:51", next!!.absolute)
        assertEquals("29/10/2026 16:51", last!!.absolute)
        listOf(next, last).forEach {
            assertFalse("nombre de millisecondes affiché : ${it.line}", brut.containsMatchIn(it.line))
            assertTrue("le relatif manque : ${it.line}", it.line.contains("·"))
        }
        // Le troisième — l'identifiant d'un passage — n'est **pas** un instant : il ne prend pas la
        // place d'une date (le test échouerait si on se remettait à en fabriquer une).
        assertNull(cronStamp(raw = "179328282.757681", nowMillis = now, locale = fr, zone = paris))
        assertEquals("179328282.757681", cronIdentity("179328282.757681"))
        // Et un passage daté se lit comme une phrase, pas comme un jeton.
        val run = cronStamp(raw = "1793292701521", nowMillis = now + 8 * 60_000L, locale = fr, zone = paris)
        assertEquals("il y a 8 minutes · 29/10/2026 17:51", run!!.line)
    }

    // ---------------------------------------------------------------------------------------
    // 3. Les chaînes anglaises
    // ---------------------------------------------------------------------------------------

    @Test
    fun `les sept chaines de la capture passent en francais par le dictionnaire`() {
        // Chacune est une **clé** du dictionnaire central : aucune phrase française n'est écrite en
        // dur dans l'écran. Le tableau du rapport est donc celui-ci, et il se prouve ici.
        val attendu = linkedMapOf(
            "Run now" to "Exécuter maintenant",
            "Pause job" to "Mettre en pause",
            "next run" to "prochaine exécution",
            "scheduled" to "planifiée",
            "Origin" to "Origine",
            "every 60m" to "toutes les 60 min",
            "Runs" to "Exécutions",
        )
        attendu.forEach { (english, french) ->
            val rendered = OnyxLocalization.translate(english, fr)
            assertEquals("« $english » reste anglais", french, rendered)
            // La clé est bien **celle que l'écran emploie** (ou la forme syntaxique du serveur pour
            // l'horaire) : le dictionnaire ne peut pas dériver du source sans échouer ici.
            //
            // « Origin » fait exception, et le relevé le dit : cette clé n'est employée par **aucun**
            // écran de planification (elle vient du registre d'un agent, où elle est déjà française
            // depuis longtemps) ; elle reste dans le tableau comme le mot de la capture qui n'est pas
            // produit par ces écrans.
            if (english != "every 60m" && english != "Origin") {
                assertTrue(
                    "« $english » n'est plus employé par les écrans de planification",
                    cron.contains("\"$english\"") || bot.contains("\"$english\""),
                )
            }
            // Et en anglais, rien ne bouge.
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `les autres libelles des deux ecrans sont francais`() {
        val attendu = linkedMapOf(
            "New job" to "Nouvelle tâche",
            "Jobs" to "Tâches",
            "Prompt" to "Consigne",
            "Schedule" to "Planification",
            "State" to "État",
            "Next run" to "Prochaine exécution",
            "Last run" to "Dernière exécution",
            "Deliver to" to "Envoyer vers",
            "Model" to "Modèle",
            "No runs yet" to "Aucune exécution pour le moment",
            "Untitled job" to "Tâche sans titre",
            "Job options" to "Options de la tâche",
            "Edit job" to "Modifier la tâche",
            "Delete job" to "Supprimer la tâche",
            "Saving…" to "Enregistrement…",
            "What should Onyx do?" to "Que doit faire Onyx ?",
            "Job" to "Tâche",
            "Resume" to "Reprendre",
            "Edit" to "Modifier",
            "Delete" to "Supprimer",
            "Cancel" to "Annuler",
            "Save" to "Enregistrer",
            "run" to "exécution",
            "Undated run" to "Exécution non datée",
        )
        attendu.forEach { (english, french) ->
            assertEquals("français : $english", french, OnyxLocalization.translate(english, fr))
        }
        // Les deux phrases du détail et de l'état vide sont là aussi.
        assertEquals(
            "Elle cesse de s’exécuter sur le gateway. Cette action est irréversible.",
            OnyxLocalization.translate("It stops running on the gateway. This can't be undone.", fr),
        )
        assertEquals(
            "Supprimer « Rapport du lundi » ?",
            OnyxLocalization.translate("Delete “Rapport du lundi”?", fr),
        )
    }

    @Test
    fun `un libelle fixe ne peut pas contourner le dictionnaire`() {
        // Les composants qui portaient `localize = false` en dur : la pilule et le titre de section.
        // Les écrans de planification les passent désormais traduits — sans quoi « Run now » et
        // « RUNS » revenaient tels quels, ce que la capture montrait.
        assertTrue(
            "la pilule doit traduire son libellé",
            anatomy.contains("localize: Boolean = false,") && anatomy.contains("text = text,\n                localize = localize,"),
        )
        assertTrue(
            "le titre de section doit pouvoir traduire le sien",
            anatomy.contains("text = if (localize) OnyxLocalization[text].uppercase() else text.uppercase()"),
        )
        // Et les sites de la planification s'en servent : les deux pilules du détail, celle du
        // titre de la liste, et le libellé de section.
        assertTrue("les pilules et le titre de section doivent traduire", Regex("localize = true,").findAll(cron).count() >= 3)
        assertTrue(cron.contains("text = \"Runs\",\n                localize = true,"))
    }

    // ---------------------------------------------------------------------------------------
    // 1. Les boutons dans leur carte
    // ---------------------------------------------------------------------------------------

    @Test
    fun `les boutons tiennent dans leur carte et la carte grandit`() {
        // Le rembourrage de carte est **le jeton**, pas un 14 dp recopié : c'est lui qui fait que la
        // forme épouse la hauteur mesurée (texte + écart + commandes + rembourrage).
        assertTrue(
            "le rembourrage de carte doit être le jeton",
            anatomy.contains("padding: Dp = OnyxSpace.CardPadding,"),
        )
        assertFalse("le 14 dp écrit à la main est revenu", anatomy.contains("padding: Dp = 14.dp,"))
        // Les deux pilules sont des enfants de la carte de résumé : entre l'ouverture de la première
        // `ContentCard` du détail et la suivante, il y a les deux `ContentPill`.
        val detail = cron.substringAfter("private fun CronDetailScreen")
        val premiere = detail.substringAfter("ContentCard {").substringBefore("\n            ContentCard")
        assertEquals("les deux commandes vivent dans la première carte", 2, Regex("ContentPill\\(").findAll(premiere).count())
        // Aucune carte des écrans de planification ne fixe sa hauteur (une hauteur fixe rogne).
        listOf(cron, bot).forEach { source ->
            assertFalse("une carte à hauteur fixe rogne ses boutons", source.contains("ContentCard(modifier = Modifier.height("))
        }
    }

    // ---------------------------------------------------------------------------------------
    // 5. Écartements et filets
    // ---------------------------------------------------------------------------------------

    @Test
    fun `un seul ecart entre cartes, un seul rembourrage, un filet par rangee ou aucun`() {
        // Un seul écart entre blocs empilés : le jeton.
        assertTrue(
            "l'écart entre les cartes du détail doit être le jeton",
            cron.contains("verticalArrangement = Arrangement.spacedBy(OnyxSpace.BlockGap),"),
        )
        // Un filet **entre** les rangées, jamais après la dernière.
        assertTrue(cron.contains("if (index > 0) DetailRowDivider()"))
        // Les passages : un filet entre eux, en retrait après la vignette (la rangée en porte une).
        assertTrue(cron.contains("if (index > 0) ContentRowDivider()"))
        assertFalse(
            "le filet des passages n'est plus aligné sur leur vignette",
            cron.contains("if (index > 0) ContentRowDivider(withTile = false)"),
        )
        // Le titre de section et les rangées partagent le même retrait que la carte.
        assertTrue(cron.contains("start = OnyxSpace.CardPadding,\n                    end = OnyxSpace.CardPadding,"))
    }

    // ---------------------------------------------------------------------------------------
    // 4. Le bas de l'écran
    // ---------------------------------------------------------------------------------------

    @Test
    fun `la derniere rangee ne passe pas sous la plaque du bas`() {
        // La réserve est **nommée et dérivée de l'échelle** : une cible tactile plus une séparation
        // de section. C'était `40.dp` écrit à la main, à quatre endroits.
        assertTrue(cron.contains("internal val CronBottomReserve: Dp = OnyxSpace.TouchTarget + OnyxSpace.Loose"))
        assertEquals(
            (OnyxSpace.TouchTarget.value + OnyxSpace.Loose.value).toDouble(),
            CronBottomReserve.value.toDouble(),
            0.001,
        )
        listOf(cron, bot).forEach { source ->
            assertFalse("une réserve en dur est revenue", source.contains(".padding(bottom = 40.dp)"))
            assertTrue(source.contains(".padding(bottom = CronBottomReserve)"))
        }
        // Et la liste paresseuse de la liste des tâches la reçoit aussi.
        assertTrue(lazy.contains("bottomPadding: Dp = 40.dp,"))
        assertTrue(cron.contains("OnyxLazyFormPage(modifier = Modifier.weight(1f), bottomPadding = CronBottomReserve)"))
    }
}
