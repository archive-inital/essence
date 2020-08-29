package org.spectral.remapper

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.commons.SimpleRemapper
import org.spectral.mapping.Mappings
import java.io.File

class AsmRemapper(private val mappings: Mappings, nameMappingFile: File? = null) : Remapper() {

    private var nameMapper: Remapper? = null

    init {
        if(nameMappingFile != null) {
            val jsonMapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            val nameMappings = jsonMapper.readValue<Map<String, String>>(nameMappingFile)

            nameMapper = SimpleRemapper(nameMappings)
        }
    }

    override fun map(name: String): String {
        val ret = mappings.classes.firstOrNull { it.obfName == name }
        if(ret != null) return ret.name

        return if(nameMapper == null) name else nameMapper!!.map(name) ?: name
    }

    override fun mapFieldName(owner: String, name: String, desc: String): String {
        val ret = mappings.classes.firstOrNull { it.obfName == owner }
            ?.fields?.firstOrNull { it.obfName == name && it.obfDesc == desc }

        if(ret != null) return ret.name

        return if(nameMapper == null) name else nameMapper!!.mapFieldName(owner, name, desc)
    }

    override fun mapMethodName(owner: String, name: String, desc: String): String {
        val ret = mappings.classes.firstOrNull { it.obfName == owner }
            ?.methods?.firstOrNull { it.obfName == name && it.obfDesc == desc }

        if(ret != null) return ret.name

        return if(nameMapper == null) name else nameMapper!!.mapMethodName(owner, name, desc)
    }

    private fun isObfuscatedName(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
    }
}