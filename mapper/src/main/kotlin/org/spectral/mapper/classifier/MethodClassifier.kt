package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AbstractInsnNode.INVOKE_DYNAMIC_INSN
import org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.spectral.mapper.asm.Method
import org.spectral.mapper.asm.targetHandle
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapper.util.RankUtil
import org.spectral.mapper.util.extractNumbers
import org.spectral.mapper.util.extractStrings
import kotlin.math.pow

/**
 * Responsible for classifying and calculating method similarity scores.
 */
object MethodClassifier : Classifier<Method>() {

    /**
     * Initialize / register classifier checks
     */
    override fun init() {
        register(methodTypeCheck, 10)
        register(accessFlags, 4)
        register(argumentTypes, 10)
        register(returnType, 5)
        register(classReferences, 3)
        register(stringConstants, 5)
        register(numericConstants, 5)
        register(parentMethods, 10)
        register(childMethods, 3)
        register(inReferences, 6)
        register(outReferences, 6)
        register(fieldReads, 5)
        register(fieldWrites, 5)
        register(position, 3)
        register(code, 12, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(inRefsBci, 6, ClassifierLevel.EXTRA)
    }

    /**
     * Recursively classify methods and return sorted rankings.
     *
     * @param src Method
     * @param dsts List<Method>
     * @param level ClassifierLevel
     * @param maxMismatch Double
     * @return List<RankResult<Method>>
     */
    override fun rank(src: Method, dsts: List<Method>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Method>> {
        return RankUtil.rank(src, dsts, getClassifiers(level), CompareUtil::isPotentiallyEqual, maxMismatch)
    }

    /////////////////////////////////////////////
    // CLASSIFIER CHECKS
    /////////////////////////////////////////////

    private val methodTypeCheck = classifier("method type check") { a, b ->
        val mask = ACC_STATIC or ACC_NATIVE or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 3).toDouble()
    }

    private val accessFlags = classifier("access flags") { a, b ->
        val mask = (ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE) or ACC_FINAL or ACC_SYNCHRONIZED or ACC_BRIDGE or ACC_VARARGS or ACC_STRICT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 8).toDouble()
    }

    private val argumentTypes = classifier("argument types") { a, b ->
        return@classifier CompareUtil.compareClassSets(a.argTypeClasses.toSet(), b.argTypeClasses.toSet())
    }

    private val returnType = classifier("return type") { a, b ->
        return@classifier if(CompareUtil.isPotentiallyEqual(a.returnTypeClass, b.returnTypeClass)) 1.0 else 0.0
    }

    private val classReferences = classifier("class references") { a, b ->
        return@classifier CompareUtil.compareClassSets(a.classRefs, b.classRefs)
    }

    private val stringConstants = classifier("string constants") { a, b ->
        val setA = a.instructions.extractStrings()
        val setB = b.instructions.extractStrings()

        return@classifier CompareUtil.compareSets(setA, setB)
    }

    private val numericConstants = classifier("numeric constants") { a, b ->
        val intsA = hashSetOf<Int>()
        val intsB = hashSetOf<Int>()
        val longsA = hashSetOf<Long>()
        val longsB = hashSetOf<Long>()
        val floatsA = hashSetOf<Float>()
        val floatsB = hashSetOf<Float>()
        val doublesA = hashSetOf<Double>()
        val doublesB = hashSetOf<Double>()

        extractNumbers(a.node, intsA, longsA, floatsA, doublesA)
        extractNumbers(b.node, intsB, longsB, floatsB, doublesB)

        return@classifier (CompareUtil.compareSets(intsA, intsB)
                + CompareUtil.compareSets(longsA, longsB)
                + CompareUtil.compareSets(floatsA, floatsB)
                + CompareUtil.compareSets(doublesA, doublesB))
    }

    private val outReferences = classifier("out references") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.refsOut, b.refsOut)
    }

    private val inReferences = classifier("in references") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.refsIn, b.refsIn)
    }

    private val fieldReads = classifier("field reads") { a, b ->
        return@classifier CompareUtil.compareFieldSets(a.fieldReadRefs, b.fieldReadRefs)
    }

    private val fieldWrites = classifier("field writes") { a, b ->
        return@classifier CompareUtil.compareFieldSets(a.fieldWriteRefs, b.fieldWriteRefs)
    }

    private val position = classifier("position") { a, b ->
        return@classifier CompareUtil.classifyPosition(a, b, { it.index }, { method, i -> method.owner.methods[method.owner.methodsIdx[i]]!! }, { method -> method.owner.methods.values.toTypedArray() })
    }

    private val code = classifier("code") { a, b ->
        return@classifier CompareUtil.compareInsns(a, b)
    }

    private val parentMethods = classifier("parent methods") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.parents, b.parents)
    }

    private val childMethods = classifier("child methods") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.children, b.children)
    }

    private val inRefsBci = classifier("in refs (bci)") { a, b ->
        val ownerA = a.owner.name
        val ownerB = b.owner.name
        val nameA = a.name
        val nameB = b.name
        val descA = a.desc
        val descB = b.desc

        var matched = 0
        var mismatched = 0

        for(src in a.refsIn) {
            if(src == a) continue

            val dst = src.match

            if(dst == null || !b.refsIn.contains(dst)) {
                mismatched++
                continue
            }

            val map = CompareUtil.mapInsns(src, dst)
            if(map == null) continue

            val insnsA = src.instructions
            val insnsB = dst.instructions

            for(srcIdx in map.indices) {
                if(map[srcIdx] < 0) continue

                var insn = insnsA[srcIdx]
                val type = insn.type

                if(type != METHOD_INSN && type != INVOKE_DYNAMIC_INSN) continue
                if(!isSameMethod(insn, ownerA, nameA, descA, a)) continue

                insn = insnsB[map[srcIdx]]

                if(!isSameMethod(insn, ownerB, nameB, descB, b)) {
                    mismatched++
                } else {
                    matched++
                }
            }
        }

        if(matched == 0 && mismatched == 0) {
            return@classifier 1.0
        } else {
            return@classifier (matched / ( matched + mismatched )).toDouble()
        }
    }

    /////////////////////////////////////////////
    // HELPER METHODS
    /////////////////////////////////////////////

    private fun isSameMethod(insn: AbstractInsnNode, owner: String, name: String, desc: String, method: Method): Boolean {
        val sOwner: String
        val sName: String
        val sDesc: String
        val sItf: Boolean

        if(insn.type == METHOD_INSN) {
            val minsn = insn as MethodInsnNode
            sOwner = minsn.owner
            sName = minsn.name
            sDesc = minsn.desc
            sItf = minsn.itf
        } else {
            val dinsn = insn as InvokeDynamicInsnNode
            val impl = dinsn.targetHandle ?: return false

            val tag = impl.tag
            if(tag < H_INVOKEVIRTUAL || tag > H_INVOKEINTERFACE) return false

            sOwner = impl.owner
            sName = impl.name
            sDesc = impl.desc
            sItf = impl.isInterface
        }

        val target = method.group[sOwner]
        return sName == name
                && sDesc == desc
                && (sOwner == owner || target != null && target.resolveMethod(name, desc, sItf) == method)
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}