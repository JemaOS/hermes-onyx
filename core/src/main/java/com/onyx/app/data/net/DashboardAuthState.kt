package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Process-wide single flight. No credentials appear in map keys, diagnostics or cookies. */
internal class DashboardAuthState private constructor() {
    val cookiePartition: String = UUID.randomUUID().toString()
    @Volatile var generation: Long = 0
        private set
    private var valid = false
    private var retryAtNanos = 0L
    private var failure: Exception? = null

    @Synchronized
    fun authenticate(rejectedGeneration: Long?, login: () -> Unit): Long {
        if (valid && (rejectedGeneration == null || rejectedGeneration != generation)) return generation
        if (System.nanoTime() < retryAtNanos) throw failure ?: TransportError.Http(429, "Dashboard login cooling down")
        valid = false
        retryAtNanos = 0
        try {
            login()
            generation++
            valid = true
            failure = null
            retryAtNanos = 0
            return generation
        } catch (error: Exception) {
            // Share rejection/network failures too: queued clients must not retry in a storm.
            failure = error
            if (retryAtNanos == 0L) cooldown(null)
            throw error
        }
    }

    fun cooldown(retryAfter: String?) {
        val seconds = retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        val dateMillis = runCatching {
            ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
        val delayMillis = when {
            seconds != null -> seconds.coerceAtMost(MAX_DELAY_MS / 1000) * 1000
            dateMillis != null -> (dateMillis - System.currentTimeMillis()).coerceIn(0, MAX_DELAY_MS)
            else -> 60_000L
        }
        retryAtNanos = System.nanoTime() + delayMillis * 1_000_000
    }

    companion object {
        private const val MAX_DELAY_MS = 365L * 24 * 60 * 60 * 1000
        private val states = ConcurrentHashMap<String, DashboardAuthState>()

        fun forConfig(config: ServerConfig): DashboardAuthState {
            val url = config.serveBase.toHttpUrlOrNull()
            val digest = MessageDigest.getInstance("SHA-256")
            // Length-prefix fields so embedded separators cannot alias another identity.
            listOf(url?.scheme.orEmpty(), url?.host.orEmpty(), url?.port.toString(),
                url?.encodedPath.orEmpty(), config.serveUsername, config.servePassword,
                config.servesWithKey.toString(), config.effectiveApiKey).forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            val key = digest.digest().joinToString("") { "%02x".format(it) }
            return states.computeIfAbsent(key) { DashboardAuthState() }
        }
    }
}
