# Compact chat acceptance ownership

The approved compact conversation replaces the former 140dp composer/tab plate. Exact source snapshots of that drawing are not visual evidence. Native Robolectric Compose tests now own contextual dispatch (including running with draft), single visible Stop, labeled plus actions, selected recipient, visible errors, bounded growth, target bounds and 320/740dp resize with 1.5x font. ChatScreen navigation tests start at the conversation, tap header overflow to Feed/Files/identity, invoke Back, retain the draft, and reach model/conversation callbacks. These are fixtures, not Fold8/OEM/IME or real speech proof.

Unchanged mixed tests retain reflection, subagent stop, no duplicate inset, obsolete destination refusal, theme and group contracts. Insets gates now measure the surviving composer rather than the removed tab row.

## Superseded source-layout methods

- `ResponsiveLayoutTest.le placeholder du composeur tient sur une ligne` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `FaceGateIdentityTest.l appui sur le repere-visage ouvre l identite, pas une page du pageur` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `FieldComposerGrowingTest.la rangee du champ grandit avec le contenu, elle n est plus figee a 64 dp` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `FieldComposerGrowingTest.la decision une ligne ou plusieurs est prise a une largeur fixe` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `PlateFinalWiringTest.la rangee du champ vaut 64 dp au repos, celle des onglets 76, et elle grandit` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `PlateFinalWiringTest.le filet interne separe le champ des onglets` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `PlateTouchupsWiringTest.le libelle du champ est celui de la planche, choisi par la mesure` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `PlateTouchupsWiringTest.la plaque vaut 140 dp au repos et grandit avec le champ, sans jamais couper` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `RemovalHandleChevronTest.la barre d onglets est posee sans aucune condition de visibilite` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `TabsPlateWiringTest.la rangee d onglets centre sa barre dans ses 76 dp` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `TabsPlateWiringTest.la barre ne recoit aucune poignee, les quatre colonnes restent egales` → CompactComposerTest / CompactChatNavigationTest. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `FieldComposerGrowingTest.la morsure — la source d avant est refusee par les deux predicats` → CompactComposerTest.compactRestBoundedGrowthAndResizeKeepDraftAndTargets. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.
- `FaceGateIdentityTest.remettre l ancien routage fait echouer la regle` → CompactChatNavigationTest.overflowReachesFeedFilesAndIdentityAndBackKeepsDraft. Removed exact-source structure expectation; approved structure supersedes old drawing, not the capability.

- `ComposerLayoutTest.le champ de saisie prend le tap sur toute la hauteur reservee` → native bottom-edge text-field tap and focus assertion in CompactComposerTest.

Baseline 94d930d paired native probes failed for expected reasons (visible bottom Feed tab, absent labeled plus menu, undersized attachment remove, three-line rest height, and wrong recipient placeholder). Original production files were restored byte-for-byte after the RED run. The subsequent explicit compact opt-in separates single-conversation layout from shared group plate styling; group routing/auth admission was not changed.
