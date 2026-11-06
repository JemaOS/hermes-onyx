package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig

/**
 * **La clé d'un canal du Dashboard** : ce à quoi un socket ouvert est réellement lié.
 *
 * Un `ServeRpc` fige sa configuration à sa construction. Le profil part dans les méthodes qui
 * l'acceptent (`session.create`, `complete.slash`, `profiles.configure`…) et l'adresse vient de
 * [ServerConfig.serveBase] : deux situations exigent donc deux canaux, et une seule les tolère.
 *
 *  · **l'adresse ou les identifiants du Dashboard changent** → autre serveur, autre session ;
 *  · **le profil change** → le canal d'un bot ne peut pas servir le tour du bot suivant (c'est le
 *    défaut vu par le propriétaire : « coder » annoncé, `default` servi).
 *
 * Ce qui **ne** compte pas, volontairement : le modèle, le provider, la langue, la température.
 * Choisir un modèle dans une conversation simple ne touche pas au socket ; jeter le canal pour
 * cela repayait un login et un ticket — un aller-retour de handshake pour un réglage qui n'a rien
 * à voir avec le transport.
 *
 * Les secrets n'entrent dans la clé que **hachés** : elle sert de clé de dictionnaire et peut
 * apparaître dans un journal de diagnostic.
 */
fun serveChannelKey(server: ServerConfig): String = listOf(
    server.transport.name,
    server.serveBase,
    server.serveUsername.trim(),
    server.servePassword.hashCode().toString(),
    server.effectiveProfile.orEmpty(),
    server.effectiveApiKey.hashCode().toString(),
).joinToString("|")