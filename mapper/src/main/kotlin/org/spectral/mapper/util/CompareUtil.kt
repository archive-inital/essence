package org.spectral.mapper.util

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.spectral.asm.*
import org.spectral.asm.util.newIdentityHashSet
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The base logic for all the mapper checks.
 *
 * Contains utility methods for determining if two
 * given elements are potential match candidates.
 */
object CompareUtil {

    fun isPotentiallyEqual(a: Class, b: Class): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Method, b: Method): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Field, b: Field): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!isObfuscatedName(a.name) && !isObfuscatedName(b.name)) {
            return a.name == b.name
        }

        return true
    }

    fun isPotentiallyEqual(a: Variable, b: Variable): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(a.isArg != b.isArg) return false
        if(!isPotentiallyEqual(a.owner, b.owner)) return false

        return true
    }

    fun isObfuscatedName(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
    }

    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0

        return (1 - delta / max(a, b)).toDouble()
    }

    fun <T> compareSets(a: Set<T>, b: Set<T>): Double {
        val setA = a
        val setB = mutableSetOf<T>().apply { this.addAll(b) }

        val oldSize = setB.size
        setB.removeAll(setA)

        val matched = oldSize - setB.size
        val total = setA.size - matched + oldSize

        return if(total == 0) 1.0 else (matched / total).toDouble()
    }

    fun compareClassSets(a: Set<Class>, b: Set<Class>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    fun compareMethodSets(a: Set<Method>, b: Set<Method>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    fun compareFieldSets(a: Set<Field>, b: Set<Field>): Double {
        return compareIdentitySets(a, b, CompareUtil::isPotentiallyEqual)
    }

    private fun <T : Matchable<T>> compareIdentitySets(a: Set<T>, b: Set<T>, predicate: (T, T) -> Boolean): Double {
        if(a.isEmpty() || b.isEmpty()) {
            return if(a.isEmpty() && b.isEmpty()) 1.0 else 0.0
        }

        val setA = newIdentityHashSet(a)
        val setB = newIdentityHashSet(b)

        val total = setA.size + setB.size
        var unmatched = 0

        var it = setA.iterator()
        while(it.hasNext()) {
            val elementA = it.next()

            if(setB.remove(elementA)) {
                it.remove()
            } else if(elementA.hasMatch()) {
                if(!setB.remove(elementA.match)) {
                    unmatched++
                }

                it.remove()
            }
        }

        it = setB.iterator()
        while(it.hasNext()) {
            val elementB = it.next()

            if(!isObfuscatedName(elementB.name)) {
                unmatched++
                it.remove()
            }
        }

        it = setA.iterator()
        while(it.hasNext()) {
            val elementA = it.next()

            var found = false

            for(elementB in setB) {
                if(predicate(elementA, elementB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
                it.remove()
            }
        }

        for(elementB in setB) {
            var found = false

            for(elementA in setA) {
                if(predicate(elementA, elementB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
            }
        }

        return ((total - unmatched) / total).toDouble()
    }

    /**
     * Compares the instructions of two [Method] objects.
     *
     * Returns a score based on their similarity on how they interact with
     * the JVM stack.
     *
     * @param a Method
     * @param b Method
     * @return Double
     */
    fun compareInsns(a: Method, b: Method): Double {
        if(!a.real || !b.real) return 1.0

        val insnsA = a.instructions
        val insnsB = b.instructions

        return compareLists(
            insnsA, insnsB,
            InsnList::get, InsnList::size,
            { ia, ib -> compareInsns(ia, ib, insnsA, insnsB, { insns: InsnList, insn: AbstractInsnNode -> insns.indexOf(insn) }, a.group, b.group) }
        )
    }

    /**
     * Compares two given instruction objects.
     *
     * Returns a score based on their similarity on how they interact with
     * the JVM stack.
     *
     * @param listA List<AbstractInsnNode>
     * @param listB List<AbstractInsnNode>
     * @param groupA ClassGroup
     * @param groupB ClassGroup
     * @return Double
     */
    fun compareInsns(listA: List<AbstractInsnNode>, listB: List<AbstractInsnNode>, groupA: ClassGroup, groupB: ClassGroup): Double {
        return compareLists(
            listA, listB,
            List<AbstractInsnNode>::get, List<AbstractInsnNode>::size,
            { ia, ib -> compareInsns(ia, ib, listA, listB, { insns: List<AbstractInsnNode>, insn: AbstractInsnNode -> insns.indexOf(insn) }, groupA, groupB) }
        )
    }

    /**
     * Compares two [Method] objects together by their given identifier attributes.
     *
     * @param ownerA String
     * @param nameA String
     * @param descA String
     * @param toIfA Boolean
     * @param groupA ClassGroup
     * @param ownerB String
     * @param nameB String
     * @param descB String
     * @param toIfB Boolean
     * @param groupB ClassGroup
     * @return Boolean
     */
    private fun compareMethods(
        ownerA: String,
        nameA: String,
        descA: String,
        toIfA: Boolean,
        groupA: ClassGroup,
        ownerB: String,
        nameB: String,
        descB: String,
        toIfB: Boolean,
        groupB: ClassGroup
    ): Boolean {
        val clsA = groupA[ownerA]
        val clsB = groupB[ownerB]

        if (clsA == null && clsB == null) return true
        if (clsA == null || clsB == null) return false

        return compareMethods(clsA, nameA, descA, toIfA, clsB, nameB, descB, toIfB)
    }

    /**
     * Compares two [Method] objects together by their given identifier attributes.
     *
     * @param ownerA Class
     * @param nameA String
     * @param descA String
     * @param toIfA Boolean
     * @param ownerB Class
     * @param nameB String
     * @param descB String
     * @param toIfB Boolean
     * @return Boolean
     */
    private fun compareMethods(
        ownerA: Class,
        nameA: String,
        descA: String,
        toIfA: Boolean,
        ownerB: Class,
        nameB: String,
        descB: String,
        toIfB: Boolean
    ): Boolean {
        val methodA = ownerA.resolveMethod(nameA, descA, toIfA)
        val methodB = ownerB.resolveMethod(nameB, descB, toIfB)

        if (methodA == null && methodB == null) return true
        if (methodA == null || methodB == null) return false

        return isPotentiallyEqual(methodA, methodB)
    }

    /**
     * Determines if two given instructions are doing the same thing
     * on the JVM stack.
     *
     * NOTE : This method is probably the most important method of the mapper.
     * It is the basis of how this entire thing works at all.
     *
     * @param insnA AbstractInsnNode
     * @param insnB AbstractInsnNode
     * @param listA T
     * @param listB T
     * @param position Function2<T, AbstractInsnNode, Int>
     * @param methodA Method
     * @param methodB Method
     * @return Boolean
     */
    private fun <T> compareInsns(
        insnA: AbstractInsnNode,
        insnB: AbstractInsnNode,
        listA: T,
        listB: T,
        position: (T, AbstractInsnNode) -> Int,
        groupA: ClassGroup,
        groupB: ClassGroup
    ): Boolean {
        if(insnA.opcode != insnB.opcode) return false

        /*
         * Switch through the different case type
         * for instruction A.
         */
        when(insnA.type) {

            AbstractInsnNode.INT_INSN -> {
                val a = insnA as IntInsnNode
                val b = insnB as IntInsnNode

                return a.operand == b.operand
            }

            AbstractInsnNode.VAR_INSN -> {
                val a = insnA as VarInsnNode
                val b = insnB as VarInsnNode

                /*
                 * Future Feature.
                 *
                 * Here, we will be doing local variable and argument
                 * matching.
                 */
            }

            AbstractInsnNode.TYPE_INSN -> {
                val a = insnA as TypeInsnNode
                val b = insnB as TypeInsnNode

                val clsA = groupA[a.desc]
                val clsB = groupB[b.desc]

                if (clsA == null && clsB == null) return true
                if (clsA == null || clsB == null) return false

                return isPotentiallyEqual(clsA, clsB)
            }

            AbstractInsnNode.FIELD_INSN -> {
                val a = insnA as FieldInsnNode
                val b = insnB as FieldInsnNode

                val clsA = groupA[a.owner]
                val clsB = groupB[b.owner]

                if (clsA == null && clsB == null) return true
                if (clsA == null || clsB == null) return false

                val fieldA = clsA.resolveField(a.name, a.desc)
                val fieldB = clsB.resolveField(b.name, b.desc)

                if(fieldA == null && fieldB == null) return true
                if(fieldA == null || fieldB == null) return false

                return isPotentiallyEqual(fieldA, fieldB)
            }

            AbstractInsnNode.METHOD_INSN -> {
                val a = insnA as MethodInsnNode
                val b = insnB as MethodInsnNode

                return compareMethods(
                    a.owner, a.name, a.desc, a.isCallToInterface, groupA,
                    b.owner, b.name, b.desc, b.isCallToInterface, groupB
                )
            }

            AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                val a = insnA as InvokeDynamicInsnNode
                val b = insnB as InvokeDynamicInsnNode

                if(a.bsm != b.bsm) return false

                if(a.bsm.isJavaLambda) {
                    val implA = a.bsmArgs[1] as Handle
                    val implB = b.bsmArgs[1] as Handle

                    if(implA.tag != implB.tag) return false

                    when(implA.tag) {
                        /*
                         * Check for known Java impl tags.
                         */
                        Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE -> {
                            return compareMethods(
                                implA.owner, implA.name, implA.desc, implA.isInterface, groupA,
                                implB.owner, implB.name, implB.desc, implB.isInterface, groupB
                            )
                        }
                    }
                }
            }

            /*
             * Control Flow Jump Instructions
             */
            AbstractInsnNode.JUMP_INSN -> {
                val a = insnA as JumpInsnNode
                val b = insnB as JumpInsnNode

                /*
                 * Since we have no primitive data to match
                 * jump instructions on,
                 *
                 * Solution is just to see if the jumps match up or down or adjacent
                 * to the current control flow block.
                 */
                return Integer.signum(position(listA, a.label) - position(listA, a)) == Integer.signum(position(listB, b.label) - position(listB, b))
            }

            AbstractInsnNode.LDC_INSN -> {
                val a = insnA as LdcInsnNode
                val b = insnB as LdcInsnNode

                if(a.cst::class != b.cst::class) return false

                if(a.cst::class == Type::class) {
                    val typeA = a.cst as Type
                    val typeB = b.cst as Type

                    if(typeA.sort != typeB.sort) return false

                    when(typeA.sort) {
                        Type.ARRAY, Type.OBJECT -> {
                            val clsA = groupA[typeA.className]
                            val clsB = groupB[typeB.className]

                            if (clsA == null && clsB == null) return true
                            if (clsA == null || clsB == null) return false

                            return isPotentiallyEqual(clsA, clsB)
                        }
                    }
                } else {
                    return a.cst == b.cst
                }
            }

            AbstractInsnNode.IINC_INSN -> {
                val a = insnA as IincInsnNode
                val b = insnB as IincInsnNode

                if(a.incr != b.incr) return false

                /*
                 * Implement local variable support
                 * Match the loaded local var from the stack.
                 */
            }

            AbstractInsnNode.TABLESWITCH_INSN -> {
                val a = insnA as TableSwitchInsnNode
                val b = insnB as TableSwitchInsnNode

                return (a.min == b.min && a.max == b.max)
            }

            AbstractInsnNode.LOOKUPSWITCH_INSN -> {
                val a = insnA as LookupSwitchInsnNode
                val b = insnB as LookupSwitchInsnNode

                return a.keys == b.keys
            }

            AbstractInsnNode.MULTIANEWARRAY_INSN -> {
                val a = insnA as MultiANewArrayInsnNode
                val b = insnB as MultiANewArrayInsnNode

                if(a.dims != b.dims) return false

                val clsA = groupA[a.desc]
                val clsB = groupB[b.desc]

                if (clsA == null && clsB == null) return true
                if (clsA == null || clsB == null) return false

                return isPotentiallyEqual(clsA, clsB)
            }

            /*
             * TO-DO List
             *
             * - Implement FRAME instruction support
             * - Implement LINE instruction support
             */
        }

        return true
    }

    /**
     * Compares two generic collection objects and returns a similarity score.
     *
     * @param listA T
     * @param listB T
     * @param elementRetriever Function2<T, Int, U>
     * @param sizeRetriever Function1<T, Int>
     * @param predicate Function2<U, U, Boolean>
     * @return Double
     */
    private fun <T, U> compareLists(
        listA: T,
        listB: T,
        elementRetriever: (T, Int) -> U,
        sizeRetriever: (T) -> Int,
        predicate: (U, U) -> Boolean
    ): Double {
        val sizeA = sizeRetriever(listA)
        val sizeB = sizeRetriever(listB)

        if(sizeA == 0 && sizeB == 0) return 1.0
        if(sizeA == 0 || sizeB == 0) return 0.0

        if(sizeA == sizeB) {
            var match = true

            for(i in 0 until sizeA) {
                if(!predicate(elementRetriever(listA, i), elementRetriever(listB, i))) {
                    match = false
                    break
                }
            }

            if(match) return 1.0
        }

        /*
         * Match the list elements by iterating over them using
         * the Levenshtein distance formula.
         *
         * https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows
         */

        val v0 = IntArray(sizeB + 1)
        val v1 = IntArray(sizeB + 1)

        for(i in v0.indices) {
            v0[i] = i
        }

        for(i in 0 until sizeA) {
            v1[0] = i + 1

            for(j in 0 until sizeB) {
                val cost = if(predicate(elementRetriever(listA, i), elementRetriever(listB, j))) 0 else 1
                v1[j + 1] = min(min(v1[j] + 1, v0[j + 1] + 1), v0[j] + cost)
            }

            for(j in v0.indices) {
                v0[j] = v1[j]
            }
        }

        val distance = v1[sizeB]
        val upperBound = max(sizeA, sizeB)

        return (1 - distance / upperBound).toDouble()
    }

    /**
     * Gets whether an invocation instruction is calling an interface reference.
     */
    val MethodInsnNode.isCallToInterface: Boolean get() {
        return this.itf
    }

    /**
     * Gets whether an invoke dynamic instruction is a Java 8 Lambda
     */
    val Handle.isJavaLambda: Boolean get() {
        return (this.tag == Opcodes.H_INVOKESTATIC && this.owner == "java/lang/invoke/LambdaMetafactory" && (this.name == "metafactory" && this.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                || this.name == "altMetafactory" && this.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
                && !this.isInterface)
    }

    /**
     * Maps the instruction indexes of two given [Method] objects.
     *
     * @param a Method
     * @param b Method
     * @return IntArray
     */
    fun mapInsns(a: Method, b: Method): IntArray? {
        if(!a.real || !b.real) return null

        val insnsA = a.instructions
        val insnsB = b.instructions

        /*
         * If this becomes too slow to compute, we may want to
         * build some sort of cache system to leave the mapped source instructions
         * in for each iteration that way we do not need to compute each pass's mapped indexes.
         */
        return mapInsns(insnsA, insnsB, a, b)
    }

    /**
     * Maps the instructions from two [InsnList] objects and returns
     * an array of mapped index values.
     *
     * @param listA InsnList
     * @param listB InsnList
     * @param methodA Method
     * @param methodB Method
     * @return IntArray
     */
    fun mapInsns(
        listA: InsnList,
        listB: InsnList,
        methodA: Method,
        methodB: Method
    ): IntArray {
        return mapLists(
            listA,
            listB,
            InsnList::get,
            InsnList::size,
            { insnA: AbstractInsnNode, insnB: AbstractInsnNode ->
                compareInsns(insnA, insnB, listA, listB, { insns: InsnList, insn: AbstractInsnNode -> insns.indexOf(insn) }, methodA.group, methodB.group)
            }
        )
    }

    /**
     * Maps two lists of generic types index's together using matrix distance calculations
     *
     * @param listA T
     * @param listB T
     * @param elementRetriever Function2<T, Int, U>
     * @param sizeRetriever Function1<T, Int>
     * @param predicate Function2<U, U, Boolean>
     * @return IntArray
     */
    private fun <T, U> mapLists(
        listA: T,
        listB: T,
        elementRetriever: (T, Int) -> U,
        sizeRetriever: (T) -> Int,
        predicate: (U, U) -> Boolean
    ): IntArray {
        val sizeA = sizeRetriever(listA)
        val sizeB = sizeRetriever(listB)

        if(sizeA == 0 && sizeB == 0) return IntArray(0)

        val ret = IntArray(sizeA)

        if(sizeA == 0 || sizeB == 0) {
            Arrays.fill(ret, -1)
            return ret
        }

        if(sizeA == sizeB) {
            var match = true

            for(i in 0 until sizeA) {
                if(!predicate(elementRetriever(listA, i), elementRetriever(listB, i))) {
                    match = false
                    break
                }
            }

            if(match) {
                for(i in ret.indices) {
                    ret[i] = i
                }

                return ret
            }
        }

        /*
         * Levenshtein Distance Calculations
         */
        val size = sizeA + 1
        val v = IntArray(size * (sizeB + 1))

        for(i in 1 .. sizeA) {
            v[i + 0] = i
        }

        for(j in 1 .. sizeB) {
            v[0 + j * size] = j
        }

        for(j in 1 .. sizeB) {
            for(i in 1 .. sizeA) {
                val cost = if(predicate(elementRetriever(listA, i - 1), elementRetriever(listB, j - 1))) 0 else 1
                v[i + j * size] = min(min(v[i - 1 + j * size] + 1, v[i + (j - 1) * size] + 1), v[i -1 + (j - 1) * size] + cost)
            }
        }

        /*
         * Reduction of index cordinates.
         */
        var i = sizeA
        var j = sizeB

        while (true) {
            val c = v[i + j * size]
            if (i > 0 && v[i - 1 + j * size] + 1 == c) {
                ret[i - 1] = -1
                i--
            } else if (j > 0 && v[i + (j - 1) * size] + 1 == c) {
                j--
            } else if (i > 0 && j > 0) {
                val dist = c - v[i - 1 + (j - 1) * size]
                if (dist == 1) {
                    ret[i - 1] = -1
                } else {
                    assert(dist == 0)
                    ret[i - 1] = j - 1
                }
                i--
                j--
            } else {
                break
            }
        }

        return ret
    }
}