package org.spectral.deobfuscator.transformer

import org.objectweb.asm.tree.FieldInsnNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

/**
 * Removes fields where are not invoked in any methods.
 */
class UnusedFieldRemover : Transformer {

    override fun transform(group: ClassGroupExt) {
        var counter = 0

        val usedFields = group.flatMap { it.methods }
            .flatMap { it.instructions.toArray().asIterable() }
            .mapNotNull { it as? FieldInsnNode }
            .map { it.owner + "." + it.name }
            .toSet()

        group.forEach { c ->
            val fieldIterator = c.fields.iterator()
            while(fieldIterator.hasNext()) {
                val field = fieldIterator.next()
                val fName = c.name + "." + field.name
                if(!usedFields.contains(fName) && Modifier.isFinal(field.access)) {
                    fieldIterator.remove()
                    counter++
                }
            }
        }

        Logger.info("Removed $counter unused fields.")
    }
}