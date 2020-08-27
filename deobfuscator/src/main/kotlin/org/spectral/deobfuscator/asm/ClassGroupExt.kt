package org.spectral.deobfuscator.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ClassGroupExt private constructor(nodes: MutableList<ClassNode>) : MutableList<ClassNode> by nodes {

    constructor() : this(mutableListOf())

    operator fun get(name: String): ClassNode? = this.firstOrNull { it.name == name }

    private fun init() {
        this.forEach { c ->
            c.setGroup(this)
            c.init()
        }
    }

    fun rebuild() {
        rebuildClasses()
        rebuildMethods()
        rebuildFields()

        this.init()
    }

    fun toJar(file: File) {
        if(file.exists()) {
            file.delete()
        }

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

        fun fromJar(file: File): ClassGroupExt {
            val nodes = mutableListOf<ClassNode>()
            JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach {
                        val node = ClassNode()
                        val reader = ClassReader(jar.getInputStream(it))
                        reader.accept(node, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
                        nodes.add(node)
                    }
            }

            val group = ClassGroupExt(nodes)
            group.init()

            return group
        }
    }
}