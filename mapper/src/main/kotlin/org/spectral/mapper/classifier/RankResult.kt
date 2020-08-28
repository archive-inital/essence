package org.spectral.mapper.classifier

data class RankResult<T>(val subject: T, val score: Double, val checks: List<ClassifierCheckResult<T>>)