package com.onyx.app.ui.bots

import com.onyx.app.data.model.serve.BotProfile

/**
 * Le visage d'un profil, résolu en **un seul endroit** pour ce qui le dessine.
 *
 * La liste des bots, la rangée d'une salle, l'en-tête et les grands visages d'une salle, les
 * réponses des bots, les mentions `@` et la pile des membres passent tous par [codeOf] : c'est ce
 * qui garantit qu'un même bot montre **le même visage partout**. Deux chemins de résolution
 * donnaient deux visages pour un seul bot — la liste montrait celui de son profil, la salle un
 * autre, déduit du seul nom —, et la personne voyait une créature dans la liste et une autre en
 * entrant dans la salle.
 *
 * [codeOf] rend le code du profil, ou `null` quand rien ne le fixe. Un `null` n'est pas un tirage :
 * sans code, c'est le **nom** qui décide, par le même calcul à chaque fois — le visage d'un bot
 * sans visage attribué est donc le même à chaque ouverture.
 *
 * Les deux sources, dans cet ordre :
 * 1. ce que le téléphone garde pour ce profil ([BotFaceStore], le choix fait dans le sélecteur de
 *    visage), déjà fondu dans la table par [withSwiftFaces] ;
 * 2. ce que le serveur écrit pour lui (`ui_meta.onyx.face`), également fondu dans la table : elle
 *    attribue les créatures **une fois pour tout le roster**, sans deux fois la même.
 *
 * Un profil que la table ne connaît pas retombe sur le choix local, puis sur `null`.
 *
 * **Un écran ne lit donc jamais `BotFaceStore` pour dessiner** : il lit cette fonction, sur la
 * table du roster telle que [withSwiftFaces] l'a produite.
 */
fun interface BotFaceResolver {

    /** Le code de visage de ce profil ; `null` laisse le nom décider, comme partout ailleurs. */
    fun codeOf(profile: String): String?

    companion object {

        /** Aucun visage résolu : les noms décident seuls. */
        val none: BotFaceResolver = BotFaceResolver { null }

        /**
         * Le résolveur d'un roster : [resolved] est la table des visages par nom de profil (telle
         * que [withSwiftFaces] la rend), [local] le choix gardé par le téléphone pour un nom que la
         * table ne porte pas.
         */
        fun of(
            resolved: Map<String, String>,
            local: (String) -> String? = { null },
        ): BotFaceResolver = BotFaceResolver { profile -> resolved[profile] ?: local(profile) }

        /**
         * Le résolveur des bots tels qu'un écran les a : chacun porte déjà son visage résolu
         * (`BotProfile.faceCode`, la sortie de [withSwiftFaces]), y compris une doublure de
         * démonstration. Un bot sans code n'entre pas dans la table : son nom décidera seul.
         */
        fun of(bots: List<BotProfile>): BotFaceResolver =
            of(bots.mapNotNull { bot -> bot.faceCode?.let { code -> bot.name to code } }.toMap())
    }
}
