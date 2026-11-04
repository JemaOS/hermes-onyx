package com.onyx.app.ui.design

/**
 * **Le modèle en vigueur** — l'UNIQUE source de vérité du « modèle qui va servir ».
 *
 * Le défaut fermé ici, et que personne ne voyait : la **pilule de la barre du haut** et la **puce du
 * compositeur** nommaient un modèle (`ModelName.display(state.modelInUse, state.server.effectiveModel)`)
 * pendant que la **requête d'envoi** en portait un autre (`server.effectiveModel`). Deux résolutions
 * parallèles de la même chose : l'app pouvait donc **afficher un nom et en envoyer un autre** — la
 * personne lisait « claude-sonnet-4.5 » en haut et la requête partait avec l'alias `hermes-agent`.
 *
 * Ici il n'y a plus qu'**une** valeur, rendue par [inForce], et **un seul** nom : l'affichage écrit
 * `state.modelInForce` tel quel, et l'envoi porte `state.modelInForce` tel quel. Aucun appelant ne
 * recalcule un libellé à part — donc **ce qui est écrit est ce qui part**.
 *
 * La résolution, du plus précis au plus général :
 *
 * 1. **la conversation** — le modèle que la session de cette conversation sert déjà
 *    ([com.onyx.app.ui.chat.ChatUiState.modelInUse] ; remis à zéro au changement de conversation, donc
 *    jamais hérité d'un fil à l'autre). C'est l'étage qui fait **suivre un changement de modèle côté
 *    serveur** : quand le serveur décharge un modèle ou redémarre, il le dit dans le flux
 *    (`chunk.model` → `TurnEvent.ModelInUse`), et cette valeur passe devant ;
 * 2. **le bot** — le modèle posé pour le bot dont c'est la conversation (toutes ses conversations le
 *    suivent : c'est ce que la feuille de modèle écrit dans `FaceKeys.model`) ;
 * 3. **le serveur** — le modèle configuré du serveur actif
 *    ([com.onyx.app.data.model.ServerConfig.effectiveModel]) ;
 * 4. **le défaut** — rien : `null`, et l'appelant écrit « Modèle par défaut », l'envoi ne nommant alors
 *    aucun modèle (le serveur choisit le sien).
 *
 * **Un alias de passerelle (`hermes-agent`, `default`) n'est pas un nom de modèle.** Il est donc écarté
 * **dès la résolution** : l'étage passe la main au suivant, et s'il n'y a rien, le résultat est `null`
 * (« Modèle par défaut »). C'est ce qui rend la valeur unique honnête : l'app ne peint jamais l'alias
 * (règle de [ModelName]) **et** ne l'envoie jamais — sinon, pour rester vraie, la pilule devrait
 * l'écrire. Ne rien nommer est exactement ce que « défaut » veut dire : le serveur choisit.
 *
 * La **pilule du haut** ([com.onyx.app.ui.chat.ChatScreen]) et l'**envoi**
 * ([com.onyx.app.ui.chat.ChatViewModel]) lisent tous deux cette même valeur : **ce qui est écrit est ce
 * qui part**. `PillModelSendLockTest` échoue si les deux venaient à diverger ; `ModelInForceTest`
 * tient les quatre étages.
 */
object ModelInForce {

    /**
     * Le modèle en vigueur, du plus précis au plus général : conversation → bot → serveur → `null`.
     *
     * Un étage vide ou blanc, **ou un alias de passerelle**, ne compte pas : il passe la main au
     * suivant. Le résultat est donc un **vrai nom** de modèle, ou `null`.
     */
    fun inForce(conversation: String?, bot: String?, server: String?): String? =
        sequenceOf(conversation, bot, server)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && !ModelName.isAlias(it) }

    /**
     * **Le modèle d'une fiche d'usage** : le modèle EN VIGUEUR de la conversation, jamais le nom
     * **figé** que la passerelle a consigné à l'ouverture de la session.
     *
     * Le défaut fermé ici, et que le propriétaire a vu : l'en-tête nommait le modèle en vigueur
     * (`deepseek/deepseek-v4.1-flash`, celui qu'il venait de choisir) pendant que la fiche
     * d'usage nommait un **autre** modèle (`sage-a1.5`) — parce que la fiche lisait le `model` que
     * `session.usage` avait consigné pour la session, et que ce champ **ne bouge pas** quand on
     * change de modèle. Une seule décision ([inForce]) doit donc alimenter l'en-tête **et** la
     * fiche : le nom consigné par le serveur n'est qu'un **repli**, quand rien n'est en vigueur.
     *
     * Les trois étages sont ceux de [inForce] (conversation → bot → serveur → rien), l'alias de
     * passerelle écarté comme partout.
     */
    fun usageModel(conversation: String?, bot: String?, server: String?, recorded: String?): String? =
        preferInForce(inForce(conversation, bot, server), recorded)

    /**
     * **Le nom en vigueur d'abord, le nom consigné ensuite** — jamais l'inverse.
     *
     * C'est la règle unique que l'en-tête, la fiche d'usage, la fiche par bot et le relevé d'agent
     * lisent dès qu'ils doivent nommer (et tarifer) le modèle d'une conversation : prendre
     * [inForce] quand il y en a un, et ne retomber sur [recorded] (le `model` du registre ou de
     * `session.usage`) que lorsque **rien** n'est en vigueur. Un alias de passerelle ne s'écrit
     * jamais, ni d'un côté ni de l'autre.
     */
    fun preferInForce(inForce: String?, recorded: String?): String? {
        val live = inForce?.trim().orEmpty()
        if (live.isNotEmpty() && !ModelName.isAlias(live)) return live
        val kept = recorded?.trim().orEmpty()
        return kept.takeIf { it.isNotEmpty() && !ModelName.isAlias(it) }
    }
}
