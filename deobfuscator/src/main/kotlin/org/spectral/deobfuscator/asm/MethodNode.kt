package org.spectral.deobfuscator.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier

private val methodOwners = hashMapOf<Int, ClassNode>()

internal val MethodNode.hash: Int get() = System.identityHashCode(this)

internal fun MethodNode.setOwner(owner: ClassNode) {
    methodOwners[this.hash] = owner
}

internal fun MethodNode.init() {

}

internal fun rebuildMethods() {
    methodOwners.clear()
}

val MethodNode.owner: ClassNode get() = methodOwners[this.hash] ?: throw NullPointerException("Unable to find method owner for method: '${this.name}'.")

val MethodNode.group: ClassGroupExt get() = this.owner.group

val MethodNode.isStatic: Boolean get() = Modifier.isStatic(this.access)