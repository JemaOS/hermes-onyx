package com.onyx.app.voice

import com.onyx.app.localization.OnyxLocalization

/**
 * Les phrases d'arrêt que l'app **cite** à l'écran — et la façon de les citer.
 *
 * Elles suivent la **langue de l'app** : l'app française cite « arrête d'écouter » et « c'est tout »,
 * l'app anglaise cite “stop listening” et “that's all”. Les deux paires sont **entendues** dans les
 * deux langues ([BUILT_IN_STOP_PHRASES]) : seule celle qu'on affiche change.
 *
 * L'erreur a été faite deux fois, et dans les deux sens : la paire anglaise s'affichait sous une
 * phrase d'aide française, puis la paire française s'est affichée sous une phrase d'aide anglaise.
 * Le couple affiché et la langue de l'app ne doivent plus pouvoir diverger — d'où **une seule**
 * fonction, qui lit la langue effective ([OnyxLocalization.isFrench]) à chaque appel, et non une
 * liste écrite en dur dans un écran.
 */
object HandsFreeStops {

    /** Les deux phrases que l'app cite quand la personne n'en a réglé aucune. */
    val cited: List<String>
        get() = if (french) {
            listOf("arrête d'écouter", "c'est tout")
        } else {
            listOf("stop listening", "that's all")
        }

    /**
     * La liste, citée comme l'app la cite : « a » ou « b ».
     *
     * Les guillemets suivent la langue — « » en français, “ ” en anglais — et le séparateur vient
     * du dictionnaire (« ou » / " or "). Les phrases que la personne a réglées sont citées de la
     * même façon : elles sont **siennes**, mais la façon de les nommer est celle de l'interface.
     */
    fun quoted(phrases: List<String> = cited): String {
        val separator = OnyxLocalization[" or "]
        return phrases.joinToString(separator) { phrase ->
            if (french) "« $phrase »" else "“$phrase”"
        }
    }

    /** La langue effective de l'interface : le choix de la personne, sinon celui d'Android. */
    private val french: Boolean
        get() = OnyxLocalization.isFrench()
}
