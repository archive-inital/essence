package org.spectral.deobfuscator.transformer.rename

import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.commons.SimpleRemapper

/**
 * An implementation of [Remapper] which supports the remapping of
 * local variable names.
 */
class AsmRemapper(mapping: Map<String, String>) : SimpleRemapper(mapping) {

    private var variableCount = 0

    fun mapArgumentName(
        className: String,
        methodName: String?,
        methodDesc: String?,
        name: String?,
        index: Int
    ): String {
        return map("$className.$methodName$methodDesc:$name[$index]") ?: name ?: "param$index"
    }

    fun mapLocalVariableName(
        className: String,
        methodName: String?,
        methodDesc: String?,
        name: String?,
        desc: String,
        index: Int,
        startInsn: Int,
        endInsn: Int
    ): String {
        return map("$className.$methodName$methodDesc:$name[$index,$startInsn,$endInsn]") ?: "var${++variableCount}"
    }
}