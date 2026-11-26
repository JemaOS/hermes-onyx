package com.onyx.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mise en forme et la performance de l'écran « Ordinateur », vérifiées sur le source.
 *
 * Comme les autres tests de câblage de ce dépôt : ce qui se prouve ici, ce sont les **règles** qui
 * ne doivent pas se perdre — l'aperçu qui remplit la place, l'image décodée hors du fil principal,
 * une barre dont les boutons ne débordent pas. Le rendu, lui, se juge sur capture.
 */
class ComputerScreenUxTest {

    private fun read(path: String) = File(path).readText()

    private val screen = read("src/main/java/com/onyx/app/ui/computer/ComputerScreen.kt")
    private val frames = read("src/main/java/com/onyx/app/ui/computer/ComputerFrames.kt")
    private val session = read("src/main/java/com/onyx/app/ui/computer/ComputerSession.kt")
    private val theme = read("../core/src/main/java/com/onyx/app/ui/theme/ResolvedTheme.kt")

    // ---- l'aperçu remplit la place ------------------------------------------------------------

    @Test
    fun `l'apercu se pose sur la scene du theme, jamais sur du blanc`() {
        // Le fond de l'aperçu est la scène assombrie : une image 16:9 dans un téléphone laisse
        // toujours des bandes, et elles ne doivent jamais être du blanc.
        assertTrue(screen.contains("modifier = modifier.background(theme.stageColor)"))
        assertTrue(theme.contains("val stageColor: Color get() = lerp(pageColor, Color.Black"))
        // La scène garde la teinte du thème dans les deux apparences.
        assertTrue(theme.contains("if (dark) 0.55f else 0.88f"))
    }

    @Test
    fun `l'image est peinte au rectangle calcule, pas posee dans une mise en page`() {
        // Un `Image` avec `ContentScale` re-mesure et re-place à chaque image ; le canvas, lui,
        // ne fait que redessiner.
        assertTrue(screen.contains("drawImage("))
        assertTrue(screen.contains("filterQuality = FilterQuality.Low"))
        assertTrue(screen.contains("val fit = ComputerViewport.fitOf("))
    }

    @Test
    fun `les insets sont respectes, et le plein ecran les franchit exprès`() {
        // Le volet des commandes porte ses propres insets ; le bandeau du plein écran tient compte
        // de l'encoche ; les commandes en plein écran respectent la zone sûre.
        assertTrue(screen.contains("windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertTrue(screen.contains("displayCutoutPadding()"))
        // La barre du bas ne colle pas à la barre de gestes : l'app est bord à bord.
        assertTrue("la barre du bas doit prendre l'inset de navigation",
            screen.contains("Modifier.navigationBarsPadding()"))
        assertTrue(screen.contains("WindowInsetsCompat.Type.systemBars()"))
        assertTrue(screen.contains("BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"))
        // Le retour sort du plein écran avant de quitter l'écran. (La garde était **inversée** :
        // elle ne se posait que hors plein écran — où elle doublait le retour système —, et en
        // plein écran le geste rendait donc l'écran entier au lieu d'en sortir.)
        assertTrue(screen.contains("if (fullscreen) {"))
        assertTrue(screen.contains("fullscreen = false"))
        assertFalse(screen.contains("BackHandler(enabled = !fullscreen)"))
    }

    @Test
    fun `le pliable est traite par la place, pas par le nom de l'appareil`() {
        assertTrue(screen.contains("val twoPanes = !editing && !fullscreen && ComputerLayout.twoPanes(windowWidth)"))
        assertTrue(screen.contains("ComputerLayout.previewWidth(windowWidth)"))
        assertTrue(screen.contains("ComputerLayout.controlsWidth(windowWidth)"))
        // L'aperçu occupe son volet : il est le seul élément souple de la rangée.
        assertTrue(screen.contains("stage(Modifier.weight(1f).fillMaxHeight())"))
    }

    @Test
    fun `le zoom et le retour a l'ajustement sont la`() {
        assertTrue(screen.contains("ComputerViewport.pinchZoomBounded("))
        assertTrue(screen.contains("ComputerViewport.doubleTapZoom("))
        // Le retour à l'ajustement a quitté la rangée du bas pour « Plus » : c'est le même geste,
        // en toutes lettres, et il ne coûte plus une case de la rangée.
        assertTrue(screen.contains("view.reset()"))
        // Le pointeur est **relatif** : le doigt le déplace, il ne le désigne pas — donc plus de
        // `toScreen` sur la position du doigt, et plus besoin de viser le pointeur lui-même.
        assertTrue(screen.contains("ComputerPointer.advance("))
        assertFalse(screen.contains("ComputerScale.toScreen("))
    }

    // ---- la performance ------------------------------------------------------------------------

    @Test
    fun `le decodage ne touche pas le fil principal`() {
        assertTrue(frames.contains("withContext(Dispatchers.Default) { decoder.decode(bytes) }"))
        assertTrue("le décodeur ne doit pas être un simple BitmapFactory dans la composition",
            frames.contains("internal class ComputerFrameDecoder"))
    }

    @Test
    fun `les images en retard sont jetees, pas mises en file`() {
        // Le flux est un StateFlow : un collecteur occupé saute les images intermédiaires. C'est
        // écrit là où on le lit, pour que personne ne « corrige » en file d'attente.
        assertTrue(frames.contains("frames.collect { frame ->"))
        assertTrue(frames.contains("val bytes = frame?.bytes"))
        assertTrue(session.contains("val frames get() = client.frame"))
    }

    @Test
    fun `la memoire des images est reutilisee, jamais celle qui s'affiche`() {
        assertTrue(frames.contains("inBitmap = reuse"))
        assertTrue("l'image affichée devient le tampon libre", frames.contains("spare = shown"))
        assertTrue(frames.contains("shown = fresh"))
    }

    @Test
    fun `l'image n'est lue que dans l'apercu, pas au niveau de l'ecran`() {
        // C'est ce qui évite de recomposer l'en-tête, la ligne d'état et la barre à chaque image.
        assertFalse("l'écran ne doit pas lire l'image", screen.contains("val frame by session.frame"))
        assertTrue(screen.contains("frames = session.frames,"))
        assertTrue(frames.contains("internal fun rememberComputerImage("))
    }

    @Test
    fun `le detecteur de gestes ne redemarre pas quand le zoom change`() {
        // Les clés d'un `pointerInput` sont un redémarrage : un pincement qui reprend ses clés au
        // milieu du geste le perd.
        assertFalse(screen.contains("pointerInput(fit,"))
        assertTrue(screen.contains(".pointerInput(imageWidth, imageHeight, screenWidth, screenHeight, slop)"))
    }

    @Test
    fun `la taille de l'image est demandee a la largeur du volet, et la cadence monte au geste`() {
        assertTrue(screen.contains("session.requestStream(previewPanePx)"))
        assertTrue(screen.contains("session.setMoving(true)"))
        assertTrue(screen.contains("delay(ComputerStream.TURBO_HOLD_MS)"))
        assertTrue(screen.contains("ComputerLayout.previewWidthPx(previewPane, density.density)"))
    }

    // ---- la barre du bas ----------------------------------------------------------------------

    @Test
    fun `la barre n'a plus de phrases dans des boutons`() {
        // Ce qui était le défaut : des libellés entiers — « Rendre la main », « Retour »,
        // « Échap » — dans une rangée de boutons qui débordait de l'écran.
        assertFalse("plus de bouton à libellé long",
            screen.contains("ComputerBarButton("))
        assertTrue(screen.contains("private fun ComputerKeyButton("))
        assertTrue(screen.contains("ComputerKeyButtonSize = 40.dp"))
        assertTrue(screen.contains(".size(ComputerKeyButtonSize)"))
        // Le nom entier reste, mais en libellé d'accessibilité — donc traduit, et jamais coupé.
        assertTrue(screen.contains("label = \"Return\","))
        assertTrue(screen.contains("label = \"Escape\","))
        assertTrue(screen.contains(".semanticsLabel(label)"))
    }

    /** Les cases de la rangée du bas : après le bouton de la main, jusqu'à la ligne du champ. */
    private fun barKeys(): String =
        screen.substringAfter("enabled = live,").substringBefore("if (holding) {")

    @Test
    fun `la barre ne porte que les commandes qui servent tout le temps`() {
        // Le défaut mesuré : six cases fixes plus une pastille à mot — de quoi déborder d'un
        // téléphone dès que la police du système grandit, la dernière case passant hors de l'écran
        // et n'y laissant qu'un fragment vertical. La rangée ne garde donc que les commandes
        // qu'on touche sans y penser.
        val row = barKeys()
        assertTrue("la main reste en clair", screen.contains("if (holding) session.release() else session.take()"))
        assertTrue("la touche Retour reste en clair", row.contains("Symbols.ArrowTurnDownRight"))
        assertTrue("la touche Échap reste en clair", row.contains("Symbols.Xmark"))
        assertTrue("la visée reste en clair", row.contains("label = pointer.gain.labelKey,"))
        // Et les quatre autres l'ont quittée : c'est ce qui la fait tenir sans défiler.
        assertFalse("« Haut » a quitté la rangée", row.contains("Symbols.ChevronUp"))
        assertFalse("« Bas » a quitté la rangée", row.contains("Symbols.ChevronDown"))
        assertFalse("« Ajuster à l'écran » a quitté la rangée", row.contains("Symbols.ArrowCounterclockwise"))
        assertFalse("le plein écran a quitté la rangée", row.contains("Symbols.FullScreenEnter"))
    }

    @Test
    fun `la rangee de la barre ne defile pas`() {
        // Une rangée défilable cache toujours une commande, et son indicateur se lit comme un
        // défaut d'affichage. La barre n'a donc rien à faire défiler : ce qui n'y tient pas est
        // replié dans « Plus », en toutes lettres.
        val row = barKeys()
        assertFalse("la rangée ne défile pas horizontalement", row.contains("horizontalScroll"))
        assertFalse("la rangée ne défile pas verticalement", row.contains("verticalScroll"))
        assertFalse("la rangée n'est pas une liste", row.contains("LazyRow"))
        // La place restante est un ressort : les cases restent collées au bord droit.
        assertTrue(row.contains("Box(modifier = Modifier.weight(1f))"))
    }

    @Test
    fun `les commandes repliees sont nommees dans le menu, et traduites`() {
        // Ce qui quitte la rangée ne disparaît pas : chaque commande repliée est une ligne du menu
        // « Plus », en toutes lettres — jamais un symbole sans nom.
        val menu = screen.substringAfter("items = buildList {").substringBefore("if (askingCommand)")
        listOf("Scroll up", "Scroll down", "Fit the screen", "Full screen").forEach { label ->
            assertTrue("« $label » doit être une ligne du menu", menu.contains("\"$label\""))
        }
        // Et chacune se traduit : un libellé sans traduction s'écrit en anglais chez le lecteur.
        val french = read("../core/src/main/java/com/onyx/app/localization/OnyxFrench.kt")
        listOf("Scroll up", "Scroll down", "Fit the screen", "Full screen", "Exit full screen")
            .forEach { label ->
                assertTrue("« $label » doit exister pour être traduit", french.contains("\"$label\" to"))
            }
    }

    @Test
    fun `la main tient en un mot, et son nom entier reste accessible`() {
        assertTrue(screen.contains("text = if (holding) \"Release\" else \"Take\","))
        assertTrue(screen.contains("contentDescription = OnyxLocalization[if (holding) \"Hand back\" else \"Take over\"]"))
    }

    @Test
    fun `la ligne d'etat tient sur une ligne et ne se replie jamais`() {
        val status = screen.substringAfter("private fun ComputerStatusLine(")
            .substringBefore("private fun ComputerNotes(")
        // **La main et la résolution gardent leur place** : une ligne chacune, elles ne bougent
        // jamais d'une trame à l'autre — c'est l'invariant de la barre, et il est inchangé.
        assertEquals("deux textes fixes sur une ligne", 2, Regex("maxLines = 1").findAll(status).count())
        // Le **détail** est le seul élément souple, et il se **replie** au plus sur deux lignes au
        // lieu d'être coupé : « Cliq… » ne dit rien. C'est la règle des textes d'état du lot des
        // troncatures (une adresse, un libellé d'action, un état ne se coupent pas).
        assertEquals("le détail se replie au plus sur deux lignes", 1, Regex("maxLines = 2").findAll(status).count())
        assertTrue(status.contains("overflow = TextOverflow.Ellipsis"))
        assertTrue(status.contains("modifier = Modifier.weight(1f, fill = false)"))
    }

    @Test
    fun `le champ pour ecrire reste sur une ligne`() {
        // Le propriétaire vient de faire réparer les champs : on ne régresse pas.
        assertFalse(screen.contains("singleLine = false"))
        val field = screen.substringAfter("placeholder = \"Type on the computer…\"")
            .substringBefore("OnyxIcon(")
        assertTrue(field.contains("ImeAction.Send"))
        assertTrue(field.contains("onSend = send"))
    }

    @Test
    fun `aucune phrase de la barre n'est coupee par un symbole d'ancetre de l'autre plateforme`() {
        // Rien de l'autre plateforme dans l'interface : les libellés passent par la traduction.
        listOf("Take", "Release", "Full screen", "Exit full screen", "Fit the screen").forEach { label ->
            assertTrue(
                "« $label » doit exister pour être traduit",
                read("../core/src/main/java/com/onyx/app/localization/OnyxFrench.kt").contains("\"$label\" to"),
            )
        }
    }

    @Test
    fun `deplie, le volet des commandes garde son retour et son menu`() {
        // Un volet n'est pas une moitié d'écran : sans en-tête, le retour disparaît et
        // « Exécuter une commande… », le raccourci clavier, l'adresse du pont et « Fermer la
        // session » deviennent inatteignables — précisément sur l'appareil déplié.
        val volet = screen.substringAfter("twoPanes -> Row(").substringBefore("else -> Column(")
        assertTrue("le volet doit porter un en-tête", volet.contains("OnyxPageHeader("))
        assertTrue("le retour du volet", volet.contains("onBack = onBack"))
        assertTrue("le menu du volet", volet.contains("onAction = { showMenu = true }"))
        // Et l'aperçu, lui, prend bien tout son volet.
        assertTrue(volet.contains("stage(Modifier.weight(1f).fillMaxHeight())"))
    }
}
