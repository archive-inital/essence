package org.spectral.mapper.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a java class loaded from bytecode.
 */
class Class private constructor(val group: ClassGroup, val node: ClassNode, val real: Boolean) : Matchable<Class>() {

    private fun init() {
        name = node.name
        parentName = node.superName
        access = node.access
        interfaceNames = node.interfaces
        type = Type.getObjectType(name)
        node.methods.forEach { methods[it.name + it.desc] = Method(group, this, it) }
        node.fields.forEach { fields[it.name + it.desc] = Field(group, this, it) }
        if(node.signature != null) {
            signature = Signature.ClassSignature.parse(node.signature, group)
        }
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
        this.match = this
        this.init()
    }

    override lateinit var name: String

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

    val methodTypeRefs = newIdentityHashSet<Method>()

    val fieldTypeRefs = newIdentityHashSet<Field>()

    val strings = newIdentityHashSet<String>()

    var signature: Signature.ClassSignature? = null

    override val isStatic = false

    /**
     * Gets a [Method] object given the name and descriptor within
     * the current class.
     *
     * @param name String
     * @param desc String
     * @return Method?
     */
    fun getMethod(name: String, desc: String): Method? {
        if(!methods.containsKey(name+desc)) return null
        return methods[name + desc]!!
    }

    /**
     * Gets a [Field] objects given the name nd descriptor within
     * the current class.
     *
     * @param name String
     * @param desc String
     * @return Field?
     */
    fun getField(name: String, desc: String): Field? {
        if(!fields.containsKey(name+desc)) return null
        return fields[name + desc]!!
    }

    /**
     * Adds a fake virtual method to the method group.
     *
     * @param name String
     * @param desc String
     * @param static Boolean
     * @return Method
     */
    fun addMethod(name: String, desc: String, static: Boolean): Method {
        val m = Method(group, this, name, desc, static)
        group.env?.share(m) ?: methods.put(name+desc, m)
        return m
    }

    /**
     * Resolves a [Method] object from the current class or following
     * the JVM resolution patterns from inheritors.
     *
     * @param name String
     * @param desc String
     * @param toInterface Boolean
     * @return Method?
     */
    fun resolveMethod(name: String, desc: String, toInterface: Boolean): Method? {
        if(!toInterface) {
            var ret = getMethod(name, desc)
            if(ret != null) return ret

            var cls = this.parent
            while(cls != null) {
                ret = cls.getMethod(name, desc)
                if(ret != null) return ret

                cls = cls.parent
            }

            return this.resolveInterfaceMethod(name, desc)
        } else {
            var ret = this.getMethod(name, desc)
            if(ret != null) return ret

            if(parent != null) {
                ret = parent!!.getMethod(name, desc)
                if(ret != null
                    && (ret.access and (ACC_PUBLIC or ACC_STATIC)) == ACC_PUBLIC) {
                    return ret
                }
            }

            return resolveInterfaceMethod(name, desc)
        }
    }

    /**
     * Resolves a [Field] object from the current class or following the JVM
     * resolution patterns from inheritors.
     *
     * @param name String
     * @param desc String
     * @return Field?
     */
    fun resolveField(name: String, desc: String): Field? {
        var ret = getField(name, desc)
        if(ret != null) return ret

        if(interfaces.isNotEmpty()) {
            val queue = ArrayDeque<Class>()
            queue.addAll(interfaces)

            var cls = queue.pollFirst()
            while(cls != null) {
                ret = cls.getField(name, desc)
                if(ret != null) return ret

                cls.interfaces.forEach { i ->
                    queue.addFirst(i)
                }

                cls = queue.pollFirst()
            }
        }

        var cls = parent
        while(cls != null) {
            ret = cls.getField(name, desc)
            if(ret != null) return ret

            cls = cls.parent
        }

        return null
    }

    /**
     * Resolves a method given a name and descriptor following the JVM
     * inheritor specifications.
     *
     * @param name String
     * @param desc String
     * @return Method?
     */
    private fun resolveInterfaceMethod(name: String, desc: String): Method? {
        val queue = ArrayDeque<Class>()
        val queued = newIdentityHashSet<Class>()

        /*
         * Loop through all the super classes and add
         * them to the queue if not already queued. As well as all
         * implemented interfaces.
         */
        var cls = this.parent
        while(cls != null) {
            cls.interfaces.forEach { i ->
                if(queued.add(i)) queue.add(i)
            }
            cls = cls.parent
        }

        if(queue.isEmpty()) return null

        val matches = newIdentityHashSet<Method>()
        var foundNonAbstract = false

        /*
         * Loop through the queue and find any abstract methods
         * first.
         */
        cls = queue.poll()
        while(cls != null) {
            val ret = cls.getMethod(name, desc)
            if (ret != null
                && ret.access and (ACC_PRIVATE or ACC_STATIC) == 0
            ) {
                matches.add(ret)

                /*
                 * Detect if the method is a non-abstract implementation.
                 */
                if (ret.access and ACC_ABSTRACT == 0) {
                    foundNonAbstract = true
                }
            }

            cls.interfaces.forEach { i ->
                if (queued.add(i)) queue.add(i)
            }

            cls = queue.poll()
        }

        if(matches.isEmpty()) return null
        if(matches.size == 1) return matches.iterator().next()

        /*
         * Non-abstract methods take priority for resolution over abstract ones.
         * Remove all abstract methods if any are found.
         */
        if(foundNonAbstract) {
            val it = matches.iterator()
            while(it.hasNext()) {
                val m = it.next()

                if(m.access and ACC_ABSTRACT != 0) {
                    it.remove()
                }
            }

            if(matches.size == 1) return matches.iterator().next()
        }

        /*
         * Remove non-max specific method declarations. (Any that have child method matches)
         */
        val it = matches.iterator()
        while(it.hasNext()) {
            val m = it.next()

            cmpLoop@ for(m2 in matches) {
                if(m2 == m) continue

                if(m2.owner.interfaces.contains(m.owner)) {
                    it.remove()
                    break
                }

                queue.addAll(m2.owner.interfaces)

                cls = queue.poll()
                while(cls != null) {
                    if(cls.interfaces.contains(m.owner)) {
                        it.remove()
                        queue.clear()
                        break@cmpLoop
                    }

                    queue.addAll(cls.interfaces)

                    cls = queue.poll()
                }
            }
        }

        /*
         * Return the closest JVM specific match.
         */
        return matches.iterator().next()
    }

    override fun toString(): String {
        return name
    }
}