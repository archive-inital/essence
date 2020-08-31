package org.spectral.remapper

import com.google.common.collect.HashMultimap
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.io.IOException
import java.util.ArrayDeque
import kotlin.math.pow

class HierarchyGraph : ClassVisitor(ASM8) {

    private val hierarchy = hashMapOf<String, HashSet<String>>()
    private val superHierarchy = hashMapOf<String, HashSet<String>>()
    private val methodHierarchy = hashMapOf<String, HashSet<MemberRef>>()
    private val fieldHierarchy = hashMapOf<String, HashSet<MemberRef>>()

    private lateinit var className: String

    data class MemberRef(val name: String, val desc: String) {

        override fun equals(other: Any?): Boolean {
            if(this === other) return true
            if(other == null || this::class.java != other::class.java) return false
            return (other as MemberRef).name == name && other.desc == desc
        }

        override fun hashCode(): Int {
            return name.hashCode().toDouble().pow(desc.hashCode().toDouble()).toInt()
        }
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)

        hierarchy[name] = hashSetOf()
        methodHierarchy[name] = hashSetOf()
        fieldHierarchy[name] = hashSetOf()

        if(superName != null) hierarchy[name]!!.add(superName)
        hierarchy[name]!!.addAll(interfaces!!)
        if((access and ACC_ENUM) != 0) hierarchy[name]!!.add("java/lang/Enum")

        className = name
    }

    override fun visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor? {
        val mv = super.visitMethod(access, name, desc, signature, exceptions)

        if((access and (ACC_PUBLIC or ACC_PROTECTED)) != 0) {
            methodHierarchy[className]!!.add(MemberRef(name, desc))
        }

        return mv
    }

    override fun visitField(
        access: Int,
        name: String,
        desc: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        val fv = super.visitField(access, name, desc, signature, value)

        if((access and (ACC_PUBLIC or ACC_PROTECTED)) != 0) {
            fieldHierarchy[className]!!.add(MemberRef(name, desc))
        }

        return fv
    }

    private fun visitClasspathClass(name: String) {
        hierarchy[name] = hashSetOf()
        methodHierarchy[name] = hashSetOf()
        fieldHierarchy[name] = hashSetOf()

        try {
            val inputStream = HierarchyGraph::class.java.classLoader.getResourceAsStream("$name.class") ?: return
            val reader = ClassReader(inputStream)
            reader.accept(this, 0)
        } catch(e : IOException) {
            throw RuntimeException(e)
        }
    }

    fun getSuperClasses(name: String): Set<String> {
        var result = hierarchy[name]

        if(result == null) {
            visitClasspathClass(name)
            result = hierarchy[name]
        }

        return result!!
    }

    fun getAllSuperClasses(name: String): Set<String> {
        val cacheResult = superHierarchy[name]
        if(cacheResult != null) return cacheResult

        val superClasses = mutableSetOf<String>()
        val stack = ArrayDeque<String>(getSuperClasses(name))

        while(stack.isNotEmpty()) {
            val currentClass = stack.pop()
            superClasses.add(currentClass)
            stack.addAll(getSuperClasses(currentClass))
        }

        superHierarchy[name] = superClasses.toHashSet()
        return superClasses
    }

    fun getHierarchyMethods(name: String): Set<MemberRef> {
        var result = methodHierarchy[name]
        if(result == null) {
            visitClasspathClass(name)
            result = methodHierarchy.get(name)
        }

        return result!!
    }

    fun getHierarchyFields(name: String): Set<MemberRef> {
        var result = fieldHierarchy.get(name)
        if(result == null) {
            visitClasspathClass(name)
            result = fieldHierarchy.get(name)
        }

        return result!!
    }
}