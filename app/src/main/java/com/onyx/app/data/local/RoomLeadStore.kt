package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.net.asObject
import com.onyx.app.data.net.asString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **Le meneur d'une salle, gardé sur ce téléphone.**
 *
 * C'est le choix que la personne fait, et il **survit à la fermeture** — rouvrir l'app, c'est
 * retrouver la salle et son destinataire par défaut. Il ne dépend d'aucune route du serveur : un
 * Hermes de base n'a pas de meneur de salon, et l'app ne doit pas perdre le choix pour autant.
 *
 * Quand la salle sait le recevoir (`groups.lead`, cf. `GroupRoomViewModel.setLead`), le même choix
 * part **aussi** dans le journal de la salle : les bots le lisent, et la salle interroge le meneur
 * en premier. Ce magasin reste la copie de ce téléphone — la source de vérité locale, jamais la
 * seule.
 *
 * Le meneur n'est pas un chef : c'est le membre à qui la salle écrit **par défaut** quand la
 * personne ne nomme personne. `@nom` adresse toujours directement, quel que soit ce réglage.
 *
 * Tout tient sous une seule clé, un objet JSON `{"<salle>": "<profil>"}` : une salle sans entrée
 * n'a pas de meneur, et « No lead » retire l'entrée au lieu d'y écrire un mot vide.
 */
class RoomLeadStore(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /** Le profil du meneur de cette salle, ou `null` quand elle n'en a pas. */
    fun lead(room: String): String? = all()[room]?.takeIf { it.isNotBlank() }

    /** Choisit le meneur ; `null` (« No lead ») l'enlève. Rendre le même choix ne réécrit rien. */
    fun set(room: String, profile: String?) {
        val clean = profile?.trim()?.takeIf { it.isNotEmpty() }
        val kept = all().toMutableMap()
        if (clean == null) kept.remove(room) else kept[room] = clean
        write(kept)
    }

    /** « Erase everything » : plus aucun meneur gardé ici. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — Ce qui est écrit sur le disque —

    private fun all(): Map<String, String> {
        val raw = text.get(KEY)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val root = runCatching { Json.parseToJsonElement(raw).asObject() }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (room, value) -> value.asString()?.takeIf { it.isNotBlank() }?.let { room to it } }.toMap()
    }

    private fun write(leads: Map<String, String>) {
        if (leads.isEmpty()) {
            text.put(KEY, EMPTY)
            return
        }
        text.put(KEY, JsonObject(leads.mapValues { (_, profile) -> JsonPrimitive(profile) }).toString())
    }

    private companion object {
        /** Le nom des préférences où les meneurs vivent. */
        const val PREFERENCES = "onyx_room_leads"

        /** La clé unique sous laquelle tout est écrit, et sa valeur quand il n'y a rien. */
        const val KEY = "leads"
        const val EMPTY = "{}"
    }
}
