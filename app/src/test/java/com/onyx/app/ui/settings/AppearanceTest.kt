package com.onyx.app.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'écran Apparence doit rester le port d'`AppearanceSettings`: le nom
 * de l'assistant, le thème replié, les couleurs du thème, l'icône, puis les habillages voiture.
 */
class AppearanceTest {

    private fun appSource(path: String): String = File("src/main/java/com/onyx/app/$path").readText()

    private fun coreSource(path: String): String = File("../core/src/main/java/com/onyx/app/$path").readText()

    @Test
    fun `la page Apparence garde les sections de la vue `() {
        val source = appSource("ui/settings/SettingsScreen.kt")

        // `NameSettings`: le champ « Name » et son pied, mot pour mot.
        assertTrue(source.contains("header = \"Name\""))
        assertTrue(
            source.contains(
                "Shown in the header and the message box. Spoken phrases and the Home Screen icon keep the app's real name.",
            ),
        )

        // La section « Appearance », le thème replié et les couleurs du thème.
        assertTrue(source.contains("header = \"Appearance\""))
        assertTrue(source.contains("OnyxDisclosureSummary("))
        assertTrue(source.contains("title = \"Theme\""))
        assertTrue(source.contains("title = \"\${theme.label} colors\""))
        assertTrue(source.contains("Use \${theme.label}'s own colors"))

        // Plus de section « Light and dark » séparée: le contrôle segmenté vit dans le thème.
        assertFalse(source.contains("header = \"Light and dark\""))

        // L'icône est dans la même carte que le reste, comme le DisclosureGroup.
        assertTrue(source.contains("AppIconSettings(embedded = true)"))
    }

    @Test
    fun `la grille des icones reste repliee comme le DisclosureGroup `() {
        val source = appSource("ui/settings/AppIconSettings.kt")

        //: `@State private var expanded = false` — seul le hook de
        // développement `-echo.expandAppIcons` ouvre la grille d'emblée.
        assertTrue(source.contains("var expanded by remember { mutableStateOf(false) }"))
        assertFalse(source.contains("var expanded by remember { mutableStateOf(true) }"))
        // La rangée résumé garde le libellé de l'icône choisie, son aperçu 24 dp et le chevron,
        // qui ne fait que tourner quand la grille s'ouvre.
        assertTrue(source.contains("Symbols.ChevronDown"))
        assertTrue(source.contains("modifier = Modifier.rotate(if (expanded) 180f else 0f)"))
    }

    @Test
    fun `la liste des themes est repliee derriere le resume du theme`() {
        val source = appSource("ui/settings/SettingsScreen.kt")

        // La rangée résumé dit « Messages, light », comme `DisclosureSummary`.
        assertTrue(source.contains("\"\${theme.label}, \${appearance.label.lowercase()}\""))
        // Les sept thèmes ne se peignent qu'une fois le groupe ouvert.
        val expanded = source.indexOf("if (showThemes) {")
        val firstRow = source.indexOf("ThemeRow(", expanded)
        assertTrue("la liste des thèmes doit vivre dans if (showThemes)", expanded >= 0 && firstRow > expanded)
        // Le contrôle segmenté d'apparence suit, dans le même groupe.
        assertTrue(source.contains("OnyxSegmented("))
    }

    @Test
    fun `les couleurs du theme passent par ThemeColors et les tokens du theme`() {
        val colors = coreSource("ui/theme/ThemeColors.kt")
        assertTrue(colors.contains("data class ThemeColors("))
        assertTrue(colors.contains("val accent: RGB?"))
        assertTrue(colors.contains("val bubble: RGB?"))
        assertTrue(colors.contains("val contrastingText: Color"))

        val theme = coreSource("ui/theme/OnyxTheme.kt")
        assertTrue(theme.contains("fun Palette.withColors(colors: ThemeColors?): Palette"))
        assertTrue(theme.contains("userBubbleTextColor = DynColor(pick.contrastingText)"))

        val resolved = coreSource("ui/theme/ResolvedTheme.kt")
        assertTrue(resolved.contains("colors: ThemeColors? = null"))
        assertTrue(resolved.contains("theme.palette.withColors(colors)"))

        // Le thème de l'app reçoit les choix, donc toute l'app les rend.
        val appTheme = coreSource("ui/theme/Theme.kt")
        assertTrue(appTheme.contains("colors: ThemeColors? = null"))
        assertTrue(appTheme.contains("ResolvedTheme.resolve(theme, dark, fonts, colors)"))
    }

    @Test
    fun `le nom de l assistant se montre dans l en tete et la zone de saisie`() {
        val app = appSource("ui/OnyxApp.kt")
        assertTrue(app.contains("assistantName = displayName.trim()"))
        assertTrue(app.contains("colors = themeColors[theme.id]"))

        val chat = appSource("ui/chat/ChatScreen.kt")
        assertTrue(chat.contains("assistantName: String = \"\""))
        assertTrue(chat.contains("val agentName = assistantName.ifBlank { state.server.displayName }"))
        // Dans le fil d'un bot, la barre et la zone de saisie nomment **le bot**: `threadSpeaker`
        // retombe sur le nom d'agent pour un fil simple. La seconde ligne de la barre, elle, porte
        // le « @handle » et le modèle — comme.
        assertTrue(chat.contains("subtitle = state.modelInUse"))
        assertTrue(chat.contains("replyName = threadSpeaker.label"))
    }

    @Test
    fun `les libelles visibles de l ecran Apparence sont traduits`() {
        val french = coreSource("localization/OnyxFrenchSettings.kt")
        listOf(
            "\"Shown in the header and the message box. Spoken phrases and the Home Screen icon keep the app's real name.\"",
            "\"Each theme keeps its own colors. Text on your bubbles and on accent buttons turns light or dark to stay readable.\"",
            "\"Accent\"",
            "\"Your bubbles\"",
            "\"Your messages\"",
        ).forEach { assertTrue("$it absente du français", french.contains(it)) }

        // Le résumé se traduit par motif: « Messages colors » et « Use Messages's own colors ».
        val catalog = coreSource("localization/OnyxLocalization.kt")
        assertTrue(catalog.contains("Regex(\"^(.+) colors\$\")"))
        assertTrue(catalog.contains("Regex(\"^Use (.+)'s own colors\$\")"))
        assertTrue(catalog.contains("Regex(\"^(.+), system\$\")"))
    }

    // MARK: - Les réglages de bots, de texte du chat et d'orbe portés par ce lot.

    @Test
    fun `la page Apparence porte les reglages de bots , avec leurs pieds de texte`() {
        val rows = appSource("ui/settings/AppearanceRows.kt")
        val source = appSource("ui/settings/SettingsScreen.kt")

        // Les trois `Toggle` d'`AppearanceSettings`, titre et explication mot pour mot.
        listOf(
            "\"Bot faces\"",
            "Off, each bot, Onyx too, is its initial on a circle of its color, and voice mode shows the Bubble orb in place of a face. The bots work the same either way.",
            "\"Pictures from Hermes\"",
            "A bot with a picture set in Hermes Desktop shows that picture instead of its face.",
            "\"Bot colors in their chats\"",
            "Your bubbles and the buttons in a bot's chat take its color, and Onyx's own chats take its face's if it has one. Group chats keep the theme's.",
        ).forEach { assertTrue("$it absent", source.contains(it)) }

        // « Chat text size »: un `Picker` en menu, et le pied de section.
        assertTrue(rows.contains("\"Chat text size\""))
        assertTrue(rows.contains("ResolvedTheme.chatTextSizes.map"))
        assertTrue(source.contains("ChatTextSizeRow(steps = chatTextSize, onPick = onChatTextSize)"))
        assertTrue(
            source.contains(
                "\u201CChat text size\u201D sets the conversation larger or smaller than the rest of the phone's text. It still follows the phone's own text size from there.",
            ),
        )

        // `VoiceOrbSettings`: l'orbe replié, la grille, la lumière.
        assertTrue(source.contains("VoiceOrbSettingsRows("))
        assertTrue(rows.contains("\"Voice orb\""))
        assertTrue(rows.contains("\"Orb light\""))
        assertTrue(rows.contains("The bars move when you speak; the light inside follows \u201COrb light\u201D. Onyx looks at you instead, and talks with the reply."))
        assertTrue(rows.contains("VoiceOrbChoice.entries.chunked(columns)"))

        // L'ordre: les bots, la taille du texte, l'icône, puis l'orbe.
        val faces = source.indexOf("title = \"Bot faces\"")
        val size = source.indexOf("ChatTextSizeRow(steps = chatTextSize")
        val icon = source.indexOf("AppIconSettings(embedded = true)")
        val orb = source.indexOf("VoiceOrbSettingsRows(")
        assertTrue(faces in 0 until size && size < icon && icon < orb)
    }

    @Test
    fun `chaque reglage porte est branche sur un etat reellement utilise`() {
        // « Bot faces » et « Pictures from Hermes »: lus par `BotAvatar` à travers des
        // CompositionLocals fournies au sommet de l'app.
        val app = appSource("ui/OnyxApp.kt")
        assertTrue(app.contains("LocalBotFaces provides botFaces"))
        assertTrue(app.contains("LocalBotPictures provides if (botPictures) botPicturesByName else emptyMap()"))
        assertTrue(app.contains("botsViewModel.loadPictures()"))

        val avatar = coreSource("ui/design/BotAvatar.kt")
        assertTrue(avatar.contains("val LocalBotFaces = staticCompositionLocalOf { true }"))
        assertTrue(avatar.contains("val LocalBotPictures = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }"))
        assertTrue(avatar.contains("if (showsInitial && !LocalBotFaces.current) BotFace.initialFor(name) else null"))
        assertTrue(avatar.contains("drawMonogram(face, initial, theme)"))
        assertTrue(avatar.contains("LocalBotPictures.current[name]"))

        // « Bot colors in their chats »: la couleur du visage remplace le thème dans le chat.
        assertTrue(avatar.contains("fun chatColors(): ThemeColors?"))
        assertTrue(app.contains("LocalOnyxTheme.current.withColors(botChatColors)"))

        // « Chat text size »: l'échelle est portée par le thème du fil, pas par celui du composeur.
        assertTrue(app.contains("chatTextScale = com.onyx.app.ui.theme.ResolvedTheme.chatTextScale(chatTextSize)"))
        val chat = appSource("ui/chat/ChatScreen.kt")
        assertTrue(chat.contains("chatTextScale: Float = 1f"))
        assertTrue(chat.contains("LocalOnyxTheme provides theme.withFontScale(chatTextScale)"))
        val group = appSource("ui/bots/GroupChatScreen.kt")
        assertTrue(group.contains("LocalOnyxTheme provides theme.withFontScale(chatTextScale)"))

        // L'orbe et sa lumière: les réglages pilotent la scène vocale (solo) et la grille.
        assertTrue(app.contains("orb = if (voiceOrb == VoiceOrbChoice.ONYX && !botFaces) VoiceOrbChoice.BUBBLE else voiceOrb"))
        assertTrue(app.contains("onOrbChange = { orb -> scope.launch { settingsRepo.setVoiceOrb(orb) } }"))
        assertTrue(app.contains("orbLight = orbLight"))
        assertTrue(app.contains("onOrbLightChange = { light -> scope.launch { settingsRepo.setOrbLight(light) } }"))
        val voice = appSource("ui/voice/VoiceScreen.kt")
        assertTrue(voice.contains("var localOrbLight by rememberSaveable { mutableStateOf(orbLight) }"))
        assertTrue(voice.contains("val shownOrbLight = if (onOrbLightChange != null) orbLight else localOrbLight"))
    }

    @Test
    fun `les reglages d Apparence sont enregistres dans le DataStore`() {
        val repo = appSource("data/repo/SettingsRepository.kt")
        listOf(
            "val botFaces: StateFlow<Boolean>",
            "val botPictures: StateFlow<Boolean>",
            "val botColors: StateFlow<Boolean>",
            "val chatTextSize: StateFlow<Int>",
            "val voiceOrb: StateFlow<VoiceOrbChoice>",
            "val orbLight: StateFlow<OrbLight>",
            "suspend fun setBotFaces(value: Boolean)",
            "suspend fun setBotPictures(value: Boolean)",
            "suspend fun setBotColors(value: Boolean)",
            "suspend fun setChatTextSize(steps: Int)",
            "suspend fun setVoiceOrb(orb: VoiceOrbChoice)",
            "suspend fun setOrbLight(light: OrbLight)",
            // Les mêmes clés que `Settings.Keys`.
            "booleanPreferencesKey(\"botFaces\")",
            "booleanPreferencesKey(\"botPictures\")",
            "booleanPreferencesKey(\"botColors\")",
            "intPreferencesKey(\"chatTextSize\")",
            "stringPreferencesKey(\"voiceOrb\")",
            "stringPreferencesKey(\"orbLight\")",
        ).forEach { assertTrue("$it absent du dépôt", repo.contains(it)) }

        val model = appSource("ui/settings/SettingsViewModel.kt")
        assertTrue(model.contains("val botFaces: StateFlow<Boolean> = settingsRepo.botFaces"))
        assertTrue(model.contains("val chatTextSize: StateFlow<Int> = settingsRepo.chatTextSize"))
    }

    @Test
    fun `les faces d orbe reprennent les libelles et l ordre `() {
        val voice = appSource("ui/voice/VoiceScreen.kt")
        // Le vocabulaire , dans son ordre, avec la valeur brute gardée pour le réglage.
        val expected = listOf(
            "onyxFace" to "Onyx",
            "waveform" to "Softer Glass",
            "glassWave" to "Glass",
            "matteWave" to "Matte",
            "flatWave" to "Flat",
            "bubbleWave" to "Bubble",
            "bubbleMatteWave" to "Matte Bubble",
            "navyGlassWave" to "Navy Glass",
            "navyMatteWave" to "Navy Matte",
            "steelGlassWave" to "Steel Glass",
            "inkWave" to "Ink",
            "goldWave" to "Gold",
        )
        var cursor = 0
        expected.forEach { (raw, label) ->
            val at = voice.indexOf("(\"$raw\", \"$label\"", cursor)
            assertTrue("$raw / $label absent ou dans le désordre", at > cursor)
            cursor = at
        }
        // « Dark Glass » n'est pas proposé: le dessine en code, ce port ne l'a pas.
        assertFalse(voice.contains("\"darkGlass\""))

        val lights = appSource("ui/voice/OrbLights.kt")
        listOf("(\"nebula\", \"Nebula\"", "(\"aurora\", \"Aurora\"", "(\"core\", \"Core\"").forEach {
            assertTrue("$it absent", lights.contains(it))
        }
    }

    @Test
    fun `les nouveaux libelles sont traduits en francais`() {
        val french = coreSource("localization/OnyxFrenchSettings.kt")
        listOf(
            "\"Bot faces\"",
            "\"Pictures from Hermes\"",
            "\"Bot colors in their chats\"",
            "\"Chat text size\"",
            "\"Voice orb\"",
            "Off, each bot, Onyx too, is its initial on a circle of its color, and voice mode shows the Bubble orb in place of a face. The bots work the same either way.",
            "A bot with a picture set in Hermes Desktop shows that picture instead of its face.",
            "Your bubbles and the buttons in a bot's chat take its color, and Onyx's own chats take its face's if it has one. Group chats keep the theme's.",
            "The bars move when you speak; the light inside follows \u201COrb light\u201D. Onyx looks at you instead, and talks with the reply.",
            "\u201CChat text size\u201D sets the conversation larger or smaller than the rest of the phone's text. It still follows the phone's own text size from there.",
            "\"Softer Glass\"",
            "\"Matte Bubble\"",
            "\"Navy Glass\"",
            "\"Steel Glass\"",
        ).forEach { assertTrue("$it absente du français", french.contains(it)) }
    }
}
