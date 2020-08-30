package org.spectral.remapper

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Represents a collection of ASM [ClassNode] objects from a JAR file.
 *
 * @constructor
 */
class ClassGroup private constructor(nodes: MutableList<ClassNode>) : MutableList<ClassNode> by nodes {

    constructor() : this(mutableListOf())

    /**
     * Gets a [ClassNode] in the group with a given class name of [name]
     *
     * @param name String
     * @return ClassNode?
     */
    operator fun get(name: String): ClassNode? = this.firstOrNull { it.name == name }

    /**
     * Export the current class group to a JAR file.
     *
     * @param file File
     */
    fun toJar(file: File) {
        if(file.exists()) {
            Logger.info("Output JAR file already exists. Overwriting it...")
            file.delete()
        }

        val jos = JarOutputStream(FileOutputStream(file))
        val namedGroup = this.associate { it.name to it }
        this.forEach { cls ->
            jos.putNextEntry(JarEntry(cls.name + ".class"))

            val writer = Writer(namedGroup)
            cls.accept(writer)

            jos.write(writer.toByteArray())
            jos.closeEntry()
        }

        jos.close()
    }

    class Writer(private val classNames: Map<String, ClassNode>) : ClassWriter(COMPUTE_FRAMES) {

        companion object {
            val OBJECT_INTERNAL_NAME: String = Type.getInternalName(Any::class.java)
        }

        override fun getCommonSuperClass(type1: String, type2: String): String {
            if (isAssignable(type1, type2)) return type1
            if (isAssignable(type2, type1)) return type2
            var t1 = type1
            do {
                t1 = checkNotNull(superClassName(t1, classNames))
            } while (!isAssignable(t1, type2))
            return t1
        }

        private fun isAssignable(to: String, from: String): Boolean {
            if (to == from) return true
            val sup = superClassName(from, classNames) ?: return false
            if (isAssignable(to, sup)) return true
            return interfaceNames(from).any { isAssignable(to, it) }
        }

        private fun interfaceNames(type: String): List<String> {
            return if (type in classNames) {
                classNames.getValue(type).interfaces
            } else {
                Class.forName(type.replace('/', '.')).interfaces.map { Type.getInternalName(it) }
            }
        }

        private fun superClassName(type: String, classNames: Map<String, ClassNode>): String? {
            return if (type in classNames) {
                classNames.getValue(type).superName
            } else {
                val c = Class.forName(type.replace('/', '.'))
                if (c.isInterface) {
                    OBJECT_INTERNAL_NAME
                } else {
                    c.superclass?.let { Type.getInternalName(it) }
                }
            }
        }
    }

    companion object {

        /**
         * Creates a [ClassGroup] from a JAR file.
         *
         * @param file File
         * @return ClassGroup
         */
        fun fromJar(file: File): ClassGroup {
            if(!file.exists()) {
                throw FileNotFoundException("Jar file not found at '${file.path}'.")
            }

            val nodes = mutableListOf<ClassNode>()

            JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach {
                        val node = ClassNode()
                        val reader = ClassReader(jar.getInputStream(it))
                        reader.accept(node, 0)

                        nodes.add(node)
                    }
            }

            return ClassGroup(nodes)
        }
    }
}