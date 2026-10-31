package com.onyx.app.ui.rich

/**
 * Ce qu'on retient d'une page HTML écrite par l'agent avant de la confier au moteur de rendu.
 *
 * La page vient d'une réponse : elle est traitée comme du **texte hostile**. Deux garanties sont
 * demandées au bloc qui la montre — **aucun script de la page ne s'exécute** et **rien ne sort du
 * téléphone depuis le rendu** — et elles se tiennent ici, avant le chargement :
 *
 * 1. tout ce qui porte du code, une autre page ou une adresse de base est **retiré en entier**
 *    (`script`, `iframe`, `frame`, `frameset`, `object`, `embed`, `applet`, `base`, les commentaires) ;
 * 2. tout gestionnaire d'événement (`onclick="…"`, `onerror=…`) est retiré de la balise ;
 * 3. toute adresse active (`javascript:`, `vbscript:` dans `href`, `src`, `action`, `formaction`,
 *    `poster`, `data`, `background`, `xlink:href`) est neutralisée ;
 * 4. un `<meta http-equiv="refresh">` est retiré (il ferait naviguer le bloc ailleurs).
 *
 * Restent les titres, les listes, les tableaux, le code, les liens, les images en ligne et les
 * styles — c'est-à-dire tout ce qu'un widget a à montrer. Le `style` est conservé : c'est lui qui
 * fait la mise en page, et il ne peut exécuter ni code ni requête.
 *
 * Le résultat est **du texte** : cette classe ne compile ni n'évalue rien, et n'a aucune dépendance
 * Android, ce qui la rend éprouvable hors appareil.
 */
object AgentHtml {

    /** Les éléments retirés avec tout leur contenu, parce qu'ils portent du code ou une autre page. */
    private val FORBIDDEN = listOf("script", "iframe", "frame", "frameset", "object", "embed", "applet", "base")

    /** Le plus grand nombre de caractères qu'on accepte de traiter (une page de widget reste courte). */
    const val MAX_CHARS = 512 * 1024

    private val IGNORE_CASE = setOf(RegexOption.IGNORE_CASE)
    private val IGNORE_CASE_DOTALL = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val COMMENTS = Regex("""<!--.*?-->""", IGNORE_CASE_DOTALL)
    private val REFRESH_META = Regex("""<meta\b[^>]*http-equiv\s*=\s*["']?\s*refresh[^>]*>""", IGNORE_CASE)
    private val EVENT_HANDLER = Regex("""\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""", IGNORE_CASE)
    private val ACTIVE_URL = Regex(
        """(\b(?:href|src|action|formaction|poster|data|background|xlink:href)\s*=\s*)(["']?)\s*(?:javascript|vbscript)\s*:""",
        IGNORE_CASE,
    )

    /**
     * Ce qui reste de [html] une fois retiré tout ce qui pourrait s'exécuter ou appeler le réseau.
     *
     * Ne lève jamais : une page tronquée à mi-balise ressort telle quelle, et c'est le moteur de
     * rendu qui décidera de ne rien en montrer (le bloc retombe alors sur le code source).
     */
    fun sanitize(html: String): String {
        if (html.isEmpty()) return html
        var out = if (html.length > MAX_CHARS) html.substring(0, MAX_CHARS) else html
        out = COMMENTS.replace(out, "")
        // Les éléments interdits : d'abord la forme appariée (avec son contenu), puis ce qui
        // resterait — une balise ouvrante seule, une balise auto-fermante, un `</…>` orphelin.
        for (tag in FORBIDDEN) {
            out = Regex("<$tag\\b[^>]*>.*?</$tag\\s*>", IGNORE_CASE_DOTALL).replace(out, "")
            out = Regex("</?$tag\\b[^>]*/?>", IGNORE_CASE).replace(out, "")
        }
        out = REFRESH_META.replace(out, "")
        out = EVENT_HANDLER.replace(out, "")
        out = ACTIVE_URL.replace(out) { match ->
            // L'adresse reste, mais elle ne désigne plus rien de vivant : le lien s'affiche et ne navigue pas.
            match.groupValues[1] + match.groupValues[2] + "blocked:"
        }
        return out
    }

    /**
     * Vrai quand [html] porte **encore** de quoi exécuter du code ou changer de page.
     *
     * C'est le contrôle que les tests opposent au texte brut (qui doit répondre vrai) et au texte
     * assaini (qui doit répondre faux) : la garantie se vérifie sur la chaîne, pas sur une intention.
     */
    fun stillExecutable(html: String): Boolean = EXECUTABLE.containsMatchIn(html)

    private val EXECUTABLE = Regex(
        """<\s*script\b|<\s*iframe\b|<\s*object\b|<\s*embed\b|<\s*base\b|<\s*meta\b[^>]*http-equiv\s*=\s*["']?\s*refresh|""" +
            """\son[a-z]+\s*=|""" +
            """(?:href|src|action|formaction|poster|data|background|xlink:href)\s*=\s*["']?\s*(?:javascript|vbscript)\s*:""",
        IGNORE_CASE,
    )
}
