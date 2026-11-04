package com.onyx.app.data.skill

/**
 * **D'une compétence à une routine.**
 *
 * La démonstration enregistrée devient un skill ; le skill doit pouvoir devenir une **routine**
 * — c'est la troisième surface du lot (« en faire une routine », vers l'écran des horaires). Ce
 * fichier ne dessine rien : il décide seulement *ce qui* est posé dans le brouillon de routine.
 *
 * Ce qui se décide ici, et rien d'autre :
 *
 *  - le **titre** est celui du skill, sans son éventuel préfixe de dossier — une routine qui
 *    s'appelle comme la compétence se retrouve dans la liste des horaires ;
 *  - l'**invite** nomme la compétence et demande de la dérouler. Elle ne recopie **pas** le
 *    Markdown : le skill vit sur le serveur, sous son nom, et le recopier le figerait dans la
 *    routine — une correction du skill ne changerait alors plus jamais la routine ;
 *  - rien d'autre n'est rempli : ni l'horaire, ni la destination. Une routine qui part toute seule
 *    sur un horaire que personne n'a choisi est une surprise, pas une fonction.
 */
object SkillRoutine {

    /** Une proposition de routine, à poser dans le brouillon de l'écran des horaires. */
    data class Seed(val title: String, val prompt: String)

    /**
     * L'invite d'une routine qui applique [skillName].
     *
     * Elle est écrite dans la langue de l'app (le français de ce port) : c'est une consigne donnée
     * à l'agent, au même titre que les autres routines écrites ici.
     */
    fun promptFor(skillName: String): String {
        val name = cleanName(skillName)
        return "Applique la compétence « $name » : déroule les étapes qu'elle décrit, dans l'ordre, " +
            "et dis-moi ce que tu as fait."
    }

    /** La routine proposée pour [skillName], ou `null` quand le nom est vide (rien à proposer). */
    fun from(skillName: String): Seed? {
        val name = cleanName(skillName)
        if (name.isEmpty()) return null
        return Seed(title = name, prompt = promptFor(name))
    }

    /**
     * Le nom d'un skill tel qu'il se lit dans une liste : le dernier segment d'un chemin
     * (`dossiers/facture` → `facture`), sans espaces autour. Le nom **complet** reste celui du
     * skill ; c'est seulement le titre de la routine qui se raccourcit.
     */
    fun cleanName(skillName: String): String =
        skillName.trim().trim('/').substringAfterLast('/').trim()
}
