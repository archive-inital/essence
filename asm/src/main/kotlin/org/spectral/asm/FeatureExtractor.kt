package org.spectral.asm

import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode

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
    }

    /**
     * Processing pass #1
     *
     * @param cls Class
     */
    private fun processA(cls: Class) {
        /*
         * Build the hierarchy graph of the class.
         */
        cls.parent = group.getOrCreate(cls.parentName)
        cls.parent!!.children.add(cls)

        cls.interfaces.addAll(cls.interfaceNames.map { group.getOrCreate(it) })
        cls.interfaces.forEach { it.implementers.add(cls) }
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
        val it = method.instructions.iterator()

        while(it.hasNext()) {
            val insn = it.next()

            when(insn) {

                /*
                 * When instruction is a method invocation.
                 */
                is MethodInsnNode -> {

                }

                /*
                 * When instruction is a field invocation.
                 */
                is FieldInsnNode -> {

                }

                /*
                 * When instruction is a type declaration
                 */
                is TypeInsnNode -> {

                }

                /*
                 * When instruction is a invoke dynamic invocation.
                 */
                is InvokeDynamicInsnNode -> {

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
    private fun processMethodInvocation(method: Method, ownerName: String, name: String, desc: String, toInterface: Boolean) {
        val owner = group.getOrCreate(ownerName)
    }
}