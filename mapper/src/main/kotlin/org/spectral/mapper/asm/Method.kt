package org.spectral.mapper.asm

import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
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

    lateinit var arguments: List<Variable>

    lateinit var variables: List<Variable>

    lateinit var signature: Signature.MethodSignature

    override val isStatic: Boolean get() = Modifier.isStatic(access)

    val isPrivate: Boolean get() = Modifier.isPrivate(access)

    val isConstructor: Boolean get() = name == "<init>"

    val isInitializer: Boolean get() = name == "<clinit>"

    val refsIn = newIdentityHashSet<Method>()

    val refsOut = newIdentityHashSet<Method>()

    val fieldReadRefs = newIdentityHashSet<Field>()

    val fieldWriteRefs = newIdentityHashSet<Field>()

    val classRefs = newIdentityHashSet<Class>()

    val hierarchyMembers = newIdentityHashSet<Method>()

    val parents = newIdentityHashSet<Method>()

    val children = newIdentityHashSet<Method>()

    /**
     * Extracts the method arguments from the current method's instruction list.
     *
     * @return List<Variable>
     */
    internal fun extractArguments(): List<Variable> {
        if(!real || type.argumentTypes.isEmpty() || instructions.size() == 0) return emptyList()

        val args = mutableListOf<Variable>()
        val locals = node.localVariables
        val insns = instructions
        val firstInsn = instructions.first()

        var lvIndex = if(isStatic) 0 else 1

        for(i in argTypeClasses.indices) {
            val typeClass = argTypeClasses[i]
            var index = -1
            var startInsn = -1
            var endInsn = -1
            var name: String? = null

            if(locals != null) {
                for(j in locals.indices) {
                    val lv = locals[j]

                    if(lv.index == lvIndex && lv.start == firstInsn) {
                        index = j
                        startInsn = insns.indexOf(lv.start)
                        endInsn = insns.indexOf(lv.end)
                        name = lv.name

                        break
                    }
                }
            }

            val arg = Variable(group, this, true, i, lvIndex, index, typeClass, startInsn, endInsn, 0, name ?: "arg${i + 1}")
            args.add(arg)

            classRefs.add(typeClass)
            typeClass.methodTypeRefs.add(this)

            /*
             * Increase the lvIndex given a slot data size.
             */
            val type = type.argumentTypes[i]
            lvIndex += if(type == Type.DOUBLE_TYPE || type == Type.LONG_TYPE) {
                2
            } else {
                1
            }
        }

        return args
    }

    /**
     * Extracts the local variables of the method from the given
     * method instruction list.
     *
     * @return List<Variable>
     */
    internal fun extractVariables(): List<Variable> {
        if(!real) return emptyList()
        if(node.localVariables == null || node.localVariables.isEmpty()) return emptyList()

        val insns = instructions
        val firstInsn = insns.first()
        val localVariables = mutableListOf<LocalVariableNode>()

        lvLoop@ for(i in node.localVariables.indices) {
            val lv = node.localVariables[i]

            if(lv.start == firstInsn) {
                /*
                 * Check if its actually an argument and not a local variable.
                 */
                if(lv.index == 0 && !isStatic) continue

                for(arg in arguments) {
                    if(arg.asmIndex == i) {
                        continue@lvLoop
                    }
                }
            }

            localVariables.add(lv)
        }

        if(localVariables.isEmpty()) return emptyList()

        /*
         * Sort the local variable list by start BCI instruction indexes.
         */
        localVariables.sortWith(compareBy { insns.indexOf(it.start) })

        val ret = mutableListOf<Variable>()

        for(i in localVariables.indices) {
            val localVar = localVariables[i]

            val startInsn = insns.indexOf(localVar.start)
            val endInsn = insns.indexOf(localVar.end)

            var start: AbstractInsnNode? = localVar.start
            var startOpIndex = 0

            start = start?.previous
            while(start != null) {
                if(start.opcode >= 0) startOpIndex++
                start = start.previous
            }

            ret.add(
                Variable(group, this, false, i, localVar.index, node.localVariables.indexOf(localVar),
            group.getOrCreate(localVar.desc), startInsn, endInsn, startOpIndex, localVar.name)
            )
        }

        return ret
    }

    /**
     * Gets the first resolved hierarchy member from matched
     * elements.
     */
    val matchedHierarchyMember: Method? get() {
        if(hasMatch()) return this

        hierarchyMembers.forEach { m ->
            if(m.hasMatch()) {
                if(!m.owner.real && !this.owner.real) return m
            }
        }

        return null
    }

    override fun toString(): String {
        return "$owner.$name$desc"
    }
}