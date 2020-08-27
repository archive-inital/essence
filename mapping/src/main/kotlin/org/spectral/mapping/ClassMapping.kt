package org.spectral.mapping

import java.lang.StringBuilder

/**
 * Represents the name to obfuscated name mappings for
 * a class file.
 *
 * @property name The class name
 * @property obfName The obfuscated class name.
 */
class ClassMapping(val name: String, val obfName: String) {

    /**
     * The method mappings in class.
     */
    val methods = mutableListOf<MethodMapping>()

    /**
     * The field mappings in this class.
     */
    val fields = mutableListOf<FieldMapping>()

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("CLASS $name $obfName\n")

        fields.forEach { ret.append(it.toString()) }
        methods.forEach { ret.append(it.toString()) }

        return ret.toString()
    }
}