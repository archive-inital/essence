package org.spectral.deobfuscator.transformer.rename

import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.deobfuscator.asm.ClassGroupExt
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger
import java.util.*

/**
 * Renames all the methods to a more readable format.
 */
class NameGenerator : Transformer {

    private var classCounter = 0
    private var methodCounter = 0
    private var fieldCounter = 0

    private val mappings = hashMapOf<String, String>()

    /**
     * Run the renaming transformation.
     */
    override fun transform(group: ClassGroupExt) {
        /**
         * Generate the mappings.
         */
        this.generateMappings(group)
        this.applyMappings(group)

        Logger.info("Renamed [classes: $classCounter, methods: $methodCounter, fields: $fieldCounter].")
    }

    /**
     * In order to properly generate mappings, we loop through all classes, methods, and fields and generate
     * an incremental name remap.
     *
     * Then, for ones with possible members, we loop back through and set the names from their super member types.
     */
    private fun generateMappings(group: ClassGroupExt) {
        /**
         * Generate class name mappings.
         */
        group.forEach classLoop@ { c ->
            if(c.name.length <= 2) {
                mappings[c.name] = "class${++classCounter}"
            }
        }

        /**
         * Generate method name mappings
         */
        group.forEach classLoop@ { c ->
            c.methods.filter { it.isGamepackMethod() }.forEach methodLoop@ { m ->
                val owner = c
                if(m.name.indexOf("<") != -1 || (m.access and ACC_NATIVE) != 0) {
                    return@methodLoop
                }

                val stack = Stack<ClassNode>()
                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    if(node != owner && node.methods.firstOrNull { it.name == m.name && it.desc == m.desc } != null) {
                        return@methodLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        stack.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    stack.addAll(interfaces)
                }

                val name = "method${++methodCounter}"

                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    val key = node.name + "." + m.name + m.desc
                    mappings[key] = name
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            stack.push(k)
                        }
                    }
                }
            }
        }

        /**
         * Generate field name mappings
         */
        group.forEach classLoop@ { c ->
            c.fields.filter { it.name.length <= 2 }.forEach fieldLoop@ { f ->
                val owner = c
                val stack = Stack<ClassNode>()

                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    if(node != owner && node.fields.firstOrNull { it.name == f.name && it.desc == f.desc } != null) {
                        return@fieldLoop
                    }

                    val parent = group[node.superName]
                    if(parent != null) {
                        stack.push(parent)
                    }

                    val interfaces = node.interfaces.mapNotNull { group[it] }
                    stack.addAll(interfaces)
                }

                val name = "field${++fieldCounter}"

                stack.add(owner)
                while(stack.isNotEmpty()) {
                    val node = stack.pop()
                    val key = node.name + "." + f.name
                    mappings[key] = name
                    group.forEach { k ->
                        if(k.superName == node.name || k.interfaces.contains(node.name)) {
                            stack.push(k)
                        }
                    }
                }
            }
        }

    }

    /**
     * Apply the mappings to the [group] using the ASM built in
     * class remapping visitor.
     */
    private fun applyMappings(group: ClassGroupExt) {
        val remapper = SimpleRemapper(mappings)

        group.forEachIndexed { index, c ->
            val newNode = ClassNode()
            c.accept(ClassRemapper(newNode, remapper))
            group[index] = newNode
        }
    }


    private fun MethodNode.isGamepackMethod(): Boolean {
        if(this.name.length <= 2 || (this.name.length == 3 && this.name.startsWith("aa"))) {
            return true
        }
        return false
    }
}