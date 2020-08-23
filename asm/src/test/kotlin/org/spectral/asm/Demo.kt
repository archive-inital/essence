package org.spectral.asm

import org.junit.jupiter.api.Test
import org.spectral.asm.util.JarUtil
import java.io.File

class Demo {

    @Test
    fun test() {
        val env = ClassEnvironment.init(File("../gamepack-deob.jar"), File("../gamepack-deob.jar"))
        println()

    }
}