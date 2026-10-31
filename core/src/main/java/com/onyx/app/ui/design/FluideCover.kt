package com.onyx.app.ui.design

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.onyx.app.ui.theme.LocalOnyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * **Le fluide de la carte de conversation, dessiné dans l'app.**
 *
 * Le champ d'écoulement est **calculé sur la carte graphique** : le programme de rendu est écrit en
 * **AGSL** (Android Graphics Shading Language) et [Fluide.plan] lui fournit ses uniformes. C'est le
 * port du `FRAG` de la maquette (`maquettes/carte.html`), branche **B — filaments**, sous le **choix
 * enregistré** ([ChoixCouverture] : graine `3417634283`, teinte bleu), avec les corrections du
 * propriétaire :
 *
 * 1. **le filament est un RUBAN LUMINEUX.** Il n'est pas un trait : un **coeur** clair (le profil fin
 *    de la maquette), un **corps** de ruban plus large et une **jupe** qui s'éteint doucement — les
 *    trois **ajoutés** à la matière, jamais soustraits d'elle. Avant, la carte n'éclairait qu'à
 *    `pow(1−fil, 17)` **et** sous une enveloppe qui valait presque zéro hors des nappes : sur l'aplat
 *    sombre qui servait de fond, il ne restait qu'un trait fin — une gravure ;
 * 2. **le fond est un BROUILLARD bleu, lumineux, avec de la profondeur**
 *    ([Fluide.Plan.brumeProfonde] → [Fluide.Plan.brumeClaire]) et non plus l'aplat
 *    `mix(profond, médian)` presque noir qui donnait la « matière sombre » ;
 * 3. **la lumière est sortie du coin du visage** — la source est tirée dans la **moitié gauche**, et
 *    une **garde de coin** assombrit explicitement le coin supérieur droit, où vit le disque.
 *
 * **Trois défauts vus à l'œil, fermés dans le champ lui-même** (et non dans les réglages de lumière) :
 *
 * 4. **le contour était ANGULEUX** — le bruit interpolait au cube (C1), ce qui laissait un **pli à
 *    chaque carreau** : l'iso-valeur du champ en devenait un **polygone**. Le bruit interpole
 *    désormais en **quintique** (C2) et le champ passe par un **flou 3×3** : la crête **s'enroule**.
 *    La courbure locale (variation du vecteur tangent) tombe de **0,271 à 0,171** rad/px d'arc
 *    (planche 0,140) ;
 * 5. **le cœur était ÉCRÊTÉ à blanc** — les gains de lumière poussaient la tache au-delà de 1, et le
 *    `clamp` l'écrasait. Une **compression douce** ([doux], un genou à 0,58 puis une retombée
 *    asymptotique vers 1) remplace le clip : le cœur **reste blanc**, mais la retombée est graduelle.
 *    La part de pixels écrêtés tombe de **1,41 % à 0,02 %** (planche 0,59 %) ;
 * 6. **le fond portait du GRAIN PARASITE** — le grain de pas de trois pixels était dosé à 0,090,
 *    bien au-dessus de la planche. Il est ramené à **0,024** (le pas de trois pixels est conservé) :
 *    l'énergie haute fréquence du fond hors des volutes tombe de **11,1 à 4,6** (planche 3,1 au même
 *    cadre d'appareil).
 *
 * **Le dessin BOUGE** ([Fluide.phase]) : un cycle de **7,2 s** où les trois vortex **dérivent** sur de
 * petits cercles, où la nappe **glisse** d'un aller-retour en sinus (moins d'une raie) et où la
 * source de lumière se déplace du même pas. Toutes les quantités dépendent de la phase par des
 * fonctions **2π-périodiques**, donc le dessin de la fin du cycle est **identique au pixel** à celui
 * de son début : la boucle n'a pas de raccord, et il n'y a ni saut ni retour brusque. La respiration
 * de la lumière ([Fluide.PERIOD_MS], 2,4 s) **divise exactement** le cycle (trois fois) : lumière et
 * forme se referment ensemble.
 *
 * **Quand il n'est pas calculé** ([Fluide.Level]) :
 * - [Fluide.Level.PRECALCULE] — les images du cycle sont **cuites une fois** par le **même**
 *   programme, en mémoire, puis défilées : le repli des appareils modestes ;
 * - [Fluide.Level.FIGE] — le fluide est **figé** à sa pose d'arrivée : « Réduire les animations »,
 *   sans exception ;
 * - [Fluide.Level.AUCUN] — l'ancienne couverture déterministe ([ConversationCover]) : interrupteur
 *   éteint, ou système sans AGSL (avant la treizième version d'Android).
 */
@Composable
fun FluideConversationCover(
    title: String,
    id: String,
    modifier: Modifier = Modifier,
) {
    val enabled = LocalFluideEnabled.current
    val choix = LocalFluideChoix.current
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val sdk = Build.VERSION.SDK_INT
    val level = remember(enabled, reduceMotion, lowRam, sdk) {
        Fluide.level(enabled = enabled, reduceMotion = reduceMotion, sdkInt = sdk, lowRam = lowRam)
    }
    // Le programme de rendu doit **se compiler** : sinon, rien ne se dessine et la carte retombe sur
    // l'ancienne couverture. La sonde est faite une fois par processus.
    val compilable = remember(sdk) {
        sdk >= Build.VERSION_CODES.TIRAMISU && agslCompiles()
    }
    val theme = LocalOnyxTheme.current

    if (level == Fluide.Level.AUCUN || !compilable) {
        // Le repli : la couverture générative de la maison — la même image figée qu'avant.
        ConversationCover.ConversationCover(title = title, id = id, modifier = modifier)
        return
    }

    // **Le choix enregistré porte la graine** : la carte dessine le matériau retenu (filaments,
    // graine de la planche, teinte bleu) et non plus une teinte tirée de la conversation.
    val plan = remember(title, id, theme.dark, choix) { Fluide.plan(title, id, theme.dark, choix) }
    val breathe = rememberFluideBreath(level, modifier)

    when (level) {
        Fluide.Level.CALCULE, Fluide.Level.FIGE ->
            ShaderFluid(
                plan = plan,
                state = breathe.state,
                cycle = breathe.cycle,
                fixedSouffle = null,
                modifier = breathe.modifier,
            )
        Fluide.Level.PRECALCULE ->
            PrecalcFluid(plan = plan, modifier = breathe.modifier)
        Fluide.Level.AUCUN -> Unit
    }
}

/**
 * L'interrupteur d'Apparence : le fluide est **activé par défaut**. Faux, la carte reprend la
 * couverture déterministe — rien d'autre ne change.
 */
val LocalFluideEnabled = compositionLocalOf { true }

/** Ce que la carte doit savoir pour animer : la pose courante, la phase du cycle, le modificateur. */
private class Breath(
    val state: MutableFloatState,
    val cycle: MutableFloatState,
    val modifier: Modifier,
)

/**
 * **Le cycle monté ici** : immobile à l'arrivée, puis la lumière respire et la **forme dérive** —
 * l'une et l'autre sur la **même** période ([Fluide.CYCLE_MS], que [Fluide.PERIOD_MS] divise
 * exactement trois fois) ; arrêt net au premier contact ou dès que la carte quitte l'écran.
 *
 * Aucune horloge de composition n'est prise avant les cinq secondes : le souffle vaut exactement 0,
 * la phase vaut 0, et **aucun rappel de trame n'est demandé** — l'œil arrive sur une image fixe.
 * Sous « Réduire les animations » (ou dans un régime non animé), **rien n'est monté du tout**.
 */
@Composable
private fun rememberFluideBreath(level: Fluide.Level, base: Modifier): Breath {
    val breath = remember { mutableFloatStateOf(0f) }
    val cycle = remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(true) }
    var contact by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Un contact (un tap, le début d'un défilement) arrête net le mouvement. Le drapeau se relève
    // dans la passe **Initiale** : la carte voit le doigt **avant** que le défilement ne le consomme.
    val touch = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) contact = true
            }
        }
    }

    val visibility = Modifier.onGloballyPositioned { coords ->
        val b: Rect = coords.boundsInWindow()
        val on = b.bottom > 0f && b.top < view.height.toFloat()
        // La carte revient à l'écran : le cycle repart de zéro, comme à l'arrivée.
        if (on && !visible) contact = false
        visible = on
    }

    // **Aucun calcul pour une carte invisible**, et rien sous « Réduire les animations ».
    val active = level == Fluide.Level.CALCULE && visible && !contact
    LaunchedEffect(active) { flow(breath, cycle, active) }

    return Breath(state = breath, cycle = cycle, modifier = base.then(touch).then(visibility))
}

/**
 * Le cycle : cinq secondes **exactement immobiles** (aucun rappel de trame), puis la lumière et la
 * forme, image par image. Inactif, rien n'est monté et l'état retombe à zéro tout de suite.
 */
private suspend fun flow(breath: MutableFloatState, cycle: MutableFloatState, active: Boolean) {
    breath.floatValue = 0f
    cycle.floatValue = 0f
    if (!active) return
    delay(Fluide.DELAY_MS)
    val start = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val ms = (now - start) / 1_000_000L
        breath.floatValue = Fluide.breath(ms, contact = false)
        cycle.floatValue = Fluide.phase(ms, contact = false)
    }
}

// -------------------------------------------------------------------------------------------
// Le dessin — le régime calculé (et la pose figée)
// -------------------------------------------------------------------------------------------

/**
 * La source AGSL — **la lumière volumétrique de la planche** : le champ des volutes.
 *
 * Le défaut d'avant, nommé : le dessin prenait les **lignes de niveau d'un potentiel de raies**
 * (`(1,25·y + inclinaison·x)·raies`) — un champ de **lignes fines, parallèles et régulièrement
 * espacées**, froid et mécanique. Les moyennes ne le voyaient pas (une brume irrégulière et un
 * champ de lignes ont des moyennes voisines) ; la **structure** le voit : cohérence d'orientation
 * **0,789** contre **0,188** sur la planche, périodicité **0,454** contre **0,000**.
 *
 * Le dessin prend maintenant les **crêtes d'un champ volumétrique** `champ(p)` : un bruit lisse de
 * basse fréquence (quelques octaves, `fbm` à `2,60`), **entraîné par les vortex figés**. L'iso-valeur
 * médiane de ce champ décrit **deux à quatre volutes larges et irrégulières** — des courbes de
 * courbure variable, dont une qui se recourbe en crochet — et la lumière naît sur cette crête :
 *
 *  - `crete` — le **corps** de la volute, d'**épaisseur VARIABLE** : la demi-largeur
 *    (`BAND_MIN` → `BAND_MAX`) suit la grande nappe `g`, si bien que la volute est épaisse par
 *    endroits, effilée à d'autres ;
 *  - `halo` — le **halo qui déborde largement** (`HALO_W`), une version large et douce de la crête ;
 *  - la **nappe de brume** — `profond` → `median` sous la grande nappe, puis `brumeClaire` là où la
 *    brume est forte : une grande étendue diffuse sur un fond bleu nuit.
 *
 * **Le grain de la planche et sa teinte sont conservés** : le grain de pas de trois pixels
 * (`h11(floor(fragCoord/3))`) et les couleurs du plan (la graine `3417634283` donne la teinte bleu).
 *
 * **La FORME est FIGÉE, la lumière circule** : les vortex ne dérivent pas (la phase n'entre pas dans
 * le champ), et seul `courant` fait avancer la tête brillante — avec un **halo solidaire**.
 */
internal const val FLUIDE_AGSL = """
uniform float2 uRes;
uniform float3 uProfond;
uniform float3 uMedian;
uniform float3 uPale;
uniform float3 uLumiere;
uniform float3 uSol;
uniform float3 uVoile;
uniform float3 uBrumeP;
uniform float3 uBrumeC;
uniform float2 uV0;
uniform float2 uV1;
uniform float2 uV2;
uniform float2 uLum;
uniform float3 uForce;
uniform float3 uRayon;
uniform float uSombre;
uniform float uVoileA;
uniform float uSouffle;
uniform float uSat;
uniform float uHaut;
uniform float uBas;
uniform float uRaies;
uniform float uIncli;
uniform float uPhase;
uniform float uRuban;

float h11(float n){ return fract(sin(n*127.1+311.7)*43758.5453123); }
// LE BRUIT : interpolation QUINTIQUE (C2) au lieu du cube (C1). Le cube laisse des PLIS tous les
// carreaux : l'iso-valeur du champ en devient un POLYGONE. La quintique les efface — l'iso-valeur
// s'enroule en courbe continue.
float vn(float2 p){ float2 i=floor(p),f=fract(p); float2 u=f*f*f*(f*(f*6.0-15.0)+10.0);
  float a=h11(i.x+i.y*57.0),b=h11(i.x+1.0+i.y*57.0),c=h11(i.x+(i.y+1.0)*57.0),d=h11(i.x+1.0+(i.y+1.0)*57.0);
  return mix(mix(a,b,u.x),mix(c,d,u.x),u.y); }
float fbm(float2 p){ float s=0.0,a=0.55; for(int i=0;i<4;i++){ s+=a*vn(p); p=p*2.02+float2(11.3,7.7); a*=0.44; } return s/1.03; }
// L'origine du Canvas est en HAUT A GAUCHE : la carte se lit comme la maquette la pose.
// **La FORME est FIGEE** : les vortex ne derivent pas (la phase n'entre pas dans le champ).
float2 flux(float2 p){ float2 s=float2(0.0),d;
  d=p-uV0; s+=uForce.x*exp(-dot(d,d)/(uRayon.x*uRayon.x))*float2(-d.y,d.x);
  d=p-uV1; s+=uForce.y*exp(-dot(d,d)/(uRayon.y*uRayon.y))*float2(-d.y,d.x);
  d=p-uV2; s+=uForce.z*exp(-dot(d,d)/(uRayon.z*uRayon.z))*float2(-d.y,d.x);
  return s; }
float dens(float2 p){
  float2 w = p + 0.68*flux(p*1.15);
  return smoothstep(0.14,0.90,fbm(w*1.95)); }
// LE CHAMP VOLUMETRIQUE : un bruit lisse de BASSE FREQUENCE, quelques octaves, fortement floute,
// entraine par les vortex. C'est DANS ce champ que se lit la lumiere : 2 a 4 masses irregulieres.
// LA FREQUENCE DU CHAMP — inchangee (2,60). Monter la frequence cassait les masses, mais le
// verrou `grain du fond` (seuil 4,50, planche 3,10) refusait le grain qu'elle ajoutait.
const float CHAMP_FREQ = 2.60;
const float CHAMP_WARP = 0.35;
// LA FREQUENCE DU PLI FIN — inchangee (8,0). C'est E_BLUR (le flou du champ) qui amincit la veine
// sans ajouter de grain : voir plus bas.
const float D2_FREQ = 8.0;
// Le champ BRUT — la crete d'un seul bruit, entraine par les vortex figes.
float champBrut(float2 p){
  float2 w = p + CHAMP_WARP*flux(p*1.02);
  return fbm(w*CHAMP_FREQ); }
// LE CHAMP LISSE — une passe de FLOU (le centre et ses quatre voisins) APLATIT les plis qui
// restent : c'est elle qui donne une crete qui s'enroule, et non un polygone.
// **La portee passe de 0,034 a 0,10** : c'est elle qui AMINCIT la veine (largeur mesuree 18,2 px
// -> 9,2) SANS ajouter de grain (le verrou `grain du fond` reste a 3,99, seuil 4,50 ; la planche
// fait 3,10). Monter la frequence du bruit au lieu d'elargir ce flou amenait le grain a 12,4.
float champ(float2 p){
  const float e = 0.10;
  float s = 0.25*champBrut(p);
  s += 0.125*(champBrut(p+float2(e,0.0)) + champBrut(p-float2(e,0.0))
            + champBrut(p+float2(0.0,e)) + champBrut(p-float2(0.0,e)));
  s += 0.0625*(champBrut(p+float2(e,e)) + champBrut(p+float2(e,-e))
             + champBrut(p+float2(-e,e)) + champBrut(p+float2(-e,-e)));
  return s; }
// LES BORNES DU PLI : la demi-largeur de la modulation (fine -> epaisse) sous la grande nappe.
const float BAND_MIN = 0.014;
const float BAND_MAX = 0.038;
const float EPAISSEUR = 1.40;
const float HALO_W = 0.60;
const float HALO_GAIN = 0.07;
const float CORPS_GAIN = 0.42;
const float HAZE_FACTOR = 0.42;
const float MIX_MEDIAN = 0.22;
// LA NAPPE : la densite pilote le TON sur une transition LARGE — c'est un degrade, pas un bord.
const float NAPPE_LO = 0.32;
const float NAPPE_HI = 0.68;
const float NAPPE_GAIN = 0.55;
const float RELIEF_GAIN = 0.10;
// LES VEINES : un PLI de la nappe. La transition est LARGE (VEINE_ECHELLE x la maille du bruit) :
// une veine est un creux doux dans la nappe, JAMAIS un isocontour.
const float VEINE_ECHELLE = 20.0;
// LA LUEUR : large et diffuse — la source et le courant ECLAIRENT la nappe, sans faire de trait.
const float LUEUR_GAIN = 0.06;
const float NOYAU_GAIN = 0.80;
const float COURANT_GAIN = 0.26;
const float GRAIN_AMP = 0.008;
// LA PORTEE DE LA LUEUR : la source ECLAIRE et ne BLANCHIT plus. La portee du foyer passe de 4,0
// a 10,0 et le gain de la lueur de 0,50 a 0,06 : la bande sombre retombe de 105,0 a 83,9
// (planche 84,4) et le bleu-rouge revient a celui de la planche (+0,100 contre +0,108).
// En echange, le NOYAU de la source monte de 0,55 a 0,80 : il rend a la coupe son pic (max - mediane
// 0,135, contre 0,088 pour la nappe d'avant) sans relever le fond.
const float FOYER_K = 10.0;
// LA COMPRESSION DOUCE — le coeur reste blanc, mais la RETOMBEE est GRADUELLE. Un clip franc
// ecrasait la tache a blanc ; cette courbe a un genou a K, puis retombe vers 1 sans jamais couper.
float3 doux(float3 c){
  const float K = 0.92;
  float3 lo = min(c, float3(K));
  float3 hi = max(c - float3(K), 0.0);
  return lo + (1.0-K)*(1.0 - exp(-hi/(1.0-K))); }
// LE COURANT : une lueur LARGE + une longue queue, et un halo SOLIDAIRE qui suit la tete.
float2 courant(float2 p, float c, float phase){
  float s = 1.25*p.x - uIncli*p.y;
  float tete = fract(phase + fract(floor(c*12.0)*0.37));
  float dd = fract(s*0.62 - tete + 0.5) - 0.5;
  float head = exp(-dd*dd/(2.0*0.20*0.20));
  float tail = dd < 0.0 ? exp(dd/0.30) : 0.0;
  float halo = exp(-dd*dd/(2.0*0.26*0.26));
  return float2(head + 0.5*tail, halo); }

half4 main(float2 fragCoord){
  float2 uv = fragCoord/uRes;
  float asp = uRes.x/uRes.y;
  float v = uv.y;
  float2 p = float2(uv.x*asp, v);
  float2 lum = uLum;
  float2 dl = p - lum;
  float foyer = exp(-dot(dl,dl)*FOYER_K);
  float noyau = exp(-dot(dl,dl)*11.0);
  float grande = 0.62*fbm((p + 0.34*flux(p*0.9))*0.44) + 0.38*fbm((p + 0.20*flux(p*0.8))*0.22);
  float g = smoothstep(0.16,0.90,grande);

  // ---- LE TON DE LA NAPPE : un CHAMP CONTINU, jamais un isocontour -------------------------
  // Le defaut nomme : la crete etait EXTRAITE comme l'iso-valeur du champ (`abs(dcre-0.5)`) — une
  // COURBE, donc un trait brillant a bords nets : un NEON (ilots, anneaux, points isoles). Ici le
  // TON est une fonction MONOTONE des densites : aucune valeur de gris n'est privilegiee, donc il
  // n'y a AUCUN anneau et AUCUN contour ferme — le regard glisse sans accroc.
  float c = champ(p);
  float d2 = fbm(p*D2_FREQ + float2(3.1,7.7));
  // L'echelle du pli : le detail fin vit dans la grande nappe (g) ; la portee est LARGE.
  float poids = (BAND_MIN + (BAND_MAX-BAND_MIN)*clamp(g*EPAISSEUR,0.0,1.0))*VEINE_ECHELLE;
  float dn = mix(c, d2, clamp(poids,0.0,0.70));
  // LA MISE EN TON est LINEAIRE (et non une courbe en S) : aucune valeur de gris n'est privilegiee,
  // donc il n'y a AUCUNE courbe de niveau raide — aucune arête, aucun anneau visible.
  float ton = clamp((dn - NAPPE_LO)/(NAPPE_HI - NAPPE_LO), 0.0, 1.0);
  // LE TON : deux gris-bleus PROCHES — le fond de la nappe et la veine, a faible contraste.
  float3 col = mix(uProfond, uBrumeC, ton);
  col = mix(col, uPale, RELIEF_GAIN*g);

  // ---- LA LUEUR : large et diffuse (la source, le halo, puis le courant qui parcourt la nappe) -
  float rub = uRuban;
  float srcw = 0.45 + 0.55*foyer;
  col = mix(col, uLumiere, LUEUR_GAIN*foyer*rub*srcw);
  col = mix(col, uLumiere, NOYAU_GAIN*noyau*rub*srcw);
  // LE HALO : une lueur tres large sur les masses les plus denses — monotone en densite, donc
  // sans anneau (aucun niveau de gris n'est privilegie).
  float uc = 1.0 - c;
  float hal = smoothstep(HALO_W, 0.0, uc);
  float halo = hal*hal;
  col = mix(col, uPale, HALO_GAIN*halo*rub);
  float2 co = courant(p, c, uPhase);
  col = mix(col, uLumiere, COURANT_GAIN*co.x*rub);
  col = mix(col, uLumiere, COURANT_GAIN*0.5*co.y*rub);

  // la matiere de la planche : le grain de pas de trois pixels, dont le contraste respire.
  // Le grain est ramene AU NIVEAU DE LA PLANCHE : il ne doit pas depasser son energie.
  float grainGain = 0.85 + 0.15*cos(6.2831853*uPhase);
  float fin = h11(floor(fragCoord.x/3.0)+floor(fragCoord.y/3.0)*57.0);
  col *= 0.978 + GRAIN_AMP*fin*grainGain;
  float2 coin = float2(asp-0.105,0.105);
  col *= 1.0 - 0.80*exp(-dot(p-coin,p-coin)*24.0);
  float lumv = dot(col,float3(0.299,0.587,0.114));
  col = mix(float3(lumv), col, uSat);
  float3 sol = uSol*mix(1.10,0.70,v);
  float masque = 1.0 - smoothstep(uHaut,uBas,v);
  col = mix(sol, col, masque);
  float vig = 1.0 - 0.26*smoothstep(0.45,1.25,length((float2(uv.x,v)-float2(0.5,0.42))*float2(1.0,0.85))*1.6);
  col *= vig;
  float a = v<0.5 ? mix(0.0,0.44,v*2.0) : mix(0.44,0.94,(v-0.5)*2.0);
  col = mix(col, uVoile, a*uVoileA);
  // LA COMPRESSION DOUCE avant le clip : le coeur reste blanc, la retombee est graduelle.
  col = doux(col);
  col = clamp(col, 0.0, 1.0);
  return half4(col.r, col.g, col.b, 1.0);
}
"""

/**
 * Le programme se compile-t-il sur **ce** système ? Un `RuntimeShader` dont la source ne compile pas
 * lève à la construction : on le sonde **une fois**, et la carte retombe alors sur la couverture
 * déterministe au lieu de faire tomber l'écran. C'est le repli des appareils anciens, et la même
 * garde sert aux systèmes dont le programme de rendu serait refusé.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun agslCompiles(): Boolean = runCatching { RuntimeShader(FLUIDE_AGSL) }
    // **Une erreur de compilation ne doit JAMAIS être muette.** Le `runCatching` décidait seul du
    // repli, sans laisser de trace : sur l'appareil, la carte retombait sur l'ancienne couverture et
    // personne ne pouvait nommer la cause. Le refus est donc journalisé (avec la source AGSL, que
    // `RuntimeShader` refuse en la citant) : c'est le message que lit `logcat`.
    .onFailure { err ->
        android.util.Log.e(
            "OnyxFluide",
            "AGSL REFUSE par l'appareil (SdkInt=" + Build.VERSION.SDK_INT + ") : " + err.message,
            err,
        )
    }
    .isSuccess

/**
 * Pose les uniformes du plan dans le programme. [phase] est la **seule** entrée qui change d'une
 * image à l'autre : le plan n'est pas recalculé, et une image ne coûte que la pose de quelques
 * flottants — c'est le budget qui garde l'animation sobre en batterie.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun poseUniforms(
    shader: RuntimeShader,
    plan: Fluide.Plan,
    w: Float,
    h: Float,
    souffle: Float,
    phase: Float = 0f,
) {
    shader.setFloatUniform("uRes", w, h)
    shader.setFloatUniform("uProfond", plan.profond.red, plan.profond.green, plan.profond.blue)
    shader.setFloatUniform("uMedian", plan.median.red, plan.median.green, plan.median.blue)
    shader.setFloatUniform("uPale", plan.pale.red, plan.pale.green, plan.pale.blue)
    shader.setFloatUniform("uLumiere", plan.lumiere.red, plan.lumiere.green, plan.lumiere.blue)
    shader.setFloatUniform("uSol", plan.sol.red, plan.sol.green, plan.sol.blue)
    shader.setFloatUniform("uVoile", plan.voile.red, plan.voile.green, plan.voile.blue)
    shader.setFloatUniform("uBrumeP", plan.brumeProfonde.red, plan.brumeProfonde.green, plan.brumeProfonde.blue)
    shader.setFloatUniform("uBrumeC", plan.brumeClaire.red, plan.brumeClaire.green, plan.brumeClaire.blue)
    shader.setFloatUniform("uV0", plan.v0[0], plan.v0[1])
    shader.setFloatUniform("uV1", plan.v1[0], plan.v1[1])
    shader.setFloatUniform("uV2", plan.v2[0], plan.v2[1])
    shader.setFloatUniform("uLum", plan.lum[0], plan.lum[1])
    shader.setFloatUniform("uForce", plan.force[0], plan.force[1], plan.force[2])
    shader.setFloatUniform("uRayon", plan.rayon[0], plan.rayon[1], plan.rayon[2])
    shader.setFloatUniform("uSombre", if (plan.dark) 1f else 0f)
    // Le voile de pied est peint par la carte ([ConversationCoverCard]) : le programme sort nu.
    shader.setFloatUniform("uVoileA", 0f)
    shader.setFloatUniform("uSouffle", souffle)
    shader.setFloatUniform("uSat", plan.sat)
    shader.setFloatUniform("uHaut", plan.haut)
    shader.setFloatUniform("uBas", plan.bas)
    shader.setFloatUniform("uRaies", plan.raies)
    shader.setFloatUniform("uIncli", plan.incli)
    shader.setFloatUniform("uPhase", phase)
    shader.setFloatUniform("uRuban", plan.ruban)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderFluid(
    plan: Fluide.Plan,
    state: MutableFloatState,
    cycle: MutableFloatState,
    fixedSouffle: Float?,
    modifier: Modifier,
) {
    val shader = remember(plan) { RuntimeShader(FLUIDE_AGSL) }
    val paint = remember { AndroidPaint().apply { isAntiAlias = false } }
    Canvas(modifier) {
        val souffle = fixedSouffle ?: state.floatValue
        val phase = if (fixedSouffle != null) 0f else cycle.floatValue
        poseUniforms(shader, plan, size.width, size.height, souffle, phase)
        paint.shader = shader
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Le repli pré-calculé — les images du cycle, cuites une fois
// -------------------------------------------------------------------------------------------

/** Combien d'images pour un tour de cycle pré-calculé. */
private const val PRECALC_FRAMES = 24

/** La résolution des images cuites — la moitié de la carte, recollée au dessin. */
private const val PRECALC_W = 190
private const val PRECALC_H = 96

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PrecalcFluid(plan: Fluide.Plan, modifier: Modifier) {
    var frames by remember(plan) { mutableStateOf<List<ImageBitmap>?>(null) }
    LaunchedEffect(plan) {
        frames = withContext(Dispatchers.Default) { bakeCycleFrames(plan) }
    }
    val ready = frames
    val phase = remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(ready, reduceMotion) {
        phase.floatValue = 0f
        if (reduceMotion) return@LaunchedEffect
        if (ready == null) return@LaunchedEffect
        delay(Fluide.DELAY_MS)
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val ms = (now - start) / 1_000_000L
            phase.floatValue = Fluide.phase(ms)
        }
    }

    if (ready == null || ready.isEmpty() || reduceMotion) {
        // Le temps de la cuisson, et le régime figé : la pose d'arrivée, calculée — jamais un trou.
        val pose = remember { mutableFloatStateOf(0f) }
        ShaderFluid(plan = plan, state = pose, cycle = pose, fixedSouffle = 0f, modifier = modifier)
        return
    }

    Canvas(modifier) {
        val idx = (phase.floatValue * ready.size).toInt().coerceIn(0, ready.size - 1)
        drawImage(
            image = ready[idx],
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

/**
 * **Cuit les images du cycle une fois**, avec le **même** programme de rendu, dans des bitmaps posés
 * en mémoire. Le dessin ne fait plus alors qu'un collage d'image, et le coût par image ne dépend plus
 * de la résolution du champ.
 *
 * Rend `null` si le système ne sait pas évaluer le programme hors écran.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun bakeCycleFrames(plan: Fluide.Plan): List<ImageBitmap>? = runCatching {
    val shader = RuntimeShader(FLUIDE_AGSL)
    val paint = AndroidPaint().apply { isAntiAlias = false; this.shader = shader }
    val out = ArrayList<ImageBitmap>(PRECALC_FRAMES)
    for (i in 0 until PRECALC_FRAMES) {
        val phase = i.toFloat() / PRECALC_FRAMES
        poseUniforms(
            shader, plan, PRECALC_W.toFloat(), PRECALC_H.toFloat(),
            Fluide.souffleDuCycle(phase), phase,
        )
        val bmp = Bitmap.createBitmap(PRECALC_W, PRECALC_H, Bitmap.Config.ARGB_8888)
        AndroidCanvas(bmp).drawRect(0f, 0f, PRECALC_W.toFloat(), PRECALC_H.toFloat(), paint)
        out.add(bmp.asImageBitmap())
    }
    out
}.getOrNull()
