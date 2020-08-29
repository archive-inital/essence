package org.spectral.remapper

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
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
        this.forEach { cls ->
            jos.putNextEntry(JarEntry(cls.name + ".class"))

            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            cls.accept(writer)

            jos.write(writer.toByteArray())
            jos.closeEntry()
        }

        jos.close()
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
                        reader.accept(node, ClassReader.SKIP_FRAMES)

                        nodes.add(node)
                    }
            }

            return ClassGroup(nodes)
        }
    }
}