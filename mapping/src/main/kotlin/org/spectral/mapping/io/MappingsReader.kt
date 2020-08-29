package org.spectral.mapping.io

import org.spectral.mapping.Mappings
import org.spectral.mapping.parser.Parser
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

/**
 * Responsible for reading mapping files from a source.
 *
 * @property mappings Mappings
 * @constructor
 */
class MappingsReader(val mappings: Mappings = Mappings()) {

    /**
     * Read mapping files from a folder.
     *
     * @param folder File
     */
    fun readFrom(folder: File) {
        if(!folder.isDirectory || !folder.exists()) {
            throw FileNotFoundException()
        }

        /*
         * Clear the current mappings
         */
        mappings.classes.clear()

        /*
         * Iterate through each file recursively
         */
        folder.walk().filter { it.isFile && it.extension == "mapping" }.forEach { file ->
            val contents = Files.newBufferedReader(file.toPath()).readText()
            val classMapping = Parser.parseMappingFile(contents)
            mappings.classes.add(classMapping)
        }
    }
}