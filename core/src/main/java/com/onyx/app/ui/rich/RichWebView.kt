package com.onyx.app.ui.rich

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.onyx.app.ui.theme.LocalOnyxTheme
import com.onyx.app.ui.theme.ResolvedTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ce qu'une vue riche sait rendre. */
enum class RichKind(val id: String) {
    MERMAID("mermaid"),
    MATH("math"),
    /** Une page HTML produite par l'agent, rendue telle quelle. */
    HTML("html"),
}

/**
 * L'état d'une page rendue, tel que `AgentPageBlock` s'en sert pour ses replis.
 *
 * connaît les mêmes cas dans: une page qui n'a rien à montrer, une page
 * qui a échoué, et une page qui se rend normalement.
 */
enum class RichPageState {
    /** La page se rend: on la montre, avec ses commandes. */
    READY,

    /** La page a chargé mais ne rend rien: on retombe sur son code source. */
    EMPTY,

    /** La page n'a pas chargé: on le dit, et on laisse la relancer. */
    FAILED,
}

/** Les bornes de hauteur d'une page, comme `PageChrome`. */
private val PageMinHeight = 120.dp
private val PageMaxHeight = 1200.dp

/**
 * Une réponse riche: un diagramme Mermaid, ou une formule.
 *
 * rend les deux hors ligne (avec Mermaid et KaTeX embarqués). Ici, une
 * `WebView` locale charge les mêmes bibliothèques depuis `assets/rich/`, avec le réseau coupé:
 * rien ne sort du téléphone, et rien ne dépend d'une connexion.
 *
 * La hauteur vient du contenu lui-même: la page mesure son corps et la renvoie par le pont JS.
 *
 * Une page produite par l'agent (`RichKind.HTML`) est portée plus loin, comme `AgentPage`:
 * elle est chargée comme un document à elle — ses scripts tournent, ses styles s'appliquent —, elle
 * prend la couleur de surface du thème si elle ne définit pas la sienne (variable CSS
 * `--onyx-surface`), elle choisit sa largeur (mesurée, bornée à l'écran) et elle peut parler à
 * l'app en postant `{"type":"send","text":"…"}` par l'interface JS `Onyx.postMessage`. Sa taille de
 * base vient de `--onyx-zoom` sur le `body`; son titre (`<title>`) et son état de chargement
 * remontent aussi par le pont. Au-delà de 1200 dp, elle est bornée et défile en elle-même.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichWebView(
    kind: RichKind,
    source: String,
    modifier: Modifier = Modifier,
    /** Une réponse à remettre à la page (`window.Onyx.receive`), quand elle en attend une. */
    incoming: String? = null,
    /** Une clé stable pour se souvenir de la hauteur; `null` la mesure à chaque fois. */
    heightKey: String? = null,
    /** Ce qu'une page renvoie à l'app quand elle poste `{"type":"send","text":"…"}`. */
    onSend: ((String) -> Unit)? = null,
    /** Change de valeur pour recharger la page (la commande « Reload » du bloc). */
    reloadToken: Int = 0,
    /** L'état de la page, pour les replis du bloc (vide, échec). */
    onPageState: ((RichPageState) -> Unit)? = null,
    /** La taille de base de la page (paliers « A− » / « A+ »), portée par `--onyx-zoom`. */
    zoom: Float = 1f,
    /** Le titre de la page (`<title>`), remonté à chaque mesure par le pont JS. */
    onPageTitle: ((String) -> Unit)? = null,
    /** Vrai pendant le chargement, faux dès que `onPageFinished` a eu lieu. */
    onLoading: ((Boolean) -> Unit)? = null,
    /** Un tap sur le dessin (le diagramme inline ouvre alors son lecteur plein écran). */
    onTap: (() -> Unit)? = null,
    /** Un appui long sur le dessin (la formule propose alors « Copy LaTeX »). */
    onLongPress: (() -> Unit)? = null,
    /** Lecteur plein écran: remplit la surface, défile et laisse pincer pour zoomer. */
    zoomable: Boolean = false,
) {
    val theme = LocalOnyxTheme.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val isPage = kind == RichKind.HTML

    // La hauteur connue évite que le bloc saute quand il se redessine. L'état est gardé stable
    // (sans clé): la fabrique de l'`AndroidView` ne tourne qu'une fois et c'est lui que le pont JS
    // met à jour, si bien qu'un état recréé à chaque redessin serait écrit dans le vide.
    val rememberedDp = remember(heightKey) { heightKey?.let { WebBlockHeights.height(it) } }
    var heightPx by remember {
        mutableIntStateOf(rememberedDp?.let { dp -> with(density) { dp.dp.roundToPx() } } ?: 0)
    }
    // La largeur naturelle d'une page; nulle tant qu'elle ne l'a pas dit.
    var widthPx by remember { mutableIntStateOf(0) }
    // La page a fini de charger: ses mesures veulent alors dire quelque chose. Avant, le corps est
    // encore vide et « rien à montrer » serait un verdict trop tôt.
    val loaded = remember { mutableStateOf(false) }
    // Le zoom déjà posé dans la page: il ne se réapplique pas à chaque redessin. `NaN` dit « rien
    // encore », ce qui force l'application au premier passage et après chaque rechargement. Un
    // tableau, et non un état: sa valeur n'a rien à redessiner.
    val appliedZoom = remember { floatArrayOf(Float.NaN) }

    // Les deux rappels, toujours à jour: la fabrique les a saisis une fois pour toutes.
    val latestSend = rememberUpdatedState(onSend)
    val latestState = rememberUpdatedState(onPageState)
    // Le titre, l'état de chargement et le zoom changent souvent: la fabrique lit leur valeur
    // courante, jamais celle de sa création.
    val latestTitle = rememberUpdatedState(onPageTitle)
    val latestLoading = rememberUpdatedState(onLoading)
    val latestZoom = rememberUpdatedState(zoom)
    val latestTap = rememberUpdatedState(onTap)
    val latestLongPress = rememberUpdatedState(onLongPress)

    // Le modèle des diagrammes est dans les assets: lu une fois, rempli pour ce bloc. Une page ne
    // passe pas par lui: elle est son propre document.
    val template = remember(isPage) {
        if (isPage) "" else runCatching {
            context.assets.open("rich/render.html").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    val html = remember(
        kind, source, template, zoomable,
        theme.dark, theme.textColor, theme.secondaryText, theme.accent,
        theme.separator, theme.surfaceColor, theme.error,
    ) {
        if (isPage) {
            pageDocument(source, theme)
        } else {
            renderHtml(
                template = template,
                kind = kind,
                source = source,
                dark = theme.dark,
                textColour = theme.textColor.toArgb(),
                errorColour = theme.error.toArgb(),
                zoomable = zoomable,
            )
        }
    }

    // Ce que la `WebView` porte: elle recharge quand le contenu ou la demande de rechargement bouge.
    val reloadKey = "$reloadToken\u0000$html"

    // La hauteur mesurée, avant la borne. Une page plus haute que `PageMaxHeight` n'est pas coupée:
    // elle est bornée, et c'est elle qui défile ensuite.
    val measuredHeight: Dp = with(density) { heightPx.toFloat().toDp() }
    val pageScrolls = isPage && measuredHeight > PageMaxHeight
    val height: Dp = when {
        // Une page est bornée comme: jamais plus haute que 1200, jamais moins que 120.
        isPage -> measuredHeight.coerceIn(PageMinHeight, PageMaxHeight)
        heightPx > 0 -> measuredHeight.coerceAtLeast(24.dp)
        else -> 120.dp
    }

    BoxWithConstraints(modifier = modifier) {
        val maxWidth = maxWidth
        // La page garde la largeur qu'elle s'est choisie, jamais plus large que ce que le bloc peut
        // occuper: la borne à l'écran.
        val naturalWidth = with(density) { widthPx.toFloat().toDp() }
        val blockWidth = when {
            zoomable -> maxWidth
            isPage && widthPx > 0 && naturalWidth < maxWidth -> naturalWidth
            else -> maxWidth
        }
        val blockHeight = if (zoomable) maxHeight else height
        val chrome = if (zoomable) {
            Modifier
        } else {
            Modifier.background(theme.surfaceColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(vertical = 4.dp)
        }

        AndroidView(
            modifier = Modifier
                .width(blockWidth)
                .height(blockHeight)
                .then(chrome),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = true
                    // Le rendu est hors ligne: aucune requête ne doit partir.
                    settings.blockNetworkLoads = true
                    // Le contenu plus large que le bloc (une formule longue, un diagramme) est
                    // ramené à la largeur du bloc plutôt que coupé.
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = false
                    settings.setSupportZoom(zoomable)
                    settings.builtInZoomControls = zoomable
                    settings.displayZoomControls = false
                    isVerticalScrollBarEnabled = zoomable
                    isHorizontalScrollBarEnabled = zoomable
                    setOnLongClickListener {
                        val action = latestLongPress.value
                        if (action == null) false else {
                            action()
                            true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            // La page a fini: à partir d'ici, ses mesures sont fiables, et
                            // l'indicateur de chargement n'a plus lieu d'être.
                            loaded.value = true
                            latestLoading.value?.invoke(false)
                            // Un dessin plus large que le bloc commence à gauche, comme 
                            // (un glissement l'emmène ensuite à droite).
                            if (!zoomable) view.scrollTo(0, 0)
                            // Le corps existe désormais: le zoom choisi s'y pose, et la mesure
                            // se refait une fois la mise en page stabilisée.
                            val chosen = latestZoom.value
                            appliedZoom[0] = chosen
                            view.evaluateJavascript("window.__onyxZoom && window.__onyxZoom($chosen)", null)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            // Un échec du document lui-même, pas d'une ressource secondaire.
                            if (request.isForMainFrame) {
                                latestLoading.value?.invoke(false)
                                latestState.value?.invoke(RichPageState.FAILED)
                            }
                        }
                    }
                    // Le pont des pages: une page poste ici ce qu'elle veut dire à l'app.
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun postMessage(json: String) {
                                val body = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
                                    ?: return
                                // Un champ simple, sans jamais faire tomber le pont: une page qui
                                // envoie autre chose qu'un objet plat n'a rien à dire à l'app.
                                fun field(name: String): String? =
                                    runCatching { body[name]?.jsonPrimitive?.contentOrNull }.getOrNull()
                                when (field("type")) {
                                    "send" -> {
                                        val text = field("text")?.trim().orEmpty()
                                        if (text.isNotEmpty()) post { latestSend.value?.invoke(text) }
                                    }

                                    "size" -> {
                                        val measured = field("height")?.toFloatOrNull()?.toInt() ?: 0
                                        val wide = field("width")?.toFloatOrNull()?.toInt() ?: 0
                                        val empty = field("empty")?.toBoolean() ?: false
                                        // Le titre vient de `<title>`: la barre du bloc le montre.
                                        val pageTitle = field("title").orEmpty()
                                        post {
                                            if (measured > 0) {
                                                heightPx = measured
                                                heightKey?.let { key ->
                                                    WebBlockHeights.remember(
                                                        key,
                                                        with(density) { measured.toDp().value.toInt() },
                                                    )
                                                }
                                            }
                                            if (wide > 0) widthPx = wide
                                            latestTitle.value?.invoke(pageTitle)
                                            if (loaded.value) {
                                                latestState.value?.invoke(
                                                    if (empty) RichPageState.EMPTY else RichPageState.READY,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        "Onyx",
                    )
                    // Le pont historique des diagrammes et des formules: le modèle `render.html`
                    // l'appelle pour sa hauteur.
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun report(measured: Int) {
                                post {
                                    heightPx = measured
                                    heightKey?.let { key ->
                                        WebBlockHeights.remember(
                                            key,
                                            with(density) { measured.toDp().value.toInt() },
                                        )
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun tap() {
                                post { latestTap.value?.invoke() }
                            }
                        },
                        "Rich",
                    )
                    tag = reloadKey
                    // L'indicateur du bloc suit le chargement réel de la page.
                    latestLoading.value?.invoke(true)
                    loadDataWithBaseURL(
                        "file:///android_asset/rich/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            update = { view ->
            if (incoming != null && isPage) {
                view.evaluateJavascript("window.Onyx&&window.Onyx.receive&&window.Onyx.receive(${quoteJs(incoming)})", null)
            }
                // Une nouvelle source, une demande de rechargement, ou un autre thème: on recharge.
                if (view.tag != reloadKey) {
                    view.tag = reloadKey
                    // La page repart de zéro: sa largeur est à remesurer, et ses mesures sont
                    // muettes jusqu'à ce qu'elle ait fini de charger.
                    loaded.value = false
                    widthPx = 0
                    appliedZoom[0] = Float.NaN
                    latestLoading.value?.invoke(true)
                    view.loadDataWithBaseURL("file:///android_asset/rich/", html, "text/html", "utf-8", null)
                    view.scrollTo(0, 0)
                }
                // Une page plus haute que la borne défile dans la WebView, au lieu d'être coupée.
                view.isVerticalScrollBarEnabled = zoomable || pageScrolls
                view.isHorizontalScrollBarEnabled = zoomable
                view.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                // Le défilement interne passe d'abord par la WebView; le chat reprend la main
                // quand elle est au bout. Un diagramme plus large que le bloc se fait glisser.
                view.isNestedScrollingEnabled = true
                // Le zoom ne recharge pas la page: la variable change, et la mesure se refait.
                if (isPage && appliedZoom[0] != zoom) {
                    appliedZoom[0] = zoom
                    view.evaluateJavascript("window.__onyxZoom && window.__onyxZoom($zoom)", null)
                }
            },
        )
    }
}

/** La page à charger: le modèle de `assets/rich/render.html`, rempli pour ce bloc. */
private fun renderHtml(
    template: String,
    kind: RichKind,
    source: String,
    dark: Boolean,
    textColour: Int,
    errorColour: Int,
    zoomable: Boolean,
): String {
    if (template.isBlank()) return ""
    val viewport = if (zoomable) {
        "width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=6, user-scalable=yes"
    } else {
        // Un dessin ou une formule plus large que le bloc est ramené à sa largeur par le mode
        // « aperçu » de la WebView (`loadWithOverviewMode`): rien ne doit être coupé, comme sur
        // où la formule de coût tient sur une ligne.
        "width=device-width, initial-scale=1.0"
    }
    val outLayout = if (zoomable) {
        "min-height:100vh;display:flex;align-items:center;justify-content:center;" +
            "padding:16px 12px;box-sizing:border-box;"
    } else {
        ""
    }
    return template
        .replace("__KIND__", JsonPrimitive(kind.id).toString())
        .replace("__SOURCE__", JsonPrimitive(source).toString())
        .replace("__MERMAID_THEME__", JsonPrimitive(if (dark) "dark" else "default").toString())
        .replace("__TEXT_COLOUR__", cssColour(textColour))
        .replace("__ERROR_COLOUR__", cssColour(errorColour))
        .replace("__VIEWPORT__", viewport)
        .replace("__ZOOMABLE__", if (zoomable) "true" else "false")
        .replace("__BODY_OVERFLOW__", if (zoomable) "visible" else "hidden")
        .replace("__OUT_LAYOUT__", outLayout)
}

// MARK: - La page que l'agent a écrite

/** Le doctype de la page, s'il en a un. */
private val DoctypeTag = Regex("""^\s*<!doctype[^>]*>""", RegexOption.IGNORE_CASE)

/** Le meta viewport de la page, s'il en a un. */
private val ViewportTag = Regex("""<meta[^>]+name\s*=\s*["']?viewport""", RegexOption.IGNORE_CASE)

/**
 * La page que l'agent a écrite, habillée par l'app.
 *
 * Comme `PageChrome.framed`: le prelude se place après le doctype, si bien que la page
 * garde le mode standards qu'elle a demandé, et une page qui ne dit pas sa largeur est dessinée pour
 * celle du téléphone. Le prelude porte les couleurs de l'app en variables CSS — dont
 * `--onyx-surface`, que la page prend pour fond si elle ne définit pas le sien: ses propres styles
 * viennent après, donc gagnent.
 */
private fun pageDocument(source: String, theme: ResolvedTheme): String {
    val vars = listOf(
        "--onyx-surface" to cssColour(theme.surfaceColor.toArgb()),
        "--foreground" to cssColour(theme.textColor.toArgb()),
        "--muted-foreground" to cssColour(theme.secondaryText.toArgb()),
        "--accent" to cssColour(theme.accent.toArgb()),
        "--border" to cssColour(theme.separator.toArgb()),
        "--card" to cssColour(theme.surfaceColor.toArgb()),
    )
    val tokens = vars.joinToString(";") { "${it.first}:${it.second}" }
    val prelude = "<style>:root{color-scheme:${if (theme.dark) "dark" else "light"};$tokens}" +
        "html,body{margin:0;padding:0;background:var(--onyx-surface);color:var(--foreground,inherit);" +
        "-webkit-text-size-adjust:100%}" +
        // La taille de base choisie par « A− » / « A+ »: la variable est posée sur le body (par le
        // script de zoom), et le body s'y conforme. La page peut la lire pour s'accorder.
        "body{--onyx-zoom:1;zoom:var(--onyx-zoom)}</style>" +
        // Une page qui attend une réponse de l'agent la reçoit ici: elle écoute
        // `onyx-reply`, ou pose son propre `window.Onyx.receive` avant.
        "<script>window.Onyx=window.Onyx||{};window.Onyx.receive=window.Onyx.receive||function(t){" +
        "window.dispatchEvent(new CustomEvent('onyx-reply',{detail:String(t||'')}))};</script>" +
        PAGE_BRIDGE
    // La mesure vient à la fin: elle lit la page une fois que ses propres scripts ont tourné.
    return framed(source, prelude) + PAGE_MEASURE
}

/** Le prelude après le doctype, avec un viewport s'il en manque un (comme `PageChrome.framed`). */
private fun framed(doc: String, head: String): String {
    var full = head
    if (!ViewportTag.containsMatchIn(doc)) {
        full = """<meta name="viewport" content="width=device-width, initial-scale=1">""" + full
    }
    val doctype = DoctypeTag.find(doc)
    return if (doctype != null) {
        val after = doctype.range.last + 1
        doc.substring(0, after) + full + doc.substring(after)
    } else {
        full + doc
    }
}

/**
 * Ce que la page peut appeler dans son propre monde (`PageChrome.bridge` ).
 *
 * `onyx.send('…')` poste `{"type":"send","text":"…"}` par l'interface `Onyx.postMessage`; tout
 * élément qui peut être touché et qui porte `data-onyx-send` (ou `data-hermes-send`) envoie aussi.
 */
private const val PAGE_BRIDGE = """
(function(){
  function post(obj){ try { Onyx.postMessage(JSON.stringify(obj)); } catch (e) {} }
  function send(p){
    if (typeof p !== 'string' || !p.trim()) return;
    post({ type: 'send', text: p });
  }
  window.onyx = { send: send };
  window.hermes = { send: send };
  addEventListener('click', function(e){
    var el = e.target && e.target.closest ? e.target.closest('[data-onyx-send],[data-hermes-send]') : null;
    if (el) send(el.getAttribute('data-onyx-send') || el.getAttribute('data-hermes-send') || '');
  }, true);
})();
"""

/**
 * La largeur et la hauteur du corps de la page (`PageChrome.measure` ), renvoyées par la
 * même interface JS. La hauteur est celle du contenu, pas de l'écran qu'il remplit; la largeur est
 * l'étendue de ce que le corps tient. `empty` dit que rien n'a été rendu.
 */
private const val PAGE_MEASURE = """
(function(){
  var lastH = 0, lastW = 0, lastEmpty = null, lastTitle = null;
  function post(){
    var b = document.body;
    if (!b) return;
    var L = Infinity, R = 0, B = 0, kids = b.children, empty = !((b.innerText || '').trim());
    for (var i = 0; i < kids.length; i++){
      var r = kids[i].getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      empty = false;
      if (r.left < L) L = r.left;
      if (r.right > R) R = r.right;
      if (r.bottom + window.scrollY > B) B = r.bottom + window.scrollY;
    }
    var s = getComputedStyle(b);
    var h = Math.ceil(Math.max(b.scrollHeight, B + parseFloat(s.paddingBottom || 0) + parseFloat(s.marginBottom || 0)));
    var w = R > L ? Math.ceil(R - L) : 0;
    var t = (document.title || '').trim();
    if (Math.abs(h - lastH) > 1 || Math.abs(w - lastW) > 1 || empty !== lastEmpty || t !== lastTitle){
      lastH = h; lastW = w; lastEmpty = empty; lastTitle = t;
      try { Onyx.postMessage(JSON.stringify({ type: 'size', height: h, width: w, empty: empty, title: t })); } catch (e) {}
    }
  }
  // La mesure forcée: elle repart de zéro, pour que le nouveau résultat soit toujours renvoyé.
  window.__onyxMeasure = function(){ lastH = 0; lastW = 0; lastEmpty = null; lastTitle = null; post(); };
  // La taille de base: la variable est posée sur le body, que le prelude redimensionne. La mesure
  // attend deux images, le temps que la nouvelle mise en page soit en place.
  window.__onyxZoom = function(z){
    try {
      if (!document.body) return;
      document.body.style.setProperty('--onyx-zoom', String(z));
      requestAnimationFrame(function(){
        requestAnimationFrame(function(){ window.__onyxMeasure(); });
      });
    } catch (e) {}
  };
  if (typeof ResizeObserver === 'function'){
    var ro = new ResizeObserver(post);
    ro.observe(document.documentElement);
    if (document.body) ro.observe(document.body);
  }
  addEventListener('load', post);
  post();
})();
"""

private fun cssColour(argb: Int): String {
    val hex = String.format("#%06X", 0xFFFFFF and argb)
    val alpha = ((argb ushr 24) and 0xFF) / 255.0
    return if (alpha >= 0.99) hex else "rgba(${(argb ushr 16) and 0xFF}, ${(argb ushr 8) and 0xFF}, ${argb and 0xFF}, $alpha)"
}

/** Une chaîne Kotlin en littéral JavaScript sûr. */
private fun quoteJs(text: String): String = buildString {
    append('"')
    text.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> if (char.code < 32) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}
