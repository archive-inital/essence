package org.spectral.mapper.asm

import org.spectral.mapper.util.CompareUtil
import java.util.*

object Signature {
    private fun <T : PotentialComparable<T>> isPotentiallyEqual(a: T?, b: T?): Boolean {
        assert(a !is Collection<*>)
        return a == null == (b == null) && (a == null || a.isPotentiallyEqual(b!!))
    }

    private fun <T : PotentialComparable<T>> isPotentiallyEqual(a: List<T>?, b: List<T>?): Boolean {
        if (a == null != (b == null)) return false
        if (a != null) {
            if (a.size != b!!.size) return false
            var i = 0
            val max = a.size
            while (i < max) {
                if (!a[i].isPotentiallyEqual(b[i])) return false
                i++
            }
        }
        return true
    }

    class ClassSignature : PotentialComparable<ClassSignature> {
        override fun toString(): String {
            val ret = StringBuilder()
            if (typeParameters != null) {
                ret.append('<')
                for (tp in typeParameters!!) {
                    ret.append(tp.toString())
                }
                ret.append('>')
            }
            ret.append(superClassSignature!!.toString())
            if (superInterfaceSignatures != null) {
                for (ts in superInterfaceSignatures!!) {
                    ret.append(ts.toString())
                }
            }
            return ret.toString()
        }

        override fun isPotentiallyEqual(o: ClassSignature): Boolean {
            return (isPotentiallyEqual(typeParameters, o.typeParameters)
                    && superClassSignature!!.isPotentiallyEqual(o.superClassSignature!!)
                    && isPotentiallyEqual(superInterfaceSignatures, o.superInterfaceSignatures))
        }

        // [<
        var typeParameters: MutableList<TypeParameter>? = null

        // >]
        var superClassSignature: ClassTypeSignature? = null
        var superInterfaceSignatures: MutableList<ClassTypeSignature>? = null

        companion object {
            fun parse(sig: String, group: ClassGroup): ClassSignature {
                // [<TypeParameter+>] ClassTypeSignature ClassTypeSignature*
                val ret = ClassSignature()
                val pos = MutableInt()
                if (sig.startsWith("<")) {
                    pos.`val`++
                    ret.typeParameters = mutableListOf()
                    do {
                        ret.typeParameters!!.add(TypeParameter.parse(sig, pos, group))
                    } while (sig[pos.`val`] != '>')
                    pos.`val`++
                }
                ret.superClassSignature = ClassTypeSignature.parse(sig, pos, group)
                if (pos.`val` < sig.length) {
                    ret.superInterfaceSignatures = ArrayList()
                    do {
                        (ret.superInterfaceSignatures as ArrayList<ClassTypeSignature>).add(ClassTypeSignature.parse(sig, pos, group))
                    } while (pos.`val` < sig.length)
                }
                assert(ret.toString() == sig)
                return ret
            }
        }
    }

    class TypeParameter : PotentialComparable<TypeParameter> {
        override fun toString(): String {
            val ret = StringBuilder()
            ret.append(identifier)
            ret.append(':')
            if (classBound != null) ret.append(classBound!!.toString())
            if (interfaceBounds != null) {
                for (ts in interfaceBounds!!) {
                    ret.append(':')
                    ret.append(ts.toString())
                }
            }
            return ret.toString()
        }

        override fun isPotentiallyEqual(o: TypeParameter): Boolean {
            return ( /*identifier.equals(o.identifier)
					&&*/isPotentiallyEqual(classBound, o.classBound)
                    && isPotentiallyEqual(interfaceBounds, o.interfaceBounds))
        }

        var identifier: String? = null

        // :[
        var classBound: ReferenceTypeSignature? = null

        // ][:
        var interfaceBounds // separated by :
                : MutableList<ReferenceTypeSignature>? = null // ]

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): TypeParameter {
                // Identifier ':' ReferenceTypeSignature ( ':' ReferenceTypeSignature )*
                val ret = TypeParameter()
                val idEnd = sig.indexOf(':', pos.`val`)
                ret.identifier = sig.substring(pos.`val`, idEnd)
                pos.`val` = idEnd + 1
                val next = sig[pos.`val`]
                if (next == 'L' || next == 'T' || next == '[') {
                    ret.classBound = ReferenceTypeSignature.parse(sig, pos, group)
                }
                if (sig[pos.`val`] == ':') {
                    ret.interfaceBounds = ArrayList()
                    do {
                        pos.`val`++
                        (ret.interfaceBounds as ArrayList<ReferenceTypeSignature>).add(ReferenceTypeSignature.parse(sig, pos, group))
                    } while (sig[pos.`val`] == ':')
                }
                return ret
            }
        }
    }

    class ReferenceTypeSignature : PotentialComparable<ReferenceTypeSignature> {
        override fun toString(): String {
            return if (cls != null) {
                cls!!.toString()
            } else if (`var` != null) {
                "T$`var`;"
            } else {
                "[" + arrayElemCls!!.toString()
            }
        }

        override fun isPotentiallyEqual(o: ReferenceTypeSignature): Boolean {
            return if (cls != null) {
                o.cls != null && cls!!.isPotentiallyEqual(o.cls!!)
            } else if (`var` != null) {
                true //var.equals(o.var);
            } else {
                assert(arrayElemCls != null)
                o.arrayElemCls != null && arrayElemCls!!.isPotentiallyEqual(o.arrayElemCls!!)
            }
        }

        var cls: ClassTypeSignature? = null

        // | (T
        var `var`: String? = null

        // ;) | (\[
        var arrayElemCls: JavaTypeSignature? = null // )

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): ReferenceTypeSignature {
                // ClassTypeSignature | TypeVariableSignature | ( '[' JavaTypeSignature )
                val ret = ReferenceTypeSignature()
                val next = sig[pos.`val`]
                if (next == 'L') { // ClassTypeSignature: L[pkg/]cls[<arg>][.innerCls[<arg>]];
                    ret.cls = ClassTypeSignature.parse(sig, pos, group)
                } else if (next == 'T') { // TypeVariableSignature: 'T' Identifier ';'
                    pos.`val`++
                    val end = sig.indexOf(';', pos.`val`)
                    ret.`var` = sig.substring(pos.`val`, end)
                    pos.`val` = end + 1
                } else if (next == '[') { // ArrayTypeSignature: '[' JavaTypeSignature
                    pos.`val`++
                    ret.arrayElemCls = JavaTypeSignature.parse(sig, pos, group)
                } else {
                    throw RuntimeException("invalid char: $next")
                }
                return ret
            }
        }
    }

    class ClassTypeSignature : PotentialComparable<ClassTypeSignature> {
        override fun toString(): String {
            val ret = StringBuilder()
            ret.append('L')
            ret.append(cls!!.name)
            SimpleClassTypeSignature.printTypeArguments(typeArguments!!, ret)
            if (suffixes != null) {
                for (ts in suffixes!!) {
                    ret.append('.')
                    ret.append(ts.toString())
                }
            }
            ret.append(';')
            return ret.toString()
        }

        override fun isPotentiallyEqual(o: ClassTypeSignature): Boolean {
            return (CompareUtil.isPotentiallyEqual(cls!!, o.cls!!)
                    && isPotentiallyEqual(typeArguments, o.typeArguments)
                    && isPotentiallyEqual(suffixes, o.suffixes))
        }

        var cls: Class? = null
        var typeArguments: List<TypeArgument>? = null
        var suffixes: MutableList<SimpleClassTypeSignature>? = null

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): ClassTypeSignature {
                // 'L' [PackageSpecifier] SimpleClassTypeSignature ( '.' SimpleClassTypeSignature )* ';'
                // [PackageSpecifier] SimpleClassTypeSignature -> cls + typeArguments
                val ret = ClassTypeSignature()
                assert(sig[pos.`val`] == 'L')
                pos.`val`++
                var end = pos.`val`
                var c: Char
                while (sig[end].also { c = it } != '<' && c != '.' && c != ';') {
                    end++
                }
                ret.cls = group.getOrCreate(
                    if (c == ';') sig.substring(pos.`val` - 1, end + 1) else sig.substring(
                        pos.`val` - 1,
                        end
                    ) + ";"
                )
                pos.`val` = end
                ret.typeArguments = SimpleClassTypeSignature.parseTypeArguments(sig, pos, group)
                if (sig[pos.`val`] == '.') {
                    ret.suffixes = ArrayList()
                    do {
                        pos.`val`++
                        (ret.suffixes as ArrayList<SimpleClassTypeSignature>).add(SimpleClassTypeSignature.parse(sig, pos, group))
                    } while (sig[pos.`val`] == '.')
                }
                assert(sig[pos.`val`] == ';')
                pos.`val`++
                return ret
            }
        }
    }

    class JavaTypeSignature : PotentialComparable<JavaTypeSignature> {
        override fun toString(): String {
            return if (cls != null) {
                cls!!.toString()
            } else {
                baseType.toString()
            }
        }

        override fun isPotentiallyEqual(o: JavaTypeSignature): Boolean {
            return if (cls != null) {
                o.cls != null && cls!!.isPotentiallyEqual(o.cls!!)
            } else {
                o.cls == null && baseType == o.baseType
            }
        }

        var cls: ReferenceTypeSignature? = null

        // |
        var baseType // B C D F I J S Z
                = 0.toChar()

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): JavaTypeSignature {
                // ReferenceTypeSignature | 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z'
                val ret = JavaTypeSignature()
                val next = sig[pos.`val`]
                if (next == 'B' || next == 'C' || next == 'D' || next == 'F' || next == 'I' || next == 'J' || next == 'S' || next == 'Z') {
                    ret.baseType = next
                    pos.`val`++
                } else {
                    ret.cls = ReferenceTypeSignature.parse(sig, pos, group)
                }
                return ret
            }
        }
    }

    class SimpleClassTypeSignature : PotentialComparable<SimpleClassTypeSignature> {
        override fun toString(): String {
            return if (typeArguments == null) {
                identifier!!
            } else {
                val ret = StringBuilder()
                ret.append(identifier)
                printTypeArguments(typeArguments!!, ret)
                ret.toString()
            }
        }

        override fun isPotentiallyEqual(o: SimpleClassTypeSignature): Boolean {
            return  /*identifier.equals(o.identifier)
					&&*/isPotentiallyEqual(typeArguments, o.typeArguments)
        }

        var identifier: String? = null

        // [<
        var typeArguments: List<TypeArgument>? = null // >]

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): SimpleClassTypeSignature {
                // Identifier [<TypeArgument+>]
                val ret = SimpleClassTypeSignature()
                var end = pos.`val`
                var c: Char
                while (sig[end].also { c = it } != '<' && c != '.' && c != ';') {
                    end++
                }
                ret.identifier = sig.substring(pos.`val`, end)
                pos.`val` = end
                ret.typeArguments = parseTypeArguments(sig, pos, group)
                return ret
            }

            fun parseTypeArguments(sig: String, pos: MutableInt, group: ClassGroup): List<TypeArgument>? {
                return if (sig[pos.`val`] == '<') {
                    pos.`val`++
                    val ret: MutableList<TypeArgument> = ArrayList()
                    do {
                        ret.add(TypeArgument.parse(sig, pos, group))
                    } while (sig[pos.`val`] != '>')
                    pos.`val`++
                    ret
                } else {
                    null
                }
            }

            fun printTypeArguments(typeArguments: List<TypeArgument>, ret: StringBuilder) {
                if (typeArguments.isEmpty()) return
                ret.append('<')
                for (ta in typeArguments) {
                    ret.append(ta.toString())
                }
                ret.append('>')
            }
        }
    }

    class TypeArgument : PotentialComparable<TypeArgument> {
        override fun toString(): String {
            return if (wildcardIndicator.toInt() != 0) {
                wildcardIndicator.toString() + cls!!.toString()
            } else if (cls == null) {
                "*"
            } else {
                cls!!.toString()
            }
        }

        override fun isPotentiallyEqual(o: TypeArgument): Boolean {
            return (wildcardIndicator == o.wildcardIndicator
                    && isPotentiallyEqual(cls, o.cls))
        }

        // [
        var wildcardIndicator // + (extends) or - (super) if present, otherwise 0
                = 0.toChar()

        // ]
        var cls // null if TypeArgument = "*" (unbounded)
                : ReferenceTypeSignature? = null

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): TypeArgument {
                // ( ['+'|'-'] ReferenceTypeSignature ) | '*'
                val ret = TypeArgument()
                val next = sig[pos.`val`]
                if (next == '*') { // unbounded
                    pos.`val`++
                } else {
                    if (next == '+' || next == '-') { // extends / super
                        ret.wildcardIndicator = next
                        pos.`val`++
                    }
                    ret.cls = ReferenceTypeSignature.parse(sig, pos, group)
                }
                return ret
            }
        }
    }

    class MethodSignature : PotentialComparable<MethodSignature> {
        override fun toString(): String {
            val ret = StringBuilder()
            if (typeParameters != null) {
                ret.append('<')
                for (tp in typeParameters!!) {
                    ret.append(tp.toString())
                }
                ret.append('>')
            }
            ret.append('(')
            for (arg in args!!) {
                ret.append(arg.toString())
            }
            ret.append(')')
            if (result == null) {
                ret.append('V')
            } else {
                ret.append(result!!.toString())
            }
            if (throwsSignatures != null) {
                for (ts in throwsSignatures!!) {
                    ret.append(ts.toString())
                }
            }
            return ret.toString()
        }

        override fun isPotentiallyEqual(o: MethodSignature): Boolean {
            return (isPotentiallyEqual(typeParameters, o.typeParameters)
                    && (isPotentiallyEqual(args, o.args)
                    and isPotentiallyEqual(result, o.result)) && isPotentiallyEqual(
                throwsSignatures,
                o.throwsSignatures
            ))
        }

        // [<
        var typeParameters: MutableList<TypeParameter>? = null

        // >]\(
        var args: MutableList<JavaTypeSignature>? = null

        // \)
        var result // null if V (void)
                : JavaTypeSignature? = null

        // [
        var throwsSignatures: MutableList<ThrowsSignature>? = null // ]

        companion object {
            fun parse(sig: String, group: ClassGroup): MethodSignature {
                // [<TypeParameter+>] '(' JavaTypeSignature* ')' ( 'V' | JavaTypeSignature ) ThrowsSignature*
                val ret = MethodSignature()
                val pos = MutableInt()
                if (sig.startsWith("<")) {
                    pos.`val`++
                    ret.typeParameters = ArrayList()
                    do {
                        (ret.typeParameters as ArrayList<TypeParameter>).add(TypeParameter.parse(sig, pos, group))
                    } while (sig[pos.`val`] != '>')
                    pos.`val`++
                }
                assert(sig[pos.`val`] == '(')
                pos.`val`++
                ret.args = ArrayList()
                while (sig[pos.`val`] != ')') {
                    (ret.args as ArrayList<JavaTypeSignature>).add(JavaTypeSignature.parse(sig, pos, group))
                }
                assert(sig[pos.`val`] == ')')
                pos.`val`++
                if (sig[pos.`val`] == 'V') {
                    pos.`val`++
                } else {
                    ret.result = JavaTypeSignature.parse(sig, pos, group)
                }
                if (pos.`val` < sig.length) {
                    ret.throwsSignatures = ArrayList()
                    do {
                        (ret.throwsSignatures as ArrayList<ThrowsSignature>).add(ThrowsSignature.parse(sig, pos, group))
                    } while (pos.`val` < sig.length)
                }
                assert(ret.toString() == sig)
                return ret
            }
        }
    }

    class ThrowsSignature : PotentialComparable<ThrowsSignature> {
        override fun toString(): String {
            return if (cls != null) {
                "^" + cls!!.toString()
            } else {
                "^T$`var`;"
            }
        }

        override fun isPotentiallyEqual(o: ThrowsSignature): Boolean {
            return if (cls != null) {
                o.cls != null && cls!!.isPotentiallyEqual(o.cls!!)
            } else {
                true //var.equals(o.var);
            }
        }

        // ^(
        var cls: ClassTypeSignature? = null

        // | (T
        var `var`: String? = null // ;))

        companion object {
            fun parse(sig: String, pos: MutableInt, group: ClassGroup): ThrowsSignature {
                // '^' ( ClassTypeSignature | TypeVariableSignature )
                val ret = ThrowsSignature()
                assert(sig[pos.`val`] == '^')
                pos.`val`++
                val next = sig[pos.`val`]
                if (next == 'L') {
                    ret.cls = ClassTypeSignature.parse(sig, pos, group)
                } else if (next == 'T') { // TypeVariableSignature: 'T' Identifier ';'
                    pos.`val`++
                    val end = sig.indexOf(';', pos.`val`)
                    ret.`var` = sig.substring(pos.`val`, end)
                    pos.`val` = end + 1
                } else {
                    throw RuntimeException("invalid char: $next")
                }
                return ret
            }
        }
    }

    class FieldSignature : PotentialComparable<FieldSignature> {
        override fun toString(): String {
            return cls!!.toString()
        }

        override fun isPotentiallyEqual(o: FieldSignature): Boolean {
            return cls!!.isPotentiallyEqual(o.cls!!)
        }

        var cls: ReferenceTypeSignature? = null

        companion object {
            fun parse(sig: String, group: ClassGroup): FieldSignature {
                // ReferenceTypeSignature
                val ret = FieldSignature()
                val pos = MutableInt()
                ret.cls = ReferenceTypeSignature.parse(sig, pos, group)
                assert(pos.`val` == sig.length)
                assert(ret.toString() == sig)
                return ret
            }
        }
    }

    class MutableInt {
        override fun toString(): String {
            return `val`.toString()
        }

        var `val` = 0
    }

    private interface PotentialComparable<T> {
        fun isPotentiallyEqual(o: T): Boolean
    }
}
