package com.onyx.app.data.link

import kotlinx.serialization.Serializable

/**
 * Une page web telle que sa fiche la montre sous un message: son titre, son site et une petite
 * image.
 *
 * Comme `LinkPreview`. L'image est le JPEG (≤ 240 px de côté) encodé en
 * base64 — c'est exactement ce que fait `JSONEncoder` d'un `Data` , donc le format gardé sur
 * disque se lit pareil des deux côtés.
 */
@Serializable
data class LinkPreview(
    val url: String,
    /** Le titre de la page, nettoyé; `null` quand il n'y en a pas. */
    val title: String? = null,
    /** Le site, sans `www.` — « example.com ». */
    val site: String,
    /** Le JPEG de l'image de la page (ou de son icône), en base64; `null` s'il n'y en a pas. */
    val image: String? = null,
) {
    /** Les octets de l'image, ou `null`; décodés à l'affichage, comme `Data(contentsOf:)`. */
    val imageBytes: ByteArray?
        get() = image?.takeIf { it.isNotEmpty() }?.let {
            runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull()
        }
}
