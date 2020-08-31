package org.spectral.remapper.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

private val fieldOwners = hashMapOf<Int, ClassNode>()

internal fun FieldNode.hash(): Int = System.identityHashCode(this)

internal fun FieldNode.init(owner: ClassNode) {
    fieldOwners[this.hash()] = owner
}