package com.onyx.app.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Le bocal à cookies du Dashboard Hermes, gardé en mémoire pour tout le processus.
 *
 * Le Dashboard ouvre une session avec un cookie ; il doit survivre d'un écran à l'autre — sans
 * quoi chaque page se reconnecterait. La clé est le schéma, l'hôte et le port, comme côté référence, où deux
 * serveurs d'une même machine ne partagent pas leur session.
 */
object ServeCookieJar : CookieJar {

    private val jars = mutableMapOf<String, MutableList<Cookie>>()
    private val lock = Any()

    fun partition(id: String): CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = save(id, url, cookies)
        override fun loadForRequest(url: HttpUrl): List<Cookie> = load(id, url)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = save("default", url, cookies)
    override fun loadForRequest(url: HttpUrl): List<Cookie> = load("default", url)

    private fun save(id: String, url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val jar = jars.getOrPut(id + "|" + key(url)) { mutableListOf() }
            cookies.forEach { cookie ->
                jar.removeAll { it.name == cookie.name }
                if (cookie.expiresAt > System.currentTimeMillis()) jar += cookie
            }
        }
    }

    private fun load(id: String, url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val jar = jars[id + "|" + key(url)] ?: return emptyList()
            val now = System.currentTimeMillis()
            jar.removeAll { it.expiresAt < now }
            return jar.filter { it.matches(url) }
        }
    }

    /** Oublie la session d'un serveur : à appeler quand son adresse ou son mot de passe change. */
    fun forget(url: HttpUrl) {
        synchronized(lock) { jars.keys.removeAll { it.endsWith("|" + key(url)) } }
    }

    fun forgetAll() {
        synchronized(lock) { jars.clear() }
    }

    private fun key(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
}
