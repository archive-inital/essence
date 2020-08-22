package org.spectral.asm.util

import java.util.*

fun <T> newIdentityHashSet(): Set<T> {
    return Collections.newSetFromMap<T>(IdentityHashMap())
}

fun <T> newIdentityHashSet(entries: Collection<T>): Set<T> {
    return Collections.newSetFromMap<T>(IdentityHashMap()).apply { this.addAll(entries) }
}