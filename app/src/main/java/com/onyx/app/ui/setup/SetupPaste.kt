package com.onyx.app.ui.setup

/**
 * **Les deux rangées qui collent ne font pas le même travail, et l'écran doit le dire.**
 *
 *  - la **rangée d'adresse** (le champ du haut) attend **une adresse seule** — là où tourne le
 *    tableau de bord : LAN, Tailscale ou HTTPS ;
 *  - la **rangée « Coller un lien de configuration »** attend **un lien complet venu d'un autre
 *    appareil** — un code de configuration (`onyx://…`), pas une adresse.
 *
 * Le défaut observé : coller **une adresse** dans la rangée du lien répondait « Ce n'est pas un lien
 * de configuration Onyx » — vrai, mais muet sur ce qu'il fallait faire ensuite, alors que la rangée
 * du dessus attendait précisément ce qui venait d'être collé. Les fonctions d'ici nomment, pour
 * chaque cas, **ce qui a été collé** et **où cela va** ; elles vivent hors du dessin pour être
 * mesurées par un test.
 */
object SetupPaste {

    /** Ce que la rangée du **lien** accepte, dit sous son titre. */
    const val LINK_ACCEPTS: String =
        "A full setup link from another device — it starts with onyx://. An address alone goes in the field above."

    /** Ce que la rangée de l'**adresse** accepte, dit sous son champ. */
    const val ADDRESS_ACCEPTS: String =
        "One address only — where your dashboard runs. A full setup link goes in the row below."

    /**
     * Le problème d'un collage, en une phrase ; `null` quand le lien a été **accepté**.
     *
     * Quatre cas nommés, dans l'ordre où ils se présentent : le lien a été accepté ; le
     * presse-papiers est vide ; ce qui est collé est **une adresse** — elle va dans le champ du
     * dessus, et on le dit au lieu de la refuser sèchement ; c'est autre chose, ce n'est donc pas un
     * lien de configuration, et le message dit **ce qu'un lien est**.
     */
    fun problemFor(pasted: String, accepted: Boolean): String? = when {
        accepted -> null
        pasted.isBlank() -> "Nothing to paste — the clipboard is empty."
        SetupSuggestions.isAddress(pasted) ->
            "That's an address, not a setup link. Paste it in the Gateway address field above, or paste a full setup link (onyx://…) from a device that is already set up."
        else ->
            "That isn't an Onyx setup link. A setup link starts with onyx:// and comes from Settings › Connection › Set up another device on a phone that is already set up."
    }
}
