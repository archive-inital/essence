package org.spectral.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import java.lang.reflect.Modifier

class Field(val group: ClassGroup, val owner: Class, val node: FieldNode) {

    val name = node.name

    val desc = node.desc

    val access = node.access

    val value = node.value

    val type = Type.getType(desc)

    val typeClass = group.getOrCreate(type.className)

    val isStatic: Boolean = Modifier.isStatic(access)

    val isPrivate: Boolean = Modifier.isPrivate(access)

    override fun toString(): String {
        return "$owner.$name"
    }
}