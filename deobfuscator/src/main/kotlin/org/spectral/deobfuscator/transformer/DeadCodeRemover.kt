package org.spectral.deobfuscator.transformer

import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger

/**
 * Removes any dead frames which are never reached as a result
 * of reordering the control-flow.
 */
class DeadCodeRemover : Transformer {

    override fun transform(group: ClassGroupExt) {
        var counter = 0

        group.forEach { c ->
            c.methods.forEach { m ->
                try {
                    val frames = Analyzer(BasicInterpreter()).analyze(c.name, m)
                    val insns = m.instructions.toArray()

                    for(i in frames.indices) {
                        if(frames[i] == null) {
                            m.instructions.remove(insns[i])
                            counter++
                        }
                    }
                } catch(e : Exception) {
                    Logger.error(e) { "Failed to remove dead code frame: ${c.name}.${m.name}${m.desc}" }
                }
            }
        }

        Logger.info("Removed $counter dead code frames.")
    }
}