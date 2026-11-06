# Hermes Onyx 1.3.86

Trois défauts de la carte de prise en main, et un quatrième trouvé en les corrigeant : le plugin qui
la sert ne répondait même pas.

## « Refuser » se voit

La carte portait ses deux pastilles dans l'**en-tête**, à droite du titre. L'action d'une carte est
une place étroite par construction (`ContextCard`, règle 4) : deux pastilles y prenaient toute la
largeur, le titre tombait à quelques pixels, et « Refuser » se lisait comme un reste de texte collé
au bouton d'accent — « on voit rien ».

Les deux gestes sont passés **sous le texte**, sur toute la largeur de la carte — la disposition que
la carte d'approbation utilise déjà pour deux choix, qui est exactement la même situation.

« Refuser » porte en plus l'idiome du bouton bordé de la maison : fond d'encre à 14 %, encre pleine,
cible de 48 dp. Il se lit comme un bouton, sans disputer l'accent.

## Un refus tient, et ne suit pas dans les autres fils

Refuser ne laissait aucune trace : le tour suivant publiait une demande **neuve**, avec un
identifiant que rien ne reliait au refus. La carte revenait à chaque prompt réécrit, et dans les
autres conversations.

- Le serveur retient le refus par **(conversation, page)** pendant 30 minutes : la même question
  n'est pas reposée. Ailleurs — un autre site, une autre conversation — une demande reste légitime.
- `lease` → `decline` **réveille l'appel d'outil** (`status: declined`) : l'agent reprend son tour au
  lieu d'attendre quinze minutes.
- `onyx-browser/health` publie la **conversation** qui demande (`session`) : la carte ne s'affiche que
  dans ce fil.
- L'app garde le même oubli de son côté, **par conversation** et non par identifiant de demande.

## Renommer un bot ne renomme que lui

Le titre d'une conversation de bot est une copie du nom de ce bot. La règle de report reconnaissait
le bot **par son libellé** : deux profils au même nom — ou les fils d'un bot créés sous le nom d'un
autre — partaient ensemble.

Un fil se reconnaît maintenant à sa **session serveur**, ou à son profil **et** un titre qui n'était
rien d'autre que son nom. Un fil qui porte la session d'un autre bot ne suit jamais un renommage par
son titre, et un titre écrit par le propriétaire (« Refonte du parser ») n'est pas réécrit. La liste
remet d'aplomb, à l'affichage seulement, les titres déjà salis par l'ancienne règle.

## Le plugin qui ne répondait pas

Le fichier du plugin écrit après la 1.3.84 référençait `TAKEOVER_TIMEOUT_S` sans l'importer :
`GET /onyx-browser/health` répondait **500**. Rien de ce qui précède n'aurait fonctionné, même bien
installé. L'import est réparé, et le double de test du viewport est aligné sur la méthode que le
serveur appelle vraiment (`repaint_viewport`).

## Vérifié

- `:core:testDebugUnitTest` + `:app:testDebugUnitTest` : **4315 tests, 0 échec**, 1 ignoré, 617 classes.
- Nouveaux tests : `TakeoverDeclinesTest` (5) — une demande neuve dans la même conversation reste
  refusée ; une autre conversation, un autre serveur, n'héritent de rien. `SidebarTitleTest` (6), et
  trois cas de plus dans `BotRenamePropagationTest`.
- `selftest.py` du plugin : **58 vérifications, 0 échec**, dont la mémoire de refus (retenue pour sa
  page et sa conversation, insensible à l'ancre) et le viewport de prise en main.
- `apksigner verify` : trois APK signés, même certificat que les versions précédentes
  (`f58852a2…`), `versionCode 1218`, `versionName 1.3.86`.

**Non vérifié** : rien de tout cela n'a été exercé sur un appareil. La visibilité du bouton est
prouvée par la structure — les deux pastilles ne partagent plus la ligne du titre — et par les tests
JVM, pas par une capture d'écran.