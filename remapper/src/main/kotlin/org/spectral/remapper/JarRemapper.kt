package org.spectral.remapper

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.*
import org.spectral.mapping.Mappings
import org.spectral.remapper.asm.ClassGroup
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.system.exitProcess

/**
 * Responsible for remaping a JAR file using given loaded
 * [Mappings] object.
 */
class JarRemapper private constructor(
    private val inputJarFile: File,
    private val outputJarFile: File,
    private val origNamesFile: File,
    val mappings: Mappings
) {

    /**
     * The [JarRemapper] builder class.
     */
    class Builder {
        private var inputJarFile: File? = null
        private var outputJarFile: File? = null
        private var mappings: Mappings? = null
        private var origNamesFile: File? = null

        fun input(file: File) = this.apply { this.inputJarFile = file }
        fun output(file: File) = this.apply { this.outputJarFile = file }
        fun mappings(mappings: Mappings) = this.apply { this.mappings = mappings }
        fun origNames(file: File) = this.apply { this.origNamesFile = file }

        /**
         * Builds the [JarRemapper] instance.
         *
         * @return JarRemapper
         */
        fun build(): JarRemapper {
            if(inputJarFile == null || outputJarFile == null || mappings == null || origNamesFile == null) {
                Logger.error("All options have not been specified.")
                exitProcess(-1)
            }

            return JarRemapper(inputJarFile!!, outputJarFile!!, origNamesFile!!, mappings!!)
        }
    }

    /**
     * Remaps the input JAR file entry names from the obfuscated names
     * to the remapped names given some loaded mappings model.
     */
    fun run() {
        Logger.info("Remapping jar file: '${inputJarFile.path}'")

        val group = ClassGroup.fromJar(inputJarFile)

        val hierarchyGraph = HierarchyGraph()

        /*
         * Build the hierarchy graph
         */
        group.forEach { it.accept(hierarchyGraph) }

        /*
         * Build the original name mappings hash map.
         */
        val jsonMapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val origNameMappings = jsonMapper.readValue<HashMap<String, String>>(origNamesFile)

        val asmMappings = AsmMappings(group, mappings, origNameMappings, hierarchyGraph)
        asmMappings.init()

        /*
         * Build the ASM mappings remapper
         */
        val remapper = AsmRemapper(asmMappings, hierarchyGraph)

        /*
         * Remap each class in the class group
         */
        val remappedClassBytes = hashMapOf<String, ByteArray>()
        val jos = JarOutputStream(FileOutputStream(outputJarFile))
        JarFile(inputJarFile).use { jar ->
            jar.entries().asSequence()
                .forEach { entry ->
                    var name = entry.name
                    val inputStream = jar.getInputStream(entry)
                    var data = inputStream.readBytes()

                    if(name.endsWith(".DSA") || name.endsWith(".RSA") || name.endsWith(".EC") || name.endsWith(".SF")) {
                        return@forEach
                    }

                    if(name.endsWith(".class")) {
                        val className = name.substring(0, name.length - 6)
                        data = remapClass(className, data, asmMappings, remapper)
                        name = remapClassName(className, remapper) + ".class"
                    }

                    remappedClassBytes[name] = data
                }
        }

        remappedClassBytes.forEach { (fileName, bytes) ->
            jos.putNextEntry(JarEntry(fileName))
            jos.write(bytes)
            jos.closeEntry()
        }

        jos.close()

        Logger.info("Successfully remapped classes.")
    }

    private fun remapClassName(name: String, remapper: AsmRemapper): String {
        return remapper.map(name)
    }

    /**
     * Remaps a class using the [remapper] data.
     *
     * @param cls ClassNode
     * @param remapper AsmRemapper
     * @return ClassNode
     */
    private fun remapClass(name: String, data: ByteArray, mappings: AsmMappings, remapper: AsmRemapper): ByteArray {
        val cls = ClassNode()
        var reader = ClassReader(data)
        reader.accept(cls, 0)

        /*
         * Rename inner class innerNames
         */
        if(cls.innerClasses != null) cls.innerClasses.forEach { innerClass ->
            if(innerClass.innerName != null) {
                val newName = mappings.mapClass(innerClass.name)
                innerClass.name = newName.substring(newName.indexOf('$') + 1)
            }
        }

        /*
         * Rename local variables and add method arguments.
         */
        cls.methods.forEach { method ->
            val isStatic = (method.access and ACC_STATIC) != 0
            val argCount = Type.getArgumentTypes(method.desc).size

            /*
             * Add arguments with names
             */
            if(method.parameters == null || method.parameters.size < argCount) {
                method.parameters = mutableListOf()
                for(i in 0 until argCount) {
                    method.parameters.add(ParameterNode("arg${i + 1}", 0))
                }
            }

            /*
             * Remove empty local variable tables
             */
            if(method.localVariables != null && method.localVariables.size < method.lvTSize) {
                if(method.localVariables.size != 0) {
                    Logger.warn("WARNING: Removed non-empty LVT (size ${method.localVariables.size})")
                }

                method.localVariables = null
            }

            var index = 0
            val localNames = hashMapOf<Int, String>()
            var varSuffix = 0

            if(method.localVariables != null) method.localVariables.forEach { local ->
                /*
                 * Name the local variable.
                 */
                if(!isStatic && index == 0) {
                    local.name = "this"
                } else if(index < method.lvTSize) {
                    local.name = method.parameters[if(isStatic) index else index - 1].name
                } else {
                    local.name = mappings.getLocal(name, method.name, method.desc, index)

                    /*
                     * No mapping exists for this local variable. Use the previous local with the name
                     * index or generate a new unique name.
                     */

                    val localHash = Objects.hash(local.index, local.desc, local.signature)
                    if(local.name == null) {
                        val localName = localNames[localHash]
                        if(localName != null) {
                            local.name = localName
                        } else {
                            local.name = "rar${++varSuffix}"
                            localNames[localHash] = local.name
                        }
                    } else {
                        localNames[localHash] = local.name
                    }
                }

                /*
                 * Fix broken local start and end labels.
                 */
                if(local.start == local.end) {
                    local.start = method.instructions.first as LabelNode
                    local.end = method.instructions.first as LabelNode
                }

                index++
            } else {
                /*
                 * Generate local variable table based on arguments. No LVT but not argument
                 * list breaks fernflower.
                 */
                method.localVariables = mutableListOf()

                /*
                 * Get the label node at the start of the method, or add it if it's missing
                 */
                if(method.instructions.first !is LabelNode) {
                    method.instructions.insert(LabelNode())
                }

                val firstLabel = method.instructions.first as LabelNode

                /*
                 * Add the implicit this argument
                 */
                if(!isStatic) {
                    method.localVariables.add(LocalVariableNode("this", "L" + cls.name + ";", null, firstLabel, firstLabel, 0))
                }

                /*
                 * Add arguments to the local variable table
                 */
                val argTypes = Type.getArgumentTypes(method.desc)
                var i = 0

                method.parameters.forEach { param ->
                    method.localVariables.add(LocalVariableNode(param.name, argTypes[i].descriptor, null, firstLabel, firstLabel, if(isStatic) i else i + 1))
                    i++
                }
            }
        }

        var writer = ClassWriter(0)
        cls.accept(writer)

        val newData = writer.toByteArray()

        reader = ClassReader(newData)
        reader.accept(cls, 0)

        val node = ClassNode()
        val classRemapper = ClassRemapper(node, remapper)
        reader.accept(classRemapper, 0)

        writer = ClassWriter(0)
        node.accept(writer)

        return writer.toByteArray()
    }

    private val MethodNode.lvTSize: Int get() {
        val isStatic = (this.access and ACC_STATIC) != 0
        val argCount = Type.getArgumentTypes(this.desc).size
        return if(isStatic) argCount else argCount + 1
    }
}