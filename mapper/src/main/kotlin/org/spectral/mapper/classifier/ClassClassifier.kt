package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN
import org.objectweb.asm.tree.MethodInsnNode
import org.spectral.mapper.asm.Class
import org.spectral.mapper.asm.Field
import org.spectral.mapper.asm.Method
import org.spectral.mapper.asm.newIdentityHashSet
import org.spectral.mapper.Mapper
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapper.util.RankUtil
import kotlin.math.max
import kotlin.math.pow

/**
 * Responsible for classifying [Class] objects.
 */
object ClassClassifier : Classifier<Class>() {

    /**
     * Register the classifier checks.
     */
    override fun init() {
        register(classTypeCheck, 20)
        register(hierarchyDepth, 1)
        register(parentClass, 4)
        register(childClasses, 3)
        register(interfaces, 3)
        register(implementers, 2)
        register(methodCount, 3)
        register(fieldCount, 3)
        register(hierarchySiblings, 2)
        register(similarMethods, 10)
        register(outReferences, 6)
        register(inReferences, 6)
        register(methodOutReferences, 6, ClassifierLevel.SECONDARY, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(methodInReferences, 6, ClassifierLevel.SECONDARY, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(fieldReadReferences, 5, ClassifierLevel.SECONDARY, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(fieldWriteReferences, 5, ClassifierLevel.SECONDARY, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(membersFull, 10, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
        register(inRefsBci, 6, ClassifierLevel.FINAL)
    }

    /**
     * Recursively ranks a [src] class to all of the [dsts] classes and returns a sorted
     * list of results.
     *
     * @param src Class
     * @param dsts List<Class>
     * @param level ClassifierLevel
     * @param maxMismatch Double
     * @return List<RankResult<Class>>
     */
    override fun rank(src: Class, dsts: List<Class>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Class>> {
        return RankUtil.rank(src, dsts, getClassifiers(level), CompareUtil::isPotentiallyEqual, maxMismatch)
    }

    /////////////////////////////////////////////
    // CLASSIFIER CHECKS
    /////////////////////////////////////////////

    private val classTypeCheck = classifier("class type check") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 4).toDouble()
    }

    private val hierarchyDepth = classifier("hierarchy depth") { a, b ->
        var countA = 0
        var countB = 0

        var clsA: Class? = a
        var clsB: Class? = b

        while(clsA?.parent != null) {
            clsA = clsA.parent
            countA++
        }

        while(clsB?.parent != null) {
            clsB = clsB.parent
            countB++
        }

        return@classifier CompareUtil.compareCounts(countA, countB)
    }

    private val hierarchySiblings = classifier("hierarchy siblings") { a, b ->
        return@classifier CompareUtil.compareCounts(a.parent?.children?.size ?: 0, b.parent?.children?.size ?: 0)
    }

    private val parentClass = classifier("parent class") { a, b ->
        if(a.parent == null && b.parent == null) return@classifier 1.0
        if(a.parent == null || b.parent == null) return@classifier 0.0

        return@classifier if(CompareUtil.isPotentiallyEqual(a.parent!!, b.parent!!)) 1.0 else 0.0
    }

    private val childClasses = classifier("child classes") { a, b ->
        return@classifier CompareUtil.compareClassSets(a.children, b.children)
    }

    private val interfaces = classifier("interfaces") { a, b ->
        return@classifier CompareUtil.compareClassSets(a.interfaces, b.interfaces)
    }

    private val implementers = classifier("implementers") { a, b ->
        return@classifier CompareUtil.compareClassSets(a.implementers, b.implementers)
    }

    private val methodCount = classifier("method count") { a, b ->
        return@classifier CompareUtil.compareCounts(a.methods.size, b.methods.size)
    }

    private val fieldCount = classifier("field count") { a, b ->
        return@classifier CompareUtil.compareCounts(a.fields.size, b.fields.size)
    }

    private val similarMethods = classifier("similar methods") { a, b ->
        if(a.methods.isEmpty() && b.methods.isEmpty()) return@classifier 1.0
        if(a.methods.isEmpty() || b.methods.isEmpty()) return@classifier 0.0

        val methodsB = newIdentityHashSet(b.methods.values.toList())
        var totalScore = 0.0
        var bestMatch: Method? = null
        var bestScore = 0.0

        for(methodA in a.methods.values) {
            methodBLoop@ for(methodB in methodsB) {
                if(!CompareUtil.isPotentiallyEqual(methodA, methodB)) continue
                if(!CompareUtil.isPotentiallyEqual(methodA.returnTypeClass, methodB.returnTypeClass)) continue

                val argsA = methodA.arguments
                val argsB = methodB.arguments

                if(argsA.size != argsB.size) continue

                for(i in argsA.indices) {
                    val argA = argsA[i].typeClass
                    val argB = argsB[i].typeClass

                    if(!CompareUtil.isPotentiallyEqual(argA, argB)) {
                        continue@methodBLoop
                    }
                }

                val score = if(!methodA.real || !methodB.real) {
                    if(!methodA.real && !methodB.real) 1.0 else 0.0
                } else {
                    CompareUtil.compareCounts(methodA.instructions.size(), methodB.instructions.size())
                }

                if(score > bestScore) {
                    bestScore = score
                    bestMatch = methodB
                }
            }

            if(bestMatch != null) {
                totalScore += bestScore
                methodsB.remove(bestMatch)
            }
        }

        return@classifier totalScore / max(a.methods.size, b.methods.size)
    }

    private val outReferences = classifier("out references") { a, b ->
        val refsA = a.getOutReferences()
        val refsB = b.getOutReferences()

        return@classifier CompareUtil.compareClassSets(refsA, refsB)
    }

    private val inReferences = classifier("in references") { a, b ->
        val refsA = a.getInReferences()
        val refsB = b.getInReferences()

        return@classifier CompareUtil.compareClassSets(refsA, refsB)
    }

    private val methodOutReferences = classifier("method out references") { a, b ->
        val refsA = a.getMethodOutReferences()
        val refsB = b.getMethodOutReferences()

        return@classifier CompareUtil.compareMethodSets(refsA, refsB)
    }

    private val methodInReferences = classifier("method in references") { a, b ->
        val refsA = a.getMethodInReferences()
        val refsB = b.getMethodInReferences()

        return@classifier CompareUtil.compareMethodSets(refsA, refsB)
    }

    private val fieldReadReferences = classifier("field read references") { a, b ->
        val refsA = a.getFieldReadReferences()
        val refsB = b.getFieldReadReferences()

        return@classifier CompareUtil.compareFieldSets(refsA, refsB)
    }

    private val fieldWriteReferences = classifier("field write references") { a, b ->
        val refsA = a.getFieldWriteReferences()
        val refsB = b.getFieldWriteReferences()

        return@classifier CompareUtil.compareFieldSets(refsA, refsB)
    }

    private val membersFull = classifier("members full") { a, b ->
        val level = ClassifierLevel.EXTRA
        var match = 0.0

        if(a.methods.isNotEmpty() && b.methods.isNotEmpty()) {
            val maxScore = MethodClassifier.getMaxScore(level)

            a.methods.values.filter { it.real }.forEach { methodA ->
                val ranking = MethodClassifier.rank(methodA, b.methods.values.toList(), level, Double.POSITIVE_INFINITY)
                if(Mapper.isValidRank(ranking, maxScore)) {
                    match += Mapper.calculateScore(ranking[0].score, maxScore)
                }
            }
        }

        if(a.fields.isNotEmpty() && b.fields.isNotEmpty()) {
            val maxScore = FieldClassifier.getMaxScore(level)

            a.fields.values.forEach { fieldA ->
                val ranking = FieldClassifier.rank(fieldA, b.fields.values.toList(), level, Double.POSITIVE_INFINITY)
                if(Mapper.isValidRank(ranking, maxScore)) {
                    match += Mapper.calculateScore(ranking[0].score, maxScore)
                }
            }
        }

        val methodCount = max(a.methods.values.filter { it.real }.size, b.methods.values.filter { it.real }.size)
        val fieldCount = max(a.fields.size, b.fields.size)

        if(methodCount == 0 && fieldCount == 0) {
            return@classifier 1.0
        } else {
            return@classifier match / (methodCount + fieldCount)
        }
    }

    private val inRefsBci = classifier("in refs (bci)") { a, b ->
        var matched = 0
        var mismatched = 0

        for(src in a.methodTypeRefs) {
            if(src.owner == a) continue

            val dst = src.match

            if(dst == null || !b.methodTypeRefs.contains(dst)) {
                mismatched++
                continue
            }

            val map = CompareUtil.mapInsns(src, dst)
            if(map == null) continue

            val insnsA = src.instructions
            val insnsB = dst.instructions

            for(srcIdx in 0 until map.size) {
                if(map[srcIdx] < 0) continue

                var insn = insnsA[srcIdx]
                if(insn.type != METHOD_INSN) continue

                var min = insn as MethodInsnNode
                var owner = a.group[min.owner]

                if(owner != a) continue

                insn = insnsB[map[srcIdx]]
                min = insn as MethodInsnNode
                owner = b.group[min.owner]

                if(owner != b) {
                    mismatched++
                } else {
                    matched++
                }
            }
        }

        if(matched== 0 && mismatched == 0) {
            return@classifier 1.0
        } else {
            return@classifier (matched / (matched + mismatched)).toDouble()
        }
    }

    /////////////////////////////////////////////
    // HELPER METHODS
    /////////////////////////////////////////////

    private fun Class.getOutReferences(): Set<Class> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Class>()

        this.methods.values.forEach { m ->
            ret.addAll(m.classRefs)
        }

        this.fields.values.forEach { f ->
            ret.add(f.typeClass)
        }

        return ret
    }

    private fun Class.getInReferences(): Set<Class> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Class>()

        this.methodTypeRefs.forEach { ret.add(it.owner) }
        this.fieldTypeRefs.forEach { ret.add(it.owner) }

        return ret
    }

    private fun Class.getMethodOutReferences(): Set<Method> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Method>()

        this.methods.values.forEach {
            ret.addAll(it.refsOut)
        }

        return ret
    }

    private fun Class.getMethodInReferences(): Set<Method> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Method>()

        this.methods.values.forEach { ret.addAll(it.refsIn) }

        return ret
    }

    private fun Class.getFieldReadReferences(): Set<Field> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Field>()

        this.methods.values.forEach { ret.addAll(it.fieldReadRefs) }

        return ret
    }

    private fun Class.getFieldWriteReferences(): Set<Field> {
        val ret = org.spectral.mapper.asm.newIdentityHashSet<Field>()

        this.methods.values.forEach { ret.addAll(it.fieldWriteRefs) }

        return ret
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}