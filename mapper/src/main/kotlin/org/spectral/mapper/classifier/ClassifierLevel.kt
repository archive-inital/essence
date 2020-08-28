package org.spectral.mapper.classifier

enum class ClassifierLevel {

    INITIAL,

    SECONDARY,

    EXTRA,

    FINAL;

    companion object {
        val ALL = values()
    }
}