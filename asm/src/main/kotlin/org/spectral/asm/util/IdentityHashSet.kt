package org.spectral.asm.util

import java.util.*

fun <T> newIdentityHashSet(): MutableSet<T> {
    return Collections.newSetFromMap<T>(IdentityHashMap())
}

fun <T> newIdentityHashSet(entries: Collection<T>): MutableSet<T> {
    return Collections.newSetFromMap<T>(IdentityHashMap()).apply { this.addAll(entries) }
}