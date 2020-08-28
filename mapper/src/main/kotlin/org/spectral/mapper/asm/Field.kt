package org.spectral.mapper.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldNode
import java.lang.reflect.Modifier

/**
 * Represents a java field which is apart of a class object.
 *
 * @property group ClassGroup
 * @property owner Class
 * @property node FieldNode
 * @property real Boolean
 * @constructor
 */
class Field private constructor(val group: ClassGroup, val owner: Class, val index: Int, val node: FieldNode, val real: Boolean) : Matchable<Field>() {

    /**
     * Build the asm values
     */
    private fun init() {
        name = node.name
        desc = node.desc
        access = node.access
        value = node.value
        type = Type.getType(desc)
    }

    /**
     * Creates a 'real' field entry.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param node FieldNode
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, index: Int, node: FieldNode) : this(group, owner, index, node, true) {
        init()
    }

    /**
     * Creates a 'fake' or virtual field entry.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param name String
     * @param desc String
     * @param static Boolean
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, index: Int, name: String, desc: String, static: Boolean) : this(group, owner, index, EMPTY_FIELD_NODE, false) {
        this.node.name = name
        this.node.desc = desc
        this.node.access = if(static) this.node.access and Opcodes.ACC_STATIC else this.node.access
        this.match = this
        init()
    }

    override lateinit var name: String

    lateinit var desc: String

    var access: Int = -1

    var value: Any? = null

    lateinit var type: Type

    lateinit var typeClass: Class

    override val isStatic: Boolean get() = Modifier.isStatic(access)

    val isPrivate: Boolean get() = Modifier.isPrivate(access)

    val readRefs = newIdentityHashSet<Method>()

    val writeRefs = newIdentityHashSet<Method>()

    val hierarchyMembers = newIdentityHashSet<Field>()

    val initializer = mutableListOf<AbstractInsnNode>()

    override fun toString(): String {
        return "$owner.$name"
    }

    companion object {
        private val EMPTY_FIELD_NODE = FieldNode(0, "", "", null, null)
    }
}