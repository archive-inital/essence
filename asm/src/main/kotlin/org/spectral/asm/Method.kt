package org.spectral.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier

class Method(val group: ClassGroup, val owner: Class, val node: MethodNode) {

    val name = node.name

    val desc = node.desc

    val access = node.access

    val type = Type.getMethodType(desc)

    val returnType = type.returnType

    val returnTypeClass = group.getOrCreate(returnType.className)

    val argTypes = type.argumentTypes

    val argTypeClasses = argTypes.map { group.getOrCreate(it.className) }

    val isStatic: Boolean = Modifier.isStatic(access)

    val isPrivate: Boolean = Modifier.isPrivate(access)

    val isConstructor: Boolean = name == "<init>"

    val isInitializer: Boolean = name == "<clinit>"

    override fun toString(): String {
        return "$owner.$name$desc"
    }
}