package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.types.file

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

    /**
     * Run the command logic.
     */
    override fun run() {

    }
}