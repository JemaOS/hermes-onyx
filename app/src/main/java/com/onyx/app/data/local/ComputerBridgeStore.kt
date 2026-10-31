package com.onyx.app.data.local

import android.content.Context

/**
 * Où joindre le pont « Ordinateur », et avec quel jeton.
 *
 * L'adresse n'est pas un secret : elle vit dans les préférences, comme l'adresse du serveur TTS.
 * Le **jeton**, lui, est un mot de passe pour le PC : il passe par [SecretStore], donc par le
 * Keystore, et il n'est jamais réécrit ailleurs. Un jeton vide efface l'entrée.
 */
class ComputerBridgeStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("onyx_computer", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    /**
     * Le jeton gardé **en mémoire** quand le trousseau a refusé de le garder.
     *
     * Un Keystore peut être indisponible ou perdre sa clé (et `SecretStore` efface alors l'entrée
     * devenue illisible, exprès). Perdre le jeton en silence obligerait à le retaper sans savoir
     * pourquoi : il reste donc en mémoire pour la session, et l'écran le dit.
     */
    private var memoryToken: String = ""

    /** Vrai quand le trousseau a refusé le jeton à l'enregistrement. */
    private var refusedAtSave: Boolean = false

    /**
     * Vrai quand le trousseau ne tient pas le jeton — à l'enregistrement, ou plus tard : une clé
     * de Keystore perdue rend l'entrée illisible, et le magasin l'efface alors. Dans les deux cas
     * l'écran le dit, plutôt que de laisser croire à une adresse oubliée.
     */
    val keystoreRefused: Boolean
        get() = refusedAtSave ||
            (prefs.getBoolean(KEY_HAD_TOKEN, false) && secrets.get(KEY_TOKEN).isEmpty() && memoryToken.isEmpty())

    /** L'adresse du pont, telle que saisie (« http://10.0.2.2:8795 »). */
    var address: String
        get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ADDRESS, value.trim()).apply()

    /** Le jeton du pont — dans le Keystore, jamais dans les préférences. */
    var token: String
        get() = secrets.get(KEY_TOKEN).ifEmpty { memoryToken }
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                secrets.remove(KEY_TOKEN)
                memoryToken = ""
                refusedAtSave = false
                prefs.edit().remove(KEY_HAD_TOKEN).apply()
                return
            }
            secrets.put(KEY_TOKEN, trimmed)
            refusedAtSave = secrets.get(KEY_TOKEN) != trimmed
            memoryToken = trimmed
            prefs.edit().putBoolean(KEY_HAD_TOKEN, true).apply()
        }

    /** Vrai quand les deux sont là : il ne manque plus que la connexion. */
    val configured: Boolean get() = address.isNotBlank() && token.isNotEmpty()

    /** Le jeton tel qu'il se montre : jamais en clair, et on sait seulement s'il y en a un. */
    val maskedToken: String get() = if (token.isEmpty()) "" else MASK

    fun clear() {
        secrets.remove(KEY_TOKEN)
        memoryToken = ""
        refusedAtSave = false
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_HAD_TOKEN).apply()
    }

    private companion object {
        const val KEY_ADDRESS = "bridge_address"
        const val KEY_TOKEN = "computer-bridge-token"
        /** Un jeton a été enregistré : s'il ne se relit plus, c'est le trousseau, pas la mémoire. */
        const val KEY_HAD_TOKEN = "bridge_had_token"
        const val MASK = "••••••••"
    }
}
