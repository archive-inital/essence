package org.spectral.mapping.io

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.spectral.mapping.Mappings
import org.tinylog.kotlin.Logger
import java.io.File

/**
 * Loads the opaque predicate values from a JSON file for
 * the mappings.
 *
 * @receiver Mappings
 * @param file File
 */
fun Mappings.loadOpaqueValues(file: File) {
    if(!file.exists()) {
        Logger.warn("Opaque predicate values file: '${file.path}' not found.")
        return
    }

    Logger.info("Loading opaque predicate values from file: '${file.path}'.")

    val jsonMapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    val opaqueValues: Map<String, Int> = jsonMapper.readValue(file)

    if(opaqueValues.isEmpty()) {
        Logger.warn("Unable to parse any opaque predicate values from file.")
        return
    }

    val namedMethodMappings = this.classes.flatMap { it.methods }.associate { "${it.obfOwner}.${it.obfName}${it.obfDesc}" to it }
    var counter = 0

    opaqueValues.forEach { (id, opaque) ->
        val mapping = namedMethodMappings[id] ?: return@forEach
        mapping.opaque = opaque
        counter++
    }

    Logger.info("Updated opaque values in $counter methods.")
}