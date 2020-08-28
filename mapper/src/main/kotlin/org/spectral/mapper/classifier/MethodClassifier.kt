package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.spectral.mapper.asm.Method
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapper.util.RankUtil
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
        register(parentMethods, 10)
        register(childMethods, 3)
        register(inReferences, 6)
        register(outReferences, 6)
        register(fieldReads, 5)
        register(fieldWrites, 5)
        register(code, 12, ClassifierLevel.EXTRA, ClassifierLevel.FINAL)
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

    private val code = classifier("code") { a, b ->
        return@classifier CompareUtil.compareInsns(a, b)
    }

    private val parentMethods = classifier("parent methods") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.parents, b.parents)
    }

    private val childMethods = classifier("child methods") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.children, b.children)
    }

    /////////////////////////////////////////////
    // HELPER METHODS
    /////////////////////////////////////////////

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}