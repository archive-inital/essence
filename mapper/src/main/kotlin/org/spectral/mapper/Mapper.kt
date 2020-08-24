package org.spectral.mapper

import org.spectral.asm.Class
import org.spectral.asm.ClassEnvironment
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.asm.util.newIdentityHashSet
import org.spectral.mapper.classifier.ClassClassifier
import org.spectral.mapper.classifier.ClassifierLevel
import org.spectral.mapper.classifier.RankResult
import org.spectral.mapper.util.CompareUtil
import org.tinylog.kotlin.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * The Spectral Client Mapper Main object
 */
class Mapper(val env: ClassEnvironment) {



    /**
     * Initialize the classifiers.
     */
    private fun initClassifiers() {
        ClassClassifier.init()
    }

    /**
     * Runs the mapper.
     */
    fun run() {
        initClassifiers()

        if(matchClasses(ClassifierLevel.INITIAL)) {
            matchClasses(ClassifierLevel.INITIAL)
        }
    }

    /**
     * Matches classes together.
     *
     * @param level ClassifierLevel
     * @return Boolean
     */
    fun matchClasses(level: ClassifierLevel): Boolean {
        val classes = env.groupA.filter { !it.hasMatch() }
        val cmpClasses = env.groupB.filter { !it.hasMatch() }

        val maxScore = ClassClassifier.getMaxScore(level)
        val maxMismatch = maxScore - calculateInverseScore(ABSOLUTE_MATCH_THRESHOLD * (1 - RELATIVE_MATCH_THRESHOLD), maxScore)
        val matches = ConcurrentHashMap<Class, Class>()

        /*
         * Match recursively
         */
        classes.forEach { src ->
            val ranking = ClassClassifier.rank(src, cmpClasses, level, maxMismatch)

            if(ranking.isValid(maxScore)) {
                matches[src] = ranking[0].subject
            }
        }

        matches.resolveConflicts()

        matches.entries.forEach { entry ->
            match(entry.key, entry.value)
        }

        Logger.info("Matched ${matches.size} classes (${classes.size - matches.size} unmatched, ${env.groupA.realClassesCount} total)")

        return !matches.isEmpty()
    }

    private fun <T> ConcurrentHashMap<T, T>.resolveConflicts() {
        val matched = newIdentityHashSet<T>()
        val conflicts = newIdentityHashSet<T>()

        this.values.forEach { cls ->
            if(!matched.add(cls)) {
                conflicts.add(cls)
            }
        }

        if(conflicts.isNotEmpty()) {
            this.values.removeAll(conflicts)
        }
    }

    ///////////////////////////////////////////////////
    // MATCH METHODS
    ///////////////////////////////////////////////////

    /**
     * Unmatches all member hierarchy elements from a [Class] object.
     *
     * @receiver Class
     */
    fun Class.unmatchMembers() {
        this.methods.values.filter { it.real && !it.isStatic }
            .forEach { m ->
                if(m.hasMatch()) {
                    m.match!!.match == null
                    m.match = null
                }
            }

        val fields = this.fields.values.filter { !it.isStatic }
            .forEach { f ->
                if(f.hasMatch()) {
                    f.match!!.match = null
                    f.match = null
                }
            }
    }

    /**
     * Matches two [Class] objects together.
     *
     * @param a Class
     * @param b Class
     */
    fun match(a: Class, b: Class) {
        if(a.match == b) return
        if(a == b) return

        Logger.info("match CLASS [$a] -> [$b]")

        if(a.hasMatch()) {
            a.unmatchMembers()
        }

        if(b.hasMatch()) {
            b.unmatchMembers()
        }

        a.match = b
        b.match = a

        /*
         * Match methods with same non-obfuscated names.
         */
        a.methods.values.forEach { src ->
            if(!CompareUtil.isObfuscatedName(src.name)) {
                val dst = b.getMethod(src.name, src.desc)

                if((dst != null) && !CompareUtil.isObfuscatedName(dst.name)) {
                    match(src, dst)
                    return@forEach
                }
            }

            /*
             * Match hierarchy members
             */
            val matchedSrc = src.matchedHierarchyMember ?: return@forEach

            val dstHierarchyMembers = matchedSrc.match!!.hierarchyMembers
            if(dstHierarchyMembers.size <= 1) return@forEach

            b.methods.values.forEach bLoop@ { dst ->
                if(dstHierarchyMembers.contains(dst)) {
                    match(src, dst)
                    return@bLoop
                }
            }
        }

        /*
         * Match non obfuscated named fields.
         */
        a.fields.values.forEach { src ->
            if(!CompareUtil.isObfuscatedName(src.name)) {
                val dst = b.getField(src.name, src.desc)

                if(dst != null && !CompareUtil.isObfuscatedName(dst.name)) {
                    match(src, dst)
                }
            }
        }
    }

    /**
     * Matches two [Method] objects together.
     *
     * @param a Method
     * @param b Method
     * @param matchHierarchy Boolean
     */
    fun match(a: Method, b: Method, matchHierarchy: Boolean = true) {
        if(a.match == b) return
        if(a == b) return

        Logger.info("match METHOD [$a] -> [$b]")

        if(a.hasMatch()) {
            a.match!!.match = null
        }

        if(b.hasMatch()) {
            b.match!!.match = null
        }

        a.match = b
        b.match = a

        if(matchHierarchy) {
            val srcHierarchyMembers = a.hierarchyMembers
            if(srcHierarchyMembers.size <= 1) return

            val reqGroup = a.group
            var dstHierarchyMembers: Set<Method>? = null

            srcHierarchyMembers.forEach { src ->
                if(src.hasMatch() || !src.owner.hasMatch() || src.owner.group != reqGroup) return@forEach
                if(dstHierarchyMembers == null) dstHierarchyMembers = b.hierarchyMembers

                src.owner.match!!.methods.values.forEach bLoop@ { dst ->
                    if(dstHierarchyMembers!!.contains(dst)) {
                        match(src, dst, false)
                        return@bLoop
                    }
                }
            }
        }
    }

    /**
     * Matches two [Field] objects together.
     *
     * @param a Field
     * @param b Field
     */
    fun match(a: Field, b: Field) {
        if(a.match == b) return
        if(a == b) return

        Logger.info("match FIELD [$a] -> [$b]")

        if(a.hasMatch()) a.match!!.match = null
        if(b.hasMatch()) b.match!!.match = null

        a.match = b
        b.match = a
    }

    companion object {

        /**
         * Matching thresholds
         */
        const val ABSOLUTE_MATCH_THRESHOLD = 0.25
        const val RELATIVE_MATCH_THRESHOLD = 0.025

        fun calculateInverseScore(score: Double, max: Double): Double {
            return sqrt(score) * max
        }

        fun calculateScore(score: Double, max: Double): Double {
            val ret = score / max
            return ret * ret
        }

        fun <T : RankResult<*>> List<T>.isValid(maxScore: Double): Boolean {
            if(this.isEmpty()) return false

            val score = calculateScore(this[0].score, maxScore)
            if(score < ABSOLUTE_MATCH_THRESHOLD) return false
            return if(this.size == 1) {
                true
            } else {
                val nextScore = calculateScore(this[1].score, maxScore)
                nextScore < score * (1 - RELATIVE_MATCH_THRESHOLD)
            }
        }

        /**
         * The static main method.
         *
         * @param args Array<String>
         */
        @JvmStatic
        fun main(args: Array<String>) = MapperCommand().main(args)
    }
}