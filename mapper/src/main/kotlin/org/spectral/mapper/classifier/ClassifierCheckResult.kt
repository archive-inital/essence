package org.spectral.mapper.classifier

data class ClassifierCheckResult<T>(val check: ClassifierCheck<T>, val score: Double) {
    override fun toString(): String {
        return "${check.name} - $score"
    }
}