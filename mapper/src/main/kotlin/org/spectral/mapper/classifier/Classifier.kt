package org.spectral.mapper.classifier

import java.util.*

/**
 * Represents a abstraction classifier type class.
 * Responsible for generating a classification similarity score between [T] types.
 *
 * @param T
 */
abstract class Classifier<T> {

    /**
     * The classifier check storage.
     */
    private val classifierChecks = EnumMap<ClassifierLevel, MutableList<ClassifierCheck<T>>>(ClassifierLevel::class.java)

    /**
     * Max score storage per classifier level.
     */
    private val maxScores = hashMapOf<ClassifierLevel, Double>()

    /**
     * Initializes / registers the classifier checks
     */
    abstract fun init()

    /**
     * Registers a classifier check
     *
     * @param classifierCheck ClassifierCheck<T>
     * @param weight Int
     * @param levels Array<out ClassifierLevel>
     */
    fun register(classifierCheck: ClassifierCheck<T>, weight: Int, vararg levels: ClassifierLevel) {
        var lvls = levels.toMutableList()
        if(lvls.isEmpty()) lvls = ClassifierLevel.ALL.toMutableList()

        classifierCheck.weight = weight.toDouble()

        for(level in lvls) {
            classifierChecks.computeIfAbsent(level) { mutableListOf() }.apply { this.add(classifierCheck) }
            maxScores[level] = getMaxScore(level) + weight
        }
    }

    /**
     * Gets the max possible score for a given classifier level if all checks
     * passed with perfect results.
     *
     * @param level ClassifierLevel
     * @return Double
     */
    fun getMaxScore(level: ClassifierLevel): Double {
        return maxScores.getOrDefault(level, 0.0)
    }

    /**
     * Gets all the classifier checks for a given [ClassifierLevel]
     *
     * @param level ClassifierLevel
     * @return List<ClassifierCheck<T>>
     */
    fun getClassifiers(level: ClassifierLevel): List<ClassifierCheck<T>> {
        return classifierChecks.getOrDefault(level, Collections.emptyList())
    }

    /**
     * An internal inline classifier builder method.
     *
     * @param name String
     * @param logic Function2<T, T, Double>
     * @return ClassifierCheck<T>
     */
    internal fun classifier(name: String, logic: (T, T) -> Double): ClassifierCheck<T> {
        return object : ClassifierCheck<T> {
            override val name = name
            override var weight = 0.0
            override val levels = mutableSetOf<ClassifierLevel>()
            override fun getScore(a: T, b: T): Double {
                return logic(a, b)
            }
        }
    }

    abstract fun rank(src: T, dsts: List<T>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<T>>
}