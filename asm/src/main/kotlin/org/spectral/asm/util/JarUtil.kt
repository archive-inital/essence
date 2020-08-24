package org.spectral.asm.util

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

/**
 * Contains utility methods for dealing with JAR files
 * and ASM.
 */
object JarUtil {

    /**
     * Loads all the classes inside a JAR file into [ClassNode] objects.
     *
     * @param file The JAR file to load from
     * @return Collection<ClassNode>
     */
    fun loadJar(file: File): Collection<ClassNode> {
        val nodes = mutableListOf<ClassNode>()

        JarFile(file).use { jar ->
            jar.entries().asSequence().forEach {
                val node = ClassNode()
                val reader = ClassReader(jar.getInputStream(it))
                reader.accept(node, ClassReader.SKIP_FRAMES)

                nodes.add(node)
            }
        }

        return nodes
    }
}