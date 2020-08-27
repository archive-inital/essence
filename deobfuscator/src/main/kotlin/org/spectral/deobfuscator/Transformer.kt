package org.spectral.deobfuscator

import org.spectral.deobfuscator.asm.ClassGroupExt

/**
 * Represents a bytecode transformer step.
 */
interface Transformer {

    fun transform(group: ClassGroupExt)

}