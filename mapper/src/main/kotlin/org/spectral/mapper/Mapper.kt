package org.spectral.mapper

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import me.tongfei.progressbar.ProgressBar
import org.spectral.mapper.classifier.*
import org.spectral.mapper.asm.*
import org.spectral.mapper.util.CompareUtil
import org.tinylog.kotlin.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * The Spectral Client Mapper Main object
 */
class Mapper(val env: ClassEnvironment, private val progress: ProgressBar? = null) {

    private val availableThreads = Runtime.getRuntime().availableProcessors()
    private val dispatcher = Executors.newWorkStealingPool(availableThreads - 2).asCoroutineDispatcher()

    /**
     * Initialize the classifiers.
     */
    private fun initClassifiers() {
        ClassClassifier.init()
        MethodClassifier.init()
        FieldClassifier.init()
    }

    /**
     * Runs the mapper.
     */
    fun run() {
        initClassifiers()

        /*
         * Match any classes we can initially.
         */
        if(matchClasses(ClassifierLevel.INITIAL)) {
            matchClasses(ClassifierLevel.INITIAL)
        }

        /*
         * Match recursively at each classification pass level.
         */
        matchRecursively(ClassifierLevel.SECONDARY)
        matchRecursively(ClassifierLevel.EXTRA)
        matchRecursively(ClassifierLevel.FINAL)

        /*
         * Print the matching statistics.
         */
        this.printMatchStatistics()
    }

    /**
     * Matches all elements recursively until no more elements where matched during
     * the last recursion pass.
     *
     * @param level ClassifierLevel
     */
    fun matchRecursively(level: ClassifierLevel) {
        var matchedAny: Boolean
        var matchedClassesBefore = true

        do {
            matchedAny = matchStaticMethods(level)
            matchedAny = matchedAny or matchMethods(level)
            matchedAny = matchedAny or matchStaticFields(level)
            matchedAny = matchedAny or matchFields(level)

            if(!matchedAny && !matchedClassesBefore) {
                break
            }

            matchedAny = matchedAny or matchClasses(level).also { matchedClassesBefore = it }

        } while(matchedAny)
    }

    /**
     * Matches classes together.
     *
     * @param level ClassifierLevel
     * @return Boolean
     */
    fun matchClasses(level: ClassifierLevel): Boolean {
        Logger.info("Analyzing Classes - Level: $level...")

        val classes = env.groupA.filter { it.real && !it.hasMatch() }
        val cmpClasses = env.groupB.filter { it.real && !it.hasMatch() }

        progress?.maxHint(classes.size.toLong())
        progress?.stepTo(0L)

        val maxScore = ClassClassifier.getMaxScore(level)
        val maxMismatch = maxScore - calculateInverseScore(ABSOLUTE_MATCH_THRESHOLD * (1 - RELATIVE_MATCH_THRESHOLD), maxScore)
        val matches = ConcurrentHashMap<Class, Class>()

        /*
         * Match recursively
         */
        runParallel(classes) { src ->
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

    /**
     * Matches all static methods with all static methods in any class
     *
     * @param level ClassifierLevel
     * @return Boolean
     */
    fun matchStaticMethods(level: ClassifierLevel): Boolean {
        Logger.info("Analyzing static methods - Level: $level...")

        val totalUnmatched = AtomicInteger()
        val matches = getMatches(
            level,
            { it.methods.values.filter { it.real } },
            MethodClassifier,
            MethodClassifier.getMaxScore(level),
            true,
            totalUnmatched
        )

        matches.forEach { (key, value) ->
            match(key, value)
        }

        Logger.info("Matched ${matches.size} static methods (${totalUnmatched.get()} unmatched)")

        return matches.isNotEmpty()
    }

    /**
     * Matches normal member methods.
     *
     * @param level ClassifierLevel
     * @return Boolean
     */
    fun matchMethods(level: ClassifierLevel): Boolean {
        Logger.info("Analyzing methods - Level: $level...")

        val totalUnmatched = AtomicInteger()
        val matches = getMatches(
            level,
            { it.methods.values.filter { it.real } },
            MethodClassifier,
            MethodClassifier.getMaxScore(level),
            false,
            totalUnmatched
        )

        matches.forEach { (key, value) ->
            match(key, value)
        }

        Logger.info("Matched ${matches.size} methods (${totalUnmatched.get()} unmatched)")

        return matches.isNotEmpty()
    }

    fun matchStaticFields(level: ClassifierLevel): Boolean {
        Logger.info("Analyzing static fields - Level: $level...")

        val totalUnmatched = AtomicInteger()
        val matches = getMatches(
            level,
            { it.fields.values.filter { it.real }.toList() },
            FieldClassifier,
            FieldClassifier.getMaxScore(level),
            true,
            totalUnmatched
        )

        matches.forEach { (key, value) ->
            match(key, value)
        }

        Logger.info("Matched ${matches.size} static fields (${totalUnmatched.get()} unmatched)")

        return matches.isNotEmpty()
    }

    fun matchFields(level: ClassifierLevel): Boolean {
        Logger.info("Analyzing fields - Level: $level...")

        val totalUnmatched = AtomicInteger()
        val matches = getMatches(
            level,
            { it.fields.values.filter{ it.real }.toList() },
            FieldClassifier,
            FieldClassifier.getMaxScore(level),
            false,
            totalUnmatched
        )

        matches.forEach { (key, value) ->
            match(key, value)
        }

        Logger.info("Matched ${matches.size} fields (${totalUnmatched.get()} unmatched)")

        return matches.isNotEmpty()
    }

    /**
     * Gets a match map for a matchable element type.
     *
     * @param level ClassifierLevel
     * @param elementResolver Function1<Class, List<T>>
     * @param classifier Classifier<T>
     * @param maxScore Double
     * @param totalUnmatched AtomicInteger
     * @return Map<T, T>
     */
    private fun <T : Matchable<T>> getMatches(
        level: ClassifierLevel,
        elementResolver: (Class) -> List<T>,
        classifier: Classifier<T>,
        maxScore: Double,
        isStatic: Boolean,
        totalUnmatched: AtomicInteger,
        maxMismatch: Double? = null
    ): Map<T, T> {
        val classesA = env.groupA.filter { it.real }
        val classesB = env.groupB.filter { it.real }

        val maxMismatchValue = maxMismatch ?: maxScore - calculateInverseScore(ABSOLUTE_MATCH_THRESHOLD * (1 - RELATIVE_MATCH_THRESHOLD), maxScore)
        val results = ConcurrentHashMap<T, T>()


        runParallel(classesA) { clsA ->
            if(!clsA.hasMatch() && !isStatic) return@runParallel

            var unmatched = 0

            val srcs = mutableListOf<T>()
            val dsts = mutableListOf<T>()

            if(!isStatic) {
                srcs.addAll(elementResolver(clsA).filter { !it.hasMatch() && !it.isStatic })
                dsts.addAll(elementResolver(clsA.match!!).filter { !it.hasMatch() && !it.isStatic })
            } else {
                srcs.addAll(elementResolver(clsA).filter { !it.hasMatch() && it.isStatic })
                classesB.forEach { clsB ->
                    dsts.addAll(elementResolver(clsB).filter { !it.hasMatch() && it.isStatic })
                }
            }

            for(element in srcs) {
                if(element.hasMatch()) continue

                val ranking = classifier.rank(element, dsts, level, maxMismatchValue)

                if(ranking.isValid(maxScore)) {
                    results[element] = ranking[0].subject
                } else {
                    unmatched++
                }
            }

            if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
        }

        results.resolveConflicts()

        return results
    }

    /**
     * Runs a [action] for each element in [sources] in parallel inside
     * of a work stealing thread pool dispatcher using kotlin coroutines.
     *
     * @param sources Collection<T>
     * @param action Function1<T, Unit>
     */
    private fun <T> runParallel(sources: Collection<T>, action: (T) -> Unit) {
        progress?.maxHint(sources.size.toLong())
        progress?.stepTo(0L)

        runBlocking {

            val jobs = mutableListOf<Deferred<Unit>>()

            val producer = flow {
                sources.forEach { emit(it) }
            }

            /*
             * Run the coroutine in parallel
             */
            producer.buffer(100000).collect {
                async(dispatcher) {
                    action(it)
                    progress?.step()
                    return@async
                }.also { job -> jobs.add(job) }
            }

            jobs.awaitAll()
        }
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

        MappingCache.clear()
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

        MappingCache.clear()
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

        a.match = b
        b.match = a

        MappingCache.clear()
    }

    ///////////////////////////////////////////////////
    // UTIL METHODS
    ///////////////////////////////////////////////////

    private fun printMatchStatistics() {
        val classCount = env.groupA.filter { it.real && it.hasMatch() }.size
        val classTotal = env.groupA.filter { it.real }.size
        val staticMethodCount = env.groupA.filter { it.real }.flatMap { it.methods.values.filter { it.real && it.isStatic && it.hasMatch() } }.size
        val staticMethodTotal = env.groupA.filter { it.real }.flatMap { it.methods.values.filter { it.real && it.isStatic } }.size
        val methodCount = env.groupA.filter { it.real }.flatMap { it.methods.values.filter { it.real && it.hasMatch() } }.size
        val methodTotal = env.groupA.filter { it.real }.flatMap { it.methods.values.filter { it.real } }.size
        val staticFieldCount = env.groupA.filter { it.real }.flatMap { it.fields.values.filter { it.real && it.isStatic && it.hasMatch() } }.size
        val staticFieldTotal = env.groupA.filter { it.real }.flatMap { it.fields.values.filter { it.real && it.isStatic } }.size
        val fieldCount = env.groupA.filter { it.real }.flatMap { it.fields.values.filter { it.real && it.hasMatch() } }.size
        val fieldTotal = env.groupA.filter { it.real }.flatMap { it.fields.values.filter { it.real } }.size

        println("===========================================")
        println("Classes: $classCount / $classTotal (${(classCount.toDouble() / classTotal.toDouble()) * 100.0}%)")
        println("Static Methods: $staticMethodCount / $staticMethodTotal (${(staticMethodCount.toDouble() / staticMethodTotal.toDouble()) * 100.0}%)")
        println("Methods: $methodCount / $methodTotal (${(methodCount.toDouble() / methodTotal.toDouble()) * 100.0}%)")
        println("Static Fields: $staticFieldCount / $staticFieldTotal (${(staticFieldCount.toDouble() / staticFieldTotal.toDouble()) * 100.0}%)")
        println("Fields: $fieldCount / $fieldTotal (${(fieldCount.toDouble() / fieldTotal.toDouble()) * 100.0}%)")
        println("===========================================")
    }

    companion object {

        /**
         * Matching thresholds
         */
        const val ABSOLUTE_MATCH_THRESHOLD = 0.01
        const val RELATIVE_MATCH_THRESHOLD = 0.001

        fun calculateInverseScore(score: Double, max: Double): Double {
            return sqrt(score) * max
        }

        fun calculateScore(score: Double, max: Double): Double {
            val ret = score / max
            return ret * ret
        }

        fun <T : RankResult<*>> isValidRank(ranking: List<T>, maxScore: Double): Boolean {
            return ranking.isValid(maxScore)
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