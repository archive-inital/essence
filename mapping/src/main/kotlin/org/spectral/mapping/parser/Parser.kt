package org.spectral.mapping.parser

import org.spectral.mapping.ArgumentMapping
import org.spectral.mapping.ClassMapping
import org.spectral.mapping.FieldMapping
import org.spectral.mapping.MethodMapping

object Parser {

    fun parseMappingFile(text: String): ClassMapping {
        val lines = text.split("\n")

        /*
         * Line #1 is always the CLASS line.
         */
        val classMapping = parseClassLine(lines[0].split(" ").toList())
        var currentMethodMapping: MethodMapping? = null

        for(i in 1 until lines.size) {
            val tokens = lines[i].split(" ")

            if(tokens[0] == "\tFIELD") {
                classMapping.fields.add(parseFieldLine(classMapping, tokens))
            }

            if(tokens[0] == "\tMETHOD") {
                currentMethodMapping = parseMethodLine(classMapping, tokens)
                classMapping.methods.add(currentMethodMapping)
            }

            if(tokens[0] == "\t\tARG") {
                if(currentMethodMapping == null) throw RuntimeException()
                currentMethodMapping.arguments.add(parseArgLine(tokens))
            }
        }

        return classMapping
    }

    private fun parseClassLine(tokens: List<String>): ClassMapping {
        if(tokens[0] != "CLASS") throw RuntimeException()
        return ClassMapping(tokens[1], tokens[2])
    }

    private fun parseFieldLine(classMapping: ClassMapping, tokens: List<String>): FieldMapping {
        if(tokens[0] != "\tFIELD") throw RuntimeException()
        return FieldMapping(
            owner = classMapping.name,
            name = tokens[1],
            desc = tokens[3],
            obfOwner = tokens[5],
            obfName = tokens[2],
            obfDesc = tokens[4]
        )
    }

    private fun parseMethodLine(classMapping: ClassMapping, tokens: List<String>): MethodMapping {
        if(tokens[0] != "\tMETHOD") throw RuntimeException()
        return MethodMapping(
            owner = classMapping.name,
            name = tokens[1],
            desc = tokens[3],
            obfOwner = tokens[5],
            obfName = tokens[2],
            obfDesc = tokens[4],
            opaque = if(tokens.size == 7) tokens[6].toInt() else -999
        )
    }

    private fun parseArgLine(tokens: List<String>): ArgumentMapping {
        if(tokens[0] != "\t\tARG") throw RuntimeException()
        return ArgumentMapping(tokens[2], tokens[1].toInt())
    }
}