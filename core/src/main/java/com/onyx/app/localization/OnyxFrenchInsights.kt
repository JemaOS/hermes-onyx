package com.onyx.app.localization

/**
 * Les libellés de l'écran « Aperçus » (ce que le profil a consommé : coût, sessions, jetons,
 * cache, jetons par jour, modèles).
 *
 * Les clés sont les chaînes anglaises de l'écran. **Aucun chiffre n'est traduit** : les montants,
 * les jetons et les parts se formatent en code (voir `InsightsFormat`), jamais par une table.
 */
internal val onyxInsightsFrench: Map<String, String> = linkedMapOf(
    // Le titre, la période et l'adresse.
    "Insights" to "Aperçus",
    "7 days" to "7 jours",
    "30 days" to "30 jours",
    "90 days" to "90 jours",

    // Le coût : l'estimation, et ce qui a été facturé quand le fournisseur le dit.
    "Estimated cost" to "Coût estimé",
    "billed" to "facturé",

    // Les trois cartes.
    "Sessions" to "Sessions",
    "Tokens in and out" to "Jetons entrée/sortie",
    "Prompt from cache" to "Part venue du cache",

    // L'histogramme.
    "Tokens by day" to "Jetons par jour",
    "Peak" to "Pic",
    "Tap a day for its numbers." to "Touchez un jour pour ses chiffres.",
    "%s tokens" to "%s jetons",
    "%d sessions" to "%d sessions",
    "1 session" to "1 session",

    // Les modèles et les états.
    "Models" to "Modèles",
    "Nothing used yet" to "Rien d’utilisé pour l’instant",
    "Chats from this period, and what they cost, show up here." to
        "Les conversations de cette période, et ce qu’elles ont coûté, apparaissent ici.",
    "Couldn’t load the insights" to "Impossible de charger les aperçus",
    "The analytics route isn’t available on this Hermes." to
        "La route d’analyse n’est pas disponible sur cet Hermes.",
    "This profile only" to "Ce profil seulement",
    "This profile only, not every profile." to
        "Ce profil seulement, pas tous les profils.",
    "Loading the insights…" to "Chargement des aperçus…",
    "Reload" to "Recharger",
    "No numbers to show." to "Rien à montrer, faute de chiffres.",
)
