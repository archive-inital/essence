package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import org.spectral.mapping.io.MappingsWriter
import org.tinylog.kotlin.Logger

/**
 * The Console or CLI usage of the program.
 */
class MapperCommand : CliktCommand(
    name = "Mapper",
    help = "Maps obfuscation names between versions or obfuscation iterations."
) {

    private val mappedJarFile by argument(name = "Mapped Jar", help = "The path to the old JAR file").file(mustExist = true, canBeDir = false)
    private val targetJarFile by argument(name = "Target Jar", help = "The path to the new JAR file").file(mustExist = true, canBeDir = false)

    private val exportDir by option("-e", "--export", help = "Export mappings directory path").file(mustExist = false, canBeDir = true)

    /**
     * Executes the command
     */
    override fun run() {
        Logger.info("Initializing...")

        /*
         * Build the mapper instance.
         */
        val mapper = Mapper.Builder()
            .mappedInput(mappedJarFile)
            .targetInput(targetJarFile)
            .build()

        /*
         * Run the mapper.
         */
        mapper.run()

        /*
         * If the export directory is specified,
         * Export the mappings to [exportDir] folder.
         */
        if(exportDir != null) {
            Logger.info("Exporting mappings to folder: '${exportDir!!.path}")

            val mappings = MappingBuilder.buildMappings(mapper.classes!!)
            MappingsWriter(mappings).write(exportDir!!)

            Logger.info("Mappings have finished exporting.")
        }
    }
}