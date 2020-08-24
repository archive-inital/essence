package org.spectral.asm

import org.spectral.asm.util.JarUtil
import java.io.File

/**
 * Represents an environment where to class groups are loaded
 * for comparisons.
 *
 * This object is only used for mapping and deobfuscation within the spectral client.
 */
class ClassEnvironment private constructor() {

    val groupA = ClassGroup(this)

    val groupB = ClassGroup(this)

    /**
     * Initializes both class groups.
     */
    private fun init() {
        groupA.init()
        groupB.init()
    }

    /**
     * Adds an element to both classes as a shared
     * element.
     *
     * @param element Class
     */
    fun share(element: Class) {
        groupA.add(element)
        groupB.add(element)
    }

    /**
     * Adds a [Method] object to both class groups as a shared
     * method.
     *
     * @param element Method
     */
    fun share(element: Method) {
    }

    companion object {

        /**
         * Creates a new [ClassEnvironment] object from two
         * jar files.
         *
         * @param jarFileA File
         * @param jarFileB File
         * @return ClassEnvironment
         */
        fun init(jarFileA: File, jarFileB: File): ClassEnvironment {
            val env = ClassEnvironment()

            /*
             * Load classes from jar file A.
             */
            JarUtil.loadJar(jarFileA)
                .map { Class(env.groupA, it) }
                .forEach { env.groupA.add(it) }

            /*
             * Load classes from jar file B.
             */
            JarUtil.loadJar(jarFileB)
                .map { Class(env.groupB, it) }
                .forEach { env.groupB.add(it) }


            env.init()

            return env
        }
    }
}
