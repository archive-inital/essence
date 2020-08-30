package org.spectral.mapping

/**
 * Represents a model of obfuscation name mappings
 * between Class group revisions.
 */
class Mappings {

    /**
     * A list of class mappings
     */
    val classes = mutableListOf<ClassMapping>()

    fun mapClass(name: String): String? {
        return classes.firstOrNull { it.obfName == name }?.name
    }

    fun mapMethod(owner: String, name: String, desc: String): String? {
        return classes.flatMap { it.methods }.firstOrNull {
            it.obfOwner == owner && it.obfName == name && it.obfDesc == desc
        }?.name
    }

    fun mapField(owner: String, name: String, desc: String): String? {
        return classes.flatMap { it.fields }.firstOrNull {
            it.obfOwner == owner && it.obfName == name && it.obfDesc == desc
        }?.name
    }

    fun mapArgument(owner: String, methodName: String, methodDesc: String, index: Int): String? {
        return classes.flatMap { it.methods }
            .filter { it.obfOwner == owner && it.obfName == methodName && it.obfDesc == methodDesc }
            .flatMap { it.arguments }
            .firstOrNull { it.index == index }?.name
    }
}