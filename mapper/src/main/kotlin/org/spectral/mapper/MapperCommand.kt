package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.spectral.mapper.asm.ClassEnvironment
import org.spectral.mapping.io.MappingsReader
import org.spectral.mapping.io.MappingsWriter
import org.spectral.mapping.io.loadOpaqueValues
import org.tinylog.kotlin.Logger
import java.io.File

/**
 * The Mapper CLI console command used to start and set
 * flags for the mapper program.
 */
class MapperCommand : CliktCommand(
    name = "Mapper",
    help = "Generates mappings between two JAR file obfuscations by similarity scoring.",
    printHelpOnEmptyArgs = true,
    invokeWithoutSubcommand = true
) {

    /**
     * The JAR file which has been renamed. This is the file that will be used
     * and mapped to [jarFileB]
     */
    private val jarFileA by argument(name = "Mapped Jar File", help = "The file path to the mapped jar file.")
        .file(mustExist = true, canBeDir = false)
        .validate { it.extension == ".jar" }

    /**
     * The target, or the non renamed obfuscated JAR file to generate mappings for.
     */
    private val jarFileB by argument(name = "Target Jar File", help = "The file path to the target jar file.")
        .file(mustExist = true, canBeDir = false)
        .validate { it.extension == ".jar" }

    private val exportMappingsDir by option("-e", "--export", help = "The directory to export the mappings to")
        .file(mustExist = false, canBeDir = true)

    private val opaquesFile by option("-o", "--opaques", help = "The opaque predicates json file.")
        .file(mustExist = false, canBeDir = false)

    /**
     * Run the command logic.
     */
    override fun run() {
        Logger.info("Building class environment...")

        /*
         * Create the class environment from both JAR files.
         */
        val env = ClassEnvironment.init(jarFileA, jarFileB)

        Logger.info("Running mapper...")

        /*
         * Build progress bar.
         */
        val progress = ProgressBarBuilder()
            .setTaskName("Mapping")
            .setUpdateIntervalMillis(250)
            .setUnit(" checks", 1L)
            .setStyle(ProgressBarStyle.ASCII)
            .build()

        /*
         * Create mapper instance.
         */
        val mapper = Mapper(env, progress)
        mapper.run()

        /*
         * If the export directory is specified, build the mappings.
         */
        if(exportMappingsDir != null) {
            val mappings = MappingBuilder.buildMappings(mapper.env.groupA.toCollection())

            /*
             * If the opaques file was specified, load the opaque predicate values.
             */
            if(opaquesFile != null) {
                mappings.loadOpaqueValues(opaquesFile!!)
            }

            Logger.info("Exporting mappings to folder: '${exportMappingsDir!!.path}'.")

            MappingsWriter(mappings).write(exportMappingsDir!!)

            Logger.info("Mapping have been successfully exported.")
        }

        Logger.info("Mapper has completed successfully.")
    }
}