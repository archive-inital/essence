package org.spectral.asm

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.tree.ClassNode

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClassTest {

    private val group = mockk<ClassGroup>()

    /**
     * Tests that creating a 'real' class entry sets the
     * correct ASM values.
     */
    @Test
    fun `construct real class entry`() {
        val node = ClassNode(ASM8)
        node.name = "testClass"
        node.superName = "parentClass"

        val cls = Class(group, node)

        assert(cls.real)
        assert(cls.name == "testClass")
        assert(cls.parentName == "parentClass")
    }

    /**
     * Tests that creating a 'fake' class entry sets the correct
     * ASM values.
     */
    @Test
    fun `construct fake class entry`() {
        val cls = Class(group, "testClass")

        assert(!cls.real)
        assert(cls.name == "testClass")
        assert(cls.parentName == "java/lang/Object")
    }
}