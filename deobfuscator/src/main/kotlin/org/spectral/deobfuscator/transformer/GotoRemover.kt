package org.spectral.deobfuscator.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger

/**
 * Removes GOTO and JUMP instructions that cause analyzer
 * execution fall off.
 */
class GotoRemover : Transformer {

    override fun transform(group: ClassGroupExt) {
        var counter = 0

        group.forEach { c ->
            c.methods.forEach { m ->
                val instructions = m.instructions.iterator()
                while(instructions.hasNext()) {
                    val insn0 = instructions.next()

                    if(insn0.opcode != Opcodes.GOTO) continue
                    insn0 as JumpInsnNode

                    val insn1 = insn0.next
                    if(insn1 == null || insn1 !is LabelNode) continue

                    if(insn0.label == insn1) {
                        instructions.remove()
                        counter++
                    }
                }
            }
        }

        Logger.info("Removed $counter GOTO instruction jumps.")
    }
}