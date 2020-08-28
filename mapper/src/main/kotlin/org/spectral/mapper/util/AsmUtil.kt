package org.spectral.mapper.util

import org.objectweb.asm.tree.*

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

fun extractNumbers(
    node: MethodNode,
    ints: MutableSet<Int>,
    longs: MutableSet<Long>,
    floats: MutableSet<Float>,
    doubles: MutableSet<Double>
) {
    val it: Iterator<AbstractInsnNode> = node.instructions.iterator()
    while (it.hasNext()) {
        val aInsn = it.next()
        if (aInsn is LdcInsnNode) {
            handleNumberValue(aInsn.cst, ints, longs, floats, doubles)
        } else if (aInsn is IntInsnNode) {
            ints.add(aInsn.operand)
        }
    }
}

fun handleNumberValue(
    number: Any?,
    ints: MutableSet<Int>,
    longs: MutableSet<Long>,
    floats: MutableSet<Float>,
    doubles: MutableSet<Double>
) {
    if (number == null) return
    if (number is Int) {
        ints.add(number)
    } else if (number is Long) {
        longs.add(number)
    } else if (number is Float) {
        floats.add(number)
    } else if (number is Double) {
        doubles.add(number)
    }
}