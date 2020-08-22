package org.spectral.asm

import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.spectral.asm.util.newIdentityHashSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a java class loaded from bytecode.
 */
class Class private constructor(val group: ClassGroup, val node: ClassNode, val real: Boolean) {

    private fun init() {
        name = node.name
        parentName = node.superName
        access = node.access
        interfaceNames = node.interfaces
        type = Type.getObjectType(name)
        node.methods.forEach { methods[it.name + it.desc] = Method(group, this, it) }
        node.fields.forEach { fields[it.name + it.desc] = Field(group, this, it) }
    }

    /**
     * Creates a 'real' known class instance.
     *
     * @param group ClassGroup
     * @param node ClassNode
     * @constructor
     */
    constructor(group: ClassGroup, node: ClassNode) : this(group, node, true) {
        this.init()
    }

    /**
     * Creates a 'fake' unknown or virtual class instance.
     *
     * @param group ClassGroup
     * @param name String
     * @constructor
     */
    constructor(group: ClassGroup, name: String) : this(group, ClassNode(ASM8), false) {
        this.node.name = name
        this.node.superName = "java/lang/Object"
        this.init()
    }

    lateinit var name: String

    lateinit var parentName: String

    var access: Int = 0

    lateinit var interfaceNames: List<String>

    var parent: Class? = null

    val children = newIdentityHashSet<Class>()

    val interfaces = newIdentityHashSet<Class>()

    val implementers = newIdentityHashSet<Class>()

    lateinit var type: Type

    val methods = ConcurrentHashMap<String, Method>()

    val fields = ConcurrentHashMap<String, Field>()

    fun getMethod(name: String, desc: String): Method? {
        if(!methods.containsKey(name+desc)) return null
        return methods[name + desc]!!
    }

    fun getField(name: String, desc: String): Field? {
        if(!fields.containsKey(name+desc)) return null
        return fields[name + desc]!!
    }

    override fun toString(): String {
        return name
    }
}