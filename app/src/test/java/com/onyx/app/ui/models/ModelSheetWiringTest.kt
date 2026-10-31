package com.onyx.app.ui.models

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le verrou de source de la **carte de composition** et du **sélecteur de modèle**.
 *
 * Le calcul, lui, est éprouvé sans appareil (`ModelSelectionTest`, `ModelPreferenceStoreTest`,
 * `ModelCatalogTest`, dans `core`). Ici on vérifie que **les écrans s'en servent vraiment** : la
 * carte ne peint pas d'aplat, la puce mesure pour ne pas déborder, la feuille porte tout ce que la
 * référence demande, et le modèle choisi **se mémorise par bot**.
 */
class ModelSheetWiringTest {

    private fun read(path: String) = File(path).readText()

    private val composer = read("src/main/java/com/onyx/app/ui/chat/components/OnyxComposer.kt")
    private val sheet = read("src/main/java/com/onyx/app/ui/models/ModelSheet.kt")
    private val chat = read("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt")
    private val app = read("src/main/java/com/onyx/app/ui/OnyxApp.kt")
    private val bots = read("src/main/java/com/onyx/app/ui/bots/BotsViewModel.kt")

    /** Le corps du champ de la carte : de l'aplat interdit jusqu'aux états du bouton d'envoi. */
    private fun cardField(): String =
        composer.substringAfter("// Le fond du champ **ne peint aucun aplat**").substringBefore("val trailingStates")

    // — La carte de composition (fonction 1) —

    @Test
    fun `la carte est une vitre maison aux coins arrondis`() {
        // La **même** carte aux deux bouts de la chaîne : le lot « composition » lui a donné sa forme
        // arrondie et sa marge (`ComposerCardSideMargin`), le lot « verre liquide » lui a donné la
        // vitre à flou réel — sous le nom des surfaces qui recouvrent du contenu défilant,
        // `liquidGlass`, exactement comme `SystemBarsInsetsTest` le documente depuis ce lot (la
        // barre de commandes prend `liquidGlass`, `frostedBar` reste la vitre des surfaces qui ne
        // recouvrent rien). Le verrou tient donc la **forme de la vitre** et le nom de la surface,
        // pas la chaîne d'appel intermédiaire du lot.
        assertTrue(
            "la carte doit être une vitre d'Onyx, pas un aplat",
            composer.contains("liquidGlass(GlassSurface.COMPOSER, RoundedCornerShape(ComposerCardRadius))"),
        )
        assertTrue("elle doit flotter, pas toucher les bords", composer.contains("ComposerCardSideMargin"))
    }

    @Test
    fun `le champ de la carte ne peint aucun aplat opaque`() {
        val field = cardField()
        assertTrue("le champ doit passer par la règle du lot", field.contains("composerFieldSurface("))
        assertFalse("le champ peint encore une surface opaque", field.contains("background(theme.surfaceColor"))
        assertFalse("le champ peint encore une vitre opaque", field.contains("glassSurface("))
    }

    @Test
    fun `la carte porte le champ en haut et la rangee des commandes en dessous`() {
        // Le champ vient avant la rangée des commandes, dans la même carte.
        val fieldIndex = composer.indexOf("composerFieldSurface(")
        val rowIndex = composer.indexOf("// La **rangée** des commandes")
        assertTrue("le champ doit précéder la rangée", fieldIndex in 1 until rowIndex)
        assertTrue("la carte doit empiler ses deux rangées", composer.contains("Column(modifier = Modifier.weight(1f))"))
    }

    @Test
    fun `la puce du modele porte le modele et la reflexion, et ouvre le selecteur`() {
        assertTrue(composer.contains("modelLabel: String? = null"))
        assertTrue(composer.contains("model = modelLabel"))
        assertTrue(composer.contains("reflection = reflectionLabel"))
        assertTrue("la puce ouvre la feuille", composer.contains("onClick = onOpenModelPicker"))
        assertTrue("elle est mesurée sous la police courante", composer.contains("rememberTextMeasurer()"))
        assertTrue("elle demande le plan de la ligne", composer.contains("ChipRowFit.labelPlan("))
    }

    @Test
    fun `les trois boutons ronds restent sur la meme ligne`() {
        // La rangée des commandes : du commentaire qui la nomme jusqu'à la puce, la fonction
        // suivante. Une seule `Row` y porte « + », la puce et les trois boutons ronds —
        // **jamais** une `Column`, donc jamais de passage à la ligne.
        val block = composer
            .substringAfter("// La **rangée** des commandes")
            .substringBefore("private fun ComposerModelChip")
        assertTrue("le micro", block.contains("Symbols.MicOutline"))
        assertTrue("le vocal", block.contains("label = \"Talk\""))
        assertTrue("l'envoi", block.contains("TrailingComposerButton("))
        assertTrue("l'envoi garde sa flèche", composer.contains("Symbols.ArrowUp"))
        assertFalse("la rangée ne doit pas passer à la ligne", block.contains("Column("))
    }

    // — Le sélecteur de modèle (fonction 2) —

    @Test
    fun `la feuille porte la poignee, le grand titre et la portee du choix`() {
        assertTrue(sheet.contains("fun ModelSheet("))
        assertTrue("le grand titre", sheet.contains("title = \"Model\""))
        assertTrue("une poignée en haut", sheet.contains("detectVerticalDragGestures"))
        assertTrue("la feuille est la feuille maison", sheet.contains("FrostSurface.SHEET"))
        // La **mention** : la portée réelle du choix, en gris secondaire, **sous le titre** et sur
        // deux lignes — plus serrée contre la croix. « Pour cette conversation » est gardée mot pour
        // mot de la référence.
        assertTrue("la portée est écrite sous le titre", sheet.contains("mention = scope.labelKey"))
        assertTrue(sheet.contains("enum class ModelScope"))
        assertTrue(sheet.contains("\"For this conversation\""))
        assertTrue("la portée du bot se lit en clair", sheet.contains("\"For every conversation of this bot\""))
        assertFalse(
            "l'ancienne mention obscure a disparu",
            sheet.contains("\"For this bot, everywhere\""),
        )
        assertTrue(sheet.contains("\"For this server\""))
        // L'écran qui ouvre la feuille dit la portée qu'il connaît vraiment.
        assertTrue("un chat de bot écrit sur le bot", app.contains("ModelScope.BOT"))
        assertTrue("une conversation simple écrit le modèle du serveur", app.contains("ModelScope.SERVER"))
        assertTrue("le style de la mention est secondaire", sheet.contains("style = theme.footnote.copy(color = theme.secondaryText)"))
        assertTrue("la mention tient sur deux lignes, jamais tranchée", sheet.contains("maxLines = 2,"))
    }

    @Test
    fun `la feuille porte la recherche, les filtres, les tuiles, l etoile et la coche`() {
        assertTrue("le champ de recherche", sheet.contains("placeholder = \"Search a model\""))
        assertFalse(
            "l'invite longue se tronquait dans le champ",
            sheet.contains("Search a model or a provider"),
        )
        assertTrue("la liste par sections (favoris puis récents)", sheet.contains("ModelSelection.sections("))
        assertTrue("la tuile d'initiale", sheet.contains("entry.monogram"))
        assertTrue("le fournisseur en sous-ligne", sheet.contains("modelRowProviders(entry)"))
        assertTrue("le prix du serveur, en monospace", sheet.contains("modelRowPrice(entry, french)"))
        assertTrue(sheet.contains("theme.monospacedFootnote"))
        assertTrue("l'étoile des favoris", sheet.contains("Symbols.StarFill"))
        assertTrue("la coche du modèle retenu", sheet.contains("Symbols.Checkmark"))
        assertTrue("les pastilles de fournisseur", sheet.contains("ModelSelection.providers("))
        assertTrue("elles filtrent vraiment les rangées", sheet.contains("ModelSelection.byProvider("))
        assertTrue("la rangée des fournisseurs est dessinée", sheet.contains("facets.map"))
    }

    @Test
    fun `la feuille agrege un modele par rangee, et garde le profil sur la liste`() {
        // Un modèle = une rangée, avec toutes ses offres (le local ET le distant sur la même ligne).
        assertTrue("le catalogue est agrégé par rangée", sheet.contains("ModelSelection.entries(models)"))
        assertTrue("la feuille regroupe par profil", sheet.contains("ModelSelection.groups("))
        assertTrue("elle reçoit les profils et leur état", sheet.contains("profiles: List<ProfileView> = emptyList()"))
        assertTrue("un en-tête par profil", sheet.contains("SectionHeader(view.name"))
        // L'état d'un profil injoignable est dit, jamais une liste vidée.
        assertTrue("l'état du profil est dit", sheet.contains("ModelSelection.profileSubtitle("))
        assertTrue(sheet.contains("\"This profile is unreachable.\""))
    }

    @Test
    fun `la selection se lit a l accent plein, dans une rangee qui se replie`() {
        assertTrue(sheet.contains("ModelContrast.activeChip(theme)"))
        assertTrue(sheet.contains("ModelContrast.activeChipInk(theme)"))
        assertTrue(sheet.contains("ModelContrast.idleChip(theme)"))
        // La pastille active **est** l'accent : plus l'encre inversée du motif précédent.
        assertFalse("les pastilles ne sont plus noires", sheet.contains("Color.Black else Color.White"))
        // La rangée retenue garde son fond teinté d'accent… à coins arrondis, et la marge qui le
        // laisse voir.
        assertTrue(sheet.contains("theme.accent.copy(alpha = ChosenRowTint)"))
        assertTrue(sheet.contains("RoundedCornerShape(OnyxSpace.ControlCorner)"))
        assertTrue("la rangée tient dans la feuille, marge comprise", sheet.contains("padding(horizontal = ModelRowSideMargin)"))
        // **Les pastilles ne défilent plus** : elles se replient, et aucune n'est tranchée.
        assertFalse(
            "plus de défilement horizontal des pastilles",
            sheet.contains("horizontalScroll"),
        )
        assertTrue("les deux rangées de pastilles sont fluides", sheet.contains("FlowRow("))
        assertTrue("les filets sont en retrait, alignés sur le nom", sheet.contains("OnyxRowDivider(inset = ModelRowDividerInset, endInset = ModelRowDividerEndInset)"))
        assertTrue("la tuile est grise, mesurée sur la feuille", sheet.contains("ModelContrast.tile(theme)"))
    }

    @Test
    fun `la rangee de reflexion garde l ordre de la reference et dit les crans absents`() {
        assertTrue(sheet.contains("ReasoningLevels.plan()"))
        assertTrue("la rangée peinte est celle du plan", sheet.contains("reasoningPlan.row"))
        assertTrue("les crans manquants sont comptés, pas nommés", sheet.contains("reasoningCountLine(reasoningPlan)"))
        assertFalse(
            "la note de développement a disparu",
            sheet.contains("This server doesn't offer every level"),
        )
    }

    @Test
    fun `le nom affiche n est jamais l alias de passerelle`() {
        // La feuille ne peint jamais l'alias : le nom vient du modèle, le profil de sa source.
        val catalog = read("../core/src/main/java/com/onyx/app/data/net/ModelCatalog.kt")
        assertTrue("l'alias est nommé une seule fois, pour être écarté", catalog.contains("GATEWAY_ALIAS = \"hermes-agent\""))
        assertTrue("les modèles montrables écartent l'alias", catalog.contains("fun usable(models: List<ModelChoice>)"))
        assertFalse(
            "aucune rangée ne prend l'alias pour nom",
            sheet.contains("label = ModelCatalog.GATEWAY_ALIAS") || sheet.contains("\"hermes-agent\""),
        )
    }

    @Test
    fun `la rangee de reflexion dit les crans absents sans les dessiner`() {
        assertTrue(sheet.contains("ReasoningLevels.plan()"))
        assertTrue("les crans manquants sont comptés à part", sheet.contains("val missing = plan.missing"))
        assertTrue("la phrase les compte au lieu de les nommer", sheet.contains("reasoning levels on this server"))
    }

    @Test
    fun `la feuille ne peint aucun prix invente`() {
        // Le seul endroit qui écrit un prix est la sous-ligne de la rangée, et ce prix vient du
        // serveur (`ModelOffer.chip()` → `ServerPrice`). Rien n'est écrit à la main dans la feuille.
        assertFalse(
            "aucun prix ne doit être écrit à la main dans la feuille",
            sheet.contains("0,00 €") || sheet.contains("\$0.00") || sheet.contains("per 1M") ||
                sheet.contains("promptPricePerMTok =") || sheet.contains("completionPricePerMTok =") ||
                sheet.contains("ServerPrice("),
        )
        assertTrue(
            "le prix de la feuille vient de l'offre reçue du serveur",
            sheet.contains("modelRowPrice(entry, french)"),
        )
        // Et la règle est tenue côté calcul : le prix est lu, jamais produit.
        val selection = read("../core/src/main/java/com/onyx/app/ui/design/ModelSelection.kt")
        assertTrue("le montant est gardé tel quel", selection.contains("data class ServerPrice("))
        assertTrue("une offre sans prix écrit le fournisseur seul", selection.contains("?: return providerName"))
    }

    @Test
    fun `la feuille monte au-dessus du clavier`() {
        assertTrue("rien ne passe sous le clavier", sheet.contains("imePadding()"))
        // La feuille est **bornée** à une part de l'écran, et la liste prend ce qui reste : c'est ce
        // qui garantit qu'aucune rangée n'est coupée par le bas.
        assertTrue("la feuille garde une hauteur bornée", sheet.contains("heightIn(max = sheetCap)"))
        assertTrue("la liste défile dans la feuille", sheet.contains("weight(1f, fill = false)"))
    }

    @Test
    fun `le retour en place est fige sous reduce motion`() {
        assertTrue(sheet.contains("LocalReduceMotion.current"))
        assertTrue("aucun mouvement quand on le demande", sheet.contains("androidx.compose.animation.core.snap()"))
    }

    // — La mémoire du choix, par bot —

    @Test
    fun `le modele choisi se memorise par bot`() {
        assertTrue(
            "le choix d'un bot écrit sur le serveur",
            app.contains("botsViewModel.setModel(bot, model)"),
        )
        assertTrue(
            "et le dépôt du bot l'écrit sur le serveur (profiles.configure)",
            bots.contains("ServeBotsRpc(rpc).setModel(bot.name, model)"),
        )
        assertTrue(
            "il est aussi gardé sur le téléphone",
            bots.contains("model = model,"),
        )
        assertTrue(
            "une conversation simple écrit le modèle du serveur",
            app.contains("settingsViewModel.update { it.copy(model = model) }"),
        )
    }

    @Test
    fun `l ecran de chat ouvre la feuille depuis la puce`() {
        assertTrue(chat.contains("onOpenModelSheet"))
        assertTrue(chat.contains("onOpenModelPicker = onOpenModelSheet"))
        assertTrue(chat.contains("reflectionLabel = reasoningLabel"))
        assertTrue("la feuille est posée par-dessus la conversation", app.contains("com.onyx.app.ui.models.ModelSheet("))
    }

    /**
     * **Le verrou du défaut corrigé.** La seconde ligne de l'en-tête — le modèle, avec son chevron —
     * ouvrait l'**écran** des modèles (`Route.MODELS`) ; elle doit ouvrir la **feuille**, le même
     * geste que la puce de la carte. Retirer ce branchement (revenir à `navTo`) fait **rougir** ce
     * test : c'est la morsure.
     */
    @Test
    fun `la pilule du haut ouvre la feuille, pas l ecran des modeles`() {
        assertTrue(
            "la pilule du haut doit ouvrir la feuille",
            app.contains("onPickModel = { showModelSheet = true }"),
        )
        assertFalse(
            "elle ne doit plus naviguer vers l'écran des modèles",
            app.contains("onPickModel = { navTo(Route.MODELS.name) }"),
        )
        // « /model » passe par le même chemin : on baisse la demande, puis on ouvre la feuille.
        val command = app.substringAfter("onModelCommand = {").substringBefore("},")
        assertTrue("« /model » ouvre la même feuille", command.contains("showModelSheet = true"))
        assertFalse("« /model » ne navigue plus ailleurs", command.contains("navTo("))
    }

    @Test
    fun `la feuille porte les sections epingles et recents, la ligne des modeles et le mode rapide`() {
        assertTrue("la section ÉPINGLÉS", sheet.contains("SectionHeader(OnyxLocalization[\"Pinned\"])"))
        assertTrue("la section RÉCENTS", sheet.contains("SectionHeader(OnyxLocalization[\"Recents\"])"))
        // Le titre de section est localise puis mis en petites capitales par `sectionTitle`
        // (le lot de la feuille fait passer la traduction avant la casse).
        assertTrue("un titre de section est mis en petites capitales", sheet.contains("text = sectionTitle(title),"))
        assertTrue("la casse est tenue par le calcul", sheet.contains("return label.uppercase(locale)"))
        assertTrue(
            "la dernière ligne déplie le reste, avec son compteur",
            sheet.contains("AllModelsRow(count = sections.rest.size)"),
        )
        assertTrue("le repli est tenu par le calcul", sheet.contains("ModelSelection.opensExpanded("))
        // Le libellé du champ a été raccourci par le lot de la feuille (l'invite longue se
        // tronquait) : le test s'aligne sur ce qui est peint.
        assertTrue("le champ de recherche", sheet.contains("placeholder = \"Search a model\""))
        assertTrue("la ligne du mode rapide", sheet.contains("fastMode?.let { FastModeRow(it) }"))
        assertTrue("son interrupteur est celui de la maison", sheet.contains("OnyxSwitch(checked = mode.on"))
        assertTrue(
            "son pied vient de la maison, jamais réinventé",
            sheet.contains("chatControlsFooter(controls, offersAutoApprove = onServe)"),
        )
    }

    @Test
    fun `le mode rapide ne se dessine que si le serveur l expose`() {
        // Un endpoint OpenAI-compatible nu n'a pas de mode rapide : la fonction rend `null` et la
        // feuille ne peint pas la ligne. Le verrou tient la garde, mot pour mot.
        assertTrue(sheet.contains("if (server == null || !server.transport.isStateful) return null"))
        assertTrue("la ligne est conditionnelle", sheet.contains("fastMode?.let { FastModeRow(it) }"))
    }

    @Test
    fun `le montant d une offre s ecrit en monospace, jamais recompose`() {
        // Le prix garde la chaîne du serveur, découpée à son séparateur ; seule la fonte change.
        assertTrue(sheet.contains("fontFamily = theme.codeFont.fontFamily"))
        assertTrue(sheet.contains("chip.substringAfter(ModelOffer.ChipSeparator"))
        assertTrue("le compteur de la dernière ligne est en monospace", sheet.contains("theme.monospacedFootnote"))
    }

    @Test
    fun `la feuille relit les favoris et note les recents`() {
        assertTrue(app.contains("modelPrefs.favourites(state.server.id)"))
        assertTrue(app.contains("modelPrefs.setFavourite(state.server.id, id, favourite)"))
        assertTrue(app.contains("modelPrefs.noteRecent(state.server.id, model)"))
    }
}
