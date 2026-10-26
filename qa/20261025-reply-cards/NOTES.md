# Cartes de réponse 2.1 — capture Android du 25/10/2026

Implémentation de la nouveauté 2.1 (`ReplyCards`, outil `onyx_show` du plugin `onyx-push`) : les cartes
qu'une réponse montre sous ses mots — **carte**, **lieux**, **météo**, **heure**. Le  du dépôt
 (`a82ecc7`, , ) est la
spécification.

- Worktree : `/home/john/onyx-cards21` (branche `cards21`), base `895ec29` (le HEAD de
  `onyx-integration2` au moment du implémentation).
- Commits : `ff27b6b` (modèle, transport, rendu, carte OSM), `d3eef58` (jour abrégé, bouton non
  rogné), `4375bd9` (phrases traduites, colonnes à l'échelle de police).
- APK release installé sur `emulator-5582` : `versionCode 236`, `versionName 1.0.0+236`,
  `sha256 d6ac6db2eefd3f177182b138dc9bf8cba70b10efb46510ce413e29f362dd7d24` — **vérifié sur
  l'appareil** (APK retiré par `pm path` puis haché : même empreinte). C'est le build de ce
  commit (`5a3ed72`) ; la passe de captures a commencé sur le build précédent (`4375bd9`,
  `versionCode 234`, même code applicatif).
- Émulateur remis en état après la passe (`wm size/density` d'origine, `font_scale 1.0`,
  langue `en-US`, locale d'app effacée).

## Vérification

```
JAVA_HOME=/home/john/android-toolchain/jdk-21 ANDROID_HOME=/home/john/android-toolchain/sdk \
/home/john/android-toolchain/gradle/bin/gradle :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease \
  --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=3
```

`BUILD SUCCESSFUL` — **748 épreuves, 0 échec, 0 erreur** (`*/build/test-results/testDebugUnitTest/*.xml`),
dont 30 nouvelles : 10 pour le modèle et le schéma, 6 pour la géométrie des tuiles, 8 pour les mots
et les chiffres des cartes, 6 pour la localisation.

## Ce que les captures montrent

| Capture | Ce qu'elle montre |
|:--|:--|
| `shot-A-map.png` | carte d'un chemin : tuiles OSM, épingle de départ, drapeau d'arrivée, trait du chemin, « © OpenStreetMap », « Walk · The office to Rossio Roasters · ≈ 1.2 km straight line · Directions » |
| `shot-B-places.png` | lieux : carte (épingles numérotées 1-2-3), liste numérotée, catégorie · ★ note, adresse, note, appel / site / carte |
| `shot-C-weather.png` | météo : maintenant (icône, 19°, H/L, ressenti, vent, humidité, pluie), 8 heures (icônes, °, % de pluie), 5 jours (jour, icône, %, mini, barre, maxi), source créditée |
| `shot-D-time.png` | heure : horloge, « Tokyo », jour long, 01:4x, écart (« 8 hr ahead ») |
| `fr-02-weather.png` | les mêmes en **français** : « Partiellement nuageux », « Vent · Humidité · Pluie », « 10 h…17 h », « ven./sam./Aujourd'hui/lun./mar. », « 8 h d'avance » |
| `fr-04-single-pane-weather.png` | à **une seule colonne** (1080×2340), en français : rien ne déborde |
| `shot-G-font130-weather.png` | à **police 1,3×** : heures, jours, barres et colonnes tiennent |
| `shot-H-offline.png` | **sans réseau** : la carte cède la place à « Map unavailable » et à la liste des lieux — jamais un cadre vide |
| `fr-05-dark.png` | apparence **sombre** : les cartes suivent le thème (icônes de météo à leurs couleurs) |
| `fr-01-weather-time.png` | les deux panneaux sur tablette (2200×2480), carte « À pied · De … à … · Itinéraire » |

Modèle  : le dépôt n'a pas de capture des cartes ( en
produit à l'exécution). La comparaison se fait donc sur la spécification  : structure des
cartes, champs dessinés, libellés et actions — reprise ligne à ligne dans `ReplyCardViews.kt`.

## Adaptations Android assumées (et pourquoi)

1. **La carte n'est pas MapKit** : les tuiles **OpenStreetMap** sont composées à la main
   (`OsmTiles` : Mercator, zoom sur la boîte englobante des points, 24 tuiles au plus). Pas de clé
   d'API, pas de SDK payant. L'attribution « © OpenStreetMap » est dessinée — la politique de
   tuiles l'exige — et un appui ouvre l'app de cartes du téléphone (`geo:`), ou le site d'OSM
   quand il y a plusieurs lieux.
2. **Pas de moteur de routage** :  demande l'itinéraire à MKDirections (durée, distance, tracé
   réel). Ici le trait est droit et pointillé, et la ligne écrit la distance honnête —
   « ≈ 1,2 km à vol d'oiseau » — au lieu d'inventer une durée d'itinéraire.
3. **Pas de géocodage sur le téléphone** : un lieu sans coordonnées (MapKit les cherchait) ne peut
   pas être placé ; la carte se replie alors sur la liste des lieux, avec « Map unavailable ».
4. **Pas de recherche de prévision sur le téléphone** :  interroge Open-Meteo quand le plugin
   n'a rien rempli (`CardLookup`). Ici la météo et l'heure sont celles que le plugin renvoie (il
   les cherche côté Hermes, 1.7.0) ; une carte qui n'a que son lieu et pas encore de relevé reste
   non dessinée (avec « Looking up the weather in … » pendant que le tour arrive).

## Ce qui reste

- La recherche côté téléphone (points 3 et 4 ci-dessus), si on veut la cohérence complète quand le
  plugin est plus ancien que 1.7.0 ou injoignable.
- La durée et la distance d'itinéraire réelles (moteur de routage OSRM/Valhalla), pour remplacer
  la ligne droite.
- `Reduce Motion` : rien à couper dans ces cartes (aucune animation décorative ; seuls l'horloge
  à la minute et l'indicateur d'attente tournent, comme ). Les colonnes fixes suivent
  l'échelle de police du téléphone, la géométrie de la carte non.
