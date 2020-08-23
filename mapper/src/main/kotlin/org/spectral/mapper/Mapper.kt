package org.spectral.mapper

import org.spectral.asm.ClassEnvironment
import org.spectral.mapper.classifier.ClassClassifier

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


    }

    companion object {

        /**
         * The static main method.
         *
         * @param args Array<String>
         */
        @JvmStatic
        fun main(args: Array<String>) = MapperCommand().main(args)
    }
}