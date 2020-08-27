package org.spectral.mapping

import java.lang.StringBuilder

/**
 * Represents a memthod argument mapping.
 *
 * @property name String
 * @property index Int
 * @constructor
 */
class ArgumentMapping(val name: String, val index: Int) {

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("\t\tARG $index $name\n")
        return ret.toString()
    }
}