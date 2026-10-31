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
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.di.AppContainer
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

    /** L'action d'un raccourci de bot : « ouvre la conversation de ce bot ». */
    const val ACTION_BOT = "com.onyx.app.action.BOT"

    /** Le nom de profil du bot. */
    const val EXTRA_BOT = "onyx.shortcut.bot"

    /** Son libellé, tel qu'il s'écrit dans le menu. */
    const val EXTRA_BOT_LABEL = "onyx.shortcut.botLabel"

    /** Son `canonical_session` : la conversation déjà gardée pour lui. */
    const val EXTRA_BOT_CHAT = "onyx.shortcut.botChat"

    /** Vrai pour le profil principal (son libellé par défaut vient du serveur, pas du profil). */
    const val EXTRA_BOT_MAIN = "onyx.shortcut.botMain"

    /**
     * Republie la section des bots.
     *
     * [bots] est la section telle qu'elle s'affiche (déjà dans l'ordre enregistré, salles
     * écartées) ; [order] est l'ordre mis à la main (« Reorder Bots »), qui prime dans la règle de
     * sélection ; [pictures] porte les photos du roster quand elles sont chargées.
     *
     * La place laissée par les trois actions et par ce qui est épinglé est déduite de
     * [ShortcutManager.getMaxShortcutCountPerActivity] : rien n'est publié au-delà.
     */
    fun publish(
        context: Context,
        bots: List<BotProfile>,
        order: List<String>,
        mainName: String,
        pictures: Map<String, ImageBitmap> = emptyMap(),
    ) {
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
            // La couleur vient du **visage résolu du roster** (`BotProfile.faceCode`, réparti par la
            // section) : c'est celle que l'app montre partout. À défaut, celle gardée sur ce
            // téléphone, puis celle du nom.
            val faceCode = shortcut.faceCode ?: faceCode(context, shortcut.bot)
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
        // `setDynamicShortcuts` remplace **tout** l'ensemble publié : un bot qui n'est plus là
        // n'a plus de raccourci. La limite de débit de la plateforme (une app en arrière-plan)
        // fait échouer l'appel : la prochaine lecture du roster republie.
        runCatching { manager.setDynamicShortcuts(infos) }
    }

    /** La catégorie des raccourcis de conversation — celle des trois actions, pour la même tenue. */
    const val CATEGORY_CONVERSATION = "android.shortcut.conversation"

    /** L'intention d'un raccourci de bot : la demande, portée jusqu'au dépôt de l'app. */
    fun intent(context: Context, shortcut: BotShortcut): Intent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_BOT
            putExtra(EXTRA_BOT, shortcut.bot)
            putExtra(EXTRA_BOT_LABEL, shortcut.label)
            putExtra(EXTRA_BOT_MAIN, shortcut.isDefault)
            shortcut.chatId?.let { putExtra(EXTRA_BOT_CHAT, it) }
            // Un lanceur réveille l'app en **tête de tâche** : la conversation demandée s'ouvre
            // sur une route neuve (le fil), plutôt que de retomber sur l'écran quitté la dernière
            // fois (un raccourci pris depuis l'écran des réglages ouvrirait les réglages).
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

    /** Le lanceur a raconté qu'un raccourci a été pris : la plateforme tient le compte. */
    fun reportUsed(context: Context, shortcutId: String) {
        runCatching {
            context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(shortcutId)
        }
    }

    /** Les identifiants des raccourcis de bots publiés pour un roster (pour les essais). */
    fun ids(bots: List<BotProfile>, order: List<String>, mainName: String): List<String> =
        BotShortcutPlan.schedule(bots, order, mainName).map { it.id }

    /** Le visage gardé sur ce téléphone pour un bot, quand le roster n'en a pas résolu. */
    private fun faceCode(context: Context, bot: String): String? =
        runCatching { AppContainer.botFaces.faceOf(bot) }.getOrNull()

    /** Le thème tel que l'app le rend, pour la couleur d'un visage « thème ». */
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
