package org.spectral.asm

import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.util.newIdentityHashSet
import java.lang.reflect.Modifier

/**
 * Represents a java method from bytecode that belongs within
 * a [Class] object.
 *
 * @property group ClassGroup
 * @property owner Class
 * @property node MethodNode
 * @property real Boolean
 * @constructor
 */
class Method private constructor(val group: ClassGroup, val owner: Class, val node: MethodNode, val real: Boolean) : Matchable<Method>() {

    /**
     * Initializes the ASM fields after the constructor.
     */
    private fun init() {
        name = node.name
        desc = node.desc
        access = node.access
        type = Type.getMethodType(desc)
        returnTypeClass = group.getOrCreate(type.returnType.className)
        argTypeClasses = type.argumentTypes.map { group.getOrCreate(it.className) }
        instructions = node.instructions
    }

    /**
     * Creates a 'real' known method object.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param node MethodNode
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, node: MethodNode) : this(group, owner, node, true) {
        this.init()
    }

    /**
     * Creates a 'fake' unknown or virtual method object.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param name String
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, name: String, desc: String, static: Boolean) : this(group, owner, MethodNode(ASM8), false) {
        this.node.name = name
        this.node.desc = desc
        this.node.access = if(static) this.node.access and ACC_STATIC else this.node.access
        this.match = this
        this.init()
    }

    override lateinit var name: String

    lateinit var desc: String

    var access: Int = 0

    lateinit var type: Type

    lateinit var returnTypeClass: Class

    lateinit var argTypeClasses: List<Class>

    lateinit var instructions: InsnList

    val isStatic: Boolean get() = Modifier.isStatic(access)

    val isPrivate: Boolean get() = Modifier.isPrivate(access)

    val isConstructor: Boolean get() = name == "<init>"

    val isInitializer: Boolean get() = name == "<clinit>"

    val refsIn = newIdentityHashSet<Method>()

    val refsOut = newIdentityHashSet<Method>()

    val fieldReadRefs = newIdentityHashSet<Field>()

    val fieldWriteRefs = newIdentityHashSet<Field>()

    val classRefs = newIdentityHashSet<Class>()

    override fun toString(): String {
        return "$owner.$name$desc"
    }
}