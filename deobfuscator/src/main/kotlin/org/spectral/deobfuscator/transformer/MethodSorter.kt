package org.spectral.deobfuscator.transformer

import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

/**
 * Sorts methods by the number of lines.
 */
class MethodSorter : Transformer {

    override fun transform(group: ClassGroupExt) {
        group.forEach { c ->
            val methodsByLineCount = c.methods.associateWith { (it.firstLineIndex) ?: Integer.MAX_VALUE }

            val comparator = compareBy<MethodNode> { Modifier.isStatic(it.access) }.thenBy { methodsByLineCount.getValue(it) }
            c.methods = c.methods.sortedWith(comparator)
        }

        Logger.info("Sorted methods by number of lines in all classes.")
    }

    private val MethodNode.firstLineIndex: Int? get() {
        this.instructions.forEach { insn ->
            if(insn is LineNumberNode) {
                return insn.line
            }
        }

        return null
    }
}