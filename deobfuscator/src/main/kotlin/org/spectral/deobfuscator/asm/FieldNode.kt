package org.spectral.deobfuscator.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.lang.reflect.Modifier

private val fieldOwners = hashMapOf<Int, ClassNode>()

internal val FieldNode.hash: Int get() = System.identityHashCode(this)

internal fun FieldNode.setOwner(owner: ClassNode) {
    fieldOwners[this.hash] = owner
}

internal fun FieldNode.init() {

}

internal fun rebuildFields() {
    fieldOwners.clear()
}

val FieldNode.owner: ClassNode get() = fieldOwners[this.hash] ?: throw NullPointerException("Unable to find field owner for field: '${this.name}'.")

val FieldNode.group: ClassGroupExt get() = this.owner.group

val FieldNode.isStatic: Boolean get() = Modifier.isStatic(this.access)