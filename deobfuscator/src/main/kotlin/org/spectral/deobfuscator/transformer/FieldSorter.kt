package org.spectral.deobfuscator.transformer

import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

/**
 * Sorts the fields in each class based on comparator specs
 */
class FieldSorter : Transformer {

    override fun transform(group: ClassGroupExt) {
        group.forEach { c ->
            c.fields = c.fields.sortedWith(FIELD_COMPARATOR)
        }

        Logger.info("Re-ordered non-static fields within classes.")
    }

    private val FIELD_COMPARATOR: Comparator<FieldNode> = compareBy<FieldNode> { !Modifier.isStatic(it.access) }
        .thenBy { Modifier.toString(it.access and Modifier.fieldModifiers()) }
        .thenBy { Type.getType(it.desc).className }
        .thenBy { it.name }
}