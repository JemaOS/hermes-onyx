package com.onyx.app.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * **La mise à jour de l'app** : la dernière version publiée sur GitHub, et le moment où elle
 * devient une proposition à l'écran.
 *
 * Le contrôle est **gratuit et local** : l'API publique de GitHub, sans compte et sans clé.
 * Elle est donc appelée depuis le téléphone lui-même — l'app sait sa propre version, et c'est
 * elle qui doit être comparée.
 *
 * **Une version, un nom.** Le tag se lit `v1.3.3` et les paquets se nomment
 * `onyx-1.3.3-release.apk` : le numéro interne n'apparaît **nulle part** dans ce qui est
 * publié. La comparaison se fait donc sur la version **lisible**, qui est dérivée du numéro
 * interne (`versionCode - versionBase`) — les deux ne peuvent pas diverger.
 *
 * Les noms et les tags **anciens** (`v1.3.1+1133`, `onyx-1133-release.apk`) restent acceptés :
 * une release déjà publiée doit rester lisible, et une release future peut encore porter le
 * numéro — l'app lit les deux formes.
 */
data class AppUpdate(
    /** Le nom lisible de la version publiée (`1.3.3`). */
    val versionName: String,
    /** Le numéro interne, **quand la release le porte** ; `null` pour un tag `v1.3.3`. */
    val versionCode: Int?,
    /** L'APK du **téléphone** — pas celui d'Auto, pas celui de Wear. */
    val downloadUrl: String,
    /** La page de la release, pour qui veut lire les notes. */
    val releaseUrl: String,
)

/**
 * L'APK du téléphone d'une release.
 *
 * Les trois paquets portent des noms voisins : `onyx-1.3.3-release.apk`,
 * `onyx-auto-1.3.3-release.apk`, `onyx-wear-1.3.3-release.apk`. Seuls les **deux** suffixes
 * comptent : `auto` et `wear` ne sont pas des mises à jour du téléphone, et proposer le Wear
 * à qui n'a pas de montre serait absurde.
 *
 * La forme tient **les deux écritures** : `onyx-1.3.3-release.apk` (l'écriture d'aujourd'hui)
 * et `onyx-1133-release.apk` (celle des releases déjà publiées). Un nom d'Auto ou de Wear porte
 * des lettres, donc ne peut pas entrer dans le groupe — impossible de proposer le Wear comme
 * mise à jour du téléphone.
 */
private val PHONE_ASSET = Regex("""^onyx-([\d.]+)-release\.apk$""")

/**
 * Le tag d'une release : `v1.3.3`, ou `v1.3.1+1133` pour les plus anciennes — le numéro après
 * le `+` est alors le `versionCode`, ce que l'app peut encore comparer exactement.
 */
private val RELEASE_TAG = Regex("""^v?(\d+\.\d+\.\d+)(?:\+(\d+))?$""")

/** Ce que GitHub répond pour une release. */
@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tag: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<AssetDto> = emptyList(),
)

/** Un asset d'une release. */
@Serializable
private data class AssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

private val releaseJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Lit la dernière release de GitHub.
 *
 * Renvoie `null` quand la réponse ne dit rien d'utile : une release dont le tag ne dit pas
 * quelle version c'est, sans APK de téléphone, ou une réponse illisible. **Mieux vaut ne rien
 * proposer qu'en proposer un mauvais** — un APK d'Auto downloadé par-dessus l'app du téléphone
 * ne s'installerait pas.
 */
fun parseLatestRelease(payload: String): AppUpdate? {
    val release = runCatching { releaseJson.decodeFromString<ReleaseDto>(payload) }.getOrNull() ?: return null
    val tag = RELEASE_TAG.find(release.tag) ?: return null
    val versionName = tag.groupValues[1]
    val versionCode = tag.groupValues.getOrNull(2)?.toIntOrNull()
    val asset = release.assets.firstOrNull { PHONE_ASSET.matches(it.name) } ?: return null
    if (asset.downloadUrl.isBlank()) return null
    return AppUpdate(
        versionName = versionName,
        versionCode = versionCode,
        downloadUrl = asset.downloadUrl,
        releaseUrl = release.htmlUrl,
    )
}

/**
 * La version publiée est-elle **plus récente** que celle installée ?
 *
 * Le « plus récent » est la **version lisible** (`1.3.4` > `1.3.3`) : c'est elle que la
 * publication annonce, et elle est dérivée du numéro interne, donc les deux ne peuvent pas
 * diverger. Quand la release porte encore son numéro — les anciennes, et celles qui le
 * portent par choix — la comparaison se fait **sur le numéro**, qui est exact.
 */
fun isUpdateAvailable(installedCode: Int, installedName: String, latest: AppUpdate): Boolean {
    val latestCode = latest.versionCode
    if (latestCode != null) return latestCode > installedCode
    val installed = versionTriple(installedName) ?: return false
    val published = versionTriple(latest.versionName) ?: return false
    return isNewer(published, installed)
}

/** Une version lisible décomposée : (majeure, mineure, correctif). */
private fun versionTriple(name: String): Triple<Int, Int, Int>? {
    val parts = name.substringBefore('+').trim().split('.')
    if (parts.size < 3) return null
    val numbers = parts.take(3).map { it.trim().toIntOrNull() ?: return null }
    return Triple(numbers[0], numbers[1], numbers[2])
}

/** La version publiée est-elle plus récente que l'installée ? Composante par composante. */
private fun isNewer(published: Triple<Int, Int, Int>, installed: Triple<Int, Int, Int>): Boolean {
    if (published.first != installed.first) return published.first > installed.first
    if (published.second != installed.second) return published.second > installed.second
    return published.third > installed.third
}
