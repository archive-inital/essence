package org.spectral.remapper

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.spectral.mapping.Mappings
import org.spectral.remapper.asm.ClassGroup
import org.tinylog.kotlin.Logger
import java.io.File

class JarRemapper private constructor(
    val inputJarFile: File,
    val outputJarFile: File,
    val deobNamesFile: File,
    val mappings: Mappings
){

    private lateinit var group: ClassGroup

    private lateinit var deobNameMap: HashMap<String, String>

    /**
     * Runs the JAR Remapper
     */
    fun run() {
        Logger.info("Loading classes from input JAR: '${inputJarFile.path}'.")

        group = ClassGroup.fromJar(inputJarFile)

        Logger.info("Successfully loaded ${group.size} classes.")

        Logger.info("Loading deob names from JSON file: '${deobNamesFile.path}'.")

        val jsonMapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        deobNameMap = jsonMapper.readValue(deobNamesFile)

        Logger.info("Found ${deobNameMap.size} original deobfuscated name mappings.")

        val remapper = AsmRemapper(group, deobNameMap, mappings)

        /*
         * Propagate the names
         */
        remapper.propagateNames()

        /*
         * Apply the updated namings and remap
         */
        remapper.remap()

        /*
         * Export the remapped class group to a jar file.
         */
        Logger.info("Exporting remapped classes to JAR file '${outputJarFile.path}'.")

        group.toJar(outputJarFile)

        Logger.info("Jar remapping completed successfully.")
    }

    /**
     * Jar Remapper builder DSL
     *
     * @property inputJarFile File?
     * @property outputJarFile File?
     * @property deobNamesFile File?
     * @property mappings Mappings?
     */
    class JarRemapperBuilder {
        private var inputJarFile: File? = null
        private var outputJarFile: File? = null
        private var deobNamesFile: File? = null
        private var mappings: Mappings? = null

        fun build(): JarRemapper {
            val jarRemapper = JarRemapper(
                inputJarFile!!,
                outputJarFile!!,
                deobNamesFile!!,
                mappings!!
            )

            return jarRemapper
        }

        fun inputJar(file: () -> File): JarRemapperBuilder {
            inputJarFile = file()
            return this
        }

        fun outputJar(file: () -> File): JarRemapperBuilder {
            outputJarFile = file()
            return this
        }

        fun deobNamesFile(file: () -> File): JarRemapperBuilder {
            deobNamesFile = file()
            return this
        }

        fun withMappings(mappings: () -> Mappings): JarRemapperBuilder {
            this.mappings = mappings()
            return this
        }
    }

    companion object {

        fun run(init: JarRemapperBuilder.() -> Unit): JarRemapper {
            val jarRemapper = JarRemapperBuilder()
            jarRemapper.init()

            val inst = jarRemapper.build()
            inst.run()

            return inst
        }
    }
}