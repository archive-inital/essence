package org.spectral.remapper.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ClassGroup private constructor(nodes: MutableList<ClassNode>) : MutableList<ClassNode> by nodes {

    init {
        this.init()
    }

    /**
     * Initializes the class group.
     */
    fun init() {
        this.forEach { it.init(this) }
    }

    operator fun get(name: String): ClassNode? = this.firstOrNull { it.name == name }

    fun toJar(file: File) {
        if(file.exists()) file.delete()

        val jos = JarOutputStream(FileOutputStream(file))

        this.forEach {
            jos.putNextEntry(JarEntry(it.name + ".class"))

            val writer = ClassWriter(0)
            it.accept(writer)

            jos.write(writer.toByteArray())
            jos.closeEntry()
        }

        jos.close()
    }

    companion object {

        fun fromJar(file: File): ClassGroup {
            val nodes = mutableListOf<ClassNode>()

            JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach {
                        val node = ClassNode()
                        val reader = ClassReader(jar.getInputStream(JarEntry(it)))
                        reader.accept(node, 0)

                        nodes.add(node)
                    }
            }

            return ClassGroup(nodes)
        }
    }
}