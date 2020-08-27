package org.spectral.mapping

import java.lang.StringBuilder

/**
 * A Field Mapping
 *
 * @property owner String
 * @property name String
 * @property desc String
 * @property obfOwner String
 * @property obfName String
 * @property obfDesc String
 * @constructor
 */
class FieldMapping(val owner: String, val name: String, val desc: String, val obfOwner: String, val obfName: String, val obfDesc: String) {

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("\tFIELD $name $obfName $desc $obfDesc $obfOwner\n")
        return ret.toString()
    }
}