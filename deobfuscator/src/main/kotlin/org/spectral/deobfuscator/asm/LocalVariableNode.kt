package org.spectral.deobfuscator.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode

private val localVariableOwners = hashMapOf<Int, MethodNode>()

internal val LocalVariableNode.hash: Int get() = System.identityHashCode(this)

internal fun LocalVariableNode.setOwner(owner: MethodNode) {
    localVariableOwners[this.hash] = owner
}

internal fun LocalVariableNode.init() {

}

val LocalVariableNode.owner: MethodNode get() = localVariableOwners[this.hash] ?: throw NullPointerException("Unable to find local variable node owner with name: '${this.name}'.")

val LocalVariableNode.origin: ClassNode get() = this.owner.owner

val LocalVariableNode.group: ClassGroupExt get() = this.owner.group