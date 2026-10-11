package com.niiros.tripplanner.core.util

import java.time.Duration
import java.time.Instant

/** Small thread-safe in-memory cache with a fixed time-to-live per entry. */
class TtlCache<K : Any, V : Any>(
    private val ttl: Duration,
    private val clock: () -> Instant,
) {
    private data class Entry<V>(val value: V, val storedAt: Instant)

    private val map = HashMap<K, Entry<V>>()

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (Duration.between(entry.storedAt, clock()) >= ttl) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = Entry(value, clock())
    }

    @Synchronized
    fun clear() = map.clear()
}
