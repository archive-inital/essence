package org.spectral.mapper

import org.spectral.asm.ClassEnvironment

/**
 * The Spectral Client Mapper Main object
 */
class Mapper(val env: ClassEnvironment) {

    /**
     * Runs the mapper.
     */
    fun run() {

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