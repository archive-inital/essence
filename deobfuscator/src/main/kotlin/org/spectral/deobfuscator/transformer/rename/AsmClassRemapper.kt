package org.spectral.deobfuscator.transformer.rename

import org.objectweb.asm.*
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.MethodRemapper

/**
 * ASM Custom Class Remapper
 *
 * @property asmremapper AsmRemapper
 * @constructor
 */
class AsmClassRemapper(classVisitor: ClassVisitor, remapper: AsmRemapper) : ClassRemapper(classVisitor, remapper) {

    val asmremapper = remapper

    var methodName: String? = null
    var methodDesc: String? = null

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        if(methodName != null) throw IllegalStateException("Already visiting a method.")

        methodName = name
        methodDesc = descriptor

        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    override fun createMethodRemapper(methodVisitor: MethodVisitor): MethodVisitor {
        return AsmMethodRemapper(methodVisitor, asmremapper)
    }

    /**
     * ASM Custom Method Remapper
     *
     * @property asmremapper AsmRemapper
     * @constructor
     */

    inner class AsmMethodRemapper(private val methodVisitor: MethodVisitor, private val asmremapper: AsmRemapper) : MethodRemapper(methodVisitor, asmremapper) {

        var insnIndex = 0
        val labels = hashMapOf<Label, Int>()
        var argsVisited = 0

        private fun checkState() {
            if(methodName == null) {
                throw IllegalStateException("Not visiting a method.")
            }
        }

        override fun visitParameter(name: String?, access: Int) {
            checkState()
            argsVisited++

            super.visitParameter(
                asmremapper.mapArgumentName(className, methodName, methodDesc, name, argsVisited),
                access
            )
        }

        private fun checkParameters() {
            if(argsVisited > 0 || methodDesc!!.startsWith("()")) return

            val argCount = Type.getArgumentTypes(methodDesc!!).size

            for(i in 0 until argCount) {
                visitParameter(null, 0)
            }
        }

        override fun visitAnnotationDefault(): AnnotationVisitor {
            checkParameters()
            return super.visitAnnotationDefault()
        }

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
            checkParameters()
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
            checkParameters()
            super.visitAnnotableParameterCount(parameterCount, visible)
        }

        override fun visitParameterAnnotation(
            parameter: Int,
            descriptor: String?,
            visible: Boolean
        ): AnnotationVisitor {
            checkParameters()
            return super.visitParameterAnnotation(parameter, descriptor, visible)
        }

        override fun visitAttribute(attribute: Attribute?) {
            checkParameters()
            super.visitAttribute(attribute)
        }

        override fun visitCode() {
            checkParameters()
            super.visitCode()
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            descriptor: String?,
            itf: Boolean
        ) {
            checkState()
            insnIndex++

            methodVisitor.visitMethodInsn(
                opcode,
                asmremapper.mapType(owner),
                asmremapper.mapMethodName(owner, name, descriptor),
                asmremapper.mapMethodDesc(descriptor),
                itf
            )
        }

        override fun visitInvokeDynamicInsn(
            name: String?,
            descriptor: String?,
            bootstrapMethodHandle: Handle?,
            vararg bootstrapMethodArguments: Any?
        ) {
            checkState()
            insnIndex++

            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
        }

        override fun visitFrame(
            type: Int,
            numLocal: Int,
            local: Array<out Any>?,
            numStack: Int,
            stack: Array<out Any>?
        ) {
            checkState()
            insnIndex++

            super.visitFrame(type, numLocal, local, numStack, stack)
        }

        override fun visitInsn(opcode: Int) {
            checkState()
            insnIndex++

            super.visitInsn(opcode)
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
            checkState()
            insnIndex++

            super.visitIntInsn(opcode, operand)
        }

        override fun visitVarInsn(opcode: Int, `var`: Int) {
            checkState()
            insnIndex++

            super.visitVarInsn(opcode, `var`)
        }

        override fun visitTypeInsn(opcode: Int, type: String?) {
            checkState()
            insnIndex++

            super.visitTypeInsn(opcode, type)
        }

        override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
            checkState()
            insnIndex++

            super.visitFieldInsn(opcode, owner, name, descriptor)
        }

        override fun visitJumpInsn(opcode: Int, label: Label?) {
            checkState()
            insnIndex++

            super.visitJumpInsn(opcode, label)
        }

        override fun visitLabel(label: Label) {
            checkState()
            if(insnIndex == 0 && labels.isNotEmpty()) {
                throw IllegalStateException()
            }

            labels[label] = insnIndex
            insnIndex++

            super.visitLabel(label)
        }

        override fun visitLdcInsn(value: Any?) {
            checkState()
            insnIndex++

            super.visitLdcInsn(value)
        }

        override fun visitIincInsn(`var`: Int, increment: Int) {
            checkState()
            insnIndex++

            super.visitIincInsn(`var`, increment)
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
            checkState()
            insnIndex++

            super.visitTableSwitchInsn(min, max, dflt, *labels)
        }

        override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
            checkState()
            insnIndex++

            super.visitLookupSwitchInsn(dflt, keys, labels)
        }

        override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
            checkState()
            insnIndex++

            super.visitMultiANewArrayInsn(descriptor, numDimensions)
        }

        override fun visitLineNumber(line: Int, start: Label?) {
            checkState()
            insnIndex++

            super.visitLineNumber(line, start)
        }

        override fun visitLocalVariable(
            name: String?,
            descriptor: String,
            signature: String?,
            start: Label,
            end: Label,
            index: Int
        ) {
            checkState()

            val startInsn = labels[start]!!
            val endInsn = labels[end]!!

            super.visitLocalVariable(
                asmremapper.mapLocalVariableName(className, methodName, methodDesc, name, descriptor, index, startInsn, endInsn),
                descriptor,
                signature,
                start,
                end,
                index
            )
        }

        override fun visitEnd() {
            checkState()
            checkParameters()

            insnIndex = 0
            labels.clear()
            argsVisited = 0
            methodName = null
            methodDesc = null

            super.visitEnd()
        }
    }
}