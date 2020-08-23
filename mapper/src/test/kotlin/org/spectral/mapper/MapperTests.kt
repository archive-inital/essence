package org.spectral.mapper

import io.mockk.mockk
import org.spectral.asm.ClassEnvironment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MapperTests : Spek({
    val env = mockk<ClassEnvironment>()

    describe("initialization") {}
})