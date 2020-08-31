package org.spectral.remapper.asm

import org.objectweb.asm.tree.ClassNode

private val classGroups = hashMapOf<Int, ClassGroup>()

internal fun ClassNode.hash(): Int = System.identityHashCode(this)

internal fun ClassNode.init(group: ClassGroup) {
    classGroups[this.hash()] = group

    this.methods.forEach { it.init(this) }
    this.fields.forEach { it.init(this) }
}

val ClassNode.group: ClassGroup get() = classGroups[this.hash()] ?: throw IllegalStateException("Class group not found for class node.")

