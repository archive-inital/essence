package org.spectral.mapper.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldNode
import java.lang.reflect.Modifier

class Field(val group: ClassGroup, val owner: Class, val node: FieldNode) : Matchable<Field>() {

    override val name = node.name

    val desc = node.desc

    val access = node.access

    val value = node.value

    val type = Type.getType(desc)

    lateinit var typeClass: Class

    override val isStatic: Boolean = Modifier.isStatic(access)

    val isPrivate: Boolean = Modifier.isPrivate(access)

    val readRefs = newIdentityHashSet<Method>()

    val writeRefs = newIdentityHashSet<Method>()

    val hierarchyMembers = newIdentityHashSet<Field>()

    val initializer = mutableListOf<AbstractInsnNode>()

    override fun toString(): String {
        return "$owner.$name"
    }
}