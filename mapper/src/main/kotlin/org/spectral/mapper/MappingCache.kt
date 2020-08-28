package org.spectral.mapper

import org.spectral.mapper.asm.Matchable
import java.util.concurrent.ConcurrentHashMap

/**
 * A global cache used to store mapped opcode indexes used
 * to speed up the mapping of method and field initializer instructions.
 */
object MappingCache {

    private val cache = ConcurrentHashMap<CacheKey<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T, U : Matchable<U>> get(token: CacheToken<T>, a: U, b: U): T {
        return cache[CacheKey(token, a, b)] as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T, U : Matchable<U>> compute(token: CacheToken<T>, a: U, b: U, f: (U, U) -> T): T {
        return cache.computeIfAbsent(CacheKey(token, a, b)) {
            f(it.a as U, it.b as U)!!
        } as T
    }

    fun clear() { cache.clear() }

    /**
     * A token object used to identify a type of
     * cached data.
     *
     * @param T
     */
    class CacheToken<T>

    /**
     * A key where the cached data entry is stored.
     *
     * @param T : Matchable<T>
     * @property token CacheToken<*>
     * @property a T
     * @property b T
     * @constructor
     */
    private data class CacheKey<T : Matchable<T>>(val token: CacheToken<*>, val a: T, val b: T)
}