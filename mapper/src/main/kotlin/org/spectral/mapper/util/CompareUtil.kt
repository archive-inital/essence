package org.spectral.mapper.util

import org.spectral.asm.*
import org.spectral.asm.util.newIdentityHashSet
import kotlin.math.abs
import kotlin.math.max

/**
 * The base logic for all the mapper checks.
 *
 * Contains utility methods for determining if two
 * given elements are potential match candidates.
 */
object CompareUtil {

    fun isPotentiallyEqual(a: Class, b: Class): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Method, b: Method): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Field, b: Field): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Variable, b: Variable): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(a.isArg != b.isArg) return false
        if(!isPotentiallyEqual(a.owner, b.owner)) return false

        return true
    }

    fun isObfuscatedName(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
    }

    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0

        return (1 - delta / max(a, b)).toDouble()
    }

    fun <T> compareSets(a: Set<T>, b: Set<T>): Double {
        val setA = a
        val setB = mutableSetOf<T>().apply { this.addAll(b) }

        val oldSize = setB.size
        setB.removeAll(setA)

        val matched = oldSize - setB.size
        val total = setA.size - matched + oldSize

        return if(total == 0) 1.0 else (matched / total).toDouble()
    }

    fun compareClassSets(a: Set<Class>, b: Set<Class>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    fun compareMethodSets(a: Set<Method>, b: Set<Method>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    fun compareFieldSets(a: Set<Field>, b: Set<Field>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    private fun <T : Matchable<T>> compareIdentitySets(a: Set<T>, b: Set<T>, predicate: (T, T) -> Boolean): Double {
        if(a.isEmpty() || b.isEmpty()) {
            return if(a.isEmpty() && b.isEmpty()) 1.0 else 0.0
        }

        val setA = newIdentityHashSet(a)
        val setB = newIdentityHashSet(b)

        val total = setA.size + setB.size
        var unmatched = 0

        var it = setA.iterator()
        while(it.hasNext()) {
            val elementA = it.next()

            if(setB.remove(elementA)) {
                it.remove()
            } else if(elementA.hasMatch()) {
                if(!setB.remove(elementA.match)) {
                    unmatched++
                }

                it.remove()
            }
        }

        it = setB.iterator()
        while(it.hasNext()) {
            val elementB = it.next()

            if(!isObfuscatedName(elementB.name)) {
                unmatched++
                it.remove()
            }
        }

        it = setA.iterator()
        while(it.hasNext()) {
            val elementA = it.next()

            var found = false

            for(elementB in setB) {
                if(predicate(elementA, elementB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
                it.remove()
            }
        }

        for(elementB in setB) {
            var found = false

            for(elementA in setA) {
                if(predicate(elementA, elementB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
            }
        }

        return ((total - unmatched) / total).toDouble()
    }
}