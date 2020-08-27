package org.spectral.deobfuscator.transformer

import org.objectweb.asm.Type
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger
import java.lang.RuntimeException

/**
 * Removes the try-catch blocks which throw a [RuntimeException] error
 * based on method and field names.
 */
class TryCatchRemover : Transformer {

    override fun transform(group: ClassGroupExt) {
        var counter = 0

        group.forEach { c ->
            c.methods.forEach methodLoop@ { m ->
                val size = m.tryCatchBlocks.size
                m.tryCatchBlocks.removeIf { it.type == Type.getInternalName(RuntimeException::class.java) }
                counter += size - m.tryCatchBlocks.size
            }
        }

        Logger.info("Removed $counter RuntimeException try-catch blocks.")
    }
}