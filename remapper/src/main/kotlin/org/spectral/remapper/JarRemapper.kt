package org.spectral.remapper

import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import org.spectral.mapping.Mappings
import org.tinylog.kotlin.Logger
import java.io.File
import kotlin.system.exitProcess

/**
 * Responsible for remaping a JAR file using given loaded
 * [Mappings] object.
 */
class JarRemapper private constructor(
    private val inputJarFile: File,
    private val outputJarFile: File,
    private val nameMappingsFile: File?,
    val mappings: Mappings
) {

    /**
     * The [JarRemapper] builder class.
     */
    class Builder {
        private var inputJarFile: File? = null
        private var outputJarFile: File? = null
        private var mappings: Mappings? = null
        private var nameMappingsFile: File? = null

        fun input(file: File) = this.apply { this.inputJarFile = file }
        fun output(file: File) = this.apply { this.outputJarFile = file }
        fun mappings(mappings: Mappings) = this.apply { this.mappings = mappings }
        fun nameMappings(file: File) = this.apply { this.nameMappingsFile = file }

        /**
         * Builds the [JarRemapper] instance.
         *
         * @return JarRemapper
         */
        fun build(): JarRemapper {
            if(inputJarFile == null || outputJarFile == null || mappings == null) {
                Logger.error("All options have not been specified.")
                exitProcess(-1)
            }

            return JarRemapper(inputJarFile!!, outputJarFile!!, nameMappingsFile, mappings!!)
        }
    }

    /**
     * Remaps the input JAR file entry names from the obfuscated names
     * to the remapped names given some loaded mappings model.
     */
    fun run() {
        Logger.info("Applying mappings to jar file: '${inputJarFile.path}'.")

        /*
         * Load the [ClassGroup] from the inputJarFile
         */
        val group = ClassGroup.fromJar(inputJarFile)

        Logger.info("Loaded input JAR file. Found ${group.size} classes.")

        val remapper = AsmRemapper(mappings, nameMappingsFile)

        val remappedGroup = this.remap(group, remapper)

        Logger.info("Preparing to export remapped classes to jar file: '${outputJarFile.path}'.")

        remappedGroup.toJar(outputJarFile)

        Logger.info("Remapped classes have finished exporting.")
    }

    /**
     * Applies the [remapper] to all elements in the [group]
     *
     * @param group ClassGroup
     * @param remapper AsmRemapper
     */
    private fun remap(group: ClassGroup, remapper: AsmRemapper): ClassGroup {
        Logger.info("Remapping class elements...")

        val newGroup = ClassGroup()

        var counter = 0

        group.forEachIndexed { index, cls ->
            val node = ClassNode()
            cls.accept(ClassRemapper(node, remapper))

            /*
             * Replace the class with the updated [ClassNode] element.
             */
            newGroup.add(node)

            counter++
        }

        Logger.info("Finished remapping $counter classes.")

        return newGroup
    }
}