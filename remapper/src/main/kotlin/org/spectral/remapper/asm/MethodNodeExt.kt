package org.spectral.remapper.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

private val methodOwners = hashMapOf<Int, ClassNode>()

internal fun MethodNode.hash(): Int = System.identityHashCode(this)

internal fun MethodNode.init(owner: ClassNode) {
    methodOwners[this.hash()] = owner
}

val MethodNode.owner: ClassNode get() = methodOwners[this.hash()] ?: throw IllegalStateException()

val MethodNode.group: ClassGroup get() = this.owner.group