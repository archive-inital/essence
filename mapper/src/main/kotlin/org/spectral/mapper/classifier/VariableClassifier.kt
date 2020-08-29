package org.spectral.mapper.classifier

import org.objectweb.asm.tree.AbstractInsnNode.IINC_INSN
import org.objectweb.asm.tree.AbstractInsnNode.VAR_INSN
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.spectral.mapper.asm.Variable
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapper.util.RankUtil

/**
 * Resposible for matching / score method argument and local
 * variable similarities.
 */
object VariableClassifier : Classifier<Variable>() {

    /**
     * Register the classifier checks
     */
    override fun init() {
        register(type, 10)
        register(position, 3)
        register(lvIndex, 2)
        register(usage, 8)
    }

    /**
     * Rank a [src] to all the [dsts] and order by similarity score.
     *
     * @param src Variable
     * @param dsts List<Variable>
     * @param level ClassifierLevel
     * @param maxMismatch Double
     * @return List<RankResult<Variable>>
     */
    override fun rank(src: Variable, dsts: List<Variable>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Variable>> {
        return RankUtil.rank(src, dsts, getClassifiers(level), CompareUtil::isPotentiallyEqual, maxMismatch)
    }

    /////////////////////////////////////////////
    // CLASSIFIER CHECKS
    /////////////////////////////////////////////

    private val type = classifier("type") { a, b ->
        return@classifier if(CompareUtil.isPotentiallyEqual(a.typeClass, b.typeClass)) 1.0 else 0.0
    }

    private val position = classifier("position") { a, b ->
        return@classifier CompareUtil.classifyPosition(
            a, b,
            { it.index },
            { variable, i -> if(variable.isArg) variable.owner.arguments[i] else variable.owner.variables[i] },
            { variable -> if(variable.isArg) variable.owner.arguments.toTypedArray() else variable.owner.variables.toTypedArray() }
        )
    }

    private val lvIndex = classifier("lv index") { a, b ->
        return@classifier if(a.lvIndex == b.lvIndex) 1.0 else 0.0
    }

    private val usage = classifier("usage") { a, b ->
        val map = CompareUtil.mapInsns(a.owner, b.owner)
        if(map == null) return@classifier 1.0

        val insnsA = a.owner.instructions
        val insnsB = b.owner.instructions

        var matched = 0
        var mismatched = 0

        for(srcIdx in map.indices) {
            val dstIdx = map[srcIdx]
            if(dstIdx < 0) continue

            val insnA = insnsA[srcIdx]
            val insnB = insnsB[dstIdx]

            val varA: Int
            val varB: Int

            if(insnA.type == VAR_INSN) {
                varA = (insnA as VarInsnNode).`var`
                varB = (insnB as VarInsnNode).`var`
            }
            else if(insnA.type == IINC_INSN) {
                varA = (insnA as IincInsnNode).`var`
                varB = (insnB as IincInsnNode).`var`
            }
            else {
                continue
            }

            if(varA == a.lvIndex && (a.startInsn < 0 || srcIdx >= a.startInsn && srcIdx < a.endInsn)) {
                if(varB == b.lvIndex && (b.startInsn < 0 || dstIdx >= b.startInsn && dstIdx < b.endInsn)) {
                    matched++
                } else {
                    mismatched++
                }
            }
        }

        if(matched == 0 && mismatched == 0) {
            return@classifier 1.0
        } else {
            return@classifier (matched / (matched + mismatched)).toDouble()
        }
    }

}