package org.spectral.mapping

import java.lang.StringBuilder

/**
 * A Method mapping
 *
 * @property ownerName String
 * @property name String
 * @property desc String
 * @property obfName String
 * @property obfDesc String
 * @constructor
 */
class MethodMapping(val owner: String, val name: String, val desc: String, val obfOwner: String, val obfName: String, val obfDesc: String) {

    val arguments = mutableListOf<ArgumentMapping>()

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("\tMETHOD $name $obfName $desc $obfDesc $obfOwner\n")
        arguments.forEach { ret.append(it.toString()) }
        return ret.toString()
    }
}