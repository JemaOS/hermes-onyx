package com.onyx.app.data.model.serve

import com.onyx.app.data.net.DashboardSkill

/**
 * Les compétences d'une famille, et la recherche qui les filtre.
 *
 * Deux règles, tenues ici pour être éprouvables sans écran :
 *
 * 1. **La recherche** porte sur le nom, la description **et** la famille, sans tenir compte de la
 *    casse — chercher « web » doit ramener une compétence rangée dans « Web ».
 * 2. **Le regroupement** range par famille, en minuscules et détourée ; une compétence sans
 *    famille va dans « other », qui est **toujours en dernier** (une famille sans nom ne mérite pas
 *    la première place, qui doit revenir à un vrai rang alphabétique).
 *
 * La clé d'un groupe est donc celle qu'on affiche en majuscules ; l'ordre des valeurs suit celui
 * du serveur, c'est-à-dire l'ordre où les compétences sont arrivées.
 */
fun skillGroups(skills: List<DashboardSkill>, query: String): Map<String, List<DashboardSkill>> {
    val needle = query.trim()
    return skills
        .filter { skill -> needle.isEmpty() || skill.matches(needle) }
        .groupBy { skill -> skill.category?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: OTHER_GROUP }
        .toSortedMap(compareBy<String> { it == OTHER_GROUP }.thenBy { it })
}

/** Le nombre de compétences que [query] retient, sans construire les groupes. */
fun skillMatchCount(skills: List<DashboardSkill>, query: String): Int {
    val needle = query.trim()
    return if (needle.isEmpty()) skills.size else skills.count { it.matches(needle) }
}

/** Vrai si le nom, la description ou la famille de la compétence porte [needle], sans la casse. */
private fun DashboardSkill.matches(needle: String): Boolean =
    listOfNotNull(name, description, category).any { field -> field.contains(needle, ignoreCase = true) }

/** La famille des compétences qui n'en déclarent pas. */
const val OTHER_GROUP: String = "other"
