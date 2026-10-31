package com.onyx.app.ui.sidebar

import com.onyx.app.ui.design.ChatTab

/**
 * **La grille de tuiles du volet, alignée sur celle de la référence** — et les fonctions propres à
 * Onyx, dans un groupe à part, qui ne disparaissent pas.
 *
 * La référence n'a que **quatre routes** (`Loading`, `Connect`, `SignIn`, `Chat`) : sa navigation
 * ne vit pas dans une énumération de routes, elle vit **dans le volet**, dont la grille porte ses
 * sections, deux tuiles par rangée, dans cet ordre :
 *
 * > Inbox · Scheduled · Insights · Capabilities · Plugins · Config · Archived
 *
 * C'est donc **dans le volet** que « 1:1 » se joue, et c'est ici que la table est tenue.
 *
 * ## Groupe 1 — les tuiles de la référence, dans son ordre
 *
 * | Tuile de la référence | Tuile d'Onyx (clé → français) | Écran réel | Même place ? | Même mot ? |
 * |---|---|---|---|---|
 * | Scheduled   | `CRON` → « Planifiés »  | `CronScreen` (tâches planifiées) | oui (2e) | oui |
 * | Insights    | `INSIGHTS` → « Aperçus » | `InsightsScreen` (coût, sessions, jetons) | oui (3e) | oui |
 * | Capabilities| `CAPABILITIES` → « Capacités » | `CapabilitiesScreen` (compétences, jeux d'outils, MCP) | oui (4e) | oui |
 * | Plugins     | `TOOLS` → « Greffons »  | `ToolsScreen` (ce que la passerelle sait activer) | oui (5e) | oui |
 * | Config      | `GATEWAY` → « Configuration » | `GatewayScreen` (la configuration de la passerelle) | oui (6e) | oui |
 * | Archived    | `ARCHIVED` → « Archives » | `ArchivedScreen` (**destination créée ici**) | oui (7e) | le mot de la maison pour l'ensemble |
 * | **Inbox**   | **aucune** | — | **écartée** | — |
 *
 * ### Pourquoi chaque correspondance
 *
 * - **Scheduled → `CRON`** : la référence liste les tâches planifiées ; Onyx aussi (`api/cron/jobs`).
 * - **Insights → `INSIGHTS`** : la référence compte la consommation ; Onyx rend la même chose.
 * - **Capabilities → `CAPABILITIES`** : « ce que l'agent peut utiliser dans les nouvelles
 *   conversations » — compétences, jeux d'outils, serveurs MCP. C'est la même page, mot pour mot
 *   (`CapabilitiesPage` d'un côté, `CapabilitiesScreen` de l'autre). Le mot du propriétaire
 *   (« Compétences ») est la **première des trois familles** que cette page porte.
 * - **Plugins → `TOOLS`** : la référence liste les **greffons du Dashboard qui ont une page à eux**.
 *   Onyx n'a **aucun** appel « lister les greffons » : la page elle-même serait une coquille. Le
 *   **plus proche réel** est l'écran qui liste *ce que la passerelle sait activer* — ses jeux
 *   d'outils (`ToolsScreen`). Les deux autres candidats sont écartés : **MCP** et le **catalogue de
 *   compétences** vivent *dans* « Capacités », les prendre aurait doublé la tuile d'à côté.
 * - **Config → `GATEWAY`** : chez la référence, la roue de la tête ouvre les **Réglages de l'app**,
 *   et la tuile *Config* ouvre la **configuration de la passerelle** — deux écrans différents. Onyx
 *   tient la même séparation : la roue de la tête reste le seul accès aux **Réglages** (`SETTINGS`),
 *   et cette tuile ouvre la page de la **passerelle** (`GatewayScreen`). Mettre ici `SETTINGS` aurait
 *   recréé le doublon que la maison a retiré.
 * - **Archived → `ARCHIVED`** : chez la référence, la tuile *Archived* range la liste sur les
 *   conversations archivées (leur état existe déjà chez Onyx : `Conversation.archived`). Onyx n'en
 *   avait **aucune destination** : elle est **créée ici** (`ArchivedScreen`), avec le
 *   contenu réel de l'appareil et son état vide. Ce n'est pas une coquille.
 * - **Inbox → rien** : la référence montre *ce que les tâches suivies ont rapporté*. Onyx n'a
 *   **aucune** fonction de boîte de réception inter-conversations : `NOTIFICATIONS` est le
 *   **réglage** des notifications d'arrière-plan (un autre écran), et le rapport d'exécution d'une
 *   tâche se lit sous **Planifiés → la tâche → ses exécutions**. **Aucune tuile n'est créée** : une
 *   tuile qui promettrait une page qui n'existe pas serait exactement la coquille à ne pas livrer.
 *   C'est le **seul** écart de la grille, écrit dans [absentFromReference].
 *
 * ## Groupe 2 — ce qui est propre à Onyx
 *
 * Sous un intitulé **« Onyx »**, le reste de l'app : Fichiers du serveur, Modèles, Kanban, Code —
 * **quatre tuiles**, donc deux rangées pleines (2 × 2), alignées à gauche. La tuile « Compétences »
 * n'y est **plus** : « Capacités » (groupe 1) porte la même famille, et l'éditeur a été versé dans
 * cet écran. Les deux groupes sont dessinés par `SidebarTileGrid`, l'un sous l'autre, même grille,
 * même finish.
 *
 * ## Les garde-fous (mesurés par les tests)
 *
 * - aucune tuile ne recouvre un onglet de la barre du bas, ni par sa destination ni par son mot ;
 * - aucune tuile ne double la roue dentée de la tête ;
 * - chaque tuile a son écran, construit par le routeur du volet — **aucune destination morte** ;
 * - l'**ordre** du groupe de la référence est verrouillé (`referenceOrder`), et il est celui de la
 *   référence, amputé du seul Inbox ;
 * - les mots sont ceux de la référence, **en anglais comme clé**, traduits au rendu (« Planifiés »,
 *   « Aperçus », « Capacités », « Greffons », « Configuration ») — un pour un ; la seule exception
 *   est l'ensemble des archives, que la maison nomme déjà « Archives » (« Afficher les archives »).
 */
object SidebarDestinations {

    /** La roue dentée de la tête du volet : le **seul** point d'entrée des Réglages dans le volet. */
    const val HEAD_SETTINGS_TILE = "SETTINGS"

    /** L'intitulé du second groupe : l'app elle-même, sous la grille de la référence. */
    const val ONYX_GROUP_LABEL = "Onyx"

    /**
     * **Groupe 1 — les tuiles de la référence, dans son ordre** (deux par rangée). Aucune ne
     * recouvre un onglet de la barre du bas ni la roue dentée de la tête : c'est la règle, et
     * [conflicts] / [headConflicts] la mesurent.
     *
     * Sa composition et sa table de correspondance sont dans le commentaire de l'objet.
     */
    val referenceTiles: List<SidebarTile> = listOf(
        SidebarTile("CRON", "Scheduled", com.onyx.app.ui.design.Symbols.Calendar),
        SidebarTile("INSIGHTS", "Insights", com.onyx.app.ui.design.Symbols.ChartBar),
        SidebarTile("CAPABILITIES", "Capabilities", com.onyx.app.ui.design.Symbols.SquareStack3dUp),
        SidebarTile("TOOLS", "Plugins", com.onyx.app.ui.design.Symbols.SliderHorizontal3),
        SidebarTile("GATEWAY", "Config", com.onyx.app.ui.design.Symbols.Gearshape),
        SidebarTile("ARCHIVED", "Archives", com.onyx.app.ui.design.Symbols.ArchiveBox),
    )

    /** **Groupe 2 — ce qui est propre à Onyx**, sous l'intitulé « Onyx ». Rien n'est retiré d'autre. */
    val onyxTiles: List<SidebarTile> = listOf(
        // La tuile « Compétences » (`SKILLS`) a été **retirée** : « Capacités » — la tuile
        // *Capabilities* de la référence — porte déjà la même famille (liste groupée par famille,
        // compte, recherche, interrupteur, lecture, catalogue du hub) **et** l'éditeur qui n'y
        // vivait pas. Deux tuiles pour un écran étaient le doublon signalé : il n'en reste qu'une.
        SidebarTile("FILES", "Files browser", com.onyx.app.ui.design.Symbols.Doc, group = SidebarTileGroup.ONYX),
        SidebarTile("MODELS", "Models", com.onyx.app.ui.design.Symbols.Brain, group = SidebarTileGroup.ONYX),
        SidebarTile("KANBAN", "Kanban", com.onyx.app.ui.design.Symbols.RectangleSplit, group = SidebarTileGroup.ONYX),
        SidebarTile("CODE", "Code", com.onyx.app.ui.design.Symbols.Terminal, group = SidebarTileGroup.ONYX),
    )

    /**
     * **Les tuiles du volet, dans l'ordre où il les montre** : le groupe de la référence d'abord,
     * puis « Onyx ». C'est cette liste que le volet dessine, et que les tests interrogent.
     */
    val tiles: List<SidebarTile> = referenceTiles + onyxTiles

    /**
     * **L'ordre du groupe de la référence, verrouillé** : celui de la référence, Inbox excepté.
     * Un test le compare à cette liste — ajouter, retirer ou déplacer une tuile de la référence se
     * voit, et ne peut plus se perdre en silence.
     */
    val referenceOrder: List<String> = referenceTiles.map { it.id }

    /** L'ordre du groupe « Onyx », verrouillé de la même façon. */
    val onyxOrder: List<String> = onyxTiles.map { it.id }

    /**
     * **Les sections de la référence qui n'ont pas de destination chez Onyx**, et pourquoi. Vide
     * n'est pas l'état attendu : c'est ici que la décision est écrite, et un test la relit.
     */
    val absentFromReference: Map<String, String> = linkedMapOf(
        "Inbox" to
            "Onyx n'a aucune boîte de réception inter-conversations. « Notifications » est le " +
            "réglage des notifications d'arrière-plan (un autre écran), et le rapport d'une tâche " +
            "se lit sous Planifiés → la tâche → ses exécutions. Aucune tuile : pas de coquille.",
    )

    /**
     * **Les tuiles fusionnées** : la tuile de la référence et la tuile propre à Onyx qui ouvraient
     * le **même écran**, réunies en une seule — la liste dit laquelle et pourquoi.
     */
    val fusionLog: List<Triple<String, String, String>> = listOf(
        Triple(
            "Plugins",
            "Tools",
            "Le mot de la référence l'emporte : un seul écran, une seule tuile (`TOOLS` → « Greffons »).",
        ),
    )

    /**
     * **Les écrans que le volet porte lui-même** : les tuiles, et les feuilles qu'elles ouvrent
     * (l'éditeur d'une compétence, celui d'un fichier).
     *
     * C'est la demande du propriétaire : une tuile ouvre son écran **dans le volet** (flèche de
     * retour, titre, sous-titre, contenu), le pied du volet reste en place. Un écran de cet
     * ensemble est construit par `OnyxApp` avec un retour qui **dépile le volet** ; les autres
     * destinations de l'app gardent leur présentation pleine page.
     *
     * Un test mesure que chaque tuile a bien son écran dans le volet — et l'ajout d'une tuile sans
     * écran le fait échouer (on ne livre pas une tuile qui n'ouvre rien).
     */
    val paneScreens: Set<String> = tiles.map { it.id }.toSet() + setOf("SKILL_EDITOR", "FILE_EDITOR")

    /**
     * Ce que la **barre du bas** offre : les onglets de contenu de la conversation — **Chat, Flux,
     * Fichiers**. Ce sont des **vues du fil**, pas des routes — c'est pourquoi aucune ne peut
     * recouvrir une tuile du volet, qui ouvre toujours un écran à part entière. La table existe
     * quand même : c'est elle qui rend la règle vérifiable, et elle oblige à se demander, à chaque
     * nouvel onglet, s'il ne double pas une tuile.
     *
     * **« Idées » et « Objectifs » ont été supprimés** (demande explicite du propriétaire) : leurs
     * destinations ne sont plus offertes. Le **repère-visage** qui occupe
     * la quatrième place n'est **pas** une vue du fil : il ouvre l'identité du bot, il n'est donc
     * pas listé ici (voir `ChatTab.BOT`).
     */
    val bottomTabDestinations: Map<String, String> = mapOf(
        ChatTab.CHAT.key to "CHAT_TAB",
        ChatTab.FEED.key to "FEED_TAB",
        ChatTab.FILES.key to "CONVERSATION_FILES",
    )

    /** Les tuiles d'un groupe, dans l'ordre — c'est ce que `SidebarTileGrid` dessine, groupe par groupe. */
    fun group(group: SidebarTileGroup): List<SidebarTile> = tiles.filter { it.group == group }

    /**
     * Les tuiles qui **doublonnent** un onglet de la barre du bas, par leur destination — vide est
     * l'état attendu.
     */
    fun conflicts(tiles: List<SidebarTile>): List<String> =
        tiles.map { it.id }.filter { it in bottomTabDestinations.values.toSet() }

    /**
     * Les tuiles qui **doublonnent la tête du volet** — la roue dentée des Réglages. Vide est l'état
     * attendu : c'est le doublon qui a été retiré, et cette fonction est ce qui empêche la tuile de
     * revenir sans qu'on s'en aperçoive.
     */
    fun headConflicts(tiles: List<SidebarTile>): List<String> =
        tiles.map { it.id }.filter { it == HEAD_SETTINGS_TILE }

    /**
     * Les tuiles dont le **libellé** est celui d'un onglet de la barre du bas. C'est le doublon que
     * la personne a vu (« Fichiers » des deux côtés) : deux entrées, deux écrans, un seul mot. Il se
     * règle par le **nom** — on renomme la tuile, on ne retire pas l'accès.
     */
    fun labelClashes(tiles: List<SidebarTile>): List<String> {
        val tabLabels = ChatTab.entries.map { it.label.lowercase() }.toSet()
        return tiles.filter { it.label.trim().lowercase() in tabLabels }.map { it.label }
    }

    /** Vrai si cette destination est déjà servie par la barre du bas. */
    fun isBottomTab(destination: String): Boolean = destination in bottomTabDestinations.values.toSet()
}
