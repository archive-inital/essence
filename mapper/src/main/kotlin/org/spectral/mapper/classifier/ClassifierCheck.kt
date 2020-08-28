package org.spectral.mapper.classifier

/**
 * Represents a classifier check
 */
interface ClassifierCheck<T> {

    val name: String

    var weight: Double

    val levels: MutableSet<ClassifierLevel>

    fun getScore(a: T, b: T): Double

}