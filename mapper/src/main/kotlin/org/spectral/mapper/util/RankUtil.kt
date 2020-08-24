package org.spectral.mapper.util

import org.spectral.asm.Matchable
import org.spectral.mapper.classifier.ClassifierCheck
import org.spectral.mapper.classifier.RankResult

/**
 * Contains utility methods for ranking classifiers.
 */
object RankUtil {

    /**
     * Recursively ranks matchable elements from single -> many
     *
     * @param src T
     * @param dsts List<T>
     * @param checks List<ClassifierCheck<T>>
     * @param predicate Function2<T, T, Boolean>
     * @param maxMismatch Double
     * @return List<RankResult<T>>
     */
    fun <T : Matchable<T>> rank(src: T, dsts: List<T>, checks: List<ClassifierCheck<T>>, predicate: (T, T) -> Boolean, maxMismatch: Double): List<RankResult<T>> {
        val results = mutableListOf<RankResult<T>>()

        dsts.forEach { dst ->
            val result = rank(src, dst, checks, predicate, maxMismatch)
            if(result != null) {
                results.add(result)
            }
        }

        return results.sortedByDescending { it.score }
    }

    /**
     * Ranks a single -> single matchable element and returns the weighted
     * score result.
     *
     * @param src T
     * @param dst T
     * @param checks List<ClassifierCheck<T>>
     * @param predicate Function2<T, T, Boolean>
     * @param maxMismatch Double
     * @return RankResult<T>
     */
    private fun <T : Matchable<T>> rank(src: T, dst: T, checks: List<ClassifierCheck<T>>, predicate: (T, T) -> Boolean, maxMismatch: Double): RankResult<T>? {
        if(!predicate(src, dst)) return null

        var score = 0.0
        var mismatch = 0.0

        checks.forEach { check ->
            val cScore = check.getScore(src, dst)
            val weight = check.weight
            val weightedScore = cScore * weight

            mismatch += weight - weightedScore
            if(mismatch >= maxMismatch) return null

            score += weightedScore
        }

        return RankResult(dst, score)
    }
}