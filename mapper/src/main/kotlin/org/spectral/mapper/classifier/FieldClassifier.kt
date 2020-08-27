package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.FeatureExtractor
import org.spectral.asm.Field
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapper.util.RankUtil
import org.spectral.mapper.util.extractStrings
import kotlin.math.pow

/**
 * Responsible for ranking fields and scoring their similarity
 * in order to match them.
 */
object FieldClassifier : Classifier<Field>() {

    /**
     * Register the class
     */
    override fun init() {
        register(fieldTypeCheck, 10)
        register(accessFlags, 4)
        register(types, 10)
        register(readReferences, 6)
        register(writeReferences, 6)
        register(initValue, 7)
        register(initStrings, 8)
    }

    /**
     * Recursively ranks a [src] class to all of the [dsts] classes and returns a sorted
     * list of results.
     */
    override fun rank(src: Field, dsts: List<Field>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Field>> {
        return RankUtil.rank(src, dsts, FieldClassifier.getClassifiers(level), CompareUtil::isPotentiallyEqual, maxMismatch)
    }

    /////////////////////////////////////////////
    // CLASSIFIER CHECKS
    /////////////////////////////////////////////

    private val fieldTypeCheck = classifier("field type check") { a, b ->
        val mask = ACC_STATIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB)).toDouble()
    }

    private val accessFlags = classifier("access flags") { a, b ->
        val mask = (ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE) or ACC_FINAL or ACC_VOLATILE or ACC_TRANSIENT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 6).toDouble()
    }

    private val types = classifier("types") { a, b ->
        return@classifier if(CompareUtil.isPotentiallyEqual(a.typeClass, b.typeClass)) 1.0 else 0.0
    }

    private val readReferences = classifier("read references") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.readRefs, b.readRefs)
    }

    private val writeReferences = classifier("write references") { a, b ->
        return@classifier CompareUtil.compareMethodSets(a.writeRefs, b.writeRefs)
    }

    private val initValue = classifier("init value") { a, b ->
        val valueA = a.value
        val valueB = b.value

        if(valueA == null && valueB == null) return@classifier 1.0
        if(valueA == null || valueB == null) return@classifier 0.0

        return@classifier if(valueA == valueB) 1.0 else 0.0
    }

    private val initStrings = classifier("init strings") { a, b ->
        val initA = a.initializer
        val initB = b.initializer

        if(initA.isEmpty() && initB.isEmpty()) return@classifier 1.0
        if(initA.isEmpty() || initB.isEmpty()) return@classifier 0.0

        val stringsA = mutableSetOf<String>()
        val stringsB = mutableSetOf<String>()

        stringsA.addAll(initA.extractStrings())
        stringsB.addAll(initB.extractStrings())

        return@classifier CompareUtil.compareSets(stringsA, stringsB)
    }

    /////////////////////////////////////////////
    // HELPER METHODS
    /////////////////////////////////////////////

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}