package com.onyx.app.ui.design

import androidx.compose.runtime.compositionLocalOf

/**
 * **Le choix de couverture de conversation, tel que le propriétaire l'a tranché — et il gagne.**
 *
 * Le propriétaire a arbitré la planche des variantes et il a retenu, pour les conversations :
 *
 * > **B — filaments, graine `3417634283`, teinte bleu.**
 *
 * Ces trois valeurs sont **un choix**, pas une déduction : elles doivent être **écrites** quelque part
 * et **relues** au démarrage. Avant ce fichier, la seule chose enregistrée était un interrupteur
 * marche/arrêt (`SettingsRepository.Keys.FLUIDE_COVER`) ; la **variante**, la **graine** et la
 * **teinte** n'existaient nulle part, et la carte les **réinventait** à chaque dessin :
 * la graine venait du `crc32` de la conversation ([Fluide.graine]), donc la teinte tombait sur
 * l'une des six de la maison — **verte pour ses conversations**, jamais le bleu qu'il avait choisi.
 * C'est exactement la divergence que ce fichier supprime.
 *
 * Deux valeurs de graine ont un sens particulier :
 *
 * - une graine **positive** est le **matériau enregistré** : toutes les conversations portent le
 *   fluide de la planche, au pixel — c'est le choix retenu, et c'est `[GRAINE_PLANCHE]` par défaut ;
 * - `[GRAINE_IDENTITE]` (`0`) rend l'ancienne règle : la graine vient de l'identifiant de la
 *   conversation, donc chaque carte porte la sienne. Ce n'est pas le choix en vigueur ; c'est la
 *   valeur que la bascule « identité » écrit, et c'est ce que le verrou d'identité interroge.
 */
data class ChoixCouverture(
    /** La variante de matière. Une seule est retenue aujourd'hui : `[VARIANTE_FILAMENTS]`. */
    val variante: String = VARIANTE_FILAMENTS,
    /** La graine du tracé — `[GRAINE_PLANCHE]` par défaut, `[GRAINE_IDENTITE]` pour l'identité. */
    val graine: Long = GRAINE_PLANCHE,
    /** La teinte nommée, telle qu'elle est écrite dans les préférences. */
    val teinte: String = TEINTE_BLEU,
) {
    /** Vrai quand la graine doit venir de l'identifiant de la conversation. */
    val estIdentite: Boolean get() = graine == GRAINE_IDENTITE

    /** L'indice de teinte dans [TEINTES], tel que [Fluide] le lit. */
    val teinteIndex: Int get() = TEINTES.indexOf(teinte).coerceAtLeast(0)

    /** La graine réellement posée dans le plan, pour une conversation donnée. */
    fun graineDe(title: String, id: String): Long =
        if (estIdentite) Fluide.graine(title, id) else graine

    /**
     * **Le choix est-il cohérent avec lui-même ?** La teinte enregistrée doit être celle que la
     * graine produit (`(graine & MAX) % 6`) : sans cette garde, un enregistrement à moitié écrit
     * ferait dessiner une teinte que la graine ne donne pas, et le verrou de conformité ne saurait
     * plus laquelle des deux croire.
     */
    val coherent: Boolean get() = teinte == teinteDe(graine) || estIdentite

    /** La graine de `[GRAINE_PLANCHE]`, telle qu'elle apparaît sur la planche de la variante B. */
    val graineTexte: String get() = graine.toString()

    companion object {
        /** La variante retenue : les **filaments** (branche B de la maquette). */
        const val VARIANTE_FILAMENTS = "filaments"

        /** La graine de la planche : « Réunion de chantier / conv-1 » → teinte bleu, 11 raies. */
        const val GRAINE_PLANCHE = 3_417_634_283L

        /** La graine « identité » : la graine est alors celle de la conversation. */
        const val GRAINE_IDENTITE = 0L

        /** La teinte retenue. */
        const val TEINTE_BLEU = "bleu"

        /** Les six teintes de la maison, dans l'ordre de [Fluide.plan]. */
        val TEINTES = listOf("ambre", "jade", "bois", "bleu", "rose", "mousse")

        /** **Le choix du propriétaire**, tel quel : filaments, graine de la planche, teinte bleu. */
        val DEFAUT = ChoixCouverture()

        /** L'ancienne règle : la graine vient de l'identifiant de la conversation. */
        fun identite(): ChoixCouverture = ChoixCouverture(graine = GRAINE_IDENTITE)

        /** La teinte que rend une graine donnée — la règle de la maison, et celle de la planche. */
        fun teinteDe(graine: Long): String =
            TEINTES[((graine.toInt()) and Int.MAX_VALUE) % TEINTES.size]

        /**
         * **Relit un choix enregistré**, champ par champ. Un champ absent garde sa valeur par défaut —
         * un enregistrement partiel ne rend jamais une couverture sans matière.
         */
        fun enregistre(
            variante: String? = null,
            graine: Long? = null,
            teinte: String? = null,
        ): ChoixCouverture = ChoixCouverture(
            variante = variante?.takeIf { it.isNotBlank() } ?: VARIANTE_FILAMENTS,
            graine = graine ?: GRAINE_PLANCHE,
            teinte = teinte?.takeIf { it.isNotBlank() } ?: TEINTE_BLEU,
        )
    }
}

/**
 * **Le choix en cours**, posé au-dessus de tout l'arbre par `OnyxApp` et lu par chaque carte de
 * conversation — le même mécanisme que [LocalFluideEnabled]. Défaut : le choix du propriétaire.
 */
val LocalFluideChoix = compositionLocalOf { ChoixCouverture.DEFAUT }
