package com.onyx.app.ui.chat

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization

/**
 * Les limites d'une pièce jointe — **celles d'Onyx**, en un seul endroit.
 *
 * Ce que la référence annonce (« Jusqu'à 10 photos ou fichiers, 25 Mo chacun ») n'est pas ce
 * qu'Onyx applique : ces quatre nombres sont ceux du code, et c'est **eux** que la feuille
 * d'ajout affiche. Ils étaient jusqu'ici dispersés — le nombre et le poids total vivaient dans
 * le compositeur (`OnyxComposer`), le plafond d'un fichier texte dans le ViewModel
 * (`MAX_TEXT_ATTACHMENT`) — si bien que la seule vérification réellement faite était celle du
 * **dépôt** : un fichier choisi par la photothèque, l'appareil photo, les fichiers ou le
 * presse-papiers traversait l'app entier (lu en mémoire, encodé en data URL) avant qu'un refus
 * éventuel n'apparaisse, quand il apparaissait.
 *
 * La règle est donc ici, **pure et mesurable** : [refusal] répond pour une pièce jointe qu'on
 * s'apprête à ajouter, et l'écran refuse **avant** l'envoi, avec le chiffre affiché.
 */
object AttachmentLimits {

    /** Nombre de pièces jointes au plus dans un message. */
    const val MAX_COUNT = 10

    /**
     * Poids total au plus, toutes pièces jointes ensemble.
     *
     * Écrit dans **l'unité que l'app affiche** : son formateur de poids est décimal (`1 Mo` =
     * 1 000 000 o, comme `ByteCountFormatter` sur la référence), donc « 25 Mio » se serait affiché
     * « 26 Mo » et le chiffre à l'écran n'aurait plus été celui du code.
     */
    const val MAX_TOTAL_BYTES = 25L * 1000L * 1000L

    /**
     * Poids au plus d'**une** pièce jointe.
     *
     * Cette limite n'existait pas : rien n'arrêtait un fichier de 300 Mo, qui partait tel quel.
     * Elle vaut le total, pour qu'un seul fichier ne puisse pas remplir le message à lui seul.
     */
    const val MAX_FILE_BYTES = 25L * 1000L * 1000L

    /**
     * Poids au plus d'un fichier **non-image** : son texte est lu et ajouté au message
     * (`[Attached file: …]`), au-delà il n'est pas joint du tout. Le chiffre existait dans le
     * ViewModel mais **sans message** : le fichier disparaissait silencieusement du message.
     */
    const val MAX_TEXT_BYTES = 128L * 1000L

    /** « 2,4 Mo » : un poids lisible, à la française, comme `ByteCountFormatter` sur la référence. */
    fun sizeLabel(bytes: Long): String {
        if (bytes < 1000) return "$bytes o"
        val units = arrayOf("Ko", "Mo", "Go", "To")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000.0
            unit++
        }
        val number = String.format(OnyxLanguage.preferred, if (value < 10) "%.1f" else "%.0f", value)
        return "$number ${units[unit]}"
    }

    /**
     * Le sous-titre de la feuille d'ajout : les quatre plafonds, avec leurs chiffres réels.
     *
     * Le gabarit est une clé anglaise du dictionnaire (le français vit dans `OnyxFrenchMenus`) ;
     * les poids sont insérés **tels quels**, pour que le nombre affiché soit toujours celui du
     * code et jamais une recopie.
     */
    fun subtitle(): String = OnyxLocalization.format(
        "Up to %1\$s attachments · %2\$s in all · %3\$s per file · %4\$s for a text file",
        MAX_COUNT,
        sizeLabel(MAX_TOTAL_BYTES),
        sizeLabel(MAX_FILE_BYTES),
        sizeLabel(MAX_TEXT_BYTES),
    )

    /**
     * Ce qu'on dit d'une pièce jointe qu'on refuse, ou `null` quand elle est acceptée.
     *
     * [bytes] vaut `null` quand le fournisseur ne donne pas la taille : on ne refuse alors que sur
     * le nombre, jamais sur un poids supposé. [attached] est le nombre de pièces jointes déjà au
     * message, [attachedBytes] leur poids cumulé.
     *
     * L'ordre des contrôles est celui qui se comprend : le nombre d'abord (la règle la plus
     * simple), puis le fichier lui-même, puis le message entier.
     */
    fun refusal(
        name: String,
        bytes: Long?,
        isText: Boolean,
        attached: Int,
        attachedBytes: Long,
    ): String? = when {
        attached + 1 > MAX_COUNT -> OnyxLocalization["You can attach up to 10 files."]

        bytes != null && bytes > MAX_FILE_BYTES -> OnyxLocalization.format(
            "%1\$s is %2\$s, over the %3\$s limit: it was not attached.",
            name,
            sizeLabel(bytes),
            sizeLabel(MAX_FILE_BYTES),
        )

        bytes != null && isText && bytes > MAX_TEXT_BYTES -> OnyxLocalization.format(
            "%1\$s is %2\$s: a text file goes into the message up to %3\$s.",
            name,
            sizeLabel(bytes),
            sizeLabel(MAX_TEXT_BYTES),
        )

        bytes != null && attachedBytes + bytes > MAX_TOTAL_BYTES -> OnyxLocalization.format(
            "%1\$s is %2\$s: a message is limited to %3\$s in all.",
            name,
            sizeLabel(bytes),
            sizeLabel(MAX_TOTAL_BYTES),
        )

        else -> null
    }
}
