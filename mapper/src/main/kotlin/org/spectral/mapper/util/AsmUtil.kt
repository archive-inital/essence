package org.spectral.mapper.util

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.LdcInsnNode

fun InsnList.extractStrings(): Set<String> {
    return this.toList().extractStrings()
}

fun Collection<AbstractInsnNode>.extractStrings(): Set<String> {
    return this.iterator().extractStrings()
}

fun Iterator<AbstractInsnNode>.extractStrings(): Set<String> {
    val results = mutableSetOf<String>()
    while(this.hasNext()) {
        val insn = this.next()

        if(insn is LdcInsnNode) {
            if(insn.cst is String) {
                results.add(insn.cst as String)
            }
        }
    }

    return results
}