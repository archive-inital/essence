package org.spectral.deobfuscator.asm

import com.google.common.collect.HashMultimap
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.ArrayDeque

private val classGroups = hashMapOf<Int, ClassGroupExt>()
private val classChildren = HashMultimap.create<Int, ClassNode>()
private val classImplementers = HashMultimap.create<Int, ClassNode>()

internal val ClassNode.hash: Int get() = System.identityHashCode(this)

internal fun ClassNode.setGroup(group: ClassGroupExt) {
    classGroups[this.hash] = group
}

internal fun ClassNode.init() {
    this.methods.forEach {
        it.setOwner(this)
        it.init()
    }

    this.fields.forEach {
        it.setOwner(this)
        it.init()
    }

    /*
     * Build class children and implementers hierarchy
     */
    this.parent?.addChild(this)
    this.interfaceClasses.forEach { it.addImplementer(this) }
}

internal fun rebuildClasses() {
    classGroups.clear()
    classChildren.clear()
    classImplementers.clear()
}

internal fun ClassNode.addChild(child: ClassNode) {
    classChildren[this.hash].add(child)
}

internal fun ClassNode.addImplementer(implementer: ClassNode) {
    classImplementers[this.hash].add(implementer)
}

val ClassNode.group: ClassGroupExt get() = classGroups[this.hash] ?: throw NullPointerException("Unable to find class group for class: '${this.name}'.")

val ClassNode.parent: ClassNode? get() = this.group[this.superName]

val ClassNode.interfaceClasses: List<ClassNode> get() = this.interfaces.mapNotNull { this.group[it] }

val ClassNode.children: List<ClassNode> get() = classChildren[this.hash].toList()

val ClassNode.implementers: List<ClassNode> get() = classImplementers[this.hash].toList()

fun ClassNode.getMethod(name: String, desc: String): MethodNode? = this.methods.firstOrNull { it.name == name && it.desc == desc }

fun ClassNode.getField(name: String, desc: String): FieldNode? = this.fields.firstOrNull { it.name == name && it.desc == desc }

fun ClassNode.resolveMethod(name: String, desc: String, toInterface: Boolean): MethodNode? {
    if(!toInterface) {
        var ret = this.getMethod(name, desc)
        if(ret != null) return ret

        var cls: ClassNode? = this.parent

        while(cls != null) {
            ret = cls.getMethod(name, desc)
            if(ret != null) return ret

            cls = cls.parent
        }

        return this.resolveInterfaceMethod(name, desc)
    } else {
        var ret = this.getMethod(name, desc)
        if(ret != null) return ret

        if(this.parent != null) {
            ret = this.parent!!.getMethod(name, desc)
            if(ret != null && (ret.access and (ACC_PUBLIC or ACC_STATIC) == ACC_PUBLIC)) return ret
        }

        return this.resolveInterfaceMethod(name, desc)
    }
}

fun ClassNode.resolveField(name: String, desc: String): FieldNode? {
    var ret = this.getField(name, desc)
    if(ret != null) return ret

    if(this.interfaceClasses.isNotEmpty()) {
        val queue = ArrayDeque<ClassNode>()
        queue.addAll(this.interfaceClasses)

        var cls = queue.pollFirst()
        while(cls != null) {
            ret = cls.getField(name, desc)
            if(ret != null) return ret

            cls.interfaceClasses.forEach {
                queue.addFirst(it)
            }

            cls = queue.pollFirst()
        }
    }

    var cls = this.parent
    while(cls != null) {
        ret = cls.getField(name, desc)
        if(ret != null) return ret

        cls = cls.parent
    }

    return null
}

private fun ClassNode.resolveInterfaceMethod(name: String, desc: String): MethodNode? {
    val queue = ArrayDeque<ClassNode>()
    val queued = hashSetOf<ClassNode>()

    var cls: ClassNode? = this.parent

    while(cls != null) {
        cls.interfaceClasses.forEach {
            if(queued.add(it)) queue.add(it)
        }
        cls = cls.parent
    }

    if(queue.isEmpty()) return null

    val matches = hashSetOf<MethodNode>()
    var foundNonAbstract = false

    cls = queue.poll()
    while(cls != null) {
        val ret = cls.getMethod(name, desc)
        if(ret != null && (ret.access and (ACC_PRIVATE or ACC_STATIC) == 0)) {
            matches.add(ret)

            if((ret.access and ACC_ABSTRACT) == 0) {
                foundNonAbstract = true
            }
        }

        cls.interfaceClasses.forEach {
            if(queued.add(it)) queue.add(it)
        }

        cls = queue.poll()
    }

    if(matches.isEmpty()) return null
    if(matches.size == 1) return matches.iterator().next()

    if(foundNonAbstract) {
        val it = matches.iterator()
        while(it.hasNext()) {
            val m = it.next()

            if((m.access and ACC_ABSTRACT) != 0) {
                it.remove()
            }
        }

        if(matches.size == 1) return matches.iterator().next()
    }

    val it = matches.iterator()
    while(it.hasNext()) {
        val m = it.next()
        cmpLoop@ for(m2 in matches) {
            if(m2 == m) continue

            if(m2.owner.interfaceClasses.contains(m.owner)) {
                it.remove()
                break
            }

            queue.addAll(m2.owner.interfaceClasses)

            cls = queue.poll()
            while(cls != null) {
                if(cls.interfaceClasses.contains(m.owner)) {
                    it.remove()
                    queue.clear()
                    break@cmpLoop
                }

                cls = queue.poll()
            }
        }
    }

    return matches.iterator().next()
}