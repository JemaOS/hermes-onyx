package com.onyx.app.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.onyx.app.MainActivity
import com.onyx.app.data.local.BotPresenceIndex
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.di.AppContainer
import com.onyx.app.ui.bots.BotFaceResolver
import com.onyx.app.ui.settings.AppIconController
import com.onyx.app.ui.theme.OnyxFonts
import com.onyx.app.ui.theme.ResolvedTheme

/**
 * La **section des bots** du menu qui s'ouvre sur un appui long sur l'icône.
 *
 * Un raccourci par bot, portant sa **couleur** et sa **tuile** ([BotTileArtwork]), et ouvrant une
 * conversation **avec ce bot** ([ACTION_BOT] → `BotShortcutInbox` → le chat du bot).
 *
 * Publiée à l'exécution, parce que la liste des bots vient du serveur : c'est la seule partie du
 * menu qui ne peut pas être déclarée dans `res/xml/shortcuts.xml`. Les trois raccourcis d'action
 * (`ask`, `talk`, `note`) y sont déclarés et **ne bougent pas** : ici on n'ajoute que la section.
 *
 * **Cohérence** : [publish] est rappelée à chaque lecture du roster par la section des bots, et
 * `setDynamicShortcuts` **remplace l'ensemble** — donc :
 * - un bot qui apparaît reçoit sa tuile ;
 * - un bot qui disparaît perd la sienne (aucun raccourci ne survit au roster) ;
 * - un bot renommé change d'identifiant (il est dérivé du nom de profil) : l'ancien s'efface ;
 * - un bot qui change de visage est republié avec sa nouvelle couleur.
 */
object RosterShortcuts {

    /** The action of a bot shortcut: "opens this bot's conversation". */
    const val ACTION_BOT = "com.onyx.app.action.BOT"

    /** The bot's profile name. */
    const val EXTRA_BOT = "onyx.shortcut.bot"

    /** Its label, as it is written in the menu. */
    const val EXTRA_BOT_LABEL = "onyx.shortcut.botLabel"

    /** Its `canonical_session`: the conversation already kept for it. */
    const val EXTRA_BOT_CHAT = "onyx.shortcut.botChat"

    /** True for the main profile (its default label comes from the server, not from the profile). */
    const val EXTRA_BOT_MAIN = "onyx.shortcut.botMain"

    /**
     * Republishes the bots section.
     *
     * [bots] is the section as it is displayed (already in the saved order, rooms excluded);
     * [order] is the hand-set order ("Reorder Bots"), which takes precedence in the selection
     * rule; [pictures] carries the roster's photos when they are loaded.
     *
     * The room left by the three actions and by what is pinned is deduced from
     * [ShortcutManager.getMaxShortcutCountPerActivity]: nothing is published beyond it.
     *
     * [faceResolver] is **the single resolution** of the face (`BotFaceResolver`, the roster
     * table): the tile never rereads the local store to draw. A shortcut whose profile already
     * carries its code does not even need it; the resolver is only used for a name it does not
     * know, and an empty resolver (`BotFaceResolver.none`) then lets the name decide.
     */
    fun publish(
        context: Context,
        bots: List<BotProfile>,
        order: List<String>,
        mainName: String,
        pictures: Map<String, ImageBitmap> = emptyMap(),
        faceResolver: BotFaceResolver = BotFaceResolver.none,
    ) {
        // What the phone knows about the bots, for what has no screen (the widget): published here
        // because this is where the roster arrives, on every read.
        runCatching { BotPresenceIndex(context).publish(bots, mainName) }
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val stored = manager.maxShortcutCountPerActivity
        val taken = BotShortcutPlan.ACTION_IDS.size + runCatching { manager.pinnedShortcuts.size }.getOrDefault(0)
        val room = (stored - taken).coerceAtLeast(0)
        val planned = BotShortcutPlan.schedule(
            bots = bots,
            order = order,
            mainName = mainName,
            maxBots = minOf(BotShortcutPlan.MAX_BOTS, room),
        )
        val theme = resolvedTheme()
        val component = launcherComponent(context)
        val side = minOf(manager.iconMaxWidth, manager.iconMaxHeight, BotTileArtwork.SIZE).coerceAtLeast(48)
        val infos = planned.mapIndexed { index, shortcut ->
            val picture = pictures[shortcut.bot] ?: pictures[shortcut.label]
            // The colour comes from the **resolved face of the roster** (`BotProfile.faceCode`,
            // distributed by the section): it is the one the app shows everywhere. Failing that,
            // the one the **single resolution** returns for this name ([faceResolver]) — never
            // again a second read of the local store, which made the tile a separate face path.
            val faceCode = shortcut.faceCode ?: faceResolver.codeOf(shortcut.bot)
            val bitmap: Bitmap = if (picture != null) {
                BotTileArtwork.photoTile(
                    photo = picture.asAndroidBitmap(),
                    deep = BotTileArtwork.botColour(shortcut.bot, faceCode, theme),
                    size = side,
                )
            } else {
                BotTileArtwork.botTile(
                    name = shortcut.bot,
                    faceCode = faceCode,
                    label = shortcut.label,
                    theme = theme,
                    size = side,
                )
            }
            ShortcutInfo.Builder(context, shortcut.id)
                .setActivity(component)
                .setShortLabel(shortcut.label)
                .setLongLabel(BotShortcutPlan.longLabel(shortcut))
                .setIcon(Icon.createWithBitmap(bitmap))
                .setIntent(intent(context, shortcut))
                .setRank(BotShortcutPlan.FIRST_RANK + index)
                .setCategories(setOf(CATEGORY_CONVERSATION))
                .build()
        }
        // `setDynamicShortcuts` replaces **the whole** published set: a bot that is no longer there
        // has no shortcut any more. The platform rate limit (an app in the background)
        // makes the call fail: the next read of the roster republishes.
        runCatching { manager.setDynamicShortcuts(infos) }
    }

    /** The conversation shortcuts' category — the three actions' one, for the same garb. */
    const val CATEGORY_CONVERSATION = "android.shortcut.conversation"

    /** The intent of a bot shortcut: the request, carried all the way to the app's holder. */
    fun intent(context: Context, shortcut: BotShortcut): Intent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_BOT
            putExtra(EXTRA_BOT, shortcut.bot)
            putExtra(EXTRA_BOT_LABEL, shortcut.label)
            putExtra(EXTRA_BOT_MAIN, shortcut.isDefault)
            shortcut.chatId?.let { putExtra(EXTRA_BOT_CHAT, it) }
            // A launcher wakes the app as **task root**: the requested conversation opens
            // on a fresh route (the thread), instead of falling back on the screen left the last
            // time (a shortcut taken from the settings screen would open the settings).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return intent
    }

    /**
     * Le composant **lanceur** du raccourci.
     *
     * Les raccourcis d'un appui long appartiennent au composant que le lanceur interroge : l'alias
     * d'icône actif, pas `MainActivity` (l'icône de l'écran d'accueil est un alias, et un raccourci
     * publié sur un autre composant n'apparaît sous **aucune** icône).
     */
    fun launcherComponent(context: Context): ComponentName {
        val choice = AppIconController(context.applicationContext).current()
        return ComponentName(context.packageName, context.packageName + choice.aliasName)
    }

    /** The launcher reported that a shortcut was taken: the platform keeps the count. */
    fun reportUsed(context: Context, shortcutId: String) {
        runCatching {
            context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(shortcutId)
        }
    }

    /** The identifiers of the bot shortcuts published for a roster (for the tests). */
    fun ids(bots: List<BotProfile>, order: List<String>, mainName: String): List<String> =
        BotShortcutPlan.schedule(bots, order, mainName).map { it.id }

    /** The theme as the app renders it, for a "theme" face's colour. */
    private fun resolvedTheme(): ResolvedTheme {
        if (!AppContainer.isInstalled) return ResolvedTheme.fallback()
        return runCatching {
            val settings = AppContainer.settings
            val theme = settings.theme.value
            ResolvedTheme.resolve(
                theme = theme,
                dark = false,
                fonts = OnyxFonts.System,
                colors = settings.themeColors.value[theme.id],
                pureBlack = settings.pureBlack.value,
                halo = settings.halo.value,
            )
        }.getOrElse { ResolvedTheme.fallback() }
    }
}
