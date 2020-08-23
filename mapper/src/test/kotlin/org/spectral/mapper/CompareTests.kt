package org.spectral.mapper

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import org.spectral.asm.Class
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.mapper.util.CompareUtil
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CompareTests : Spek({
    describe("class is potentially equal") {
        val a = mockk<Class>()
        val b = mockk<Class>()

        describe("matches are the same") {
            every { a.match } returns b
            every { b.match } returns a

            it("returns true") {
                assert(CompareUtil.isPotentiallyEqual(a, b))
            }
        }

        describe("both names are not obfuscated and match") {
            every { a.name } returns "test"
            every { b.name } returns "test"

            it("returns true") {
                assert(CompareUtil.isPotentiallyEqual(a, b))
            }
        }

        describe("both names are not obfuscate and dont match") {
            every { a.name } returns "test"
            every { b.name } returns "test2"

            it("returns false") {
                assert(!CompareUtil.isPotentiallyEqual(a, b))
            }
        }
    }

    describe("method is potentially equal") {
        val a = mockk<Method>()
        val b = mockk<Method>()

        describe("both owners are not potential matches") {
            val clsA = mockk<Class>()
            val clsB = mockk<Class>()

            every { a.match } returns null
            every { b.match } returns null
            every { a.isStatic } returns false
            every { b.isStatic } returns false
            every { a.owner } returns clsA
            every { b.owner } returns clsB
            every { clsA.match } returns null
            every { clsB.match } returns null
            every { clsA.name } returns "test"
            every { clsB.name } returns "test1"

            it("returns false") {
                assert(!CompareUtil.isPotentiallyEqual(a, b))
            }
        }
    }
})