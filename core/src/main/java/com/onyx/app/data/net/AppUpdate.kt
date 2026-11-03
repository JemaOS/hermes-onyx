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
 * Le format des tags est `v1.0.0+<versionCode>` : c'est le **numéro** qui décide de l'ordre
 * (le `versionCode` de l'APK), pas la partie lisible — deux versions peuvent porter le même
 * `1.0.0` avec des numéros différents, et c'est le numéro qui compte pour l'installation par
 *-dessus.
 */
data class AppUpdate(
    /** Le numéro de la version publiée (`versionCode` de l'APK). */
    val versionCode: Int,
    /** Le nom lisible, tel que le tag l'écrit (`1.0.0+1129`). */
    val versionName: String,
    /** L'APK du **téléphone** — pas celui d'Auto, pas celui de Wear. */
    val downloadUrl: String,
    /** La page de la release, pour qui veut lire les notes. */
    val releaseUrl: String,
)

/**
 * L'APK du téléphone d'une release.
 *
 * Les trois paquets portent des noms voisins : `onyx-1129-release.apk`,
 * `onyx-auto-1129-release.apk`, `onyx-wear-1129-release.apk`. Seuls les **deux** suffixes
 * comptent : `auto` et `wear` ne sont pas des mises à jour du téléphone, et proposer le Wear
 * à qui n'a pas de montre serait absurde.
 */
private val PHONE_ASSET = Regex("""^onyx-(\d+)-release\.apk$""")

/** Le numéro dans un tag `v1.0.0+1129` ; `null` si le tag ne le porte pas. */
private val TAG_CODE = Regex("""^v?\d+\.\d+\.\d+\+(\d+)$""")

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
 * Renvoie `null` quand la réponse ne dit rien d'utile : une release sans numéro de version,
 * sans APK de téléphone, ou une réponse illisible. **Mieux vaut ne rien proposer qu'en proposer
 * un mauvais** — un APK d'Auto downloadé par-dessus l'app du téléphone ne s'installerait pas.
 */
fun parseLatestRelease(payload: String): AppUpdate? {
    val release = runCatching { releaseJson.decodeFromString<ReleaseDto>(payload) }.getOrNull() ?: return null
    val code = TAG_CODE.find(release.tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val asset = release.assets.firstOrNull { PHONE_ASSET.matches(it.name) } ?: return null
    if (asset.downloadUrl.isBlank()) return null
    return AppUpdate(
        versionCode = code,
        // Le nom lisible, sans le numéro : le tag est `v1.3.1+1133`, la carte doit dire « 1.3.1 ».
        versionName = release.tag.removePrefix("v").substringBefore('+'),
        downloadUrl = asset.downloadUrl,
        releaseUrl = release.htmlUrl,
    )
}

/**
 * La version publiée est-elle **plus récente** que celle installée ?
 *
 * Le « plus récente » est le **numéro** (`versionCode`), jamais le nom : c'est lui que
 * l'Android compare pour accepter une installation par-dessus l'autre.
 */
fun isUpdateAvailable(installedCode: Int, latestCode: Int): Boolean = latestCode > installedCode
