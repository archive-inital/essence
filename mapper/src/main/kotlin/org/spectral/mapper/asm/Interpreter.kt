package org.spectral.mapper.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.*
import org.objectweb.asm.tree.analysis.Interpreter
import java.util.*

/**
 * Contains JVM bytecode interpretation utility methods
 */
object Interpreter {

    /**
     * Extracts the instructions which initialize a field.
     *
     * @param field Field
     * @return List<AbstractInsnNode>
     */
    open fun extractInitializer(field: Field): List<AbstractInsnNode> {
        if (field.type.isPrimitive) return emptyList()
        val method: Method = field.writeRefs.iterator().next()
        val asmNode: MethodNode = method.node
        val il = asmNode.instructions
        var fieldWrite: AbstractInsnNode? = null

        //dump(method.asmNode);
        //System.out.println("\n------------------------\n");
        val it: Iterator<AbstractInsnNode> = il.iterator()
        while (it.hasNext()) {
            val aInsn = it.next()
            if (aInsn.opcode == Opcodes.PUTFIELD || aInsn.opcode == Opcodes.PUTSTATIC) {
                val insn = aInsn as FieldInsnNode
                var cls: Class
                if (insn.name == field.name && insn.desc == field.desc && (insn.owner == field.owner.name || field.group[insn.owner]
                        .also { cls = it!! } != null && cls.resolveField(insn.name, insn.desc) === field)
                ) {
                    fieldWrite = insn
                    break
                }
            }
        }
        val interpreter: Interpreter<SourceValue> = SourceInterpreter()
        val analyzer = Analyzer(interpreter)
        val frames: Array<Frame<SourceValue>>
        try {
            frames = analyzer.analyze(method.owner.name, asmNode)
            if (frames.size != asmNode.instructions.size()) throw RuntimeException("invalid frame count")
        } catch (e: AnalyzerException) {
            throw RuntimeException(e)
        }
        val tracedPositions = BitSet(il.size())
        val positionsToTrace: Queue<AbstractInsnNode> = ArrayDeque()
        tracedPositions.set(il.indexOf(fieldWrite))
        positionsToTrace.add(fieldWrite)
        var insn: AbstractInsnNode?
        while (positionsToTrace.poll().also { insn = it } != null) {
            val pos = il.indexOf(insn)
            val frame = frames[pos]
            val stackConsumed: Int = simulate(insn!!, frame)
            for (i in 0 until stackConsumed) {
                val value = frame.getStack(frame.stackSize - i - 1)
                for (in2 in value.insns) {
                    val pos2 = il.indexOf(in2)
                    if (tracedPositions[pos2]) continue
                    tracedPositions.set(pos2)
                    positionsToTrace.add(in2)
                }
            }
            if (insn!!.type == AbstractInsnNode.VAR_INSN && insn!!.opcode >= Opcodes.ILOAD && insn!!.opcode <= Opcodes.ALOAD) {
                val vin = insn as VarInsnNode
                val value = frame.getLocal(vin.`var`)
                for (in2 in value.insns) {
                    val pos2 = il.indexOf(in2)
                    if (tracedPositions[pos2]) continue
                    tracedPositions.set(pos2)
                    positionsToTrace.add(in2)
                }
            } else if (insn!!.opcode == Opcodes.NEW) { // ensure we track the constructor call when running across a NEW insn
                val tin = insn as TypeInsnNode
                val itA: Iterator<AbstractInsnNode> = il.iterator(pos + 1)
                while (itA.hasNext()) {
                    val ain = itA.next()
                    if (ain.opcode == Opcodes.INVOKESPECIAL) {
                        val in2 = ain as MethodInsnNode
                        if (in2.name == "<init>" && in2.owner == tin.desc) {
                            val pos2 = il.indexOf(in2)
                            if (!tracedPositions[pos2]) {
                                tracedPositions.set(pos2)
                                positionsToTrace.add(in2)
                            }
                            break
                        }
                    }
                }
            }
        }

        //Textifier textifier = new Textifier();
        //MethodVisitor visitor = new TraceMethodVisitor(textifier);
        val initIl: MutableList<AbstractInsnNode> = ArrayList(tracedPositions.cardinality())
        var pos = 0
        while (tracedPositions.nextSetBit(pos).also { pos = it } != -1) {
            insn = il[pos]
            initIl.add(insn!!)

            /*System.out.print(pos+": ");

			il.get(pos).accept(visitor);
			System.out.print(textifier.getText().get(0));
			textifier.getText().clear();*/pos++
        }
        field.initializer.addAll(initIl)

        /*		int pos = fieldWritePos;

		for (int i = 0; i < 100; i++) {
			System.out.println(i+" ("+pos+"):");

			Frame<SourceValue> frame = frames[pos];
			Frame<SourceValue> nextFrame = frames[pos + 1];

			int stackConsumed = frame.getStackSize() - nextFrame.getStackSize();

			SourceValue value = frame.getStack(frame.getStackSize() - 1);

			if (value.insns.isEmpty()) {
				System.out.println("empty");
				break;
			}

			for (AbstractInsnNode ain : value.insns) {
				ain.accept(visitor);
				System.out.print(textifier.getText().get(0));
				textifier.getText().clear();
			}

			pos = method.asmNode.instructions.indexOf(value.insns.iterator().next());
		}*/

        /*System.out.println(frame);
		System.out.println("\n------------------------\n");

		dump(frame.getStack(frame.getStackSize() - 1).insns);*/
        //System.out.println();

        return initIl
    }

    /**
     * Simulates what a given opcode does to the JVM stack at a given frame.
     *
     * @param ain AbstractInsnNode
     * @param frame Frame<*>
     * @return Int
     */
    private fun simulate(ain: AbstractInsnNode, frame: Frame<*>): Int {
        return when (ain.type) {
            AbstractInsnNode.INSN -> when (ain.opcode) {
                Opcodes.NOP -> 0
                Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1 -> 0 // +1
                Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD -> 2 // +2
                Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> 3
                Opcodes.POP -> 1
                Opcodes.POP2 -> if (frame.getStack(frame.stackSize - 1)
                        .size == 1
                ) 2 else 1
                Opcodes.DUP -> 1 // +2
                Opcodes.DUP_X1 -> 2 // +3
                Opcodes.DUP_X2 -> if (frame.getStack(frame.stackSize - 2)
                        .size == 1
                ) 3 else 2 // +4/3
                Opcodes.DUP2 -> if (frame.getStack(frame.stackSize - 1)
                        .size == 1
                ) 2 else 1 // +4/2
                Opcodes.DUP2_X1 -> if (frame.getStack(frame.stackSize - 1)
                        .size == 1
                ) 3 else 2 // +5/3
                Opcodes.DUP2_X2 -> if (frame.getStack(frame.stackSize - 1).size == 1) {
                    if (frame.getStack(frame.stackSize - 3).size == 1) { // 4 single slots
                        4 // +6
                    } else { // single at top, then double
                        3 // +5
                    }
                } else if (frame.getStack(frame.stackSize - 3).size == 1) { // double at top, then 2 single
                    3 // +4
                } else { // 2 double slots
                    2 // +3
                }
                Opcodes.SWAP -> 2 // +2
                Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL, Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM -> 2 // +1
                Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG -> 1 // +1
                Opcodes.ISHL, Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR, Opcodes.IAND, Opcodes.LAND, Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR -> 2 // +1
                Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D, Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S -> 1 // +1
                Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG -> 2 // +1
                Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> 1
                Opcodes.RETURN -> 0
                Opcodes.ARRAYLENGTH -> 1 // +1
                Opcodes.ATHROW -> 1 // ->1
                Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> 1
                else -> throw IllegalArgumentException("unknown insn opcode " + ain.opcode)
            }
            AbstractInsnNode.INT_INSN -> when (ain.opcode) {
                Opcodes.BIPUSH, Opcodes.SIPUSH -> 0 // +1
                Opcodes.NEWARRAY -> 1 // +1
                else -> throw IllegalArgumentException("unknown int insn opcode " + ain.opcode)
            }
            AbstractInsnNode.VAR_INSN -> when (ain.opcode) {
                Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> 0 // +1
                Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> 1
                Opcodes.RET -> 0
                else -> throw IllegalArgumentException("unknown var insn opcode " + ain.opcode)
            }
            AbstractInsnNode.TYPE_INSN -> when (ain.opcode) {
                Opcodes.NEW -> 0 // +1
                Opcodes.ANEWARRAY -> 1 // +1
                Opcodes.CHECKCAST, Opcodes.INSTANCEOF -> 1 // +1
                else -> throw IllegalArgumentException("unknown type insn opcode " + ain.opcode)
            }
            AbstractInsnNode.FIELD_INSN -> when (ain.opcode) {
                Opcodes.GETSTATIC -> 0 // +1
                Opcodes.PUTSTATIC -> 1
                Opcodes.GETFIELD -> 1 // +1
                Opcodes.PUTFIELD -> 2
                else -> throw IllegalArgumentException("unknown field insn opcode " + ain.opcode)
            }
            AbstractInsnNode.METHOD_INSN -> Type.getArgumentTypes((ain as MethodInsnNode).desc).size + if (ain.getOpcode() != Opcodes.INVOKESTATIC) 1 else 0 // +1 if ret type != void
            AbstractInsnNode.INVOKE_DYNAMIC_INSN -> Type.getArgumentTypes(
                (ain as InvokeDynamicInsnNode).desc
            ).size // +1 if ret type != void
            AbstractInsnNode.JUMP_INSN -> when (ain.opcode) {
                Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> 1
                Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> 2
                Opcodes.GOTO -> 0
                Opcodes.JSR -> 0 // +1
                Opcodes.IFNULL, Opcodes.IFNONNULL -> 1
                else -> throw IllegalArgumentException("unknown jump insn opcode " + ain.opcode)
            }
            AbstractInsnNode.LABEL -> 0
            AbstractInsnNode.LDC_INSN -> 0 // +1
            AbstractInsnNode.IINC_INSN -> 0
            AbstractInsnNode.TABLESWITCH_INSN -> 1
            AbstractInsnNode.LOOKUPSWITCH_INSN -> 1
            AbstractInsnNode.MULTIANEWARRAY_INSN -> (ain as MultiANewArrayInsnNode).dims // +1
            AbstractInsnNode.FRAME -> 0
            AbstractInsnNode.LINE -> 0
            else -> throw IllegalArgumentException("unknown insn type " + ain.type + " for opcode " + ain.opcode + ", in " + ain.javaClass.name)
        }
    }

    val Type.isPrimitive: Boolean get() {
        val id = this.descriptor
        val start = id[0]
        return start != 'L' && start != '['
    }
}