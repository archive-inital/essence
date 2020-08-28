package org.spectral.mapper.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * Responsible for extracting features from classes.
 *
 * @property group ClassGroup
 * @constructor
 */
class FeatureExtractor(private val group: ClassGroup) {

    /**
     * Processes each class and extracts the features.
     */
    fun process() {
        group.getOrCreate("java/lang/Object")
        /*
         * Processing pass A.
         */
        group.forEach { c ->
            this.processA(c)
        }

        /*
         * Processing pass B.
         */
        group.forEach { c ->
            this.processB(c)
        }

        /*
         * Processing pass C.
         */
        group.forEach { c ->
            this.processC(c)
        }

        /*
         * Processing pass D.
         */
        group.forEach { c ->
            this.processD(c)
        }
    }

    /**
     * Processing pass #1
     *
     * @param cls Class
     */
    private fun processA(cls: Class) {
        if(!cls.real) return

        /*
         * Build the hierarchy graph of the class.
         */
        if(cls.parent == null) {
            cls.parent = group.getOrCreate(cls.parentName)
            cls.parent!!.children.add(cls)
        }

        cls.interfaceNames.forEach { iname ->
            val icls = group.getOrCreate(iname)
            if(cls.interfaces.add(icls)) icls.implementers.add(cls)
        }

        cls.methods.values.forEach { m ->
            m.returnTypeClass = m.group.getOrCreate(m.type.returnType.className)
            m.argTypeClasses = m.type.argumentTypes.map { m.group.getOrCreate(it.className) }
            m.arguments = m.extractArguments()
            m.variables = m.extractVariables()
        }

        cls.fields.values.forEach { f ->
            f.typeClass = f.group.getOrCreate(f.type.className)
        }
    }

    /**
     * Processing pass #2
     * @param cls Class
     */
    private fun processB(cls: Class) {
        cls.methods.values.forEach { m ->
            this.processMethodInsns(m)
        }
    }

    /**
     * Processes and extracts information from a method instruction.
     *
     * @param method Method
     */
    private fun processMethodInsns(method: Method) {
        if(!method.real) return

        val it = method.instructions.iterator()

        while(it.hasNext()) {
            val insn = it.next()

            when(insn) {

                /*
                 * When instruction is a method invocation.
                 */
                is MethodInsnNode -> processMethodInvocation(method, insn.owner, insn.name, insn.desc, insn.itf, (insn.opcode == INVOKESTATIC))

                /*
                 * When instruction is a field invocation.
                 */
                is FieldInsnNode -> {
                    val owner = group.getOrCreate(insn.owner)

                    /*
                     * In the future, we should add virtual fields.
                     * This should increase the matching rate for member elements.
                     */
                    val dst = owner.resolveField(insn.name, insn.desc) ?: return

                    /*
                     * Determine if the field instructions is a read or write.
                     */
                    if(insn.opcode == GETSTATIC || insn.opcode == GETFIELD) {
                        dst.readRefs.add(method)
                        method.fieldReadRefs.add(dst)
                    } else {
                        dst.writeRefs.add(method)
                        method.fieldWriteRefs.add(dst)
                    }

                    dst.owner.methodTypeRefs.add(method)
                    method.classRefs.add(dst.owner)
                }

                /*
                 * When instruction is a type declaration
                 */
                is TypeInsnNode -> {
                    val dst = group.getOrCreate(insn.desc)

                    dst.methodTypeRefs.add(method)
                    method.classRefs.add(dst)
                }

                /*
                 * When instruction is a invoke dynamic invocation.
                 */
                is InvokeDynamicInsnNode -> {
                    val handle = insn.targetHandle ?: return

                    when(handle.tag) {
                        H_INVOKEVIRTUAL, H_INVOKESTATIC, H_INVOKESPECIAL, H_NEWINVOKESPECIAL, H_INVOKEINTERFACE -> {
                            processMethodInvocation(method, handle.owner, handle.name, handle.desc, handle.isInterface, (handle.tag == H_INVOKESTATIC))
                            return
                        }
                    }
                }
            }
        }
    }

    /**
     * Processes a method invocation instruction
     * and updates the backing field models.
     *
     * @param method Method
     * @param ownerName String
     * @param name String
     * @param desc String
     * @param toInterface Boolean
     */
    private fun processMethodInvocation(method: Method, ownerName: String, name: String, desc: String, toInterface: Boolean, isStatic: Boolean) {
        val owner = group.getOrCreate(ownerName)
        val dst = owner.resolveMethod(name, desc, toInterface) ?: owner.addMethod(name, desc, isStatic)

        dst.refsIn.add(method)
        method.refsOut.add(dst)
        dst.owner.methodTypeRefs.add(method)
        method.classRefs.add(dst.owner)
    }

    /**
     * Processing pass C.
     *
     * @param cls Class
     */
    private fun processC(cls: Class) {
        if(cls.children.isNotEmpty() || cls.implementers.isNotEmpty()) return

        val methods = ConcurrentHashMap<String, Method>()
        val toCheck = ArrayDeque<Class>()
        toCheck.add(cls)

        var cur = toCheck.poll()
        while(cur != null) {
            for(method in cur.methods.values) {
                val prev = methods[method.name+method.desc]
                if(method.isHierarchyBarrier()) {
                    if(method.hierarchyMembers.isEmpty()) {
                        method.hierarchyMembers.add(method)
                    }
                } else if(prev != null) {
                    if(method.hierarchyMembers.isEmpty()) {
                        method.hierarchyMembers.clear()
                        method.hierarchyMembers.addAll(prev.hierarchyMembers)
                        method.hierarchyMembers.add(method)
                    } else if(method.hierarchyMembers != prev.hierarchyMembers) {
                        method.hierarchyMembers.addAll(prev.hierarchyMembers)

                        prev.hierarchyMembers.stream().collect(Collectors.toList()).forEach { m ->
                            m.hierarchyMembers.clear()
                            m.hierarchyMembers.addAll(method.hierarchyMembers)
                        }
                    }
                } else {
                    methods[method.name+method.desc] = method

                    if(method.hierarchyMembers.isEmpty()) {
                        method.hierarchyMembers.add(method)
                    }
                }
            }

            if(cur.parent != null) toCheck.add(cur.parent!!)
            toCheck.addAll(cur.interfaces)

            cur = toCheck.poll()
        }
    }

    /**
     * The final class processing pass.
     *
     * @param cls Class
     */
    private fun processD(cls: Class) {
        /*
         * Build method parent/child hierarchy
         */
        cls.methods.values.forEach { m ->
            if(m.hierarchyMembers.size > 1) {
                /*
                 * If there are more than one hierarchy member,
                 * then the method has parent or child methods.
                 */
                calculateMethodRelationships(m, ArrayDeque(), mutableSetOf())
            }
        }

        /*
         * Finish the final updates on the fields.
         */
        cls.fields.values.forEach { f ->
            f.hierarchyMembers.add(f)

            if(f.writeRefs.size == 1) {
                f.initializer.addAll(Interpreter.extractInitializer(f))
            }
        }
    }

    private fun calculateMethodRelationships(method: Method, toCheck: ArrayDeque<Class>, checked: MutableSet<Class>) {
        if(method.isInitializer || method.isConstructor) return
        if(method.isHierarchyBarrier()) return

        if(method.owner.parent != null) toCheck.add(method.owner.parent!!)
        toCheck.addAll(method.owner.interfaces)

        var cur: Class? = toCheck.poll()
        while(cur != null) {
            if(!checked.add(cur)) continue

            val m = cur.getMethod(method.name, method.desc)
            if(m != null && !m.isHierarchyBarrier()) {
                method.parents.add(m)
                m.children.add(method)
            } else {
                if(cur.parent != null) toCheck.add(cur.parent!!)
                toCheck.addAll(cur.interfaces)
            }

            cur = toCheck.poll()
        }

        checked.clear()
    }

    private fun Method.isHierarchyBarrier(): Boolean {
        return (this.access and (ACC_PRIVATE or ACC_STATIC)) != 0
    }
}